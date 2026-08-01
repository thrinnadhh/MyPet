# M2 Internal Modules Plan

## Overview

Link the twelve business-service Gradle projects into `mypet-application` as dormant library modules while preserving the current distributed deployment. M2 establishes explicit module identity and enforceable boundaries without activating databases, Redis, Kafka, gRPC servers, schedulers, controllers, or legacy `@SpringBootApplication` entry points inside the consolidated JVM.

## Project type

Backend architecture refactor — Kotlin, Spring Boot 3.5, Gradle multi-project build.

## Success criteria

- `mypet-application` has non-transitive project dependencies on all twelve business modules and `common`.
- Each business module owns one descriptor implementing the shared module contract.
- The consolidated application exposes one immutable catalog containing exactly the twelve modules.
- Legacy service application classes remain packaged but are not registered as Spring beans in the consolidated runtime.
- Automated architecture tests reject cross-module Gradle dependencies and direct imports of another module's repository package.
- `mypet-application` continues to start without PostgreSQL, Redis, Kafka, gRPC, or service-specific configuration.
- Existing service boot jars, Docker Compose deployment, public APIs, and mobile applications remain unchanged.

## Specialist lanes

| Lane | Responsibility | Execution |
|---|---|---|
| project-planner / architecture | dependency graph, scope, rollback | serial design before shared-file writes |
| backend-specialist | Gradle linkage, descriptors, Spring catalog | implementation |
| database-architect | confirm no schema/Flyway ownership change | read-only review |
| security-auditor | confirm no trust/auth boundary change | read-only review |
| test-engineer | runtime-isolation and source-boundary tests | implementation and CI |
| devops-engineer | preserve boot jars and distributed deployment | regression verification |

## Dependency graph

1. Shared `BusinessModuleDescriptor` contract in `common`.
2. Twelve module-owned descriptor objects, independently writable.
3. Non-transitive module dependencies in `mypet-application`.
4. Application-owned immutable catalog and Actuator info contribution.
5. Runtime-isolation and architecture-boundary tests.
6. Full backend, mobile, production-hardening, and clean-volume smoke verification.

## Rollback

Revert the M2 branch or PR. No database migration, infrastructure removal, public API modification, or service-entry-point deletion is included.

## Verification

- `./gradlew clean test --no-daemon`
- generated-artifact and static production gates
- customer mobile lint/typecheck/tests
- merchant/captain mobile lint/typecheck/tests
- clean-volume Full Stack Smoke
- `:mypet-application:bootJar`
