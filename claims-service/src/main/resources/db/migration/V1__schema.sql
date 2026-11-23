-- Claims service schema. The Modulith event_publication (outbox) table is created
-- by spring.modulith.events.jdbc.schema-initialization, not here.

CREATE TABLE markets (
    code      VARCHAR(2)  PRIMARY KEY,
    name      VARCHAR(50) NOT NULL,
    currency  VARCHAR(3)  NOT NULL
);

CREATE TABLE users (
    id     UUID         PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    email  VARCHAR(150) NOT NULL UNIQUE,
    role   VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'LEAD')),
    team   VARCHAR(50)
);

CREATE TABLE claims (
    id               UUID          PRIMARY KEY,
    reference_no     VARCHAR(20)   NOT NULL UNIQUE,
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('MOTOR', 'PROPERTY')),
    market_code      VARCHAR(2)    NOT NULL REFERENCES markets (code),
    currency         VARCHAR(3)    NOT NULL,
    policy_number    VARCHAR(50)   NOT NULL,
    incident_date    DATE          NOT NULL,
    description      TEXT          NOT NULL,
    claimed_amount   NUMERIC(14,2) NOT NULL CHECK (claimed_amount > 0),
    reserve_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    approved_amount  NUMERIC(14,2),
    status           VARCHAR(20)   NOT NULL CHECK (status IN
                       ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'APPROVED',
                        'SETTLED', 'REJECTED', 'WITHDRAWN')),
    rejection_reason TEXT,
    claimant_id      UUID          NOT NULL REFERENCES users (id),
    assignee_id      UUID          REFERENCES users (id),
    submitted_at     TIMESTAMPTZ   NOT NULL,
    decided_at       TIMESTAMPTZ,
    settled_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_claims_status_submitted ON claims (status, submitted_at);
CREATE INDEX idx_claims_assignee_status  ON claims (assignee_id, status);
CREATE INDEX idx_claims_claimant         ON claims (claimant_id);

CREATE TABLE claim_status_history (
    id           BIGSERIAL   PRIMARY KEY,
    claim_id     UUID        NOT NULL REFERENCES claims (id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    actor_id     UUID        NOT NULL REFERENCES users (id),
    reason       TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_history_claim ON claim_status_history (claim_id, occurred_at);

CREATE TABLE information_requests (
    id            UUID        PRIMARY KEY,
    claim_id      UUID        NOT NULL REFERENCES claims (id),
    question      TEXT        NOT NULL,
    response      TEXT,
    requested_by  UUID        NOT NULL REFERENCES users (id),
    requested_at  TIMESTAMPTZ NOT NULL,
    responded_at  TIMESTAMPTZ,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'ANSWERED'))
);

-- At most one open information request per claim
CREATE UNIQUE INDEX uq_info_request_open ON information_requests (claim_id) WHERE status = 'OPEN';

CREATE TABLE reserve_changes (
    id          BIGSERIAL     PRIMARY KEY,
    claim_id    UUID          NOT NULL REFERENCES claims (id),
    old_amount  NUMERIC(14,2) NOT NULL,
    new_amount  NUMERIC(14,2) NOT NULL,
    actor_id    UUID          NOT NULL REFERENCES users (id),
    occurred_at TIMESTAMPTZ   NOT NULL
);

-- Idempotent claim submission: same key from the same claimant returns the same claim
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(100) NOT NULL,
    claimant_id     UUID         NOT NULL REFERENCES users (id),
    claim_id        UUID         NOT NULL REFERENCES claims (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (idempotency_key, claimant_id)
);

CREATE SEQUENCE claim_reference_seq START 1001;
