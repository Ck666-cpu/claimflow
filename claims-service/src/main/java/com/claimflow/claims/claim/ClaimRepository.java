package com.claimflow.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** Next value for the human-readable reference, formatted as CLM-%06d by the service. */
    @Query(value = "select nextval('claim_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    List<Claim> findByClaimantIdOrderBySubmittedAtDesc(UUID claimantId);

    /** The work queue: claims nobody has picked up yet, oldest first. */
    List<Claim> findByStatusAndAssigneeIdIsNullOrderBySubmittedAtAsc(ClaimStatus status);

    List<Claim> findByAssigneeIdAndStatusInOrderBySubmittedAtAsc(UUID assigneeId, Collection<ClaimStatus> statuses);

    List<Claim> findByStatusInOrderBySubmittedAtAsc(Collection<ClaimStatus> statuses);
}
