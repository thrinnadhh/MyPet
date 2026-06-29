# Sprint 1: Identity, Auth, Provider Onboarding

## Goal

Customers and merchants can sign up, and merchants can submit providers for approval.

## Acceptance Checklist

- [ ] Supabase Auth user creates or syncs an `identity.profiles` row.
- [ ] Role claims and `identity.user_roles` are consistent.
- [ ] Customer app login/signup works against real Supabase config.
- [ ] Merchant app onboarding adapts fields by provider type.
- [ ] Provider state machine supports `DRAFT -> PENDING_APPROVAL -> ACTIVE`.
- [ ] Document upload uses Supabase Storage or a documented pre-signed upload flow.
- [ ] Provider approval is admin-only with no hardcoded admin API key.
- [ ] Gateway injects identity headers from JWT only and strips client-supplied identity headers.

## Verification

- Create customer and merchant users.
- Create a provider, upload documents, submit for approval, approve as admin.
- Confirm non-admin approval attempts are rejected.
