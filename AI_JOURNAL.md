# AI working journal

A running log of what I asked the AI, what I accepted, what I challenged and what I overrode, with brief reasoning. Not polished on purpose.

**Tools used**
- **Claude (chat)** for planning, design decisions, specs and review.
- **Claude Code (IntelliJ plugin)** for implementing steps 3–6 from the specs in [`docs/prompts/`](docs/prompts/), one part at a time, with a review stop after each part.

**How I worked with the AI**
1. Plan first: brief check, PRD, framework choice, before any code.
2. For each build step, have the chat AI write a precise spec with acceptance checks, then have Claude Code implement it part by part.
3. Review and run each part's check myself before committing. Nothing was committed that I had not run.

Entries marked **TODO(me)** are details only I know from the IntelliJ sessions; they are filled in below each step.

---

## Pre-work

### Checking the brief for traps
**Asked:** Inspect the brief `.docx` for hidden text, white or tiny text, comments, metadata or prompt-injection aimed at AI tools.
**Accepted:** It checked the visible text, raw XML, headers and footers, metadata and the embedded image. Nothing hidden.
**Noted:**
- The file metadata title says "Senior/Lead Engineer" while the brief is for the graduate programme: a reused template, not a trick.
- The hiring panel contact is an unfilled placeholder.
- The explicit rules: no public hosting; no client branding or proprietary material. I used the neutral working name **ClaimFlow**.

### Planning: PRD
**Asked:** A PRD with personas, scope, domain model, architecture, REST vs Kafka split and a time plan.
**Accepted:**
- Two services (claims as system of record, notifications as a pure consumer).
- Transactional outbox for events; explicit command endpoints instead of PATCH status.
- Backend-first, roughly 70/30, because the brief rates a solid backend with a partial frontend above a rushed attempt at both.
- MoSCoW cut line and a "stop adding features at 4:00" rule.
  **Decided:** The AI laid out both C#/.NET and Java options; I chose **Java / Spring Boot** because I can defend it line by line.

### Framework choice
**Asked:** The best open-source Spring Boot framework to speed up event-driven work.
**Accepted:** **Spring Modulith**: its event publication registry is the outbox, and it externalizes events to Kafka without hand-written relay code.
**Rejected (with reasons):**
- Axon Framework: CQRS and event sourcing are a different programming model to learn and defend inside the time box.
- Eventuate Tram: needs an extra CDC service container.
- Debezium: Kafka Connect setup eats the time box. Kept as the production-scale talking point.

### Challenge: why Kafka and not a "sync" queue?
**Asked:** Wouldn't a synchronous MQ avoid losing claims if the system crashes while a message is still in the queue?
**Answer I accepted after checking the reasoning:**
- A claim is saved in Postgres before the REST response, so it is never only in a queue; Kafka carries "this happened" facts.
- RabbitMQ and friends are asynchronous too; durability comes from settings (`acks=all`, replication, commit offsets after processing).
- The real gap is between the database and the broker, and the outbox closes it.
- Redelivery means duplicates, never loss, so consumers dedupe on `eventId`.
  **Kept for the walkthrough:** locally there is one broker with no replicas; production would be 3 brokers, replication factor 3, `min.insync.replicas=2`.

---

## Scaffold

**Asked:** Generate the Maven multi-module project, docker-compose (Postgres + single-node Kafka), Flyway schema and seed data, and the Angular app.
**Accepted:** Repo layout, two services, Flyway V1 schema + V2 seed (6 markets, 5 users, 7 claims across statuses), docker-compose with named volumes, Angular 22 app with a dev proxy.
**Challenged / limitations:**
- The AI could not reach Maven Central from its sandbox, so the Java was **not compiled** when handed over. It validated the SQL migrations on a real Postgres and confirmed the Angular build. I ran the first real `mvn verify` locally.
- Versions were pinned to ones the AI knew existed rather than the latest.
  **AI mistakes caught before handover (during its own review):**
- An invalid Kafka `CLUSTER_ID` (KRaft needs a 22-character base64 UUID).
- `spring-boot-docker-compose` would have failed when running a service from its module folder, so it was removed.

**TODO(me):** anything that failed on my machine at `mvn verify` or first start, and how I fixed it.

### Local setup
- Maven was missing on my laptop: I used IntelliJ's bundled Maven and installed Maven 3.9.11 for the terminal.
- The AI's request to access my folders was blocked, so it wrote `check-setup.ps1` for me to run instead (file presence + tool versions + Docker running + `.m2` mirror check).

---

## Step 3 — Claims core

