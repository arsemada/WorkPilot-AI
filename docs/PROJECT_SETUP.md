# Project setup

This document records how the WorkPilot AI repository was initialized. It describes infrastructure only. Authentication, business data, and AI features are not implemented.

## Decisions

- **Java 21 language level.** `pom.xml` sets `java.version` to 21. The Docker backend image uses `eclipse-temurin:21-jre-jammy` and builds with `maven:3.9.9-eclipse-temurin-21`.
- **Host JDK.** The default local JDK is Eclipse Temurin 25.0.2. JDK 23 is also installed, and JDK 21 is not. Spring Boot 3.5.16 supports Java 17 through 25, so local Maven builds can run on Temurin 25 while emitting Java 21 bytecode. Container execution stays on Java 21.
- **Spring Boot 3.5.16.** Latest 3.5.x OSS release. OSS support for 3.5.x ended with this release.
- **Springdoc OpenAPI 2.8.14.** The 2.8 line supports Spring Boot 3.5. Springdoc 3.x targets Spring Boot 4 and is not used.
- **Spring Security is installed and intentionally open.** `DevelopmentSecurityConfig` permits every request, disables form login, HTTP basic, and CSRF, and registers an empty in-memory user service so Boot does not generate a default password. This exists so `/actuator/health` is reachable. It is not an authentication system.
- **Schema management.** `spring.jpa.hibernate.ddl-auto` is `none`. The app does not create or alter tables. No Flyway, Liquibase, entities, or init SQL are included. `infrastructure/docker/postgres/init` is mounted for later scripts and is empty.
- **PostgreSQL 17.** Official `postgres:17` image. Credentials come from environment variables.
- **Host port 5434.** Port 5432 is occupied by a local `postgres.exe`. Port 5433 is occupied by the existing `smartbill-postgres` container. Inside the Compose network the database still listens on 5432.
- **Persistent volume.** Named volume `workpilot_postgres_data` mounted at `/var/lib/postgresql/data`.
- **Frontend.** Next.js 16.4.1 App Router, React 19.3.0, TypeScript, Tailwind CSS 4, and ESLint, created with npm. The page identifies the project and prints the configured public API base URL.
- **API addresses.** `NEXT_PUBLIC_API_BASE_URL=http://localhost:8081` is for the browser. `API_BASE_URL` is `http://localhost:8081` on the host and `http://backend:8081` in Compose.
- **Health.** Actuator exposes `health` on port 8081, including readiness and liveness probes. Health details are set to `always` so a local check can show the database component. Restrict that before any shared deployment.
- **Environment files.** Root, `backend/`, and `frontend/` each have an `.env.example`. Spring Boot imports an optional `backend/.env` as properties when the process working directory is `backend/`. Compose reads the root `.env` for interpolation and injects container environment variables directly. Passwords are not written in Java, YAML defaults, or Dockerfiles.

## Versions

| Component | Version |
| --- | --- |
| Java language target | 21 |
| Local JDK | Temurin 25.0.2+10 |
| Maven (host) | 3.9.9 |
| Maven image | `maven:3.9.9-eclipse-temurin-21` |
| JRE image | `eclipse-temurin:21-jre-jammy` |
| Spring Boot | 3.5.16 |
| Springdoc OpenAPI | 2.8.14 |
| Node.js | 24.18.0 |
| npm | 11.4.1 |
| Next.js | 16.4.1 |
| React | 19.3.0 |
| Tailwind CSS | 4.3.3 |
| TypeScript | 5.9.3 |
| ESLint | 9.39.5 |
| PostgreSQL image | `postgres:17` (running container reports 17.11) |
| PostgreSQL JDBC | 42.7.11 |
| Hibernate ORM | 6.6.53.Final |
| Docker | 29.4.1 |
| Docker Compose | v5.1.3 |

Resolved Maven and npm dependency versions are recorded after verification in the section below.

## Service names and ports

| Compose service | Container | Host |
| --- | --- | --- |
| `postgres` | `workpilot-postgres` | `localhost:5434` |
| `backend` | `workpilot-backend` | `localhost:8081` |
| `frontend` | `workpilot-frontend` | `localhost:3000` |

