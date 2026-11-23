# Claim events

How `claims-service` tells the rest of the system that something happened to a claim, and how
`notification-service` consumes it. Decisions behind this: ADR-2, ADR-3, ADR-4, ADR-10, ADR-11 in
[`decisions.md`](decisions.md).

```
claims-service                                    Kafka                         notification-service
──────────────                                    ─────                         ────────────────────
command ─► Claim aggregate registers event
        ─► one DB transaction:
             claims row + history + event_publication row (outbox)
        ─► after commit: Modulith maps event ─► ClaimEventMessage ─► claims.events ─► ClaimEventListener
             and marks the outbox row completed       (key = claimId)              dedupe on eventId
             once Kafka acks                                                       ─► notifications table
        ─► every 30 s: resubmit outbox rows                     claims.events.DLT ◄─ poison records
             still incomplete after 30 s
```

## Topic

| | |
| --- | --- |
| Topic | `claims.events` (`claimflow.events-topic`) |
| Partitions | 3 |
| Replication | 1 locally (`claimflow.events-replication`); production 3 with `min.insync.replicas=2` |
| Key | `claimId` (string). All events of one claim land on one partition, so consumers see them in order. |
| Value | `ClaimEventMessage` as UTF-8 JSON (below) |
| Headers | `eventType`, `eventId` (convenience copies of envelope fields). Spring also adds `__TypeId__`; consumers must not rely on it. |
| Producer | `acks=all`, `enable.idempotence=true` |
| Dead letters | `claims.events.DLT`, 3 partitions, same partition as the original record |

The topic is created on startup by claims-service, the DLT by notification-service (Spring `NewTopic` beans).

## Envelope: `ClaimEventMessage` (schemaVersion 1)

Domain events (`ClaimEvents.*`) are internal. Consumers parse this envelope only.

| Field | Type | Notes |
| --- | --- | --- |
| `eventId` | UUID | unique per event; **consumers de-duplicate on it** |
| `eventType` | string | `ClaimSubmitted`, `ClaimAssigned`, … (simple class name of the domain event) |
| `schemaVersion` | int | `1`; bumped only for a breaking change |
| `claimId` | UUID | also the Kafka key |
| `referenceNo` | string | human-readable, e.g. `CLM-001013` |
| `claimantId` | UUID | owner of the claim |
| `actorId` | UUID | user who caused the event |
| `occurredAt` | ISO-8601 instant (UTC) | |
| `payload` | object | event-specific fields only (table below) |

Money is always a **string with two decimals** (`"2750.00"`), never a JSON number, so no precision is lost.
UUIDs are strings. Consumers should ignore fields they do not know: new optional fields may be added
without bumping `schemaVersion`.

| eventType | payload fields |
| --- | --- |
| `ClaimSubmitted` | `type`, `marketCode`, `currency`, `claimedAmount` |
| `ClaimAssigned` | `assigneeId` |
| `InformationRequested` | `requestId`, `question` |
| `InformationProvided` | `requestId`, `assigneeId` |
| `ReserveChanged` | `marketCode`, `currency`, `oldAmount`, `newAmount` (internal figure, never show to claimants) |
| `ClaimApproved` | `currency`, `approvedAmount` |
| `ClaimRejected` | `reason` |
| `ClaimSettled` | `currency`, `settledAmount` |
| `ClaimWithdrawn` | none |

### Examples (real records from the local topic)

<details>
<summary>ClaimSubmitted</summary>

```json
{
  "eventId": "781936b5-31aa-4d4f-bb91-9b049de6a3b8",
  "eventType": "ClaimSubmitted",
  "schemaVersion": 1,
  "claimId": "86812cfa-fcb0-442c-84c6-b03e3bdf66b6",
  "referenceNo": "CLM-001014",
  "claimantId": "00000000-0000-0000-0000-000000000002",
  "actorId": "00000000-0000-0000-0000-000000000002",
  "occurredAt": "2026-11-23T14:00:10.947186900Z",
  "payload": { "type": "PROPERTY", "marketCode": "MY", "currency": "MYR", "claimedAmount": "1800.00" }
}
```
</details>

<details>
<summary>ClaimAssigned</summary>

```json
{
  "eventId": "9cfbb942-21de-408c-bc93-f605f4565a07",
  "eventType": "ClaimAssigned",
  "schemaVersion": 1,
  "claimId": "86812cfa-fcb0-442c-84c6-b03e3bdf66b6",
  "referenceNo": "CLM-001014",
  "claimantId": "00000000-0000-0000-0000-000000000002",
  "actorId": "00000000-0000-0000-0000-000000000021",
  "occurredAt": "2026-11-23T14:00:11.184609Z",
  "payload": { "assigneeId": "00000000-0000-0000-0000-000000000012" }
}
```
</details>

