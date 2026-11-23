# ClaimFlow

A claims platform for motor and property claims: claimants report and track claims, claims staff assess and decide them, and team leads see workload and outstanding exposure. Take-home assessment build; backend first.

> Local only. Not hosted publicly. No client branding or proprietary material is used.

## Stack

| Part | Choice |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL 17 |
| Events | Spring Modulith event publication registry (transactional outbox) + Kafka externalization; Kafka 4.1 single-node KRaft |
| Services | `claims-service` (system of record, REST, port 8080) and `notification-service` (Kafka consumer, port 8081) |
| Frontend | Angular 22 (`web/`) |
| Contract | OpenAPI from springdoc, exported to `docs/api/openapi.yaml`; Angular client generated from it |
| Tests | JUnit 5, Spring Modulith module tests, Testcontainers |

## Run locally

Prerequisites: Docker, JDK 21, Maven 3.9+, Node 22+.

```bash
# 1. Infrastructure (Postgres + Kafka)
docker compose up -d

# 2. Backend services (two terminals)
mvn -pl claims-service spring-boot:run
mvn -pl notification-service spring-boot:run

# 3. Frontend
cd web && npm install && npm start      # http://localhost:4200
```

Or run everything except the frontend in containers:

```bash
docker compose --profile app up -d --build
```

Useful URLs: Swagger UI http://localhost:8080/swagger-ui.html · health http://localhost:8080/actuator/health · notifications Swagger UI http://localhost:8081/swagger-ui.html

Watch claim events on Kafka (envelope, topic, delivery guarantee and failure demo: [`docs/events.md`](docs/events.md)):

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claims.events --from-beginning --property print.key=true --property print.headers=true
```

`mvn verify` also runs the Testcontainers integration tests (`*IT`), so Docker must be running.

## Demo users (seeded)

Real authentication is out of scope. The frontend sends an `X-User-Id` header for the selected user.

| User | Role | ID |
| --- | --- | --- |
| Alice Tan | Claimant | `00000000-0000-0000-0000-000000000001` |
| Raj Kumar | Claimant | `00000000-0000-0000-0000-000000000002` |
| Ben Lim | Officer | `00000000-0000-0000-0000-000000000011` |
| Chitra Nair | Officer | `00000000-0000-0000-0000-000000000012` |
| Daniel Wong | Team lead | `00000000-0000-0000-0000-000000000021` |

## Design

- Decisions and trade-offs: [`docs/decisions.md`](docs/decisions.md)
- Events on Kafka (contract, delivery, dead letters, failure demo): [`docs/events.md`](docs/events.md)
- AI working journal: [`AI_JOURNAL.md`](AI_JOURNAL.md)

Claim lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED → APPROVED → SETTLED`, with `REJECTED` from review and `WITHDRAWN` before a decision.

## Assumptions

- Policy numbers are free text; policy validation is stubbed.
- Six markets are configuration (SG, MY, HK, AU, TH, ID), each with its own currency. Exposure is never summed across currencies.
- One officer owns a claim at a time; leads can reassign.
- Open claims older than 10 days count as SLA breaches (`claimflow.sla-days`).

## What is not built (and why)

_Fill in at the end: what you cut, and what you would build next._
