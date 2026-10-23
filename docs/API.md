# WorkPilot AI API

Phase 1 exposes account, organization, and membership operations. Phase 3 adds organization-scoped tasks. Documents can be uploaded, searched, downloaded, and deleted inside an organization. The dashboard summarizes tasks and recorded activity for one organization. Members can keep private assistant conversations. Organizations have workflow rules, execution history, and in-app notifications. Interactive documentation is served at `http://localhost:8081/swagger-ui.html` and `http://localhost:8081/v3/api-docs`.

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
| `PATCH` | `/api/auth/me` | Session | Update the current user's first and last name |

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
| `PATCH` | `/api/organizations/{organizationId}` | `ORG_UPDATE` | Rename the organization. The slug stays the same |
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
| `GET` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_READ` | Read document metadata, including processing status and a short text preview when text was indexed |
| `GET` | `/api/organizations/{organizationId}/documents/{documentId}/status` | `RESOURCE_READ` | Read processing status, a safe error message, and whether text was indexed |
| `POST` | `/api/organizations/{organizationId}/documents/{documentId}/retry` | `RESOURCE_UPDATE` | Queue text extraction again. Only a `FAILED` document can be retried |
| `POST` | `/api/organizations/{organizationId}/documents/{documentId}/reindex` | `RESOURCE_UPDATE` | Queue embedding again when extracted text is ready |
| `GET` | `/api/organizations/{organizationId}/documents/{documentId}/content` | `RESOURCE_READ` | Download the stored file |
| `PATCH` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_UPDATE` | Update `title` and `description`. Send `null` to clear the description |
| `DELETE` | `/api/organizations/{organizationId}/documents/{documentId}` | `RESOURCE_DELETE` | Delete the record and the stored file |

Accepted extensions are `.pdf`, `.txt`, `.csv`, `.md`, `.png`, `.jpg`, `.jpeg`, `.webp`, and `.docx`. The server checks the file bytes as well as the extension. The maximum size is 10 MB. Upload stores the file and returns `processingStatus` `PENDING` and `indexingStatus` `SKIPPED`. PDF, text, CSV, and Markdown extraction then moves the document to `COMPLETED` or `FAILED`. `processingError` is a short message with no file path. PNG, JPEG, WebP, and Word files finish as `COMPLETED` without extracted text, and indexing stays `SKIPPED`.

When extraction stores text, indexing moves through `PENDING` and `PROCESSING` to `COMPLETED` or `FAILED`. `indexingError` is a short message. A completed upload does not imply that embeddings were stored. `POST .../reindex` requires `RESOURCE_UPDATE`, a completed extraction, and extracted text. It returns `400` with `The document text is not ready to index.` or `This document has no text to index.` when those are missing. Deleting a document deletes its chunks and embeddings. Answer history keeps the document title and clears the document id.

The embedding model, version, and dimensions are configured as `text-embedding-3-small`, `3`, and `1536`. PostgreSQL stores each vector in a `vector(1536)` column with an HNSW cosine index. A document whose stored model, version, or dimensions differ from the current configuration is returned as `indexingStatus` `STALE` with `This document was indexed with a different embedding model.` That status is not written to the database. Similarity search is limited to the organization in the path and to chunks indexed with the current model and version.

Search matches title, description, file name, and extracted text after extraction finishes. `page` defaults to `0`, `size` defaults to `20` with a maximum of `100`, and `sort` may be `createdAt`, `updatedAt`, `title`, `originalFileName`, or `sizeBytes`. Deleting a document removes the database row and the stored file.

### Dashboard

Dashboard reads use `RESOURCE_READ`. A caller who is not a member receives `404`. Counts are calculated in the database for the organization in the path.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/organizations/{organizationId}/dashboard` | Current task counts, member count, and the five most recently updated tasks |
| `GET` | `/api/organizations/{organizationId}/dashboard/trends` | Tasks marked done on each UTC day |
| `GET` | `/api/organizations/{organizationId}/dashboard/activity` | The ten most recent task activity events |

Trend query parameters `from` and `to` are inclusive `YYYY-MM-DD` dates. When both are omitted, the range is the last 14 UTC days including today. Sending only one date returns `400`. A range longer than 90 days returns `400`.

`overdueTasks` counts tasks whose due date is before today's UTC date and whose status is `TODO` or `IN_PROGRESS`. A task due today is not overdue. Done and cancelled tasks are not overdue.

`points` includes every day in the range, with `completedCount` of `0` when nothing was marked done. The count comes from recorded `TASK_COMPLETED` events. A task inserted without that event still increases `completedTasks` and does not add a trend point.

Activity is recorded when a task is created, when its fields change, when its status changes, when it is marked done, and when it is deleted. Deleting a task keeps the activity row. The summary text describes the change, and `actorName` is the member who made it.

### Assistant

Conversations belong to one member inside one organization. Listing, reading, sending, and deleting use `RESOURCE_READ`, and every role has that permission. The API returns only conversations owned by the signed-in member. Another member receives `404 Conversation not found`. A caller who is not a member of the organization receives `404 Organization not found`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/organizations/{organizationId}/conversations` | Start a conversation. `title` is optional and defaults to `New conversation` |
| `GET` | `/api/organizations/{organizationId}/conversations` | List the caller's conversations, newest first, up to 50 |
| `GET` | `/api/organizations/{organizationId}/conversations/{conversationId}` | Read the conversation and its messages |
| `POST` | `/api/organizations/{organizationId}/conversations/{conversationId}/messages` | Send `{ "content": "..." }`. Stores the user message and the assistant reply together |
| `POST` | `/api/organizations/{organizationId}/conversations/{conversationId}/tool-calls/{toolCallId}/confirm` | Save a proposed task through the task service |
| `POST` | `/api/organizations/{organizationId}/conversations/{conversationId}/tool-calls/{toolCallId}/reject` | Leave a proposed task unsaved |
| `DELETE` | `/api/organizations/{organizationId}/conversations/{conversationId}` | Delete that member's conversation and its messages |

