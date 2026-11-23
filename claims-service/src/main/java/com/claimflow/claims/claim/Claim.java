package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimEvents.ClaimApproved;
import com.claimflow.claims.claim.ClaimEvents.ClaimAssigned;
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
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Claim aggregate. Enforces <em>what</em> may happen from each state (via {@link ClaimStatus}),
 * never <em>who</em> may do it: role and ownership checks live in the service layer.
 * Every command registers a domain event that Spring Data publishes when the claim is saved.
 */
@Entity
@Table(name = "claims")
public class Claim extends AbstractAggregateRoot<Claim> {

    @Id
    private UUID id;

    @Column(name = "reference_no", nullable = false, unique = true, length = 20)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType type;

    @Column(name = "market_code", nullable = false, length = 2)
    private String marketCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "policy_number", nullable = false, length = 50)
    private String policyNumber;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "claimed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "reserve_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "approved_amount", precision = 14, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Null until first persisted, so Spring Data treats a new claim as new. */
    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    @OrderBy("occurredAt ASC")
    private List<StatusHistoryEntry> history = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    @OrderBy("requestedAt ASC")
    private List<InformationRequest> informationRequests = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    @OrderBy("occurredAt ASC")
    private List<ReserveChange> reserveChanges = new ArrayList<>();

    protected Claim() {
    }

    // ---------------------------------------------------------------- factory

    public static Claim submit(UUID id, String referenceNo, ClaimType type, Market market, String policyNumber,
                               LocalDate incidentDate, String description, BigDecimal claimedAmount,
                               UUID claimantId, Instant now) {
        if (claimedAmount == null || claimedAmount.signum() <= 0) {
            throw new BadRequestException("Claimed amount must be greater than zero");
        }
        if (incidentDate == null || incidentDate.isAfter(LocalDate.ofInstant(now, ZoneOffset.UTC))) {
            throw new BadRequestException("Incident date cannot be in the future");
        }

        Claim claim = new Claim();
        claim.id = id;
        claim.referenceNo = referenceNo;
        claim.type = type;
        claim.marketCode = market.getCode();
        claim.currency = market.getCurrency();
        claim.policyNumber = policyNumber;
        claim.incidentDate = incidentDate;
        claim.description = description;
        claim.claimedAmount = claimedAmount;
        claim.reserveAmount = claimedAmount;
        claim.status = ClaimStatus.SUBMITTED;
        claim.claimantId = claimantId;
        claim.submittedAt = now;
        claim.updatedAt = now;
        claim.history.add(new StatusHistoryEntry(null, ClaimStatus.SUBMITTED, claimantId, null, now));

        claim.registerEvent(new ClaimSubmitted(UUID.randomUUID(), id, referenceNo, claimantId, claimantId, now,
                type, claim.marketCode, claim.currency, claimedAmount));
        return claim;
    }

    // ---------------------------------------------------------------- commands

    /** Pick up (from SUBMITTED this starts the review) or reassign an open claim. */
    public void assign(UUID officerId, UUID actorId, Instant now) {
        if (!ClaimStatus.OPEN.contains(status)) {
            throw new InvalidClaimStateException("assign", status);
        }
        if (Objects.equals(officerId, assigneeId)) {
            throw new ConflictException("Claim is already assigned to this officer");
        }
        if (status == ClaimStatus.SUBMITTED) {
            transitionTo(ClaimStatus.UNDER_REVIEW, actorId, "Assigned to " + officerId, now);
        }
        assigneeId = officerId;
        updatedAt = now;
        registerEvent(new ClaimAssigned(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now, officerId));
    }

    /** Ask the claimant a question. Returns the new request's id. */
    public UUID requestInformation(String question, UUID actorId, Instant now) {
        if (isBlank(question)) {
            throw new BadRequestException("A question is required");
        }
        // Only UNDER_REVIEW -> INFO_REQUESTED is allowed, so a second open request is impossible.
        transitionTo(ClaimStatus.INFO_REQUESTED, actorId, question, now);
        UUID requestId = UUID.randomUUID();
        informationRequests.add(new InformationRequest(requestId, question, actorId, now));
        registerEvent(new InformationRequested(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now,
                requestId, question));
        return requestId;
    }

    /** The claimant answers an open request; review resumes. */
    public void provideInformation(UUID requestId, String response, UUID actorId, Instant now) {
        if (isBlank(response)) {
            throw new BadRequestException("A response is required");
        }
        InformationRequest request = informationRequests.stream()
                .filter(r -> r.getId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Information request " + requestId + " not found"));
        if (!request.isOpen()) {
            throw new ConflictException("Information request " + requestId + " has already been answered");
        }
        transitionTo(ClaimStatus.UNDER_REVIEW, actorId, response, now);
        request.answer(response, now);
        registerEvent(new InformationProvided(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now,
                requestId, assigneeId));
    }

    /** The officer's estimate of what the claim will cost. Only during assessment. */
    public void changeReserve(BigDecimal amount, UUID actorId, Instant now) {
        if (amount == null || amount.signum() < 0) {
            throw new BadRequestException("Reserve must be zero or more");
        }
        if (!status.isUnderAssessment()) {
            throw new InvalidClaimStateException("change the reserve of", status);
        }
        BigDecimal old = reserveAmount;
        if (old.compareTo(amount) != 0) {
            recordReserve(amount, actorId, now);
            updatedAt = now;
            registerEvent(new ReserveChanged(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now,
                    marketCode, currency, old, amount));
        }
    }

    public void approve(BigDecimal amount, UUID actorId, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Approved amount must be greater than zero");
        }
        transitionTo(ClaimStatus.APPROVED, actorId, "Approved " + currency + " " + amount.toPlainString(), now);
        approvedAmount = amount;
        decidedAt = now;
        recordReserve(amount, actorId, now);
        registerEvent(new ClaimApproved(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now,
                currency, amount));
    }

    public void reject(String reason, UUID actorId, Instant now) {
        if (isBlank(reason)) {
            throw new BadRequestException("A rejection reason is required");
        }
        transitionTo(ClaimStatus.REJECTED, actorId, reason, now);
        rejectionReason = reason;
        decidedAt = now;
        recordReserve(BigDecimal.ZERO, actorId, now);
        registerEvent(new ClaimRejected(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now, reason));
    }

    public void settle(UUID actorId, Instant now) {
        // approvedAmount is null before approval; transitionTo then refuses the move, so build the reason null-safely
        String reason = approvedAmount == null ? null : "Settled " + currency + " " + approvedAmount.toPlainString();
        transitionTo(ClaimStatus.SETTLED, actorId, reason, now);
        settledAt = now;
        recordReserve(BigDecimal.ZERO, actorId, now);
        registerEvent(new ClaimSettled(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now,
                currency, approvedAmount));
    }

    public void withdraw(UUID actorId, Instant now) {
        transitionTo(ClaimStatus.WITHDRAWN, actorId, "Withdrawn by claimant", now);
        recordReserve(BigDecimal.ZERO, actorId, now);
        registerEvent(new ClaimWithdrawn(UUID.randomUUID(), id, referenceNo, claimantId, actorId, now));
    }

    /** Optimistic-lock check against the version the client last read. */
    public void checkVersion(long expected) {
        if (expected != getVersion()) {
            throw new StaleClaimException(expected, getVersion());
        }
    }

    // ---------------------------------------------------------------- internals

    private void transitionTo(ClaimStatus target, UUID actorId, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);
        }
        history.add(new StatusHistoryEntry(status, target, actorId, reason, now));
        status = target;
        updatedAt = now;
    }

    /**
     * Sets the reserve and always writes an audit row, even if the amount is unchanged, so every
     * decision that fixes the reserve has a row naming who made it. {@link #changeReserve} skips
     * calling this when the amount is the same (a no-op edit, not a decision).
     */
    private void recordReserve(BigDecimal amount, UUID actorId, Instant now) {
        reserveChanges.add(new ReserveChange(reserveAmount, amount, actorId, now));
        reserveAmount = amount;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ---------------------------------------------------------------- queries

    public boolean isOwnedBy(UUID userId) {
        return claimantId.equals(userId);
    }

    public boolean isAssignedTo(UUID userId) {
        return assigneeId != null && assigneeId.equals(userId);
    }

    /** Events registered but not yet published. For tests. */
    List<Object> pendingEvents() {
        return List.copyOf(domainEvents());
    }

    public UUID getId() { return id; }
    public String getReferenceNo() { return referenceNo; }
    public ClaimType getType() { return type; }
    public String getMarketCode() { return marketCode; }
    public String getCurrency() { return currency; }
    public String getPolicyNumber() { return policyNumber; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getReserveAmount() { return reserveAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public ClaimStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getClaimantId() { return claimantId; }
    public UUID getAssigneeId() { return assigneeId; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public List<StatusHistoryEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public List<InformationRequest> getInformationRequests() {
        return Collections.unmodifiableList(informationRequests);
    }

    public List<ReserveChange> getReserveChanges() {
        return Collections.unmodifiableList(reserveChanges);
    }
}
