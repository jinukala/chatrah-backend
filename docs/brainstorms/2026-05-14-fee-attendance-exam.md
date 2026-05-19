# Fee, Attendance & Exam Brainstorms
Generated: 2026-05-14

---

# Brainstorm: Fee Overrides — Wire FeeOverrideService
Date: 2026-05-14

## Problem
`FeeService.computeFeeSummary` calculates `totalFee` from `FeePlan` (base + hostel + transport) but never consults `FeeOverrideRepository`, even though it is injected. The `FeeOverride` entity stores a per-student `totalFee` (the final concession amount) and a `reason`. Students with a concession are billed the full class fee instead of their negotiated amount.

## Constraints
- `FeeOverride` uses `@OneToOne(student_id UNIQUE)` — at most one override per student.
- `FeeOverride.totalFee` is the **complete** replacement fee, not a delta. The override supersedes the FeePlan calculation entirely.
- `computeFeeSummary` is annotated `@CacheResult(cacheName = "fee-summary")` — the cache must be invalidated whenever an override is created or updated.
- Must not break the existing `FeeSummaryDTO` shape (consumers depend on it).
- `FeeOverrideRepository` is already injected in `FeeService` but unused.

## Existing Patterns That Apply
- `FeeOverrideRepository.findByStudent(Student)` — direct lookup, O(1) by unique index.
- `safeInt(Integer)` helper already in `FeeService` for null-safe int coercion.
- `CacheManagementService` exists for manual cache invalidation.
- `@Transactional` + `@CacheResult` pattern already used on `computeFeeSummary`.

## Approaches Considered

### Option A — Override replaces totalFee entirely (full substitution)
After computing `baseClassFee + hostelComponent + transportComponent`, check for a `FeeOverride`. If one exists, replace `totalFee` with `override.getTotalFee()`. Hostel/transport components are absorbed into the override amount (the admin sets the final number).

Pros: Simple, matches the entity design (`FeeOverride.totalFee` = final fee). No ambiguity.
Cons: Admin must manually include hostel/transport in the override amount.

### Option B — Override as a discount delta
Treat `FeeOverride.totalFee` as a discount to subtract from the computed total.

Pros: Admin only specifies the concession amount.
Cons: Contradicts the entity field name `totalFee` and its Javadoc ("Final total fee for this student after concession"). Would require a schema/rename.

### Option C — Override only for base fee; hostel/transport still added on top
Override replaces only `baseClassFee`; hostel and transport components are still appended.

Pros: Granular.
Cons: Adds complexity not implied by the current entity model. Over-engineering for now.

## Decision
**Option A.** The entity design is unambiguous — `FeeOverride.totalFee` is the final amount. Implementation: after computing the FeePlan-based total, call `feeOverrideRepository.findByStudent(student)`; if non-null, replace `totalFee` with `override.getTotalFee()` and set a `concessionReason` field on `FeeSummaryDTO`. Invalidate `fee-summary` cache on override create/update via `@CacheInvalidate`.

## Open Questions
- Should `FeeSummaryDTO` expose `concessionReason` and `isOverridden` flags to the frontend?
- Who is authorised to create/update a `FeeOverride`? Should there be a `POST /fees/overrides` endpoint with ADMIN role guard?
- If a student has both hostel/transport flags AND an override, should hostel/transport still be shown as line items in the receipt even though the total is overridden?

## Next Step
1. Add `isOverridden` + `concessionReason` fields to `FeeSummaryDTO`.
2. In `computeFeeSummary`, after computing `totalFee`, call `feeOverrideRepository.findByStudent(student)` and substitute if present.
3. Add `@CacheInvalidate(cacheName = "fee-summary")` to any future `createOverride` / `updateOverride` service methods.
4. Write a `@QuarkusTest` covering: no override, override present, override + hostel flag.

---

# Brainstorm: Fee Defaulter Report — GET /fees/defaulters
Date: 2026-05-14

