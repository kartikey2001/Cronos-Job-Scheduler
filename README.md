# Chronos

A production-grade distributed job scheduler REST API built with Java 21 and Spring Boot 3.

Schedule one-off or recurring webhook jobs, track execution history, and get automatic retries with exponential backoff — all through a clean JSON API secured with JWT.

---

## Features

- **One-off and cron jobs** — schedule a webhook to fire at a specific time or on a cron expression
- **Automatic retries** — configurable retry count and delay per job; failed attempts are re-queued via Redis
- **Dead-letter queue** — jobs that exhaust all retries move to a DLQ; retry them manually via the API
- **Execution history** — every attempt is logged with status, output, and error
- **Email notifications** — success and permanent-failure emails via SMTP
- **Request tracing** — every response carries an `X-Request-ID` header; the ID appears in every log line for that request
- **User isolation** — JWT-authenticated; users see only their own jobs

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 + Flyway |
| Queue | Redis 7 (Redisson `RDelayedQueue`) |
| Scheduler | Quartz (in-memory) |
| Auth | JWT (jjwt 0.12) + Spring Security |
| HTTP client | `RestTemplate` (5 s connect / 30 s read) |
| Testing | JUnit 5, Mockito, Testcontainers, WireMock, GreenMail |
| Coverage | JaCoCo >= 85% line coverage enforced on every build |

---

## Quick Start

**Prerequisites:** Docker Desktop, Java 21+, Maven 3.9+

```bash
# 1. Copy and fill in environment variables
cp .env.example .env

# 2. Start Postgres + Redis
docker compose up -d postgres redis

# 3. Run the application
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register — returns a JWT |
| `POST` | `/api/auth/login` | Login — returns a JWT |

```http
POST /api/auth/register
Content-Type: application/json

{ "username": "alice", "email": "alice@example.com", "password": "password123" }
```

```json
{ "token": "<jwt>", "username": "alice" }
```

---

### Jobs

All endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Description | Response |
|---|---|---|---|
| `POST` | `/api/jobs` | Create a job | `201` |
| `GET` | `/api/jobs` | List jobs (paginated) | `200` |
| `GET` | `/api/jobs/{id}` | Get a job | `200` |
| `PUT` | `/api/jobs/{id}` | Update a PENDING job | `200` |
| `DELETE` | `/api/jobs/{id}` | Cancel a PENDING job | `204` |
| `GET` | `/api/jobs/{id}/dlq` | List DLQ entries | `200` |
| `POST` | `/api/jobs/{id}/dlq/retry` | Re-enqueue a dead job | `202` |

**Create job request body:**

```json
{
  "name": "Nightly Report",
  "webhookUrl": "https://your-service.com/hook",
  "scheduledAt": "2026-06-01T02:00:00",
  "cronExpression": "0 0 2 * * ?",
  "description": "Optional description",
  "maxRetries": 3,
  "retryDelayMs": 5000
}
```

`cronExpression`, `description`, `maxRetries` (default `3`), and `retryDelayMs` (default `5000`) are optional.

**Job lifecycle:**

```
PENDING → RUNNING → SUCCESS
                 ↘ FAILED → (retry) → PENDING → ...
                                    → DEAD (retries exhausted)
PENDING → CANCELLED
```

**List response:**

```json
{
  "content": [ ...jobs ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false
}
```

---

## Architecture

```
HTTP Request
     │
     ▼
RequestIdFilter ──► MDC(requestId), X-Request-ID header
     │
     ▼
JwtAuthFilter ──► SecurityContext
     │
     ▼
JobController
     │
     ├─► JobService ──► QuartzJobScheduler ──► WebhookJobExecutor
     │                                                │
     │                                                ▼
     │                                    WebhookExecutionService
     │                                         │          │
     │                               (TX-1)    │   (HTTP) │   (TX-2)
     │                         beginExecution  │          │  finalizeSuccess
     │                                         │          │  finalizeFailure
     │                                         ▼          │
     │                               WebhookExecutionStore│
     │                               (DB ops only)        │
     │                                                     ▼
     └─► DlqService ◄─────────────────────── RetryService
                                              (RDelayedQueue → RBlockingQueue)
                                              RetryQueueConsumer (virtual thread)
```

**Key design decision — split transactions:**
`WebhookExecutionService.execute()` is not `@Transactional`. It calls `WebhookExecutionStore.beginExecution()` (TX-1, commits immediately), fires the HTTP request with no database connection held, then calls `finalizeSuccess/Failure` (TX-2). This prevents holding a connection-pool slot during potentially long network I/O.

---

## Running Tests

```bash
mvn verify
```

Tests require Docker for Testcontainers (spins up Postgres and Redis automatically).

100 tests across unit (Mockito), integration (MockMvc + Testcontainers), webhook (WireMock), and email (GreenMail) layers.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/chronos` |
| `DB_USERNAME` | Postgres user | `chronos` |
| `DB_PASSWORD` | Postgres password | `secret` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `JWT_SECRET` | HS256 key, base64-encoded, >= 32 bytes | `openssl rand -base64 32` |
| `JWT_EXPIRY_MS` | Token TTL in milliseconds | `3600000` |
| `SMTP_HOST` | SMTP host | `smtp.gmail.com` |
| `SMTP_PORT` | SMTP port | `587` |
| `SMTP_USER` | SMTP username | `you@gmail.com` |
| `SMTP_PASS` | SMTP password / app password | — |
| `MAIL_FROM` | Sender address | `noreply@chronos.io` |
