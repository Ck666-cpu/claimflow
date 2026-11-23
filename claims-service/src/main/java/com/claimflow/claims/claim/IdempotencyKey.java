package com.claimflow.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Remembers which claim a claimant's Idempotency-Key produced, so a retried submit returns
 * the original claim instead of creating a duplicate. The key is scoped per claimant.
 */
@Entity
@Immutable
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Pk.class)
class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Id
    @Column(name = "claimant_id")
    private UUID claimantId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    IdempotencyKey(String idempotencyKey, UUID claimantId, UUID claimId, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.claimantId = claimantId;
        this.claimId = claimId;
        this.createdAt = createdAt;
    }

    String getIdempotencyKey() { return idempotencyKey; }
    UUID getClaimantId() { return claimantId; }
    UUID getClaimId() { return claimId; }
    Instant getCreatedAt() { return createdAt; }

    /** Composite primary key (idempotency_key, claimant_id). */
    static class Pk implements Serializable {

        private String idempotencyKey;
        private UUID claimantId;

        protected Pk() {
        }

        Pk(String idempotencyKey, UUID claimantId) {
            this.idempotencyKey = idempotencyKey;
            this.claimantId = claimantId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pk other
                    && Objects.equals(idempotencyKey, other.idempotencyKey)
                    && Objects.equals(claimantId, other.claimantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idempotencyKey, claimantId);
        }
    }
}
