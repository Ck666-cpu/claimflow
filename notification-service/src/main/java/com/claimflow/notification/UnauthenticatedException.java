package com.claimflow.notification;

/** Maps to HTTP 401. */
class UnauthenticatedException extends RuntimeException {

    UnauthenticatedException(String message) {
        super(message);
    }
}
