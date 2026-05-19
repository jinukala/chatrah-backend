# Chatrah Backend — Enhancement & Microservices Rewrite Guide

> Generated: 2026-05-14

---

## 1. Executive Summary

The Chatrah backend is a Quarkus-based monolith covering student management, attendance, fee collection (Razorpay), exam records, and access-request workflows. While the core domain model is largely in place, the application is not production-ready: credentials are hardcoded, there are no migrations, no health checks, no observability stack, and several critical security and data-integrity bugs exist across the payment and authentication flows. Business logic is incomplete in every major service — validations are absent, analytics queries are unbounded, and concurrency hazards are unguarded. The path forward is a two-track effort: (1) stabilise the monolith with critical fixes so it can safely serve real users, and (2) incrementally decompose it into focused microservices using the Strangler Fig pattern, introducing proper observability, event-driven communication, and a modern DevOps pipeline along the way.

---

## 2. Critical Fixes (Must-Have Before Production)

Issues are ordered by severity (Critical → High → Medium).

| # | Issue | Category | Severity | Fix |
|---|-------|----------|----------|-----|
| 1 | Razorpay webhook signature never verified — payment forgery possible | Security | Critical | Verify `X-Razorpay-Signature` HMAC-SHA256 against webhook secret before processing any payment event |
| 2 | Hardcoded DB, SMTP, and Razorpay credentials in `application.properties` | Security | Critical | Externalise all secrets via environment variables or a secrets manager (AWS Secrets Manager / Vault); add profile separation (`%prod`, `%dev`) |
| 3 | PEM signing keys on filesystem with no confirmed `.gitignore` entry | Security | Critical | Add `*.pem` and `src/main/resources/*.pem` to `.gitignore`; rotate keys immediately if already committed; use env-var or mounted secret at runtime |
| 4 | `handlePaymentSuccess` fallback creates `FeePayment` with null student/amount | Data Integrity | Critical | Add null-guard and throw a recoverable exception; never persist a structurally invalid payment row |
| 5 | Webhook not idempotent — duplicate emails sent on Razorpay retries | Data Integrity | Critical | Store processed `razorpay_payment_id` with a unique DB constraint; skip processing if already seen |
| 6 | `resetPassword` and `markOtpUsed` are separate transactions — OTP reuse window | Security | Critical | Merge both operations into a single `@Transactional` method |
| 7 | `initiateOnlinePayment` has no idempotency key — duplicate orders possible | Data Integrity | Critical | Generate and persist a client-side idempotency key; return existing order if key already exists |
| 8 | No login brute-force protection | Security | High | Add rate-limiting per username/IP (Bucket4j or a Redis counter); lock account after N failures |
| 9 | User enumeration in OTP flow (different error messages for known vs unknown email) | Security | High | Return identical response regardless of whether the email exists |
| 10 | `validatePasswordResetOtp` increments attempts before checking code — user gets 4 attempts not 5 | Security / Logic | High | Check the code first, then increment on failure |
| 11 | `markAttendance` read-then-write with no lock or unique constraint on `(student, date, session)` | Concurrency | High | Add a `UNIQUE` DB constraint on `(student_id, date, session)`; use `INSERT … ON CONFLICT` upsert |
| 12 | `uploadExamMarks` no unique constraint on `(exam, student, subject)` — concurrent uploads create duplicates | Concurrency | High | Add unique constraint; implement upsert logic |
| 13 | Hibernate DDL set to `update` in production | Reliability | High | Set `quarkus.hibernate-orm.database.generation=validate`; introduce Flyway or Liquibase for all schema changes |
| 14 | Synchronous email sending inside `@Transactional` holds DB connection during SMTP I/O | Reliability | High | Move email dispatch to an async event (CDI `@Observes` or a message queue) outside the transaction boundary |
| 15 | `RazorpayClient` instantiated per-request (no pooling) | Reliability | High | Make `RazorpayClient` `@ApplicationScoped`; initialise once at startup |
| 16 | No retry or circuit-breaker on SMTP or Razorpay calls | Reliability | High | Add `@Retry` and `@CircuitBreaker` via MicroProfile Fault Tolerance |
| 17 | `GenericExceptionMapper` TODO — stack traces never logged | Observability | High | Replace TODO with `log.error("Unhandled exception", e)` and return a structured error body |
| 18 | JWT parse failure in `AccessRequestResource` falls back to `-1L` approver ID | Security / Logic | High | Throw `401 Unauthorized` on JWT parse failure; never use a sentinel ID |
| 19 | No `@Valid` on any DTO or service input | Data Integrity | High | Add Bean Validation annotations to all DTOs; annotate service/resource parameters with `@Valid` |
| 20 | `FeeService` payment status always set to `SUCCESS` without gateway verification | Logic | High | Only set `SUCCESS` after confirming payment status from Razorpay API or a verified webhook |
| 21 | Overpayment not prevented in `FeeService` | Logic | High | Check `amountPaid + newPayment <= totalDue` before persisting |
| 22 | `computeAttendanceAnalytics` loads entire attendance table into memory | Reliability | Medium | Rewrite as an aggregation SQL query; add pagination |
| 23 | `computeFeeAnalytics` has N+1 query pattern over all students | Reliability | Medium | Rewrite with a single JOIN query or batch fetch |
| 24 | `listByClass` returns unbounded list | Reliability | Medium | Add `@QueryParam("page")` / `@QueryParam("size")` pagination |
| 25 | Swagger UI always enabled (`always-include=true`) in production | Security | Medium | Set `quarkus.swagger-ui.always-include=false`; enable only in `%dev` profile |
| 26 | No duplicate `rollNo` check in `StudentService` | Data Integrity | Medium | Add unique DB constraint on `roll_no` + `class_id`; handle `ConstraintViolationException` gracefully |
| 27 | Access request has no unique constraint on `(teacherId, classId, status=PENDING)` | Data Integrity | Medium | Add partial unique index; return `409 Conflict` on duplicate submission |
| 28 | Dead OAuth dependencies (Google + Microsoft) with zero implementation | Security | Medium | Remove unused dependencies to reduce supply-chain attack surface |
| 29 | No test classes despite test dependencies present | Quality | Medium | Write at minimum one integration test per resource using `@QuarkusTest` |
| 30 | Overall exam pass% computed incorrectly (sum of per-subject passes, not per-student all-pass) | Logic | Medium | Fix aggregation: a student passes only if they pass every subject |

