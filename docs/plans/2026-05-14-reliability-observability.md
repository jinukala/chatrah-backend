# Reliability & Observability — Implementation Plans
Status: DRAFT
Generated: 2026-05-15

---

## Item 1 — Async Email Dispatch (CDI @ObservesAsync)

### Summary
Decouple `mailer.send(...)` from `@Transactional` methods in `NotificationService`. Persist the `Notification` row (status=PENDING) inside the business transaction, then fire a CDI async event. A separate `@ApplicationScoped` listener picks it up on a worker thread, sends the email, and updates the status in its own transaction. Eliminates SMTP I/O blocking DB connections and prevents SMTP failures from rolling back business transactions.

### Files to Change
- `src/main/java/com/chatrah/school/service/NotificationService.java` — remove `mailer.send(...)` calls; inject `Event<NotificationEvent>`; fire async event after `notificationRepository.persist(notification)`

### New Files
- `src/main/java/com/chatrah/school/event/NotificationEvent.java` — record carrying `notificationId`, `recipientEmail`, `subject`, `body`
- `src/main/java/com/chatrah/school/service/NotificationDispatcher.java` — `@ApplicationScoped` bean with `@ObservesAsync` observer; handles SMTP send and status update

### Method Signatures
```java
// NotificationEvent.java
public record NotificationEvent(Long notificationId, String recipientEmail, String subject, String body) {}

// NotificationService.java  (changed)
@Inject Event<NotificationEvent> notificationEvent;
// replaces mailer.send(...) in each @Transactional method:
notificationEvent.fireAsync(new NotificationEvent(notification.getId(), recipientEmail, title, body));

// NotificationDispatcher.java
@ApplicationScoped
public class NotificationDispatcher {
    void onNotification(@ObservesAsync NotificationEvent event);  // calls mailer.send + updates status
}
```

### Implementation Steps
1. Create `NotificationEvent` record with four fields: `notificationId`, `recipientEmail`, `subject`, `body`.
2. In `NotificationService`, inject `Event<NotificationEvent>` and remove all four `mailer.send(...)` blocks. After `notificationRepository.persist(notification)`, call `notificationEvent.fireAsync(...)`.
3. Create `NotificationDispatcher` (`@ApplicationScoped`). Inject `Mailer`, `NotificationRepository`. In `onNotification(...)`, call `mailer.send(...)`, then open a new `@Transactional` context to update `notification.status` to SENT/FAILED and set `sentAt`.
4. The `@ObservesAsync` method runs on Quarkus's managed executor — no custom `ExecutorService` needed.
5. Remove `@Inject Mailer` from `NotificationService` (no longer needed there).

### Edge Cases
- `notification.getId()` is null if `persist()` hasn't flushed yet — call `notificationRepository.flush()` before firing the event, or use `@Transactional` with `REQUIRED` (default) which flushes on commit; fire the event after the flush point.
- If the JVM crashes between fire and send, the `Notification` row stays PENDING — a future outbox scheduler (Phase 3) will pick these up.
- `recipientEmail` blank/null: guard in `NotificationDispatcher` before calling `mailer.send(...)` to avoid a no-op SMTP call.
- Bulk event notifications (`sendEventNotificationToStudents`) fire N async events in a loop — acceptable for now; cap at 500 students per call.

### Test Cases
- `@QuarkusTest` with `@InjectMock Mailer`: verify `mailer.send(...)` is NOT called synchronously inside the transactional method.
- Verify `Notification.status == PENDING` immediately after the transactional method returns.
- Verify `Notification.status == SENT` after the async observer completes (use `Awaitility`).
- Simulate `mailer.send(...)` throwing `MailException`; verify `Notification.status == FAILED` and `errorMessage` is set.

---

## Item 2 — RazorpayClient @ApplicationScoped Singleton

### Summary
Replace per-request `new RazorpayClient(keyId, keySecret)` instantiation in `FeeService` with a CDI-managed singleton produced by a dedicated `RazorpayClientProducer`. Credentials are injected via `@ConfigProperty` mapped to environment variables. The singleton is shared across all concurrent requests (thread-safe per Razorpay SDK docs).

### Files to Change
- `src/main/java/com/chatrah/school/service/FeeService.java` — remove `new RazorpayClient(...)` calls; add `@Inject RazorpayClient razorpayClient`
- `src/main/resources/application.properties` — add `razorpay.key.id` and `razorpay.key.secret` mapped to env vars

