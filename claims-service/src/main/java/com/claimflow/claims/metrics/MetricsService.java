package com.claimflow.claims.metrics;

import com.claimflow.claims.claim.ClaimStatus;
import com.claimflow.claims.claim.ClaimType;
import com.claimflow.claims.metrics.MetricsDtos.ExposureReport;
import com.claimflow.claims.metrics.MetricsDtos.MarketExposure;
import com.claimflow.claims.metrics.MetricsDtos.OfficerWorkload;
import com.claimflow.claims.metrics.MetricsDtos.Queue;
import com.claimflow.claims.metrics.MetricsDtos.Totals;
import com.claimflow.claims.metrics.MetricsDtos.WaitingOnClaimant;
import com.claimflow.claims.metrics.MetricsDtos.WorkloadReport;
import com.claimflow.claims.metrics.MetricsQueries.ExposureRow;
import com.claimflow.claims.metrics.MetricsQueries.QueueRow;
import com.claimflow.claims.metrics.MetricsQueries.TotalsRow;
import com.claimflow.claims.metrics.MetricsQueries.WaitingRow;
import com.claimflow.claims.shared.CurrentUser;
import com.claimflow.claims.shared.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff metrics: workload (officers and leads) and exposure (leads only). Assembles the SQL results
 * into reports and fills in every expected key with zero, so clients never have to guess a missing key.
 */
@Service
@Transactional(readOnly = true)
class MetricsService {

    static final List<String> AGE_BUCKETS = List.of("0-7d", "8-30d", "31d+");

    private final MetricsQueries queries;
    private final Clock clock;
    private final int slaDays;

    MetricsService(MetricsQueries queries, Clock clock, @Value("${claimflow.sla-days:10}") int slaDays) {
        this.queries = queries;
        this.clock = clock;
        this.slaDays = slaDays;
    }

    public WorkloadReport workload(CurrentUser user) {
        user.requireRole(Role.OFFICER, Role.LEAD);
        Instant now = clock.instant();

        QueueRow queue = queries.queue(now);
        WaitingRow waiting = queries.waitingOnClaimant(now);
        TotalsRow totals = queries.totals(now, slaDays);
        List<OfficerWorkload> officers = queries.officers(now, slaDays).stream()
                .map(o -> {
                    Map<ClaimStatus, Long> byStatus = new LinkedHashMap<>();
                    byStatus.put(ClaimStatus.UNDER_REVIEW, o.underReview());
                    byStatus.put(ClaimStatus.INFO_REQUESTED, o.infoRequested());
                    byStatus.put(ClaimStatus.APPROVED, o.approved());
                    return new OfficerWorkload(o.officerId(), o.name(), o.open(), byStatus, o.slaBreaches(),
                            o.decidedLast7Days(), o.avgDaysToDecisionLast30());
                })
                .toList();

        return new WorkloadReport(now, slaDays,
                new Queue(queue.unassigned(), queue.oldestAgeDays()),
                new WaitingOnClaimant(waiting.count(), waiting.oldestWaitingDays()),
                new Totals(totals.open(), totals.slaBreaches()),
                officers);
    }

    public ExposureReport exposure(CurrentUser user) {
        user.requireRole(Role.LEAD);
        Instant now = clock.instant();

        Map<String, MarketTotals> markets = new LinkedHashMap<>(); // rows arrive sorted by market code
        for (ExposureRow row : queries.exposure(now)) {
            MarketTotals m = markets.computeIfAbsent(row.marketCode(),
                    code -> new MarketTotals(code, row.marketName(), row.currency()));
            if (row.claims() > 0) {
                m.add(ClaimType.valueOf(row.type()), ClaimStatus.valueOf(row.status()), row.ageBucket(),
                        row.claims(), row.reserve());
            }
        }
        return new ExposureReport(now, markets.values().stream().map(MarketTotals::toDto).toList());
    }

    /** Running totals for one market, every key present from the start. */
    private static final class MarketTotals {

        private final String code;
        private final String name;
        private final String currency;
        private long openClaims;
        private BigDecimal total = BigDecimal.ZERO;
        private final Map<ClaimType, BigDecimal> byType = new EnumMap<>(ClaimType.class);
        private final Map<ClaimStatus, BigDecimal> byStatus = new LinkedHashMap<>();
        private final Map<String, BigDecimal> ageing = new LinkedHashMap<>();

        MarketTotals(String code, String name, String currency) {
            this.code = code;
            this.name = name;
            this.currency = currency;
            for (ClaimType t : ClaimType.values()) {
                byType.put(t, BigDecimal.ZERO);
            }
            for (ClaimStatus s : ClaimStatus.values()) {
                if (ClaimStatus.OPEN.contains(s)) {
                    byStatus.put(s, BigDecimal.ZERO);
                }
            }
            for (String bucket : AGE_BUCKETS) {
                ageing.put(bucket, BigDecimal.ZERO);
            }
        }

        void add(ClaimType type, ClaimStatus status, String bucket, long claims, BigDecimal reserve) {
            openClaims += claims;
            total = total.add(reserve);
            byType.merge(type, reserve, BigDecimal::add);
            byStatus.merge(status, reserve, BigDecimal::add);
            ageing.merge(bucket, reserve, BigDecimal::add);
        }

        MarketExposure toDto() {
            return new MarketExposure(code, name, currency, openClaims, money(total),
                    toMoney(byType), toMoney(byStatus), toMoney(ageing));
        }

        private static <K> Map<K, String> toMoney(Map<K, BigDecimal> amounts) {
            Map<K, String> out = new LinkedHashMap<>();
            amounts.forEach((k, v) -> out.put(k, money(v)));
            return out;
        }

        private static String money(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        }
    }
}
