# P2B Order 3 — Merchant Inventory and Orders

## Merchant orders

The operational application now has a dedicated merchant order queue backed by:

- `GET /api/v1/orders/provider/{providerId}`
- `PUT /api/v1/orders/{orderId}/status?status=...&note=...`

The UI exposes only merchant-authorized lifecycle actions:

- `PLACED → ACCEPTED | REJECTED`
- `ACCEPTED → PREPARING | CANCELLED`
- `PREPARING → READY_FOR_PICKUP`

Later dispatch/captain states are read-only for merchants. The order service remains authoritative and rejects stale or unauthorized transitions. Conflict responses trigger a server refresh.

Queues cover new, in-progress, ready-for-pickup and past orders. Destructive transitions require an operational note.

## Inventory

The inventory workspace is backed by provider-owned catalog endpoints and supports:

- provider/business selection;
- product and service search;
- active, inactive, low-stock and out-of-stock queues;
- create, edit, activate/deactivate and delete;
- delivery-product stock, SKU and barcode;
- appointment-service duration;
- INR pricing and shared client formatting;
- backend field validation and trace-aware errors.

Zero-stock delivery products are persisted as `OUT_OF_STOCK` and cannot be reactivated until stock is increased.

## Navigation interlinks

- Merchant tabs contain a dedicated Orders destination.
- Merchant deep links to `/orders` are role guarded.
- Incoming order notifications open Orders instead of the appointment Bookings route.
- Appointment slot operations remain in Bookings and are completed in P2B Order 6.

## Safety boundaries

- No client-side order status is trusted without a successful server response.
- Product ownership is enforced using the existing offering/provider ownership checks.
- Existing order item snapshots remain intact if an offering is removed.
- The production inventory screen does not substitute offline mock catalog data.