### New Files
- `src/main/java/com/chatrah/school/config/RazorpayClientProducer.java` — `@ApplicationScoped` producer bean

### Method Signatures
```java
// RazorpayClientProducer.java
@ApplicationScoped
public class RazorpayClientProducer {

    @ConfigProperty(name = "razorpay.key.id")
    String keyId;

    @ConfigProperty(name = "razorpay.key.secret")
    String keySecret;

    @Produces
    @ApplicationScoped
    public RazorpayClient razorpayClient() throws RazorpayException { ... }
}
```

```properties
# application.properties additions
razorpay.key.id=${RAZORPAY_KEY_ID}
razorpay.key.secret=${RAZORPAY_KEY_SECRET}
```

### Implementation Steps
1. Create `RazorpayClientProducer` in `com.chatrah.school.config`. Inject `keyId` and `keySecret` via `@ConfigProperty`. Annotate the producer method `@Produces @ApplicationScoped`. Wrap `new RazorpayClient(keyId, keySecret)` — constructor throws checked `RazorpayException`, so declare it or wrap in `RuntimeException`.
2. Add `razorpay.key.id=${RAZORPAY_KEY_ID}` and `razorpay.key.secret=${RAZORPAY_KEY_SECRET}` to `application.properties`.
3. In `FeeService`, remove all `new RazorpayClient(...)` instantiations and add `@Inject RazorpayClient razorpayClient`.
4. Check if `RazorpayClient 1.2.0` exposes a `close()` method; if so, add `@PreDestroy void destroy()` to the producer.

### Edge Cases
- Missing env vars at startup: Quarkus will fail fast with a `DeploymentException` — acceptable; add `%dev` fallback values in `application.properties` for local development (use test credentials).
- `RazorpayException` from constructor: wrap as `RuntimeException` so CDI producer failure is reported clearly at startup rather than at first injection point.
- Test environments: use `@InjectMock` or `@QuarkusTestProfile` to override the producer with a mock `RazorpayClient`.

### Test Cases
- `@QuarkusTest`: verify `RazorpayClient` is injected (non-null) and is the same instance across two injection points (singleton check).
- Verify `FeeService` no longer instantiates `RazorpayClient` directly (static analysis / grep check in CI).
- With missing config property: verify startup fails with a clear message (negative test using `@QuarkusTestProfile` that omits the property).

---

## Item 3 — Fault Tolerance (@Retry + @CircuitBreaker on SMTP and Razorpay)

### Summary
Add `quarkus-smallrye-fault-tolerance` to `pom.xml`. Apply `@Retry` + `@CircuitBreaker` + `@Timeout` on the SMTP dispatch method in `NotificationDispatcher` (Item 1) and on a new `RazorpayGateway` wrapper bean that encapsulates all Razorpay SDK calls. Add `@Fallback` handlers that update status and log failures without propagating exceptions to callers.

### Files to Change
- `pom.xml` — add `quarkus-smallrye-fault-tolerance` dependency
- `src/main/java/com/chatrah/school/service/NotificationDispatcher.java` — add `@Retry`, `@CircuitBreaker`, `@Timeout`, `@Fallback` on `onNotification(...)`
- `src/main/java/com/chatrah/school/service/FeeService.java` — replace direct `razorpayClient.*` calls with `razorpayGateway.*` calls

### New Files
- `src/main/java/com/chatrah/school/gateway/RazorpayGateway.java` — `@ApplicationScoped` wrapper with fault-tolerance annotations on each Razorpay operation

### Method Signatures
```java
// pom.xml addition
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
</dependency>

// NotificationDispatcher.java
@Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS,
       retryOn = Exception.class, abortOn = AddressException.class)
@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5,
                delay = 30, delayUnit = ChronoUnit.SECONDS)
@Timeout(value = 10, unit = ChronoUnit.SECONDS)
@Fallback(fallbackMethod = "onNotificationFallback")
void onNotification(@ObservesAsync NotificationEvent event);

void onNotificationFallback(NotificationEvent event);  // marks Notification FAILED

// RazorpayGateway.java
@ApplicationScoped
public class RazorpayGateway {

    @Retry(maxRetries = 2, delay = 1, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5,
                    delay = 60, delayUnit = ChronoUnit.SECONDS)
    @Timeout(value = 15, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "createOrderFallback")
    public Order createOrder(JSONObject options) throws RazorpayException { ... }

    public Order createOrderFallback(JSONObject options) { ... }  // throws PaymentGatewayException
}
```

