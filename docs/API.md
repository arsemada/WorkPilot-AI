# WorkPilot AI API

Phase 1 exposes account, organization, and membership operations. Phase 3 adds organization-scoped tasks. Documents can be uploaded, searched, downloaded, and deleted inside an organization. Interactive documentation is served at `http://localhost:8081/swagger-ui.html` and `http://localhost:8081/v3/api-docs`.

The API base URL for local development is `http://localhost:8081`.

## Session and CSRF

Authentication uses a server-side session. The session cookie is `WORKPILOT_SESSION`. It is HttpOnly, SameSite `Lax`, and is marked Secure only when `SESSION_COOKIE_SECURE=true`.

The Next.js app at `http://localhost:3000` is the default allowed CORS origin. The browser must send requests with credentials. Arbitrary origins are not allowed.

Every `POST`, `PATCH`, and `DELETE` requires CSRF protection, including registration and login. `GET /actuator/health` is excluded so container health checks keep working.

1. `GET /api/auth/csrf` returns the token and sets the `XSRF-TOKEN` cookie.
2. Send that token in the `X-XSRF-TOKEN` header on mutating requests, with the cookie.
3. `POST /api/auth/login` sets `WORKPILOT_SESSION`.
4. Send the session cookie on later requests.
5. `POST /api/auth/logout` invalidates the session.

Passwords are hashed with BCrypt before they are stored. Responses and logs do not include password hashes, raw passwords, or session identifiers.

## Roles

Permissions belong to an organization membership, not to the user globally. The same person can be an owner in one organization and a member in another.

| Permission | OWNER | ADMIN | MEMBER |
| --- | --- | --- | --- |
| Read organization | yes | yes | yes |
| Update organization | yes | yes | no |
| Delete or transfer organization | yes | no | no |
| Read members | yes | yes | yes |
| Invite, update, or remove members | yes | yes | no |
| Create, read, or update a future organization resource | yes | yes | yes |
| Delete a future organization resource | yes | yes | no |

An admin can add or change members only at the `MEMBER` role. An owner can manage every role. An organization must keep at least one owner.

A caller who is not a member of the organization in the URL receives `404 Organization not found`. A member without the required permission receives `403`.

Future organization-owned entities should implement `OrganizationScoped` and be checked with `OrganizationAccessService.requireResourceInOrganization` before they are read or changed.

## Endpoints

### Authentication

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Public | Issue the CSRF token |
| `POST` | `/api/auth/register` | Public | Create an account. `organizationName` is optional and makes the user the owner of a new organization |
| `POST` | `/api/auth/login` | Public | Start a session |
| `POST` | `/api/auth/logout` | Session | Invalidate the session |
| `GET` | `/api/auth/me` | Session | Current user and memberships |

Registration body:

```json
{
  "email": "ada@example.com",
  "password": "at-least-8",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "organizationName": "Analytical Engines"
}
```

Login body:

```json
{
  "email": "ada@example.com",
  "password": "at-least-8"
}
```

### Organizations

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| `POST` | `/api/organizations` | Authenticated | Create an organization and become its owner |
| `GET` | `/api/organizations` | Authenticated | List organizations for the current user |
| `GET` | `/api/organizations/{organizationId}` | `ORG_READ` | Read one organization |
| `GET` | `/api/organizations/{organizationId}/members` | `MEMBER_READ` | List members |
| `POST` | `/api/organizations/{organizationId}/members` | `MEMBER_INVITE` | Add an existing account by email |
| `PATCH` | `/api/organizations/{organizationId}/members/{userId}` | `MEMBER_UPDATE` | Change a member's role |
| `DELETE` | `/api/organizations/{organizationId}/members/{userId}` | `MEMBER_REMOVE` | Remove a member |

Create organization body:

```json
{
  "name": "Northwind Trading",
  "slug": "northwind-trading"
}
```

`slug` is optional. When it is omitted, the API derives one from the name.

Add member body:

```json
{
  "email": "mia@example.com",
  "role": "MEMBER"
}
```

`role` is `OWNER`, `ADMIN`, or `MEMBER`.

### Tasks

