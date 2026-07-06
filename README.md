# MyPet — On-Demand Pet Care & Store Marketplace

MyPet is a premium, multi-service on-demand pet care platform and marketplace (Blinkit/Swiggy model for pet supplies, and Urban Company/Zocdoc model for veterinary/grooming appointments). 

The platform consists of a distributed Spring Boot + Kotlin microservices backend, shared storage and streaming infrastructure, and two mobile applications built with Expo SDK 56.

---

## 🏗️ System Architecture

```mermaid
graph TD
    classDef gateway fill:#8e44ad,stroke:#fff,stroke-width:2px,color:#fff;
    classDef service fill:#3498db,stroke:#fff,stroke-width:1px,color:#fff;
    classDef infra fill:#2ecc71,stroke:#fff,stroke-width:1px,color:#fff;
    classDef client fill:#e67e22,stroke:#fff,stroke-width:1px,color:#fff;

    subgraph Clients
        C1["Customer App (Expo RN)"]:::client
        C2["Merchant & Captain App"]:::client
    end

    subgraph API Gateway Layer
        GW["API Gateway (8080)"]:::gateway
    end

    subgraph Service Layer (Spring Boot + Kotlin)
        DS["Discovery Service (Eureka)"]:::service
        PROV["Provider Service (8081)"]:::service
        CAT["Catalog Service (8082)"]:::service
        APP["Appointment Service (8083)"]:::service
        ORD["Order Service (8084)"]:::service
        PAY["Payment Service (8090)"]:::service
        DISP["Dispatch Service (8086)"]:::service
        NOTIF["Notification Service (8087)"]:::service
        REV["Review Service (8085)"]:::service
        CHAT["Chat Service (8088)"]:::service
        CONT["Content Service (8092)"]:::service
    end

    subgraph Datastores & Infrastructure
        DB[("PostgreSQL (5433)")]:::infra
        RD[("Redis (6380)")]:::infra
        KF[("Apache Kafka (9092)")]:::infra
        PROM["Prometheus (9095)"]:::infra
        GRAF["Grafana (3005)"]:::infra
    end

    %% Client communication
    C1 -->|REST / WebSockets| GW
    C2 -->|REST / WebSockets| GW

    %% Gateway Routing
    GW --> PROV
    GW --> CAT
    GW --> APP
    GW --> ORD
    GW --> PAY
    GW --> DISP
    GW --> CHAT
    GW --> CONT

    %% Discovery Registry
    PROV & CAT & APP & ORD & PAY & DISP & NOTIF & REV & CHAT & CONT -->|Register| DS

    %% Database schemas
    PROV & CAT & APP & ORD & PAY & DISP & REV & CONT -->|JDBC / JPA| DB
    
    %% Cache & Pub/Sub
    PAY & PROV -->|Cache| RD
    ORD & DISP & NOTIF & PAY -->|Events| KF

    %% Monitoring
    PROM -->|Metrics| PROV & PAY & APP & ORD
    GRAF -->|Visualize| PROM
```

---

## 📁 Repository Structure

```
Mypet/
├── apps/
│   ├── customer-app/            # Customer mobile app (Expo Go / standalone APK)
│   └── merchant-captain-app/    # Merchant/Captain unified app (Expo Go / standalone APK)
├── backend/
│   ├── api-gateway/             # Spring Cloud Gateway (Port 8080)
│   ├── discovery-service/       # Netflix Eureka Service Registry (Port 8761)
│   ├── provider-service/        # Merchant provider profiles, ratings, and commission (Port 8081)
│   ├── catalog-service/         # Product catalog and pet care services inventory (Port 8082)
│   ├── appointment-service/     # Doctor consultation and grooming appointments booking (Port 8083)
│   ├── order-service/           # On-demand store product checkout and order flow (Port 8084)
│   ├── payment-service/         # Razorpay checkout, route accounts, transfers, clawbacks (Port 8090)
│   ├── dispatch-service/        # Matchmaking algorithms assigning captains to orders (Port 8086)
│   ├── review-service/          # Ratings and user review system (Port 8085)
│   ├── chat-service/            # Messaging between customer, merchant, and captain (Port 8088)
│   ├── notification-service/    # Push notifications delivery service (Port 8087)
│   ├── content-service/         # Guides, landing banners, static metadata (Port 8092)
│   └── common/                  # Shared security, filters, and utilities library
└── infra/
    ├── docker-compose.yml       # Local database, queue, cache, and monitoring services
    ├── docker-compose.replicas.yml # Bounded scale service configurations for local development
    └── prometheus.yml           # Monitoring metrics collection config
```

