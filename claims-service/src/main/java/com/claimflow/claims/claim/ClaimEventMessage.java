package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimEvents.ClaimApproved;
import com.claimflow.claims.claim.ClaimEvents.ClaimAssigned;
import com.claimflow.claims.claim.ClaimEvents.ClaimEvent;
import com.claimflow.claims.claim.ClaimEvents.ClaimRejected;
import com.claimflow.claims.claim.ClaimEvents.ClaimSettled;
import com.claimflow.claims.claim.ClaimEvents.ClaimSubmitted;
import com.claimflow.claims.claim.ClaimEvents.ClaimWithdrawn;
import com.claimflow.claims.claim.ClaimEvents.InformationProvided;
import com.claimflow.claims.claim.ClaimEvents.InformationRequested;
import com.claimflow.claims.claim.ClaimEvents.ReserveChanged;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The integration contract on {@code claims.events} (ADR-10). Domain events ({@link ClaimEvents}) are
 * internal and may be refactored freely; this envelope is what consumers parse. Common fields are
 * top-level; event-specific fields go in {@code payload}. Money is a plain decimal string so JSON
 * never loses precision. Bump {@link #SCHEMA_VERSION} on a breaking change.
 */
public record ClaimEventMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID claimId,
        String referenceNo,
        UUID claimantId,
        UUID actorId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public static final int SCHEMA_VERSION = 1;

    public static ClaimEventMessage from(ClaimEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        switch (e) {
            case ClaimSubmitted s -> {
                p.put("type", s.type().name());
                p.put("marketCode", s.marketCode());
                p.put("currency", s.currency());
                p.put("claimedAmount", money(s.claimedAmount()));
            }
            case ClaimAssigned a -> p.put("assigneeId", a.assigneeId());
            case InformationRequested r -> {
                p.put("requestId", r.requestId());
                p.put("question", r.question());
            }
            case InformationProvided r -> {
                p.put("requestId", r.requestId());
                p.put("assigneeId", r.assigneeId());
            }
            case ReserveChanged r -> {
                p.put("marketCode", r.marketCode());
                p.put("currency", r.currency());
                p.put("oldAmount", money(r.oldAmount()));
                p.put("newAmount", money(r.newAmount()));
            }
            case ClaimApproved a -> {
                p.put("currency", a.currency());
                p.put("approvedAmount", money(a.approvedAmount()));
            }
            case ClaimRejected r -> p.put("reason", r.reason());
            case ClaimSettled s -> {
                p.put("currency", s.currency());
                p.put("settledAmount", money(s.settledAmount()));
            }
            case ClaimWithdrawn w -> {
                // no event-specific fields
            }
        }
        return new ClaimEventMessage(e.eventId(), e.getClass().getSimpleName(), SCHEMA_VERSION, e.claimId(),
                e.referenceNo(), e.claimantId(), e.actorId(), e.occurredAt(), Collections.unmodifiableMap(p));
    }

    /**
     * Always two decimals, as stored (NUMERIC(14,2)): a client may send 2750 or 2750.00, the wire says "2750.00".
     * Request validation already caps amounts at 2 fractional digits, so this never rounds.
     */
    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
