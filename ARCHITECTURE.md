# Chatrah Backend — Architecture Document

> Generated: 2026-05-14 | Stack: Quarkus 3.10 · Java 17 · PostgreSQL · SmallRye JWT · Razorpay · Apache POI

---

## 1. Project Overview

**Project Name:** Chatrah Backend  
**Purpose:** RESTful backend for a school management system — handling students, teachers, attendance, exams, fees, salaries, notifications, and analytics.  
**Runtime:** Quarkus 3.10 (JVM mode), Java 17  
**Database:** PostgreSQL 15 (`chatrah` schema), Hibernate ORM via Quarkus Panache  
**Auth:** MicroProfile JWT (SmallRye), RSA-signed tokens, BCrypt password hashing  
**Payments:** Razorpay (sandbox), HMAC-SHA256 webhook verification  
**Excel Export:** Apache POI (exam marks upload/export)  
**Email:** Quarkus Mailer (Gmail SMTP, STARTTLS port 587)  
**API Docs:** Swagger UI at `/swagger-ui`  
**Port:** 8080

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT (Browser / Mobile)                  │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS / REST (JSON)
┌───────────────────────────────▼─────────────────────────────────────┐
│                        REST RESOURCES (JAX-RS)                      │
│  Auth  Student  Teacher  ClassRoom  Attendance  Exam  Fee  Salary   │
│  Payment  Notification  Blog  Event  Analytics  Cache  AccessReq    │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│                          SERVICE LAYER                               │
│  AuthService  OtpService  FeeService  PaymentService  ExamService   │
│  AttendanceService  StudentService  TeacherService  SalaryService   │
│  NotificationService  AnalyticsService  BlogService  EventService   │
│  ClassRoomService  SchoolProfileService  CacheManagementService     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│                       REPOSITORY LAYER (Panache)                    │
│  UserRepo  StudentRepo  TeacherRepo  ClassRoomRepo  AttendanceRepo  │
│  ExamMarkRepo  FeePaymentRepo  FeePlanRepo  SalaryRepo  OtpRepo     │
│  NotificationRepo  BlogRepo  EventRepo  AccessRequestRepo  ...      │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ JDBC
┌───────────────────────────────▼─────────────────────────────────────┐
│                     PostgreSQL Database (chatrah)                   │
└─────────────────────────────────────────────────────────────────────┘

Cross-Cutting Concerns
──────────────────────
  Security      │ SmallRye JWT · @RolesAllowed · BCrypt · RSA keys
  Cache         │ Quarkus Cache (@CacheResult / @CacheInvalidate)
                │   fee-summary · attendance-summary · class-students · school-profile
  Notifications │ Quarkus Mailer (email) · SMS stub (MSG91 TODO)
  Payment GW    │ Razorpay REST API → Webhook (HMAC-SHA256)
  Error Handling│ WebAppExceptionMapper · GenericExceptionMapper → ErrorResponseDTO
