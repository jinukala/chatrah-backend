# Reliability & Observability Brainstorms
Generated: 2026-05-14

---

# Brainstorm: Async Email Dispatch
Date: 2026-05-14

## Problem
`NotificationService` sends email synchronously inside `@Transactional` methods (`sendFeePaymentNotification`, `sendAttendanceAbsentNotification`, etc.). The `mailer.send(...)` call blocks the thread and holds the DB connection open during SMTP I/O (which can take seconds or time out). If SMTP is slow or down, the entire business transaction rolls back or hangs, causing data loss or degraded UX for unrelated operations like fee recording.

## Constraints
- Quarkus 3.10, Java 17, `quarkus-mailer` already on classpath.
- No message broker (Kafka/RabbitMQ) currently in the project.
- `Notification` entity must still be persisted (status tracking is required).
- The fix must not break the existing `Notification` status lifecycle (PENDING → SENT/FAILED).
- Must remain within the monolith for now (microservices extraction is Phase 3).

## Existing Patterns That Apply
- Quarkus CDI `@Observes` / `@ObservesAsync` for in-process async events.
- `quarkus-mailer` supports both blocking `Mailer` and reactive `ReactiveMailer`.
- `@Transactional(REQUIRES_NEW)` can isolate the email send from the parent transaction.

## Approaches Considered

### Option A — CDI `@ObservesAsync` (fire-and-forget event)
Split into two beans: the transactional service fires a CDI event carrying the notification payload; a separate `@ApplicationScoped` listener annotated `@ObservesAsync` picks it up on a worker thread outside the transaction and calls `mailer.send(...)`, then updates the `Notification` status in its own transaction.

Pros: No new infrastructure, pure CDI, already supported by Quarkus. Clean separation.
Cons: In-process — if the JVM crashes between fire and send, the email is lost. Acceptable for now.

### Option B — Quarkus Scheduler + outbox table
Persist the `Notification` row (status=PENDING) inside the business transaction, then a `@Scheduled` job polls for PENDING rows every N seconds and dispatches them. This is the Transactional Outbox pattern.

Pros: Durable — survives JVM crash. Naturally idempotent with a status flag.
Cons: Adds polling latency (up to N seconds delay). More moving parts for a monolith fix.

### Option C — `ReactiveMailer` with `@Transactional(REQUIRES_NEW)` on send
Keep the send in-line but wrap it in a new transaction so SMTP failure doesn't roll back the parent. Use `ReactiveMailer.send(...).subscribe()` to avoid blocking.

Pros: Minimal code change.
Cons: Still ties SMTP latency to the request thread. Does not truly decouple I/O from the transaction.

## Decision
**Option A** for the monolith stabilisation phase. Fire a CDI async event after persisting the `Notification` row (status=PENDING). The async observer handles SMTP and updates status in its own `@Transactional` context. Option B (outbox) is the right long-term pattern and should be adopted when the Notification Service is extracted in Phase 3.

