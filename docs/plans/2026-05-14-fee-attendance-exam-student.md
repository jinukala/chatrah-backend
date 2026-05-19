# Implementation Plans — Fee, Attendance, Exam, Student, Access & Reporting
**Date:** 2026-05-14
**Status:** DRAFT
**Phase:** 2 — Plan

---

## Plan 1 — Fee Overrides Wire-Up

### Summary
`FeeService.computeFeeSummary` ignores the already-injected `FeeOverrideRepository`. Wire it in so a student with a `FeeOverride` is billed `override.totalFee` instead of the FeePlan-computed total. Expose `isOverridden` and `concessionReason` on `FeeSummaryDTO`. Invalidate the `fee-summary` cache on override create/update.

### Files to Change
- `src/main/java/com/chatrah/school/service/FeeService.java` — add override lookup in `computeFeeSummary`; add `@CacheInvalidate` to future override mutators
- `src/main/java/com/chatrah/school/dto/FeeSummaryDTO.java` — add `isOverridden: boolean`, `concessionReason: String`

### New Files
- `src/main/java/com/chatrah/school/resource/FeeOverrideResource.java` — `POST /fees/overrides`, `PUT /fees/overrides/{studentId}` (ADMIN only)
- `src/test/java/com/chatrah/school/service/FeeServiceOverrideTest.java`

### Method Signatures
```java
// FeeService.java — no signature change, internal logic change only
@Transactional
@CacheResult(cacheName = "fee-summary")
public FeeSummaryDTO computeFeeSummary(Long studentId)

// New mutator methods (trigger cache invalidation)
@Transactional
@CacheInvalidate(cacheName = "fee-summary")
public FeeSummaryDTO createOrUpdateOverride(Long studentId, FeeOverrideDTO dto)
```

### Implementation Steps
1. In `computeFeeSummary`, after computing `totalFee`, call `feeOverrideRepository.findByStudent(student)`.
2. If override is non-null: replace `totalFee` with `override.getTotalFee()`; set `dto.setIsOverridden(true)`; set `dto.setConcessionReason(override.getReason())`.
3. Add `isOverridden` and `concessionReason` fields + getters/setters to `FeeSummaryDTO`.
4. Create `createOrUpdateOverride` in `FeeService` annotated `@CacheInvalidate(cacheName = "fee-summary")`.
5. Create `FeeOverrideResource` with `POST /fees/overrides` and `PUT /fees/overrides/{studentId}`, both `@RolesAllowed(ADMIN)`.

### Edge Cases
- Student has no `FeePlan` for their class AND has an override → override total is used as-is (no plan needed).
- Override `totalFee` is 0 (full scholarship) → `due` becomes 0; must not go negative.
- Cache key for `fee-summary` is `studentId`; `@CacheInvalidate` must pass the same key.

### Test Cases
- No override → `totalFee` = FeePlan computed total; `isOverridden = false`.
- Override present → `totalFee` = `override.totalFee`; `isOverridden = true`; `concessionReason` populated.
- Override + hostel flag → `totalFee` = override amount (hostel absorbed); `isOverridden = true`.
- Override `totalFee = 0` → `due = 0`, not negative.
- `createOrUpdateOverride` → subsequent `computeFeeSummary` call returns fresh (non-cached) result.

---

## Plan 2 — Fee Defaulter Report `GET /fees/defaulters`

### Summary
Add a paginated endpoint that returns students where `totalDue > totalPaid`, computed via a single aggregation SQL query (not an in-memory loop). Supports optional `classId` filter. Restricted to ADMIN / PRINCIPAL.

### Files to Change
- `src/main/java/com/chatrah/school/repository/FeePaymentRepository.java` — add `findDefaulters` native query
- `src/main/java/com/chatrah/school/resource/FeeResource.java` — add `GET /fees/defaulters`
- `src/main/java/com/chatrah/school/service/FeeService.java` — add `getDefaulters` method

### New Files
- `src/main/java/com/chatrah/school/dto/FeeDefaulterDTO.java`
- `src/test/java/com/chatrah/school/resource/FeeDefaulterResourceTest.java`

### Method Signatures
```java
// FeePaymentRepository.java
public List<Object[]> findDefaulters(Long classId, int page, int size);

// FeeService.java
public List<FeeDefaulterDTO> getDefaulters(Long classId, int page, int size);

// FeeResource.java
@GET
@Path("/defaulters")
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.PRINCIPAL})
public List<FeeDefaulterDTO> getDefaulters(
    @QueryParam("classId") Long classId,
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("size") @DefaultValue("20") int size);
```

