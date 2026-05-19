---
date: 2026-05-14
feature: compound-synthesis
category: architecture
tags: [compound-workflow, brainstorm, plan, synthesis]
---

# Solution: Compound Synthesis — Full Enhancement Brainstorm & Plan

## Problem Solved

This compound workflow session took the Chatrah backend from a state of undocumented technical debt to a fully catalogued, prioritised, and planned enhancement roadmap. Starting from the `ENHANCEMENT_AND_MICROSERVICES.md` audit document (which identified 30+ issues and a microservices migration path), the session produced:

- **6 brainstorm documents** covering security, data integrity, reliability, auth/notifications, fee/attendance/exam, and student/access/reporting domains
- **4 implementation plan documents** with file-level change lists, method signatures, edge cases, and test cases for all 35 features
- **1 master AGENTS.md** codifying all rules, patterns, and learnings for future agent sessions
- **This synthesis document** capturing cross-cutting patterns and a sprint plan

The monolith is now ready for systematic implementation with clear dependency ordering and no ambiguity about what to build first.

## What Worked

### Brainstorm Process
- **Options-based decision making**: Every brainstorm evaluated 2–3 concrete approaches with explicit pros/cons before selecting one. This prevented premature commitment and documented why alternatives were rejected.
- **Constraint-first framing**: Starting each brainstorm with constraints (existing patterns, stack limitations, backward compatibility) kept solutions grounded in reality rather than ideal-world designs.
- **Cross-referencing existing code**: Identifying existing patterns (e.g., `OtpToken` structure reusable for refresh tokens, `findByStudentDateSession` as a template for upsert) reduced design time and ensured consistency.

### Plan Process
- **File-level change tables**: Listing every file to change with the reason made plans executable without ambiguity.
- **Method signatures before implementation**: Defining the API contract first caught design issues (return types, exception types, transaction boundaries) before any code was written.
- **Edge case tables**: Explicitly mapping edge cases to handling strategies prevented the "happy path only" trap.
- **Test case checklists**: Pre-defining test cases ensured coverage was planned, not an afterthought.

### Compound Workflow Structure
- **Brainstorm → Plan separation**: Keeping ideation separate from implementation planning allowed broader exploration in brainstorms without premature detail, then precise specification in plans.
- **Grouping related features**: Batching security fixes together, data integrity fixes together, etc. revealed shared dependencies (Flyway, env vars) that would have been missed if features were planned in isolation.

## What Needed Adjustment

### Template Adaptations
- **Multiple features per document**: The compound workflow template assumes one feature per brainstorm/plan. For a stabilisation effort with 35 related items, batching by domain (security, data integrity, reliability) was more practical than 35 separate documents.
- **No "Async / Reactive Chain" section needed**: The plan template includes a reactive chain section that doesn't apply to this synchronous Quarkus stack. Replaced with transaction boundary diagrams where relevant.
- **Status tracking at the AGENTS.md level**: The template doesn't prescribe a central status tracker. Adding the Feature Status Tracker table to AGENTS.md provides a single source of truth for progress.

### Process Adaptations
- **Dependency ordering was critical**: The template's "Implementation Steps" are linear within a feature, but cross-feature dependencies (all upserts depend on Flyway, all async dispatch depends on the CDI event pattern) required a global priority ordering that the template doesn't address. Solved by the Implementation Priority Order section in AGENTS.md.
- **Brainstorm "Open Questions" often resolved themselves**: Many open questions in early brainstorms (e.g., "Is the DB MySQL or PostgreSQL?") were answered by reading other parts of the codebase during later brainstorms. A second pass to close open questions would be valuable.

## Cross-Cutting Patterns Discovered

These patterns appear in 3+ brainstorms/plans and represent the project's core architectural idioms:

### 1. DB UNIQUE Constraint + Application Pre-Check (5 occurrences)
- Webhook idempotency (`pg_payment_id`)
- Payment idempotency key (`idempotency_key`)
- Attendance upsert (`student_id, date, session`)
- Exam marks upsert (`exam_id, student_id, subject`)
- Access request duplicate guard (`teacher_id, class_id, request_type`)

**Pattern**: Always add the DB constraint via Flyway first (correctness), then add an application-level pre-check (performance, clean error messages). Catch `ConstraintViolationException` as the race-condition backstop.

