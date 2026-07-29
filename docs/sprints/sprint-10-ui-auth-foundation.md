# Sprint S10 — Customer UI and OTP foundation

## Scope

S10 converts the customer app to guest-first discovery, a four-tab route shell, OTP-first authentication, centralized profile readiness, and optional account-level Order Protection with device-local verification material. It preserves Expo Router, Supabase Auth, the API gateway, and existing backend service boundaries.

## Implementation decisions

- `AuthProvider` restores Supabase sessions from chunked platform SecureStore storage on native and browser local storage on web. No service-role key, demo user, mock token, or automatic demo login is present.
- `AuthIntentProvider` is the only customer UI entry point for protected actions. It preserves a serializable route intent and an in-memory callback; process restarts safely resume the destination without replaying an uncommitted write.
- `ProtectionProvider` is separate from authentication. Protection never replaces or disables the OTP session. The enabled preference is stored server-side on the customer profile; per-user PIN/biometric material stays in `WHEN_UNLOCKED_THIS_DEVICE_ONLY` secure storage with a PIN fallback.
- Fresh OTP authorization is transient and is not restored with the session. The gateway strips spoofed `X-User-Auth-Time`, derives it from the verified Supabase JWT, and the provider service rejects preference mutations older than ten minutes. Enrollment, disabling, recovery, and enrollment on another device require a fresh verification.
- Profile completeness is a pure policy module. First verification requires a display name; checkout additionally requires a verified mobile and a server-returned default delivery address.
- Public provider discovery is data-driven by provider type and launch-market configuration. Tirupati is the first market record, not a route-level constant.
- Legacy `/home`, `/profile`, and `/explore` routes redirect to the new tab routes. `/shop`, `/vet`, and `/groom` remain public and use reusable discovery screens without production fallback data.

## Security invariants

- Marketplace APIs are called only through the configured API gateway.
- Protected write callbacks are never serialized into route parameters.
- Order Protection keys are namespaced by authenticated Supabase user ID to prevent cross-account device leakage. A server-enabled account with no local key material fails closed into fresh-OTP device enrollment.
- Checkout readiness is derived from verified Supabase identity plus the gateway-backed default-address endpoint.
- S10 does not introduce client-authoritative discounts, loyalty balances, provider IDs, customer IDs, or payment results.

## Validation commands

```bash
cd apps/customer-app
npm ci
npm run typecheck
npm run lint
npm run test:s10
```

The repository CI additionally runs the complete backend Gradle build/tests, provider-service migration validation through Flyway startup/tests, and validates the merchant/captain app.

## Pet identity boundary

S10 deliberately does not submit appointment holds until a real pet profile is selected. The current backend requires a distinct `petId`, and no customer pet-list API exists on the approved base. The UI therefore authenticates and preserves the booking intent, then explains that a saved pet is required instead of fabricating `petId` from the customer ID. A later bounded sprint must expose pet profiles through the gateway before appointment submission is enabled.
