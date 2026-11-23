package com.claimflow.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * This service's own reading of the {@code claims.events} envelope (schemaVersion 1). Deliberately a
 * local copy, not a class shared with claims-service: services agree on JSON, not on Java types.
 * Unknown fields are ignored so the producer can add fields without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ClaimEventMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID claimId,
        String referenceNo,
        UUID claimantId,
        UUID actorId,
        Instant occurredAt,
        Map<String, Object> payload) {

    /** A payload field as text, or null if absent. */
    String payloadText(String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : value.toString();
    }
}