### 2. Startup Validation / Fail-Fast (4 occurrences)
- Webhook secret validation (`PaymentConfig.@PostConstruct`)
- Razorpay key validation (`RazorpayClientProducer`)
- JWT key path validation (SmallRye JWT fails if path missing)
- MFA encryption key validation

**Pattern**: `@PostConstruct` method that throws `IllegalStateException` if a required secret is blank, missing, or still a placeholder value. Application must not start in an insecure state.

### 3. Single-Transaction Atomicity with Pessimistic Lock (3 occurrences)
- OTP validate + consume + password reset
- Attendance mark (upsert within transaction)
- Exam marks upload (upsert within transaction)

**Pattern**: Operations that must be atomic (validate-then-mutate) require either a pessimistic write lock (`LockModeType.PESSIMISTIC_WRITE`) or a native upsert (`ON CONFLICT DO UPDATE`). Never split validate/consume across transactions.

### 4. Async Event Dispatch for I/O (4 occurrences)
- Email notifications (CDI `@ObservesAsync`)
- SMS notifications (CDI `@Asynchronous`)
- Attendance threshold alerts (scheduler + notification dispatch)
- Fee payment confirmation (post-webhook notification)

**Pattern**: Persist the business record in the main transaction, then fire an async CDI event carrying the record ID. A separate `@ApplicationScoped` observer handles the I/O in its own transaction context.

### 5. Native SQL Aggregation for Analytics (4 occurrences)
- Attendance analytics (GROUP BY classroom)
- Fee defaulter report (JOIN + HAVING)
- Fee collection trend (DATE_TRUNC + GROUP BY)
- Exam rankings (RANK() window function)

**Pattern**: Use native PostgreSQL queries for analytics. Never load all rows into memory. Always paginate results. Accept PostgreSQL-specific syntax — portability is not a concern.

### 6. Reusable Token Entity Pattern (4 occurrences)
- OTP tokens (password reset, email verification)
- Refresh tokens
- MFA pending tokens
- Access request expiry

**Pattern**: Entity with `code/tokenHash`, `user`, `purpose` (enum), `expiresAt`, `consumed`, `attempts`, `maxAttempts`. Query: `purpose = X AND consumed = false AND expiresAt > now()`. Consume atomically within a locked transaction.

## Dependency Graph

```
S2 (Secrets/Env)
├── S3 (PEM Keys) — needs .gitignore from S2
├── S1 (Webhook Signature) — needs env vars from S2
├── R2 (Razorpay Singleton) — needs env vars from S2
└── R6 (Observability) — needs env var config pattern from S2

D6 (Flyway Setup)
├── D2 (Webhook Idempotency) — needs migration infrastructure
├── D3 (Payment Idempotency) — needs migration infrastructure
├── D4 (Attendance Upsert) — needs UNIQUE constraint migration
├── D5 (Exam Marks Upsert) — needs UNIQUE constraint migration
├── AR3 (Access Duplicate Guard) — needs partial unique index
├── F2 (Fee Defaulters) — needs index on fee_payment
└── F3 (Fee Trend) — needs index on paid_on

R1 (Async Email)
├── R3 (Fault Tolerance) — @Retry applies to async dispatcher
├── AT2 (Attendance Alert) — sends via async notification
└── A5 (SMS via MSG91) — follows same async pattern

R2 (Razorpay Singleton)
└── R3 (Fault Tolerance) — @CircuitBreaker wraps the singleton

S4 (OTP Race Fix)
├── A1 (Refresh Token) — reuses OtpToken pattern
├── A2 (Email Verification) — reuses OtpToken pattern
└── A4 (MFA/TOTP) — reuses OtpToken pattern for pending tokens

R4 (ExceptionMapper Fix)
└── R6 (Observability) — CorrelationIdFilter provides requestId for error responses

F1 (Fee Overrides)
├── F2 (Fee Defaulters) — defaulter query must account for overrides
└── F3 (Fee Trend) — trend is independent of overrides (actual payments only)
```

## Recommended Sprint Plan

### Sprint 1 — Security Foundation (Week 1–2)
**Goal**: Eliminate all credential exposure and authentication bypasses.