---

## 3. Feature Enhancements

### 3.1 Authentication & Identity
- **Token refresh & revocation**: Implement a `RefreshToken` entity (hashed, expiry, revoked flag). Issue short-lived JWTs (15 min) + long-lived refresh tokens (7 days). Add `POST /auth/refresh` and `POST /auth/logout` endpoints.
- **Password strength validation**: Enforce minimum length, complexity, and breach-check (HaveIBeenPwned API) on registration and password reset.
- **Email verification**: Repurpose the existing OTP infrastructure to verify email on registration before allowing login.
- **OAuth (Google + Microsoft)**: Implement OIDC login using `quarkus-oidc`; map external subject to internal `User` on first login.
- **MFA / TOTP**: Add optional TOTP second factor using a library like `java-otp`.

### 3.2 SMS Notifications
- Complete the MSG91 stub: implement `SmsService` with HTTP client, retry logic, and delivery status callback.
- Update `SmsLog` status from `PENDING` → `SENT` / `FAILED` based on callback.
- Send SMS for: OTP, fee receipt, attendance alerts, exam results.

### 3.3 Fee Management
- **Fee overrides**: Wire the injected `FeeOverrideService` / advanced-course fee logic so per-student overrides are actually applied.
- **Fee defaulter report**: Query students where `totalPaid < totalDue` grouped by class; expose as `GET /fees/defaulters`.
- **Fee collection trend**: Time-series aggregation by week/month; expose as `GET /fees/analytics/trend?from=&to=`.
- **Cash payment audit**: Record `recordedBy` (staff ID) on every manual payment.
- **CSV/PDF export**: Add `GET /fees/export?format=csv|pdf` using Apache POI / iText.

