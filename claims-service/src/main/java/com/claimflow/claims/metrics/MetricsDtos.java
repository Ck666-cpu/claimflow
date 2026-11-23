package com.claimflow.claims.metrics;

import com.claimflow.claims.claim.ClaimStatus;
import com.claimflow.claims.claim.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Metrics responses. Money is a string with two decimals (no JSON float rounding) and is never summed
 * across currencies: every amount belongs to one market, and each market has one currency.
 */
public final class MetricsDtos {

    private MetricsDtos() {
    }

    // ---------------------------------------------------------------- workload

    public record WorkloadReport(
            Instant generatedAt,
            int slaDays,
            Queue queue,
            WaitingOnClaimant waitingOnClaimant,
            Totals totals,
            List<OfficerWorkload> officers) {
    }

    /** SUBMITTED claims nobody has picked up; {@code oldestAgeDays} is null when the queue is empty. */
    public record Queue(long unassigned, Integer oldestAgeDays) {
    }

    /** INFO_REQUESTED claims; waiting measured from the open request's requested_at. */
    public record WaitingOnClaimant(long count, Integer oldestWaitingDays) {
    }

    public record Totals(long open, long slaBreaches) {
    }

    /**
     * {@code byStatus} always has UNDER_REVIEW, INFO_REQUESTED and APPROVED.
     * {@code avgDaysToDecisionLast30} has one decimal, null if nothing was decided in the last 30 days.
     */
    public record OfficerWorkload(
            UUID officerId,
            String name,
            long open,
            Map<ClaimStatus, Long> byStatus,
            long slaBreaches,
            long decidedLast7Days,
            BigDecimal avgDaysToDecisionLast30) {
    }

    // ---------------------------------------------------------------- exposure

    public record ExposureReport(Instant generatedAt, List<MarketExposure> markets) {
    }

    /** Sum of reserve_amount over open claims in one market, in that market's currency (ADR-7). */
    public record MarketExposure(
            String marketCode,
            String marketName,
            String currency,
            long openClaims,
            String totalReserve,
            Map<ClaimType, String> byType,
            Map<ClaimStatus, String> byStatus,
            Map<String, String> ageing) {
    }
}
