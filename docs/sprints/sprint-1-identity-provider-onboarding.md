# Sprint 1: Identity, Auth, Provider Onboarding

## Goal

Customers and merchants can sign up, and merchants can submit providers for approval.

## Acceptance Checklist

- [x] Supabase Auth user creates or syncs an `identity.profiles` row.
- [x] Authenticated default delivery address API exists and uses gateway-injected `X-User-Id`.
- [x] Role claims and `identity.user_roles` are consistent.
- [x] Customer app login/signup works against real Supabase config.
- [x] Merchant app onboarding adapts fields by provider type.
- [x] Provider state machine supports `DRAFT -> PENDING_APPROVAL -> ACTIVE`.
- [x] Document upload uses Supabase Storage or a documented pre-signed upload flow.
- [x] Provider approval is admin-only with no hardcoded admin API key.
- [x] Gateway injects identity headers from JWT only and strips client-supplied identity headers.

## Verification

- Create customer and merchant users.
- Create a customer default address with a real bearer token and confirm another user cannot create it for them.
- Create a provider, upload documents, submit for approval, approve as admin.
- Confirm non-admin approval attempts are rejected.

## Proof Notes

- Live API proof captured on July 1, 2026 using real Supabase Auth users and gateway bearer tokens.
- Customer user `ce7d1d92-831e-44e4-90f1-e02821454106`, merchant user `e9a9199f-cdfc-4b1c-99b3-7c697d55e8bd`, and admin user `6aa6d558-d458-422f-a2f3-79b08be04d10` were created in Supabase Auth.
- Local backend auth mirror rows were inserted for those Supabase users because the current local backend database does not automatically receive remote Supabase `auth.users` rows.
- `identity.profiles` and `identity.user_roles` each had 3 matching rows for the proof users.
- Default customer address `97fe0b7d-6a5a-4198-828d-4131274fbb6a` was created and fetched through `/api/v1/addresses/default`.
- Provider `a01014fc-ead6-4a53-8b99-f499462f7263` moved `DRAFT -> PENDING_APPROVAL -> ACTIVE`.
- Provider document upload URL and local upload-file flow succeeded; one provider document row was attached.
- Merchant approval attempt was rejected with 403; admin approval succeeded.
- Repeatable local proof: `scripts/verify-sprints-1-3.sh --live`.
- Production auth sync is implemented through `/api/v1/profiles/sync`. The gateway strips spoofed identity/profile headers, injects JWT-derived user id, role, email, name, and phone, and provider-service creates or updates `identity.profiles` plus `identity.user_roles` idempotently.
- Legacy local foreign keys from `identity.profiles`, `identity.user_roles`, `identity.addresses`, and `identity.pets` to `auth.users` are removed so remote Supabase JWT subjects do not need a manual local auth mirror.
- Mobile apps call profile sync after a real Supabase session is available, so signup/login no longer depends on manual local profile creation.
- Live verifier proof creates customer/merchant/admin profiles through the sync endpoint, creates a default address using authenticated user context, uploads provider document media through the local pre-signed flow, submits provider approval, confirms non-admin approval fails, and confirms admin approval activates the provider.
- Repeatable proof captured on July 2, 2026 with run ID `503a06ec`.
  - Customer: `7100ad3b-9649-489c-8689-784ca9c4ff8f`
  - Merchant: `9ed121a3-c627-4f3a-b093-ec7ae5e3a6e6`
  - Admin: `0f9b553c-e08b-4aa2-af54-ce855612b20f`
  - Approved providers: shop `cdeef564-83c2-4f59-9873-0c3e448a7cf8`, vet `40639d1f-163f-453b-a28f-5ebc7237dc25`, groom `16f4976d-dbd3-4c50-a815-7d658c083300`.