## Environment variables

| Variable | Used by | Local value |
| --- | --- | --- |
| `POSTGRES_DB` | PostgreSQL, Compose | `workpilot_db` |
| `POSTGRES_USER` | PostgreSQL, Compose | `workpilot` |
| `POSTGRES_PASSWORD` | PostgreSQL, Compose | local placeholder in `.env` |
| `POSTGRES_HOST` | Host clients | `localhost` |
| `POSTGRES_PORT` | Host port mapping | `5434` |
| `SPRING_DATASOURCE_URL` | Backend | host: `jdbc:postgresql://localhost:5434/workpilot_db`; Compose: `jdbc:postgresql://postgres:5432/workpilot_db` |
| `SPRING_DATASOURCE_USERNAME` | Backend | `workpilot` |
| `SPRING_DATASOURCE_PASSWORD` | Backend | same placeholder as `POSTGRES_PASSWORD` |
| `SERVER_PORT` / `BACKEND_PORT` | Backend | `8081` |
| `FRONTEND_PORT` | Compose host mapping | `3000` |
| `NEXT_PUBLIC_API_BASE_URL` | Browser bundle | `http://localhost:8081` |
| `API_BASE_URL` | Next.js server | host: `http://localhost:8081`; Compose: `http://backend:8081` |

## Limitations

- Phase 1 replaced the temporary permit-all filter with cookie sessions, BCrypt passwords, and organization roles. See [API.md](API.md).
- Flyway owns the schema. The initial tables are users, organizations, and organization memberships.
- No AI provider, background jobs, or extra infrastructure.
- Actuator health details are visible without authentication.
- The local development password lives only in gitignored `.env` files, but `.env.example` contains the obvious placeholder `change-me`.
- Host execution uses JDK 25 because JDK 21 is not installed. The compiled target and the container runtime are Java 21.
- `npm audit` may report vulnerabilities in the Next.js dependency tree. They were not force-upgraded during initialization.
- Port 3000 may already be taken by another local Next.js process. Compose cannot publish the frontend until that process is stopped.

## Verification

Checked on this machine on 14 October 2026.

Passed:

- Tool versions: Temurin 25.0.2, Maven 3.9.9, Node.js 24.18.0, npm 11.4.1, Docker 29.4.1, Compose v5.1.3.
- `mvn -B package` in `backend/`: BUILD SUCCESS. `WorkPilotApplicationTests` ran 1 test, 0 failures, and connected to PostgreSQL 17.11.
- Host backend `http://localhost:8081/actuator/health` returned `"status":"UP"` with `db.status` `UP` and `database` `PostgreSQL`. The host process was stopped afterward so Compose could bind port 8081.
- `npm run build` and `npm run lint` in `frontend/` completed successfully.
- `workpilot-postgres` became healthy. `psql` connected to `workpilot_db` as `workpilot`. `\dt` reported no relations.
- After `docker restart workpilot-postgres`, the container became healthy again and `workpilot_db` still accepted connections. Docker volume `workpilot-ai_workpilot_postgres_data` is mounted at `/var/lib/postgresql/data`.
- `docker compose up --build -d` built `workpilot-ai-backend` and `workpilot-ai-frontend`. The backend container runs Temurin 21.0.12.1 and uses `jdbc:postgresql://postgres:5432/workpilot_db`. Its health endpoint returned `"status":"UP"` with the database component `UP`.

Not completed as specified:

- WorkPilot's frontend is not available at `http://localhost:3000`. That port is already held by another Next.js process for the smartbill web app. Compose created `workpilot-frontend` and then failed with: `listen tcp 0.0.0.0:3000: bind: Only one usage of each socket address is normally permitted.` The command exited with code 1.
- The frontend image was checked separately by running it on host port 3010. It returned the WorkPilot AI page, title, and `API base URL: http://localhost:8081`. That temporary container was removed. Port 3010 is not part of the project configuration.

To finish the Compose frontend check, stop the process listening on port 3000 and run `docker compose up -d frontend`.
