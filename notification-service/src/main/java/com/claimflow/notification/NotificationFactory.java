package com.claimflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Decides who is told what about a claim event: zero or one notification per event.
 * Unknown event types are skipped, so the producer can add new events without breaking this service.
 */
@Component
class NotificationFactory {

    private static final Logger log = LoggerFactory.getLogger(NotificationFactory.class);

    Optional<Notification> create(ClaimEventMessage e) {
        String ref = e.referenceNo();
        return switch (e.eventType()) {
            case "ClaimSubmitted" -> toClaimant(e, "We received claim " + ref + ". We'll review it shortly.");
            case "ClaimAssigned" -> toClaimant(e, "Claim " + ref + " is now being reviewed by a claims officer.");
            case "InformationRequested" ->
                    toClaimant(e, "We need more information on claim " + ref + ": " + e.payloadText("question"));
            case "InformationProvided" -> toAssignedOfficer(e, "The claimant replied on claim " + ref + ".");
            case "ClaimApproved" -> toClaimant(e, "Claim " + ref + " was approved for "
                    + e.payloadText("currency") + " " + e.payloadText("approvedAmount") + ".");
            case "ClaimRejected" -> toClaimant(e, "Claim " + ref + " was not approved: " + e.payloadText("reason"));
            case "ClaimSettled" -> toClaimant(e, "Payment for claim " + ref + " has been made.");
            case "ClaimWithdrawn" -> toClaimant(e, "Claim " + ref + " was withdrawn.");
            case "ReserveChanged" -> Optional.empty(); // internal estimate, never shown to claimants
            default -> {
                log.info("Skipping event {} of unknown type {}", e.eventId(), e.eventType());
                yield Optional.empty();
            }
        };
    }

    private static Optional<Notification> toClaimant(ClaimEventMessage e, String message) {
        return Optional.of(notification(e, e.claimantId(), message));
    }

    private static Optional<Notification> toAssignedOfficer(ClaimEventMessage e, String message) {
        String assignee = e.payloadText("assigneeId");
        if (assignee == null) {
            log.warn("Event {} ({}) has no assigneeId; nobody to notify", e.eventId(), e.eventType());
            return Optional.empty();
        }
        return Optional.of(notification(e, UUID.fromString(assignee), message));
    }

    /** createdAt is the event time, so notifications keep lifecycle order even when consumed late. */
    private static Notification notification(ClaimEventMessage e, UUID userId, String message) {
        return new Notification(UUID.randomUUID(), e.eventId(), userId, e.claimId(), e.eventType(), message,
                e.occurredAt());
    }
}
