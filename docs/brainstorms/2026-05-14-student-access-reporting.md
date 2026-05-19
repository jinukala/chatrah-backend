# Brainstorm — Student, Access & Reporting Features
**Date:** 2026-05-14  
**Phase:** 1 — Brainstorm  
**Project:** chatrah-backend

---

## 1. Student Search & Filter — `GET /students?name=&rollNo=&classId=`

### Problem
`StudentResource.GET /api/students` currently requires `classId` and throws `BadRequestException` if it is absent. There is no way to search by student name or roll number, and no way to list all students across classes. `StudentService.listByClass` loads the full class list and maps it in memory — no predicate filtering exists.

### Constraints
- Must not break the existing `?classId=` callers (backward-compatible).
- `name` search should be case-insensitive and support partial match (prefix or contains).
- `rollNo` is an `Integer` on `Student`; it is unique only within a class, not school-wide.
- The `class-students` Quarkus cache is keyed on `classId` only; adding extra params invalidates the current cache strategy.
- Panache `PanacheRepository` is already in use — keep the same pattern.

### Existing Patterns
- `StudentRepository.findByClassRoomId(classId)` — single-param JPQL query.
- `StudentService.listByClass` uses `@CacheResult(cacheName = "class-students")`.
- `Student` entity has `@Column` on `name` and `rollNo` but no `@Index` annotations.

### Options

**Option A — Panache dynamic query with `Parameters`**  
Add `StudentRepository.search(String name, Integer rollNo, Long classId)` using a hand-built JPQL string with optional predicates. Add `@Index` annotations on `name` and `rollNo` in `Student`. Remove the mandatory `classId` guard in the resource; make all three params optional.

**Option B — Panache `PanacheQuery` with `filterBy` map**  
Use Panache's `find("name like ?1 and ...", ...)` with a dynamically assembled where-clause string. Simpler than a full Criteria API but still requires string concatenation.

**Option C — Hibernate Criteria API / Specification pattern**  
Introduce a `StudentSearchCriteria` DTO and a `StudentRepository.search(StudentSearchCriteria)` method using `CriteriaBuilder`. Most flexible for future filters (gender, admissionDate range) but adds boilerplate.

### Decision
**Option A** — dynamic JPQL with optional predicates is the right balance: minimal new code, consistent with existing Panache style, and easy to extend. Cache the result only when `classId` is the sole filter (existing cache key); bypass cache for multi-param searches.

### Open Questions
- Should `name` match be prefix (`LIKE 'x%'`) or contains (`LIKE '%x%'`)? Contains is slower without a full-text index.
- Do we need pagination (`page`/`size`) in the same ticket or as a follow-up?
- Should `rollNo` search be school-wide or always scoped to a `classId`?

### Next Step
1. Add `@Index(name = "idx_student_name", columnList = "name")` and `@Index(name = "idx_student_roll_no", columnList = "rollNo")` to `Student` entity.
2. Add `search` method to `StudentRepository`.
3. Update `StudentService` with a new `search(String name, Integer rollNo, Long classId)` method (no cache for multi-param path).
4. Update `StudentResource.GET` to accept all three optional `@QueryParam`s and route to `search` when any non-classId param is present.

---

## 2. Student Hard-Delete Guard + `StudentAuditLog` Field-Level Change History

### Problem
`StudentService.delete` performs an unconditional hard delete (`studentRepository.delete(s)`). There is no check for dependent records (attendance, fees, exam marks) and no audit trail. Deleting a student silently orphans or cascades data depending on FK constraints, and there is no way to recover or review what changed on a student record over time.

### Constraints
- The existing `delete` endpoint is `DELETE /api/students/{id}` — signature must stay the same.
- Audit log must capture field-level diffs (old value → new value), not just "record updated".
- Must not require a separate audit framework dependency (e.g., Hibernate Envers) unless it is already on the classpath.
- `Student` uses `@PrePersist` / `@PreUpdate` lifecycle hooks — can be extended.

### Existing Patterns
- `StudentService.createOrUpdate` mutates fields directly; no before/after snapshot is taken.
- `StudentService.delete` does a simple null-check then deletes.
- No soft-delete (`deletedAt`, `active`) flag exists on `Student`.

### Options

**Option A — Soft delete flag + manual audit log entity**  
Add `deletedAt LocalDateTime` to `Student`. Change `delete()` to set `deletedAt = now()` instead of removing the row. Add a `StudentAuditLog` entity with columns: `studentId`, `changedBy`, `changedAt`, `fieldName`, `oldValue`, `newValue`. Populate it in `createOrUpdate` by diffing old vs new values before applying changes.