### 3.4 Attendance
- **Future-date guard**: Reject attendance dates after today.
- **Student-class membership check**: Verify the student belongs to the given class before recording.
- **Below-threshold alert**: Nightly job (Quarkus Scheduler) to flag students below configurable attendance % and send notification.
- **Query by date/class/session**: Add filter params to `GET /attendance`.
- **Attendance history**: Preserve overwrite history in an `AttendanceAudit` table.

### 3.5 Exam & Marks
- **Upsert on marks upload**: Replace insert with `INSERT … ON CONFLICT (exam_id, student_id, subject) DO UPDATE`.
- **Marks validation**: Reject marks outside `[0, maxMarks]`.
- **Class rank / topper report**: Rank students by total marks per exam; expose `GET /exams/{id}/rankings`.
- **Academic-year scoping**: Add `academicYear` filter to all exam and analytics endpoints.

### 3.6 Student Management
- **Search & filter**: Add `GET /students?name=&rollNo=&classId=` with indexed columns.
- **Missing DTO fields**: Expose `isHosteller` and `isTransportUser` in `StudentDTO`.
- **Hard delete guard**: Return `404` (not silent no-op) when deleting a non-existent student.
- **Field-level change history**: Introduce `StudentAuditLog` capturing old/new values on update.

### 3.7 Access Requests
- **Access expiry**: Add `expiresAt` field; a scheduler revokes expired grants.
- **Rejection metadata**: Add `rejectedBy`, `rejectedAt`, `rejectionReason` fields.
- **Duplicate guard**: Enforce unique pending request per `(teacherId, classId)`.

### 3.8 Reporting & Analytics
- **Academic-year scoping** on all analytics endpoints.
- **CSV/PDF export** for attendance, fee, and exam reports.
- **Correct pass% calculation**: Per-student all-subject pass logic.

---


## 4. Observability & DevOps Stack

### 4.1 Logging
`quarkus-logging-json` is already present — structured JSON logs are emitted to stdout. Remaining steps:

1. **Correlation IDs**: Add a JAX-RS `ContainerRequestFilter` that reads `X-Request-ID` (or generates a UUID) and stores it in MDC key `requestId`. Include it in every log line and every outbound HTTP call header.
2. **Fix `GenericExceptionMapper`**: Replace the TODO with `log.error("Unhandled exception [requestId={}]", requestId, e)`.
3. **Sensitive field masking**: Never log passwords, OTP codes, or card numbers. Use a custom `JsonLayout` field filter.
4. **Log levels by profile**: `%dev` → DEBUG, `%prod` → INFO, configurable via env var `QUARKUS_LOG_LEVEL`.

### 4.2 Metrics (Micrometer + Prometheus)

Add dependency:
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

Key custom metrics to instrument:
- `payment.initiated.count` / `payment.success.count` / `payment.failure.count` (tags: `gateway`)
- `otp.sent.count` / `otp.verified.count` / `otp.failed.count`
- `attendance.recorded.count` (tags: `session`)
- `http.request.duration` (auto-provided by Micrometer)
- JVM heap, GC pause, thread pool — auto-provided

Expose at `GET /q/metrics` (Prometheus scrape endpoint, restricted to internal network).

### 4.3 Distributed Tracing (OpenTelemetry)

Add dependency:
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

Configuration:
```properties
quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
quarkus.otel.resource.attributes=service.name=chatrah-backend,service.version=${app.version}
```

- Traces propagate via W3C `traceparent` header.
- Instrument Hibernate queries, REST client calls, and async events automatically.
- Ship traces to Jaeger or Tempo via the OTLP collector.

### 4.4 Health Checks (SmallRye Health)

Add dependency:
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

Implement custom probes:

| Probe | Type | Checks |
|-------|------|--------|
| `DatabaseHealthCheck` | Readiness | `SELECT 1` against the datasource |
| `RazorpayHealthCheck` | Readiness | Ping Razorpay API key validity |
| `SmtpHealthCheck` | Readiness | TCP connect to SMTP host:port |
| `DiskSpaceHealthCheck` | Liveness | Available disk > 200 MB |