### Implementation Steps
1. Add `quarkus-smallrye-fault-tolerance` to `pom.xml` (no version — managed by Quarkus BOM).
2. Create `RazorpayGateway` (`@ApplicationScoped`). Inject `RazorpayClient`. Expose one method per Razorpay operation currently called in `FeeService` (e.g., `createOrder`, `fetchPayment`, `verifySignature`). Annotate each with `@Retry` + `@CircuitBreaker` + `@Timeout`. Fallback methods throw a domain `PaymentGatewayException` with a user-friendly message.
3. In `NotificationDispatcher.onNotification(...)`, add `@Retry(maxRetries=3, abortOn=AddressException.class)` + `@CircuitBreaker` + `@Timeout(10s)`. Add `onNotificationFallback(NotificationEvent)` that updates `Notification.status = FAILED` in a new transaction.
4. Note: `@ObservesAsync` methods cannot directly carry fault-tolerance annotations in all CDI implementations — if annotation processing fails, extract the SMTP call into a separate `@ApplicationScoped` method and call it from the observer.
5. Update `FeeService` to inject `RazorpayGateway` instead of calling `razorpayClient` directly.

### Edge Cases
- `@Retry` on `@ObservesAsync`: SmallRye FT annotations require the method to be called through a CDI proxy. `@ObservesAsync` observers are called directly by the CDI container — extract the annotated logic into a separate bean method to ensure the proxy intercepts it.
- `@CircuitBreaker` is per-bean-instance; with `@ApplicationScoped` this is effectively global — correct behaviour.
- `abortOn = AddressException.class` prevents retrying invalid email addresses (would always fail).
- Razorpay signature verification (`verifySignature`) is a local HMAC computation — no network call, no fault tolerance needed.
- `@Timeout` on Razorpay calls: the SDK uses blocking HTTP; `@Timeout` will interrupt the thread — verify the SDK handles `InterruptedException` cleanly.

### Test Cases
- Mock SMTP to throw on first 2 calls, succeed on 3rd: verify `Notification.status == SENT` (retry worked).
- Mock SMTP to always throw: verify `Notification.status == FAILED` after 3 retries + fallback.
- Trigger 5 consecutive Razorpay failures: verify circuit opens and subsequent calls return fallback immediately without hitting the SDK.
- Verify `@Timeout` fires after 10s on a stubbed slow SMTP call.

---

## Item 4 — GenericExceptionMapper: Structured Error Logging + ErrorResponseDTO

### Summary
Replace the `// TODO: log exception stack trace` in `GenericExceptionMapper.toResponse(Throwable)` with `log.error(...)` using JBoss Logger. Enrich `ErrorResponseDTO` with `timestamp` (ISO-8601) and `requestId` (read from MDC, set by `CorrelationIdFilter` from Item 6). Log at WARN for 4xx `WebApplicationException`, ERROR for everything else.

### Files to Change
- `src/main/java/com/chatrah/school/exception/GenericExceptionMapper.java` — add logger field, replace TODO, populate `timestamp` and `requestId`
- `src/main/java/com/chatrah/school/dto/ErrorResponseDTO.java` — add `timestamp` (String) and `requestId` (String) fields with getters/setters

### New Files
None.

### Method Signatures
```java
// GenericExceptionMapper.java additions
private static final Logger log = Logger.getLogger(GenericExceptionMapper.class);

@Override
public Response toResponse(Throwable exception) {
    // determine level: WARN for 4xx WebApplicationException, ERROR otherwise
    // log.error("Unhandled exception [requestId={}, path={}]", requestId, path, exception);
    // populate body.setTimestamp(...) and body.setRequestId(...)
}

// ErrorResponseDTO.java additions
private String timestamp;   // ISO-8601, e.g. "2026-05-15T11:45:38Z"
private String requestId;   // from MDC "requestId", fallback "unknown"
// + getters/setters
```

### Implementation Steps
1. Add `private static final Logger log = Logger.getLogger(GenericExceptionMapper.class);` to `GenericExceptionMapper`.
2. Read `requestId` from `org.jboss.logging.MDC.get("requestId")`; default to `"unknown"` if null.
3. Check if `exception instanceof WebApplicationException wae && wae.getResponse().getStatus() < 500`; if so, log at WARN without stack trace. Otherwise log at ERROR with full throwable.
4. Set `body.setTimestamp(Instant.now().toString())` and `body.setRequestId(requestId)`.
5. Add `timestamp` and `requestId` fields + getters/setters to `ErrorResponseDTO`.

