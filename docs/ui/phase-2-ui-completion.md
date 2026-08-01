# Phase 2 UI Completion Program

## Objective

Complete every user-facing MyPet surface without redesigning the product. Existing customer screens and the approved Stitch **Premium Pet Commerce System** remain the visual source of truth:

- royal blue for brand, navigation, and primary actions;
- amber for ratings, offers, loyalty, and delight moments;
- emerald for healthy, available, paid, and completed states;
- cool-white layered surfaces with soft ambient elevation;
- Inter typography;
- a strict 4 px spacing grid;
- 48 px minimum interactive targets;
- 8 px controls, 16 px cards, and 24 px feature surfaces.

The program must not introduce client-authoritative prices, discounts, loyalty balances, provider identities, payment results, order state, appointment state, or demo authentication.

## Execution order

1. Design-system audit and reusable-component cleanup.
2. Customer application completion.
3. Merchant application completion.
4. Captain application completion.
5. Admin portal completion.
6. Cross-application motion, accessibility, and consistency pass.
7. End-to-end UI QA.

Shared-file writes remain serialized. Feature-screen work may proceed independently only after the shared component contracts are stable.

## Design-system audit

### Current findings

- The customer application already contains the approved royal-blue token system under `src/design/tokens.ts`.
- The merchant/captain application previously used an independent orange-first theme, causing brand fragmentation.
- Both applications expose legacy `Colors`, `Spacing`, `Radius`, and `Shadows` contracts, so token migration can remain backward compatible.
- The approved Stitch reference includes 23 customer-facing screens and a design specification.
- Reusable state treatment exists conceptually, but completion must verify loading, empty, error, offline, unauthenticated, and disabled states on every data-driven screen.

### Cleanup contract

Each application should consume semantic tokens rather than raw hex values inside screens. New reusable primitives must cover:

- `ScreenShell` and bounded responsive content;
- app bar and location header;
- search field;
- filter and category chips;
- section header;
- product, provider, appointment, order, and metric cards;
- status, stock, rating, offer, and role badges;
- skeleton, empty, error, offline, and unauthenticated states;
- primary, secondary, destructive, icon, and sticky CTA buttons;
- accessible form fields and validation messages;
- bottom sheet/dialog foundation;
- toast/banner feedback;
- reduced-motion-aware transitions.

## Customer application matrix

### Existing and reference-aligned discovery

- Home with shops, hospitals, grooming, guides, offers, and delivery context.
- Search and result filtering.
- Category catalogs:
  - food and nutrition;
  - toys and enrichment;
  - treats and chews;
  - vaccinations and tablets;
  - grooming supplies/services;
  - furniture and sleep;
  - travel and apparel;
  - waste management;
  - new arrivals.
- Shop profile and inventory.
- Hospital profile and appointment entry.
- Groomer profile and appointment entry.
- Guide list and guide details.

### Transactional completion

- Product details.
- Cart and quote refresh.
- Address selection and readiness.
- Payment initiation, pending, success, failure, cancellation, and retry.
- Order list, order details, tracking, cancellation eligibility, and support escalation.
- Appointment list, details, reschedule/cancel eligibility, invoice, and support escalation.
- Favourites.
- Loyalty and reward state.
- Reviews and rating submission.
- Chat and notification center.
- Pet profiles and medical reports.
- Profile, settings, language, legal, privacy, and support.

## Merchant application matrix

- Authentication and onboarding status.
- Store profile and operating hours.
- Dashboard and actionable alerts.
- Product/service catalog CRUD.
- Inventory and stock adjustment.
- Incoming, accepted, packing, ready, dispatched, completed, cancelled, and disputed orders.
- Appointment calendar and appointment lifecycle.
- Promotions, coupons, loyalty reward configuration, and content/banner participation.
- Chat, reviews, notifications, and support cases.
- Sales, commission, payout, invoice, and basic analytics views.
- Staff-safe settings and logout.

## Captain application matrix

- Verified authentication and onboarding status.
- Availability toggle.
- Offer queue and offer timeout/rejection states.
- Accepted delivery summary.
- Pickup verification.
- Navigation handoff and route context.
- Delivery confirmation and proof collection.
- Active, completed, cancelled, and failed deliveries.
- Earnings, payout, and delivery history.
- Chat, notifications, support, and emergency escalation.
- Offline/reconnect behavior without inventing server state.

## Admin portal matrix

The admin surface must be a distinct responsive web portal or an explicitly separated admin route set. It must include:

- operations dashboard;
- customer, merchant, provider, captain, and staff lookup;
- merchant/provider/captain approval and suspension;
- order and appointment oversight;
- payment, refund, commission, payout, and dispute operations;
- loyalty and promotion controls;
- review moderation;
- content and banner management;
- launch-city and service-availability configuration;
- support-case workflow;
- audit log and role-safe settings.

No admin operation may require raw SQL for routine support work.

## Cross-application quality contract

### Motion

- Use motion only for navigation continuity, state change, skeleton-to-content transition, and action feedback.
- Respect reduced-motion settings.
- Avoid long or blocking animation in ordering, payment, dispatch, or emergency flows.

### Accessibility

- Minimum 48 px touch targets.
- Semantic labels for icon-only controls.
- Scalable text without clipped critical content.
- Sufficient contrast in light and dark modes.
- Keyboard-safe forms.
- Screen-reader announcement for validation, payment, order, appointment, and dispatch status changes.
- Do not communicate status by colour alone.

### Consistency

- One terminology set for order and appointment states.
- One price, discount, tax, and fee presentation model.
- One date/time and currency formatting layer.
- One loading/error/empty/offline pattern per surface type.
- One analytics event vocabulary for equivalent actions across applications.

## End-to-end UI QA gates

### Static gates

- TypeScript typecheck passes for both Expo applications.
- Lint passes for both Expo applications.
- UI unit tests pass.
- No hard-coded secrets or service-role keys.
- No direct service URLs when the API gateway contract exists.
- No raw colours in new screen code outside the token layer.

### Device gates

- Android small phone, common phone, and tablet widths.
- iOS compact and modern notched widths where available.
- Light and dark mode.
- Text scaling at 100%, 130%, and 160%.
- Reduced motion enabled.
- Intermittent network and offline recovery.
- Keyboard open on every form.

### Connected journey gates

1. Guest discovery to authenticated checkout.
2. Merchant product creation to customer purchase.
3. Customer payment to merchant fulfilment.
4. Merchant-ready order to captain assignment.
5. Captain pickup to delivered order.
6. Delivered order to loyalty and review.
7. Customer appointment booking to provider completion and invoice.
8. Customer support case to admin resolution.
9. Refund/dispute visibility across customer, merchant, and admin surfaces.
10. Push/deep-link navigation into the correct authenticated destination.

## Definition of done

A surface is complete only when:

- all required screens are implemented;
- data comes from approved APIs or explicit unavailable-state UI;
- loading, empty, error, offline, unauthorized, and success states exist;
- accessibility labels and target sizes are verified;
- light/dark and responsive layouts are verified;
- typecheck, lint, tests, and connected journey checks pass;
- no unrelated backend or deployment behavior is changed.
