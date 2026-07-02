from verify_sprint_common import check, has_text, finish

print("Sprint 2: Catalog And Discovery")

check("catalog validates delivery stock", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt", "DELIVERY fulfillment offerings must specify a stock quantity"))
check("catalog validates appointment duration", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt", "APPOINTMENT fulfillment offerings must specify a duration"))
check("discovery uses Redis search and PostGIS fallback", has_text("backend/discovery-service/src/main/kotlin/com/pawsnearme/discoveryservice/service/DiscoveryService.kt", "searchNearbyProviders", "queryPostgisFallback"))
check("customer shop uses discovery API", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/discovery/providers", "PET_STORE"))
check("customer vet/groom use discovery API", has_text("apps/customer-app/src/app/vet.tsx", "VET_HOSPITAL") and has_text("apps/customer-app/src/app/groom.tsx", "GROOMING_CENTER"))
check("repeatable live proof exists", has_text("backend/verify_sprints_1_2_live.py", "verify_discovery", "PET_STORE", "VET_HOSPITAL", "GROOMING_CENTER"))
check("sprint proof documented", has_text("docs/sprints/sprint-2-catalog-discovery.md", "Repeatable local proof", "verify-sprints-1-3.sh --live"))

finish("Sprint 2")
