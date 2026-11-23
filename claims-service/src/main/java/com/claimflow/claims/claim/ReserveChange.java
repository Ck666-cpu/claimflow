package com.claimflow.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Audit row for every change of a claim's reserve. Immutable once written. */
@Entity
@Immutable
@Table(name = "reserve_changes")
public class ReserveChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "old_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ReserveChange() {
    }

    ReserveChange(BigDecimal oldAmount, BigDecimal newAmount, UUID actorId, Instant occurredAt) {
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public BigDecimal getOldAmount() { return oldAmount; }
    public BigDecimal getNewAmount() { return newAmount; }
    public UUID getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
}
