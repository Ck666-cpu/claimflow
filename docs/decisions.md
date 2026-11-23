# Design decisions

Short records of the decisions the panel is most likely to probe. Each: decision, why, trade-off.

## ADR-1: Two services, not one or many

**Decision.** `claims-service` owns all claim state; `notification-service` only reacts to events.
**Why.** One service would leave Kafka with nothing real to do. More services would spend the time box on plumbing rather than the domain.
**Trade-off.** Metrics are SQL queries inside the claims service. At scale they would move to a read-model service fed by events.

## ADR-2: REST for commands and queries, Kafka for "something happened"

**Decision.** Submit, assign, request info, decide and settle are synchronous REST calls. Every state change also emits a domain event on `claims.events`.
**Why.** The user needs an immediate answer (a reference number, a validation error, a 409 conflict). Side effects such as notifications must not slow down or break the write.
**Trade-off.** Notifications are eventually consistent: they appear a moment after the action.

## ADR-3: Transactional outbox via Spring Modulith

**Decision.** Events are stored in the `event_publication` table in the same transaction as the claim change; Modulith externalizes them to Kafka after commit and re-publishes incomplete ones on restart.
**Why.** "Save, then send to Kafka" loses the event if the app crashes in between, or sends one for a change that rolled back. The outbox closes that gap with no hand-written relay.
**Trade-off.** At-least-once delivery, so consumers must de-duplicate (`notifications.event_id` is unique). Debezium (log-based CDC) is the production-scale alternative.

## ADR-4: Events keyed by claim ID

**Decision.** Kafka record key = `claimId`.
**Why.** All events for one claim land on one partition, so consumers see them in order.

## ADR-5: Explicit command endpoints instead of PATCH status

**Decision.** `POST /claims/{id}/decision`, `/assign`, `/settle` and so on, rather than a generic status update.
**Why.** Each transition's rules and permissions live in one place, and illegal transitions return 409.

## ADR-6: Optimistic locking

**Decision.** `claims.version` column; commands carry the version they read.
**Why.** Two officers acting on the same claim must not silently overwrite each other.

## ADR-7: Reserve as the basis of exposure

**Decision.** Outstanding exposure = sum of `reserve_amount` on open claims, per market and currency.
**Why.** The reserve is the insurer's current estimate of what a claim will cost. It defaults to the claimed amount and is set by the officer during assessment; it becomes the approved amount on approval and zero when the claim closes.

## ADR-8: Backend owns the API contract

**Decision.** springdoc generates OpenAPI from the code; the Angular client is generated from the exported spec.
**Why.** One source of truth; frontend and backend cannot drift.

## ADR-9: Stubbed authentication

**Decision.** Seeded users and an `X-User-Id` header; role checks on every endpoint.
**Why.** Real OIDC is out of the time box. Authorization rules (who may do what) are still enforced and tested.

## ADR-10: Integration envelope separate from domain events

**Decision.** Kafka carries `ClaimEventMessage` (eventId, eventType, schemaVersion, claimId, referenceNo, claimantId, actorId, occurredAt, payload), mapped from the internal `ClaimEvents.*` records at the externalization boundary. Consumers parse their own copy of the envelope; no Java classes are shared between services. Details: [`events.md`](events.md).
**Why.** Domain events are shaped for the aggregate and change when the domain model is refactored; the envelope is a published contract that changes only on purpose. `schemaVersion` lets the contract evolve: additive fields keep version 1 (consumers ignore unknown fields), a breaking change bumps the version so consumers can handle both. Money is a two-decimal string, so no consumer loses precision to JSON numbers.
**Trade-off.** One mapping to maintain per event type (an exhaustive `switch` over the sealed interface, so a new event cannot be forgotten). No schema registry: the contract is documented and tested, not enforced by the broker.

## ADR-11: Scheduled resubmission of incomplete publications

**Decision.** `IncompleteEventResubmitter` calls Modulith's `resubmitIncompletePublicationsOlderThan(30 s)` every 30 s, in addition to republishing outstanding events on restart.
**Why.** A broker outage longer than the producer's own timeout leaves outbox rows incomplete. Republish-on-restart alone would need someone to restart the service; the scheduled job heals the gap on its own within about 30 s of Kafka coming back (verified in the failure demo in `events.md`).
**Trade-off.** More duplicates: a resubmission can race a slow original send, so the same event may be published twice. That is already within the at-least-once contract (consumers de-duplicate on `eventId`). With several instances, each would resubmit the same rows; acceptable for now, a scheduler lock (e.g. ShedLock) is the fix at scale.
