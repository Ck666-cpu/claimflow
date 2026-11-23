package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.AssignRequest;
import com.claimflow.claims.claim.ClaimDtos.ClaimDetail;
import com.claimflow.claims.claim.ClaimDtos.ClaimSummary;
import com.claimflow.claims.claim.ClaimDtos.CreateInformationRequest;
import com.claimflow.claims.claim.ClaimDtos.DecisionRequest;
import com.claimflow.claims.claim.ClaimDtos.InformationResponseRequest;
import com.claimflow.claims.claim.ClaimDtos.ReserveRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitClaimRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitResult;
import com.claimflow.claims.claim.ClaimDtos.VersionRequest;
import com.claimflow.claims.shared.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Claims API. One endpoint per command (ADR-5); no generic status update.
 * Every command body carries the {@code version} the client last read (ADR-6).
 */
@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
class ClaimController {

    private final ClaimService service;
    private final ClaimQueryService queries;

    ClaimController(ClaimService service, ClaimQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Submit a claim (claimant). Replaying an Idempotency-Key returns the original claim with 200.")
    public ResponseEntity<ClaimDetail> submit(
            CurrentUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitClaimRequest request) {
        SubmitResult result = service.submit(user, request, idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(result.claim());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.claim().id()).toUri();
        return ResponseEntity.created(location).body(result.claim());
    }

    @GetMapping("/mine")
    @Operation(summary = "My claims, newest first (claimant)")
    public List<ClaimSummary> mine(CurrentUser user) {
        return queries.myClaims(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Claim detail with history and information requests (staff: any claim; claimant: own)")
    public ClaimDetail detail(CurrentUser user, @PathVariable UUID id) {
        return queries.detail(id, user);
    }

    @GetMapping
    @Operation(summary = "Search claims (staff): unassigned=true for the work queue, assignee=me|{userId}, "
            + "status filter (default: open statuses)")
    public List<ClaimSummary> search(
            CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) String assignee,
            @RequestParam(name = "status", required = false) Set<ClaimStatus> statuses) {
        return queries.search(user, unassigned, assignee, statuses);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign a claim: an officer picks it up for themselves; a lead assigns or reassigns to an officer")
    public ClaimDetail assign(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return service.assign(user, id, request);
    }

    @PostMapping("/{id}/info-requests")
    @Operation(summary = "Ask the claimant for more information (assigned officer or lead)")
    public ClaimDetail requestInformation(CurrentUser user, @PathVariable UUID id,
                                          @Valid @RequestBody CreateInformationRequest request) {
        return service.requestInformation(user, id, request);
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    @Operation(summary = "Answer an open information request (owning claimant)")
    public ClaimDetail respond(CurrentUser user, @PathVariable UUID id, @PathVariable UUID requestId,
                               @Valid @RequestBody InformationResponseRequest request) {
        return service.respondToInformationRequest(user, id, requestId, request);
    }

    @PutMapping("/{id}/reserve")
    @Operation(summary = "Set the reserve during assessment (assigned officer or lead)")
    public ClaimDetail changeReserve(CurrentUser user, @PathVariable UUID id,
                                     @Valid @RequestBody ReserveRequest request) {
        return service.changeReserve(user, id, request);
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Approve (with approvedAmount) or reject (with reason) a claim under review")
    public ClaimDetail decide(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(user, id, request);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Mark an approved claim as settled (assigned officer or lead)")
    public ClaimDetail settle(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.settle(user, id, request);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw a claim before a decision (owning claimant)")
    public ClaimDetail withdraw(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.withdraw(user, id, request);
    }
}
