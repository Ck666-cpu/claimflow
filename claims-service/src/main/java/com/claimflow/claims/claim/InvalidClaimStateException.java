package com.claimflow.claims.claim;

import com.claimflow.claims.shared.ConflictException;

/**
 * A command that is not a status change (assign, change reserve) was refused because of the
 * claim's current status. Status changes refused by the table throw {@link InvalidTransitionException}.
 */
public class InvalidClaimStateException extends ConflictException {

    public InvalidClaimStateException(String action, ClaimStatus status) {
        super("Cannot " + action + " a claim in status " + status);
    }
}
