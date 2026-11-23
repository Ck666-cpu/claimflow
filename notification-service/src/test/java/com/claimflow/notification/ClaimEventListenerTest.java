package com.claimflow.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimEventListenerTest {

    private static final String TOPIC = "claims.events";
    private static final UUID CLAIMANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock NotificationRepository repository;

    private ClaimEventListener listener;

    @BeforeEach
    void setUp() {
        // Production code injects Boot's JsonMapper; a plain one parses the same envelope in a unit test
        JsonMapper jsonMapper = JsonMapper.builder().build();
        listener = new ClaimEventListener(jsonMapper, new NotificationService(repository, new NotificationFactory()));
    }

    /** The exact shape claims-service puts on the topic (taken from a real record). */
    private static ConsumerRecord<String, String> submitted(UUID eventId, UUID claimId) {
        String json = """
                {"eventId":"%s","eventType":"ClaimSubmitted","schemaVersion":1,"claimId":"%s",\
                "referenceNo":"CLM-001010","claimantId":"%s","actorId":"%s",\
                "occurredAt":"2026-11-23T13:53:07.682096700Z",\
                "payload":{"type":"MOTOR","marketCode":"SG","currency":"SGD","claimedAmount":"850.00"},\
                "someFutureField":"ignored"}
                """.formatted(eventId, claimId, CLAIMANT, CLAIMANT);
        return new ConsumerRecord<>(TOPIC, 0, 42L, claimId.toString(), json);
    }

    @Test
    void sameEventDeliveredTwiceStoresOneNotification() {
        UUID eventId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false, true);

        listener.onClaimEvent(submitted(eventId, claimId));
        listener.onClaimEvent(submitted(eventId, claimId));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
        assertThat(saved.getValue().getUserId()).isEqualTo(CLAIMANT);
        assertThat(saved.getValue().getMessage()).isEqualTo("We received claim CLM-001010. We'll review it shortly.");
    }

    @Test
    void uniqueConstraintRaceIsSwallowedAsDuplicate() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        when(repository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatCode(() -> listener.onClaimEvent(submitted(eventId, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedJsonThrowsSoTheErrorHandlerCanDeadLetterIt() {
        ConsumerRecord<String, String> garbage = new ConsumerRecord<>(TOPIC, 1, 7L, "k", "{not json");

        assertThatThrownBy(() -> listener.onClaimEvent(garbage)).isInstanceOf(JacksonException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recordWithoutEventIdIsRejected() {
        ConsumerRecord<String, String> noId = new ConsumerRecord<>(TOPIC, 1, 8L, "k",
                "{\"eventType\":\"ClaimSubmitted\",\"schemaVersion\":1,\"claimId\":\"" + UUID.randomUUID() + "\"}");

        assertThatThrownBy(() -> listener.onClaimEvent(noId)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
