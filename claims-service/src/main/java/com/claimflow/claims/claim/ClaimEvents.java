package com.claimflow.claims.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events raised by the {@link Claim} aggregate, one per state change.
 * {@code eventId} is a fresh random UUID per event; consumers de-duplicate on it.
 */
public final class ClaimEvents {

    private ClaimEvents() {
    }

    public sealed interface ClaimEvent permits ClaimSubmitted, ClaimAssigned, InformationRequested,
            InformationProvided, ReserveChanged, ClaimApproved, ClaimRejected, ClaimSettled, ClaimWithdrawn {

        UUID eventId();

        UUID claimId();

        String referenceNo();

        UUID claimantId();

        UUID actorId();

        Instant occurredAt();
    }

    public record ClaimSubmitted(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                 Instant occurredAt, ClaimType type, String marketCode, String currency,
                                 BigDecimal claimedAmount) implements ClaimEvent {
    }

    public record ClaimAssigned(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                Instant occurredAt, UUID assigneeId) implements ClaimEvent {
    }

    public record InformationRequested(UUID eventId, UUID claimId, String referenceNo, UUID claimantId,
                                       UUID actorId, Instant occurredAt, UUID requestId, String question)
            implements ClaimEvent {
    }

    public record InformationProvided(UUID eventId, UUID claimId, String referenceNo, UUID claimantId,
                                      UUID actorId, Instant occurredAt, UUID requestId, UUID assigneeId)
            implements ClaimEvent {
    }

    public record ReserveChanged(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                 Instant occurredAt, String marketCode, String currency, BigDecimal oldAmount,
                                 BigDecimal newAmount) implements ClaimEvent {
    }

    public record ClaimApproved(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                Instant occurredAt, String currency, BigDecimal approvedAmount)
            implements ClaimEvent {
    }

    public record ClaimRejected(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                Instant occurredAt, String reason) implements ClaimEvent {
    }

    public record ClaimSettled(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                               Instant occurredAt, String currency, BigDecimal settledAmount)
            implements ClaimEvent {
    }

    public record ClaimWithdrawn(UUID eventId, UUID claimId, String referenceNo, UUID claimantId, UUID actorId,
                                 Instant occurredAt) implements ClaimEvent {
    }
}