## Problem
There is no endpoint to identify students who owe money. Admins currently have no way to query which students have `totalPaid < totalDue` without manually checking each student's fee summary. `computeFeeAnalytics` in `AnalyticsService` has an N+1 pattern over all students and is not exposed as a filterable defaulter list.

## Constraints
- Must be performant — school may have hundreds to thousands of students; a per-student loop is unacceptable.
- Response must be paginated (issue #24 in the enhancement doc: unbounded lists).
- Must account for `FeeOverride` when determining `totalDue` (after the override fix is applied).
- `FeePayment` only counts `status = 'SUCCESS'` payments toward `totalPaid`.
- Should support optional filter by `classId` to narrow results.
- Role-restricted: ADMIN / PRINCIPAL only.

## Existing Patterns That Apply
- `FeePaymentRepository` uses Panache — native queries via `getEntityManager().createNativeQuery()` or JPQL are available.
- `FeePlanRepository.find("classRoom", cr)` pattern for class-scoped queries.
- `StudentRepository` is a `PanacheRepository` — supports `.page()` pagination.
- `FeeSummaryDTO` already models the per-student fee breakdown.

## Approaches Considered

### Option A — In-memory loop (current analytics pattern)
Load all students, call `computeFeeSummary` per student, filter where `due > 0`.

Pros: Reuses existing logic including override handling.
Cons: N+1 queries, loads entire student table, does not paginate at DB level. Explicitly called out as a bug in the enhancement doc.

### Option B — Single aggregation SQL query
Write a JPQL/native query that JOINs `students`, `fee_plan`, `fee_override`, and `fee_payment` to compute `totalDue - totalPaid` per student in one round-trip, filtering `HAVING due > 0`.

```sql
SELECT s.id, s.name, fp.total_fee, COALESCE(fo.total_fee, fp.total_fee + ...) AS total_due,
       COALESCE(SUM(CASE WHEN pay.status='SUCCESS' THEN pay.amount ELSE 0 END), 0) AS total_paid
FROM students s
JOIN fee_plan fp ON fp.class_room_id = s.class_room_id
LEFT JOIN fee_override fo ON fo.student_id = s.id
LEFT JOIN fee_payment pay ON pay.student_id = s.id
GROUP BY s.id, s.name, fp.total_fee, fo.total_fee, ...
HAVING total_due > total_paid
```

Pros: Single query, DB-level pagination, correct.
Cons: Slightly complex SQL; hostel/transport components need to be joined or computed inline.

### Option C — Materialized / scheduled snapshot
Nightly job computes defaulter list and stores it in a `fee_defaulter_snapshot` table; endpoint reads from snapshot.

Pros: Instant reads, no heavy query at request time.
Cons: Stale data (up to 24 h); overkill for current scale; adds a new table and scheduler.

## Decision
**Option B.** Write a native/JPQL aggregation query in a new `FeeDefaulterRepository` method (or directly in `FeeService`). Return a `FeeDefaulterDTO` list with pagination (`page`, `size` query params). Add optional `classId` filter. Expose via `GET /fees/defaulters?classId=&page=0&size=20`.

## Open Questions
- Should the response include students with `due = 0` but `totalPaid = 0` (i.e., fee plan exists but no payment at all)? Likely yes — they are defaulters too.
- How to handle students with no `FeePlan` for their class? Exclude or flag separately?
- Should the endpoint support CSV export (enhancement 3.3)?

## Next Step
1. Create `FeeDefaulterDTO` (studentId, name, className, totalDue, totalPaid, due).
2. Add `findDefaulters(Long classId, int page, int size)` to `FeePaymentRepository` or a new query method in `FeeService`.
3. Add `GET /fees/defaulters` to `FeeResource` with `@RolesAllowed({ADMIN, PRINCIPAL})`.
4. Integration test: seed 3 students (1 fully paid, 1 partial, 1 zero paid), assert only 2 appear.

---

# Brainstorm: Fee Collection Trend — GET /fees/analytics/trend
Date: 2026-05-14

## Problem
There is no time-series view of fee collection. Admins cannot see whether collections are improving month-over-month, identify slow periods, or correlate collection spikes with due dates. `computeFeeAnalytics` returns a flat aggregate with no temporal breakdown.

## Constraints
- Date range is mandatory (`from`, `to` query params) to prevent unbounded aggregation.
- Granularity should be selectable: `week` or `month` (default `month`).
- Only `SUCCESS` payments count.
- Response must be a list of `{ period, totalCollected, paymentCount }` buckets ordered by period ascending.
- Must not load all `FeePayment` rows into memory.

## Existing Patterns That Apply
- `FeePaymentRepository` is a Panache repo — supports JPQL with `DATE_TRUNC` (PostgreSQL) or `FUNCTION('DATE_FORMAT', ...)` for MySQL.
- `FeeAnalyticsDTO` already exists — can be extended or a new `FeeTrendPointDTO` created.
- `@QueryParam` pattern used in other resources (e.g., `AnalyticsResource`).

## Approaches Considered

### Option A — JPQL GROUP BY with date truncation
```jpql
SELECT FUNCTION('DATE_TRUNC', :granularity, p.paidOn), COUNT(p), SUM(p.amount)
FROM FeePayment p
WHERE p.status = 'SUCCESS' AND p.paidOn BETWEEN :from AND :to
GROUP BY FUNCTION('DATE_TRUNC', :granularity, p.paidOn)
ORDER BY 1
```
Pros: Single query, DB does the aggregation, no memory issue.
Cons: `DATE_TRUNC` is PostgreSQL-specific; `FUNCTION()` in JPQL is portable but verbose.

### Option B — Native SQL query
Use a native query with `DATE_TRUNC` (PostgreSQL) directly. Cleaner SQL, easier to read and maintain.

Pros: Full SQL expressiveness, easy to add `classId` filter later.
Cons: Ties implementation to PostgreSQL (acceptable given the tech stack).

### Option C — Stream and aggregate in Java
Load all payments in range, group by `paidOn.truncatedTo(ChronoUnit.MONTHS)` in a stream.

Pros: DB-agnostic.
Cons: Loads potentially thousands of rows into memory — exactly the anti-pattern called out in the enhancement doc.

## Decision
**Option B** (native SQL). Add `findTrend(LocalDateTime from, LocalDateTime to, String granularity)` to `FeePaymentRepository` returning `List<Object[]>`, mapped to a new `FeeTrendPointDTO { String period, long totalCollected, int paymentCount }` in `FeeService`. Expose as `GET /fees/analytics/trend?from=&to=&granularity=month`.

Validate `granularity` is one of `{day, week, month}`; default to `month`. Validate `from` < `to` and range ≤ 366 days to prevent runaway queries.

## Open Questions
- Should the trend be filterable by `classId` or payment `mode` (cash/UPI/online)?
- Should missing periods (months with zero collection) be filled in with zero-value buckets for a complete chart?
- Is the `paidOn` column indexed? If not, a range scan on a large table will be slow — needs a DB index.

## Next Step
1. Create `FeeTrendPointDTO`.
2. Add native query method to `FeePaymentRepository`.
3. Add `getTrend(from, to, granularity)` to `FeeService` with input validation.
4. Add `GET /fees/analytics/trend` to `FeeResource`.
5. Confirm/add index on `fee_payment(paid_on)` in Flyway migration.


---

# Brainstorm: Attendance Future-Date Guard + Student-Class Membership Check
Date: 2026-05-14

## Problem
`AttendanceService.markAttendance` accepts any date including future dates, which allows pre-marking attendance that hasn't happened yet. It also accepts any `studentId` in the request rows without verifying the student actually belongs to the class being marked — a teacher could mark attendance for a student from a different class.

## Constraints
- "Today" must be determined server-side (`LocalDate.now()`) to avoid client clock manipulation.
- The membership check must use the student's `classRoom` FK on the `Student` entity (already present).
- Invalid rows (future date, wrong class) should be rejected with a clear error — not silently skipped.
- The existing loop already skips `null` studentId and missing students; the new checks fit the same pattern.
- Must not break the existing `markAttendance` signature or `AttendanceMarkRequestDTO`.

## Existing Patterns That Apply
- `AttendanceService` already throws `IllegalArgumentException` for missing session — same pattern for date/membership violations.
- `Student.getClassRoom()` returns the student's current `ClassRoom` — direct comparison with `request.getClassId()` is sufficient.
- `ClassRoom` entity has an `id` field; comparison is `student.getClassRoom().getId().equals(cr.getId())`.
- The class is already loaded as `cr` at the top of `markAttendance` before the student loop.

## Approaches Considered

### Option A — Fail-fast on the whole request
Check `date > LocalDate.now()` before entering the student loop and throw immediately. Check class membership per student inside the loop and throw on first mismatch.

Pros: Simple, consistent with existing `IllegalArgumentException` pattern.
Cons: A single bad row aborts the entire batch — teacher must fix and resubmit all rows.

### Option B — Per-row error collection (partial success)
Collect errors per row, persist valid rows, return a response with `successCount` and `errors[]`.

Pros: More user-friendly for bulk submissions.
Cons: Partial persistence is harder to reason about; complicates the `void` return type; over-engineered for the current UI which submits one class at a time.

### Option C — Silently skip invalid rows (current pattern for null studentId)
Log a warning and skip future-date or wrong-class rows.

Pros: No breaking change.
Cons: Silent data loss — teacher gets no feedback that some rows were ignored. Dangerous for attendance integrity.

## Decision
**Option A** for the future-date guard (whole-request check before the loop — one bad date poisons the whole request). **Option A** for membership check too, but log the offending `studentId` and `classId` in the exception message so the caller knows which row failed. This is consistent with the existing `IllegalArgumentException` usage and keeps the method `void`.

Implementation:
```java
// Future-date guard (before loop)
if (date.isAfter(LocalDate.now())) {
    throw new IllegalArgumentException("Attendance date cannot be in the future: " + date);
}

// Inside loop, after student lookup
if (student.getClassRoom() == null || !student.getClassRoom().getId().equals(cr.getId())) {
    throw new IllegalArgumentException(
        "Student " + studentId + " does not belong to class " + request.getClassId());
}
```

## Open Questions
- Should same-day future-session marking be allowed? (e.g., marking AFTERNOON at 8 AM.) Probably yes — session ≠ time-of-day restriction.
- Should the membership check be skipped for ADMIN role (admin correcting historical records)?
- When a student is transferred mid-year, their `classRoom` FK changes — should historical attendance still be valid? (Yes — `Attendance.classRoom` is a snapshot, not a live FK constraint.)

## Next Step
1. Add future-date guard at the top of `markAttendance`, after `date` is resolved.
2. Add membership check inside the student loop, after `student` is loaded.
3. Update `AttendanceResource` to return `400 Bad Request` on `IllegalArgumentException` (verify `WebAppExceptionMapper` covers this).
4. Add unit tests: future date rejected, student from wrong class rejected, valid request succeeds.

---

# Brainstorm: Attendance Below-Threshold Alert (Quarkus Scheduler Nightly Job)
Date: 2026-05-14

## Problem
There is no automated mechanism to detect and notify when a student's attendance drops below a configurable threshold (e.g., 75%). Currently, low attendance is only visible if someone manually checks the `GET /attendance/summary/{studentId}` endpoint. Parents and teachers receive no proactive alert.

## Constraints
- Must use Quarkus Scheduler (`@Scheduled`) — already a dependency in the Quarkus stack.
- Threshold should be configurable via `application.properties` (`@ConfigProperty`), not hardcoded.
- Notification must go through the existing `NotificationService` to reuse email/SMS infrastructure.
- The job must be efficient — cannot call `getStudentSummary` per student (N+1 queries).
- Job should be idempotent: running it twice on the same night must not send duplicate alerts.
- Must not hold a DB transaction open for the entire job duration.

## Existing Patterns That Apply
- `AttendanceRepository.countPresentForStudent` and `countTotalForStudent` exist but are per-student — need a bulk equivalent.
- `NotificationService.sendFeePaymentNotification` shows the notification dispatch pattern.
- `@ApplicationScoped` + `@Scheduled` is the standard Quarkus scheduler pattern.
- `@ConfigProperty(name = "attendance.threshold.percent", defaultValue = "75")` for config injection.

## Approaches Considered

### Option A — Per-student loop with existing repository methods
Load all students, call `countPresent` + `countTotal` per student, filter below threshold, send notifications.

Pros: Reuses existing methods.
Cons: N+1 queries — 500 students = 1000 queries. Unacceptable.

### Option B — Single aggregation query returning all below-threshold students
Add a JPQL/native query to `AttendanceRepository`:
```sql
SELECT a.student_id, COUNT(*) AS total,
       SUM(CASE WHEN a.present = true THEN 1 ELSE 0 END) AS present_count
FROM attendance a
GROUP BY a.student_id
HAVING (SUM(CASE WHEN a.present = true THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) < :threshold
```
Returns a list of `(studentId, total, presentCount)` in one query. Then load student details (name, parentMobile, email) in a second query by ID list.

Pros: Two queries total regardless of student count. Scalable.
Cons: Slightly more complex; requires a new repository method and a projection DTO.

### Option C — Materialized attendance percentage column updated on each mark
Add a `attendancePercent` column to `Student`, updated on every `markAttendance` call. Scheduler just queries `WHERE attendancePercent < :threshold`.

Pros: Scheduler query is trivial.
Cons: Denormalization; `markAttendance` becomes more complex; stale if marks are bulk-edited; schema change needed.

## Decision
**Option B.** Add `findStudentsBelowThreshold(double threshold)` to `AttendanceRepository` using a native aggregation query. Create a `AttendanceAlertScheduler` class annotated `@ApplicationScoped` with a `@Scheduled(cron = "0 0 21 * * ?")` method (9 PM nightly). Inject `@ConfigProperty(name = "attendance.threshold.percent", defaultValue = "75") double threshold`. For idempotency, check a `NotificationLog` or a simple `alert_sent_date` before dispatching — skip students already alerted today.

## Open Questions
- Should the alert be per-term or cumulative (all-time)? Per-term is more meaningful but requires a `termStart` date parameter.
- Who receives the alert — parent (SMS/email), class teacher, or both?
- Should there be a `POST /attendance/alerts/trigger` endpoint for manual on-demand runs (useful for testing)?
- Idempotency mechanism: a `NotificationLog` lookup is cleanest but adds a DB read per student. A simple in-memory date check per JVM restart is simpler but not cluster-safe.

## Next Step
1. Add `findStudentsBelowThreshold(double threshold)` native query to `AttendanceRepository`.
2. Create `AttendanceAlertScheduler` with `@Scheduled` + `@ConfigProperty` threshold.
3. Add idempotency check (query `NotificationLog` for today's alert type before sending).
4. Wire to `NotificationService` for email/SMS dispatch.
5. Add `attendance.threshold.percent=75` to `application.properties`.
6. Test: mock scheduler trigger, assert notifications sent only for below-threshold students.


---

# Brainstorm: Exam Marks Upsert + Marks Validation [0, maxMarks]
Date: 2026-05-14

## Problem
`ExamService.uploadExamMarks` always calls `examMarkRepository.persist(mark)` — a plain INSERT. Re-uploading marks for the same `(exam, student, subject)` creates duplicate rows instead of updating the existing record. Additionally, there is no validation that `marks` falls within `[0, maxMarks]`; negative marks or marks exceeding the maximum are silently persisted. Both issues are explicitly listed as critical bugs in the enhancement doc (items #12 and the marks validation feature in §3.5).

## Constraints
- The unique key for upsert is `(exam_id, student_id, subject)` — no DB unique constraint exists yet; one must be added via Flyway migration.
- `ExamMark.maxMarks` defaults to 100 if not provided in the row DTO.
- Validation must happen before any persistence — reject the entire batch on first invalid row (consistent with the attendance pattern) or collect all errors.
- `ExamMarkRepository` extends `PanacheRepository` — upsert can be done via a find-then-update pattern or a native `INSERT … ON CONFLICT DO UPDATE`.
- Must remain `@Transactional` — all rows in a batch succeed or all fail.

## Existing Patterns That Apply
- `AttendanceService.markAttendance` already does find-then-update (`existing != null ? existing : new Attendance()`) — same pattern applies here.
- `IllegalArgumentException` is the established pattern for validation failures in services.
- `ExamMarkRepository.findByExamIdAndStudentId` exists; a subject filter can be added.
- `safeInt` helper in `FeeService` for null coercion.

## Approaches Considered

### Option A — Find-then-update (application-level upsert)
For each row, call `examMarkRepository.find("exam = ?1 and student = ?2 and subject = ?3", ...)`. If found, update `marks` and `maxMarks`. If not, create new.

Pros: No schema change needed immediately; consistent with the attendance pattern already in the codebase.
Cons: One extra SELECT per row; race condition if two uploads run concurrently (mitigated by the unique constraint).

### Option B — Native `INSERT … ON CONFLICT (exam_id, student_id, subject) DO UPDATE`
Single SQL statement per row; DB handles the upsert atomically.

Pros: Atomic, no race condition, one round-trip per row.
Cons: Requires the unique constraint to exist first (Flyway migration); native SQL ties to PostgreSQL.

### Option C — Delete-then-reinsert for the whole (exam, class, subject) batch
Delete all existing marks for `(examId, classId, subject)` before inserting the new batch.

Pros: Simple bulk replace.
Cons: Loses `createdAt` audit trail; dangerous if partial upload fails mid-delete.

## Decision
**Option A** for immediate implementation (matches existing codebase pattern, no migration dependency). Add the unique constraint via Flyway migration in parallel so Option B can replace it later. Validation: before the loop, validate each row's `marks` is not null and `0 <= marks <= maxMarks`; throw `IllegalArgumentException` with the offending `studentId` and `subject` if invalid.

```java
// Validation (before persist)
int max = row.getMaxMarks() != null ? row.getMaxMarks() : 100;
if (row.getMarks() == null || row.getMarks() < 0 || row.getMarks() > max) {
    throw new IllegalArgumentException(
        "Invalid marks " + row.getMarks() + " for student " + studentId
        + " subject " + subject + " (max=" + max + ")");
}

// Upsert
ExamMark mark = examMarkRepository
    .find("exam = ?1 and student = ?2 and subject = ?3", exam, student, subject)
    .firstResult();
if (mark == null) mark = new ExamMark();
mark.setExam(exam); mark.setStudent(student); mark.setClassRoom(classRoom);
mark.setSubject(subject); mark.setMarks(row.getMarks()); mark.setMaxMarks(max);
examMarkRepository.persist(mark);
```

## Open Questions
- Should validation collect all errors across all rows and return them together, or fail on the first bad row?
- Should `maxMarks` be defined at the exam level (not per-row) to prevent inconsistent max values for the same subject across students?
- Should re-upload of marks trigger a notification to students/parents?

## Next Step
1. Add marks validation loop before the persist loop in `uploadExamMarks`.
2. Replace `new ExamMark()` + `persist` with find-then-update upsert logic.
3. Add `findByExamStudentSubject(Exam, Student, String)` to `ExamMarkRepository`.
4. Write Flyway migration: `ALTER TABLE exam_marks ADD CONSTRAINT uq_exam_student_subject UNIQUE (exam_id, student_id, subject)`.
5. Unit tests: valid upload, duplicate upload (assert update not insert), marks = 0 (valid), marks > maxMarks (rejected), marks < 0 (rejected).

---

# Brainstorm: Class Rank / Topper Report — GET /exams/{id}/rankings
Date: 2026-05-14

## Problem
There is no way to see how students rank against each other within an exam. `ExamService.getStudentResult` returns a single student's result in isolation. Admins and teachers need a ranked list (1st, 2nd, 3rd…) of all students for a given exam, optionally scoped to a class, to identify toppers and generate report cards.

## Constraints
- Ranking is by total marks obtained across all subjects for the exam.
- Ties must be handled: students with equal total marks share the same rank (dense rank or standard rank — decide).
- Response must be paginated for large classes.
- Optional `classId` filter — without it, rankings are school-wide across all classes that sat the exam.
- `ExamMark` has no unique constraint on `(exam_id, student_id, subject)` yet (being fixed in topic 6) — until that's in place, duplicate rows could inflate totals. Rankings depend on the upsert fix.
- Must not load all marks into memory for aggregation.

## Existing Patterns That Apply
- `ExamMarkRepository.findByExamAndClassRoom` exists — returns all marks for an exam+class, but loads them all into memory.
- `getStudentResult` already computes `totalObtained` and `totalMax` per student — the ranking query is the same aggregation across all students.
- `StudentExamResultDTO` already has `totalMarksObtained`, `percentage` — can be extended with `rank`.
- Panache supports native queries for window functions.

## Approaches Considered

### Option A — In-memory sort after loading all results
Load all `ExamMark` rows for the exam, group by student, sum marks, sort descending, assign rank index.

Pros: Simple Java code, no new query.
Cons: Loads all marks into memory; N+1 if student details are fetched separately; no DB-level pagination.

### Option B — Aggregation SQL query with window function
```sql
SELECT s.id, s.name, cr.class_name, cr.section,
       SUM(em.marks) AS total_obtained,
       SUM(em.max_marks) AS total_max,
       RANK() OVER (ORDER BY SUM(em.marks) DESC) AS rank
FROM exam_marks em
JOIN students s ON s.id = em.student_id
JOIN class_room cr ON cr.id = em.class_room_id
WHERE em.exam_id = :examId
  AND (:classId IS NULL OR em.class_room_id = :classId)
GROUP BY s.id, s.name, cr.class_name, cr.section
ORDER BY rank
```

Pros: Single query, DB computes rank with `RANK()` window function, supports pagination via `LIMIT/OFFSET`, correct tie handling.
Cons: Native SQL (PostgreSQL-specific); requires a new projection DTO.

### Option C — Application-level dense rank with sorted stream
Load aggregated totals (one query, no window function), sort in Java, assign rank with tie logic.

Pros: DB-agnostic, avoids window function complexity.
Cons: Still loads all rows into memory; pagination must be done after sorting (can't push to DB).

## Decision
**Option B.** Use a native aggregation query with `RANK() OVER (...)`. Create `ExamRankingDTO { Long studentId, String studentName, String className, String section, int totalObtained, int totalMax, double percentage, int rank }`. Add `findRankings(Long examId, Long classId, int page, int size)` to `ExamMarkRepository` as a native query. Expose as `GET /exams/{id}/rankings?classId=&page=0&size=20`.

Use `RANK()` (not `DENSE_RANK()`) so tied students share a rank and the next rank skips (standard academic convention: two students tied at 1st → both rank 1, next is rank 3).

## Open Questions
- Should rankings be scoped to a class by default (most common use case) or school-wide by default?
- Should the response include the total number of students ranked (for pagination metadata)?
- Should subject-wise ranks also be returned, or only overall total?
- Does the `academicYear` filter from §3.5 need to be applied here? (Exam already has `academicYear` field — filter at exam level, not marks level.)
- Should this endpoint be cached? Rankings change only when marks are uploaded — `@CacheInvalidate` on `uploadExamMarks` would work.

## Next Step
1. Create `ExamRankingDTO`.
2. Add native query `findRankings(examId, classId, page, size)` to `ExamMarkRepository`.
3. Add `getRankings(examId, classId, page, size)` to `ExamService`.
4. Add `GET /exams/{id}/rankings` to `ExamResource` with pagination params.
5. Integration test: 3 students with different totals → assert correct rank order; tie scenario → assert shared rank.