**Option B — Hibernate Envers (`@Audited`)**  
Annotate `Student` with `@Audited`. Envers auto-generates a `students_aud` revision table. Hard-delete guard still needs manual implementation. Requires adding `quarkus-hibernate-envers` extension.

**Option C — Soft delete + DB trigger for audit**  
Use a database trigger on `UPDATE` to write to an audit table. Keeps Java code clean but moves logic to the DB layer, making it harder to test and environment-dependent.

### Decision
**Option A** — soft delete + manual `StudentAuditLog` entity. No new dependencies, full control over what is logged, consistent with the project's existing plain-JPA style. The diff logic in `createOrUpdate` is straightforward given the small number of fields.

Hard-delete guard: before soft-deleting, check for active fee records, attendance records, and exam marks. If any exist, throw a `409 Conflict` with a descriptive message. Provide a separate `DELETE /api/students/{id}?force=true` for privileged hard-delete (PRINCIPAL / SYS_ADMIN only).

### Open Questions
- Who is `changedBy`? The JWT subject? Need to thread the caller identity into `StudentService` (via `@Context SecurityContext` or a CDI `RequestScoped` identity bean).
- Should soft-deleted students be hidden from all list/search endpoints by default, or filterable via `?includeDeleted=true`?
- What is the retention policy for `StudentAuditLog` rows?

### Next Step
1. Add `deletedAt` field to `Student` entity; add `@Where(clause = "deleted_at IS NULL")` filter or explicit predicate in all queries.
2. Create `StudentAuditLog` entity and repository.
3. Refactor `createOrUpdate` to snapshot old values, apply changes, then persist audit rows.
4. Refactor `delete` to soft-delete with dependency check; add `force` path for hard delete.

---

## 3. Access Request Expiry — `expiresAt` Field + Quarkus Scheduler Revocation Job

### Problem
`AccessRequest` has no expiry concept. Once approved, access is permanent until manually revoked (no revocation endpoint exists either). There is no mechanism to automatically expire stale approvals, which is a security and compliance gap.

### Constraints
- `AccessRequest` entity uses public fields (not private with getters/setters consistently — `id`, `teacherId`, etc. are `public`). New fields should follow the same pattern for consistency, or this is an opportunity to normalise.
- Quarkus Scheduler (`quarkus-scheduler`) must be on the classpath or added.
- The revocation job must be idempotent — safe to run multiple times.
- Expiry should be configurable (e.g., 30 days default) via `application.properties`.

### Existing Patterns
- `AccessRequestService.approveRequest` sets `status = "APPROVED"` and `approvedAt = now()`.
- No scheduler or background job exists in the codebase yet.
- Status values are plain strings: `"PENDING"`, `"APPROVED"`, `"REJECTED"`.

### Options

**Option A — `expiresAt` column + Quarkus `@Scheduled` job**  
Add `expiresAt LocalDateTime` to `AccessRequest`, set during approval (`approvedAt + configurable duration`). Add a `@Scheduled(every = "1h")` job in a new `AccessRequestExpiryJob` bean that queries `status = 'APPROVED' AND expiresAt < now()` and bulk-updates status to `"EXPIRED"`.

**Option B — Compute expiry on read (no scheduler)**  
Do not store `expiresAt`. Instead, compute `isExpired()` in the service layer based on `approvedAt + duration`. No background job needed, but expired records still show as `APPROVED` in the DB, which is misleading for queries and reporting.

**Option C — Separate `TeacherPermission` table with TTL**  
Move the actual permission grant to a `TeacherPermission` table (referenced in the `approveRequest` comment: "Future: actually record permissions in a separate table"). Store `expiresAt` there. The scheduler deletes expired rows. More correct architecturally but larger scope.

### Decision
**Option A** for now — it is the minimal correct solution: persisted expiry, DB-queryable, and the scheduler is a single small class. Add `"EXPIRED"` as a valid status value. Option C is the right long-term direction and should be a follow-up ticket.

### Open Questions
- What is the default expiry duration? 30 days? Should it be per `requestType`?
- Should expiry send a notification to the teacher?
- Should the scheduler run at a fixed rate or cron? (e.g., `@Scheduled(cron = "0 0 * * * ?")` — top of every hour)
- Does `"EXPIRED"` status need to be surfaced in the pending list endpoint, or only in a separate history endpoint?

### Next Step
1. Add `expiresAt LocalDateTime` field to `AccessRequest` entity.
2. Add `@ConfigProperty(name = "access.request.expiry.days", defaultValue = "30")` in `AccessRequestService`.
3. Set `expiresAt = approvedAt + expiryDays` in `approveRequest`.
4. Create `AccessRequestExpiryJob` with `@Scheduled(every = "1h")` that bulk-updates expired approved requests.
5. Add `findExpiredApproved()` query to `AccessRequestRepository`.

---