```

---

## 3. Domain Model

| Entity | Table | Key Fields | Relationships |
|---|---|---|---|
| User | `users` | id, username (unique), passwordHash, role, teacherId, studentId, email, mobile, isActive | teacherId/studentId are plain Long refs |
| Student | `students` | id, rollNo, name, gender, dob, parentName, parentMobile, email, isHosteller, isTransportUser | ManyToOne → ClassRoom |
| Teacher | `teachers` | id, name, subject, mobile, email, joinDate, salary, isActive | — |
| ClassRoom | `class_rooms` | id, className, section, classTeacherId | classTeacherId is plain Long |
| Course | `courses` | id, name, fee, description | — |
| AdvancedCourse | `advanced_course` | id, name, description, baseFee, isActive | — (no audit timestamps) |
| SchoolProfile | `school_profile` | id, schoolName, logoUrl, motto, address, contactPhone, contactEmail | — |
| Attendance | `attendance` | id, date, session (MORNING/AFTERNOON), present, markedByUserId, markedAt | ManyToOne → Student, ManyToOne → ClassRoom |
| TeacherAttendance | `teacher_attendance` | id, date, present, markedAt; unique(teacher_id, date) | ManyToOne → Teacher |
| Exam | `exams` | id, name, academicYear, description, createdBy | — |
| ExamMark | `exam_marks` | id, subject, marks, maxMarks (default 100) | ManyToOne → Exam, Student, ClassRoom |
| FeePlan | `fee_plan` | id, totalFee, hostelFee, transportFee, description | ManyToOne → ClassRoom |
| FeePayment | `fee_payments` | id, amount, mode, status, paidOn, transactionId, receiptNo, pgOrderId, pgPaymentId, pgSignature | ManyToOne → Student |
| FeeOverride | `fee_override` | id, totalFee, reason | OneToOne → Student (unique) |
| SalaryStructure | `salary_structure` | id, teacherId (unique), baseSalary, paidLeaves | teacherId is plain Long |
| SalaryPayment | `salary_payments` | id, teacherId, amount, month, mode, status, paidOn, transactionId, utrNumber | teacherId is plain Long |
| StudentCourseEnrollment | `student_course_enrollment` | id, finalCourseFee, concessionReason | ManyToOne → Student, Course |
| SchoolBankAccount | `school_bank_accounts` | id, bankName, accountHolderName, accountNumber, ifsc, active | — |
| Notification | `notifications` | id, type (enum), channel (enum), recipientMobile, recipientEmail, title, message, status, sentAt | ManyToOne → Student (nullable) |
| NotificationLog | `notification_logs` | id, studentId, type, message, mobile, sentAt | studentId is plain Long |
| OtpToken | `otp_tokens` | id, purpose (FORGOT_PASSWORD), code, destination, expiresAt, attempts, maxAttempts, consumed | ManyToOne → User |
| Blog | `blogs` | id, userId, title, content (max 4000), status (PENDING/APPROVED/REJECTED) | userId is plain Long |
| Event | `events` | id, title, description, eventDate, createdBy | createdBy is plain Long |
| AccessRequest | `access_requests` | id, teacherId, classId, requestType, status, requestedAt, approvedBy, approvedAt | all refs are plain Longs |



---

## 4. API Reference

### Auth — `/api/auth`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/auth/login` | PermitAll | Authenticate with username/password, returns JWT |
| GET | `/api/auth/me` | Authenticated | Returns current user profile from JWT claims |
| POST | `/api/auth/otp/send` | PermitAll | Send 6-digit OTP to user's email for password reset |
| POST | `/api/auth/otp/verify` | PermitAll | Validate OTP code (does not consume it) |
| POST | `/api/auth/password/reset` | PermitAll | Reset password after OTP verified; marks OTP consumed |

### Student — `/api/students`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/students` | ⚠️ Unprotected | List all students |
| POST | `/api/students` | ⚠️ Unprotected | Create a new student |
| GET | `/api/students/{id}` | ⚠️ Unprotected | Get student by ID |
| PUT | `/api/students/{id}` | ⚠️ Unprotected | Update student details |
| DELETE | `/api/students/{id}` | ⚠️ Unprotected | Delete student |

> ⚠️ All `@RolesAllowed` annotations are commented out — endpoints are currently unprotected.

### Teacher — `/api/teachers`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/teachers` | Authenticated | List all teachers |
| POST | `/api/teachers` | PRINCIPAL, CLERK, SYS_ADMIN | Create a new teacher |
| GET | `/api/teachers/{id}` | Authenticated | Get teacher by ID |
| PUT | `/api/teachers/{id}` | PRINCIPAL, CLERK, SYS_ADMIN | Update teacher details |
| DELETE | `/api/teachers/{id}` | PRINCIPAL, SYS_ADMIN | Delete teacher |

