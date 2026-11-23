package com.claimflow.claims.claim;

import com.claimflow.claims.shared.ConflictException;

public class InvalidTransitionException extends ConflictException {

    public InvalidTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to);
    }
}