Tasks are organization resources. A caller who is not a member receives `404`. A member without the required permission receives `403`.

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| `POST` | `/api/organizations/{organizationId}/tasks` | `RESOURCE_CREATE` | Create a task. Status defaults to `TODO` and priority to `MEDIUM` |
| `GET` | `/api/organizations/{organizationId}/tasks` | `RESOURCE_READ` | List tasks with filters, search, sorting, and pagination |
| `GET` | `/api/organizations/{organizationId}/tasks/{taskId}` | `RESOURCE_READ` | Read one task |
| `PATCH` | `/api/organizations/{organizationId}/tasks/{taskId}` | `RESOURCE_UPDATE` | Update the fields present in the body |
| `DELETE` | `/api/organizations/{organizationId}/tasks/{taskId}` | `RESOURCE_DELETE` | Delete a task |

Create body:

```json
{
  "title": "Prepare the launch brief",
  "description": "Share it with the team",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2026-11-01",
  "assigneeId": "00000000-0000-0000-0000-000000000000"
}
```

`description`, `status`, `priority`, `dueDate`, and `assigneeId` are optional. `assigneeId` must be an active member of the same organization.

List query parameters:

| Parameter | Meaning |
| --- | --- |
| `status` | `TODO`, `IN_PROGRESS`, `DONE`, or `CANCELLED` |
| `priority` | `LOW`, `MEDIUM`, `HIGH`, or `URGENT` |
| `assigneeId` | Tasks assigned to that member |
| `unassigned` | `true` returns tasks with no assignee. Do not combine it with `assigneeId` |
| `dueAfter`, `dueBefore` | Inclusive due-date bounds, `YYYY-MM-DD`. Tasks without a due date are excluded |
| `q` | Case-insensitive match on title or description, up to 200 characters |
| `page` | Zero-based page, default `0` |
| `size` | Page size from 1 to 100, default `20` |
| `sort` | `createdAt`, `updatedAt`, `dueDate`, `title`, `priority`, or `status` |
| `direction` | `asc` or `desc`, default `desc` |

Status changes on update:

| From | Permitted targets |
| --- | --- |
| `TODO` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `TODO`, `DONE`, `CANCELLED` |
| `DONE` | `IN_PROGRESS` |
| `CANCELLED` | `TODO` |

A patch may keep the current status. Any other change returns `400`. Send `null` for `description`, `dueDate`, or `assigneeId` to clear that field. Omitted fields stay unchanged.

### Documents

Documents are organization resources. A caller who is not a member receives `404`. A member without the required permission receives `403`.

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| `POST` | `/api/organizations/{organizationId}/documents` | `RESOURCE_CREATE` | Upload a file as `multipart/form-data` with `file`, `title`, and optional `description` |
| `GET` | `/api/organizations/{organizationId}/documents` | `RESOURCE_READ` | List documents with search, sorting, and pagination |
| `GET` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_READ` | Read document metadata, including a short text preview when text was indexed |
| `GET` | `/api/organizations/{organizationId}/documents/{documentId}/content` | `RESOURCE_READ` | Download the stored file |
| `PATCH` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_UPDATE` | Update `title` and `description`. Send `null` to clear the description |
| `DELETE` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_DELETE` | Delete the record and the stored file |

Accepted extensions are `.pdf`, `.txt`, `.csv`, `.md`, `.png`, `.jpg`, `.jpeg`, `.webp`, and `.docx`. The maximum size is 10 MB. Text, CSV, and Markdown contents are indexed for `q`. Search also matches title, description, and file name. `page` defaults to `0`, `size` defaults to `20` with a maximum of `100`, and `sort` may be `createdAt`, `updatedAt`, `title`, `originalFileName`, or `sizeBytes`.

### Health

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/actuator/health` | Public | Application and database health |
| `GET` | `/actuator/health/readiness` | Public | Readiness, including the database |

## Errors

Error bodies use one shape:

```json
{
  "timestamp": "2026-10-19T17:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "password": "Password must be between 8 and 100 characters"
  },
  "path": "/api/auth/register"
}
```

`details` is omitted when there are no field errors.

| Status | When |
| --- | --- |
| `400` | Validation, malformed JSON, or a role change that would remove the last owner |
| `401` | Missing session, or email and password did not match |
| `403` | Missing CSRF token, or the member lacks permission |
| `404` | The organization or member is outside the caller's access |
| `409` | The email or organization slug is already in use |

Failed login always returns `Invalid email or password`, whether the account is missing, inactive, or the password is wrong.