### ClassRoom — `/api/classes`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/classes` | Authenticated | List all classrooms |
| POST | `/api/classes` | PRINCIPAL, CLERK, SYS_ADMIN | Create a classroom |
| GET | `/api/classes/{id}` | Authenticated | Get classroom by ID |
| PUT | `/api/classes/{id}` | PRINCIPAL, CLERK, SYS_ADMIN | Update classroom |
| DELETE | `/api/classes/{id}` | PRINCIPAL, SYS_ADMIN | Delete classroom |
| GET | `/api/classes/{id}/students` | Authenticated | List students in a classroom |

### Attendance — `/api/attendance`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/attendance` | ⚠️ Unprotected | Bulk mark attendance for a class/date/session |
| GET | `/api/attendance/student/{id}/summary` | ⚠️ Unprotected | Get attendance summary for a student (cached) |

### Exam — `/api/exams`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/exams` | Authenticated | Create a new exam |
| POST | `/api/exams/{id}/marks` | Authenticated | Upload exam marks from Excel (multipart) |
| GET | `/api/exams/{id}/students/{studentId}/result` | Authenticated | Get student result with per-subject breakdown |
| GET | `/api/exams/{id}/classes/{classId}/export` | Authenticated | Export class marks as Excel binary |

### Fee — `/api/fees`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/fees/students/{id}/summary` | Authenticated | Get fee summary for a student (cached) |
| POST | `/api/fees/students/{id}/pay` | Authenticated | Initiate direct fee payment (non-Razorpay) |
| GET | `/api/fees/payments/{id}/receipt` | Authenticated | Get fee receipt DTO |
| GET | `/api/fees/me/summary` | STUDENT | Self-service: get own fee summary from JWT claims |

### Salary — `/api/salary`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/salary/structure` | PRINCIPAL, SYS_ADMIN | Set/upsert salary structure for a teacher |
| POST | `/api/salary/pay` | PRINCIPAL, SYS_ADMIN | Record a salary payment |
| GET | `/api/salary/teachers/{id}/payments` | PRINCIPAL, SYS_ADMIN | List salary payments for a teacher |
| GET | `/api/salary/me/payments` | TEACHER | Self-service: get own salary payments from JWT claims |

### Payment — `/api/payments`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/payments/create-order` | Authenticated | Create Razorpay order; returns orderId + public key |
| POST | `/api/payments/webhook` | No role (HMAC-verified) | Razorpay webhook; verifies signature, marks payment SUCCESS |

### Notification — `/api/notifications`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/notifications/fee-payment` | Authenticated | Manually trigger fee payment notification |
| POST | `/api/notifications/exam-result` | Authenticated | Manually trigger exam result notification |

### Blog — `/api/blogs`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/blogs/approved` | PermitAll | List all approved blogs |
| GET | `/api/blogs/pending` | Authenticated | List blogs pending approval |
| POST | `/api/blogs` | Authenticated | Create a blog post (status: PENDING) |
| PUT | `/api/blogs/{id}/approve` | PRINCIPAL, SYS_ADMIN | Approve a blog post |
| PUT | `/api/blogs/{id}/reject` | PRINCIPAL, SYS_ADMIN | Reject a blog post |

### Event — `/api/events`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/events/upcoming` | PermitAll | List upcoming events (eventDate >= today) |
| POST | `/api/events` | PRINCIPAL, CLERK, SYS_ADMIN | Create a school event |
| PUT | `/api/events/{id}` | PRINCIPAL, CLERK, SYS_ADMIN | Update a school event |

### Analytics — `/api/analytics`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/analytics/attendance` | ⚠️ Unprotected | School-wide and per-class attendance analytics |
| GET | `/api/analytics/fees` | ⚠️ Unprotected | Fee collection analytics per class and school-wide |
| GET | `/api/analytics/exams/{examId}` | ⚠️ Unprotected | Exam analytics: avg marks, pass % per subject |

### Cache — `/api/cache`

