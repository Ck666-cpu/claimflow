package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.AssignRequest;
import com.claimflow.claims.claim.ClaimDtos.ClaimDetail;
import com.claimflow.claims.claim.ClaimDtos.CreateInformationRequest;
import com.claimflow.claims.claim.ClaimDtos.DecisionRequest;
import com.claimflow.claims.claim.ClaimDtos.DecisionRequest.Outcome;
import com.claimflow.claims.claim.ClaimDtos.InformationResponseRequest;
import com.claimflow.claims.claim.ClaimDtos.ReserveRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitClaimRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitResult;
import com.claimflow.claims.claim.ClaimDtos.VersionRequest;
import com.claimflow.claims.shared.AppUser;
import com.claimflow.claims.shared.AppUserRepository;
import com.claimflow.claims.shared.BadRequestException;
import com.claimflow.claims.shared.ConflictException;
import com.claimflow.claims.shared.CurrentUser;
import com.claimflow.claims.shared.ForbiddenException;
import com.claimflow.claims.shared.NotFoundException;
import com.claimflow.claims.shared.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final Market SG = new Market("SG", "Singapore", "SGD");

    private static final CurrentUser ALICE = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alice Tan", Role.CLAIMANT);
    private static final CurrentUser RAJ = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "Raj Kumar", Role.CLAIMANT);
    private static final CurrentUser BEN = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000011"), "Ben Lim", Role.OFFICER);
    private static final CurrentUser CHITRA = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000012"), "Chitra Nair", Role.OFFICER);
    private static final CurrentUser DANIEL = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000021"), "Daniel Wong", Role.LEAD);

    @Mock ClaimRepository claims;
    @Mock MarketRepository markets;
    @Mock IdempotencyKeyRepository idempotencyKeys;
    @Mock AppUserRepository users;

    private ClaimService service;
    private ClaimQueryService queries;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ClaimMapper mapper = new ClaimMapper(clock, 10);
        service = new ClaimService(claims, markets, idempotencyKeys, users, mapper, clock);
        queries = new ClaimQueryService(claims, mapper);
        lenient().when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Claim stored(Claim claim) {
        lenient().when(claims.findById(claim.getId())).thenReturn(Optional.of(claim));
        return claim;
    }

    private static Claim submittedBy(CurrentUser claimant) {
        return Claim.submit(UUID.randomUUID(), "CLM-001001", ClaimType.MOTOR, SG, "POL-1",
                LocalDate.of(2026, 11, 18), "Rear-ended", new BigDecimal("2800.00"), claimant.id(), NOW.minusSeconds(3600));
    }

    private static Claim assignedTo(CurrentUser officer) {
        Claim claim = submittedBy(ALICE);
        claim.assign(officer.id(), officer.id(), NOW.minusSeconds(1800));
        return claim;
    }

    private static AppUser userWithRole(Role role) {
        AppUser user = mock(AppUser.class);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    // ---------------------------------------------------------------- assign

    @Test
    void officerCannotAssignToSomeoneElse() {
        Claim claim = stored(submittedBy(ALICE));

        assertThatThrownBy(() -> service.assign(BEN, claim.getId(), new AssignRequest(CHITRA.id(), 0L)))
                .isInstanceOf(ForbiddenException.class);
        verify(claims, never()).saveAndFlush(any());
    }

    @Test
    void officerPicksUpUnassignedClaimForThemselves() {
        Claim claim = stored(submittedBy(ALICE));

        ClaimDetail detail = service.assign(BEN, claim.getId(), new AssignRequest(null, 0L));

        assertThat(detail.assigneeId()).isEqualTo(BEN.id());
        assertThat(detail.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(detail.reserveAmount()).isEqualByComparingTo("2800.00"); // staff see the reserve
    }

    @Test
    void officerCannotPickUpAnAssignedClaim() {
        Claim claim = stored(assignedTo(CHITRA));

        assertThatThrownBy(() -> service.assign(BEN, claim.getId(), new AssignRequest(null, 0L)))
                .isInstanceOf(ConflictException.class);
        assertThat(claim.getAssigneeId()).isEqualTo(CHITRA.id());
    }

    @Test
    void leadMustNameAnOfficer() {
        Claim claim = stored(assignedTo(BEN));

        assertThatThrownBy(() -> service.assign(DANIEL, claim.getId(), new AssignRequest(null, 0L)))
                .isInstanceOf(BadRequestException.class);

        AppUser raj = userWithRole(Role.CLAIMANT);
        when(users.findById(RAJ.id())).thenReturn(Optional.of(raj));
        assertThatThrownBy(() -> service.assign(DANIEL, claim.getId(), new AssignRequest(RAJ.id(), 0L)))
                .isInstanceOf(BadRequestException.class);

        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(DANIEL, claim.getId(), new AssignRequest(unknown, 0L)))
                .isInstanceOf(BadRequestException.class);

        AppUser chitra = userWithRole(Role.OFFICER);
        when(users.findById(CHITRA.id())).thenReturn(Optional.of(chitra));
        ClaimDetail detail = service.assign(DANIEL, claim.getId(), new AssignRequest(CHITRA.id(), 0L));
        assertThat(detail.assigneeId()).isEqualTo(CHITRA.id());
    }

    @Test
    void claimantCannotAssign() {
        Claim claim = stored(submittedBy(ALICE));
        assertThatThrownBy(() -> service.assign(ALICE, claim.getId(), new AssignRequest(null, 0L)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------------------------------------------------------------- assessment actions

    @Test
    void officerCannotActOnAnotherOfficersClaim() {
        Claim claim = stored(assignedTo(CHITRA));
        UUID id = claim.getId();

        assertThatThrownBy(() -> service.requestInformation(BEN, id, new CreateInformationRequest("Photos?", 0L)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.changeReserve(BEN, id, new ReserveRequest(BigDecimal.TEN, 0L)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.decide(BEN, id,
                new DecisionRequest(Outcome.APPROVE, BigDecimal.TEN, null, 0L)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.settle(BEN, id, new VersionRequest(0L)))
                .isInstanceOf(ForbiddenException.class);
        verify(claims, never()).saveAndFlush(any());
    }

    @Test
    void leadMayActOnAnyClaim() {
        Claim claim = stored(assignedTo(CHITRA));

        ClaimDetail detail = service.changeReserve(DANIEL, claim.getId(), new ReserveRequest(new BigDecimal("2000.00"), 0L));

        assertThat(detail.reserveAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void approveRequiresAnAmount() {
        Claim claim = stored(assignedTo(BEN));
        assertThatThrownBy(() -> service.decide(BEN, claim.getId(), new DecisionRequest(Outcome.APPROVE, null, null, 0L)))
                .isInstanceOf(BadRequestException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void staleVersionIsRefusedBeforeAnyChange() {
        Claim claim = stored(assignedTo(BEN));
        assertThatThrownBy(() -> service.changeReserve(BEN, claim.getId(), new ReserveRequest(BigDecimal.TEN, 5L)))
                .isInstanceOf(StaleClaimException.class);
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("2800.00");
    }

    // ---------------------------------------------------------------- claimant isolation

    @Test
    void claimantGets404ForAnotherClaimantsClaim() {
        Claim alices = stored(submittedBy(ALICE));
        UUID id = alices.getId();

        assertThatThrownBy(() -> service.withdraw(RAJ, id, new VersionRequest(0L)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.respondToInformationRequest(RAJ, id, UUID.randomUUID(),
                new InformationResponseRequest("Here", 0L)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> queries.detail(id, RAJ))
                .isInstanceOf(NotFoundException.class);
        assertThat(alices.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void claimantDoesNotSeeTheReserve() {
        Claim claim = stored(submittedBy(ALICE));

        assertThat(queries.detail(claim.getId(), ALICE).reserveAmount()).isNull();
        assertThat(queries.detail(claim.getId(), BEN).reserveAmount()).isEqualByComparingTo("2800.00");
    }

    // ---------------------------------------------------------------- submit

    @Test
    void submitCreatesClaimAndStoresIdempotencyKey() {
        when(markets.findById("SG")).thenReturn(Optional.of(SG));
        when(claims.nextReferenceNumber()).thenReturn(1001L);
        when(idempotencyKeys.findById(any())).thenReturn(Optional.empty());

        SubmitResult result = service.submit(ALICE, request(), "key-1");

        assertThat(result.created()).isTrue();
        assertThat(result.claim().referenceNo()).isEqualTo("CLM-001001");
        assertThat(result.claim().currency()).isEqualTo("SGD");
        assertThat(result.claim().reserveAmount()).isNull(); // claimant view
        verify(claims).saveAndFlush(any(Claim.class));
        verify(idempotencyKeys).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    void reusedIdempotencyKeyReturnsOriginalAndSavesNothing() {
        Claim original = stored(submittedBy(ALICE));
        when(idempotencyKeys.findById(any())).thenReturn(Optional.of(
                new IdempotencyKey("key-1", ALICE.id(), original.getId(), NOW.minusSeconds(60))));

        SubmitResult result = service.submit(ALICE, request(), "key-1");

        assertThat(result.created()).isFalse();
        assertThat(result.claim().id()).isEqualTo(original.getId());
        verify(claims, never()).saveAndFlush(any());
        verify(claims, never()).nextReferenceNumber();
        verify(idempotencyKeys, never()).saveAndFlush(any());
    }

    @Test
    void submitWithUnknownMarketIsBadRequest() {
        when(markets.findById("SG")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(ALICE, request(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void staffCannotSubmit() {
        assertThatThrownBy(() -> service.submit(BEN, request(), null))
                .isInstanceOf(ForbiddenException.class);
    }

    private static SubmitClaimRequest request() {
        return new SubmitClaimRequest(ClaimType.MOTOR, "SG", "POL-1", LocalDate.of(2026, 11, 18),
                "Rear-ended", new BigDecimal("2800.00"));
    }
}
