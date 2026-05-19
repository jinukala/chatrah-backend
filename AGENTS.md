# AGENTS.md — Chatrah Backend

## Project Context

Chatrah is a school management backend built with Quarkus 3.10, Java 17, Hibernate ORM Panache, and PostgreSQL. It covers student management, attendance tracking, fee collection (Razorpay integration), exam records, OTP-based authentication (SmallRye JWT), email notifications (Quarkus Mailer), and teacher access-request workflows. The project is in monolith stabilisation phase — critical security and data-integrity fixes must land before any feature work or microservice extraction begins.

## Non-Negotiable Rules

These rules are absolute. Violating any of them is a blocking issue.

### Security
- Never hardcode secrets (DB passwords, API keys, PEM keys, SMTP credentials) in source control. All secrets must come from environment variables or a secrets manager.
- Never commit PEM keys to git. `*.pem` must be in `.gitignore`. Keys are loaded from filesystem paths or env vars at runtime.
- Always verify webhook signatures (HMAC-SHA256) before processing any payment event. Log failures at SEVERE/ERROR.
- Never use a sentinel value (e.g., `-1L`) as a fallback for failed JWT parsing. Throw `NotAuthorizedException` (401) instead.
- Never comment out `@RolesAllowed` annotations. If access control is temporarily disabled for testing, use a test profile, not code comments.
- All `@Transactional` methods must NOT perform I/O (email, SMS, HTTP calls to external services). Move I/O to async observers or separate non-transactional methods.
- TOTP secrets must be stored encrypted at rest (AES-256-GCM), never as plaintext.
- Refresh tokens must be stored as SHA-256 hashes, never as raw values.
- Never log passwords, OTP codes, full tokens, or PII. Reference by key name or first 8 chars only.

### Data Integrity
- Never persist a structurally invalid entity. Validate all required fields (null checks, range checks) before calling `persist()`.
- All unique business keys must have a DB-level UNIQUE constraint enforced via Flyway migration. Application-level checks alone are insufficient (TOCTOU race).
- All schema changes must go through Flyway migrations. `quarkus.hibernate-orm.database.generation=none` in production; `validate` is acceptable.
- Never use `listAll()` in production code paths. All list queries must be bounded (pagination or explicit LIMIT).
- All list endpoints must support pagination (`page`/`size` query params). Server-side cap at 100 items per page.
- Idempotency keys must be stored with UNIQUE constraints. Duplicate webhook/payment processing must return 200 without side effects.
- OTP validation and consumption must happen in a single `@Transactional` method with a pessimistic write lock to prevent race conditions.

### Code Quality
- No `System.out.println` — use `org.jboss.logging.Logger` or `java.util.logging.Logger`.
- No wildcard imports.
- No magic numbers or strings — use constants, enums, or `@ConfigProperty`.
- Methods should be < 30 lines. Classes should be < 300 lines.
- Controllers/Resources delegate immediately to services — no business logic in the resource layer.
- All exceptions must be logged with stack trace before being translated to HTTP responses.
- `@ConfigProperty` values that are required in production must have no default (forces fail-fast on missing config).

### Testing
- Every new feature or bug fix must have at least one test (unit or integration).
- Test names follow `should{Behavior}_when{Condition}` convention.
- Use `@QuarkusTest` with Testcontainers PostgreSQL for integration tests, not H2.

## Patterns

Reusable implementation patterns extracted from plans. Use these as templates.

### Upsert Pattern (Native SQL)
```sql
INSERT INTO {table} (col1, col2, col3)
VALUES (:val1, :val2, :val3)
ON CONFLICT (unique_key_col1, unique_key_col2) DO UPDATE
SET col3 = EXCLUDED.col3, updated_at = NOW()
```
Used for: attendance marking, exam marks upload. Requires a Flyway-managed UNIQUE constraint.

### Idempotency Key Pattern
```java
// Client sends UUID idempotencyKey in request DTO
FeePayment existing = feePaymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
if (existing != null) {
    return computeFeeSummary(studentId); // Return existing result, no side effects
}
// ... proceed with creation, set payment.setIdempotencyKey(key)
```
Backed by `@Column(unique = true)` + Flyway migration. Catch `ConstraintViolationException` as race-condition backstop.

