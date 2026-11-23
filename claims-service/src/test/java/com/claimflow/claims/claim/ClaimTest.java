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
import com.claimflow.claims.shared.BadRequestException;
import com.claimflow.claims.shared.ConflictException;
import com.claimflow.claims.shared.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.claimflow.claims.claim.ClaimStatus.APPROVED;
import static com.claimflow.claims.claim.ClaimStatus.INFO_REQUESTED;
import static com.claimflow.claims.claim.ClaimStatus.REJECTED;
import static com.claimflow.claims.claim.ClaimStatus.SETTLED;
import static com.claimflow.claims.claim.ClaimStatus.SUBMITTED;
import static com.claimflow.claims.claim.ClaimStatus.UNDER_REVIEW;
import static com.claimflow.claims.claim.ClaimStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    private static final Market SG = new Market("SG", "Singapore", "SGD");
    private static final UUID CLAIMANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OFFICER_1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID OFFICER_2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-000000000021");

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(T0, ZoneOffset.UTC);

    private static Instant t(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static Claim newClaim() {
        return Claim.submit(UUID.randomUUID(), "CLM-001001", ClaimType.MOTOR, SG, "POL-SG-1",
                TODAY.minusDays(2), "Rear-ended at a junction", money("2800.00"), CLAIMANT, T0);
    }

    private static Claim underReview() {
        Claim claim = newClaim();
        claim.assign(OFFICER_1, OFFICER_1, t(1));
        return claim;
    }

    private static Claim approved() {
        Claim claim = underReview();
        claim.approve(money("2500.00"), OFFICER_1, t(2));
        return claim;
    }

    private static <E extends ClaimEvent> E lastEvent(Claim claim, Class<E> type) {
        List<Object> events = claim.pendingEvents();
        Object last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(type);
        return type.cast(last);
    }

    // ---------------------------------------------------------------- submit

    @Test
    void submitSetsDefaults() {
        Claim claim = newClaim();

        assertThat(claim.getStatus()).isEqualTo(SUBMITTED);
        assertThat(claim.getCurrency()).isEqualTo("SGD");
        assertThat(claim.getMarketCode()).isEqualTo("SG");
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("2800.00");
        assertThat(claim.getAssigneeId()).isNull();
        assertThat(claim.getVersion()).isZero();
        assertThat(claim.getSubmittedAt()).isEqualTo(T0);
        assertThat(claim.getUpdatedAt()).isEqualTo(T0);

        assertThat(claim.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(SUBMITTED);
            assertThat(h.getActorId()).isEqualTo(CLAIMANT);
        });

        assertThat(claim.pendingEvents()).singleElement().isInstanceOfSatisfying(ClaimSubmitted.class, e -> {
            assertThat(e.eventId()).isNotNull();
            assertThat(e.claimId()).isEqualTo(claim.getId());
            assertThat(e.referenceNo()).isEqualTo("CLM-001001");
            assertThat(e.claimantId()).isEqualTo(CLAIMANT);
            assertThat(e.actorId()).isEqualTo(CLAIMANT);
            assertThat(e.currency()).isEqualTo("SGD");
            assertThat(e.claimedAmount()).isEqualByComparingTo("2800.00");
        });
    }

    @Test
    void submitAcceptsIncidentToday() {
        Claim claim = Claim.submit(UUID.randomUUID(), "CLM-001002", ClaimType.PROPERTY, SG, "POL", TODAY,
                "Burst pipe", money("1.00"), CLAIMANT, T0);
        assertThat(claim.getStatus()).isEqualTo(SUBMITTED);
    }

    @Test
    void submitRejectsFutureIncidentDate() {
        assertThatThrownBy(() -> Claim.submit(UUID.randomUUID(), "CLM-001003", ClaimType.MOTOR, SG, "POL",
                TODAY.plusDays(1), "Not yet happened", money("100.00"), CLAIMANT, T0))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submitRejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> Claim.submit(UUID.randomUUID(), "CLM-001004", ClaimType.MOTOR, SG, "POL",
                TODAY, "Free", BigDecimal.ZERO, CLAIMANT, T0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Claim.submit(UUID.randomUUID(), "CLM-001005", ClaimType.MOTOR, SG, "POL",
                TODAY, "Negative", money("-5.00"), CLAIMANT, T0))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------------------------------------------------------------- assign

    @Test
    void assignStartsReview() {
        Claim claim = newClaim();
        claim.assign(OFFICER_1, OFFICER_1, t(1));

        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
        assertThat(claim.getAssigneeId()).isEqualTo(OFFICER_1);
        assertThat(claim.isAssignedTo(OFFICER_1)).isTrue();
        assertThat(claim.getHistory()).hasSize(2);
        assertThat(lastEvent(claim, ClaimAssigned.class).assigneeId()).isEqualTo(OFFICER_1);
    }

    @Test
    void reassignKeepsStatus() {
        Claim claim = underReview();
        claim.requestInformation("Photos please", OFFICER_1, t(2));

        claim.assign(OFFICER_2, LEAD, t(3));

        assertThat(claim.getStatus()).isEqualTo(INFO_REQUESTED);
        assertThat(claim.getAssigneeId()).isEqualTo(OFFICER_2);
        assertThat(claim.getHistory()).hasSize(3); // no history row for a reassignment
        ClaimAssigned event = lastEvent(claim, ClaimAssigned.class);
        assertThat(event.assigneeId()).isEqualTo(OFFICER_2);
        assertThat(event.actorId()).isEqualTo(LEAD);
    }

    @Test
    void assignToCurrentOwnerConflicts() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.assign(OFFICER_1, LEAD, t(2)))
                .isInstanceOf(ConflictException.class)
                .isNotInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void closedClaimCannotBeReassigned() {
        Claim claim = approved();
        claim.settle(OFFICER_1, t(3));

        assertThatThrownBy(() -> claim.assign(OFFICER_2, LEAD, t(4)))
                .isInstanceOf(InvalidClaimStateException.class)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cannot assign a claim in status SETTLED");
    }

    // ---------------------------------------------------------------- information requests

    @Test
    void informationRequestLoop() {
        Claim claim = underReview();

        UUID requestId = claim.requestInformation("Please send photos", OFFICER_1, t(2));

        assertThat(claim.getStatus()).isEqualTo(INFO_REQUESTED);
        assertThat(claim.getInformationRequests()).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(requestId);
            assertThat(r.getStatus()).isEqualTo(InformationRequest.Status.OPEN);
            assertThat(r.getRequestedBy()).isEqualTo(OFFICER_1);
        });
        InformationRequested requested = lastEvent(claim, InformationRequested.class);
        assertThat(requested.requestId()).isEqualTo(requestId);
        assertThat(requested.question()).isEqualTo("Please send photos");

        claim.provideInformation(requestId, "Attached", CLAIMANT, t(3));

        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
        InformationRequest answered = claim.getInformationRequests().get(0);
        assertThat(answered.getStatus()).isEqualTo(InformationRequest.Status.ANSWERED);
        assertThat(answered.getResponse()).isEqualTo("Attached");
        assertThat(answered.getRespondedAt()).isEqualTo(t(3));
        InformationProvided provided = lastEvent(claim, InformationProvided.class);
        assertThat(provided.requestId()).isEqualTo(requestId);
        assertThat(provided.assigneeId()).isEqualTo(OFFICER_1);
        assertThat(provided.actorId()).isEqualTo(CLAIMANT);

        // a second round is allowed once the first is answered
        claim.requestInformation("And the police report?", OFFICER_1, t(4));
        assertThat(claim.getInformationRequests()).hasSize(2);
    }

    @Test
    void questionIsRequired() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.requestInformation("  ", OFFICER_1, t(2)))
                .isInstanceOf(BadRequestException.class);
        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
    }

    @Test
    void secondOpenRequestRefused() {
        Claim claim = underReview();
        claim.requestInformation("First", OFFICER_1, t(2));

        assertThatThrownBy(() -> claim.requestInformation("Second", OFFICER_1, t(3)))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(claim.getInformationRequests()).hasSize(1);
    }

    @Test
    void answeringTwiceRefused() {
        Claim claim = underReview();
        UUID requestId = claim.requestInformation("Photos?", OFFICER_1, t(2));
        claim.provideInformation(requestId, "Here", CLAIMANT, t(3));

        assertThatThrownBy(() -> claim.provideInformation(requestId, "Again", CLAIMANT, t(4)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void answeringUnknownRequestIsNotFound() {
        Claim claim = underReview();
        claim.requestInformation("Photos?", OFFICER_1, t(2));

        assertThatThrownBy(() -> claim.provideInformation(UUID.randomUUID(), "Here", CLAIMANT, t(3)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- reserve

    @Test
    void reserveOnlyDuringAssessment() {
        Claim submitted = newClaim();
        assertThatThrownBy(() -> submitted.changeReserve(money("100.00"), OFFICER_1, t(1)))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessage("Cannot change the reserve of a claim in status SUBMITTED");

        Claim approved = approved();
        assertThatThrownBy(() -> approved.changeReserve(money("100.00"), OFFICER_1, t(3)))
                .isInstanceOf(InvalidClaimStateException.class);

        Claim infoRequested = underReview();
        infoRequested.requestInformation("Photos?", OFFICER_1, t(2));
        infoRequested.changeReserve(money("2000.00"), OFFICER_1, t(3));
        assertThat(infoRequested.getReserveAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void reserveChangeIsRecorded() {
        Claim claim = underReview();

        claim.changeReserve(money("2400.00"), OFFICER_1, t(2));

        assertThat(claim.getReserveAmount()).isEqualByComparingTo("2400.00");
        assertThat(claim.getReserveChanges()).singleElement().satisfies(rc -> {
            assertThat(rc.getOldAmount()).isEqualByComparingTo("2800.00");
            assertThat(rc.getNewAmount()).isEqualByComparingTo("2400.00");
            assertThat(rc.getActorId()).isEqualTo(OFFICER_1);
        });
        ReserveChanged event = lastEvent(claim, ReserveChanged.class);
        assertThat(event.oldAmount()).isEqualByComparingTo("2800.00");
        assertThat(event.newAmount()).isEqualByComparingTo("2400.00");
        assertThat(event.currency()).isEqualTo("SGD");
        assertThat(event.marketCode()).isEqualTo("SG");
    }

    @Test
    void sameReserveIsNoOp() {
        Claim claim = underReview();
        int eventsBefore = claim.pendingEvents().size();

        claim.changeReserve(money("2800"), OFFICER_1, t(2)); // same value, different scale

        assertThat(claim.getReserveChanges()).isEmpty();
        assertThat(claim.pendingEvents()).hasSize(eventsBefore);
    }

    @Test
    void negativeReserveRejected() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.changeReserve(money("-1.00"), OFFICER_1, t(2)))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------------------------------------------------------------- decisions

    @Test
    void approveSetsReserveToApprovedAmountThenSettleReleasesIt() {
        Claim claim = underReview();

        claim.approve(money("2500.00"), OFFICER_1, t(2));

        assertThat(claim.getStatus()).isEqualTo(APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("2500.00");
        assertThat(claim.getDecidedAt()).isEqualTo(t(2));
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("2500.00");
        assertThat(claim.getReserveChanges()).hasSize(1);
        assertThat(lastEvent(claim, ClaimApproved.class).approvedAmount()).isEqualByComparingTo("2500.00");

        claim.settle(OFFICER_1, t(3));

        assertThat(claim.getStatus()).isEqualTo(SETTLED);
        assertThat(claim.getSettledAt()).isEqualTo(t(3));
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("2500.00");
        assertThat(claim.getReserveChanges()).hasSize(2);
        assertThat(lastEvent(claim, ClaimSettled.class).settledAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void decisionsAlwaysWriteAReserveAuditRowEvenWhenUnchanged() {
        Claim claim = underReview();

        claim.approve(money("2800.00"), LEAD, t(2)); // same as the current reserve

        assertThat(claim.getReserveChanges()).singleElement().satisfies(rc -> {
            assertThat(rc.getOldAmount()).isEqualByComparingTo("2800.00");
            assertThat(rc.getNewAmount()).isEqualByComparingTo("2800.00");
            assertThat(rc.getActorId()).isEqualTo(LEAD);
            assertThat(rc.getOccurredAt()).isEqualTo(t(2));
        });
    }

    @Test
    void withdrawWritesAReserveAuditRowEvenWhenReserveIsAlreadyZero() {
        Claim claim = underReview();
        claim.changeReserve(BigDecimal.ZERO, OFFICER_1, t(2));

        claim.withdraw(CLAIMANT, t(3));

        assertThat(claim.getReserveChanges()).hasSize(2);
        ReserveChange last = claim.getReserveChanges().get(1);
        assertThat(last.getOldAmount()).isEqualByComparingTo("0");
        assertThat(last.getNewAmount()).isEqualByComparingTo("0");
        assertThat(last.getActorId()).isEqualTo(CLAIMANT);
    }

    @Test
    void approveNeedsPositiveAmount() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.approve(BigDecimal.ZERO, OFFICER_1, t(2)))
                .isInstanceOf(BadRequestException.class);
        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
    }

    @Test
    void rejectNeedsReasonAndReleasesReserve() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.reject(" ", OFFICER_1, t(2)))
                .isInstanceOf(BadRequestException.class);
        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);

        claim.reject("Not covered", OFFICER_1, t(3));

        assertThat(claim.getStatus()).isEqualTo(REJECTED);
        assertThat(claim.getRejectionReason()).isEqualTo("Not covered");
        assertThat(claim.getDecidedAt()).isEqualTo(t(3));
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        assertThat(claim.getHistory().get(claim.getHistory().size() - 1).getReason()).isEqualTo("Not covered");
        assertThat(lastEvent(claim, ClaimRejected.class).reason()).isEqualTo("Not covered");
    }

    @Test
    void cannotSettleBeforeApproval() {
        Claim claim = underReview();
        assertThatThrownBy(() -> claim.settle(OFFICER_1, t(2)))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessage("Cannot move a claim from UNDER_REVIEW to SETTLED");
    }

    @Test
    void cannotApproveASubmittedClaim() {
        Claim claim = newClaim();
        assertThatThrownBy(() -> claim.approve(money("100.00"), LEAD, t(1)))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ---------------------------------------------------------------- withdraw

    @Test
    void withdrawAllowedBeforeDecision() {
        Claim submitted = newClaim();
        submitted.withdraw(CLAIMANT, t(1));
        assertThat(submitted.getStatus()).isEqualTo(WITHDRAWN);
        assertThat(submitted.getReserveAmount()).isEqualByComparingTo("0");
        assertThat(lastEvent(submitted, ClaimWithdrawn.class).actorId()).isEqualTo(CLAIMANT);

        assertThat(submitted.getHistory().get(1).getReason()).isEqualTo("Withdrawn by claimant");

        Claim reviewing = underReview();
        reviewing.withdraw(CLAIMANT, t(2));
        assertThat(reviewing.getStatus()).isEqualTo(WITHDRAWN);

        Claim infoRequested = underReview();
        infoRequested.requestInformation("Photos?", OFFICER_1, t(2));
        infoRequested.withdraw(CLAIMANT, t(3));
        assertThat(infoRequested.getStatus()).isEqualTo(WITHDRAWN);
    }

    @Test
    void withdrawRefusedAfterApproval() {
        Claim claim = approved();
        assertThatThrownBy(() -> claim.withdraw(CLAIMANT, t(3)))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(claim.getStatus()).isEqualTo(APPROVED);
    }

    // ---------------------------------------------------------------- version and ownership

    @Test
    void staleVersionRefused() {
        Claim claim = newClaim();
        claim.checkVersion(0); // matches

        assertThatThrownBy(() -> claim.checkVersion(3))
                .isInstanceOf(StaleClaimException.class)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("0");
    }

    @Test
    void ownershipHelpers() {
        Claim claim = underReview();
        assertThat(claim.isOwnedBy(CLAIMANT)).isTrue();
        assertThat(claim.isOwnedBy(OFFICER_1)).isFalse();
        assertThat(claim.isAssignedTo(OFFICER_1)).isTrue();
        assertThat(claim.isAssignedTo(OFFICER_2)).isFalse();
        assertThat(newClaim().isAssignedTo(OFFICER_1)).isFalse();
    }

    // ---------------------------------------------------------------- full lifecycle

    @Test
    void historyOrderForFullLifecycle() {
        Claim claim = newClaim();
        claim.assign(OFFICER_1, OFFICER_1, t(1));
        UUID requestId = claim.requestInformation("Photos?", OFFICER_1, t(2));
        claim.provideInformation(requestId, "Attached", CLAIMANT, t(3));
        claim.changeReserve(money("2600.00"), OFFICER_1, t(4));
        claim.approve(money("2500.00"), OFFICER_1, t(5));
        claim.settle(OFFICER_1, t(6));

        assertThat(claim.getHistory())
                .extracting(StatusHistoryEntry::getFromStatus, StatusHistoryEntry::getToStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(null, SUBMITTED),
                        org.assertj.core.groups.Tuple.tuple(SUBMITTED, UNDER_REVIEW),
                        org.assertj.core.groups.Tuple.tuple(UNDER_REVIEW, INFO_REQUESTED),
                        org.assertj.core.groups.Tuple.tuple(INFO_REQUESTED, UNDER_REVIEW),
                        org.assertj.core.groups.Tuple.tuple(UNDER_REVIEW, APPROVED),
                        org.assertj.core.groups.Tuple.tuple(APPROVED, SETTLED));
        assertThat(claim.getHistory())
                .extracting(StatusHistoryEntry::getOccurredAt)
                .isSorted();
        assertThat(claim.getHistory())
                .extracting(StatusHistoryEntry::getReason)
                .containsExactly(
                        null,
                        "Assigned to " + OFFICER_1,
                        "Photos?",
                        "Attached",
                        "Approved SGD 2500.00",
                        "Settled SGD 2500.00");

        assertThat(claim.pendingEvents())
                .extracting(Object::getClass)
                .containsExactly(ClaimSubmitted.class, ClaimAssigned.class, InformationRequested.class,
                        InformationProvided.class, ReserveChanged.class, ClaimApproved.class, ClaimSettled.class);

        assertThat(claim.getReserveChanges())
                .extracting(ReserveChange::getNewAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(money("2600.00"), money("2500.00"), BigDecimal.ZERO);
        assertThat(claim.getUpdatedAt()).isEqualTo(t(6));
    }
}
