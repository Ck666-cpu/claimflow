package com.claimflow.notification;

/** Maps to HTTP 404. */
class NotFoundException extends RuntimeException {

    NotFoundException(String message) {
        super(message);
    }
}
