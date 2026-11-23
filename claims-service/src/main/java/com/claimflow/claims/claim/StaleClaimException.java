package com.claimflow.claims.claim;

import com.claimflow.claims.shared.ConflictException;

public class StaleClaimException extends ConflictException {

    public StaleClaimException(long expected, long current) {
        super("Claim was changed by someone else: expected version " + expected
                + " but current version is " + current + ". Reload and try again.");
    }
}
