package com.claimflow.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A question from the officer to the claimant, and the claimant's answer. */
@Entity
@Table(name = "information_requests")
public class InformationRequest {

    public enum Status {
        OPEN,
        ANSWERED
    }

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(columnDefinition = "text")
    private String response;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    protected InformationRequest() {
    }

    InformationRequest(UUID id, String question, UUID requestedBy, Instant requestedAt) {
        this.id = id;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.status = Status.OPEN;
    }

    void answer(String response, Instant at) {
        this.response = response;
        this.respondedAt = at;
        this.status = Status.ANSWERED;
    }

    boolean isOpen() {
        return status == Status.OPEN;
    }

    public UUID getId() { return id; }
    public String getQuestion() { return question; }
    public String getResponse() { return response; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public Status getStatus() { return status; }
}
