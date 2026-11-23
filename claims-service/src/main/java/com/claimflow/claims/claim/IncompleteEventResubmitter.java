package com.claimflow.claims.claim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Re-sends outbox entries that are still incomplete after 30 s, e.g. because Kafka was down when the
 * claim was saved (ADR-11). Lets a broker outage heal without restarting the service. At-least-once:
 * a publication may be sent twice, consumers de-duplicate on eventId.
 */
@Component
class IncompleteEventResubmitter {

    private static final Logger log = LoggerFactory.getLogger(IncompleteEventResubmitter.class);
    private static final Duration MIN_AGE = Duration.ofSeconds(30);

    private final IncompleteEventPublications incompletePublications;

    IncompleteEventResubmitter(IncompleteEventPublications incompletePublications) {
        this.incompletePublications = incompletePublications;
    }

    @Scheduled(fixedDelayString = "${claimflow.outbox.resubmit-interval:PT30S}",
            initialDelayString = "${claimflow.outbox.resubmit-interval:PT30S}")
    void resubmit() {
        log.debug("Resubmitting event publications incomplete for more than {}", MIN_AGE);
        incompletePublications.resubmitIncompletePublicationsOlderThan(MIN_AGE);
    }
}