### Implementation Steps
1. Create `FeeDefaulterDTO { Long studentId, String studentName, String className, String section, int totalDue, int totalPaid, int due }`.
2. Add native query to `FeePaymentRepository`:
```sql
SELECT s.id, s.name, cr.class_name, cr.section,
       COALESCE(fo.total_fee,
           fp.total_fee
           + CASE WHEN s.is_hosteller = true THEN COALESCE(fp.hostel_fee,0) ELSE 0 END
           + CASE WHEN s.is_transport_user = true THEN COALESCE(fp.transport_fee,0) ELSE 0 END
       ) AS total_due,
       COALESCE(SUM(CASE WHEN pay.status='SUCCESS' THEN pay.amount ELSE 0 END),0) AS total_paid
FROM students s
JOIN class_room cr ON cr.id = s.class_room_id
LEFT JOIN fee_plan fp ON fp.class_room_id = s.class_room_id
LEFT JOIN fee_override fo ON fo.student_id = s.id
LEFT JOIN fee_payment pay ON pay.student_id = s.id
WHERE (:classId IS NULL OR s.class_room_id = :classId)
GROUP BY s.id, s.name, cr.class_name, cr.section, fo.total_fee, fp.total_fee, fp.hostel_fee, fp.transport_fee, s.is_hosteller, s.is_transport_user
HAVING COALESCE(fo.total_fee, fp.total_fee + ...) > COALESCE(SUM(...), 0)
LIMIT :size OFFSET :page * :size
```
3. Map `Object[]` rows to `FeeDefaulterDTO` in `FeeService.getDefaulters`.
4. Add `GET /fees/defaulters` to `FeeResource`.

### Edge Cases
- Student has no `FeePlan` → exclude from results (no fee obligation defined).
- Student has `FeePlan` but zero payments → appears as defaulter with `totalPaid = 0`.
- `classId` not found → return empty list (no 404; the filter simply matches nothing).
- `page` or `size` ≤ 0 → default to 0 and 20 respectively.

### Test Cases
- 3 students: 1 fully paid, 1 partial, 1 zero paid → only 2 appear in defaulters list.
- `classId` filter → only defaulters from that class returned.
- Student with override and partial payment → `totalDue` uses override amount.
- Empty result when all students are fully paid.

---

## Plan 3 — Fee Collection Trend `GET /fees/analytics/trend`

### Summary
Add a time-series endpoint that returns fee collection bucketed by day/week/month within a mandatory date range. Uses a native SQL aggregation query. Validates granularity and range length.

### Files to Change
- `src/main/java/com/chatrah/school/repository/FeePaymentRepository.java` — add `findTrend` native query
- `src/main/java/com/chatrah/school/service/FeeService.java` — add `getTrend` with input validation
- `src/main/java/com/chatrah/school/resource/FeeResource.java` — add `GET /fees/analytics/trend`
### New Files
- `src/main/java/com/chatrah/school/dto/FeeTrendPointDTO.java`
- `src/test/java/com/chatrah/school/service/FeeServiceTrendTest.java`

### Method Signatures
```java
// FeePaymentRepository.java
public List<Object[]> findTrend(LocalDate from, LocalDate to, String granularity);

// FeeService.java
public List<FeeTrendPointDTO> getTrend(LocalDate from, LocalDate to, String granularity);

// FeeResource.java
@GET
@Path("/analytics/trend")
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.PRINCIPAL})
public List<FeeTrendPointDTO> getTrend(
    @QueryParam("from") String from,
    @QueryParam("to") String to,
    @QueryParam("granularity") @DefaultValue("month") String granularity);
```

### Implementation Steps
1. Create `FeeTrendPointDTO { String period, long totalCollected, int paymentCount }`.
2. Add native query to `FeePaymentRepository`:
```sql
SELECT TO_CHAR(DATE_TRUNC(:granularity, paid_on), 'YYYY-MM-DD') AS period,
       SUM(amount) AS total_collected,
       COUNT(*) AS payment_count
FROM fee_payment
WHERE status = 'SUCCESS'
  AND paid_on >= :from AND paid_on <= :to
GROUP BY DATE_TRUNC(:granularity, paid_on)
ORDER BY 1
```
3. In `FeeService.getTrend`:
   - Validate `granularity` ∈ `{day, week, month}`; throw `IllegalArgumentException` otherwise.
   - Validate `from` < `to`; validate range ≤ 366 days.
   - Parse `from`/`to` strings to `LocalDate` in the resource layer.
   - Map `Object[]` to `FeeTrendPointDTO`.
4. Add `GET /fees/analytics/trend` to `FeeResource`; parse date strings; return list.
5. Add Flyway migration to confirm/create index: `CREATE INDEX IF NOT EXISTS idx_fee_payment_paid_on ON fee_payment(paid_on)`.

### Edge Cases
- `from` or `to` missing → `400 Bad Request`.
- `from` >= `to` → `400 Bad Request`.
- Range > 366 days → `400 Bad Request` to prevent runaway queries.
- Invalid `granularity` value → `400 Bad Request`.
- No payments in range → return empty list (not an error).

### Test Cases
- 3 payments in Jan, 2 in Feb → `month` granularity returns 2 buckets with correct sums.
- `day` granularity → each payment day is its own bucket.
- Range with no SUCCESS payments → empty list.
- Range > 366 days → service throws `IllegalArgumentException`.
- Invalid granularity `"quarter"` → `IllegalArgumentException`.