## 4. Access Request Rejection Metadata — `rejectedBy`, `rejectedAt`, `rejectionReason`

### Problem
`AccessRequestService.rejectRequest` currently reuses the `approvedBy` and `approvedAt` fields to record rejection, which is semantically wrong and lossy — you cannot distinguish who approved vs who rejected from the data alone. There is also no way to record a reason for rejection, which is important for teacher communication and audit.

### Constraints
- `AccessRequest` entity already has `approvedBy` and `approvedAt`; these must remain for approved records.
- `AccessRequestDTO` must be updated to expose the new fields.
- The `POST /{id}/reject` endpoint currently takes only `approverUserId` as a query param — the signature needs to accept a reason without breaking existing callers (reason should be optional).
- `rejectionReason` should have a max length (e.g., 500 chars) to prevent abuse.

### Existing Patterns
- `rejectRequest(Long id, Long approverUserId)` — two-arg method, no body/DTO.
- `AccessRequestResource.reject` passes `approverUserId` as a `@QueryParam`.
- `approveRequest` and `rejectRequest` are symmetric in structure — a refactor opportunity.

### Options

**Option A — Add `rejectedBy`, `rejectedAt`, `rejectionReason` columns; update reject path**  
Add three new nullable columns to `AccessRequest`. Update `rejectRequest` to accept a `RejectionDTO` (or add `rejectionReason` as an optional query param). Stop writing to `approvedBy`/`approvedAt` on rejection.

**Option B — Generic `actionBy` / `actionAt` / `actionNote` columns**  
Rename `approvedBy`→`actionBy`, `approvedAt`→`actionAt`, add `actionNote`. Simpler schema but loses the semantic distinction between approval and rejection actors if both are needed simultaneously (they are not, but it is less clear).

**Option C — Separate `AccessRequestAction` audit table**  
Each approve/reject/expire action is a row in a child table. Full history, but over-engineered for the current need.

### Decision
**Option A** — explicit, semantically clear columns. Stop misusing `approvedBy`/`approvedAt` for rejections. The `reject` endpoint will accept an optional `rejectionReason` query param (or a small request body DTO). Existing `approvedBy`/`approvedAt` fields stay untouched for the approval path.

### Open Questions
- Should `rejectionReason` be mandatory or optional? Making it mandatory improves UX for teachers but adds friction for admins.
- Should the old `approvedBy` / `approvedAt` values written during rejection be cleared (set to null) in a migration, or left as-is?
- Should `rejectedBy` be resolved from JWT (like `approvedBy`) or always passed explicitly?

### Next Step
1. Add `rejectedBy Long`, `rejectedAt LocalDateTime`, `rejectionReason String` to `AccessRequest` entity.
2. Update `rejectRequest` signature to `rejectRequest(Long id, Long rejectorUserId, String reason)`.
3. Stop writing to `approvedBy`/`approvedAt` in the reject path.
4. Update `AccessRequestDTO` and `toDTO` mapping.
5. Update `AccessRequestResource.reject` to accept optional `rejectionReason` query param.
6. Write a Flyway/Liquibase migration to add the three columns and null out any `approvedBy`/`approvedAt` values on existing `REJECTED` rows.

---

## 5. Access Request Duplicate Guard — Unique Pending per `(teacherId, classId)`

### Problem
`AccessRequestService.requestFeeAccess` creates a new `AccessRequest` row every time it is called with no check for an existing pending or approved request for the same `(teacherId, classId, requestType)` combination. A teacher can spam the endpoint and flood the admin's pending list.

### Constraints
- The guard should cover `requestType` as well, since future request types (e.g., `EXAM_ACCESS`) are anticipated.
- A teacher should be able to re-request after a previous request was `REJECTED` or `EXPIRED`.
- The unique constraint should be enforced at both the DB level (for safety) and the service level (for a clean error message).
- `AccessRequestRepository` currently only has `findPending()`.

### Existing Patterns
- `AccessRequest.status` is a plain string; no enum.
- `AccessRequestRepository.findPending()` returns all pending requests — no per-teacher query exists.
- No unique DB constraint on `(teacherId, classId, requestType)` currently.

### Options

**Option A — Service-level check + DB unique partial index**  
In `requestFeeAccess`, query for an existing row with `status IN ('PENDING', 'APPROVED')` for the same `(teacherId, classId, requestType)`. If found, throw `409 Conflict`. Also add a DB-level partial unique index on `(teacher_id, class_id, request_type) WHERE status IN ('PENDING', 'APPROVED')` as a safety net.

**Option B — Service-level check only (no DB constraint)**  
Same service check but no DB constraint. Simpler migration but race-condition-prone under concurrent requests.

