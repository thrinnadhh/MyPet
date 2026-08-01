# M6 Events and Background Work

## Objective

Replace broker-shaped business coupling with explicit workflow classifications while retaining Kafka as the production-safe rollback transport.

M6 does not delete Kafka, retry topics, DLQs or consumer groups. It introduces a transport-neutral event envelope, guarded in-process consumer bridges and a routed outbox publisher so each topic can move independently after verification.

## Workflow classes

| Workflow | Producer | Consumers | Classification | Current transport | In-process readiness |
|---|---|---|---|---|---|
| Catalog stock consistency | Order | Catalog | Direct typed call | Module API / HTTP fallback | Active through M5 |
| Payment/order consistency | Order | Payment | Direct typed call | Module API / HTTP fallback | Active through M5 |
| Provider ownership consistency | Appointment | Provider | Direct typed call | Module API / HTTP fallback | Active through M5 |
| Order lifecycle fan-out | Order | Notification, Dispatch, Captain | Durable outbox job | Kafka | Verified bridge |
| Appointment reminder scheduling | Appointment | Notification | Durable outbox job | Kafka | Verified bridge |
| Review projections | Review | Provider, Discovery | In-process event | Kafka | Verified bridge |
| Provider discovery projection | Provider | Discovery | In-process event | Kafka | Verified bridge |
| Vaccination reminders | Provider | Notification | Durable outbox job | Outbox → Kafka | Verified bridge |
| Chat message notification | Chat | Notification | Durable background work | Direct Kafka | Pending producer migration |
| Dispatch lifecycle fan-out | Dispatch | Notification, Captain | Durable outbox job | Kafka | Pending consumers |
| Payment lifecycle fan-out | Payment | Order, Notification | Durable outbox job | Kafka | Pending consumers |
| Catalog projections | Catalog | Discovery | In-process event | Kafka | Pending consumer |
| Support notification fan-out | Support | Notification | Durable outbox job | Kafka | Pending consumer |

The executable source of truth is `MyPetWorkflowCatalog` in `common`.

## Delivery modes

### `KAFKA_ONLY`

Default for all standalone services and the consolidated application.

- The outbox publishes to the existing Kafka topic.
- Existing retry topics, DLQs and consumer groups remain authoritative.
- No in-process business handler executes.
- Rollback behavior is identical to M5.

### `DUAL_SHADOW`

Evidence mode for comparing routing and payload coverage.

- A `ModuleDomainEvent` is published with `shadow=true`.
- Every in-process bridge ignores shadow events.
- Kafka remains authoritative and executes business behavior.
- The outbox row is marked published only after Kafka succeeds.

### `IN_PROCESS_ONLY`

Cutover mode for an individually verified topic.

- Kafka publication is skipped.
- The event is delivered through Spring's application event publisher.
- The routed publisher rejects any topic not marked `inProcessReplacementReady`.
- Consumer bridges activate only in this mode.

This mode must not be enabled globally while pending topics remain.

## Durable outbox guarantees

`OutboxPoller` now delegates transport selection to `OutboxEventPublisher`.

The poller retains the existing guarantees:

1. Load unpublished owner records in repository order.
2. Publish synchronously through the selected route.
3. Set `published_at` only after successful publication.
4. Stop the batch on failure to avoid reordering later events.
5. Leave the failed event unpublished for the next retry.

No existing outbox table or migration is changed.

## In-process bridges

The bridges adapt `ModuleDomainEvent` back into the established listener entry points:

- Notification: orders, appointments, chat and vaccination.
- Provider: review projection.
- Discovery: provider and review projections.
- Dispatch: order ready-for-pickup events.
- Captain: delivered-order earnings.

They deliberately reuse current handler methods so parsing, idempotency, transactions, retry semantics and business behavior are not reimplemented in a second code path.

## Rollback

Rollback is configuration-only:

```text
MYPET_EVENT_DELIVERY_MODE=KAFKA_ONLY
```

Because M6 does not remove Kafka listeners or topics, restoring Kafka-only delivery requires no database rollback, replay rewrite or mobile release.

## Operational checks

Before enabling `IN_PROCESS_ONLY` for a topic:

- verify the producer uses the routed outbox publisher;
- verify every consumer is represented in the workflow catalog;
- verify every consumer has an enabled bridge;
- verify shadow events cause no side effects;
- verify idempotency remains valid when replaying unpublished rows;
- run complete backend, mobile and clean-volume full-stack validation;
- retain the Kafka topic and consumer group through M9 observation.

## Deferred work

The following remain intentionally Kafka-authoritative:

- chat producer migration to a durable outbox;
- dispatch lifecycle consumers;
- payment lifecycle consumers;
- catalog projection consumer;
- support notification consumer.

They are exposed as pending topics through `/actuator/info` and cannot be selected by `IN_PROCESS_ONLY` publication.