---

## 🚀 Setup & Execution Manual

### Prerequisites
* **Java**: JDK 21 (Temurin / Android Studio Java Home recommended)
* **Node.js**: v18+ and npm
* **Docker**: Docker Desktop with Compose V2

---

### Step 1: Compile the Backend Services
Before building Docker images, compile the Kotlin source code into runnable Spring Boot Fat JARs on the host machine to bypass heavy memory compilation overhead:
```bash
cd backend
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew bootJar
```

---

### Step 2: Spin Up the Infrastructure and Services
Initialize the complete 19-container stack (relational schemas, cache, queue, monitoring, gateway, and all microservices):
```bash
cd ../
docker compose -f infra/docker-compose.yml -f infra/docker-compose.replicas.yml up -d --build
```

#### Ports Overview (Host Bindings)
* **API Gateway**: `8080` (Consolidated Entrypoint)
* **PostgreSQL**: `5433` (isolated schemas per domain)
* **Redis Cache**: `6380`
* **Apache Kafka**: `9092`
* **Prometheus**: `9095`
* **Grafana Dashboard**: `3005`
* **Payment Service**: `8090` (Exposed directly for local API and Webhook testing)

---

### Step 3: Run Mobile Apps
Navigate into the respective application folder and start the Expo Metro Bundler:
```bash
# Customer Mobile App
cd apps/customer-app
npm install
npx expo start

# Merchant/Captain Unified Mobile App
cd ../merchant-captain-app
npm install
npx expo start
```
Scan the printed terminal QR code with your iOS Camera or Android Expo Go app.

---

## 💸 Automatic Commission & Payout System (Razorpay Route)

Implemented in Sprint 22, the payouts architecture deducts the platform's commission automatically and triggers direct payouts to merchants and captains immediately.

### 1. Linked Accounts Creation
Administrators link merchant and captain bank details directly to Razorpay's Route API.
* **Endpoint**: `POST /api/v1/payments/linked-accounts`
* **Payload**:
```json
{
  "payeeUserId": "98765432-1234-1234-1234-123456789abc",
  "payeeRole": "MERCHANT",
  "accountNumber": "123456789",
  "ifsc": "UTIB0001234",
  "businessName": "Test Store Inc",
  "email": "owner@store.com"
}
```

### 2. Commission Retained Ledger
* Calculates dynamic commission per provider by checking their `commissionPct` via the **Provider Service** (utilizing a local JVM map cache to minimize duplicate HTTP overhead).
* Logs platform earnings per provider, per period into the `platform_commission_ledger` table.
* Deducts the commission (`original_amount * commission_pct / 100`) and issues the rest to the payee.

### 3. Stateful Clawback Netting (Option 2.B)
* When a payment refund occurs, Razorpay triggers a `transfer.reversed` webhook event.
* The system catches the event, updates the Payout status to `REVERSED`, and increments the payee's stateful `pending_clawback_balance` on the `LinkedAccount` table.
* On the next payout calculation cycle, the system nets this clawback balance out:
  * `netAmount = calculatedPayout - pendingClawbackBalance`
  * If `netAmount < 0`, the payee is issued a `0.00` payout, and the remaining deficit stays on `pending_clawback_balance`.
  * If `netAmount >= 0`, the payout is issued for `netAmount` and the `pending_clawback_balance` resets to `0.00`.

### 4. Automatic Webhooks
* **Webhook path**: `/api/v1/payments/webhook`
* **Signature Bypass**: For frictionless local sandbox testing, signature checks are bypassed if `razorpayWebhookSecret` is left blank in local environments.
* **Webhook Events**:
  * `transfer.processed` ➡️ Updates payout status to `PAID`.
  * `transfer.failed` ➡️ Updates payout status to `FAILED`.
  * `transfer.reversed` ➡️ Updates payout status to `REVERSED` and increments clawback balance.

---

## 🧪 Testing Guidelines

### Unit and Service Tests
Execute standard unit and service mock tests inside the `backend` folder:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :payment-service:test
```

### End-to-End Integration Verification Script
Run the automated python script verifying route accounts registration, payouts calculation, webhook reversals, and subsequent netting:
```bash
python3 backend/verify_sprint22.py
```
