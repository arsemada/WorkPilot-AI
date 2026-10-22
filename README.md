# WorkPilot AI

WorkPilot AI is an AI-powered business operations platform. Accounts, organizations, cookie sessions, task management, documents, the assistant, workflow rules, and the organization dashboard are in place.

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
| `V4__documents.sql` | Organization documents and allowed file types |
| `V5__activity_events.sql` | Task activity used by the dashboard |
| `V6__document_processing.sql` | Document storage keys and processing status |
| `V7__conversations.sql` | Assistant conversations and messages |
| `V8__document_chunks.sql` | Document chunks, indexing status, and answer sources |
| `V9__pgvector.sql` | PostgreSQL `vector` extension and similarity index |
| `V10__assistant_tool_calls.sql` | Assistant task lookup and confirmed task creation |
| `V11__workflows.sql` | Workflow rules, execution history, and in-app notifications |

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
| `/dashboard` | Task counts, completion trend, recent tasks, and recent activity |
| `/tasks` | Task list, filters, create, edit, status changes, and delete |
| `/documents` | Upload, search, download, edit, and delete organization files |
| `/assistant` | Conversations with the assistant |
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

## Documents

Documents belong to one organization. Upload, read, and metadata updates use the resource permissions held by every role. Delete uses `RESOURCE_DELETE`, so owners and admins can delete documents and members cannot.

Accepted files are PDF, plain text, CSV, Markdown, PNG, JPEG, WebP, and Word `.docx`, up to 10 MB. The extension is checked against the file bytes, so a renamed file is rejected. PDF, text, CSV, and Markdown files are extracted in the background. Extraction ends as `COMPLETED` or `FAILED`. A failed document can be retried. Extracted text is capped at 20,000 characters and split into ordered chunks. PNG, JPEG, WebP, and Word files are stored for download and are not read for search.

A completed text extraction is queued for embedding. Indexing has its own status: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, or `SKIPPED`. Upload success does not mean an embedding was stored. A failed index can be retried. Deleting a document deletes its chunks and embeddings. The configured embedding model is `text-embedding-3-small`, version `3`, with 1,536 dimensions. A document indexed with a different model or dimension is reported as `STALE` until it is indexed again.

The database stores a storage key. The file itself stays in the configured directory, `data/documents` for a host backend and the `workpilot_documents` volume in Compose. Set `DOCUMENT_STORAGE_DIR` to move that directory. Replacing local files with object storage later means implementing the same storage key contract. Chunk embeddings use pgvector in PostgreSQL. Compose runs `pgvector/pgvector:pg17`.

The Documents page uploads, searches, pages, downloads, deletes, and retries files through `/api/organizations/{organizationId}/documents`. It also shows indexing status.

## AI Assistant

The assistant keeps a separate conversation history for each member of an organization. Another member of the same organization cannot open those conversations. A caller who is not a member of the organization receives `404`.

Set `OPENAI_API_KEY` in the backend environment. The chat model defaults to `gpt-4o-mini`. Embeddings use `text-embedding-3-small` at 1,536 dimensions. `OPENAI_BASE_URL` defaults to `https://api.openai.com/v1`, and `OPENAI_TIMEOUT` defaults to `30s`. The key stays on the server. Each member can send 40 messages per UTC day. A message can be 4,000 characters. The provider receives the system instruction, up to 20 earlier messages, and any retrieved document passages. Its reply is limited to 800 output tokens.

A missing key or a provider failure returns `503` with a short message. Reaching the daily limit returns `429`. Failed requests are not stored as assistant replies. The Assistant page can send the same draft again.

When indexed chunks exist for the organization, the assistant retrieves the closest passages and stores those sources with the reply. Passages are treated as untrusted data. If nothing relevant is retrieved, the reply is marked as having no document support and the page says that no matching documents were found.

The assistant can look up tasks and prepare a new task. Preparing a task does not save it. The member confirms the title, priority, and due date before the task service creates the task. The saved task is the one returned by that service, and the same confirmation cannot create a second copy. A caller who does not own the conversation cannot confirm the action. Tool arguments cannot choose an organization or a user id. Each request allows 3 tool calls and a 15 second tool budget.

## Workflows

Each organization starts with five workflows. Owners and admins can enable or disable them. Members can read the rules and the run history.

| Trigger | Action | Starts enabled |
| --- | --- | --- |
| A task is assigned | Notify the assignee | Yes |
| A task is overdue | Notify the assignee, or the creator when there is no assignee | Yes |
| A document is uploaded | Process the document | Yes |
| A document finishes processing and has extracted text | Summarize it for the uploader | No |
| Each day | Send the previous day's task activity | Yes |

A task assignment sends one notification for that assignee. The same assignment does not send another. The overdue check runs daily at 08:00 UTC and notifies once for a task and due date. Uploading a document starts processing through the workflow. If that rule is disabled, the upload stays stored and processing does not start. The document summary uses the assistant and stays off until an owner or admin enables it. If the assistant is not configured, that run is saved as failed and the document processing result stays separate. The activity summary runs daily at 08:15 UTC.

Runs keep a status of running, succeeded, failed, or skipped, with up to 3 attempts. A failed run shows the error and is not reported as succeeded. Notifications have a read state. The Workflows page shows the unread count for the signed-in user.

## Dashboard

The dashboard reads one organization at a time. Counts for total, to do, in progress, completed, cancelled, and overdue tasks come from database aggregates. A task is overdue when its due date is before today's UTC date and its status is `TODO` or `IN_PROGRESS`.

Completion trends count tasks marked done on each UTC day. Creating a task as `DONE`, or moving a task to `DONE`, records that day. Reopening the task leaves the original day in the trend. Tasks that were already done before activity recording started stay in the completed count and do not receive a made-up trend day.

Recent activity lists the latest task create, update, status change, completion, and delete events for that organization. The contract is in [docs/API.md](docs/API.md).

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
