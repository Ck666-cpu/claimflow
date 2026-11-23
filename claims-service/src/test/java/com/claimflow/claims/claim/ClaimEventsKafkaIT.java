package com.claimflow.claims.claim;

import com.claimflow.claims.claim.ClaimDtos.SubmitClaimRequest;
import com.claimflow.claims.claim.ClaimDtos.SubmitResult;
import com.claimflow.claims.claim.ClaimEvents.ClaimSubmitted;
import com.claimflow.claims.shared.CurrentUser;
import com.claimflow.claims.shared.Role;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End to end through the outbox: ClaimService saves a claim, Modulith stores the event in
 * event_publication and externalizes it to Kafka after commit. Needs Docker.
 */
@SpringBootTest
@Testcontainers
@RecordApplicationEvents
class ClaimEventsKafkaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

    private static final CurrentUser ALICE = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alice Tan", Role.CLAIMANT);

    @Autowired ClaimService claimService;
    @Autowired JsonMapper jsonMapper;
    @Autowired CompletedEventPublications completedPublications;
    @Autowired ApplicationEvents applicationEvents;

    @Value("${claimflow.events-topic}")
    String topic;

    @Test
    void submittedClaimIsPublishedToKafkaAsTheEnvelope() {
        SubmitResult result = claimService.submit(ALICE, new SubmitClaimRequest(ClaimType.MOTOR, "SG", "POL-IT-1",
                LocalDate.now().minusDays(1), "Integration test claim", new BigDecimal("1234.56")), null);
        UUID claimId = result.claim().id();

        ClaimSubmitted domainEvent = applicationEvents.stream(ClaimSubmitted.class)
                .filter(e -> e.claimId().equals(claimId))
                .findFirst()
                .orElseThrow();

        List<ConsumerRecord<String, String>> forClaim = consumeRecordsWithKey(claimId.toString());

        assertThat(forClaim).hasSize(1);
        ConsumerRecord<String, String> record = forClaim.get(0);
        JsonNode json = jsonMapper.readTree(record.value());
        assertThat(json.get("eventType").asString()).isEqualTo("ClaimSubmitted");
        assertThat(json.get("eventId").asString()).isEqualTo(domainEvent.eventId().toString());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("claimId").asString()).isEqualTo(claimId.toString());
        assertThat(json.get("payload").get("claimedAmount").asString()).isEqualTo("1234.56");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(completedPublications.findAll())
                        .filteredOn(p -> isPublicationOf(p, domainEvent))
                        .singleElement()
                        .satisfies(p -> assertThat(p.getCompletionDate()).isPresent()));
    }

    private static boolean isPublicationOf(EventPublication publication, ClaimSubmitted event) {
        return publication.getEvent() instanceof ClaimSubmitted s && s.eventId().equals(event.eventId());
    }

    /** Reads the topic from the beginning until a record with the key shows up, then a little longer for duplicates. */
    private List<ConsumerRecord<String, String>> consumeRecordsWithKey(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        matching.add(r);
                    }
                });
                return !matching.isEmpty();
            });
            consumer.poll(Duration.ofSeconds(2)).forEach(r -> {
                if (key.equals(r.key())) {
                    matching.add(r);
                }
            });
        }
        return matching;
    }
}