Endpoints: `GET /q/health/live`, `GET /q/health/ready`, `GET /q/health`.

### 4.5 Docker & docker-compose

**`Dockerfile`** (multi-stage, JVM mode):
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/quarkus-app /app/quarkus-app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-app/quarkus-run.jar"]
```

**`docker-compose.yml`** (local dev):
```yaml
version: "3.9"
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: chatrah
      POSTGRES_USER: chatrah
      POSTGRES_PASSWORD: secret
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]

  app:
    build: .
    ports: ["8080:8080"]
    environment:
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://db:5432/chatrah
      QUARKUS_DATASOURCE_USERNAME: chatrah
      QUARKUS_DATASOURCE_PASSWORD: secret
      RAZORPAY_KEY_ID: ${RAZORPAY_KEY_ID}
      RAZORPAY_KEY_SECRET: ${RAZORPAY_KEY_SECRET}
    depends_on: [db]

  prometheus:
    image: prom/prometheus:v2.51.0
    volumes: [./infra/prometheus.yml:/etc/prometheus/prometheus.yml]
    ports: ["9090:9090"]

  grafana:
    image: grafana/grafana:10.4.0
    ports: ["3000:3000"]
    depends_on: [prometheus]

  jaeger:
    image: jaegertracing/all-in-one:1.56
    ports: ["16686:16686", "4317:4317"]

volumes:
  pgdata:
```

### 4.6 CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  build-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env: { POSTGRES_DB: chatrah, POSTGRES_USER: chatrah, POSTGRES_PASSWORD: secret }
        ports: ["5432:5432"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: ./mvnw verify
        env:
          QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://localhost:5432/chatrah
          QUARKUS_DATASOURCE_USERNAME: chatrah
          QUARKUS_DATASOURCE_PASSWORD: secret

  docker-push:
    needs: build-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
      - uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/${{ github.repository }}:${{ github.sha }},ghcr.io/${{ github.repository }}:latest
```

Pipeline stages: **Lint → Unit Tests → Integration Tests → Build Image → Push → Deploy (staging)**.

---

## 5. Microservices Architecture

### 5.1 ASCII Service Diagram

```
                          ┌─────────────────────────────────────────────────────┐
                          │                  API Gateway (Kong / AWS ALB)        │
                          └──────┬──────┬──────┬──────┬──────┬──────┬───────────┘
                                 │      │      │      │      │      │
                    ┌────────────▼─┐ ┌──▼───┐ ┌▼─────┴┐ ┌───▼──┐ ┌▼──────────┐
                    │ Auth Service │ │Student│ │  Fee  │ │Attend│ │   Exam    │
                    │  :8081       │ │Service│ │Service│ │Service│ │  Service  │
                    │              │ │ :8082 │ │ :8083 │ │ :8084 │ │  :8085   │
                    └──────┬───────┘ └──┬───┘ └───┬───┘ └───┬──┘ └─────┬─────┘
                           │            │          │         │           │
                           └────────────┴──────────┴────┬────┴───────────┘
                                                         │  Domain Events
                                                  ┌──────▼──────┐
                                                  │   Message   │
                                                  │   Broker    │
                                                  │  (Kafka /   │
                                                  │  RabbitMQ)  │
                                                  └──────┬──────┘
                                                         │
                                          ┌──────────────▼──────────────┐
                                          │     Notification Service     │
                                          │  (Email + SMS)  :8086        │
                                          └─────────────────────────────┘

  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────────────┐
  │  Access-Request  │    │  Reporting /     │    │  Admin / Config Service  │
  │  Service :8087   │    │  Analytics :8088 │    │         :8089            │
  └──────────────────┘    └──────────────────┘    └──────────────────────────┘

  All services → PostgreSQL (separate schema/DB per service)
  All services → Prometheus scrape → Grafana
  All services → OTLP → Jaeger / Tempo
  All services → Fluent Bit → Loki / CloudWatch
```

### 5.2 Service Decomposition Table

