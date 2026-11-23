-- Runs in the "notifications" schema (spring.flyway.default-schema).

CREATE TABLE notifications (
    id          UUID         PRIMARY KEY,
    event_id    UUID         NOT NULL UNIQUE,   -- dedupe: a redelivered event is ignored
    user_id     UUID         NOT NULL,
    claim_id    UUID         NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    message     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    read        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
