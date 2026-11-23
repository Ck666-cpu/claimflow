package com.claimflow.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code claims.events}. Delivery is at-least-once, so handling is idempotent on eventId.
 * The offset is committed by the container only after this method returns (auto-commit is off);
 * an exception goes to the DefaultErrorHandler (retries, then claims.events.DLT).
 */
@Component
class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);

    private final JsonMapper jsonMapper;
    private final NotificationService notificationService;

    ClaimEventListener(JsonMapper jsonMapper, NotificationService notificationService) {
        this.jsonMapper = jsonMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${claimflow.events-topic}", groupId = "notification-service")
    void onClaimEvent(ConsumerRecord<String, String> record) {
        ClaimEventMessage event = parse(record);
        try {
            boolean stored = notificationService.record(event);
            log.debug("Event {} ({}) for claim {}: notification {}", event.eventId(), event.eventType(),
                    event.claimId(), stored ? "stored" : "not needed");
        } catch (DataIntegrityViolationException e) {
            // Another delivery of the same event won the race to the unique event_id
            log.info("Duplicate event {} ({}) detected on insert, skipping", event.eventId(), event.eventType());
        }
    }

    /** Malformed records throw here; the error handler sends them to the DLT without retrying. */
    private ClaimEventMessage parse(ConsumerRecord<String, String> record) {
        ClaimEventMessage event = jsonMapper.readValue(record.value(), ClaimEventMessage.class);
        if (event.eventId() == null || event.eventType() == null || event.claimId() == null) {
            throw new IllegalArgumentException("Record at " + record.topic() + "-" + record.partition() + "@"
                    + record.offset() + " is missing eventId, eventType or claimId");
        }
        return event;
    }
}
