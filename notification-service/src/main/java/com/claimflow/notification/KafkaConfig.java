package com.claimflow.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Poison-message handling: a failing record is retried 3 times, 1 s apart, then published to
 * {@code claims.events.DLT} (same partition) and skipped, so one bad record never blocks its partition.
 * Records that cannot be parsed are sent to the DLT straight away: retrying will not fix them.
 */
@Configuration
class KafkaConfig {

    static final String DLT_SUFFIX = ".DLT";

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }

    /** Same partition count as claims.events, so a dead record keeps its partition number. */
    @Bean
    NewTopic claimEventsDeadLetterTopic(@Value("${claimflow.events-topic}") String topic,
                                        @Value("${claimflow.events-replication:1}") int replication) {
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(3).replicas(replication).build();
    }
}