| Method | Path | Roles | Description |
|---|---|---|---|
| DELETE | `/api/cache/all` | PRINCIPAL, SYS_ADMIN | Clear all four caches |
| DELETE | `/api/cache/fee` | PRINCIPAL, SYS_ADMIN | Clear fee-summary cache |
| DELETE | `/api/cache/attendance` | PRINCIPAL, SYS_ADMIN | Clear attendance-summary cache |
| DELETE | `/api/cache/class-students` | PRINCIPAL, SYS_ADMIN | Clear class-students cache |
| DELETE | `/api/cache/school-profile` | PRINCIPAL, SYS_ADMIN | Clear school-profile cache |

### AccessRequest — `/api/access-requests`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/access-requests` | ⚠️ Unprotected | Teacher submits fee-view access request for a class |
| GET | `/api/access-requests/pending` | ⚠️ Unprotected | List all pending access requests |
| PUT | `/api/access-requests/{id}/approve` | ⚠️ Unprotected | Approve an access request |
| PUT | `/api/access-requests/{id}/reject` | ⚠️ Unprotected | Reject an access request |

### School Profile — `/api/school/profile`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/school/profile` | PermitAll | Get school branding/profile (cached) |
| PUT | `/api/school/profile` | PRINCIPAL, CLERK, SYS_ADMIN | Update school profile (invalidates cache) |



---

## 5. Service Layer

| Service | Responsibility |
|---|---|
| **AuthService** | Login (BCrypt verify → JWT issue), password reset, display name resolution by role |
| **JwtService** | RSA-signed JWT generation with role, studentId, teacherId custom claims; 1-hour TTL |
| **OtpService** | 3-step password reset: generate 6-digit OTP (SecureRandom, 10-min TTL, 5 attempts), validate, consume |
| **EmailService** | Thin wrapper around Quarkus Mailer — `sendTextMail(to, subject, body)` |
| **NotificationService** | Persists Notification entity, sends email via Mailer; SMS stubbed (MSG91 TODO); types: FEE_PAYMENT, ATTENDANCE_ABSENT, EXAM_RESULT, EVENT |
| **PaymentService** | Razorpay order creation (amount in paise), pre-creates PENDING FeePayment; webhook handler updates to SUCCESS and triggers notification; cache invalidation |
| **FeeService** | Cached fee summary (FeePlan + hostel/transport flags + sum of SUCCESS payments); direct payment path; receipt assembly |
| **StudentService** | CRUD with class assignment; `listByClass` cached; cache invalidation on student change |
| **TeacherService** | Standard CRUD for teacher profiles |
| **AttendanceService** | Bulk upsert attendance per class/date/session; cached student summary (total/present/absent/%) |
| **ExamService** | Create exam; bulk upload marks from Excel (Apache POI); aggregate student result with per-subject breakdown and overall % |
| **ClassRoomService** | CRUD for classrooms (className, section, classTeacherId) |
| **AnalyticsService** | Attendance analytics (per-class avg %); fee analytics (expected/collected/due per class); exam analytics (avg marks, pass % at threshold 35%) |
| **SchoolProfileService** | Get/update school branding; cached (`school-profile`); cache invalidated on update |
| **AccessRequestService** | Teacher requests FEE_ACCESS for a class; admin approves/rejects with timestamp |
| **SalaryService** | Upsert salary structure (baseSalary, paidLeaves); record salary payment with UUID transaction ID |
| **BlogService** | Create blog (PENDING); admin approve/reject; list approved and pending |
| **EventService** | CRUD for school events; `listUpcoming` returns events with eventDate >= today |
| **CacheManagementService** | Admin-triggered invalidation of all four caches: fee-summary, attendance-summary, class-students, school-profile |

---

## 6. Security Model

### Roles

| Role | Description |
|---|---|
| `PRINCIPAL` | Full administrative access; can approve/reject, manage all data, clear caches |
| `CLERK` | Operational staff; can manage students, teachers, events, school profile |
| `TEACHER` | Can mark attendance, upload exam marks, create blogs, view own salary |
| `STUDENT` | Self-service only: view own fee summary via `/me` endpoints |
| `SYS_ADMIN` | System-level admin; same broad access as PRINCIPAL plus cache management |

