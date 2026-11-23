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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ClaimEventMessageTest {

    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID CLAIM = UUID.randomUUID();
    private static final UUID CLAIMANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID OFFICER = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final String REF = "CLM-001001";
    private static final Instant AT = Instant.parse("2026-11-20T08:00:00Z");

    @Test
    void envelopeFieldsComeFromTheDomainEvent() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimWithdrawn(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT));

        assertThat(m.eventId()).isEqualTo(EVENT);
        assertThat(m.eventType()).isEqualTo("ClaimWithdrawn");
        assertThat(m.schemaVersion()).isEqualTo(1);
        assertThat(m.claimId()).isEqualTo(CLAIM);
        assertThat(m.referenceNo()).isEqualTo(REF);
        assertThat(m.claimantId()).isEqualTo(CLAIMANT);
        assertThat(m.actorId()).isEqualTo(ACTOR);
        assertThat(m.occurredAt()).isEqualTo(AT);
        assertThat(m.payload()).isEmpty();
    }

    @Test
    void claimSubmitted() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimSubmitted(EVENT, CLAIM, REF, CLAIMANT, CLAIMANT, AT,
                ClaimType.MOTOR, "SG", "SGD", new BigDecimal("3200.50")));

        assertThat(m.eventType()).isEqualTo("ClaimSubmitted");
        assertThat(m.payload()).containsExactly(
                entry("type", "MOTOR"), entry("marketCode", "SG"), entry("currency", "SGD"),
                entry("claimedAmount", "3200.50"));
    }

    @Test
    void claimAssigned() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimAssigned(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, OFFICER));
        assertThat(m.eventType()).isEqualTo("ClaimAssigned");
        assertThat(m.payload()).containsExactly(entry("assigneeId", OFFICER));
    }

    @Test
    void informationRequested() {
        ClaimEventMessage m = ClaimEventMessage.from(new InformationRequested(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                REQUEST, "Photos please"));
        assertThat(m.eventType()).isEqualTo("InformationRequested");
        assertThat(m.payload()).containsExactly(entry("requestId", REQUEST), entry("question", "Photos please"));
    }

    @Test
    void informationProvided() {
        ClaimEventMessage m = ClaimEventMessage.from(new InformationProvided(EVENT, CLAIM, REF, CLAIMANT, CLAIMANT, AT,
                REQUEST, OFFICER));
        assertThat(m.eventType()).isEqualTo("InformationProvided");
        assertThat(m.payload()).containsExactly(entry("requestId", REQUEST), entry("assigneeId", OFFICER));
    }

    @Test
    void reserveChanged() {
        ClaimEventMessage m = ClaimEventMessage.from(new ReserveChanged(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SG", "SGD", new BigDecimal("3200.00"), new BigDecimal("2900.00")));
        assertThat(m.eventType()).isEqualTo("ReserveChanged");
        assertThat(m.payload()).containsExactly(
                entry("marketCode", "SG"), entry("currency", "SGD"),
                entry("oldAmount", "3200.00"), entry("newAmount", "2900.00"));
    }

    @Test
    void claimApproved() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimApproved(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SGD", new BigDecimal("2750.00")));
        assertThat(m.eventType()).isEqualTo("ClaimApproved");
        assertThat(m.payload()).containsExactly(entry("currency", "SGD"), entry("approvedAmount", "2750.00"));
    }

    @Test
    void claimRejected() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimRejected(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, "Not covered"));
        assertThat(m.eventType()).isEqualTo("ClaimRejected");
        assertThat(m.payload()).containsExactly(entry("reason", "Not covered"));
    }

    @Test
    void claimSettled() {
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimSettled(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SGD", new BigDecimal("2750.00")));
        assertThat(m.eventType()).isEqualTo("ClaimSettled");
        assertThat(m.payload()).containsExactly(entry("currency", "SGD"), entry("settledAmount", "2750.00"));
    }

    @Test
    void moneyIsAPlainStringEvenForLargeOrScientificValues() {
        // 1E+7 would print as "1E+7" with toString(); the wire format must be "10000000.00"
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimApproved(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "IDR", new BigDecimal("1E+7")));
        assertThat(m.payload().get("approvedAmount")).isEqualTo("10000000.00");

        ClaimEventMessage precise = ClaimEventMessage.from(new ReserveChanged(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SG", "SGD", new BigDecimal("999999999999.99"), new BigDecimal("0.01")));
        assertThat(precise.payload()).contains(entry("oldAmount", "999999999999.99"), entry("newAmount", "0.01"));
    }

    @Test
    void moneyAlwaysHasTwoDecimals() {
        // A client that sends 2750 (JSON number without decimals) must still produce "2750.00"
        ClaimEventMessage m = ClaimEventMessage.from(new ClaimApproved(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SGD", new BigDecimal("2750")));
        assertThat(m.payload().get("approvedAmount")).isEqualTo("2750.00");

        ClaimEventMessage zero = ClaimEventMessage.from(new ReserveChanged(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT,
                "SG", "SGD", new BigDecimal("2750.00"), BigDecimal.ZERO));
        assertThat(zero.payload().get("newAmount")).isEqualTo("0.00");
    }

    @Test
    void everyEventTypeIsCovered() {
        List<ClaimEvent> all = List.of(
                new ClaimSubmitted(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, ClaimType.PROPERTY, "MY", "MYR", BigDecimal.ONE),
                new ClaimAssigned(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, OFFICER),
                new InformationRequested(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, REQUEST, "q"),
                new InformationProvided(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, REQUEST, OFFICER),
                new ReserveChanged(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, "MY", "MYR", BigDecimal.ONE, BigDecimal.TEN),
                new ClaimApproved(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, "MYR", BigDecimal.ONE),
                new ClaimRejected(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, "r"),
                new ClaimSettled(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT, "MYR", BigDecimal.ONE),
                new ClaimWithdrawn(EVENT, CLAIM, REF, CLAIMANT, ACTOR, AT));

        assertThat(all).extracting(e -> ClaimEventMessage.from(e).eventType())
                .containsExactly("ClaimSubmitted", "ClaimAssigned", "InformationRequested", "InformationProvided",
                        "ReserveChanged", "ClaimApproved", "ClaimRejected", "ClaimSettled", "ClaimWithdrawn");
        assertThat(ClaimEvent.class.getPermittedSubclasses()).hasSize(all.size());
        all.forEach(e -> assertThat(ClaimEventMessage.from(e).payload()).isInstanceOf(Map.class));
    }
}