### Design from the chat AI
**Asked:** Claim aggregate and state machine, child entities, domain events, services, REST API, Problem Details errors, stubbed auth, tests.
**Accepted design:**
- State machine as an enum transition table (`ClaimStatus.allowedNext`); anything else is a 409.
- `Claim` checks **what** is allowed from a state; `ClaimService` checks **who** may do it.
- Events registered on the aggregate and published on save inside the transaction, so the outbox row commits with the change.
- `saveAndFlush` rather than `save`, so `@Version` is bumped before mapping and the response carries the version the client must send next.
- Claimants never see the reserve; another claimant's claim returns 404 rather than 403, so ids cannot be probed.
- Idempotent submission with an `Idempotency-Key`.
  **Deviations from the PRD (decided here):**
- Dropped `claimVersion` from the event envelope: the version is not known until flush, and per-claim ordering already comes from the partition key.
- Lists unpaged for demo volumes; paging noted as the first change for real data.

### Override: how the code was produced
The chat AI first generated a full reference implementation and verified the domain logic against stub annotations (80 scenario checks passed). **I chose not to paste chat-generated code.** Instead I had it write a spec ([`docs/prompts/step3-claims-core.md`](docs/prompts/step3-claims-core.md)) and had Claude Code implement it in IntelliJ in four parts (domain, persistence, services, REST), stopping for my review after each. Reason: I sign off every line, and reviewing in my own IDE, with the build and tests running, is how I can defend it.

### Implementation in IntelliJ
**TODO(me):** for each part (A domain, B persistence, C services, D REST):
- What Claude Code got wrong or did differently from the spec, and what I changed.
- Any question it asked me and what I answered.
- Test results (`mvn -pl claims-service test`, then the `claims.http` walkthrough).

---

## Step 4 — Events (outbox to Kafka, notification consumer)

### Spec decisions (chat AI)
**Accepted:**
- **Integration envelope separate from domain events** (`ClaimEventMessage` with `eventId`, `eventType`, `schemaVersion`, claim fields and a payload map), so internal refactors do not break consumers. This replaced the PRD's idea of annotating the domain records directly.
- Kafka key = `claimId` for per-claim ordering; topic `claims.events`, 3 partitions.
- Money in the payload as strings, to avoid JSON float rounding.
- A scheduled resubmission of incomplete outbox publications, so a Kafka outage heals without a restart.
- Notification service: dedupe on `eventId` backed by a unique constraint; a dead-letter topic so a poison message cannot block the partition; reserve changes never notify claimants.
  **Flagged as unverified by the AI (I had to check):**
- The exact Spring Modulith externalization API for the pinned version.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.*`), so old `ObjectMapper` examples would not compile.
- Fallback agreed in advance: if Kafka fought me for more than about 45 minutes, keep the outbox, log what would be published, and explain the gap.

### Implementation in IntelliJ
**TODO(me):**
- Which externalization API Claude Code actually used, and whether it matched the docs.
- The first real record seen on `claims.events` (paste one line).
- Did the crash demo (stop Kafka → submit → start Kafka → it catches up) work as written?
- Anything I overrode.

---

## Step 5 — Metrics

### Spec decisions (chat AI)
**Accepted:**
- Workload and exposure as SQL aggregates (`JdbcClient`) in the claims service's `metrics` module; "now" comes from the injected `Clock`, not `now()` in SQL.
- Exposure never summed across currencies; all six markets listed, including zeros.
- The test asserts exact numbers from the seed data, worked out by hand: queue 2 (oldest 12 days), 5 open, 3 SLA breaches, SGD 10,300, MYR 16,100, HKD 5,200.
  **AI mistake caught:** the first version of the spec showed Ben's average days to decision as 22.0 in an example; checking it against the seed gave 15.0, and the spec was corrected before I used it.
  **Watch item:** the integration test must start without Kafka, without switching event publishing off in the main config.

### Implementation in IntelliJ
**TODO(me):** did the seed-based assertions pass first time? How did Claude Code keep Kafka out of the test? Anything I changed.

---

## Step 6 — Frontend

### Spec decisions (chat AI)
**Accepted:**
- No UI component library; plain SCSS, neutral branding.
- **The UI does not repeat the state machine**: action buttons come from the claim's `allowedNext` in the API response.
- Polling every 15 s instead of SSE (simple and enough at demo scale; SSE is the upgrade path).
- Typed client generated from `openapi.yaml` with `ng-openapi-gen` (pure Node), with a hand-written fallback if it did not support Angular 22. This replaced the PRD's openapi-generator, which needs Java tooling.
- Priority order: foundation → claimant → staff → lead dashboard only if time remained.

### Implementation in IntelliJ
**TODO(me):**
- Which parts were finished (A–E) and what was cut.
- Did `ng-openapi-gen` work, or did we fall back?
- The two-tab conflict demo result.
- Anything I overrode in the UI.

---

## Wrap-up

**Asked:** Submission checklist and how to submit.
**Noted:** The brief lists deliverables but no submission method, so I followed the recruiter's instructions. Repo kept private; no hosting; history not squashed. Before submitting I ran `scripts/check-submission.ps1` (secrets, branding, stray files, build).

**TODO(me):** total time on the clock, and the one thing I would build next.