### Async Event Dispatch Pattern (CDI @ObservesAsync)
```java
// Producer (inside @Transactional method, after persist + flush):
notificationEvent.fireAsync(new NotificationEvent(id, email, subject, body));

// Consumer (separate @ApplicationScoped bean):
void onNotification(@ObservesAsync NotificationEvent event) {
    // SMTP/SMS I/O here, in its own @Transactional context for status update
}
```
Used for: email dispatch, SMS dispatch, any I/O that must not block the business transaction.

### Fault Tolerance Pattern (@Retry + @CircuitBreaker)
```java
@Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS,
       retryOn = Exception.class, abortOn = AddressException.class)
@CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.5,
                delay = 30, delayUnit = ChronoUnit.SECONDS)
@Timeout(value = 10, unit = ChronoUnit.SECONDS)
@Fallback(fallbackMethod = "onFailureFallback")
public void sendEmail(NotificationEvent event) { ... }
```
Used for: SMTP calls, Razorpay API calls. Requires `quarkus-smallrye-fault-tolerance`.

### Pagination Pattern (Panache)
```java
// Resource layer:
@QueryParam("page") @DefaultValue("0") int page,
@QueryParam("size") @DefaultValue("20") int size

// Service layer:
int cappedSize = Math.min(size, 100);
return repository.find("classRoom.id", classId)
    .page(Page.of(page, cappedSize))
    .list();
```
All list endpoints must use this pattern. Never return unbounded results.

### Audit Log Pattern (Field-Level Diff)
```java
// Before applying changes, snapshot old values:
String oldName = student.getName();
// Apply changes...
student.setName(newName);
// Persist audit entry:
if (!Objects.equals(oldName, newName)) {
    auditLogRepository.persist(new StudentAuditLog(studentId, "name", oldName, newName, changedBy, now()));
}
```
Used for: Student entity changes. `changedBy` comes from JWT subject.

### Scheduler Pattern (Quarkus @Scheduled)
```java
@ApplicationScoped
public class AttendanceAlertScheduler {
    @ConfigProperty(name = "attendance.threshold.percent", defaultValue = "75")
    double threshold;

    @Scheduled(cron = "{attendance.alert.cron:0 0 21 * * ?}")
    void checkAttendance() {
        // Bulk aggregation query, not per-student loop
        // Idempotency check before sending notifications
    }
}
```
Cron expressions must be configurable via `application.properties`, never hardcoded.

### Startup Validation Pattern
```java
@PostConstruct
public void validateSecrets() {
    if (secret == null || secret.isBlank() || secret.startsWith("your_")) {
        throw new IllegalStateException("Required secret not configured: " + configKey);
    }
}
```
Used for: webhook secrets, API keys, encryption keys. Fail fast at startup, not at first request.

### CDI Producer Pattern (Third-Party Clients)
```java
@ApplicationScoped
public class RazorpayClientProducer {
    @Produces @ApplicationScoped
    public RazorpayClient razorpayClient() throws RazorpayException {
        return new RazorpayClient(keyId, keySecret);
    }
}
```
Used for: any third-party SDK client that should be a singleton (Razorpay, MSG91, etc.).

### Correlation ID Filter Pattern
```java
@Provider @ApplicationScoped
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public void filter(ContainerRequestContext req) {
        String id = req.getHeaderString("X-Request-ID");
        if (id == null) id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
    }
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        res.getHeaders().putSingle("X-Request-ID", MDC.get("requestId"));
        MDC.remove("requestId");
    }
}
```

## Implementation Priority Order

Ordered from critical (must-fix-first) to nice-to-have. Dependencies noted in parentheses.

