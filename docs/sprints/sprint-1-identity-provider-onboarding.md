# Sprint 1: Identity, Auth, Provider Onboarding

## Goal

Customers and merchants can sign up, and merchants can submit providers for approval.

## Acceptance Checklist

- [x] Supabase Auth user creates or syncs an `identity.profiles` row.
- [x] Authenticated default delivery address API exists and uses gateway-injected `X-User-Id`.
- [x] Role claims and `identity.user_roles` are consistent.
- [ ] Customer app login/signup works against real Supabase config.
- [ ] Merchant app onboarding adapts fields by provider type.
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
- Remaining Sprint 1 product proof: run the same signup/login/onboarding path through the customer and merchant mobile UI with demo mode disabled.