### Edge Cases
- `MDC.get("requestId")` returns null before `CorrelationIdFilter` is deployed — fallback to `"unknown"` handles this gracefully.
- `WebApplicationException` with status >= 500 (e.g., `503 Service Unavailable`) should still log at ERROR.
- `uriInfo` can be null in unit tests — existing null-guard already present, keep it.
- Do not include the exception message in the client response body — `"An unexpected error occurred"` is sufficient to avoid leaking internals.

### Test Cases
- Throw a plain `RuntimeException` from a test endpoint: verify response body contains `status=500`, `timestamp` (non-null, valid ISO-8601), `requestId`.
- Verify server log contains the full stack trace at ERROR level.
- Throw a `NotFoundException` (404): verify log is at WARN level, not ERROR.
- With `CorrelationIdFilter` active: verify `requestId` in response body matches `X-Request-ID` header sent in the request.

---

## Item 5 — Analytics N+1 Fix (JPQL GROUP BY + Panache Pagination)

### Summary
Replace `attendanceRepository.listAll()` in `computeAttendanceAnalytics()` with a single JPQL GROUP BY aggregation query. Replace the per-student loop in `computeFeeAnalytics()` with a JOIN aggregation query. Add `page`/`size` query parameters to `AnalyticsResource` list endpoints and apply `Panache.page(Page.of(page, size))` to unbounded queries.

### Files to Change
- `src/main/java/com/chatrah/school/service/AnalyticsService.java` — rewrite `computeAttendanceAnalytics()` and `computeFeeAnalytics()`
- `src/main/java/com/chatrah/school/resource/AnalyticsResource.java` — add `page`/`size` params to applicable endpoints
- `src/main/java/com/chatrah/school/repository/AttendanceRepository.java` — add `findAttendanceSummaryByClass()` JPQL query method

### New Files
- `src/main/java/com/chatrah/school/dto/AttendanceClassSummary.java` — record for JPQL constructor expression projection
- `src/main/java/com/chatrah/school/dto/FeeClassSummary.java` — record for fee aggregation projection

### Method Signatures
```java
// AttendanceClassSummary.java
public record AttendanceClassSummary(Long classRoomId, String className, String section,
                                     long totalRecords, long presentCount) {}

// FeeClassSummary.java
public record FeeClassSummary(Long classRoomId, String className, String section,
                               long totalExpected, long totalCollected) {}

// AttendanceRepository.java
public List<AttendanceClassSummary> findAttendanceSummaryByClass() {
    // JPQL: SELECT new com.chatrah.school.dto.AttendanceClassSummary(
    //         a.classRoom.id, a.classRoom.className, a.classRoom.section,
    //         COUNT(a), SUM(CASE WHEN a.present = true THEN 1L ELSE 0L END))
    //       FROM Attendance a GROUP BY a.classRoom.id, a.classRoom.className, a.classRoom.section
}

// AnalyticsService.java
public AttendanceAnalyticsDTO computeAttendanceAnalytics() { ... }  // uses findAttendanceSummaryByClass()
public FeeAnalyticsDTO computeFeeAnalytics() { ... }                // uses fee aggregation query

// AnalyticsResource.java
@GET @Path("/attendance")
public AttendanceAnalyticsDTO getAttendanceAnalytics(
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("size") @DefaultValue("20") int size) { ... }
```

### Implementation Steps
1. Add `findAttendanceSummaryByClass()` to `AttendanceRepository` using `getEntityManager().createQuery(jpql, AttendanceClassSummary.class).getResultList()`. The JPQL uses a constructor expression with the record's canonical constructor.
2. Rewrite `computeAttendanceAnalytics()` to call `findAttendanceSummaryByClass()` and map results to `AttendanceAnalyticsDTO` — no `listAll()`, no in-memory grouping.
3. For `computeFeeAnalytics()`, add a JPQL query joining `Student`, `FeePayment`, and `FeeStructure` grouped by `classRoom`. If `feeService.computeFeeSummary` applies override logic that cannot be expressed in SQL, extract that logic into a separate method and apply it post-aggregation on the smaller result set (one row per class, not per student).
4. Add `page`/`size` params to `AnalyticsResource.getAttendanceAnalytics()` and pass them to the service. Cap `size` at 100 server-side: `size = Math.min(size, 100)`.
5. For `computeExamAnalytics`, the current `examMarkRepository.find("exam", exam).list()` is scoped to one exam — acceptable; no change needed unless exam mark counts are very large.

