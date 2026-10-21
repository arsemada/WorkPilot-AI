# WorkPilot AI

WorkPilot AI is an AI-powered business operations platform. Accounts, organizations, cookie sessions, and task management are in place. Documents, dashboard metrics, the AI assistant, and workflows are not implemented yet.

## Technology stack

| Area | Choice |
| --- | --- |
| Frontend | Next.js 16.4.1 (App Router), React 19.3.0, TypeScript 5.9.3, Tailwind CSS 4.3.3, ESLint 9.39.5, npm |
| Backend | Java 21, Spring Boot 3.5.16, Maven, Spring Web, Spring Data JPA, Spring Security, Validation, Actuator, Flyway, PostgreSQL JDBC, Springdoc OpenAPI 2.8.14 |
| Database | PostgreSQL 17 in Docker, named volume `workpilot_postgres_data` |
| Infrastructure | Docker, Docker Compose, environment files |

## Repository structure

```text
frontend/                 Next.js application
backend/                  Spring Boot application
infrastructure/docker/    PostgreSQL init mount (empty at this stage)
docs/                     Setup decisions and verification notes
docker-compose.yml        postgres, backend, and frontend
.env.example              Shared environment placeholders
```

## Prerequisites

- Node.js 24 and npm 11 (verified with Node.js 24.18.0 and npm 11.4.1)
- JDK 21 or a newer JDK that can compile Java 21 bytecode. This machine uses Temurin 25.0.2. JDK 21 itself is not installed. Docker runs the backend on Eclipse Temurin 21.
- Maven 3.6.3 or later (verified with Maven 3.9.9)
- Docker Engine with Docker Compose v2 (verified with Docker 29.4.1 and Compose v5.1.3)
- Git

## Environment setup

From the repository root:

```powershell
Copy-Item .env.example .env
Copy-Item backend\.env.example backend\.env
Copy-Item frontend\.env.example frontend\.env
```

`.env.example` files are tracked. Real `.env` files are gitignored. The checked-in password value `change-me` is a local development placeholder. Replace it before using this project in any shared or deployed environment.

Host ports:

| Service | URL or address |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8081 |
| PostgreSQL | `localhost:5434` |

Port `5432` is not used because a local PostgreSQL server is already listening there. Port `5433` is already used by another Docker database (`smartbill-postgres`).

## Run locally

Use this when you want Next.js and Spring Boot hot reload on the host, with only PostgreSQL in Docker.

1. Start the database:

```powershell
docker compose up -d postgres
```

2. Start the backend from `backend/` so it loads `backend/.env`:

```powershell
cd backend
mvn spring-boot:run
```

3. In another terminal, start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

The backend on the host connects to `localhost:5434`. The browser calls `http://localhost:8081`.

## Run with Docker Compose

Use this to run PostgreSQL, the backend, and the frontend in containers:

```powershell
docker compose up --build
```

Add `-d` to run in the background. Stop the stack with:

```powershell
docker compose down
```

`docker compose down` keeps the named database volume. Do not add `-v` unless you intend to delete the database files.

Inside Compose:

- The backend uses the hostname `postgres` and port `5432`.
- Browser code still uses `http://localhost:8081`, because that code runs in the user's browser.
- Server-side frontend code receives `API_BASE_URL=http://backend:8081`.
- `NEXT_PUBLIC_API_BASE_URL` is embedded when the frontend image is built. Change it in `.env`, then rebuild the frontend image.

## Database access

From the host, if `psql` is installed:

```powershell
psql -h localhost -p 5434 -U workpilot -d workpilot_db
```

From the running database container:

```powershell
docker compose exec postgres psql -U workpilot -d workpilot_db
```

The database password is the value of `POSTGRES_PASSWORD` in `.env`. Flyway creates and updates the schema. Hibernate validates that schema and does not create or drop tables (`ddl-auto: validate`).

Migrations:

| Version | Purpose |
| --- | --- |
| `V1__initial_schema.sql` | `users`, `organizations`, and `organization_memberships` |
| `V2__membership_role_check.sql` | Restricts membership roles to `OWNER`, `ADMIN`, and `MEMBER` |
| `V3__tasks.sql` | Organization tasks, with status and priority checks |

## Health checks

Backend liveness and database readiness:

```text
http://localhost:8081/actuator/health
http://localhost:8081/actuator/health/readiness
```

A healthy database connection looks like `"status":"UP"` with a `db` component of `"UP"`.

Container status:

```powershell
docker compose ps
```

Springdoc OpenAPI is available at `http://localhost:8081/swagger-ui.html` once the backend is running. The endpoint reference is also in [docs/API.md](docs/API.md).

## Authentication

