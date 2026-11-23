package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimEvents.ClaimEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

/**
 * Sends every {@link ClaimEvent} to Kafka through Spring Modulith's event publication registry
 * (transactional outbox, ADR-3). The record value is the {@link ClaimEventMessage} envelope as JSON
 * (ADR-10); the record key is the claim id, so all events of one claim stay ordered on one partition (ADR-4).
 * Scheduling is enabled here for {@link IncompleteEventResubmitter}.
 */
@Configuration
@EnableScheduling
class ClaimEventsExternalization {

    // No constructor injection here: Modulith looks up EventExternalizationConfiguration beans very early,
    // before this configuration class could be constructed with arguments. Values go on the @Bean methods.

    @Bean
    EventExternalizationConfiguration claimEventsExternalizationConfiguration(
            @Value("${claimflow.events-topic:claims.events}") String topic) {
        // Modulith 2.0 resolves the route from the original domain event, then maps it to the envelope;
        // headers are computed from the mapped envelope.
        return EventExternalizationConfiguration.externalizing()
                .select(event -> event instanceof ClaimEvent)
                .route(ClaimEvent.class, event -> RoutingTarget.forTarget(topic).andKey(event.claimId().toString()))
                .mapping(ClaimEvent.class, ClaimEventMessage::from)
                .headers(ClaimEventMessage.class, message -> Map.of(
                        "eventType", message.eventType(),
                        "eventId", message.eventId().toString()))
                .build();
    }

    /**
     * Created on startup by Spring Boot's KafkaAdmin if missing. Local: one broker, replication 1.
     * Production uses replication 3 with min.insync.replicas=2, so acks=all survives one broker down.
     */
    @Bean
    NewTopic claimEventsTopic(@Value("${claimflow.events-topic:claims.events}") String topic,
                              @Value("${claimflow.events-replication:1}") int replication) {
        return TopicBuilder.name(topic).partitions(3).replicas(replication).build();
    }
}
