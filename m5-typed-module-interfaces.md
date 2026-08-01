# M5 — Typed Module Interfaces

## Goal

Remove synchronous transport knowledge from business orchestration while preserving the existing distributed services as a rollback-compatible runtime.

## Architecture

M5 uses a port/facade/adapter model:

1. Transport-neutral capability interfaces live in `common`.
2. Owning modules expose direct Spring facades that implement those interfaces.
3. Calling services depend only on the interfaces.
4. Conditional HTTP adapters are created only when no direct facade is present.
5. Public controllers and external integrations remain unchanged.

This allows the distributed services to continue calling one another through HTTP today while enabling the consolidated application to bind direct in-process implementations later.

## Typed contracts

- `CatalogModuleApi`: offering snapshots, stock reservation/restoration and slot state.
- `ProviderModuleApi`: provider ownership and enabled vaccination reminders.
- `PaymentModuleApi`: transaction verification, promotions, coupon lifecycle, COD, refunds and loyalty events.
- `DiscoveryModuleApi`: delivery serviceability decisions.
- `OrderModuleApi`: internal order status transitions.

Contracts expose immutable business snapshots rather than persistence entities, controller DTOs, URLs, headers or client-library types.

## Migrated call sites

### Order

- offering and stock checks;
- stock reservation and compensation;
- promotion validation and coupon lifecycle;
- payment confirmation;
- COD eligibility;
- serviceability checks;
- provider-owner resolution;
- refund and loyalty notifications.

### Appointment

- slot lookup and status updates;
- provider-owner authorization;
- payment verification.

### Dispatch

- assigned, picked-up and delivered order transitions.

### Content

- provider ownership for banner auction bids.

### Notification

- enabled vaccination reminder synchronization.

## Compatibility controls

- Existing `/api/v1/**` controllers are unchanged.
- Remote adapters preserve legacy URL, header and response behavior.
- Compatibility constructors/DTOs remain where focused tests and rollback tooling instantiate services directly.
- External Razorpay and Expo/FCM calls are not converted into module interfaces.
- Direct cross-module repository access remains prohibited.
- Database migrations, Kafka topics and Docker service topology are unchanged.

## Architecture enforcement

`ModuleInterfaceArchitectureTest` verifies:

- the typed contract catalog contains catalog, discovery, order, payment and provider;
- migrated business service sources contain no internal API paths;
- migrated business service sources contain no `RestOperations` execution methods or URI builders.

The application exposes the interface inventory through `/actuator/info` with:

- `binding=direct-when-present`;
- `fallback=conditional-http-adapter`;
- `transportKnowledgeInBusinessServices=false`.

## Rollback

M5 does not require a traffic or infrastructure cutover.

To roll back:

1. Deploy the previous service images or revert the M5 merge.
2. Keep the current API gateway and all standalone services running.
3. Retain existing service URL environment variables and internal trust secrets.
4. No database, Kafka or mobile rollback is required because those contracts are unchanged.

## Exit gates

- Complete backend Gradle build and tests.
- Typed-contract and transport-boundary architecture tests.
- Generated-artifact and production-hardening checks.
- Customer mobile validation.
- Merchant/captain mobile validation.
- Clean-volume Full Stack Smoke proving the HTTP fallback adapters remain operational.
