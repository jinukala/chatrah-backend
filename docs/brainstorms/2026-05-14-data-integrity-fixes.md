# Data Integrity Fixes — Brainstorm
**Date:** 2026-05-14  
**Phase:** 1 — Brainstorm  
**Scope:** FeeService, AttendanceService, ExamService + related entities

---

## 1. handlePaymentSuccess null-guard — never persist invalid FeePayment row

### Problem
`FeeService.initiateOnlinePayment` builds a `FeePayment` and calls `feePaymentRepository.persist(payment)` without validating that required fields (`amount`, `mode`, `status`) are non-null. A caller passing a partial `OnlineFeePaymentRequestDTO` (e.g. `amount = null`) will produce a row that violates the DB `NOT NULL` constraint at flush time, throwing an opaque `PersistenceException` instead of a clean 400. Worse, if the constraint is ever relaxed, a zero-amount or null-status row silently enters the ledger.

### Constraints
- Must not change the public method signature.
- Must throw a meaningful exception before `persist` is called (fail-fast).
- `FeePayment.status`, `amount`, `mode` are all `@Column(nullable = false)` — DB will catch it eventually, but we want application-level validation.
- `student` is already guarded by the `NotFoundException` above.

### Existing Patterns
- `markAttendance` already does `if (studentId == null) continue;` and `if (request.getSession() == null) throw new IllegalArgumentException(...)` — same pattern should apply here.
- `safeInt()` helper exists but only converts null → 0 for read paths; it should not mask a null `amount` on write.

### Options

**A — Inline null checks before persist**  
Add explicit `if (request.getAmount() == null || request.getAmount() <= 0) throw new IllegalArgumentException(...)` guards directly in `initiateOnlinePayment` before constructing the entity.  
_Pro:_ Minimal change, clear error message.  
_Con:_ Validation logic scattered across service methods.

**B — Bean Validation (`@NotNull`, `@Min`) on DTO + `@Valid` on method param**  
Annotate `OnlineFeePaymentRequestDTO` fields and add `@Valid` to the service method (Quarkus/CDI supports this via `quarkus-hibernate-validator`).  
_Pro:_ Declarative, reusable, consistent with REST layer validation.  
_Con:_ Requires `quarkus-hibernate-validator` dependency (likely already present); validation fires on CDI method boundary, not inside the method.

**C — Guard inside `FeePayment.prePersist` / a factory method**  
Add validation in `@PrePersist` or a static factory `FeePayment.of(student, amount, mode)` that throws if inputs are invalid.  
_Pro:_ Impossible to persist an invalid entity regardless of caller.  
_Con:_ `@PrePersist` throwing is unusual and harder to surface as a 400; factory pattern requires refactoring all callers.

### Decision
**Option B** (Bean Validation on DTO) as the primary guard, **plus Option A** as a belt-and-suspenders check for the `amount > 0` business rule that `@NotNull` alone won't catch. This matches the project's Quarkus stack and keeps service code clean.

### Open Questions
- Is `quarkus-hibernate-validator` already in `pom.xml`? If not, add it.
- Should `amount` be validated as `> 0` or `>= 1` (integer paise vs rupees)?
- Does the REST resource layer already apply `@Valid`? If so, service-level `@Valid` may be redundant.

### Next Step
Phase 2: Add `@NotNull @Min(1)` to `OnlineFeePaymentRequestDTO.amount`, `@NotBlank` to `mode`; add `if (request.getAmount() == null || request.getAmount() <= 0) throw new IllegalArgumentException("Amount must be positive")` in `initiateOnlinePayment` before entity construction.

---

## 2. Webhook idempotency — store razorpay_payment_id with unique constraint, skip if seen

### Problem
When Razorpay fires a `payment.captured` webhook, the endpoint calls `handlePaymentSuccess` (to be implemented). Razorpay guarantees **at-least-once** delivery — the same webhook can arrive multiple times (network retry, Razorpay retry on non-2xx). Without idempotency, each delivery creates a duplicate `FeePayment` row with the same `pgPaymentId`, crediting the student twice and corrupting the fee ledger.

