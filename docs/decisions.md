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

## ADR-12: Metrics as SQL aggregates in the claims service

**Decision.** Workload and exposure (`/api/v1/metrics/*`) are a read model inside `claims-service`: the `metrics` module runs plain SQL aggregates (`JdbcClient`) over the claims tables on each request. No entities are loaded, no extra tables, "now" is a query parameter. The module reads `claim` types; `claim` never depends on `metrics` (checked by `ModularityTests`).
**Why.** Correct and real time by construction: the figures come from the same rows the commands just wrote, so there is no lag and nothing to reconcile. A handful of grouped queries answers the manager's question at this scale in milliseconds, and it was quick to build and test against the seed data.
**Trade-off.** Metrics share the claims database: heavy dashboard traffic would compete with the write path, and every request recomputes from scratch (no caching). Money is grouped by market currency and never converted, so there is no single cross-market total.
**Scale-up path.** An event-fed read model in its own service: consume `claims.events` (the envelope carries market, currency, the claimed amount and manual reserve changes; approve, reject, settle and withdraw reset the reserve without a `ReserveChanged`, so the read model derives those from the decision events, or the producer starts emitting them), maintain pre-aggregated tables, and serve dashboards from there. That trades real-time for eventual consistency and frees the claims database.

## ADR-13: UI driven by `allowedNext`

**Decision.** The Angular app shows an action only if the claim's `allowedNext` (from `ClaimDetail`) contains the target status and the user's role permits it (lead: any claim; officer: claims assigned to them; claimant: own claim). It never encodes the transition table itself. Every command sends the version of the claim on screen; the returned `ClaimDetail` replaces it, so the next command carries the new version.
**Why.** One source of truth for the lifecycle: `ClaimStatus.allowedNext()` on the server. A rule change in the backend reaches the UI without a frontend change, and the two cannot disagree about what is possible. The backend stays the authority anyway: it re-checks every command and answers 409 (wrong state or stale version) or 403, which the UI shows and recovers from by reloading the claim. A component test checks that Settle appears only when `allowedNext` contains `SETTLED`, even for an APPROVED claim.
**Trade-off.** Role rules ("officers act only on their own claims") still live in two places, UI and service, because the API does not return per-user permissions. A `permittedActions` field computed for the caller would remove that duplication.

## ADR-14: Polling over server push

**Decision.** Lists, claim pages, the notification bell and the dashboard re-fetch every 15 s while the tab is visible, and immediately when the tab becomes visible again. No WebSockets or Server-Sent Events.
**Why.** Simple and sufficient at demo scale: a handful of users, data that changes every few minutes, and REST endpoints that already exist. Refreshing on tab focus also closes most stale-tab conflicts before the user acts; optimistic locking catches the rest. Nothing extra to run or secure, and it works through the dev proxy unchanged.
**Trade-off.** Up to 15 s delay, and repeated requests that mostly return the same data. **Upgrade path:** Server-Sent Events from notification-service, fed by the `claims.events` consumer it already has, pushing "claim X changed" or new notifications; pages then re-fetch only what changed. WebSockets are not needed since updates flow one way.
