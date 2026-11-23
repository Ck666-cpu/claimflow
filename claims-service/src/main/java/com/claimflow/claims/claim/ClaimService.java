package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.AssignRequest;
import com.claimflow.claims.claim.ClaimDtos.ClaimDetail;
import com.claimflow.claims.claim.ClaimDtos.CreateInformationRequest;
import com.claimflow.claims.claim.ClaimDtos.DecisionRequest;
import com.claimflow.claims.claim.ClaimDtos.InformationResponseRequest;
import com.claimflow.claims.claim.ClaimDtos.ReserveRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitClaimRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitResult;
import com.claimflow.claims.claim.ClaimDtos.VersionRequest;
import com.claimflow.claims.shared.AppUserRepository;
import com.claimflow.claims.shared.BadRequestException;
import com.claimflow.claims.shared.ConflictException;
import com.claimflow.claims.shared.CurrentUser;
import com.claimflow.claims.shared.ForbiddenException;
import com.claimflow.claims.shared.NotFoundException;
import com.claimflow.claims.shared.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Claim commands. Every command follows the same pattern:
 * load, check <em>who</em> (role and ownership), checkVersion, call the aggregate, saveAndFlush, map.
 * {@code saveAndFlush} (not {@code save}) bumps {@code @Version} before mapping, so the response carries
 * the version the client must send next. Spring Data publishes the aggregate's events during the save,
 * inside this transaction (Modulith stores them in the outbox).
 */
