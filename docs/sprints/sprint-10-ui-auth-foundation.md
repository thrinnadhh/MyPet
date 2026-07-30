# Sprint S10 — Customer UI and OTP foundation

## Scope

S10 converts the customer app to guest-first discovery, a four-tab route shell, OTP-first authentication, centralized profile readiness, and reusable native UI foundations. It preserves Expo Router, Supabase Auth, the API gateway, and existing backend service boundaries.

## Implementation decisions

- `AuthProvider` restores the existing Supabase session and listens for subsequent auth-state changes. No service-role key, demo user, mock token, or automatic demo login is present.
- `AuthIntentProvider` is the central customer UI entry point for protected actions. It preserves a serializable route intent and an in-memory callback; authentication resumes the intended destination without serializing a write callback into route parameters.
- Phone and email OTP share one typed state machine for input validation, send, verification, resend, expiry, rate-limit, network, cancellation, and recovery states.
- First verification collects a display name when the Supabase user does not already have one.
- Profile completeness is a pure policy module. Post-auth completion requires a display name; checkout additionally requires a verified mobile number and a server-returned default delivery address. Email remains optional.
- Public provider discovery is data-driven by provider type and launch-market configuration. Tirupati is the first market record, not a route-level architectural constant.
- Legacy `/home`, `/profile`, `/explore`, `/shop`, `/vet`, and `/groom` routes redirect or compose into the new tab and nested-route foundations so existing navigation does not break during migration.
- English and Telugu resources are established while existing Hindi support is retained.

## Design foundation

- Typed royal-blue, amber, emerald, cool-white, light-mode, and dark-mode colour tokens.
- Inter typography, a 4 px spacing scale, 8/16/24 px radii, subtle elevations, and semantic interaction states.
- Reusable screen shell, app bar, location header, search field, chips, section headers, entity/provider/product cards, badges, star progress, loading/empty/error/offline/unauthenticated states, bottom-sheet foundation, and sticky CTA.
- Touch targets, safe areas, scalable text, keyboard handling, semantic labels, and reduced-motion support are centralized in the foundation components.

## Security and data invariants

- Marketplace APIs are called through the configured API gateway.
- Protected write callbacks are never serialized into route parameters.
- Checkout readiness is derived from verified Supabase identity plus the gateway-backed default-address endpoint.
- S10 does not introduce client-authoritative discounts, loyalty balances, provider IDs, customer IDs, payment results, mock sessions, or sample API responses.
- The customer bundle uses the Supabase public client configuration only; no service-role credential is referenced.

## Validation commands

```bash
cd apps/customer-app
npm ci
npm run typecheck
npm run lint
npm run test:s10
```

The S10 test suite covers phone/email OTP send, verification, error and resend handling, auth-intent serialization/resumption, profile-completeness policy, customer tab definitions, and English/Telugu/Hindi resource loading.

## Pet identity boundary

S10 deliberately does not submit appointment holds until a real pet profile is selected. The current backend requires a distinct `petId`, and no customer pet-list API exists on the approved base. The UI therefore authenticates and preserves the booking intent, then explains that a saved pet is required instead of fabricating `petId` from the customer ID. A later bounded sprint must expose pet profiles through the gateway before appointment submission is enabled.