### JWT Flow

```
1. POST /api/auth/login  {username, password}
        │
        ▼
   AuthService.login()
   ├── UserRepository.findByUsername()
   ├── Check isActive == true
   ├── BCrypt.checkpw(password, passwordHash)
   └── JwtService.generateToken()
        ├── issuer:   chatrah-school
        ├── subject:  userId (string)
        ├── upn:      username
        ├── groups:   [role]          ← used by @RolesAllowed
        ├── claims:   role, studentId, teacherId
        └── signed:   RSA private key (privateKey_pkcs8.pem)
        │
        ▼
   Returns: { token, role, displayName }

2. Subsequent requests:
   Authorization: Bearer <token>
        │
        ▼
   SmallRye JWT filter verifies:
   ├── Signature (publicKey.pem)
   ├── Issuer == chatrah-school
   └── Expiry (1 hour)
        │
        ▼
   @RolesAllowed enforced per endpoint
```

### Public Endpoints (no token required)

- `POST /api/auth/login`
- `POST /api/auth/otp/send`
- `POST /api/auth/otp/verify`
- `POST /api/auth/password/reset`
- `GET /api/school/profile`
- `GET /api/events/upcoming`
- `GET /api/blogs/approved`
- `POST /api/payments/webhook` (HMAC-verified instead)

### OTP / Password Reset Flow

```
1. POST /api/auth/otp/send      → generates 6-digit code, stores OtpToken (10 min, 5 attempts), emails user
2. POST /api/auth/otp/verify    → validates code, increments attempt counter, does NOT consume token
3. POST /api/auth/password/reset → BCrypt re-hash new password, marks OtpToken consumed
```

### Known Security Gaps

- `StudentResource`, `AccessRequestResource`, `AnalyticsResource` — all `@RolesAllowed` annotations are commented out; these endpoints are currently unprotected.

---

## 7. Integrations

### Razorpay (Payment Gateway)

- **Mode:** TEST / sandbox
- **SDK:** Razorpay Java SDK
- **Config:** `razorpay.key_id`, `razorpay.key_secret`, `razorpay.webhook_secret` (via `PaymentConfig`)
- **Order creation:** Amount sent in paise (×100), auto-capture enabled
- **Webhook security:** HMAC-SHA256 of raw request body with `webhook_secret`; constant-time string comparison to prevent timing attacks
- **Flow:** Frontend → create-order → Razorpay Checkout JS → payment → Razorpay webhook → backend verifies → FeePayment updated to SUCCESS → notification sent

### Quarkus Mailer (Email)

- **Provider:** Gmail SMTP
- **Protocol:** STARTTLS on port 587
- **Config:** `quarkus.mailer.from`, `quarkus.mailer.host`, `quarkus.mailer.port`, `quarkus.mailer.username`, `quarkus.mailer.password`
- **Mock:** `quarkus.mailer.mock=false` (real sends in all environments)
- **Usage:** OTP delivery, fee payment confirmations, attendance absence alerts, exam result notifications

### Google OAuth / Microsoft MSAL

- Not yet implemented in the analyzed codebase. The JWT-based auth system uses username/password with OTP reset. OAuth/MSAL integration is a planned extension.

### Apache POI (Excel)

- Used in `ExamService` for:
  - **Upload:** Parse `.xlsx` files containing student marks (examId, classId, subject, studentId, marks, maxMarks)
  - **Export:** Generate `.xlsx` binary response for class exam results (`GET /api/exams/{id}/classes/{classId}/export`)

### SMS (Stubbed)

- `NotificationService` has a TODO comment for MSG91 SMS integration. Currently only email channel is active; SMS channel is a no-op.



---

## 8. Data Flow Examples

### Flow 1: Student Fee Payment (Razorpay)