<details>
<summary>InformationRequested</summary>

```json
{
  "eventId": "a008f815-3532-4505-8727-5dbfbfd27bea",
  "eventType": "InformationRequested",
  "schemaVersion": 1,
  "claimId": "97975816-8a97-488f-9dd4-be849bdaa41e",
  "referenceNo": "CLM-001013",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000011",
  "occurredAt": "2026-11-23T14:00:10.547636300Z",
  "payload": {
    "requestId": "dda0ae25-b3f6-4346-b44f-ca9f7310ef71",
    "question": "Please upload photos of the damage and the police report number."
  }
}
```
</details>

<details>
<summary>InformationProvided</summary>

```json
{
  "eventId": "61e5d255-34ca-44ac-9027-cd52e44ec3ee",
  "eventType": "InformationProvided",
  "schemaVersion": 1,
  "claimId": "97975816-8a97-488f-9dd4-be849bdaa41e",
  "referenceNo": "CLM-001013",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000001",
  "occurredAt": "2026-11-23T14:00:10.611025800Z",
  "payload": {
    "requestId": "dda0ae25-b3f6-4346-b44f-ca9f7310ef71",
    "assigneeId": "00000000-0000-0000-0000-000000000011"
  }
}
```
</details>

<details>
<summary>ReserveChanged</summary>

```json
{
  "eventId": "9706fa6b-536f-4216-9bed-550cb10dc9e7",
  "eventType": "ReserveChanged",
  "schemaVersion": 1,
  "claimId": "97975816-8a97-488f-9dd4-be849bdaa41e",
  "referenceNo": "CLM-001013",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000011",
  "occurredAt": "2026-11-23T14:00:10.673681900Z",
  "payload": { "marketCode": "SG", "currency": "SGD", "oldAmount": "3200.00", "newAmount": "2900.00" }
}
```
</details>

<details>
<summary>ClaimApproved</summary>

```json
{
  "eventId": "235433bd-a2ce-43c7-b855-6798086dfc8f",
  "eventType": "ClaimApproved",
  "schemaVersion": 1,
  "claimId": "97975816-8a97-488f-9dd4-be849bdaa41e",
  "referenceNo": "CLM-001013",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000011",
  "occurredAt": "2026-11-23T14:00:10.740140600Z",
  "payload": { "currency": "SGD", "approvedAmount": "2750.00" }
}
```
</details>

<details>
<summary>ClaimRejected</summary>

```json
{
  "eventId": "f2d21df3-ef57-42db-b4cd-d5ca01500111",
  "eventType": "ClaimRejected",
  "schemaVersion": 1,
  "claimId": "2c6fa7ab-401d-4573-a144-b839e4804522",
  "referenceNo": "CLM-001018",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000011",
  "occurredAt": "2026-11-23T14:08:48.101847900Z",
  "payload": { "reason": "Items lost outside the home are not covered by this policy." }
}
```
</details>

<details>
<summary>ClaimSettled</summary>

```json
{
  "eventId": "fca66a67-9b4b-46ad-81a8-df2b85a98bdb",
  "eventType": "ClaimSettled",
  "schemaVersion": 1,
  "claimId": "97975816-8a97-488f-9dd4-be849bdaa41e",
  "referenceNo": "CLM-001013",
  "claimantId": "00000000-0000-0000-0000-000000000001",
  "actorId": "00000000-0000-0000-0000-000000000011",
  "occurredAt": "2026-11-23T14:00:10.799535800Z",
  "payload": { "currency": "SGD", "settledAmount": "2750.00" }
}
```
</details>

<details>
<summary>ClaimWithdrawn</summary>

```json
{
  "eventId": "24621e1d-c2d5-4cf1-b16e-f217566fba3e",
  "eventType": "ClaimWithdrawn",
  "schemaVersion": 1,
  "claimId": "bb916045-d569-45c9-a3a1-f40e95f716b2",
  "referenceNo": "CLM-001017",
  "claimantId": "00000000-0000-0000-0000-000000000002",
  "actorId": "00000000-0000-0000-0000-000000000002",
  "occurredAt": "2026-11-23T14:08:47.990900Z",
  "payload": {}
}
```
</details>

## Delivery guarantee

**At-least-once, with de-duplication in every consumer.**

- **Producer side (outbox).** The event is written to `event_publication` in the same transaction as the
  claim change, so a committed claim always has its event and a rolled-back one never does. After commit,
  Modulith sends it to Kafka and sets `completion_date` once Kafka acknowledges (`completion-mode: update`
  keeps completed rows for inspection). If the send fails or hangs, the row stays incomplete and is sent
  again: by `IncompleteEventResubmitter` every 30 s (rows incomplete for over 30 s), and on restart
  (`republish-outstanding-events-on-restart`). A resend can race the original send, so **the same event can
  reach Kafka twice**; it keeps its `eventId`.