The API uses an HttpOnly session cookie named `WORKPILOT_SESSION`. Passwords are stored as BCrypt hashes. A browser client on `http://localhost:3000` can call the API with credentials; other origins are rejected.

The frontend API client always sends `credentials: "include"` to `NEXT_PUBLIC_API_BASE_URL`. Before `POST`, `PATCH`, or `DELETE`, it calls `GET /api/auth/csrf` and sends the returned token in the `X-XSRF-TOKEN` header. The session cookie is not readable from JavaScript. A request that expected an existing session and receives `401` clears the local user and returns to the sign-in page.

`GET /actuator/health` stays public and does not require CSRF.

Organization permissions are `OWNER`, `ADMIN`, and `MEMBER`. A user who is not a member of an organization receives `404` for that organization's resources. Future organization-owned entities should implement `OrganizationScoped` and pass through `OrganizationAccessService`.

## Frontend application

Public routes are `/login` and `/register`. `/` sends a signed-in user to `/dashboard` and everyone else to `/login`. These routes require a session:

| Route | Page |
| --- | --- |
| `/dashboard` | Placeholder for later metrics, plus the organizations on the current session |
| `/tasks` | Task list, filters, create, edit, status changes, and delete |
| `/documents` | Placeholder |
| `/assistant` | Placeholder |
| `/workflows` | Placeholder |
| `/settings` | Placeholder |

Registration calls `POST /api/auth/register`, then `POST /api/auth/login`. The API does not start a session during registration. Sign-out calls `POST /api/auth/logout`. Reloading the page calls `GET /api/auth/me` and keeps the user in the shell while the session cookie is still valid.

## Tasks

Tasks belong to one organization. Create, read, and update use the resource permissions held by every role. Delete uses `RESOURCE_DELETE`, so owners and admins can delete tasks and members cannot.

A new task defaults to status `TODO` and priority `MEDIUM`. Creation may choose any status. Later status changes follow this lifecycle:

| From | Allowed next statuses |
| --- | --- |
| `TODO` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `TODO`, `DONE`, `CANCELLED` |
| `DONE` | `IN_PROGRESS` |
| `CANCELLED` | `TODO` |

An assignee must be an active member of the same organization. The Tasks page lists, filters, searches, and pages tasks from `GET /api/organizations/{organizationId}/tasks`. The contract is in [docs/API.md](docs/API.md).

Frontend checks from `frontend/`:

```powershell
npm run lint
npm run typecheck
npm run build
npm run dev
```

`NEXT_PUBLIC_API_BASE_URL` is the browser address (`http://localhost:8081` for local and Docker builds). It is public in the client bundle, so it must not contain secrets. `API_BASE_URL` is read only by the Next.js server. Compose sets it to `http://backend:8081`. Session requests from the browser do not use that internal hostname.

## Local process and Docker Compose

| | Local processes | Docker Compose |
| --- | --- | --- |
| Frontend | `npm run dev` in `frontend/` | `workpilot-frontend` on port 3000 |
| Backend | `mvn spring-boot:run` in `backend/` | `workpilot-backend` on port 8081, Temurin 21 |
| Database | `docker compose up -d postgres` | same `workpilot-postgres` service |
| JDBC host | `localhost:5434` | `postgres:5432` |
| Browser API URL | `http://localhost:8081` | `http://localhost:8081` |
| Server-side API URL | `http://localhost:8081` | `http://backend:8081` |

Do not run the host backend and the Compose backend at the same time. Both bind port `8081`. The same applies to the frontend and port `3000`.

## Troubleshooting

- **Port 3000 is already in use.** Another Next.js app may be running. Stop that process, or it will block the WorkPilot frontend.
- **Port 5432 is already in use.** That is expected on this machine. WorkPilot publishes PostgreSQL on `5434`.
- **Backend cannot connect to PostgreSQL.** For a host process, the JDBC URL must use `localhost:5434`. `postgres` is a Docker network hostname and only works inside Compose.
- **`mvn test` fails during startup.** Backend tests use an in-memory H2 database and Flyway. A failure there is a test or migration problem, not a missing local PostgreSQL server.
- **Frontend still calls the old API URL.** `NEXT_PUBLIC_API_BASE_URL` is fixed at build time. Rebuild the frontend image after changing it.
- **Actuator health returns 401.** `/actuator/health` and `/actuator/health/**` are public. Other API routes require a session. A `POST`, `PATCH`, or `DELETE` without the CSRF cookie and `X-XSRF-TOKEN` header returns 403.
- **Maven prints a restricted-method warning on JDK 25.** The project still targets Java 21. The warning comes from Maven's terminal library on JDK 25 and does not fail the build.

See [docs/PROJECT_SETUP.md](docs/PROJECT_SETUP.md) for initialization decisions, versions, and current limitations.
