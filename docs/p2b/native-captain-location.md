# P2B-01 Native Captain Location

## Scope

This change replaces the previous web-only/demo location boundary with production native location support for captains.

## Behavior

- Requests foreground location before allowing a captain to go online.
- Requests background location only for active delivery tracking.
- Publishes current coordinates through the API gateway to the dispatch service.
- Starts a foreground service on Android for background delivery tracking.
- Stops foreground and background tracking when the captain goes offline, signs out, or completes delivery.
- Keeps demo coordinates restricted to explicit demo mode.
- Fails closed when permission, location services, or a fresh coordinate are unavailable.

## Platform configuration

- Android coarse, fine, foreground-service and background-location permissions are declared.
- iOS when-in-use and always/background usage descriptions are declared.
- Expo Location and Task Manager are installed with SDK-compatible versions.

## Interlinks

Captain App → API Gateway → Dispatch Service → live location store → customer tracking, merchant operations and admin oversight.

The dispatch service remains authoritative for delivery state. Location updates do not directly transition an order or delivery.

## Validation

- TypeScript typecheck
- Expo lint
- Operational unit and safety tests
- Source guards against hardcoded production coordinates and fixed OTPs
