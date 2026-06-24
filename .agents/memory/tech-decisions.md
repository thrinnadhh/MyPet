---
type: reference
created: 2026-06-24
updated: 2026-06-24
---

# PawsNearMe Tech Decisions & Core Architecture

This document tracks the core architectural decisions and data abstraction conventions established in the initial project specifications.

## 1. Core Abstractions & Domain Model
- **Provider Generalization**: Store (`PET_STORE`), Hospital (`VET_HOSPITAL`), and Groomer (`GROOMING_CENTER`) are all represented by a single `Provider` model, differentiated by `provider_type` and `fulfillment_type`.
- **Fulfillment Types**: 
  - `DELIVERY` (Physical products, Captain-fulfilled deliveries, requires stock tracking).
  - `APPOINTMENT` (In-person customer visits, time slots, duration based, no Captain involved).
- **Master Catalog**: Physical products and service offerings share a single generalized `Offering` table with nullable type-specific columns (`stock_quantity` for `DELIVERY`, `duration_minutes` for `APPOINTMENT`).

## 2. Infrastructure & System Design
- **Services Architecture**: Independent Kotlin + Spring Boot microservices behind a Spring Cloud Gateway, communicating asynchronously via Kafka events.
- **Database Boundary**: A single Supabase (PostgreSQL) project using a strict **schema-per-service** boundary (e.g., `identity.*`, `providers.*`, `catalog.*`, `orders.*`, `appointments.*`). Services access their respective schemas with dedicated JDBC roles and never write/query across schemas directly.
- **Distributed Transactions**: Handled using the orchestrator-based Saga Pattern with compensating events (e.g., restocking catalog when an order is cancelled).
- **Slot Calendar & Locking**: Vet/grooming slot double-bookings are avoided via a Redis distributed lock (with a 5-minute TTL) during slot reservation, backed by a unique index constraint in the PostgreSQL `appointments.appointments` table.
