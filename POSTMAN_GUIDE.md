# Postman API Guide — Chatrah Backend

Base URL: `http://localhost:8080`

---

## Step 1: Login (Get JWT Token)

**POST** `http://localhost:8080/api/auth/login`

Headers:
```
Content-Type: application/json
```

Body (raw JSON):
```json
{
  "username": "principal",
  "password": "admin123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9...",
  "role": "PRINCIPAL",
  "displayName": "Principal Name",
  "studentId": null,
  "teacherId": null
}
```

> **Copy the `token` value.** You'll use it in all subsequent requests.

---

## Step 2: Set Up Authorization in Postman

For ALL protected endpoints, add this header:
```
Authorization: Bearer <paste-your-token-here>
```

**Tip:** In Postman, go to the Collection → Authorization tab → Type: Bearer Token → paste the token. All requests in the collection will inherit it.

---

## Public Endpoints (No Token Needed)

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/api/auth/login` | Login |
| GET | `/api/school/profile` | Get school info |
| GET | `/api/events/upcoming` | List upcoming events |
| GET | `/api/blogs/approved` | List approved blogs |

---

## Auth & Password Reset

| Method | URL | Body | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/login` | `{"username":"...","password":"..."}` | Login |
| GET | `/api/auth/me` | — | Get current user info |
| POST | `/api/auth/otp/send-reset` | `{"username":"teacher1"}` | Send OTP to email |
| POST | `/api/auth/otp/verify-reset` | `{"username":"teacher1","otp":"123456"}` | Verify OTP |
| POST | `/api/auth/password/reset` | `{"username":"teacher1","otp":"123456","newPassword":"NewPass@123"}` | Reset password |

---

## School Profile

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/school/profile` | — | Public |
| PUT | `/api/school/profile` | `{"name":"Chatrah School","address":"...","phone":"...","email":"...","logo":"..."}` | PRINCIPAL, CLERK |

---

## Classes

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/classes` | — | PRINCIPAL, CLERK, TEACHER |
| GET | `/api/classes/{id}` | — | PRINCIPAL, CLERK, TEACHER |
| POST | `/api/classes` | `{"name":"10","section":"A"}` | PRINCIPAL, CLERK |
| PUT | `/api/classes/{id}` | `{"name":"10","section":"B"}` | PRINCIPAL, CLERK |
| DELETE | `/api/classes/{id}` | — | PRINCIPAL, CLERK |
| GET | `/api/classes/{classId}/students` | — | TEACHER, PRINCIPAL, CLERK |

---

## Students

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/students?classId=1` | — | PRINCIPAL, CLERK, TEACHER |
| GET | `/api/students/{id}` | — | PRINCIPAL, CLERK, TEACHER |
| POST | `/api/students` | See below | PRINCIPAL, CLERK |
| PUT | `/api/students/{id}` | See below | PRINCIPAL, CLERK |
| DELETE | `/api/students/{id}` | — | PRINCIPAL only |

**Create/Update Student Body:**
```json
{
  "name": "Ravi Kumar",
  "rollNo": 1,
  "gender": "Male",
  "dateOfBirth": "2010-05-15",
  "parentName": "Suresh Kumar",
  "parentMobile": "9876543210",
  "email": "ravi@example.com",
  "address": "123 Main St",
  "admissionDate": "2023-06-01",
  "classRoomId": 1,
  "isHosteller": false,
  "isTransportUser": true
}
```

---

## Teachers

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/teachers` | — | PRINCIPAL, CLERK |
| GET | `/api/teachers/{id}` | — | PRINCIPAL, CLERK, TEACHER (self) |
| POST | `/api/teachers` | `{"name":"Mrs. Sharma","subject":"Math","phone":"9876543210","email":"sharma@school.com"}` | PRINCIPAL, CLERK |
| PUT | `/api/teachers/{id}` | Same as above | PRINCIPAL, CLERK |
| DELETE | `/api/teachers/{id}` | — | PRINCIPAL, CLERK |

---

## Attendance

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| POST | `/api/attendance/mark` | See below | TEACHER, PRINCIPAL, CLERK |
| GET | `/api/attendance/student/{studentId}/summary` | — | ALL roles |

**Mark Attendance Body:**
```json
{
  "classId": 1,
  "date": "2026-05-16",
  "session": "MORNING",
  "students": [
    { "studentId": 1, "present": true },
    { "studentId": 2, "present": false },
    { "studentId": 3, "present": true }
  ]
}
```

---

