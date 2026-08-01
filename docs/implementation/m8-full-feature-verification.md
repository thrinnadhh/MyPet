# M8 Full Feature Verification

## Objective

M8 proves that the current MyPet system still supports its complete connected business journey after the M1–M7 modular-monolith changes. It is a verification milestone, not an infrastructure cutover.

A successful M8 run does **not** authorize M9. Traffic cutover, consolidated API activation, worker topology changes and legacy-service retirement remain separately reviewed M9 work. The distributed stack and its rollback path remain required until M9 completes.

## Verified domains

The matrix covers exactly fourteen domains:

- `customer` — authenticated profile sync, default address and favourite lifecycle;
- `provider` — merchant ownership, provider submission state and admin approval;
- `catalog` — delivery offerings, stock, appointment offerings and slots;
- `appointment` — hold, double-book prevention, confirmation, completion, invoice and scheduler timeout;
- `order` — quote-token checkout, COD eligibility, stock reservation and delivery state;
- `payment` — captured transaction persistence and payment event creation;
- `loyalty` — delivered-order award, progress and duplicate-event idempotency;
- `captain` — onboarding, protected bank fields and online availability;
- `dispatch` — offer, acceptance, OTP pickup, delivery and order propagation;
- `review` — completed-appointment review and provider aggregate;
- `notification` — event-driven appointment reminders and push-token lifecycle;
- `chat` — customer/merchant conversation, message and read state;
- `content` — admin publishing, public reads and blocked customer writes;
- `admin` — provider approval, profile inventory and revoke/restore.

## Connected fixture graph

The verifier creates one clean connected graph instead of unrelated endpoint probes:

1. Customer, second customer, merchant, administrator and captain identities are synchronized.
2. The customer receives one default Tirupati delivery address.
3. The merchant creates a delivery provider and an appointment provider.
4. The administrator activates both providers.
5. The merchant creates one stocked delivery offering, one appointment offering and two future slots.
6. The appointment path creates reminders, a completed appointment, an invoice and a review.
7. The commerce path creates a quote, order, payment, dispatch job, delivery, captain earnings and loyalty award.
8. The same order and provider identities are reused by chat, content, notification and admin assertions.

This graph detects broken cross-module ownership, transport adapters, event consumers, schedulers and database relations that isolated controller tests can miss.

## Evidence classes

Every domain has HTTP contract evidence. The matrix also records the failure modes most relevant to production migration:

- **Database state:** stock, transactions, user suspension and durable event records are queried directly from PostgreSQL.
- **Authorization boundary:** merchant ownership, admin-only approval/publishing and customer write denial are exercised.
- **Asynchronous projection:** discovery, appointment reminders, dispatch propagation, captain earnings and reviews are polled until the consumer-side state exists.
- **Idempotency:** duplicate delivered-order loyalty events are rejected without awarding a second star.
- **Concurrency:** a second customer cannot hold an already-held appointment slot.
- **Scheduler:** a deliberately expired appointment hold returns its catalog slot to `AVAILABLE`.

## Execution

The complete clean-volume run is:

```bash
bash scripts/test-all.sh
```

Execution order:

1. `scripts/test-full-stack.sh` builds the backend and starts a fresh PostgreSQL, Redis, Kafka, all distributed services and the M4 shadow application.
2. `scripts/test-feature-flows.sh` retains the pre-M8 loyalty, serviceability and ShedLock proof.
3. `scripts/test-m8-feature-matrix.py` executes the connected fourteen-domain matrix.
4. All results are appended to `build/reports/full-stack-smoke.md`.

The M8 verifier uses only Python's standard library and the existing PostgreSQL Compose container. It does not add a package-manager or network dependency to CI.

## Static enforcement

`scripts/check-m8-feature-verification.py` runs from the production-hardening gate and fails when:

- one of the fourteen domains is absent from the runtime verifier or catalog;
- the runtime verifier is invalid Python;
- authorization, projection, idempotency, concurrency or scheduler evidence is removed;
- `scripts/test-all.sh` no longer executes the matrix;
- actuator metadata authorizes cutover or drops the rollback requirement;
- this runbook no longer identifies all required domains.

The application exposes the catalog through `/actuator/info` under `featureVerification`. It reports fourteen domains, `cutoverAuthorized=false` and `legacyRollbackRequired=true`.

## Failure triage

A failed matrix stops at the first broken connected flow and appends the exact failure to the shared report. Triage should proceed in this order:

1. Confirm the relevant service readiness and gateway route.
2. Inspect the domain service and gateway logs from `build/reports/docker-diagnostics/`.
3. Inspect the owning schema and outbox row.
4. Inspect Kafka topic/consumer state when the assertion is asynchronous.
5. Determine whether the failure is a product defect, stale verifier payload or an environmental dependency.
6. Fix the narrowest owning module and rerun the complete clean-volume suite.

Do not weaken an assertion merely to restore green CI. Any intentionally unsupported flow must be explicitly removed from the M8 scope through a separately reviewed architecture decision.

## Exit criteria

M8 is complete only when all of the following pass on the same PR head:

- complete backend build and tests;
- application metadata and fourteen-domain catalog tests;
- generated-artifact and production-hardening gates;
- customer mobile validation;
- merchant/captain mobile validation;
- clean-volume infrastructure and all distributed service readiness checks;
- the existing extended feature checks;
- all fourteen connected feature-domain scenarios;
- authorization, concurrency, idempotency, scheduler, durable-outbox and asynchronous-projection evidence;
- no public API, schema, migration, Kafka topic, base Compose service or rollback component is removed.

## M9 boundary

M9 remains **not authorized** until the M8 PR is reviewed and merged and its final validation evidence is recorded. M8 does not change production traffic, enable the consolidated edge, disable Kafka, remove distributed services or start legacy retirement.