@Service
@Transactional
class ClaimService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final ClaimRepository claims;
    private final MarketRepository markets;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final AppUserRepository users;
    private final ClaimMapper mapper;
    private final Clock clock;

    ClaimService(ClaimRepository claims, MarketRepository markets, IdempotencyKeyRepository idempotencyKeys,
                 AppUserRepository users, ClaimMapper mapper, Clock clock) {
        this.claims = claims;
        this.markets = markets;
        this.idempotencyKeys = idempotencyKeys;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- claimant commands

    public SubmitResult submit(CurrentUser user, SubmitClaimRequest req, String idempotencyKey) {
        user.requireRole(Role.CLAIMANT);

        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Optional<IdempotencyKey> previous = idempotencyKeys.findById(new IdempotencyKey.Pk(key, user.id()));
            if (previous.isPresent()) {
                Claim original = claims.findById(previous.get().getClaimId())
                        .orElseThrow(() -> new NotFoundException("Claim for this Idempotency-Key no longer exists"));
                return new SubmitResult(mapper.toDetail(original, false), false);
            }
        }

        Market market = markets.findById(req.marketCode())
                .orElseThrow(() -> new BadRequestException("Unknown market " + req.marketCode()));
        Instant now = clock.instant();
        String referenceNo = "CLM-%06d".formatted(claims.nextReferenceNumber());

        Claim claim = Claim.submit(UUID.randomUUID(), referenceNo, req.type(), market, req.policyNumber(),
                req.incidentDate(), req.description(), req.claimedAmount(), user.id(), now);
        claim = claims.saveAndFlush(claim);

        if (key != null) {
            // After the claim: the key row references it. A concurrent duplicate fails on the primary key (409).
            idempotencyKeys.saveAndFlush(new IdempotencyKey(key, user.id(), claim.getId(), now));
        }
        return new SubmitResult(mapper.toDetail(claim, false), true);
    }

    public ClaimDetail respondToInformationRequest(CurrentUser user, UUID claimId, UUID requestId,
                                                   InformationResponseRequest req) {
        Claim claim = loadOwnClaim(user, claimId);
        claim.checkVersion(req.version());
        claim.provideInformation(requestId, req.response(), user.id(), clock.instant());
        return save(claim, user);
    }

    public ClaimDetail withdraw(CurrentUser user, UUID claimId, VersionRequest req) {
        Claim claim = loadOwnClaim(user, claimId);
        claim.checkVersion(req.version());
        claim.withdraw(user.id(), clock.instant());
        return save(claim, user);
    }

    // ---------------------------------------------------------------- staff commands

    public ClaimDetail assign(CurrentUser user, UUID claimId, AssignRequest req) {
        user.requireRole(Role.OFFICER, Role.LEAD);
        Claim claim = load(claimId);

        UUID target;
        if (user.is(Role.OFFICER)) {
            // Officers only pick up unassigned claims, for themselves
            if (req.assigneeId() != null && !req.assigneeId().equals(user.id())) {
                throw new ForbiddenException("Officers can only assign claims to themselves");
            }
            if (claim.getAssigneeId() != null) {
                throw new ConflictException("Claim is already assigned");
            }
            target = user.id();
        } else {
            // Leads assign or reassign to any officer
            if (req.assigneeId() == null) {
                throw new BadRequestException("assigneeId is required");
            }
            boolean isOfficer = users.findById(req.assigneeId())
                    .map(u -> u.getRole() == Role.OFFICER)
                    .orElse(false);
            if (!isOfficer) {
                throw new BadRequestException("Assignee " + req.assigneeId() + " is not an officer");
            }
            target = req.assigneeId();
        }

        claim.checkVersion(req.version());
        claim.assign(target, user.id(), clock.instant());
        return save(claim, user);
    }

    public ClaimDetail requestInformation(CurrentUser user, UUID claimId, CreateInformationRequest req) {
        Claim claim = loadForStaffAction(user, claimId);
        claim.checkVersion(req.version());
        claim.requestInformation(req.question(), user.id(), clock.instant());
        return save(claim, user);
    }

    public ClaimDetail changeReserve(CurrentUser user, UUID claimId, ReserveRequest req) {
        Claim claim = loadForStaffAction(user, claimId);
        claim.checkVersion(req.version());
        claim.changeReserve(req.amount(), user.id(), clock.instant());
        return save(claim, user);
    }

    public ClaimDetail decide(CurrentUser user, UUID claimId, DecisionRequest req) {
        Claim claim = loadForStaffAction(user, claimId);
        claim.checkVersion(req.version());
        Instant now = clock.instant();
        switch (req.outcome()) {
            case APPROVE -> {
                if (req.approvedAmount() == null) {
                    throw new BadRequestException("approvedAmount is required to approve");
                }
                claim.approve(req.approvedAmount(), user.id(), now);
            }
            case REJECT -> claim.reject(req.reason(), user.id(), now);
        }
        return save(claim, user);
    }

    public ClaimDetail settle(CurrentUser user, UUID claimId, VersionRequest req) {
        Claim claim = loadForStaffAction(user, claimId);
        claim.checkVersion(req.version());
        claim.settle(user.id(), clock.instant());
        return save(claim, user);
    }

    // ---------------------------------------------------------------- helpers

    private Claim load(UUID claimId) {
        return claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim " + claimId + " not found"));
    }

    /** Claimant commands. Another claimant's claim is reported as 404, so claim ids cannot be probed. */
    private Claim loadOwnClaim(CurrentUser user, UUID claimId) {
        user.requireRole(Role.CLAIMANT);
        Claim claim = load(claimId);
        if (!claim.isOwnedBy(user.id())) {
            throw new NotFoundException("Claim " + claimId + " not found");
        }
        return claim;
    }

    /** Assessment commands: a lead on any claim, an officer only on claims assigned to them. */
    private Claim loadForStaffAction(CurrentUser user, UUID claimId) {
        user.requireRole(Role.OFFICER, Role.LEAD);
        Claim claim = load(claimId);
        if (user.is(Role.OFFICER) && !claim.isAssignedTo(user.id())) {
            throw new ForbiddenException("Claim is not assigned to you");
        }
        return claim;
    }

    private ClaimDetail save(Claim claim, CurrentUser user) {
        return mapper.toDetail(claims.saveAndFlush(claim), user.isStaff());
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return trimmed;
    }
}