| Service | Bounded Context | Owns Entities | Exposes API | Tech |
|---------|----------------|---------------|-------------|------|
| **Auth Service** | Identity & Access | `User`, `OtpRecord`, `RefreshToken` | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/register`, `/auth/otp/*` | Quarkus, JWT, PostgreSQL |
| **Student Service** | Student Registry | `Student`, `StudentAuditLog` | `GET/POST/PUT/DELETE /students`, `/students/search` | Quarkus, PostgreSQL |
| **Fee Service** | Fee Collection | `FeeStructure`, `FeePayment`, `FeeReceipt`, `FeeOverride` | `GET/POST /fees`, `/fees/pay`, `/fees/webhook`, `/fees/defaulters`, `/fees/analytics` | Quarkus, Razorpay SDK, PostgreSQL |
| **Attendance Service** | Attendance Tracking | `Attendance`, `AttendanceAudit` | `POST /attendance`, `GET /attendance`, `/attendance/analytics` | Quarkus, PostgreSQL |
| **Exam Service** | Academic Assessment | `Exam`, `ExamMark`, `Subject` | `GET/POST /exams`, `/exams/{id}/marks`, `/exams/{id}/rankings` | Quarkus, PostgreSQL |
| **Notification Service** | Messaging | `EmailLog`, `SmsLog` | Internal only (consumes events) | Quarkus, Mailer, MSG91, PostgreSQL |
| **Access-Request Service** | Authorization Workflow | `AccessRequest` | `POST /access-requests`, `GET /access-requests`, `PUT /access-requests/{id}/approve` | Quarkus, PostgreSQL |
| **Reporting Service** | Analytics & Exports | Read models (materialized views) | `GET /reports/fees`, `/reports/attendance`, `/reports/exams` (CSV/PDF) | Quarkus, Apache POI, iText, PostgreSQL read replica |
| **API Gateway** | Routing & Auth enforcement | — | All public routes | Kong / AWS ALB + Lambda Authorizer |

### 5.3 Event Catalog

| Event | Producer | Consumers | Trigger |
|-------|----------|-----------|---------|
| `student.created` | Student Service | Notification Service | New student registered |
| `student.updated` | Student Service | Reporting Service | Student record changed |
| `student.deleted` | Student Service | Fee, Attendance, Exam Services | Student removed |
| `payment.initiated` | Fee Service | Notification Service | Razorpay order created |
| `payment.success` | Fee Service | Notification Service, Reporting Service | Webhook verified |
| `payment.failed` | Fee Service | Notification Service | Webhook failure event |
| `attendance.recorded` | Attendance Service | Reporting Service, Notification Service | Bulk attendance saved |
| `attendance.threshold.breached` | Attendance Service | Notification Service | Student drops below configured % |
| `exam.marks.uploaded` | Exam Service | Reporting Service, Notification Service | Marks batch saved |
| `otp.requested` | Auth Service | Notification Service | OTP generation |
| `access.request.approved` | Access-Request Service | Auth Service, Notification Service | Admin approves request |
| `access.request.rejected` | Access-Request Service | Notification Service | Admin rejects request |

### 5.4 Data Ownership Map

Each service owns its own PostgreSQL database/schema. Cross-service reads use published events or dedicated read-model projections. No service queries another service's database directly.

| Service | Database | Key Tables | Shared Via |
|---------|----------|------------|------------|
| Auth | `auth_db` | `users`, `otp_records`, `refresh_tokens` | JWT (stateless); `user.created` event |
| Student | `student_db` | `students`, `student_audit_log` | `student.*` events; Student Service REST API |
| Fee | `fee_db` | `fee_structures`, `fee_payments`, `fee_receipts` | `payment.*` events |
| Attendance | `attendance_db` | `attendance`, `attendance_audit` | `attendance.*` events |
| Exam | `exam_db` | `exams`, `exam_marks`, `subjects` | `exam.*` events |
| Notification | `notification_db` | `email_logs`, `sms_logs` | Consumes all notification-trigger events |
| Access-Request | `access_db` | `access_requests` | `access.*` events |
| Reporting | `reporting_db` | Materialized views / projections | Consumes all domain events |

---


## 6. Migration Roadmap (Strangler Fig)

The Strangler Fig pattern lets us run the monolith and new services side-by-side, routing traffic incrementally until the monolith can be decommissioned.

### Phase 1 — Stabilise the Monolith
**What**: Apply all Critical and High severity fixes from Section 2. Add Flyway migrations, profile-separated config, `.gitignore` for PEM files, Dockerfile, basic CI pipeline, and SmallRye Health endpoints.

**Why**: The monolith must be safe and observable before any decomposition begins. Extracting a broken service just distributes the bugs.

**Estimated effort**: 3–4 weeks (1–2 engineers)

---

### Phase 2 — Extract Auth Service
**What**: Move `User`, `OtpRecord`, `RefreshToken`, and all `/auth/*` endpoints into a standalone `auth-service`. The monolith delegates authentication to the new service via REST or validates JWTs locally (public key shared). Implement token refresh, revocation, email verification, and OAuth.

**Why**: Auth is the highest-risk bounded context (security bugs, no refresh/revocation). Isolating it lets the team harden it independently and apply stricter security controls (WAF, rate-limiting at the gateway level).

**Estimated effort**: 3–4 weeks (1–2 engineers)

---

### Phase 3 — Extract Fee Service & Notification Service
**What**: Extract `FeeStructure`, `FeePayment`, `FeeReceipt` and all `/fees/*` endpoints into `fee-service`. Extract email/SMS dispatch into `notification-service` consuming events from a message broker (Kafka or RabbitMQ). Wire `payment.success` / `payment.failed` events to replace synchronous email calls.

**Why**: Fee collection is the most financially critical path and has the most concurrency/integrity bugs. Notification extraction removes the synchronous email-in-transaction anti-pattern from every other service simultaneously.

**Estimated effort**: 4–5 weeks (2 engineers)

---

### Phase 4 — Extract Remaining Domain Services
**What**: Extract in order: `student-service` → `attendance-service` → `exam-service` → `access-request-service` → `reporting-service`. For each: create new service, migrate schema with Flyway, publish domain events, update API Gateway routing, deprecate monolith route.

**Why**: By this phase the event infrastructure and deployment pipeline are proven. Each extraction is lower risk and follows the same playbook.

**Estimated effort**: 6–8 weeks (2–3 engineers, services extracted in parallel where bounded contexts are independent)

---

### Phase 5 — Decommission Monolith
**What**: Verify 100% of traffic is routed to microservices via the API Gateway. Remove monolith from docker-compose and CI. Archive the repository. Migrate any remaining shared config to the Admin/Config service.

**Why**: Eliminate the operational burden of maintaining a dead codebase and remove the risk of accidental fallback to the old, unfixed paths.

**Estimated effort**: 1–2 weeks (validation, monitoring, cutover)

---

## 7. Technology Recommendations

| Concern | Current | Recommended | Reason |
|---------|---------|-------------|--------|
| **Database migrations** | Hibernate `update` (DDL auto) | Flyway | Versioned, repeatable, auditable schema changes; safe for production |
| **Secret management** | Hardcoded in `application.properties` | AWS Secrets Manager or HashiCorp Vault | Secrets never in source control; rotation without redeployment |
| **Message broker** | None (synchronous calls) | Apache Kafka | Durable, replayable event log; decouples producers from consumers; supports event sourcing |
| **API Gateway** | None (direct service exposure) | Kong (self-hosted) or AWS API Gateway | Centralised auth enforcement, rate-limiting, routing, TLS termination |
| **Caching** | Quarkus `@CacheResult` (in-process) | Redis (via `quarkus-redis-client`) | Shared cache across instances; supports distributed invalidation; TTL-based expiry |
| **Search / filtering** | JPA `LIKE` queries | PostgreSQL full-text search (short term) / OpenSearch (long term) | Efficient student/record search without full table scans |
| **Distributed tracing** | None | OpenTelemetry + Jaeger / AWS X-Ray | End-to-end request visibility across microservices |
| **Metrics & dashboards** | None | Micrometer + Prometheus + Grafana | Standard Quarkus integration; rich JVM and business metrics |
| **Log aggregation** | Stdout JSON | Fluent Bit → AWS CloudWatch Logs / Loki | Centralised search, alerting, and retention across all services |
| **Service mesh** | None | Istio or AWS App Mesh (Phase 4+) | mTLS between services, traffic shaping, retries at the mesh layer |
| **Container orchestration** | None | Kubernetes (EKS) or AWS ECS | Auto-scaling, rolling deployments, self-healing |
| **CI/CD** | None | GitHub Actions + ArgoCD | GitOps-based deployments; environment promotion pipeline |
| **SMS provider** | MSG91 stub (unimplemented) | MSG91 (complete implementation) or AWS SNS | MSG91 already chosen; AWS SNS as fallback for reliability |
| **PDF/CSV export** | None | Apache POI (Excel/CSV) + iText (PDF) | Mature Java libraries; no external service dependency |
| **Rate limiting** | None | Bucket4j (in-process) or Kong rate-limit plugin | Protect auth endpoints from brute force; protect payment endpoints from abuse |

---

## 8. Agentic Opportunities in Microservices

As the system decomposes into microservices with a shared event bus, AI agents can operate as first-class participants in the architecture — consuming events, making decisions, and emitting actions without human intervention.

### 8.1 Saga Orchestration Agent
**Problem**: Multi-step workflows like fee payment (initiate → gateway → verify → receipt → notify) span multiple services and can fail at any step.

**Agent role**: A stateful saga orchestrator subscribes to `payment.*` events and drives compensating transactions on failure (e.g., cancel Razorpay order, mark payment as `FAILED`, trigger refund notification). Uses an LLM or rule engine to decide between retry, compensate, or escalate based on error type and retry count.

### 8.2 Anomaly Detection Agent
**Problem**: Unusual patterns (sudden spike in failed payments, bulk attendance manipulation, exam mark outliers) are invisible without active monitoring.

**Agent role**: Consumes the event stream in real time, maintains rolling statistics, and raises alerts when values deviate beyond configurable thresholds. Can auto-flag suspicious `payment.success` events where the amount differs from the fee structure, or attendance records submitted outside school hours.

### 8.3 Fee Defaulter Follow-up Agent
**Problem**: Manually identifying and chasing fee defaulters is time-consuming.

**Agent role**: Runs on a nightly schedule, queries the Reporting Service for defaulters, generates personalised reminder messages (using an LLM for tone/language), and dispatches them via the Notification Service. Escalates to a different message template after N reminders with no payment.

### 8.4 Auto-Scaling Advisor Agent
**Problem**: Traffic to Fee Service spikes at fee-due dates; Attendance Service spikes at school start/end times.

**Agent role**: Monitors Prometheus metrics for each service, predicts load based on the academic calendar (fee due dates, exam schedules), and pre-emptively adjusts Kubernetes HPA `minReplicas` or ECS desired count before the spike arrives.

### 8.5 Schema Migration Safety Agent
**Problem**: Flyway migrations in a microservices environment can break backward compatibility between service versions during rolling deployments.

**Agent role**: Analyses proposed migration scripts against the current schema and running service versions, flags breaking changes (column drops, type changes, NOT NULL additions without defaults), and blocks the CI pipeline until a safe migration strategy (expand-contract pattern) is confirmed.

### 8.6 Audit & Compliance Agent
**Problem**: No global audit trail exists; regulatory or institutional audits require reconstructing who did what and when.

**Agent role**: Subscribes to all domain events and writes immutable audit records to an append-only store (e.g., AWS QLDB or an event-sourced audit log). Can answer natural-language queries like "show all fee payments recorded by staff member X in March" by querying the audit store.

### 8.7 Intelligent Report Generation Agent
**Problem**: Administrators need ad-hoc reports that don't map to pre-built endpoints.

**Agent role**: Exposes a natural-language query interface (`POST /reports/ask` with `{ "question": "Which students in Class 10A have attendance below 75% this term?" }`). The agent translates the question into a SQL query against the Reporting Service's read model, executes it, and returns a formatted response with optional CSV export.

---

*Document generated by Kiro — 2026-05-14*
