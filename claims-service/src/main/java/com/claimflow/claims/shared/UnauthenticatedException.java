package com.claimflow.claims.shared;

/** Maps to HTTP 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
