# Sprint 2: Catalog And Discovery

## Goal

Customers can browse real nearby providers and offerings from live backend data.

## Acceptance Checklist

- [x] Merchant can create/edit products for `DELIVERY` providers.
- [x] Merchant can create/edit services for `APPOINTMENT` providers.
- [x] Catalog validation enforces stock fields for products and duration fields for services.
- [x] Discovery service returns active nearby providers using PostGIS.
- [x] Redis geo cache strategy is documented and tested.
- [x] `ProviderApproved` indexes newly active providers for discovery.
- [x] Customer Shop, Vet, and Groom tabs use live discovery results, not silent mock fallbacks.

## Verification

- Seed active providers and offerings.
- Query discovery by location and provider type.
- Open all three customer tabs and verify live provider/offering data.

## Proof Notes

- Static sprint proof: `python3 backend/verify_sprint2.py`.
- Repeatable local proof: `scripts/verify-sprints-1-3.sh --live`.
- Live verifier creates active `PET_STORE`, `VET_HOSPITAL`, and `GROOMING_CENTER` providers, creates one delivery product and two appointment services, confirms delivery products require stock, and confirms discovery returns each active provider type from live backend data.
- Repeatable proof captured on July 2, 2026 with run ID `503a06ec`.
  - Shop provider `cdeef564-83c2-4f59-9873-0c3e448a7cf8`, offering `9edfdc01-4f1a-4158-8e69-e4e702a8d8c5`.
  - Vet provider `40639d1f-163f-453b-a28f-5ebc7237dc25`, offering `ce1b6e84-3000-44a7-8366-ef7eaefc115c`.
  - Groom provider `16f4976d-dbd3-4c50-a815-7d658c083300`, offering `cb93f76e-fcc4-4e79-b505-b18cb427b433`.
- Customer app Shop, Vet, and Groom screens call `/api/v1/discovery/providers` with live provider types and fail visibly when demo mode is disabled instead of silently returning mock success.