**Option C — Upsert / return existing**  
Instead of rejecting, return the existing pending/approved request. Less surprising to the caller but hides the fact that a duplicate was attempted.

### Decision
**Option A** — service check for a clean 409 response plus a DB partial unique index for correctness under concurrency. The partial index (only on active statuses) allows re-requests after rejection/expiry without constraint violations.

### Open Questions
- Should `APPROVED` requests also block a new request, or only `PENDING`? (Probably yes — no point requesting access you already have.)
- What HTTP status and error body should the 409 return? Should it include the existing request's ID so the client can link to it?
- Does the partial unique index syntax work on the target DB (MySQL vs PostgreSQL differ)?

### Next Step
1. Add `AccessRequestRepository.findActiveForTeacherAndClass(Long teacherId, Long classId, String requestType)` — queries `status IN ('PENDING', 'APPROVED')`.
2. In `requestFeeAccess`, call the above; if result is non-null throw `WebApplicationException(409)` with the existing request ID in the body.
3. Add Flyway/Liquibase migration for the partial unique index (syntax varies by DB — document both MySQL and PostgreSQL variants).
4. Add integration test covering the duplicate scenario.

---

## 6. Reporting Exports — CSV/PDF for Attendance, Fee, Exam + Academic-Year Scoping

### Problem
`AnalyticsService` computes attendance, fee, and exam analytics in memory by loading all records (`listAll()`). There is no export capability (CSV or PDF), and no concept of academic-year scoping — all data is aggregated across all time. For a school with multiple years of data, this is both slow and meaningless.

### Constraints
- `AnalyticsService` already has three compute methods; the export layer should reuse them rather than duplicate query logic.
- PDF generation requires a library (e.g., iText, OpenPDF, or Flying Saucer). No PDF library is currently on the classpath — must be added.
- CSV can be generated with no new dependency (manual string building or `opencsv`).
- Academic-year scoping requires a date range or an `academicYear` string (e.g., `"2025-2026"`) to be threaded into all repository queries.
- `AnalyticsService.computeFeeAnalytics` calls `feeService.computeFeeSummary` per student in a loop — N+1 problem that will worsen with year scoping.

### Existing Patterns
- `AnalyticsService` returns typed DTOs (`AttendanceAnalyticsDTO`, `FeeAnalyticsDTO`, `ExamAnalyticsDTO`).
- No `ReportResource` or export endpoint exists yet.
- `AttendanceRepository`, `ExamMarkRepository` etc. use Panache — adding date-range params to queries is straightforward.

### Options

**Option A — New `ReportService` + `ReportResource`; CSV via manual builder, PDF via OpenPDF**  
Add `ReportService` that accepts `(reportType, format, academicYear)`. For CSV, stream rows directly to `StreamingOutput`. For PDF, use OpenPDF (Apache-licensed fork of iText 2). Add `academicYear` param to all three analytics compute methods (passed as a date range derived from the year string).

**Option B — CSV only first, PDF deferred**  
Implement CSV export now (zero new dependencies). PDF is a separate ticket. Simpler scope but leaves PDF as a known gap.

**Option C — Use a reporting framework (JasperReports)**  
JasperReports handles both CSV and PDF with template files. Powerful but heavy dependency and steep learning curve; overkill for three report types.

### Decision
**Option A** — implement both CSV and PDF in one pass to avoid revisiting the same plumbing twice. Use OpenPDF (small, no AGPL concerns). Add `academicYear` scoping to all three analytics methods simultaneously to avoid a second migration of the analytics layer. Fix the N+1 in `computeFeeAnalytics` as part of this work (batch query by class).

### Open Questions
- What is the academic year format? `"2025-2026"` string parsed to `LocalDate` range, or explicit `startDate`/`endDate` params?
- Should exports be synchronous (streamed response) or async (generate → store → download link)? For large schools, async is safer.
- Who is allowed to export? PRINCIPAL and SYS_ADMIN only, or also CLERK?
- Should the PDF include the school logo/name? Where is that config stored?
- For exam exports, is the scope per-exam (already has `examId`) or all exams in an academic year?

### Next Step
1. Add `academicYear` (or `startDate`/`endDate`) parameter to `AttendanceRepository`, `ExamMarkRepository`, and `FeeService` queries.
2. Update the three `AnalyticsService.compute*` methods to accept and pass through the date range.
3. Add `opencsv` and `openpdf` to `pom.xml` (pinned versions).
4. Create `ReportService` with `exportAttendanceCsv`, `exportFeeCsv`, `exportExamCsv`, and corresponding PDF variants.
5. Create `ReportResource` at `GET /api/reports/{type}?format=csv|pdf&academicYear=2025-2026`.
6. Fix N+1 in `computeFeeAnalytics` — replace per-student loop with a batch fee query grouped by class.