| # | Feature | Effort |
|---|---------|--------|
| 1 | S2 — Secrets/Env Separation | 1 day |
| 2 | S3 — PEM Keys Removal + Rotation | 1 day |
| 3 | S1 — Webhook Signature Verification | 0.5 day |
| 4 | S4 — OTP Race Condition | 1 day |
| 5 | S5 — JWT -1L Fix + @RolesAllowed | 0.5 day |
| 6 | D6 — Flyway Migration Setup | 1 day |

**Exit criteria**: Application starts only with valid secrets; all endpoints require authentication; OTP flow is race-safe; schema changes are migration-managed.

### Sprint 2 — Data Integrity (Week 3–4)
**Goal**: Eliminate all duplicate-data and invalid-persist bugs.

| # | Feature | Effort |
|---|---------|--------|
| 7 | D1 — Payment Null-Guard | 0.5 day |
| 8 | D2 — Webhook Idempotency | 1 day |
| 9 | D3 — Payment Idempotency Key | 1 day |
| 10 | D4 — Attendance Upsert | 1 day |
| 11 | D5 — Exam Marks Upsert | 1 day |

**Exit criteria**: No duplicate rows possible for payments, attendance, or exam marks; all entities validated before persist.

### Sprint 3 — Reliability & Observability (Week 5–6)
**Goal**: Decouple I/O from transactions; add retry/circuit-breaker; make the system observable.

| # | Feature | Effort |
|---|---------|--------|
| 12 | R1 — Async Email Dispatch | 1 day |
| 13 | R2 — RazorpayClient Singleton | 0.5 day |
| 14 | R3 — Fault Tolerance | 1 day |
| 15 | R4 — GenericExceptionMapper Fix | 0.5 day |
| 16 | R5 — Analytics N+1 Fix | 1.5 days |
| 17 | R6 — Observability Stack | 2 days |

**Exit criteria**: Email never blocks transactions; Razorpay calls have retry/circuit-breaker; all errors logged with correlation IDs; health endpoints active; metrics exposed.

### Sprint 4 — Auth & Notifications (Week 7–9)
**Goal**: Complete the authentication stack and notification channels.

| # | Feature | Effort |
|---|---------|--------|
| 18 | A1 — Refresh Token + Revocation | 2 days |
| 19 | A2 — Password Strength + Email Verify | 1.5 days |
| 20 | A3 — OAuth/OIDC (Google + Microsoft) | 3 days |
| 21 | A4 — MFA/TOTP | 2 days |
| 22 | A5 — SMS via MSG91 | 2 days |

**Exit criteria**: Users can refresh tokens, logout, verify email, login via Google/Microsoft, enable TOTP MFA; SMS notifications delivered for fee payments and attendance alerts.

### Sprint 5 — Feature Completeness (Week 10–12)
**Goal**: Complete all remaining domain features.

| # | Feature | Effort |
|---|---------|--------|
| 23 | F1 — Fee Overrides | 1 day |
| 24 | F2 — Fee Defaulters | 1 day |
| 25 | F3 — Fee Trend | 1 day |
| 26 | AT1 — Attendance Guards | 0.5 day |
| 27 | AT2 — Attendance Alert | 1 day |
| 28 | E1 — Exam Upsert + Validation | 1 day |
| 29 | E2 — Exam Rankings | 1 day |
| 30 | ST1 — Student Search | 1 day |
| 31 | ST2 — Student Audit | 1.5 days |
| 32 | AR1 — Access Expiry | 1 day |
| 33 | AR2 — Rejection Metadata | 0.5 day |
| 34 | AR3 — Duplicate Guard | 0.5 day |
| 35 | RP1 — Reporting Exports | 3 days |

**Exit criteria**: All 35 features implemented, tested, and documented. Monolith is production-ready.

## AGENTS.md Updates Made

- Created `AGENTS.md` from scratch with the following sections:
  - **Project Context**: One-paragraph summary of the tech stack and project phase
  - **Non-Negotiable Rules**: 20 absolute rules extracted from all brainstorms/plans covering security, data integrity, code quality, and testing
  - **Patterns**: 9 reusable code patterns with annotated examples (upsert, idempotency, async dispatch, fault tolerance, pagination, audit log, scheduler, startup validation, CDI producer, correlation ID)
  - **Implementation Priority Order**: All 35 features ordered by criticality with dependency notes
  - **Feature Status Tracker**: Table linking each feature to its plan doc and current status
  - **Learnings**: 13 learnings organized by category (security, data integrity, reliability, architecture) extracted from brainstorm decisions and plan edge cases