`content` is required and must be 4,000 characters or fewer. A blank message returns `400`. The provider is OpenAI chat completions. `OPENAI_API_KEY` is required at request time. The chat model defaults to `gpt-4o-mini`. Embeddings use `text-embedding-3-small` with 1,536 dimensions. The timeout defaults to 30 seconds, and each reply asks for at most 800 output tokens. The request includes a system instruction, up to 20 earlier messages from that conversation, and a second system message containing retrieved passages or an explicit statement that no passages were retrieved.

Retrieved passages are untrusted. The instruction tells the model to ignore commands inside them, including requests to create tasks or change data, and to say when the documents do not contain the answer. Sources stored on the assistant message come from retrieval. `documentSupport` is `true` only when at least one passage was retrieved. It is `false` when none were. User messages leave `documentSupport` null and `sources` empty. Each source includes `documentId`, `documentTitle`, `chunkIndex`, `pageNumber`, `sectionLabel`, and `snippet`. `documentId` becomes null after the document is deleted.

The assistant can call two tools. `list_tasks` reads tasks through the task service for the organization in the path. `mine` limits that lookup to the signed-in user. `create_task` validates a title, optional description, priority, and `YYYY-MM-DD` due date, then waits for confirmation. The model cannot supply an organization id or a user id. Confirming calls the same task creation used by the tasks API and returns the saved task. Confirming the same proposal again returns that saved task and does not create another. Rejecting leaves it unsaved. A missing title, malformed arguments, or an unknown tool is stored as `FAILED` and does not create a task. Another member receives `404`. Each reply allows 3 tool calls. The tool budget is 15 seconds.

Each member can send 40 messages per organization per UTC day. The 41st message returns `429` with `You have reached today's assistant message limit.` A missing key, a provider error, or a timeout returns `503`. Those failures are not saved as assistant messages, so the same text can be sent again. If embedding fails while answering, the reply is still generated and `documentSupport` is `false`.

### Workflows and notifications

Listing rules and execution history requires `ORG_READ`. Creating, renaming, enabling, and disabling a rule requires `ORG_UPDATE`. A caller who is not a member receives `404 Organization not found`. A member without `ORG_UPDATE` receives `403`.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/organizations/{organizationId}/workflows` | List the organization's rules |
| `POST` | `/api/organizations/{organizationId}/workflows` | Create a supported rule. Body: `{ "name", "eventType", "actionType" }` |
| `PATCH` | `/api/organizations/{organizationId}/workflows/{ruleId}` | Rename a rule. Body: `{ "name" }` |
| `POST` | `/api/organizations/{organizationId}/workflows/{ruleId}/enable` | Enable a rule |
| `POST` | `/api/organizations/{organizationId}/workflows/{ruleId}/disable` | Disable a rule |
| `GET` | `/api/organizations/{organizationId}/workflows/executions` | Recent runs, newest first, up to 50 |
| `GET` | `/api/notifications` | Notifications for the signed-in user, newest first, up to 50 |
| `GET` | `/api/notifications/unread-count` | `{ "count" }` of unread notifications |
| `POST` | `/api/notifications/{notificationId}/read` | Mark one notification read. Another user's notification returns `404 Notification not found` |
| `POST` | `/api/notifications/read-all` | Mark every notification for the signed-in user read |

Supported pairs are `TASK_ASSIGNED` with `NOTIFY_ASSIGNEE`, `TASK_OVERDUE` with `NOTIFY_RELEVANT_USERS`, `DOCUMENT_UPLOADED` with `PROCESS_DOCUMENT`, `DOCUMENT_PROCESSED` with `SUMMARIZE_DOCUMENT`, and `SCHEDULED_ACTIVITY` with `SUMMARIZE_ACTIVITY`. Any other pair returns `400` with `That trigger and action are not supported together.` A second rule for the same event returns `400` with `This organization already has a workflow for that event.` A missing rule returns `404 Workflow not found`.

New organizations receive all five rules. The document summary starts disabled. The others start enabled. A disabled rule does not run. Each event is claimed once per rule through an idempotency key, so a repeated event does not run again. A run is `RUNNING`, `SUCCEEDED`, `FAILED`, or `SKIPPED`. Failed runs keep the error message and are retried up to 3 attempts. A skipped or failed run is not returned as succeeded.

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
| `404` | The organization, member, document, or conversation is outside the caller's access |
| `409` | The email or organization slug is already in use |
| `429` | The caller reached the daily assistant message limit |
| `503` | The assistant is not configured or the provider did not return a reply |

Failed login always returns `Invalid email or password`, whether the account is missing, inactive, or the password is wrong.