```
Student/Parent (Frontend)
  │
  ├─1─► POST /api/payments/create-order  { studentId, amount }
  │         │
  │         ▼
  │     PaymentService.createOrder()
  │     ├── Razorpay API: create order (amount in paise)
  │     ├── INSERT FeePayment { status=PENDING, pgOrderId=... }
  │     └── Returns { orderId, razorpayKeyId }
  │
  ├─2─► Frontend renders Razorpay Checkout JS with orderId
  │
  ├─3─► User completes payment on Razorpay UI
  │
  └─4─► Razorpay → POST /api/payments/webhook  { payload + X-Razorpay-Signature }
            │
            ▼
        RazorpayWebhookUtil.verifySignature()  (HMAC-SHA256, constant-time)
            │
            ▼
        PaymentService.handlePaymentSuccess()
        ├── Find FeePayment by pgOrderId
        ├── UPDATE status=SUCCESS, pgPaymentId, pgSignature, paidOn
        ├── FeeService.invalidateFeeSummaryCache(studentId)
        └── NotificationService.send(FEE_PAYMENT, student)
                └── Mailer.send(parentEmail, "Fee Payment Confirmed", ...)
                    INSERT Notification { status=SENT }
```

### Flow 2: Attendance Marking

```
Teacher (Frontend)
  │
  └─1─► POST /api/attendance  { classId, date, session, records: [{studentId, present}] }
            │
            ▼
        AttendanceService.markAttendance()
        ├── Validate session enum (MORNING / AFTERNOON)
        ├── For each record:
        │   ├── AttendanceRepository.findByStudentDateSession()
        │   ├── If exists → UPDATE present, markedByUserId, markedAt
        │   └── If not    → INSERT new Attendance row
        └── Returns bulk result summary

  ── Later ──

  └─2─► GET /api/attendance/student/{id}/summary
            │
            ▼
        AttendanceService.getStudentSummary()  [@CacheResult("attendance-summary")]
        ├── AttendanceRepository.countPresentForStudent()
        ├── AttendanceRepository.countTotalForStudent()
        └── Returns { total, present, absent, percentage }
```

### Flow 3: Exam Result Upload

```
Teacher/Admin (Frontend)
  │
  ├─1─► POST /api/exams  { name, academicYear, description }
  │         │
  │         ▼
  │     ExamService.createExam()
  │     └── INSERT Exam { name, academicYear, createdBy=userId }
  │
  └─2─► POST /api/exams/{id}/marks  (multipart: examId, classId, subject, Excel file)
            │
            ▼
        ExamService.uploadExamMarks()
        ├── Apache POI: parse .xlsx rows → List<{studentId, marks, maxMarks}>
        ├── Default maxMarks=100 if null
        └── Bulk INSERT ExamMark rows { exam, student, classRoom, subject, marks, maxMarks }

  ── Student views result ──

  └─3─► GET /api/exams/{id}/students/{studentId}/result
            │
            ▼
        ExamService.getStudentResult()
        ├── ExamMarkRepository.findByExamAndStudent()
        ├── Aggregate: totalObtained, totalMax, percentage
        └── Returns { subjects: [{subject, marks, maxMarks}], total, percentage }

  ── Admin exports class sheet ──

  └─4─► GET /api/exams/{id}/classes/{classId}/export
            └── Returns application/vnd.openxmlformats-officedocument.spreadsheetml.sheet binary
```

---

## 9. Configuration

Key entries from `application.properties`:

| Property | Value / Purpose |
|---|---|
| `quarkus.datasource.db-kind` | `postgresql` |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/chatrah` |
| `quarkus.datasource.username` | `postgres` |
| `quarkus.datasource.password` | `root` |
| `quarkus.hibernate-orm.database.generation` | `update` — auto DDL on startup |
| `quarkus.http.port` | `8080` |
| `mp.jwt.verify.publickey.location` | `publicKey.pem` — RSA public key for token verification |
| `mp.jwt.verify.issuer` | `chatrah-school` |
| `smallrye.jwt.sign.key-location` | `privateKey_pkcs8.pem` — RSA private key for token signing |
| `smallrye.jwt.time-to-live` | `3600` (seconds = 1 hour) |
| `quarkus.mailer.from` | Gmail sender address |
| `quarkus.mailer.host` | `smtp.gmail.com` |
| `quarkus.mailer.port` | `587` (STARTTLS) |
| `quarkus.mailer.username` | Gmail account username |
| `quarkus.mailer.password` | Gmail app password |
| `quarkus.mailer.mock` | `false` — real email sends |
| `razorpay.key_id` | `rzp_test_yourKeyHere` (replace with real sandbox key) |
| `razorpay.key_secret` | `yourTestSecretHere` (replace with real sandbox secret) |
| `razorpay.webhook_secret` | `your_webhook_secret_here` (replace with Razorpay dashboard secret) |
| `quarkus.swagger-ui.always-include` | `true` |
| `quarkus.swagger-ui.path` | `/swagger-ui` |

> **Security note:** Do not commit real credentials. Use environment variable overrides or a secrets manager in production:
> ```
> QUARKUS_DATASOURCE_PASSWORD=...
> RAZORPAY_KEY_SECRET=...
> QUARKUS_MAILER_PASSWORD=...
> ```

---

## 10. Agentic Task Index

A checklist of AI-agent tasks this backend's APIs and data model can enable:

### Fee & Payments
- [ ] **Auto-generate fee receipts** — trigger `GET /api/fees/payments/{id}/receipt` after each successful payment and email PDF to parent
- [ ] **Fee defaulter detection** — query fee analytics, identify students where `due > 0` past due date, auto-send reminder notifications
- [ ] **Fee concession recommendation** — analyze payment history and flag students eligible for FeeOverride based on configurable criteria
- [ ] **Monthly fee collection report** — aggregate FeePayment records by month, generate summary and email to PRINCIPAL

### Attendance
- [ ] **Attendance anomaly detection** — flag students with attendance % below threshold (e.g., < 75%) and notify parents
- [ ] **Chronic absentee alert** — detect students absent for N consecutive sessions and escalate to class teacher
- [ ] **Daily attendance digest** — compile class-wise attendance summary each evening and push to PRINCIPAL dashboard
- [ ] **Teacher attendance tracking** — monitor TeacherAttendance records and alert admin on unplanned absences

### Exams & Academics
- [ ] **Exam analytics auto-report** — after marks upload, call `GET /api/analytics/exams/{id}` and generate subject-wise performance PDF
- [ ] **At-risk student identification** — flag students scoring below pass threshold (35%) in multiple subjects
- [ ] **Progress trend analysis** — compare ExamMark records across multiple exams per student to detect improvement or decline
- [ ] **Class rank computation** — rank students within a class by total marks for a given exam

### Notifications & Communication
- [ ] **Bulk event notification** — on new Event creation, auto-trigger notifications to all parents via NotificationService
- [ ] **OTP expiry monitoring** — detect and clean up expired/unconsumed OtpToken records on a schedule
- [ ] **Blog moderation queue** — alert PRINCIPAL when pending blog count exceeds threshold

### Administration
- [ ] **Cache warm-up agent** — after cache clear, proactively re-populate fee-summary and attendance-summary caches for active students
- [ ] **Access request auto-approval** — auto-approve FEE_ACCESS requests from teachers for their own assigned class
- [ ] **Salary disbursement reminder** — detect teachers without a SalaryPayment for the current month and alert admin
- [ ] **School profile completeness check** — verify SchoolProfile has logo, motto, and contact details; alert if incomplete

### Analytics & Reporting
- [ ] **Principal dashboard aggregation** — combine attendance, fee, and exam analytics into a single daily briefing
- [ ] **Year-end academic report** — generate per-student report cards from ExamMark data across all exams in an academic year
- [ ] **Revenue forecasting** — project expected fee collection based on enrolled students and FeePlan data
- [ ] **Teacher performance index** — correlate class attendance rates and exam pass rates with assigned class teacher

