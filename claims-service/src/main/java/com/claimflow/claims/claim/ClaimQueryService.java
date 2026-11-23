package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.ClaimDetail;
import com.claimflow.claims.claim.ClaimDtos.ClaimSummary;
import com.claimflow.claims.shared.BadRequestException;
import com.claimflow.claims.shared.CurrentUser;
import com.claimflow.claims.shared.NotFoundException;
import com.claimflow.claims.shared.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Claim reads. Lists are unpaged for now; paging is the first change needed for real volumes. */
@Service
@Transactional(readOnly = true)
class ClaimQueryService {

    private final ClaimRepository claims;
    private final ClaimMapper mapper;

    ClaimQueryService(ClaimRepository claims, ClaimMapper mapper) {
        this.claims = claims;
        this.mapper = mapper;
    }

    /** The caller's own claims, newest first. */
    public List<ClaimSummary> myClaims(CurrentUser user) {
        user.requireRole(Role.CLAIMANT);
        return claims.findByClaimantIdOrderBySubmittedAtDesc(user.id()).stream()
                .map(c -> mapper.toSummary(c, false))
                .toList();
    }

    /**
     * Staff search.
     * {@code unassigned=true}: the work queue (SUBMITTED, nobody assigned).
     * {@code assignee=me|<uuid>}: that officer's claims, OPEN unless statuses are given.
     * Otherwise: claims in the given statuses, OPEN by default.
     */
    public List<ClaimSummary> search(CurrentUser user, boolean unassigned, String assignee,
                                     Set<ClaimStatus> statuses) {
        user.requireRole(Role.OFFICER, Role.LEAD);
        Set<ClaimStatus> wanted = statuses == null || statuses.isEmpty() ? ClaimStatus.OPEN : statuses;

        List<Claim> result;
        if (unassigned) {
            result = claims.findByStatusAndAssigneeIdIsNullOrderBySubmittedAtAsc(ClaimStatus.SUBMITTED);
        } else if (assignee != null && !assignee.isBlank()) {
            result = claims.findByAssigneeIdAndStatusInOrderBySubmittedAtAsc(parseAssignee(user, assignee), wanted);
        } else {
            result = claims.findByStatusInOrderBySubmittedAtAsc(wanted);
        }
        return result.stream().map(c -> mapper.toSummary(c, true)).toList();
    }

    /** Staff see any claim; claimants only their own (404 otherwise, so ids cannot be probed). */
    public ClaimDetail detail(UUID id, CurrentUser user) {
        Claim claim = claims.findById(id).orElseThrow(() -> new NotFoundException("Claim " + id + " not found"));
        if (!user.isStaff() && !claim.isOwnedBy(user.id())) {
            throw new NotFoundException("Claim " + id + " not found");
        }
        return mapper.toDetail(claim, user.isStaff());
    }

    private static UUID parseAssignee(CurrentUser user, String assignee) {
        if ("me".equalsIgnoreCase(assignee.trim())) {
            return user.id();
        }
        try {
            return UUID.fromString(assignee.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("assignee must be 'me' or a user id");
        }
    }
}
