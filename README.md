# Task Manager API

A REST API where users register, log in and manage their own tasks: priority, status, due dates, filtering, search and paging. An admin can see all users and tasks and deactivate accounts.

It is a small portfolio project built to show core Java backend skills: Spring Boot, JPA, stateless JWT security, validation, testing and Docker.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation) |
| Build | Maven |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (stateless, HS256, jjwt) |
| Mapping | DTOs as Java records, manual mappers |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers, JaCoCo |
| Run | Docker + Docker Compose |
| CI | GitHub Actions (`mvn verify` on every push) |

## How to run

### With Docker (recommended)

```bash
docker compose up --build
```

This starts PostgreSQL and the API on <http://localhost:8080>. Flyway creates the schema and seeds the admin account. No `.env` file is needed: `docker-compose.yml` has demo defaults. To change them, copy `.env.example` to `.env` and edit it. Always set your own `JWT_SECRET` (`openssl rand -base64 32`) outside local demos.

- Swagger UI: <http://localhost:8080/swagger-ui.html>. Log in with `POST /auth/login`, click **Authorize**, and paste the `accessToken`.
- Default admin: `admin@taskmanager.local` / `ChangeMe123` (from `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

### Locally

Requires Java 21, Maven and a PostgreSQL 16 database. Set the variables listed in `.env.example`, then:

```bash
mvn spring-boot:run
```

### Tests

```bash
mvn verify
```

This runs the unit tests (`*Test`) and the integration tests (`*IT`, which need Docker for Testcontainers). The coverage report is written to `target/site/jacoco/index.html`.

## Environment variables

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection | local `taskmanager` DB |
| `JWT_SECRET` | Base64 HMAC key, ≥ 32 bytes | **required** |
| `JWT_EXPIRATION` | Token lifetime (ISO-8601) | `PT24H` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Admin account seeded by Flyway | **required** |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:4200` |

## Endpoints

All endpoints are under `/api/v1`. Everything except register and login needs `Authorization: Bearer <token>`.

| Method | Path | Description | Success |
| --- | --- | --- | --- |
| POST | `/auth/register` | Create account (fullName, email, password) | 201 |
| POST | `/auth/login` | Returns a JWT valid for 24 h | 200 |
| GET | `/users/me` | Current user's profile | 200 |
| POST | `/tasks` | Create task | 201 + `Location` |
| GET | `/tasks` | List my tasks (filters, search, sort, paging) | 200 |
| GET | `/tasks/{id}` | Get one task | 200 |
| PUT | `/tasks/{id}` | Update title, description, priority, dueDate (+ `version`) | 200 |
| PATCH | `/tasks/{id}/status` | Change status (+ `version`) | 200 |
| DELETE | `/tasks/{id}` | Delete task | 204 |
| GET | `/tasks/summary` | Counts by status, by priority, overdue count | 200 |
| GET | `/admin/users` | List users (paged) — ADMIN | 200 |
| PATCH | `/admin/users/{id}/deactivate` | Deactivate a user — ADMIN | 204 |
| GET | `/admin/tasks` | List all tasks (same filters) — ADMIN | 200 |

**Query parameters for `GET /tasks`** (all optional, combinable): `status` (repeatable), `priority` (repeatable), `dueFrom` / `dueTo` (`yyyy-MM-dd`), `overdue=true`, `q` (case-insensitive search in title and description), `sort` (`dueDate`, `priority`, `createdAt` or `title`, plus `,asc` or `,desc`; default `createdAt,desc`), `page` (default 0), `size` (default 10, max 50).

**Business rules**
- Status moves: `TODO → IN_PROGRESS → DONE`, `TODO → DONE`, and `DONE → IN_PROGRESS` (reopen). Any other move returns 409.
- `completedAt` is set when a task becomes DONE and cleared when it is reopened.
- A DONE task can't be edited until it is reopened (409).
- `dueDate` can't be in the past when a task is created or its due date is changed.
- Each user can have at most 200 open tasks (409).

**Errors** all use one RFC 7807 shape:

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

## Sample requests

More are in [`requests.http`](requests.http) (IntelliJ HTTP Client).

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Jane Doe","email":"jane@example.com","password":"secret123"}'

# Log in and keep the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"secret123"}' | jq -r .accessToken)

# Create a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Prepare sprint demo","priority":"HIGH","dueDate":"2030-01-15"}'

# Search and filter
curl "http://localhost:8080/api/v1/tasks?status=TODO&q=demo&sort=dueDate,asc" \
  -H "Authorization: Bearer $TOKEN"

# Move to IN_PROGRESS (send the version you last read)
curl -X PATCH http://localhost:8080/api/v1/tasks/1/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS","version":0}'

# Summary
curl http://localhost:8080/api/v1/tasks/summary -H "Authorization: Bearer $TOKEN"
```

## Design decisions

**Why JWT.** The API is stateless: any instance can verify a token with the shared secret, with no session store and no sticky sessions. On each request the filter still loads the user by the token's id. That costs one indexed lookup, and it is what lets a deactivated account's existing tokens stop working immediately instead of when they expire.

**Why optimistic locking.** Two edits to the same task rarely collide, so locking rows for every read would be wasteful. Each task has a `@Version`, and clients send back the version they last read. A stale version returns 409 instead of silently overwriting someone else's change (a "lost update"). The service compares versions explicitly, and JPA's version check on flush also catches two requests that race past that comparison.

**Why 404 for other users' tasks.** A 403 would confirm that a task with that id exists, so anyone could probe ids and count other users' tasks. The ownership check is part of the query (`findByIdAndOwnerId`), so "not yours" and "doesn't exist" are the same answer.

**Other choices**
- *Package by feature* (`auth`, `user`, `task`, `admin`, `common`), with thin controllers. All rules live in services, and only DTO records leave the API, never entities.
- *Priority is stored as a level* (1 = LOW, 2 = MEDIUM, 3 = HIGH) through a JPA converter, so `sort=priority` orders by importance rather than alphabetically.
- *One injectable `Clock`* is used for due-date checks, `completedAt`, audit timestamps and token expiry, so the time-based rules are tested deterministically.
- *Flyway owns the schema* (`ddl-auto: validate`). The admin is seeded with a BCrypt hash created by PostgreSQL's `pgcrypto`, so no password is ever committed.

## Project structure

```
com.tasneem.taskmanager
├── auth     AuthController, AuthService, JwtService, JwtFilter, DTOs
├── user     User entity, repository, service, controller, DTOs
├── task     Task entity, repository, specifications, sorting, service, controller, mapper, DTOs
├── admin    AdminController
└── common   error handling, custom exceptions, security/CORS/auditing/OpenAPI config, PageResponse
```
