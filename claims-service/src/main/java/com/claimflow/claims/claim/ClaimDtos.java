package com.claimflow.claims.claim;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response records for the claims API. Entities never leave the service layer. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record SubmitClaimRequest(
            @NotNull ClaimType type,
            @NotBlank @Size(min = 2, max = 2) String marketCode,
            @NotBlank @Size(max = 50) String policyNumber,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal claimedAmount) {
    }

    /** {@code assigneeId} is optional for an officer picking up a claim; a lead must name one. */
    public record AssignRequest(UUID assigneeId, @NotNull Long version) {
    }

    public record CreateInformationRequest(@NotBlank @Size(max = 4000) String question, @NotNull Long version) {
    }

    public record InformationResponseRequest(@NotBlank @Size(max = 4000) String response, @NotNull Long version) {
    }

    public record ReserveRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull Long version) {
    }

    public record DecisionRequest(
            @NotNull Outcome outcome,
            @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal approvedAmount,
            @Size(max = 4000) String reason,
            @NotNull Long version) {

        public enum Outcome {
            APPROVE,
            REJECT
        }
    }

    public record VersionRequest(@NotNull Long version) {
    }

    // ---------------------------------------------------------------- responses

    /** {@code reserveAmount} is null for claimants: it is the insurer's internal estimate. */
    public record ClaimSummary(
            UUID id,
            String referenceNo,
            ClaimType type,
            String marketCode,
            String currency,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            ClaimStatus status,
            UUID claimantId,
            UUID assigneeId,
            Instant submittedAt,
            Instant updatedAt,
            long ageDays,
            boolean slaBreached,
            long version) {
    }

    /** {@code reserveAmount} is null for claimants: it is the insurer's internal estimate. */
    public record ClaimDetail(
            UUID id,
            String referenceNo,
            ClaimType type,
            String marketCode,
            String currency,
            String policyNumber,
            LocalDate incidentDate,
            String description,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            BigDecimal approvedAmount,
            ClaimStatus status,
            String rejectionReason,
            UUID claimantId,
            UUID assigneeId,
            Instant submittedAt,
            Instant decidedAt,
            Instant settledAt,
            Instant updatedAt,
            long ageDays,
            boolean slaBreached,
            Set<ClaimStatus> allowedNext,
            long version,
            List<HistoryItem> history,
            List<InformationRequestItem> informationRequests) {
    }

    public record HistoryItem(
            ClaimStatus fromStatus,
            ClaimStatus toStatus,
            UUID actorId,
            String reason,
            Instant occurredAt) {
    }

    public record InformationRequestItem(
            UUID id,
            String question,
            String response,
            InformationRequest.Status status,
            UUID requestedBy,
            Instant requestedAt,
            Instant respondedAt) {
    }

    /** {@code created} is false when an Idempotency-Key replay returned the original claim. */
    public record SubmitResult(ClaimDetail claim, boolean created) {
    }
}
