# Task Manager API — Business Spec

Nov 21, 2026 · @Tasneem Alaa

## Overview

A REST API where users register, log in, and manage their own tasks with priority, status, due dates, filtering and search. It is a portfolio project that must be finished in 2 days, so the scope is small but the code must be clean, tested and easy to run.

**Goal:** show core Java backend skills to recruiters: Spring Boot, JPA, security, validation, testing and Docker.

### Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.x (Web, Data JPA, Security, Validation) |
| Build | Maven |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (stateless) |
| Mapping | DTOs with MapStruct (or manual mappers) |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Run | Docker + Docker Compose |
| Extras | Lombok |

## Users, roles and domain model

There are two roles. A **USER** manages only their own tasks. An **ADMIN** can list all users and all tasks (read-only), and can deactivate a user.

### User

| Field | Type | Rules |
| --- | --- | --- |
| id | Long | Auto-generated |
| fullName | String | Required, 2–100 chars |
| email | String | Required, valid email, unique (case-insensitive) |
| password | String | Required, min 8 chars, at least 1 letter and 1 number; stored as BCrypt hash, never returned |
| role | Enum | USER or ADMIN; default USER |
| active | Boolean | Default true |
| createdAt | Instant | Set automatically |

### Task

| Field | Type | Rules |
| --- | --- | --- |
| id | Long | Auto-generated |
| title | String | Required, 3–120 chars |
| description | String | Optional, max 2000 chars |
| status | Enum | TODO, IN\_PROGRESS, DONE; default TODO |
| priority | Enum | LOW, MEDIUM, HIGH; default MEDIUM |
| dueDate | LocalDate | Optional; cannot be in the past when created or changed |
| completedAt | Instant | Set when status becomes DONE; cleared if moved back |
| owner | User | Many tasks belong to one user; set from the logged-in user |
| createdAt / updatedAt | Instant | Set automatically (JPA auditing) |
| version | Long | `@Version` for optimistic locking |

A computed field **overdue** (true when dueDate is before today and status is not DONE) is returned in responses but not stored.

## Business rules

1. A user can see, edit and delete only their own tasks. Accessing another user's task returns **404** (not 403), so task ids are not leaked.
2. Status can move TODO → IN\_PROGRESS → DONE, and back from DONE to IN\_PROGRESS (reopen). TODO → DONE directly is allowed. Any other move returns **409**.
3. When a task becomes DONE, `completedAt` is set to now. When it is reopened, `completedAt` is cleared.
4. A DONE task cannot be edited (title, description, priority, dueDate) until it is reopened. Trying returns **409**.
5. `dueDate` cannot be in the past when creating a task or changing its due date.
6. A user can have at most **200** open (not DONE) tasks. Creating one more returns **409**.
7. Deleting a task is a hard delete. Only the owner can delete.
8. A deactivated user cannot log in; their existing tokens are rejected.
9. Email is unique and compared case-insensitively; stored in lowercase.
10. Two updates to the same task at the same time: the second one fails with **409** (optimistic locking via `version`). The client must send the `version` it last read.

## API endpoints

All endpoints are under `/api/v1`. All except register and login need `Authorization: Bearer <token>`.

### Auth

| Method | Path | Description | Success |
| --- | --- | --- | --- |
| POST | /auth/register | Create account (fullName, email, password) | 201 + user (no password) |
| POST | /auth/login | Email + password, returns JWT (expires in 24 h) | 200 + token |
| GET | /users/me | Current user's profile | 200 |

### Tasks (owner only)

| Method | Path | Description | Success |
| --- | --- | --- | --- |
| POST | /tasks | Create task | 201 + Location header |
| GET | /tasks | List my tasks with filters, search, sort, paging | 200 |
| GET | /tasks/{id} | Get one task | 200 |
| PUT | /tasks/{id} | Update title, description, priority, dueDate (+ version) | 200 |
| PATCH | /tasks/{id}/status | Change status only (+ version) | 200 |
| DELETE | /tasks/{id} | Delete task | 204 |
| GET | /tasks/summary | Counts by status, by priority, and overdue count | 200 |

### Admin

| Method | Path | Description | Success |
| --- | --- | --- | --- |
| GET | /admin/users | List users (paged) | 200 |
| PATCH | /admin/users/{id}/deactivate | Deactivate a user | 204 |
| GET | /admin/tasks | List all tasks (paged, same filters) | 200 |

### Filtering, search and paging on GET /tasks

