package com.claimflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores notifications from claim events, and serves them to their recipients. */
@Service
class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationFactory factory;

    NotificationService(NotificationRepository notifications, NotificationFactory factory) {
        this.notifications = notifications;
        this.factory = factory;
    }

    /**
     * Idempotent: an event already seen is skipped. Two deliveries racing past the check make the second
     * insert fail on the unique event_id; that DataIntegrityViolationException propagates to the caller,
     * which treats it as a duplicate (catching it here would leave this transaction rollback-only).
     *
     * @return true if a notification was stored
     */
    @Transactional
    public boolean record(ClaimEventMessage event) {
        if (notifications.existsByEventId(event.eventId())) {
            log.info("Duplicate event {} ({}), already handled", event.eventId(), event.eventType());
            return false;
        }
        Optional<Notification> notification = factory.create(event);
        notification.ifPresent(notifications::saveAndFlush);
        return notification.isPresent();
    }

    @Transactional(readOnly = true)
    public List<NotificationView> mine(UUID userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationView::of).toList();
    }

    /** 404 if the notification does not exist or belongs to someone else (ids cannot be probed). */
    @Transactional
    public NotificationView markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Notification " + notificationId + " not found"));
        notification.markRead();
        return NotificationView.of(notification);
    }

    /** What the API returns; the entity never leaves this class. */
    record NotificationView(UUID id, UUID claimId, String eventType, String message, Instant createdAt,
                            boolean read) {

        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getClaimId(), n.getEventType(), n.getMessage(),
                    n.getCreatedAt(), n.isRead());
        }
    }
}