### Constraints
- `FeePayment` already has `pgPaymentId` field (type `String`, no unique constraint yet).
- The fix must be safe under concurrent webhook deliveries (two threads receiving the same event simultaneously).
- Must return HTTP 200 to Razorpay on duplicate (so it stops retrying), but must NOT create a second payment row.
- Quarkus + Hibernate + likely MySQL/PostgreSQL.

### Existing Patterns
- `Attendance` uses an application-level `findByStudentDateSession` check before deciding to insert vs update — same read-then-write pattern, but it is not race-safe without a DB unique constraint.
- `FeePayment.pgPaymentId` field exists but has no `@Column(unique = true)` annotation and no DB constraint.

### Options

**A — Application-level check: `findByPgPaymentId` before persist**  
Query `feePaymentRepository.find("pgPaymentId", paymentId).firstResult()` and return early if found.  
_Pro:_ Simple, no schema change.  
_Con:_ TOCTOU race: two concurrent webhook deliveries both pass the check before either persists → duplicate row.

**B — DB unique constraint on `pg_payment_id` + catch `ConstraintViolationException`**  
Add `@Column(unique = true)` to `FeePayment.pgPaymentId` (backed by a Flyway migration). On duplicate, catch the exception and return 200.  
_Pro:_ Race-safe; DB enforces uniqueness regardless of concurrency.  
_Con:_ Exception-as-control-flow is slightly ugly; requires Flyway migration.

**C — Pessimistic lock / `SELECT FOR UPDATE` on a webhook-events table**  
Maintain a separate `webhook_events(event_id, processed_at)` table; lock the row before processing.  
_Pro:_ Clean separation of concerns; can store full event payload for audit.  
_Con:_ Extra table and complexity; overkill for this use case.