All query parameters are optional and can be combined:

- `status` – one or more, e.g. `status=TODO&status=IN_PROGRESS`
- `priority` – one or more
- `dueFrom`, `dueTo` – date range (yyyy-MM-dd)
- `overdue=true` – only overdue tasks
- `q` – search text, case-insensitive, matches title or description
- `sort` – `dueDate`, `priority`, `createdAt` or `title`, with `,asc` or `,desc`; default `createdAt,desc`
- `page` (default 0), `size` (default 10, max 50)

Build the query with JPA Specifications. The response is a page object: `content`, `page`, `size`, `totalElements`, `totalPages`.

### Example task response

```json
{
  "id": 12,
  "title": "Prepare sprint demo",
  "description": "Slides + live demo",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "dueDate": "2026-11-25",
  "overdue": false,
  "completedAt": null,
  "createdAt": "2026-11-21T10:15:00Z",
  "updatedAt": "2026-11-21T12:40:00Z",
  "version": 2
}
```

## Validation, errors and security

Validate every request DTO with Bean Validation (`@NotBlank`, `@Size`, `@Email`, `@FutureOrPresent`). One `@RestControllerAdvice` turns every error into the same JSON shape (RFC 7807 `ProblemDetail`):

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has invalid fields",
  "instance": "/api/v1/tasks",
  "errors": { "title": "must be between 3 and 120 characters" }
}
```

| Status | When |
| --- | --- |
| 400 | Invalid input or bad query parameter |
| 401 | Missing, invalid or expired token; wrong login |
| 403 | Non-admin calling /admin endpoints |
| 404 | Task not found or not owned by the caller |
| 409 | Email already used, invalid status move, editing a DONE task, task limit reached, version conflict |
| 500 | Unexpected error (generic message, details only in logs) |

### Security

- Spring Security, stateless, JWT filter; secret and expiry read from environment variables, never hard-coded.
- Passwords hashed with BCrypt.
- Login errors say only "Invalid email or password".
- One ADMIN account is seeded by a Flyway migration (credentials from environment variables).
- CORS open to `http://localhost:4200` only (configurable).

## Testing

- **Unit tests** (JUnit 5 + Mockito) for `TaskService`: every business rule above has at least one test.
- **Integration tests** (`@SpringBootTest` + Testcontainers PostgreSQL + MockMvc): register → login → create → filter → update status → delete; a user cannot read another user's task (404); version conflict returns 409.
- Target: at least 70% line coverage on the service layer (JaCoCo report).

## Project structure

Package by feature, layered inside each feature:

```
com.tasneem.taskmanager
├── auth        (controller, service, JwtService, JwtFilter, DTOs)
├── user        (entity, repository, service, controller, DTOs)
├── task        (entity, repository, specifications, service, controller, DTOs, mapper)
├── admin       (controller)
└── common      (exception handler, custom exceptions, config, auditing)
```

Controllers stay thin; all rules live in services. Entities are never returned directly, only DTOs (Java records).

## Deliverables (definition of done)

- [ ] `docker compose up` starts the API and PostgreSQL with no extra steps
- [ ] Swagger UI at `/swagger-ui.html` with JWT "Authorize" button working
- [ ] Flyway migrations create the schema and seed the admin
- [ ] All tests pass with `mvn verify`
- [ ] GitHub Actions workflow runs `mvn verify` on every push
- [ ] `.env.example` lists every environment variable
- [ ] README: what it is, tech stack, how to run, endpoint table, sample curl requests, design decisions (why JWT, why optimistic locking, why 404 for other users' tasks)
- [ ] Postman collection or `requests.http` file in the repo

## Out of scope

Frontend, refresh tokens, email verification, password reset, file attachments, task sharing or teams, notifications, caching.

## Instructions for Claude Code

Build this project exactly as specified, in this order, and commit after each step with a clear message:

1. Project setup: Maven, dependencies, Docker Compose, Flyway, `application.yml` with environment variables
2. User entity, register and login with JWT, security config
3. Task entity, CRUD endpoints, ownership checks
4. Business rules (status moves, DONE lock, due date, task limit, optimistic locking)
5. Filtering, search, sort and paging with JPA Specifications; summary endpoint
6. Admin endpoints
7. Global error handling
8. Unit and integration tests
9. Swagger, README, GitHub Actions, `requests.http`

Ask me before adding anything that is not in this spec. Keep the code simple and readable: this is a portfolio project that recruiters will read.
