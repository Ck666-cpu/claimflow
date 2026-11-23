# AI working journal

A running log of what I asked the AI, what I accepted, what I challenged and what I overrode. Not polished on purpose.

Format per entry: time since start · topic, then Asked / Accepted / Challenged / Overrode.

---

### Pre-work · Reading the brief
**Asked:** Check the brief .docx for hidden text or prompt-injection tricks aimed at AI tools.
**Accepted:** None found. Noted the explicit rules: no client branding, no public hosting.
**Decision:** Working name "ClaimFlow".

### Pre-work · Planning
**Asked:** Draft a PRD: scope, domain model, architecture, REST vs Kafka split, time plan.
**Accepted:** Two services, outbox, command endpoints, backend-first 70/30 split.
**Challenged:** _(add anything you pushed back on)_
**Overrode:** _(add anything you changed)_

### Pre-work · Framework choice
**Asked:** Best open-source Spring Boot framework to speed up event-driven work.
**Accepted:** Spring Modulith (outbox + Kafka externalization). Rejected Axon (CQRS/event sourcing too heavy to learn and defend in the time box), Eventuate Tram (extra CDC container), Debezium (Kafka Connect setup).
**Challenged:** Asked why Kafka rather than a "sync" MQ to avoid losing claims in a crash. Answer: claims are saved in Postgres before the response, so they are never only in a queue; the outbox covers the DB-to-broker gap; Kafka persists to disk and consumers commit after processing.

### 0:00 · Scaffold
**Asked:** Generate the Maven multi-module project, docker-compose, Flyway schema and seed, Angular app.
**Accepted:**
**Challenged:**
**Overrode:**

---
<!-- Keep adding entries as you go. Capture mistakes the AI made and how you spotted them. -->