### Edge Cases
- Empty attendance table: `findAttendanceSummaryByClass()` returns empty list — `computeAttendanceAnalytics()` already handles this; keep the early-return guard.
- `CASE WHEN` in JPQL: supported by Hibernate 6 (Quarkus 3.10 uses Hibernate 6). Use `CASE WHEN a.present = true THEN 1 ELSE 0 END` — returns `Long` from `SUM`.
- Fee override logic: if `feeService.computeFeeSummary` applies per-student overrides, the SQL aggregation will differ. Document this discrepancy and add a comment; resolve fully in Phase 3 when FeeService is extracted.
- Pagination on analytics aggregation endpoints: the `page`/`size` params apply to the class-level result list, not individual records — document this in the API.
- `page < 0` or `size <= 0`: validate and return 400 Bad Request.

### Test Cases
- Insert 3 classrooms × 10 students × 5 attendance records: verify `computeAttendanceAnalytics()` issues exactly 1 query (check via Hibernate statistics or `@TestTransaction` + query count interceptor).
- Verify `schoolAverageAttendance` and per-class percentages match expected values from known test data.
- `GET /api/analytics/attendance?page=0&size=2`: verify response contains at most 2 class entries.
- `GET /api/analytics/attendance?size=200`: verify server caps at 100.
- Empty DB: verify `computeAttendanceAnalytics()` returns a valid DTO with zero values, not a 500.

---

## Item 6 — Observability Stack (Micrometer+Prometheus, OpenTelemetry, SmallRye Health, CorrelationIdFilter)

### Summary
Add three Quarkus extensions to `pom.xml`: `quarkus-micrometer-registry-prometheus`, `quarkus-opentelemetry`, `quarkus-smallrye-health`. Implement `CorrelationIdFilter` (reads/generates `X-Request-ID`, sets MDC). Implement four `HealthCheck` beans. Inject `MeterRegistry` into `FeeService` and `NotificationService` for business metric counters. Configure OTLP endpoint in `application.properties`.

### Files to Change
- `pom.xml` — add three extensions
- `src/main/resources/application.properties` — add OTel config, log level profiles
- `src/main/java/com/chatrah/school/service/FeeService.java` — inject `MeterRegistry`, add payment counters
- `src/main/java/com/chatrah/school/service/NotificationService.java` — inject `MeterRegistry`, add notification counters

### New Files
- `src/main/java/com/chatrah/school/filter/CorrelationIdFilter.java` — `@Provider` `ContainerRequestFilter` + `ContainerResponseFilter`
- `src/main/java/com/chatrah/school/health/DatabaseHealthCheck.java` — Readiness probe
- `src/main/java/com/chatrah/school/health/SmtpHealthCheck.java` — Readiness probe
- `src/main/java/com/chatrah/school/health/RazorpayHealthCheck.java` — Readiness probe
- `src/main/java/com/chatrah/school/health/DiskSpaceHealthCheck.java` — Liveness probe

### Method Signatures
```java
// pom.xml additions
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-micrometer-registry-prometheus</artifactId></dependency>
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-opentelemetry</artifactId></dependency>
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-smallrye-health</artifactId></dependency>

// CorrelationIdFilter.java
@Provider @ApplicationScoped
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Override public void filter(ContainerRequestContext req) { ... }         // set MDC + store id
    @Override public void filter(ContainerRequestContext req, ContainerResponseContext res) { ... }  // add header
}

// DatabaseHealthCheck.java
@Readiness @ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    @Override public HealthCheckResponse call() { ... }  // SELECT 1 via EntityManager
}

// SmtpHealthCheck.java
@Readiness @ApplicationScoped
public class SmtpHealthCheck implements HealthCheck {
    @Override public HealthCheckResponse call() { ... }  // TCP connect to SMTP host:port
}

// RazorpayHealthCheck.java
@Readiness @ApplicationScoped
public class RazorpayHealthCheck implements HealthCheck {
    @Override public HealthCheckResponse call() { ... }  // razorpayClient.orders.all() or key validation
}

// DiskSpaceHealthCheck.java
@Liveness @ApplicationScoped
public class DiskSpaceHealthCheck implements HealthCheck {
    @Override public HealthCheckResponse call() { ... }  // Files.getFileStore(".").getUsableSpace() > 200MB
}

// FeeService.java additions
@Inject MeterRegistry registry;
// in payment success path:  registry.counter("payment.success", "gateway", "razorpay").increment();
// in payment failure path:  registry.counter("payment.failure", "gateway", "razorpay").increment();

// NotificationService.java additions (or NotificationDispatcher after Item 1)
@Inject MeterRegistry registry;
// after SENT:   registry.counter("notification.sent", "channel", "email").increment();
// after FAILED: registry.counter("notification.failed", "channel", "email").increment();
```