### Critical — Block Production
1. S2 — Hardcoded secrets / environment separation (`.gitignore`, profile blocks, `.env.example`)
2. S3 — PEM keys removal from source + key rotation (depends on S2 for `.gitignore`)
3. S1 — Razorpay webhook signature verification + startup validation (depends on S2 for env vars)
4. S4 — OTP reset race condition (pessimistic lock, single transaction)
5. S5 — JWT parse failure `-1L` sentinel + re-enable `@RolesAllowed`
6. D6 — Flyway migration setup (baseline V1 + `hibernate.generation=none` in prod)
7. D1 — handlePaymentSuccess null-guard (Bean Validation on DTO)
8. D2 — Webhook idempotency (UNIQUE on `pg_payment_id`, depends on D6)
9. D3 — initiateOnlinePayment idempotency key (depends on D6)
10. D4 — markAttendance race condition (UNIQUE constraint + upsert, depends on D6)
11. D5 — uploadExamMarks duplicate (UNIQUE constraint + upsert, depends on D6)

### High — Required for Stability
12. R1 — Async email dispatch (CDI @ObservesAsync)
13. R2 — RazorpayClient singleton producer
14. R3 — Fault tolerance (@Retry + @CircuitBreaker on SMTP and Razorpay, depends on R1, R2)
15. R4 — GenericExceptionMapper fix (structured logging + ErrorResponseDTO enrichment)
16. R5 — Analytics N+1 fix (JPQL aggregation + pagination)
17. R6 — Observability stack (Micrometer, OpenTelemetry, SmallRye Health, CorrelationIdFilter)

### Medium — Feature Completeness
18. A1 — JWT refresh token + revocation
19. A2 — Password strength validation + email verification
20. A3 — OAuth/OIDC login (Google + Microsoft)
21. A4 — MFA/TOTP second factor
22. A5 — SMS notifications via MSG91
23. F1 — Fee overrides wire-up
24. F2 — Fee defaulter report (`GET /fees/defaulters`)
25. F3 — Fee collection trend (`GET /fees/analytics/trend`)
26. AT1 — Attendance future-date guard + student-class membership check
27. AT2 — Attendance below-threshold alert (scheduler)
28. E1 — Exam marks upsert + marks validation [0, maxMarks]
29. E2 — Class rank / topper report (`GET /exams/{id}/rankings`)
30. ST1 — Student search & filter (`GET /students?name=&rollNo=&classId=`)
31. ST2 — Student hard-delete guard + `StudentAuditLog`
32. AR1 — Access request expiry (`expiresAt` + scheduler)
33. AR2 — Access request rejection metadata
34. AR3 — Access request duplicate guard

### Low — Nice-to-Have / Phase 2+
35. RP1 — Reporting exports (CSV/PDF) + academic-year scoping

## Feature Status Tracker

| # | Feature | Plan Doc | Status | Branch |
|---|---------|----------|--------|--------|
| 1 | S2 — Secrets/Env Separation | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 2 | S3 — PEM Keys Removal | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 3 | S1 — Webhook Signature | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 4 | S4 — OTP Race Condition | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 5 | S5 — JWT -1L Fix | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 6 | D6 — Flyway Setup | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 7 | D1 — Payment Null-Guard | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 8 | D2 — Webhook Idempotency | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 9 | D3 — Payment Idempotency Key | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 10 | D4 — Attendance Upsert | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 11 | D5 — Exam Marks Upsert | `docs/plans/2026-05-14-security-data-integrity.md` | PLANNED | — |
| 12 | R1 — Async Email | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 13 | R2 — Razorpay Singleton | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 14 | R3 — Fault Tolerance | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 15 | R4 — ExceptionMapper Fix | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 16 | R5 — Analytics N+1 Fix | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 17 | R6 — Observability Stack | `docs/plans/2026-05-14-reliability-observability.md` | PLANNED | — |
| 18 | A1 — Refresh Token | `docs/plans/2026-05-14-auth-notifications.md` | PLANNED | — |
| 19 | A2 — Password + Email Verify | `docs/plans/2026-05-14-auth-notifications.md` | PLANNED | — |
| 20 | A3 — OAuth/OIDC | `docs/plans/2026-05-14-auth-notifications.md` | PLANNED | — |
| 21 | A4 — MFA/TOTP | `docs/plans/2026-05-14-auth-notifications.md` | PLANNED | — |
| 22 | A5 — SMS via MSG91 | `docs/plans/2026-05-14-auth-notifications.md` | PLANNED | — |
| 23 | F1 — Fee Overrides | `docs/plans/2026-05-14-fee-attendance-exam-student.md` | PLANNED | — |
| 24 | F2 — Fee Defaulters | `docs/plans/2026-05-14-fee-attendance-exam-student.md` | PLANNED | — |
| 25 | F3 — Fee Trend | `docs/plans/2026-05-14-fee-attendance-exam-student.md` | PLANNED | — |
| 26 | AT1 — Attendance Guards | `docs/brainstorms/2026-05-14-fee-attendance-exam.md` | BRAINSTORMED | — |
| 27 | AT2 — Attendance Alert | `docs/brainstorms/2026-05-14-fee-attendance-exam.md` | BRAINSTORMED | — |
| 28 | E1 — Exam Upsert+Validate | `docs/plans/2026-05-14-fee-attendance-exam-student.md` | PLANNED | — |
| 29 | E2 — Exam Rankings | `docs/brainstorms/2026-05-14-fee-attendance-exam.md` | BRAINSTORMED | — |
| 30 | ST1 — Student Search | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |
| 31 | ST2 — Student Audit | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |
| 32 | AR1 — Access Expiry | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |
| 33 | AR2 — Rejection Metadata | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |
| 34 | AR3 — Duplicate Guard | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |
| 35 | RP1 — Reporting Exports | `docs/brainstorms/2026-05-14-student-access-reporting.md` | BRAINSTORMED | — |

