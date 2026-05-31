# HRIS

HRIS is a Spring Boot + Angular human resources platform with Keycloak for authentication only, PostgreSQL for application data, RabbitMQ for async events, and a database-owned authorization model based on access profiles and permissions.

## Architecture Summary

- Authentication: Keycloak OIDC/JWT only
- Authorization: HRIS database tables for `AccessProfile`, `Permission`, `MenuItem`, `ProfilePermission`, `ProfileMenuAccess`, and `UserProfileAssignment`
- Backend: Spring Boot, Spring Security, Spring Data JPA, Flyway, RabbitMQ
- Frontend: Angular standalone components
- Database: PostgreSQL
- Messaging: RabbitMQ with retry/DLQ support

## Main Features

- Dynamic sidebar navigation and backend-enforced permissions
- User management and access profile assignment
- Teams, team hierarchy, and validation workflows
- Leave requests with approval workflow, ledger-backed balances, accrual policies, and HR calendars
- Administration requests with a separate back-office lifecycle
- Notifications, audit logs, scheduler-controlled jobs, and Analytics V2 reports

## Required Services

- PostgreSQL on `localhost:5433`
- RabbitMQ on `localhost:5672` and management UI on `localhost:15672`
- Keycloak on `localhost:8180`
- Mailpit on `localhost:1025` / `localhost:8025`

## Local Setup

1. Start infrastructure:

```powershell
cd backend/hris
docker compose up -d
```

2. Start the backend:

```powershell
cd backend/hris
mvn spring-boot:run
```

3. Start the frontend:

```powershell
cd frontend
npm install
npm start
```

## Common Commands

Backend:

```powershell
cd backend/hris
mvn -q clean test
mvn -q "-Dflyway.cleanDisabled=false" flyway:clean flyway:migrate
mvn -q flyway:info
```

Frontend:

```powershell
cd frontend
npm run build
```

## Environment Variables

Backend reads these from `backend/hris/src/main/resources/application.yml`:

- `DATASOURCE_URL`, `DATASOURCE_USER`, `DATASOURCE_PASS`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASS`, `RABBITMQ_VHOST`
- `KEYCLOAK_JWKS_URI`, `KEYCLOAK_ISSUER_URI`
- `KEYCLOAK_ADMIN_SERVER_URL`, `KEYCLOAK_ADMIN_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET`
- `APP_STORAGE_ATTACHMENTS_ROOT`, `APP_STORAGE_EXPORTS_ROOT`
- `APP_ONBOARDING_LOGIN_URL`, `APP_MAIL_FROM`
- `APP_LEAVE_ACCRUAL_ENABLED`, `APP_LEAVE_ACCRUAL_CRON`
- `APP_ADMIN_SLA_CHECK_ENABLED`, `APP_ADMIN_SLA_CHECK_CRON`
- `CORS_ALLOWED_ORIGINS`

Frontend defaults:

- dev API URL: `http://localhost:8081/api`
- prod runtime config: `window.__HRIS_CONFIG__`

## Security

### Development defaults

All credentials in this repository are placeholder values intended for
local development only. Before deploying to any non-local environment,
rotate the following:

| Where | What | Default |
|---|---|---|
| `backend/hris/src/main/resources/application.yml` | Database password | `hris_pass` |
| `backend/hris/src/main/resources/application.yml` | RabbitMQ password | `hris_pass` |
| `backend/hris/src/main/resources/application.yml