- **Consumer side.** `notification-service` checks `existsByEventId` before inserting, and
  `notifications.event_id` is unique for the case where two deliveries race past that check (the
  `DataIntegrityViolationException` is treated as a duplicate). Offsets are committed by the listener
  container only after the listener returns, so a crash mid-record means redelivery, never loss.
- **Order.** Per claim only (one key, one partition). There is no ordering across claims.

## Dead letters

`notification-service` uses a `DefaultErrorHandler`:

- a record that throws is retried 3 times, 1 s apart (`FixedBackOff(1000, 3)`), then published to
  `claims.events.DLT` on the same partition and skipped;
- records that cannot be parsed (bad JSON, missing `eventId`/`eventType`/`claimId`) go to the DLT
  immediately: retrying will not fix them.

The partition is never blocked. DLT records keep the original value and carry `kafka_dlt-*` headers
(original topic, partition, offset, consumer group, exception class and message). Inspect them with:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claims.events.DLT --from-beginning --property print.headers=true
```

Replaying from the DLT is manual for now (fix the cause, then re-publish the value to `claims.events`;
the consumer de-duplicates, so replaying an event that did get through is harmless).

## Watching the topic

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claims.events --from-beginning --property print.key=true --property print.headers=true
```

Consumer position and lag:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service
```

## Failure demo

Prerequisites: `docker compose up -d`, claims-service on :8080 and notification-service on :8081, both
running the step 4 code. Results below are from a run on 2026-11-23.

### A. Kafka down: the write still succeeds, the event follows later

1. Stop the broker:
   ```powershell
   docker compose stop kafka
   ```
2. Submit a claim (Swagger UI, `claims.http` request 1, or):
   ```powershell
   Invoke-RestMethod http://localhost:8080/api/v1/claims -Method Post -Headers @{"X-User-Id"="00000000-0000-0000-0000-000000000001"} -ContentType application/json -Body '{"type":"PROPERTY","marketCode":"SG","policyNumber":"POL-SG-DEMO","incidentDate":"2026-11-22","description":"Kafka-outage demo claim.","claimedAmount":"640.00"}'
   ```
   It returns **201** straight away (390 ms in our run): the request only writes to Postgres; the Kafka
   send happens after commit on another thread.
3. The outbox row is there but incomplete:
   ```powershell
   docker compose exec postgres psql -U claimflow -d claimflow -c "select event_type, completion_date from event_publication order by publication_date desc limit 5;"
   ```
   ```
                         event_type                       |        completion_date
   -------------------------------------------------------+-------------------------------
    com.claimflow.claims.claim.ClaimEvents$ClaimSubmitted |
    com.claimflow.claims.claim.ClaimEvents$ClaimAssigned  | 2026-11-23 14:00:11.210942+00
   ```
4. Start the broker again:
   ```powershell
   docker compose start kafka
   ```
   The row gets its `completion_date` and the claimant's notification appears ("We received claim
   CLM-0010xx. We'll review it shortly."). How it heals depends on the outage length:
   - **Short outage (under ~60 s):** the original send is still waiting for the broker (the producer
     blocks up to `max.block.ms`, 60 s) and simply completes when Kafka is back. In our run: completed
     21 s after the claim was saved.
   - **Long outage:** the original send times out and the row stays `PROCESSING`;
     `IncompleteEventResubmitter` re-sends it every 30 s. In our run with an 80 s outage: 3 attempts
     while Kafka was down, `COMPLETED` 7 s after Kafka came back, one notification. Both the original
     send and a resubmission reached the topic; the consumer logged
     `Duplicate event … (ClaimSubmitted), already handled` and stored one notification.

   Check the row's progress with:
   ```powershell
   docker compose exec postgres psql -U claimflow -d claimflow -c "select event_type, status, completion_date, completion_attempts, last_resubmission_date from event_publication order by publication_date desc limit 3;"
   ```

### B. Consumer down: it catches up from its committed offset, no duplicates

5. Stop notification-service, then generate events (submit a claim, assign it, request information).
   The group shows lag on the claim's partition:
   ```powershell
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service
   ```
   Start notification-service again. It resumes from the committed offset and stores exactly one
   notification per event (in our run: the 3 notifications for the new claim, lag back to 0 on every
   partition, `count(*) = count(distinct event_id)` in `notifications.notifications`).

   If the service was killed rather than stopped cleanly, the group waits for the old member's session
   timeout (45 s) before reassigning partitions, so the catch-up starts after that.
