package com.claimflow.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFactoryTest {

    private static final UUID CLAIM = UUID.randomUUID();
    private static final UUID CLAIMANT = UUID.randomUUID();
    private static final UUID OFFICER = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-11-20T08:00:00Z");

    private final NotificationFactory factory = new NotificationFactory();

    private static ClaimEventMessage event(String type, Map<String, Object> payload) {
        return new ClaimEventMessage(UUID.randomUUID(), type, 1, CLAIM, "CLM-001001", CLAIMANT, OFFICER, AT, payload);
    }

    private Notification toClaimant(String type, Map<String, Object> payload) {
        ClaimEventMessage e = event(type, payload);
        Notification n = factory.create(e).orElseThrow();
        assertThat(n.getUserId()).isEqualTo(CLAIMANT);
        assertThat(n.getEventId()).isEqualTo(e.eventId());
        assertThat(n.getClaimId()).isEqualTo(CLAIM);
        assertThat(n.getEventType()).isEqualTo(type);
        assertThat(n.getCreatedAt()).isEqualTo(AT);
        assertThat(n.isRead()).isFalse();
        return n;
    }

    @Test
    void claimSubmitted() {
        assertThat(toClaimant("ClaimSubmitted", Map.of("currency", "SGD", "claimedAmount", "100.00")).getMessage())
                .isEqualTo("We received claim CLM-001001. We'll review it shortly.");
    }

    @Test
    void claimAssigned() {
        assertThat(toClaimant("ClaimAssigned", Map.of("assigneeId", OFFICER.toString())).getMessage())
                .isEqualTo("Claim CLM-001001 is now being reviewed by a claims officer.");
    }

    @Test
    void informationRequested() {
        assertThat(toClaimant("InformationRequested", Map.of("question", "Please send photos.")).getMessage())
                .isEqualTo("We need more information on claim CLM-001001: Please send photos.");
    }

    @Test
    void informationProvidedGoesToTheAssignedOfficer() {
        Notification n = factory.create(event("InformationProvided",
                Map.of("requestId", UUID.randomUUID().toString(), "assigneeId", OFFICER.toString()))).orElseThrow();
        assertThat(n.getUserId()).isEqualTo(OFFICER);
        assertThat(n.getMessage()).isEqualTo("The claimant replied on claim CLM-001001.");
    }

    @Test
    void informationProvidedWithoutAssigneeNotifiesNobody() {
        assertThat(factory.create(event("InformationProvided", Map.of()))).isEmpty();
    }

    @Test
    void claimApproved() {
        assertThat(toClaimant("ClaimApproved", Map.of("currency", "SGD", "approvedAmount", "2750.00")).getMessage())
                .isEqualTo("Claim CLM-001001 was approved for SGD 2750.00.");
    }

    @Test
    void claimRejected() {
        assertThat(toClaimant("ClaimRejected", Map.of("reason", "Not covered by the policy")).getMessage())
                .isEqualTo("Claim CLM-001001 was not approved: Not covered by the policy");
    }

    @Test
    void claimSettled() {
        assertThat(toClaimant("ClaimSettled", Map.of("currency", "SGD", "settledAmount", "2750.00")).getMessage())
                .isEqualTo("Payment for claim CLM-001001 has been made.");
    }

    @Test
    void claimWithdrawn() {
        assertThat(toClaimant("ClaimWithdrawn", Map.of()).getMessage())
                .isEqualTo("Claim CLM-001001 was withdrawn.");
    }

    @Test
    void reserveChangedNotifiesNobody() {
        Optional<Notification> n = factory.create(event("ReserveChanged",
                Map.of("oldAmount", "100.00", "newAmount", "80.00")));
        assertThat(n).isEmpty();
    }

    @Test
    void unknownTypeNotifiesNobody() {
        assertThat(factory.create(event("ClaimTeleported", Map.of()))).isEmpty();
    }
}
