# P2B Order 4 — Captain Delivery Completion

## Dispatch lifecycle

The dispatch job now records an explicit `PICKED_UP` state:

```text
ACCEPTED → PICKED_UP → COMPLETED
```

Pickup proof is accepted only from `ACCEPTED`. Delivery proof is accepted only from `PICKED_UP`. Order status transitions remain server-authoritative.

## Secure job views

Raw dispatch entities contain pickup and delivery verification codes and must not be returned to clients.

`DispatchJobView` contains only:

- job ID;
- order ID;
- status;
- attempt count;
- created/resolved/assigned timestamps.

Endpoints:

- `GET /api/v1/dispatch/jobs/me` — authenticated captain history and active-job recovery;
- `GET /api/v1/dispatch/jobs` — administrator only;
- `GET /api/v1/dispatch/jobs/by-order/{orderId}` — administrator only.

Pickup and delivery responses also return the OTP-safe view.

## App restart recovery

At startup, the captain app reads `/jobs/me` and restores:

- `ACCEPTED` at pickup travel step;
- `PICKED_UP` at customer travel step.

Background tracking begins after an accepted active assignment, not merely while waiting online. Completion stops background tracking while the captain may remain available through foreground location updates.

## History

The captain screen presents recent accepted assignments, including completed and failed jobs, without exposing verification codes or protected destination data.

## Deferred dependency

Pickup and customer navigation addresses are intentionally not fabricated. They require a separate authorized delivery-context DTO that exposes only the assigned job's necessary destination information.