```properties
# application.properties additions
quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_ENDPOINT:http://localhost:4317}
quarkus.otel.resource.attributes=service.name=chatrah-backend
%prod.quarkus.log.level=INFO
%dev.quarkus.log.level=DEBUG
quarkus.smallrye-health.root-path=/q/health
```

### Implementation Steps
1. Add the three extensions to `pom.xml` (no versions — BOM-managed).
2. Create `CorrelationIdFilter`: in `filter(ContainerRequestContext)`, read `X-Request-ID` header; if absent, generate `UUID.randomUUID().toString()`. Call `MDC.put("requestId", id)`. Store id in request property. In `filter(ContainerRequestContext, ContainerResponseContext)`, add `X-Request-ID` header to response and call `MDC.remove("requestId")`.
3. Create `DatabaseHealthCheck`: inject `EntityManager`; execute `entityManager.createNativeQuery("SELECT 1").getSingleResult()` inside a try/catch; return UP or DOWN with detail message.
4. Create `SmtpHealthCheck`: read `quarkus.mailer.host` and `quarkus.mailer.port` via `@ConfigProperty`; attempt `new Socket(host, port)` with 3s timeout; return UP/DOWN.
5. Create `RazorpayHealthCheck`: inject `RazorpayClient` (from Item 2); call a lightweight read operation (e.g., `razorpayClient.orders.all(new JSONObject().put("count", 1))`); return UP/DOWN. If circuit is open (Item 3), return DOWN immediately.
6. Create `DiskSpaceHealthCheck`: call `Files.getFileStore(Path.of(".")).getUsableSpace()`; return DOWN if below 200 MB threshold.
7. In `FeeService`, inject `MeterRegistry` and add `Counter` increments at payment initiation, success, and failure points.
8. In `NotificationService` (or `NotificationDispatcher` after Item 1), inject `MeterRegistry` and add counters for `notification.sent` and `notification.failed` tagged with `channel=email`.
9. Add OTel and log-level config to `application.properties`.

### Edge Cases
- `CorrelationIdFilter` MDC cleanup: `MDC.remove("requestId")` must be called in the response filter (or a `@ServerRequestFilter` with `@Priority`) to avoid MDC leaking across thread-pool reuse. Use try/finally if possible.
- `SmtpHealthCheck` on startup: if SMTP is unreachable at boot, the readiness probe returns DOWN — Kubernetes will not route traffic until SMTP is reachable. Consider making SMTP health check a liveness probe instead, or add a startup grace period.
- `RazorpayHealthCheck` making a live API call: this counts against Razorpay rate limits. Use a very lightweight call or cache the result for 30s.
- `quarkus-opentelemetry` auto-instruments Hibernate and REST — no code changes needed for basic tracing. Ensure `quarkus.otel.enabled=false` in `%test` profile to avoid test noise.
- Prometheus `/q/metrics` endpoint is unauthenticated by default — restrict via network policy or add `quarkus.management.auth.enabled=true` in production.
- Business counters in `NotificationService` vs `NotificationDispatcher`: after Item 1, the SMTP send moves to `NotificationDispatcher` — place the `notification.sent`/`notification.failed` counters there, not in `NotificationService`.

### Test Cases
- `GET /q/health/ready`: verify 200 with all readiness checks UP (use `@QuarkusTest` with in-memory H2 or Testcontainers PostgreSQL).
- `GET /q/health/live`: verify 200 with `DiskSpaceHealthCheck` UP.
- `GET /q/metrics`: verify `payment_success_total` and `notification_sent_total` counters are present after triggering the respective flows.
- Send request with `X-Request-ID: test-123`: verify response contains `X-Request-ID: test-123` and server log line contains `requestId=test-123`.
- Send request without `X-Request-ID`: verify response contains a generated UUID in `X-Request-ID`.
- Simulate DB down (stop Testcontainer): verify `/q/health/ready` returns 503.
