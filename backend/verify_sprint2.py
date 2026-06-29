from verify_sprint_common import check, has_text, finish

print("Sprint 2: Catalog And Discovery")

check("catalog validates delivery stock", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt", "DELIVERY fulfillment offerings must specify a stock quantity"))
check("catalog validates appointment duration", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt", "APPOINTMENT fulfillment offerings must specify a duration"))
check("discovery uses Redis search and PostGIS fallback", has_text("backend/discovery-service/src/main/kotlin/com/pawsnearme/discoveryservice/service/DiscoveryService.kt", "searchNearbyProviders", "queryPostgisFallback"))
check("customer shop uses discovery API", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/discovery/providers", "PET_STORE"))
check("customer vet/groom use discovery API", has_text("apps/customer-app/src/app/vet.tsx", "VET_HOSPITAL") and has_text("apps/customer-app/src/app/groom.tsx", "GROOMING_CENTER"))

finish("Sprint 2", [
    "Seed active providers and offerings.",
    "Open Shop, Vet, and Groom with demo mode disabled and verify empty/error state is not mock data.",
])