### Decision
**Option B** — DB unique constraint is the only race-safe solution without distributed locking. Catch `ConstraintViolationException` (or Hibernate's `org.hibernate.exception.ConstraintViolationException`) in the webhook handler, log a warning, and return 200. Pair with a Flyway migration (see item 6).

### Open Questions
- What is the webhook handler class/endpoint? It needs to be identified and updated.
- Should `pgPaymentId` be `UNIQUE NOT NULL` or `UNIQUE` (nullable for cash payments)? → `UNIQUE` with nullable is fine; DB allows multiple NULLs in a unique index.
- Should failed/pending payments also store `pgPaymentId`? Yes — Razorpay sends the ID even for failed events.

### Next Step
Phase 2: Add `@Column(unique = true)` to `FeePayment.pgPaymentId`; add Flyway migration `ALTER TABLE fee_payments ADD CONSTRAINT uq_fee_payments_pg_payment_id UNIQUE (pg_payment_id)`; update webhook handler to catch `ConstraintViolationException` and return 200.

---

## 3. initiateOnlinePayment idempotency key — prevent duplicate Razorpay orders

### Problem
`FeeService.initiateOnlinePayment` currently mocks payment (no real Razorpay call), but the planned implementation will call `razorpay.orders.create(...)`. If the client retries the HTTP request (network timeout, double-click), two Razorpay orders are created for the same intent, and the student may pay twice. Even in the current mock, calling the endpoint twice creates two `FeePayment` rows with `status=SUCCESS` for the same student.

### Constraints
- Razorpay Orders API accepts a `receipt` field (max 40 chars) that can serve as a client-supplied idempotency key.
- The idempotency key must be deterministic for a given (student, amount, intent) so retries produce the same order.
- Must not block legitimate second payments (e.g. student paying a second installment).

### Existing Patterns
- Current code generates `payment.setTransactionId("TXN-" + UUID.randomUUID())` — random, so every call is unique; no idempotency.
- `FeePayment.pgOrderId` field exists for storing the Razorpay order ID.
- No existing idempotency key mechanism in the codebase.

### Options

**A — Client-supplied idempotency key in request DTO**  
Add `idempotencyKey` (UUID) to `OnlineFeePaymentRequestDTO`. Store it in `FeePayment`. Before creating an order, check if a `FeePayment` with that key already exists; if so, return the existing order details.  
_Pro:_ Standard REST idempotency pattern; client controls retry safety.  
_Con:_ Requires client cooperation; clients may forget to send the key.

**B — Server-generated key: hash of (studentId + amount + date)**  
Derive a deterministic key: `SHA-256(studentId + "|" + amount + "|" + LocalDate.now())`. Use as Razorpay `receipt`. Check for an existing `FeePayment` with matching `pgOrderId` receipt before creating a new order.  
_Pro:_ No client change needed; prevents same-day duplicate orders for same amount.  
_Con:_ Legitimate same-day same-amount second payment is blocked (edge case for installments).

**C — Razorpay idempotency header (`X-Razorpay-Idempotency-Key`)**  
Pass a UUID idempotency key in the Razorpay API request header. Razorpay deduplicates on their side and returns the same order for the same key within 24 hours.  
_Pro:_ Razorpay handles deduplication; no extra DB query.  
_Con:_ Still need to store the key client-side to reuse it on retry; doesn't prevent duplicate DB rows if our service crashes after Razorpay responds but before we persist.

### Decision
**Option A + C combined**: Client sends an `idempotencyKey` UUID; server checks for existing `FeePayment` with that key (DB lookup) before calling Razorpay, and also passes it as the Razorpay idempotency header. This covers both the DB-duplicate and the Razorpay-duplicate scenarios. Add `idempotency_key` column to `fee_payments` with a unique constraint.

### Open Questions
- Is the Razorpay Java SDK already a dependency? Which version?
- What is the planned `OnlineFeePaymentRequestDTO` shape for the real integration?
- Should the idempotency key expire (e.g. 24h TTL)? Razorpay's header key expires in 24h; our DB key can be permanent.

### Next Step
Phase 2: Add `idempotencyKey` field to `OnlineFeePaymentRequestDTO` and `FeePayment` entity; add `UNIQUE` constraint via Flyway; add pre-check in `initiateOnlinePayment`; pass key as Razorpay `receipt` and idempotency header.

---

## 4. markAttendance race condition — UNIQUE(student_id, date, session) + upsert

### Problem
`AttendanceService.markAttendance` does:
```java
Attendance existing = attendanceRepository.findByStudentDateSession(student, date, session);
Attendance att = existing != null ? existing : new Attendance();
attendanceRepository.persist(att);
```
This is a classic read-then-write (TOCTOU) race. If two requests for the same class/date/session arrive concurrently (e.g. teacher double-submits, or two staff members submit simultaneously), both threads read `existing = null`, both create a `new Attendance()`, and both persist — resulting in duplicate rows for the same `(student_id, date, session)`. The `attendance` table has no unique constraint to prevent this.

### Constraints
- `Attendance` entity has `student`, `date`, `session` — these three form the natural unique key.
- The fix must handle the update case (re-marking attendance) as well as the insert case.
- Quarkus Panache `persist()` calls `EntityManager.persist()` for new entities and is a no-op for managed entities — the upsert must be explicit.
- Must not lose `markedByUserId` or `classRoom` on update.

### Existing Patterns
- `findByStudentDateSession` already exists in `AttendanceRepository` — the lookup is there, just not race-safe.
- `ExamService.uploadExamMarks` has the same pattern (no upsert, no unique constraint) — see item 5.

### Options

**A — DB UNIQUE constraint only, catch `ConstraintViolationException`**  
Add `UNIQUE(student_id, date, session)` to the `attendance` table. On duplicate insert, catch the exception and ignore (or re-fetch and update).  
_Pro:_ DB enforces correctness; simple.  
_Con:_ Exception-as-control-flow for the update case; catching and re-fetching is awkward.

**B — DB UNIQUE constraint + native `INSERT ... ON DUPLICATE KEY UPDATE` (MySQL) or `ON CONFLICT DO UPDATE` (PostgreSQL)**  
Use a native upsert query in `AttendanceRepository`.  
_Pro:_ Atomic, race-safe, no exception handling needed.  
_Con:_ DB-vendor-specific SQL; need to know target DB (MySQL vs PostgreSQL).

**C — DB UNIQUE constraint + application-level pessimistic lock**  
Before the read-then-write, acquire a pessimistic lock on the `(student, date, session)` combination using `SELECT ... FOR UPDATE`.  
_Pro:_ Works with any DB; no vendor-specific SQL.  
_Con:_ Serializes all attendance writes for a student; higher contention; complex to implement with Panache.

### Decision
**Option B** — native upsert is the cleanest solution. Add `UNIQUE(student_id, date, session)` via Flyway migration. Add `AttendanceRepository.upsert(Attendance att)` using `@Query` with native SQL. The service calls `upsert` instead of `persist`. Target DB dialect to be confirmed (see Open Questions).

### Open Questions
- Is the DB MySQL or PostgreSQL? This determines `ON DUPLICATE KEY UPDATE` vs `ON CONFLICT DO UPDATE`.
- Should re-marking attendance (update) overwrite `markedByUserId`? Likely yes — last writer wins.
- Does `findByStudentDateSession` use JPQL or native SQL? Check `AttendanceRepository` implementation.

### Next Step
Phase 2: Add `@UniqueConstraint(columnNames = {"student_id", "date", "session"})` to `Attendance` entity `@Table`; add Flyway migration; implement `AttendanceRepository.upsert()` with native upsert SQL; update `markAttendance` to call `upsert` instead of `persist`.

---

## 5. uploadExamMarks duplicate — UNIQUE(exam_id, student_id, subject) + upsert

### Problem
`ExamService.uploadExamMarks` iterates rows and calls `examMarkRepository.persist(mark)` for every row unconditionally:
```java
ExamMark mark = new ExamMark();
// ... set fields ...
examMarkRepository.persist(mark);
```
There is no check for an existing `ExamMark` with the same `(exam_id, student_id, subject)`. Re-uploading an Excel sheet (e.g. to correct a mark) creates duplicate rows instead of updating the existing one. `getStudentResult` then sums all rows, double-counting marks. The `exam_marks` table has no unique constraint.

### Constraints
- `(exam_id, student_id, subject)` is the natural unique key for an exam mark.
- Re-upload must update (overwrite) `marks` and `maxMarks`, not insert a duplicate.
- `ExamMark.classRoom` is also set per row — on update, it should be overwritten too.
- `createdAt` must not change on update (`updatable = false` is already set).

### Existing Patterns
- Same read-then-write gap as `markAttendance` (item 4), but here there is not even a pre-check — it always inserts.
- `ExamMark` entity has no `findByExamStudentSubject` repository method yet.

### Options

**A — Pre-check + update in service**  
Add `examMarkRepository.findByExamStudentSubject(exam, student, subject)` and update the existing entity if found, else insert.  
_Pro:_ Simple, no native SQL.  
_Con:_ TOCTOU race if two uploads run concurrently (unlikely but possible for bulk uploads).

**B — DB UNIQUE constraint + native upsert**  
Add `UNIQUE(exam_id, student_id, subject)` via Flyway. Implement `ExamMarkRepository.upsert()` with native SQL.  
_Pro:_ Race-safe; consistent with the attendance fix approach.  
_Con:_ Vendor-specific SQL; same DB dialect question as item 4.

**C — Delete-then-insert per subject batch**  
Before inserting rows for a given `(exam_id, subject)`, delete all existing marks for that exam+subject, then insert fresh.  
_Pro:_ Simple logic; no upsert needed.  
_Con:_ Destructive — loses `createdAt` history; risky if upload is partial (partial delete + partial insert on failure).

### Decision
**Option B** — consistent with item 4. Add `UNIQUE(exam_id, student_id, subject)` via Flyway. Add `ExamMarkRepository.upsert()`. Update `uploadExamMarks` to call `upsert`. Option A can be added as a fast-path pre-check to avoid the native query overhead in the non-duplicate case.

### Open Questions
- Should a re-upload of marks trigger a notification to the student/parent?
- Should `maxMarks` be per-exam-subject (same for all students) or per-student? Currently per-row — confirm with product.
- Same DB dialect question as item 4.

### Next Step
Phase 2: Add `@UniqueConstraint(columnNames = {"exam_id", "student_id", "subject"})` to `ExamMark` entity `@Table`; add Flyway migration; add `ExamMarkRepository.findByExamStudentSubject()` and `upsert()`; update `uploadExamMarks` to use upsert.

---

## 6. Hibernate DDL update → Flyway migration

### Problem
The project currently relies on `quarkus.hibernate-orm.database.generation=update` (or `drop-and-create`) to manage schema changes. This is unsafe for production: Hibernate's DDL update does not handle column renames, constraint additions on existing data, or index creation reliably. All five fixes above (items 1–5) require schema changes (new columns, unique constraints). Without Flyway, these changes are applied inconsistently across environments and cannot be rolled back or audited.

### Constraints
- Quarkus supports Flyway via `quarkus-flyway` extension.
- Existing schema must be baselined — a V1 migration must capture the current state so Flyway doesn't try to re-create existing tables.
- `hibernate-orm.database.generation` must be set to `none` (or `validate`) in production; `update` can remain for local dev only.
- Migrations must be idempotent where possible (`CREATE INDEX IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`).

### Existing Patterns
- No `src/main/resources/db/migration/` directory exists yet (to be confirmed).
- Entities use standard JPA annotations — Hibernate can generate a baseline DDL script via `jakarta.persistence.schema-generation.scripts.action=create`.

### Options

**A — Add `quarkus-flyway`, baseline current schema as V1, add V2 for all 5 fixes**  
Generate V1 DDL from Hibernate (`hbm2ddl`), create `V1__baseline.sql`, then `V2__data_integrity_constraints.sql` for all new constraints.  
_Pro:_ Clean history; single migration for all related changes.  
_Con:_ V1 baseline generation requires care to match the live DB exactly.

**B — One migration file per fix (V2–V6)**  
Skip a monolithic V2; each fix gets its own numbered migration.  
_Pro:_ Easier to review, revert individual changes, and understand history.  
_Con:_ More files; all must be applied together for the system to be consistent.

**C — Keep `hibernate.ddl-auto=update` for dev, add Flyway only for prod profile**  
Use Quarkus profiles: `%prod.quarkus.flyway.migrate-at-start=true`, `%dev.quarkus.hibernate-orm.database.generation=update`.  
_Pro:_ Minimal disruption to dev workflow.  
_Con:_ Dev and prod schemas can diverge; masks migration errors until prod deploy.

### Decision
**Option A + C**: Baseline as V1, group all 5 constraint changes into V2 (they are one logical batch). Use Quarkus profiles so `%dev` keeps `update` for convenience but `%prod` uses Flyway exclusively with `database.generation=none`. This is the standard Quarkus + Flyway recommended setup.

### Open Questions
- Is `quarkus-flyway` already in `pom.xml`?
- What is the target DB (MySQL 8 / PostgreSQL 14+)? Affects constraint syntax and upsert SQL in items 4 & 5.
- Is there an existing live DB that needs a V1 baseline, or is this a greenfield deploy?
- Should migrations run automatically on startup (`migrate-at-start=true`) or be triggered manually (CI/CD step)?

### Next Step
Phase 2: Add `quarkus-flyway` to `pom.xml`; generate V1 baseline DDL; create `src/main/resources/db/migration/V1__baseline.sql` and `V2__data_integrity_constraints.sql` containing:
- `ALTER TABLE fee_payments ADD COLUMN IF NOT EXISTS pg_payment_id VARCHAR(255), ADD CONSTRAINT uq_fee_payments_pg_payment_id UNIQUE (pg_payment_id);`
- `ALTER TABLE fee_payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(36), ADD CONSTRAINT uq_fee_payments_idempotency_key UNIQUE (idempotency_key);`
- `ALTER TABLE attendance ADD CONSTRAINT uq_attendance_student_date_session UNIQUE (student_id, date, session);`
- `ALTER TABLE exam_marks ADD CONSTRAINT uq_exam_marks_exam_student_subject UNIQUE (exam_id, student_id, subject);`

Set `%prod.quarkus.hibernate-orm.database.generation=none` and `%prod.quarkus.flyway.migrate-at-start=true`.