## Open Questions
- Should the async observer use a dedicated `ExecutorService` or rely on Quarkus's default managed executor?
- What is the retry behaviour on SMTP failure in the async observer? (Ties into item #16.)
- Should failed notifications be retried automatically or require manual re-trigger?

## Next Step
1. Create `NotificationEvent` record/DTO carrying recipient, subject, body, `notificationId`.
2. In `NotificationService`, replace `mailer.send(...)` with `Event<NotificationEvent>.fireAsync(...)` after persisting the row.
3. Create `NotificationDispatcher` (`@ApplicationScoped`) with `void onNotification(@ObservesAsync NotificationEvent e)` that calls `mailer.send(...)` and updates status.

---

# Brainstorm: RazorpayClient Singleton
Date: 2026-05-14

## Problem
`RazorpayClient` (from `razorpay-java 1.2.0`) is instantiated per-request inside `FeeService` (or wherever payment calls are made). Each instantiation creates a new HTTP client, loads credentials, and allocates connection resources. Under load this wastes memory and connections, and risks hitting Razorpay's connection limits.

## Constraints
- `razorpay-java 1.2.0` is on the classpath; `RazorpayClient` constructor takes `(keyId, keySecret)`.
- Credentials must come from config (not hardcoded) — item #2 fix is a prerequisite.
- Must be thread-safe: multiple concurrent requests will share the singleton.
- Quarkus CDI lifecycle must be respected (no static singletons).

## Existing Patterns That Apply
- Quarkus `@ApplicationScoped` producer method (`@Produces`) for third-party clients.
- `@ConfigProperty` injection for externalised credentials.
- `RazorpayClient` is documented as thread-safe for concurrent use once constructed.

## Approaches Considered

### Option A — `@ApplicationScoped` CDI producer
Create a `RazorpayClientProducer` bean with a `@Produces @ApplicationScoped` method that reads `%{RAZORPAY_KEY_ID}` and `%{RAZORPAY_KEY_SECRET}` via `@ConfigProperty` and returns a single `RazorpayClient` instance. Inject it with `@Inject RazorpayClient razorpayClient` wherever needed.

Pros: Standard Quarkus pattern. Lifecycle managed by CDI. Credentials injected cleanly.
Cons: None significant.

### Option B — Singleton inside `FeeService` with `@PostConstruct`
Annotate `FeeService` as `@ApplicationScoped` and initialise `RazorpayClient` in a `@PostConstruct` method.

Pros: Fewer classes.
Cons: Couples client lifecycle to `FeeService`. Cannot be reused by other services (e.g., a future `RefundService`). Harder to mock in tests.

### Option C — Static holder class
Use a static `getInstance()` pattern.

Pros: Simple.
Cons: Not CDI-managed, not injectable, not testable, violates Quarkus idioms. Rejected.

## Decision
**Option A**. A dedicated `RazorpayClientProducer` with `@Produces @ApplicationScoped` is the idiomatic Quarkus approach, keeps credentials in config, and makes the client injectable and mockable in tests.

## Open Questions
- Does `RazorpayClient 1.2.0` expose a `close()` / `destroy()` method that should be called on shutdown? If so, add a `@PreDestroy` method.
- Should a `RazorpayHealthCheck` (item in Section 4.4) reuse this same singleton to ping the API?

## Next Step
1. Add `@ConfigProperty(name = "razorpay.key.id") String keyId` and `keySecret` to a new `RazorpayClientProducer`.
2. Annotate the producer method `@Produces @ApplicationScoped`.
3. Remove all `new RazorpayClient(...)` calls from `FeeService`; replace with `@Inject RazorpayClient`.
4. Add `razorpay.key.id` and `razorpay.key.secret` to `application.properties` mapped to env vars.

---

# Brainstorm: Fault Tolerance — @Retry + @CircuitBreaker on SMTP and Razorpay
Date: 2026-05-14

## Problem
Neither SMTP calls (`mailer.send(...)`) nor Razorpay API calls have any retry or circuit-breaker logic. A transient SMTP failure silently marks the notification as FAILED with no retry. A Razorpay API blip causes the entire payment flow to fail. Under sustained outage, threads pile up waiting for timeouts, potentially exhausting the thread pool.

## Constraints
- `quarkus-smallrye-fault-tolerance` is NOT currently in `pom.xml` — must be added.
- Fault tolerance annotations (`@Retry`, `@CircuitBreaker`, `@Timeout`) only work on CDI bean methods.
- SMTP send will be on the async observer (after item #14 fix), so annotations apply cleanly there.
- Razorpay calls are synchronous blocking SDK calls — must be wrapped in a CDI method to apply annotations.
- `@CircuitBreaker` state is per-instance by default in SmallRye; with `@ApplicationScoped` beans this is effectively global, which is correct.

## Existing Patterns That Apply
- MicroProfile Fault Tolerance (`@Retry`, `@CircuitBreaker`, `@Timeout`, `@Fallback`) — standard Quarkus extension.
- The async CDI observer from item #14 is the natural place to apply `@Retry` on SMTP.
- The `@ApplicationScoped` `RazorpayClient` singleton from item #15 means Razorpay calls are on a single bean — ideal for `@CircuitBreaker`.

## Approaches Considered

### Option A — Annotations directly on service methods
Add `@Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS, retryOn = Exception.class)` on `NotificationDispatcher.onNotification(...)` and on a `RazorpayGateway.createOrder(...)` wrapper method. Add `@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)` on the same methods.

Pros: Declarative, minimal code, standard MicroProfile. Works out of the box with Quarkus.
Cons: Retry on SMTP must not retry inside a transaction (resolved by item #14 async decoupling).

### Option B — Manual retry loop with exponential backoff
Implement retry logic manually using a loop with `Thread.sleep` and exponential backoff.

Pros: Full control.
Cons: Verbose, error-prone, reinvents the wheel. Rejected.

### Option C — Resilience4j directly
Add Resilience4j as a standalone library.

Pros: More configuration options.
Cons: Redundant — SmallRye Fault Tolerance wraps Resilience4j internally in Quarkus. Adds unnecessary dependency. Rejected.

## Decision
**Option A**. Add `quarkus-smallrye-fault-tolerance` to `pom.xml`. Apply `@Retry` + `@CircuitBreaker` + `@Timeout` on the SMTP dispatch method and on a new `RazorpayGateway` wrapper bean. Add `@Fallback` methods that log the failure and update the relevant status (Notification → FAILED, payment → error response).

## Open Questions
- Should SMTP retries use `@Retry` with `abortOn = {AddressException.class}` to avoid retrying on invalid email addresses?
- What is the right `@CircuitBreaker` threshold for Razorpay — 5 failures in 10 requests seems reasonable but needs load testing to confirm.
- Should circuit-breaker state be exposed as a metric (ties into Section 4 observability)?

## Next Step
1. Add `<artifactId>quarkus-smallrye-fault-tolerance</artifactId>` to `pom.xml`.
2. Create `RazorpayGateway` (`@ApplicationScoped`) wrapping `RazorpayClient` calls with `@Retry` + `@CircuitBreaker` + `@Timeout`.
3. Annotate `NotificationDispatcher.onNotification(...)` with `@Retry(maxRetries=3)` + `@CircuitBreaker` + `@Fallback`.
4. Write unit tests using `@QuarkusTest` with a mock SMTP server (e.g., GreenMail) to verify retry behaviour.


---

# Brainstorm: GenericExceptionMapper — Fix TODO, Structured Error Logging
Date: 2026-05-14

## Problem
`GenericExceptionMapper.toResponse(Throwable)` contains a `// TODO: log exception stack trace` comment — meaning unhandled exceptions are silently swallowed. The client gets a generic 500 body but the server logs nothing. Debugging production issues is impossible without the stack trace. Additionally, the error response lacks a `timestamp` and `requestId` field, making log correlation difficult.

## Constraints
- `quarkus-logging-json` is already on the classpath — structured JSON logs are emitted automatically.
- `UriInfo` is already injected via `@Context`.
- A correlation ID filter (item from Section 4.1) may not exist yet — the logger call should degrade gracefully if MDC `requestId` is absent.
- The client-facing message must NOT leak internal details (stack trace, class names).
- Must use the standard Quarkus/JBoss `Logger` (`org.jboss.logging.Logger`), not `System.out`.

## Existing Patterns That Apply
- `org.jboss.logging.Logger` is the standard logger in Quarkus; `Logger.getLogger(Class)` is the idiom.
- MDC (`org.jboss.logging.MDC`) can carry `requestId` set by a `ContainerRequestFilter`.
- `ErrorResponseDTO` already exists and has `status`, `error`, `message`, `path` fields — add `timestamp` and `requestId`.

## Approaches Considered

### Option A — Minimal fix: add `log.error` + timestamp
Replace the TODO with `log.error("Unhandled exception", exception)`. Add `timestamp` (ISO-8601) and `requestId` (read from `MDC.get("requestId")`, fallback to `"unknown"`) to `ErrorResponseDTO`.

Pros: Tiny change, immediately actionable, no new dependencies.
Cons: `requestId` only works once the `ContainerRequestFilter` is in place; until then it logs `"unknown"`.

### Option B — Structured log with explicit fields
Use `log.errorf` or a structured log builder to emit a JSON log entry with explicit fields (`requestId`, `path`, `exceptionClass`, `message`) rather than relying on the JSON layout to serialize the exception automatically.

Pros: More queryable in log aggregation tools (CloudWatch Insights, Loki).
Cons: Slightly more verbose; the JSON logging extension already serializes the exception well.

### Option C — Separate error-tracking integration (Sentry/Rollbar)
Send unhandled exceptions to an external error tracker.

Pros: Rich error grouping, alerting, breadcrumbs.
Cons: New external dependency, out of scope for monolith stabilisation. Defer to Phase 4+.

## Decision
**Option A** now, with Option B's field discipline applied: log at ERROR level with the full throwable, include `requestId` from MDC, and enrich `ErrorResponseDTO` with `timestamp` and `requestId`. This is a 10-line change with immediate observability value.

## Open Questions
- Should `WebApplicationException` subclasses (4xx) be excluded from ERROR-level logging and logged at WARN instead? (Avoids noise from client errors.)
- Once the `ContainerRequestFilter` for correlation IDs is added, should `GenericExceptionMapper` inject the filter's `requestId` directly or always read from MDC?
- Should `ErrorResponseDTO` be versioned (add `apiVersion` field) for future API evolution?

## Next Step
1. Add `private static final Logger log = Logger.getLogger(GenericExceptionMapper.class);` field.
2. Replace TODO with `log.error("Unhandled exception [path=" + path + "]", exception);`.
3. Add `timestamp` (current ISO-8601) and `requestId` (from `MDC.get("requestId")`) to `ErrorResponseDTO` and its setter/getter.
4. Populate both fields in `toResponse(...)` before building the response.
5. Optionally: skip ERROR logging for `WebApplicationException` with status < 500.

---

# Brainstorm: Analytics N+1 and Unbounded Queries — Pagination + SQL Aggregation
Date: 2026-05-14

## Problem
Three concrete issues in `AnalyticsService`:

1. **`computeAttendanceAnalytics()`** calls `attendanceRepository.listAll()` — loads the entire `attendance` table into JVM heap. For a school with 500 students × 200 school days × 2 sessions = 200,000 rows, this is a full table scan on every analytics request.

2. **`computeFeeAnalytics()`** has a classic N+1: it iterates over every `ClassRoom`, then every `Student` in that class, then calls `feeService.computeFeeSummary(studentId)` per student — each of which likely issues 2–3 queries. For 500 students this is ~1,500 queries per analytics call.

3. **`listByClass`** (referenced in item #24) returns an unbounded list of students with no pagination, risking OOM and slow responses as the student count grows.

## Constraints
- Quarkus 3.10 + Hibernate ORM Panache + PostgreSQL.
- `AttendanceRepository`, `ExamMarkRepository`, `StudentRepository` are Panache repositories — support JPQL and native queries.
- `FeeService.computeFeeSummary` is a separate method with its own transaction; refactoring it into a JOIN query requires care.
- Pagination must be backward-compatible or introduced on new endpoints (existing callers may not pass page/size).
- Java 17 — records are available for lightweight projection DTOs.

## Approaches Considered

### Option A — JPQL aggregation queries + Panache pagination
Replace `listAll()` in `computeAttendanceAnalytics` with a JPQL GROUP BY query:
```sql
SELECT a.classRoom.id, a.classRoom.className, a.classRoom.section,
       COUNT(a), SUM(CASE WHEN a.present = true THEN 1 ELSE 0 END)
FROM Attendance a GROUP BY a.classRoom.id, a.classRoom.className, a.classRoom.section
```
Replace the N+1 in `computeFeeAnalytics` with a single JOIN aggregation over `FeePayment` and `FeeStructure`. For `listByClass`, add `Page` parameter to the Panache query.

Pros: Single query per analytics call. No heap explosion. Standard Panache/JPQL.
Cons: JPQL aggregation requires projection into a constructor expression or a `@NamedNativeQuery`; slightly more complex than `listAll()`.

### Option B — Native SQL with PostgreSQL aggregation
Use `@NamedNativeQuery` or `entityManager.createNativeQuery(...)` for maximum SQL expressiveness (e.g., `FILTER (WHERE present = true)` aggregate syntax).

Pros: Most performant; full PostgreSQL feature set.
Cons: Loses portability (minor — PostgreSQL is the only DB used). Harder to maintain than JPQL.

### Option C — Materialized views (read model)
Create PostgreSQL materialized views for attendance and fee analytics, refreshed on a schedule or on write. Analytics endpoints query the view.

Pros: Near-instant reads; zero runtime aggregation cost.
Cons: Adds schema complexity; stale data between refreshes; overkill for monolith stabilisation. Better fit for the Reporting Service in Phase 4.

## Decision
**Option A** for immediate fix (JPQL aggregation + Panache pagination). Option C is the right long-term architecture for the Reporting Service microservice and should be noted in the Phase 4 design.

Specific changes:
- `computeAttendanceAnalytics`: replace `listAll()` with a JPQL GROUP BY projection.
- `computeFeeAnalytics`: replace the per-student loop with a single `SELECT s.classRoom, SUM(fp.amount), fs.totalFee FROM Student s LEFT JOIN FeePayment fp ... GROUP BY s.classRoom` query.
- `listByClass` endpoint: add `@QueryParam("page") @DefaultValue("0") int page` and `@QueryParam("size") @DefaultValue("20") int size`; use `PanacheQuery.page(Page.of(page, size)).list()`.

## Open Questions
- Should analytics endpoints accept date-range filters (`?from=&to=`) to scope the aggregation? (Item #22 mentions academic-year scoping.)
- What is the maximum acceptable page size for `listByClass`? Should it be capped server-side (e.g., max 100)?
- For `computeFeeAnalytics`, `feeService.computeFeeSummary` may apply override logic — does the SQL aggregation need to replicate that logic, or can overrides be joined in?

## Next Step
1. Add a JPQL projection query to `AttendanceRepository`: `findAttendanceSummaryByClass()` returning a list of `Object[]` or a record `AttendanceClassSummary(classroomId, className, section, total, presentCount)`.
2. Rewrite `computeAttendanceAnalytics()` to call this query instead of `listAll()`.
3. Add a JOIN aggregation query to `FeeService` or a new `FeeAnalyticsRepository` for the fee totals per class.
4. Rewrite `computeFeeAnalytics()` to use the aggregation query.
5. Add `page`/`size` params to the `listByClass` resource method and underlying repository query.


---

# Brainstorm: Observability Stack — Micrometer+Prometheus, OpenTelemetry, SmallRye Health, Correlation IDs
Date: 2026-05-14

## Problem
The application has zero observability in production:
- No metrics — cannot tell if payment success rate is dropping or SMTP is failing.
- No distributed tracing — cannot follow a request across async CDI events or future service boundaries.
- No health endpoints — Kubernetes/load balancers cannot determine if the app is ready to serve traffic.
- No correlation IDs — log lines from the same request cannot be grouped; debugging requires guesswork.
- `quarkus-logging-json` is present but the `GenericExceptionMapper` TODO means errors are never logged.

## Constraints
- Quarkus 3.10 BOM manages versions for `quarkus-micrometer-registry-prometheus`, `quarkus-opentelemetry`, and `quarkus-smallrye-health` — no explicit version needed.
- None of these three extensions are currently in `pom.xml`.
- The Prometheus scrape endpoint (`/q/metrics`) must be restricted to internal network in production (not a code change — infra/nginx config).
- Correlation ID filter must be a JAX-RS `ContainerRequestFilter` (works with `quarkus-resteasy-reactive`).
- MDC keys must match the JSON log field names expected by the log aggregation tool (CloudWatch / Loki).
- OpenTelemetry exporter endpoint is environment-specific — must be in `application.properties` with a `%prod` profile override.

## Existing Patterns That Apply
- `quarkus-logging-json` already on classpath — structured logs to stdout, MDC fields are automatically included in JSON output.
- JAX-RS `@Provider` + `ContainerRequestFilter` / `ContainerResponseFilter` — same pattern as `GenericExceptionMapper`.
- Quarkus auto-instruments Hibernate, REST endpoints, and `quarkus-rest-client-reactive` for OpenTelemetry spans with zero code changes once the extension is added.
- SmallRye Health `HealthCheck` interface — implement `call()` returning `HealthCheckResponse`.

## Approaches Considered

### Option A — Full stack: all four concerns in one phase
Add all three extensions (`micrometer-prometheus`, `opentelemetry`, `smallrye-health`) plus the correlation ID filter in a single PR. Wire custom business metrics (`payment.success.count`, `otp.sent.count`, etc.) at the same time.

Pros: Complete observability in one shot. All concerns are related and the `pom.xml` change is one commit.
Cons: Large PR; harder to review and roll back if one piece causes issues.

### Option B — Incremental: health first, then metrics, then tracing
Ship SmallRye Health first (lowest risk, highest operational value for Kubernetes readiness probes), then Micrometer+Prometheus, then OpenTelemetry.

Pros: Each step is independently deployable and reviewable.
Cons: Three separate PRs; correlation ID filter is needed by all three so it should go first regardless.

### Option C — Use a single APM agent (e.g., AWS X-Ray Java agent or Elastic APM)
Attach a Java agent at runtime that provides metrics, traces, and logs correlation without code changes.

Pros: Zero code changes.
Cons: Vendor lock-in; less control over custom business metrics; doesn't provide health endpoints; conflicts with Quarkus's native compilation path. Rejected for primary approach.

## Decision
**Option A** — add all three extensions together since they are all additive (no behaviour change to existing code) and the `pom.xml` diff is small. Implement in this order within the PR:
1. Correlation ID `ContainerRequestFilter` (unblocks item #17 fix and enriches all logs).
2. `quarkus-smallrye-health` + four custom `HealthCheck` implementations.
3. `quarkus-micrometer-registry-prometheus` + custom business metric counters in `FeeService`, `NotificationService`, `AuthService`.
4. `quarkus-opentelemetry` + `application.properties` OTLP config.

Custom metrics to instrument first (highest signal value):
- `payment.initiated.count`, `payment.success.count`, `payment.failure.count` (tag: `gateway=razorpay`)
- `otp.sent.count`, `otp.verified.count`, `otp.failed.count`
- `notification.sent.count`, `notification.failed.count` (tag: `channel=email|sms`)

Health probes to implement:
- `DatabaseHealthCheck` (Readiness) — `SELECT 1`
- `SmtpHealthCheck` (Readiness) — TCP connect to configured SMTP host:port
- `RazorpayHealthCheck` (Readiness) — validate API key via Razorpay SDK
- `DiskSpaceHealthCheck` (Liveness) — `Files.getFileStore(...).getUsableSpace() > 200MB`

## Open Questions
- Should `/q/metrics` require authentication or be restricted by network policy? (Infra concern, but needs a decision before production.)
- What Grafana dashboard template should be used as a starting point for Quarkus + Micrometer? (Grafana dashboard ID 14370 is the standard Quarkus dashboard.)
- For OpenTelemetry, should traces be sent to a self-hosted Jaeger (docker-compose) or AWS X-Ray in production? The OTLP endpoint differs.
- Should the correlation ID (`X-Request-ID`) be propagated to outbound REST client calls automatically? (`quarkus-rest-client-reactive` supports header propagation via `@ClientHeaderParam`.)
- What MDC key name should be used for `requestId` — `requestId`, `traceId`, or align with OTel's `trace_id`? Using OTel's `trace_id` avoids duplication once tracing is active.

## Next Step
1. Add to `pom.xml`:
   - `quarkus-micrometer-registry-prometheus`
   - `quarkus-opentelemetry`
   - `quarkus-smallrye-health`
2. Create `CorrelationIdFilter` (`@Provider`, `@ApplicationScoped`, implements `ContainerRequestFilter` + `ContainerResponseFilter`): reads `X-Request-ID` header (or generates UUID), sets `MDC.put("requestId", id)`, adds header to response.
3. Create `DatabaseHealthCheck`, `SmtpHealthCheck`, `RazorpayHealthCheck`, `DiskSpaceHealthCheck` implementing `HealthCheck`.
4. Inject `MeterRegistry` into `FeeService` and `NotificationService`; add `Counter.increment()` calls at key decision points.
5. Add to `application.properties`:
   ```
   quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_ENDPOINT:http://localhost:4317}
   quarkus.otel.resource.attributes=service.name=chatrah-backend
   %prod.quarkus.log.level=INFO
   %dev.quarkus.log.level=DEBUG
   ```
6. Fix `GenericExceptionMapper` TODO (item #17) to use the MDC `requestId` now available from step 2.
