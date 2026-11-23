package com.claimflow.claims.metrics;

import com.claimflow.claims.claim.ClaimStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Read model: plain SQL aggregates over the claims tables, no entity loading (ADR-12).
 * "Now" is always a parameter, never now() in SQL, so tests can pin time. Age = whole days since
 * submitted_at, rounded down (the same rule as ClaimMapper.ageDays).
 */
@Repository
class MetricsQueries {

    /** Whole days between :now and a timestamp column. */
    private static String daysSince(String column) {
        return "floor(extract(epoch from (cast(:now as timestamptz) - " + column + ")) / 86400)::int";
    }

    private static final String CLAIM_AGE = daysSince("c.submitted_at");

    private static final List<String> OPEN = ClaimStatus.OPEN.stream().map(Enum::name).toList();

    private final JdbcClient jdbc;

    MetricsQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record QueueRow(long unassigned, Integer oldestAgeDays) {
    }

    record WaitingRow(long count, Integer oldestWaitingDays) {
    }

    record TotalsRow(long open, long slaBreaches) {
    }

    record OfficerRow(UUID officerId, String name, long open, long underReview, long infoRequested,
                      long approved, long slaBreaches, long decidedLast7Days, BigDecimal avgDaysToDecisionLast30) {
    }

    /** One row per (market, type, status, age bucket) with open claims, plus one empty row per market without any. */
    record ExposureRow(String marketCode, String marketName, String currency, String type, String status,
                       String ageBucket, long claims, BigDecimal reserve) {
    }

    QueueRow queue(Instant now) {
        return jdbc.sql("""
                        select count(*) as unassigned, max(%s) as oldest
                        from claims c
                        where c.status = 'SUBMITTED' and c.assignee_id is null
                        """.formatted(CLAIM_AGE))
                .param("now", at(now))
                .query((rs, i) -> new QueueRow(rs.getLong("unassigned"), rs.getObject("oldest", Integer.class)))
                .single();
    }

    WaitingRow waitingOnClaimant(Instant now) {
        return jdbc.sql("""
                        select count(c.id) as waiting, max(%s) as oldest
                        from claims c
                        left join information_requests ir on ir.claim_id = c.id and ir.status = 'OPEN'
                        where c.status = 'INFO_REQUESTED'
                        """.formatted(daysSince("ir.requested_at")))
                .param("now", at(now))
                .query((rs, i) -> new WaitingRow(rs.getLong("waiting"), rs.getObject("oldest", Integer.class)))
                .single();
    }

    TotalsRow totals(Instant now, int slaDays) {
        return jdbc.sql("""
                        select count(*) as open,
                               count(*) filter (where %s > :slaDays) as sla_breaches
                        from claims c
                        where c.status in (:open)
                        """.formatted(CLAIM_AGE))
                .param("now", at(now))
                .param("slaDays", slaDays)
                .param("open", OPEN)
                .query((rs, i) -> new TotalsRow(rs.getLong("open"), rs.getLong("sla_breaches")))
                .single();
    }

    /** Every OFFICER, including those with no claims (left join), busiest first. */
    List<OfficerRow> officers(Instant now, int slaDays) {
        return jdbc.sql("""
                        select u.id, u.name,
                               count(c.id) filter (where c.status in (:open))                    as open,
                               count(c.id) filter (where c.status = 'UNDER_REVIEW')              as under_review,
                               count(c.id) filter (where c.status = 'INFO_REQUESTED')            as info_requested,
                               count(c.id) filter (where c.status = 'APPROVED')                  as approved,
                               count(c.id) filter (where c.status in (:open) and %1$s > :slaDays) as sla_breaches,
                               count(c.id) filter (where c.decided_at >  cast(:now as timestamptz) - interval '7 days'
                                                     and c.decided_at <= cast(:now as timestamptz)) as decided_7,
                               round(avg(extract(epoch from (c.decided_at - c.submitted_at)) / 86400)
                                     filter (where c.decided_at >  cast(:now as timestamptz) - interval '30 days'
                                               and c.decided_at <= cast(:now as timestamptz)), 1) as avg_days_30
                        from users u
                        left join claims c on c.assignee_id = u.id
                        where u.role = 'OFFICER'
                        group by u.id, u.name
                        order by open desc, u.name
                        """.formatted(CLAIM_AGE))
                .param("now", at(now))
                .param("slaDays", slaDays)
                .param("open", OPEN)
                .query((rs, i) -> new OfficerRow(
                        rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("open"), rs.getLong("under_review"), rs.getLong("info_requested"),
                        rs.getLong("approved"), rs.getLong("sla_breaches"), rs.getLong("decided_7"),
                        rs.getBigDecimal("avg_days_30")))
                .list();
    }

    /** All markets (left join), open claims only; amounts grouped so they never mix currencies. */
    List<ExposureRow> exposure(Instant now) {
        return jdbc.sql("""
                        select m.code, m.name, m.currency, c.type, c.status,
                               case when c.id is null then null
                                    when %1$s <= 7  then '0-7d'
                                    when %1$s <= 30 then '8-30d'
                                    else '31d+' end as age_bucket,
                               count(c.id) as claims,
                               coalesce(sum(c.reserve_amount), 0) as reserve
                        from markets m
                        left join claims c on c.market_code = m.code and c.status in (:open)
                        group by m.code, m.name, m.currency, c.type, c.status, age_bucket
                        order by m.code
                        """.formatted(CLAIM_AGE))
                .param("now", at(now))
                .param("open", OPEN)
                .query((rs, i) -> new ExposureRow(
                        rs.getString("code"), rs.getString("name"), rs.getString("currency"),
                        rs.getString("type"), rs.getString("status"), rs.getString("age_bucket"),
                        rs.getLong("claims"), rs.getBigDecimal("reserve")))
                .list();
    }

    private static OffsetDateTime at(Instant now) {
        return now.atOffset(ZoneOffset.UTC);
    }
}
