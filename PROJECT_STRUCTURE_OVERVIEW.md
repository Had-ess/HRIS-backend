# HRIS Backend Project Structure Overview

## Summary
- Spring Boot application rooted at `hris/`
- Base package: `com.hris`
- Main class: `hris/src/main/java/com/hris/HrisApplication.java`
- Build tool: Maven (`hris/pom.xml`)
- Persistence: Spring Data JPA + PostgreSQL + Flyway
- Security: Spring Security OAuth2 resource server with Keycloak JWT conversion
- Messaging: RabbitMQ

## Project Structure

```text
backend/
├── PROJECT_STRUCTURE_OVERVIEW.md
└── hris/
    ├── pom.xml
    ├── docker-compose.yml
    ├── init-db.sql
    ├── keycloak/
    │   └── realm-export.json
    └── src/
        ├── main/
        │   ├── java/com/hris/
        │   │   ├── HrisApplication.java
        │   │   ├── admin/
        │   │   ├── analytics/
        │   │   ├── approval/
        │   │   ├── auth/
        │   │   ├── common/
        │   │   ├── config/
        │   │   ├── dashboard/
        │   │   ├── health/
        │   │   ├── leave/
        │   │   ├── notification/
        │   │   ├── organisation/
        │   │   └── security/
        │   └── resources/
        │       ├── application.yml
        │       ├── db/migration/
        │       └── i18n/
        └── test/
            └── java/com/hris/
                ├── admin/
                ├── approval/
                ├── auth/
                ├── dashboard/
                ├── leave/
                ├── notification/
                ├── organisation/
                ├── security/
                └── support/
```

## Main Packages

### `com.hris.admin`
- Administrative request domain.
- Contains `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, and `enums`.

### `com.hris.analytics`
- Analytics and audit-log domain.
- Contains REST controllers plus analytics entities, repositories, DTOs, enums, and services.

### `com.hris.approval`
- Approval workflow domain for step-based approvals.
- Contains workflow controllers, services, repositories, entities, DTOs, mappers, and enums.

### `com.hris.auth`
- User, employee, department, role, and permission management.
- This is the largest module and includes controllers, services, repositories, entities, DTOs, mappers, and enums.

### `com.hris.common`
- Cross-cutting support code.
- Includes `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, `ScopeFilter`, and custom exceptions.

### `com.hris.config`
- Application configuration and infrastructure beans.
- Current config classes:
  - `SecurityConfig`
  - `JacksonConfig`
  - `RabbitMQConfig`
  - `RetryConfig`
  - `SchedulingConfig`

### `com.hris.dashboard`
- Dashboard-specific read models and aggregation logic.
- Contains `DashboardController`, `DashboardService`, and dashboard DTOs.

### `com.hris.health`
- Custom health checks.
- Currently contains `StorageHealthIndicator` for attachment storage readiness.

### `com.hris.leave`
- Leave management domain.
- Contains controllers, services, repositories, entities, DTOs, mappers, and enums for requests, balances, policies, types, and file attachments.

### `com.hris.notification`
- Notification domain and messaging consumers/publishers.
- Contains controller, services, repositories, entities, DTOs, mapper, and enums.

### `com.hris.organisation`
- Organisation/project scheduling domain.
- Contains project and work schedule controllers, services, repositories, entities, DTOs, mappers, and enums.

### `com.hris.security`
- Security support classes outside `config`.
- Includes:
  - `JwtAuthenticationFilter`
  - `KeycloakJwtConverter`
  - `PermissionAuthorizationService`
  - `SecurityUtils`

## Layer Responsibilities

- Controllers: REST endpoints and request/response handling.
- Services: business logic and orchestration across repositories, mappers, and external infrastructure.
- Repositories: Spring Data JPA data access interfaces.
- Entities: JPA domain objects stored in PostgreSQL.
- DTOs: request and response contracts exposed by the API.
- Mappers: entity/DTO mapping helpers.
- Config: security, Jackson, retry, scheduling, and RabbitMQ bean configuration.

## Important Files

### Application Entry Point
- `hris/src/main/java/com/hris/HrisApplication.java`
  - `@SpringBootApplication`
  - `@EnableJpaAuditing`

### Configuration
- `hris/src/main/resources/application.yml`
  - Datasource, JPA, Flyway, RabbitMQ, OAuth2 resource server, multipart, actuator, logging, and storage settings.

### Database
- `hris/src/main/resources/db/migration/`
  - Flyway migrations `V1__init.sql` through `V4__permission_metadata.sql`.
- `hris/init-db.sql`
  - Local database bootstrap script used by Docker Compose.

### Infrastructure
- `hris/docker-compose.yml`
  - Starts PostgreSQL, RabbitMQ, and Keycloak for local development.
- `hris/keycloak/realm-export.json`
  - Imported realm definition for local Keycloak setup.

### Localization
- `hris/src/main/resources/i18n/messages_en.properties`
- `hris/src/main/resources/i18n/messages_fr.properties`

## Assumptions / Needs Review

- This overview documents the code currently checked into the repository. It does not describe any generated sources or local-only files outside `hris/`.
- `application.yml` defines `cors.allowed-origins`, but `SecurityConfig` currently hardcodes allowed origins instead of reading that property.
