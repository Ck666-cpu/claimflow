# ClaimFlow — product requirements (as built)

The plan I worked from, updated to match what was built. Detailed rationale is in [`decisions.md`](decisions.md); event details in [`events.md`](events.md).

## Problem

Claims arrive by phone and email; staff work from shared inboxes and spreadsheets; managers have no real-time view of outstanding claims or liability exposure.

## Goals for the sprint (2–3 h target, 5 h cap)

1. A claim travels the full lifecycle through the API: submit, assign, request information, respond, decide, settle or reject.
2. Every state change is recorded and published as an event, and at least one consumer reacts to it.
3. Staff see a work queue, their workload, team metrics and outstanding exposure by market.
4. Runs locally; every design decision is written down and defensible.

**Non-goals:** real authentication, payments, document storage, fraud scoring, policy administration, public hosting, pixel-perfect UI.

## Users

| Persona | Core stories | Priority |
| --- | --- | --- |
| Claimant | Report an incident and get a reference number at once; see my claims and their history; answer information requests; see the decision. | Must |
| Claims officer | Pick up unassigned claims (oldest first); request information; set the reserve; approve and settle, or reject with a reason. | Must |
| Team lead | Workload per officer and status; SLA breaches; outstanding exposure by market; reassign claims. | Should |

## Scope (MoSCoW)

| Priority | Item |
| --- | --- |
| Must | Claims service: state machine, REST API, validation, Postgres, Flyway |
| Must | Domain events to Kafka through a transactional outbox (Spring Modulith) |
| Must | Notification service consuming events, with dedupe |
| Must | Seeded users and role checks; docker-compose; README; AI journal; core tests |
| Should | Angular: claimant flow and officer queue + claim workbench |
| Should | Workload and exposure endpoints; OpenAPI contract with a generated client |
| Could | Lead dashboard; live updates (SSE); document upload |
| Won't | Real auth (OIDC), payments, FX conversion, public hosting |

## Claim lifecycle

```
SUBMITTED ──assign──▶ UNDER_REVIEW ──approve──▶ APPROVED ──settle──▶ SETTLED
    │                   │    ▲    │
    │      request info ▼    │    └──reject──▶ REJECTED
    │                INFO_REQUESTED (respond)
    └──withdraw (any open state, before a decision)──▶ WITHDRAWN
```

Any other transition returns 409. The reserve (expected cost) starts at the claimed amount, is set by the officer during assessment, becomes the approved amount on approval, and drops to zero when the claim closes. Outstanding exposure = sum of reserves on open claims, per market and currency.

## Architecture

| Component | Responsibility |
| --- | --- |
| `claims-service` (Spring Boot 4) | System of record. REST commands and queries, state machine, metrics, outbox |
| PostgreSQL | Claims schema + Modulith `event_publication` table; separate `notifications` schema |
| Kafka (`claims.events`, key = claimId) | "Something happened to claim X" facts, in order per claim |
| `notification-service` | Consumes events, dedupes on `eventId`, stores notifications, serves them over REST |
| Angular app (`web/`) | One app for claimants and staff, role-guarded routes |

**REST for commands and reads** (the user needs an immediate answer: a reference number, a validation error, a 409). **Kafka for side effects** (notifications now, analytics or fraud checks later), so they never slow down or break the write. If Kafka is down, claims are still submitted and decided; events wait in the outbox.

## Quality rules

Money as `BigDecimal` with a currency; optimistic locking on every command; RFC 7807 errors; audit row per transition; idempotent submission; at-least-once events with consumer dedupe; claimants never see reserves.

## Plan vs actual

| Planned | Built / changed | Why |
| --- | --- | --- |
| Events annotated `@Externalized` on domain records | Mapped to a separate integration envelope | Internal refactors must not break consumers |
| `claimVersion` in the event envelope | Dropped | Not known until flush; ordering comes from the partition key |
| openapi-generator for the Angular client | `ng-openapi-gen` (or hand-written fallback) | Pure Node, no Java tooling in the frontend build |
| Paged lists | Unpaged | Demo volumes; first change for real data |
| _fill in anything else that changed_ | | |
