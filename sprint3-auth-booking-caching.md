# Sprint 3: Auth, Bookings & Pre-signed Caching

This plan details the implementation strategy for Sprint 3 of **PawsNearMe**. In this sprint, we will integrate Supabase Auth (stateless gateway verification), scaffold two new backend services (`order-service` and `appointment-service`), implement pre-signed storage uploads with custom React Native file-system image caching, replace hardcoded merchant configurations with dynamic provider lookups, and integrate slot scheduling pickers.

## Project Type
MOBILE + BACKEND (Hybrid Strategy)

## Success Criteria
1. Stateless JWT validation on API Gateway (8080) with downstream headers.
2. Complete signup/login UI and session context in both mobile apps.
3. Successful onboarding and slot inventory management mapped dynamically to the logged-in merchant.
4. Two new backend services (order-service and appointment-service) fully functioning on ports 8084 & 8085.
5. Pre-signed uploads and client-side image caching operational on mobile.
6. DateTimePicker slot schedule management.

## Tech Stack
- Frontend: React Native + Expo (TypeScript), `@supabase/supabase-js`, `expo-file-system`
- Backend: Kotlin + Spring Boot 3.x, Spring Cloud Gateway, Spring Security OAuth2, Redis (Distributed Lock), Apache Kafka

## Proposed File Structure Changes

### API Gateway (`backend/api-gateway`)
- [MODIFY] `build.gradle.kts`
- [MODIFY] `src/main/resources/application.yml`
- [NEW] `src/main/kotlin/com/pawsnearme/apigateway/config/SecurityConfig.kt`
- [NEW] `src/main/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilter.kt`

### Customer App (`apps/customer-app`)
- [NEW] `src/utils/supabase.ts`
- [NEW] `src/context/AuthContext.tsx`
- [NEW] `src/app/login.tsx`
- [NEW] `src/components/CachedImage.tsx`

### Merchant Captain App (`apps/merchant-captain-app`)
- [NEW] `src/utils/supabase.ts`
- [NEW] `src/context/AuthContext.tsx`
- [NEW] `src/app/login.tsx`
- [NEW] `src/components/CachedImage.tsx`
- [MODIFY] `src/app/onboarding.tsx`
- [MODIFY] `src/app/inventory.tsx`

### Order Service (`backend/order-service`) [NEW]
- [NEW] `build.gradle.kts`
- [NEW] `src/main/kotlin/...`
- [NEW] `src/main/resources/application.yml`

### Appointment Service (`backend/appointment-service`) [NEW]
- [NEW] `build.gradle.kts`
- [NEW] `src/main/kotlin/...`
- [NEW] `src/main/resources/application.yml`

---

## Task Breakdown

### Task 1: Auth & Gateway Layer (P0)
- **Agent**: `backend-specialist` + `security-auditor`
- **Goal**: Statelessly verify JWT in Gateway, inject `X-User-Id` & `X-User-Role` downstream.
- **Verification**: Run request via Gateway with valid and invalid JWTs, check gateway log headers.

### Task 2: Mobile Auth Integration (P1)
- **Agent**: `mobile-developer`
- **Goal**: Authenticate users, load AuthContext, and secure views in Customer and Merchant apps.
- **Verification**: User can login, navigate to home/onboarding, check session values console logs.

### Task 3: Scaffold Order Service & Appointment Service (P1)
- **Agent**: `backend-specialist` + `database-architect`
- **Goal**: Create the two new microservices on ports 8084 and 8085 with standard JPA routes.
- **Verification**: Run `./gradlew bootRun` for both services, query status endpoints.

### Task 4: Slot Locking & Kafka Events (P1)
- **Agent**: `backend-specialist`
- **Goal**: Enforce single-booking slot locks in Redis, publish Kafka lifecycle events on success.
- **Verification**: Run concurrent bookings on same slot, check Redis lock key, assert lock gets released or denied.

### Task 5: Dynamic Onboarding & Inventory (P2)
- **Agent**: `mobile-developer`
- **Goal**: Fetch dynamic provider options from DB under logged-in merchant to load in inventory.
- **Verification**: Logged in merchant registers new store, store appears in dropdown on inventory page.

### Task 6: Pre-signed Uploads & Caching (P2)
- **Agent**: `mobile-developer` + `backend-specialist`
- **Goal**: Upload docs/images to Supabase Storage via pre-signed URL and cache files locally using `CachedImage`.
- **Verification**: File uploaded, subsequent renders use local cache path instead of URL.

### Task 7: DateTimePicker for Slots (P2)
- **Agent**: `mobile-developer`
- **Goal**: UI DateTime picker component integrated.
- **Verification**: Touch slot picker, calendar modal pops up, returns selected date.

---

## Phase X: Final Verification
- Run: `python .agents/scripts/verify_all.py`
- Run local test suite: `./gradlew test`
- Mobile build check: `npx tsc --noEmit`
