package com.claimflow.claims.claim;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle. {@link #allowedNext()} is the single source of truth for legal transitions.
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    APPROVED,
    SETTLED,
    REJECTED,
    WITHDRAWN;

    /** Claims in these states count toward workload and outstanding exposure. */
    public static final Set<ClaimStatus> OPEN =
            Collections.unmodifiableSet(EnumSet.of(SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, APPROVED));

    public Set<ClaimStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> Collections.unmodifiableSet(EnumSet.of(UNDER_REVIEW, WITHDRAWN));
            case UNDER_REVIEW -> Collections.unmodifiableSet(EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED, WITHDRAWN));
            case INFO_REQUESTED -> Collections.unmodifiableSet(EnumSet.of(UNDER_REVIEW, WITHDRAWN));
            case APPROVED -> Collections.unmodifiableSet(EnumSet.of(SETTLED));
            case SETTLED, REJECTED, WITHDRAWN -> Collections.emptySet();
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedNext().contains(target);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** The officer is actively assessing the claim: reserve may change. */
    public boolean isUnderAssessment() {
        return this == UNDER_REVIEW || this == INFO_REQUESTED;
    }
}
