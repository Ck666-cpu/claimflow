package com.claimflow.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A message for one user about one claim event. {@code eventId} is unique: one notification per event. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean read;

    protected Notification() {
    }

    Notification(UUID id, UUID eventId, UUID userId, UUID claimId, String eventType, String message,
                 Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.claimId = claimId;
        this.eventType = eventType;
        this.message = message;
        this.createdAt = createdAt;
        this.read = false;
    }

    void markRead() {
        this.read = true;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public UUID getClaimId() { return claimId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
}