<!-- LEARNINGS:START -->
## Learnings

### Security Learnings
- A placeholder secret value (e.g., `your_webhook_secret_here`) is as dangerous as no secret — it establishes a pattern where real secrets get committed to the same file. Always use `${ENV_VAR}` references with no default in production profiles.
- Static utility classes that swallow exceptions (`catch (Exception e) { return false; }`) create invisible failures. Always log at SEVERE/ERROR before returning a failure sentinel.
- `@RolesAllowed` annotations commented out "for testing" are a critical security gap. Use test profiles or `@TestSecurity` annotations instead.
- The `@QueryParam("approverUserId")` pattern allows approver-ID spoofing. The approver must always come from the authenticated JWT subject, never from the request.
- PEM keys committed to git history remain accessible even after deletion from the working tree. Git history must be scrubbed AND keys must be rotated.

### Data Integrity Learnings
- Application-level duplicate checks (read-then-write) are never race-safe without a DB UNIQUE constraint. Always add the constraint first, then optimize with an application-level pre-check for the common case.
- `@Transactional` boundaries must be carefully designed: splitting validate/consume/act across multiple transactions creates race windows. Merge related operations into a single transaction with appropriate locking.
- Hibernate's `database.generation=update` silently fails on constraint additions, column renames, and index creation. Flyway is non-negotiable for production.
- `persist()` without prior validation produces opaque `PersistenceException` at flush time. Always validate before persist for clear 400 responses.

### Reliability Learnings
- Synchronous I/O (SMTP, HTTP) inside `@Transactional` methods holds DB connections open during network calls. This is the #1 cause of connection pool exhaustion under load.
- `@ObservesAsync` methods are called directly by CDI, not through a proxy. Fault-tolerance annotations (`@Retry`, `@CircuitBreaker`) require the annotated logic to be in a separate CDI bean method called through the proxy.
- `listAll()` is a production incident waiting to happen. Even with 500 students × 200 days × 2 sessions = 200K rows, a single analytics call can OOM the JVM.
- Health checks that make external API calls (Razorpay, SMTP) count against rate limits. Cache health check results for 30 seconds.

### Architecture Learnings
- The OtpToken entity pattern (code, expiresAt, consumed, attempts, purpose) is highly reusable: refresh tokens, MFA pending tokens, and email verification all follow the same structure.
- CDI `@ApplicationScoped` + `@Produces` is the correct pattern for third-party SDK clients (Razorpay, MSG91). Never use `new Client()` per-request.
- Native SQL aggregation queries (GROUP BY, RANK(), DATE_TRUNC) are acceptable and preferred over in-memory aggregation for analytics. PostgreSQL is the only target DB — portability is not a concern.
- Fee override logic: `FeeOverride.totalFee` is a full substitution (final amount), not a delta. The override supersedes the entire FeePlan calculation.
<!-- LEARNINGS:END -->
