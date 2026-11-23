package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.ClaimDetail;
import com.claimflow.claims.claim.ClaimDtos.ClaimSummary;
import com.claimflow.claims.claim.ClaimDtos.HistoryItem;
import com.claimflow.claims.claim.ClaimDtos.InformationRequestItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

/** Entity to DTO. {@code staffView} controls fields claimants must not see (the reserve). */
@Component
class ClaimMapper {

    private final Clock clock;
    private final int slaDays;

    ClaimMapper(Clock clock, @Value("${claimflow.sla-days:10}") int slaDays) {
        this.clock = clock;
        this.slaDays = slaDays;
    }

    ClaimSummary toSummary(Claim c, boolean staffView) {
        long age = ageDays(c);
        return new ClaimSummary(
                c.getId(), c.getReferenceNo(), c.getType(), c.getMarketCode(), c.getCurrency(),
                c.getClaimedAmount(), reserve(c, staffView), c.getStatus(), c.getClaimantId(), c.getAssigneeId(),
                c.getSubmittedAt(), c.getUpdatedAt(), age, slaBreached(c, age), c.getVersion());
    }

    ClaimDetail toDetail(Claim c, boolean staffView) {
        long age = ageDays(c);
        return new ClaimDetail(
                c.getId(), c.getReferenceNo(), c.getType(), c.getMarketCode(), c.getCurrency(),
                c.getPolicyNumber(), c.getIncidentDate(), c.getDescription(),
                c.getClaimedAmount(), reserve(c, staffView), c.getApprovedAmount(),
                c.getStatus(), c.getRejectionReason(), c.getClaimantId(), c.getAssigneeId(),
                c.getSubmittedAt(), c.getDecidedAt(), c.getSettledAt(), c.getUpdatedAt(),
                age, slaBreached(c, age), c.getStatus().allowedNext(), c.getVersion(),
                c.getHistory().stream()
                        .map(h -> new HistoryItem(h.getFromStatus(), h.getToStatus(), h.getActorId(),
                                h.getReason(), h.getOccurredAt()))
                        .toList(),
                c.getInformationRequests().stream()
                        .map(r -> new InformationRequestItem(r.getId(), r.getQuestion(), r.getResponse(),
                                r.getStatus(), r.getRequestedBy(), r.getRequestedAt(), r.getRespondedAt()))
                        .toList());
    }

    private static BigDecimal reserve(Claim c, boolean staffView) {
        return staffView ? c.getReserveAmount() : null;
    }

    private long ageDays(Claim c) {
        return Math.max(0, Duration.between(c.getSubmittedAt(), clock.instant()).toDays());
    }

    private boolean slaBreached(Claim c, long ageDays) {
        return ClaimStatus.OPEN.contains(c.getStatus()) && ageDays > slaDays;
    }
}