## Fees

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/fees/student/{studentId}/summary` | — | ALL roles |
| GET | `/api/fees/me/summary` | — | STUDENT (uses JWT studentId) |
| POST | `/api/fees/student/{studentId}/pay/online` | `{"amount": 5000}` | STUDENT |
| GET | `/api/fees/receipt/{paymentId}` | — | STUDENT, PRINCIPAL, CLERK |

---

## Payments (Razorpay)

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| POST | `/api/payments/student/{studentId}/create-order` | `{"amount": 5000}` | STUDENT, PRINCIPAL, CLERK |
| POST | `/api/payments/webhook` | Razorpay payload | No auth (webhook) |

---

## Exams

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| POST | `/api/exams` | See below | PRINCIPAL, CLERK |
| POST | `/api/exams/upload` | See below | TEACHER, PRINCIPAL, CLERK |
| GET | `/api/exams/{examId}/student/{studentId}` | — | ALL roles |
| GET | `/api/exams/{examId}/class/{classId}/subject/{subject}/export` | — (downloads Excel) | TEACHER, PRINCIPAL, CLERK |

**Create Exam Body:**
```json
{
  "name": "Mid-Term 2026",
  "classRoomId": 1,
  "examDate": "2026-06-15",
  "subjects": ["Math", "Science", "English"]
}
```

**Upload Marks Body:**
```json
{
  "examId": 1,
  "classId": 1,
  "subject": "Math",
  "marks": [
    { "studentId": 1, "marks": 85, "maxMarks": 100 },
    { "studentId": 2, "marks": 72, "maxMarks": 100 },
    { "studentId": 3, "marks": 91, "maxMarks": 100 }
  ]
}
```

---

## Salary

| Method | URL | Body/Params | Roles |
|--------|-----|-------------|-------|
| POST | `/api/salary/structure` | `{"teacherId":1,"basicPay":30000,"hra":5000,"da":3000}` | PRINCIPAL, CLERK |
| POST | `/api/salary/pay?teacherId=1&amount=38000&month=2026-05&mode=BANK` | — | PRINCIPAL, CLERK |
| GET | `/api/salary/teacher/{teacherId}/payments` | — | PRINCIPAL, CLERK, TEACHER |
| GET | `/api/salary/me/payments` | — | TEACHER (uses JWT teacherId) |

---

## Notifications (Manual Trigger)

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| POST | `/api/notifications/sendFeePayment` | `{"studentId":1,"paymentId":1}` | PRINCIPAL, CLERK |
| POST | `/api/notifications/sendExamResult` | `{"studentId":1,"examName":"Mid-Term 2026"}` | PRINCIPAL, TEACHER |

---

## Blogs

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/blogs/approved` | — | Public |
| GET | `/api/blogs/pending` | — | PRINCIPAL, CLERK |
| POST | `/api/blogs` | `{"title":"My First Day","content":"It was amazing..."}` | ALL logged-in |
| POST | `/api/blogs/{id}/approve` | — | PRINCIPAL, CLERK |
| POST | `/api/blogs/{id}/reject` | — | PRINCIPAL, CLERK |

---

## Events

| Method | URL | Body | Roles |
|--------|-----|------|-------|
| GET | `/api/events/upcoming` | — | Public |
| POST | `/api/events` | `{"title":"Annual Day","description":"...","eventDate":"2026-08-15"}` | PRINCIPAL, CLERK |
| PUT | `/api/events/{id}` | Same as above | PRINCIPAL, CLERK |

---

## Access Requests (Teacher → Principal)

| Method | URL | Params | Roles |
|--------|-----|--------|-------|
| POST | `/api/access-requests/fee?classId=1` | — | TEACHER |
| GET | `/api/access-requests/pending` | — | PRINCIPAL, CLERK |
| POST | `/api/access-requests/{id}/approve` | — | PRINCIPAL, CLERK |
| POST | `/api/access-requests/{id}/reject` | — | PRINCIPAL, CLERK |

---

## Analytics (Dashboard)

| Method | URL | Roles |
|--------|-----|-------|
| GET | `/api/analytics/attendance` | PRINCIPAL |
| GET | `/api/analytics/fee` | PRINCIPAL, CLERK |
| GET | `/api/analytics/exams/{examId}` | PRINCIPAL, TEACHER |

---

## Swagger UI

Open in browser: `http://localhost:8080/swagger-ui`

This gives you an interactive API explorer with all endpoints documented.

---

## Role Summary

| Role | Can Do |
|------|--------|
| **PRINCIPAL** | Everything — full admin |
| **CLERK** | Student/teacher CRUD, fees, salary, notifications |
| **TEACHER** | Mark attendance, upload marks, view students, request access, blogs |
| **STUDENT** | View own fees/attendance/results, pay fees, write blogs |
| **SYS_ADMIN** | Technical admin — same as PRINCIPAL |

---

## Quick Test Flow

1. **Login as PRINCIPAL** → get token
2. **Create a class** → POST `/api/classes`
3. **Create a student** → POST `/api/students`
4. **Create a teacher** → POST `/api/teachers`
5. **Login as TEACHER** → get teacher token
6. **Mark attendance** → POST `/api/attendance/mark`
7. **Check attendance summary** → GET `/api/attendance/student/{id}/summary`
8. **Create exam** (as PRINCIPAL) → POST `/api/exams`
9. **Upload marks** (as TEACHER) → POST `/api/exams/upload`
10. **Check results** → GET `/api/exams/{examId}/student/{studentId}`

---

## Notes

- All dates use ISO format: `YYYY-MM-DD`
- All responses are JSON
- 401 = missing/invalid token
- 403 = valid token but wrong role
- 404 = resource not found
- 500 = server error (check logs)
