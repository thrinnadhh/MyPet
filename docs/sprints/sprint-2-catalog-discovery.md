# Sprint 2: Catalog And Discovery

## Goal

Customers can browse real nearby providers and offerings from live backend data.

## Acceptance Checklist

- [ ] Merchant can create/edit products for `DELIVERY` providers.
- [ ] Merchant can create/edit services for `APPOINTMENT` providers.
- [ ] Catalog validation enforces stock fields for products and duration fields for services.
- [ ] Discovery service returns active nearby providers using PostGIS.
- [ ] Redis geo cache strategy is documented and tested.
- [ ] `ProviderApproved` indexes newly active providers for discovery.
- [ ] Customer Shop, Vet, and Groom tabs use live discovery results, not silent mock fallbacks.

## Verification

- Seed active providers and offerings.
- Query discovery by location and provider type.
- Open all three customer tabs and verify live provider/offering data.
