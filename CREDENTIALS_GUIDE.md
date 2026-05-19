# Credentials Guide — Chatrah Backend

This guide explains how to configure all external service credentials for the Chatrah backend.

---

## Quick Start (Local Dev)

1. Copy `.env.example` to `.env`:
   ```
   cp .env.example .env
   ```
2. Edit `.env` with your real values.
3. Run the app — Quarkus reads env vars automatically:
   ```
   mvn quarkus:dev
   ```

> **Note:** With default values, the app starts in "mock mode" — emails are logged (not sent), SMS is disabled, and Razorpay uses placeholder keys.

---

## Email (Gmail SMTP)

| Variable | Value |
|----------|-------|
| `SMTP_FROM` | `jinukalaraneesh3@gmail.com` |
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | `jinukalaraneesh3@gmail.com` |
| `SMTP_PASSWORD` | Your Gmail **App Password** |
| `SMTP_MOCK` | `false` (set to `true` to skip real sending) |

### How to get a Gmail App Password:
1. Go to https://myaccount.google.com/security
2. Enable **2-Step Verification** if not already enabled
3. Go to https://myaccount.google.com/apppasswords
4. Select "Mail" and "Other (Custom name)" → enter "Chatrah Backend"
5. Copy the 16-character password → paste into `SMTP_PASSWORD`

---

## Razorpay (Payment Gateway)

| Variable | Value |
|----------|-------|
| `RAZORPAY_KEY_ID` | Your Razorpay Key ID (starts with `rzp_test_` or `rzp_live_`) |
| `RAZORPAY_KEY_SECRET` | Your Razorpay Key Secret |
| `RAZORPAY_WEBHOOK_SECRET` | Webhook secret from Razorpay Dashboard |

### How to get Razorpay credentials:
1. Sign up at https://dashboard.razorpay.com
2. Go to **Settings → API Keys → Generate Key**
3. Copy Key ID and Key Secret
4. For webhooks: **Settings → Webhooks → Add New Webhook**
   - URL: `https://your-domain.com/api/payments/webhook`
   - Events: `payment.captured`, `payment.failed`
   - Copy the webhook secret

### Test Mode:
- Use keys starting with `rzp_test_` for sandbox testing
- Test card: `4111 1111 1111 1111`, any future expiry, any CVV

---

## SMS (MSG91)

| Variable | Value |
|----------|-------|
| `SMS_ENABLED` | `true` to enable SMS sending |
| `SMS_PROVIDER` | `msg91` (or `mock` for testing) |
| `MSG91_AUTH_KEY` | Your MSG91 auth key |
| `MSG91_SENDER_ID` | 6-char sender ID (e.g., `CHATRAH`) |
| `MSG91_TEMPLATE_ID` | DLT-approved template ID |

### How to get MSG91 credentials:
1. Sign up at https://msg91.com
2. Go to **Dashboard → API Keys** → copy auth key
3. Register a Sender ID under **SMS → Sender ID**
4. Create a DLT-approved template under **SMS → Templates**
5. Use the template ID for absence notifications

### SMS Template Example:
```
Dear {parentName}, your child {studentName} was marked absent on {date}. 
Please contact the school for details. - Chatrah School
```

---

## JWT Keys

| Variable | Value |
|----------|-------|
| `JWT_PUBLIC_KEY_LOCATION` | Path to public key PEM file |
| `JWT_PRIVATE_KEY_LOCATION` | Path to private key PEM file (PKCS8) |

### Generate new keys:
```bash
# Generate RSA private key
openssl genrsa -out privateKey.pem 2048

# Convert to PKCS8 format
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in privateKey.pem -out privateKey_pkcs8.pem

# Extract public key
openssl rsa -in privateKey.pem -pubout -out publicKey.pem
```

Place both `publicKey.pem` and `privateKey_pkcs8.pem` in `src/main/resources/` for dev, or set the env vars to absolute paths in production.

> ⚠️ **Never commit PEM files to git.** They are in `.gitignore`.

---

## Database (PostgreSQL)

| Variable | Value |
|----------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/chatrah` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Your DB password |

### Setup:
```bash
# Create the database
psql -U postgres -c "CREATE DATABASE chatrah;"
```

---

## Attendance Alert Configuration

| Variable | Value |
|----------|-------|
| `ATTENDANCE_ALERT_ENABLED` | `true` to enable nightly SMS alerts |
| `ATTENDANCE_THRESHOLD` | Minimum attendance % (default: 75) |

When enabled, a nightly job (9 PM) checks all students' attendance. If a student's attendance drops below the threshold, an SMS is sent to their parent's phone number.

---

## Production Checklist

- [ ] All `placeholder` values replaced with real credentials
- [ ] `SMTP_MOCK=false`
- [ ] `SMS_ENABLED=true`
- [ ] `SMS_PROVIDER=msg91`
- [ ] Razorpay keys switched from `rzp_test_` to `rzp_live_`
- [ ] PEM keys rotated (not the dev keys)
- [ ] `.env` file is NOT committed to git
- [ ] Database password is strong and unique
