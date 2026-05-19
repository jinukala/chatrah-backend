# Implementation Plans: Security & Data Integrity Fixes
**Status:** DRAFT
**Date:** 2026-05-14
**Phase:** 2 — Plan
**Compound Workflow Session:** plan_security_data_integrity

---

## Table of Contents

### Security (S)
- [S1 — Razorpay Webhook Signature Verification](#s1--razorpay-webhook-signature-verification)
- [S2 — Hardcoded Secrets / Environment Separation](#s2--hardcoded-secrets--environment-separation)
- [S3 — PEM Keys on Filesystem](#s3--pem-keys-on-filesystem)
- [S4 — OTP Reset Race Condition](#s4--otp-reset-race-condition)
- [S5 — JWT Parse Failure Fallback to -1L](#s5--jwt-parse-failure-fallback-to--1l)

### Data Integrity (D)
- [D1 — handlePaymentSuccess Null-Guard](#d1--handlepaymentsuccessnull-guard)
- [D2 — Webhook Idempotency](#d2--webhook-idempotency)
- [D3 — initiateOnlinePayment Idempotency Key](#d3--initiateonlinepayment-idempotency-key)
- [D4 — markAttendance Race Condition](#d4--markattendance-race-condition)
- [D5 — uploadExamMarks Duplicate](#d5--uploadexammarks-duplicate)
- [D6 — Hibernate DDL → Flyway Migration](#d6--hibernate-ddl--flyway-migration)

---

## S1 — Razorpay Webhook Signature Verification

### Summary
`RazorpayWebhookUtil.verifySignature` silently swallows all exceptions and returns `false`, making JCE misconfiguration invisible. `application.properties` contains the placeholder value `your_webhook_secret_here` for `razorpay.webhook_secret`, meaning every legitimate webhook is rejected in the current deployed state. Fix: replace the placeholder with an env-var reference, add exception logging to `verifySignature`, and add a `@PostConstruct` startup check in `PaymentConfig` that fails fast if the secret is blank or still the placeholder.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/resources/application.properties` | Modify | Replace `razorpay.webhook_secret=your_webhook_secret_here` with `${RAZORPAY_WEBHOOK_SECRET}` env-var reference |
| `src/main/java/com/chatrah/school/util/RazorpayWebhookUtil.java` | Modify | Add static logger; log exception before returning `false` in `verifySignature` |
| `src/main/java/com/chatrah/school/config/PaymentConfig.java` | Modify | Add `@PostConstruct` startup validation asserting `webhookSecret` is non-blank and not the placeholder string |

### New Files

| File | Reason |
|------|--------|
| _(none)_ | Startup validation is added directly to the existing `PaymentConfig` bean |

### Method Signatures

```java
// RazorpayWebhookUtil.java — no signature change, internal logging added
public static boolean verifySignature(String payload, String actualSignature, String secret)

// PaymentConfig.java — new lifecycle method
@PostConstruct
public void validateSecrets()
```

### Implementation Steps

1. In `application.properties`, change:
   ```
   razorpay.webhook_secret=your_webhook_secret_here
   ```
   to:
   ```
   %prod.razorpay.webhook_secret=${RAZORPAY_WEBHOOK_SECRET}
   %dev.razorpay.webhook_secret=${RAZORPAY_WEBHOOK_SECRET:dev_webhook_secret_placeholder}
   ```
2. In `RazorpayWebhookUtil`, add a static logger at the top of the class:
   ```java
   private static final java.util.logging.Logger LOG =
       java.util.logging.Logger.getLogger(RazorpayWebhookUtil.class.getName());
   ```
3. In `verifySignature`, replace `catch (Exception e) { return false; }` with:
   ```java
   catch (Exception e) {
       LOG.severe("HMAC computation failed — check JCE config or secret value: " + e.getMessage());
       return false;
   }
   ```
4. In `PaymentConfig`, add `jakarta.annotation.PostConstruct` import and the following method:
   ```java
   @PostConstruct
   public void validateSecrets() {
       if (webhookSecret == null || webhookSecret.isBlank()
               || webhookSecret.startsWith("your_webhook_secret")) {
           throw new IllegalStateException(
               "razorpay.webhook_secret is not configured. Set RAZORPAY_WEBHOOK_SECRET env var.");
       }
   }
   ```
5. Verify `PaymentResource.handleWebhook` still returns `400` on invalid signature and `200` on success — no change needed there.
6. Set `RAZORPAY_WEBHOOK_SECRET` in the deployment environment (Docker env, GitHub Actions secret, or `.env` file for dev).

### Edge Cases

| Case | Handling |
|------|----------|
| Secret is empty string `""` | `isBlank()` catches it; startup fails with `IllegalStateException` |
| Secret is the literal placeholder string | `startsWith("your_webhook_secret")` catches it; startup fails |
| JCE `HmacSHA256` unavailable (misconfigured JRE) | Exception is now logged at SEVERE before returning `false`; webhook returns `400` |
| Razorpay retries a valid webhook after a transient `400` | Idempotency (D2) handles duplicate processing; signature check is stateless |
| `payload` is null | `mac.doFinal(null.getBytes(...))` throws NPE — caught and logged by the updated catch block |

### Test Cases

- [ ] `verifySignature` returns `true` for a known-good payload/secret/signature triple
- [ ] `verifySignature` returns `false` and logs SEVERE when `Mac.getInstance` throws (mock JCE failure)
- [ ] `verifySignature` returns `false` for a tampered payload
- [ ] `PaymentConfig.validateSecrets()` throws `IllegalStateException` when secret is blank
- [ ] `PaymentConfig.validateSecrets()` throws `IllegalStateException` when secret is `"your_webhook_secret_here"`
- [ ] `PaymentConfig.validateSecrets()` passes when secret is a non-blank, non-placeholder value
- [ ] `POST /api/payments/webhook` returns `400` when `X-Razorpay-Signature` is wrong
- [ ] `POST /api/payments/webhook` returns `200` when signature is valid

---

## S2 — Hardcoded Secrets / Environment Separation

### Summary
`application.properties` contains live/real credentials committed to source control: `quarkus.datasource.password=root`, `quarkus.mailer.password=Esangam@123`, and Razorpay keys. There is no root `.gitignore`. Fix: restructure `application.properties` with `%dev`/`%prod` profile blocks, replace all secret values with `${ENV_VAR}` references, create a root `.gitignore`, and provide a `.env.example`. The Gmail app password `Esangam@123` must be rotated immediately.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/resources/application.properties` | Modify | Add `%dev`/`%prod` profile prefixes; replace all secret values with env-var references; add `%prod.quarkus.swagger-ui.always-include=false` |

### New Files

| File | Reason |
|------|--------|
| `.gitignore` (project root) | Prevent `.env`, `*.pem`, `target/`, and IDE files from being committed |
| `.env.example` | Document all required environment variables for developer onboarding |

### Method Signatures

```java
// No Java code changes — configuration-only fix
```

### Implementation Steps

1. Create `.gitignore` at the project root with at minimum:
   ```
   .env
   *.env
   *.pem
   src/main/resources/*.pem
   target/
   .idea/
   *.iml
   ```
2. Restructure `application.properties` — replace secret values with env-var references:
   ```properties
   # Datasource
   %dev.quarkus.datasource.password=${DB_PASSWORD:root}
   %prod.quarkus.datasource.password=${DB_PASSWORD}

   # Mailer
   %dev.quarkus.mailer.password=${MAIL_PASSWORD:}
   %prod.quarkus.mailer.password=${MAIL_PASSWORD}

   # Razorpay
   %dev.razorpay.key_id=${RAZORPAY_KEY_ID:rzp_test_placeholder}
   %prod.razorpay.key_id=${RAZORPAY_KEY_ID}
   %dev.razorpay.key_secret=${RAZORPAY_KEY_SECRET:placeholder}
   %prod.razorpay.key_secret=${RAZORPAY_KEY_SECRET}
   %dev.razorpay.webhook_secret=${RAZORPAY_WEBHOOK_SECRET:dev_placeholder}
   %prod.razorpay.webhook_secret=${RAZORPAY_WEBHOOK_SECRET}

   # Swagger UI — disable in prod
   %prod.quarkus.swagger-ui.always-include=false
   ```
3. Create `.env.example` documenting every variable:
   ```
   DB_PASSWORD=
   MAIL_PASSWORD=
   RAZORPAY_KEY_ID=
   RAZORPAY_KEY_SECRET=
   RAZORPAY_WEBHOOK_SECRET=
   JWT_PRIVATE_KEY_PATH=
   JWT_PUBLIC_KEY_PATH=
   ```
4. **Immediately rotate** the Gmail app password `Esangam@123` — generate a new Google App Password and update the deployment environment variable.
5. Run `git log --all --full-history -- src/main/resources/application.properties` to confirm whether the real password was ever pushed. If yes, scrub git history with `git filter-repo --path src/main/resources/application.properties --invert-paths` (or BFG Repo Cleaner) after rotating.
6. Confirm CI/CD pipeline (GitHub Actions) injects all required secrets as environment variables.

### Edge Cases

| Case | Handling |
|------|----------|
| `%prod` env var not set | Quarkus fails at startup with `NoSuchElementException` — intentional fail-fast |
| Developer forgets to create `.env` | `%dev` defaults (empty or placeholder) allow startup; real operations may fail gracefully |
| Git history contains the real Gmail password | Must scrub history AND rotate the credential — both steps are required |
| `QUARKUS_PROFILE` not set | Quarkus defaults to `prod` in a packaged JAR — correct behaviour |

### Test Cases

- [ ] Application starts in `%dev` profile without any env vars set (uses defaults)
- [ ] Application fails to start in `%prod` profile when `DB_PASSWORD` is unset
- [ ] Application fails to start in `%prod` profile when `MAIL_PASSWORD` is unset
- [ ] `.gitignore` prevents `.env` from being staged (`git status` shows `.env` as ignored)
- [ ] `.env.example` is committed and contains all required variable names
- [ ] Swagger UI is not accessible at `/q/swagger-ui` in `%prod` profile
- [ ] Razorpay config bean loads correctly when env vars are set

---


## S3 — PEM Keys on Filesystem

### Summary
`privateKey_pkcs8.pem` and `publicKey.pem` live in `src/main/resources/` and are packaged into the JAR. There is no root `.gitignore`, so they are almost certainly committed to git. Any attacker with the private key can forge arbitrary JWTs for any user including `SYS_ADMIN`. Fix: remove PEM files from `src/main/resources`, add `*.pem` to `.gitignore` (covered by S2), update `application.properties` to load keys from filesystem paths supplied via env vars, generate a new key pair (existing keys are potentially compromised), and document the git history scrub procedure.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/resources/application.properties` | Modify | Replace classpath PEM references with env-var filesystem path references |
| `src/main/resources/privateKey_pkcs8.pem` | Delete | Must not be packaged in JAR or committed to git |
| `src/main/resources/publicKey.pem` | Delete | Must not be packaged in JAR or committed to git |
| `.gitignore` | Modify | Already created in S2; confirm `*.pem` and `src/main/resources/*.pem` entries are present |

### New Files

| File | Reason |
|------|--------|
| _(none — keys are external to the repo)_ | Keys are provisioned at deploy time via filesystem mount or env var |

### Method Signatures

```java
// No Java code changes — JwtService uses SmallRye JWT which reads keys from config automatically
```

### Implementation Steps

1. Generate a new RSA-2048 (minimum) key pair — the existing keys must be considered compromised:
   ```bash
   openssl genrsa -out privateKey.pem 2048
   openssl pkcs8 -topk8 -inform PEM -in privateKey.pem -out privateKey_pkcs8.pem -nocrypt
   openssl rsa -in privateKey.pem -pubout -out publicKey.pem
   ```
2. Store the new PEM files outside the repository (e.g. `/etc/chatrah/keys/` on the server, or as Docker secrets at `/run/secrets/`).
3. Update `application.properties`:
   ```properties
   %dev.smallrye.jwt.sign.key-location=${JWT_PRIVATE_KEY_PATH:src/test/resources/dev-privateKey_pkcs8.pem}
   %prod.smallrye.jwt.sign.key-location=${JWT_PRIVATE_KEY_PATH}

   %dev.mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_PATH:src/test/resources/dev-publicKey.pem}
   %prod.mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_PATH}
   ```
4. Delete `src/main/resources/privateKey_pkcs8.pem` and `src/main/resources/publicKey.pem` from the working tree.
5. Generate a throwaway dev-only key pair and place it at `src/test/resources/dev-privateKey_pkcs8.pem` and `src/test/resources/dev-publicKey.pem`. Add `src/test/resources/dev-*.pem` to `.gitignore` as well.
6. Scrub git history to remove the committed PEM files:
   ```bash
   git filter-repo --path src/main/resources/privateKey_pkcs8.pem --invert-paths
   git filter-repo --path src/main/resources/publicKey.pem --invert-paths
   ```
   Force-push all branches after scrubbing. Invalidate all existing JWTs by rotating the key pair (step 1 already does this).
7. Add `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` to `.env.example`.

### Edge Cases

| Case | Handling |
|------|----------|
| `JWT_PRIVATE_KEY_PATH` file does not exist at startup | SmallRye JWT throws at startup — application fails to start (correct fail-fast) |
| Dev profile with no `JWT_PRIVATE_KEY_PATH` set | Falls back to `src/test/resources/dev-privateKey_pkcs8.pem` default |
| Existing JWTs signed with the old (compromised) key | They will fail verification after key rotation — users must re-login |
| Key file permissions too broad (world-readable) | Document that key files must be `chmod 600` owned by the app user |
| Git history scrub on a shared remote | All collaborators must re-clone or `git fetch --all` after force-push |

### Test Cases

- [ ] Application starts in `%dev` profile using the dev key pair from `src/test/resources/`
- [ ] Application fails to start in `%prod` when `JWT_PRIVATE_KEY_PATH` env var is unset
- [ ] `JwtService.generateToken()` produces a verifiable JWT using the new key pair
- [ ] A JWT signed with the old key is rejected after key rotation
- [ ] `src/main/resources/privateKey_pkcs8.pem` does not exist in the working tree
- [ ] `git log --all -- src/main/resources/privateKey_pkcs8.pem` returns no commits after history scrub
- [ ] `*.pem` entries are present in `.gitignore` and `git status` shows PEM files as ignored

---

## S4 — OTP Reset Race Condition

### Summary
The password reset flow makes three separate `@Transactional` calls: `validatePasswordResetOtp`, `resetPassword`, and `markOtpUsed`. Between calls, a concurrent request with the same OTP can pass validation because the OTP is not yet consumed. Additionally, `validatePasswordResetOtp` increments `attempts` before checking the code, giving users effectively 4 attempts instead of 5. Fix: add `OtpTokenRepository.findActiveTokenForUserWithLock` (pessimistic write lock), add `OtpService.validateAndConsumeOtp` (single transaction: lock → check code first → increment on failure → mark consumed), add `AuthService.resetPasswordWithOtp` (single transaction wrapping both), and update `AuthResource` to call the new method.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/java/com/chatrah/school/repository/OtpTokenRepository.java` | Modify | Add `findActiveTokenForUserWithLock` using `PESSIMISTIC_WRITE` lock mode |
| `src/main/java/com/chatrah/school/service/OtpService.java` | Modify | Add `validateAndConsumeOtp(String username, String otp)` method |
| `src/main/java/com/chatrah/school/service/AuthService.java` | Modify | Add `resetPasswordWithOtp(String username, String otp, String newPassword)` method; inject `OtpService` |
| `src/main/java/com/chatrah/school/resource/AuthResource.java` | Modify | Replace the three-call sequence in `resetPassword` with a single call to `authService.resetPasswordWithOtp` |

### New Files

| File | Reason |
|------|--------|
| _(none)_ | All changes are additions to existing classes |

### Method Signatures

```java
// OtpTokenRepository.java
public OtpToken findActiveTokenForUserWithLock(Long userId, OtpToken.Purpose purpose)

// OtpService.java
@Transactional
public void validateAndConsumeOtp(String username, String otp)

// AuthService.java
@Transactional
public void resetPasswordWithOtp(String username, String otp, String newPassword)
```

### Implementation Steps

1. In `OtpTokenRepository`, add the locked query method:
   ```java
   public OtpToken findActiveTokenForUserWithLock(Long userId, Purpose purpose) {
       return find("user.id = ?1 and purpose = ?2 and consumed = false and expiresAt > ?3",
               userId, purpose, LocalDateTime.now())
               .withLock(LockModeType.PESSIMISTIC_WRITE)
               .firstResult();
   }
   ```
   Add `import jakarta.persistence.LockModeType;`.

2. In `OtpService`, add `validateAndConsumeOtp`:
   ```java
   @Transactional
   public void validateAndConsumeOtp(String username, String otp) {
       User user = userRepository.find("username", username).firstResult();
       if (user == null) throw new NotFoundException("User not found");

       OtpToken token = otpTokenRepository.findActiveTokenForUserWithLock(user.getId(), FORGOT_PASSWORD);
       if (token == null) throw new BadRequestException("OTP expired or not requested");
       if (token.isConsumed()) throw new BadRequestException("OTP already used");
       if (token.getAttempts() >= token.getMaxAttempts()) {
           token.setConsumed(true);
           throw new BadRequestException("Too many invalid attempts. Request a new OTP.");
       }

       // Check code FIRST, then increment attempts only on failure
       if (!token.getCode().equals(otp)) {
           token.setAttempts(token.getAttempts() + 1);
           throw new BadRequestException("Invalid OTP");
       }

       // OTP is correct — consume it atomically
       token.setConsumed(true);
   }
   ```

3. In `AuthService`, inject `OtpService` and add `resetPasswordWithOtp`:
   ```java
   @Inject
   OtpService otpService;

   @Transactional
   public void resetPasswordWithOtp(String username, String otp, String newPassword) {
       otpService.validateAndConsumeOtp(username, otp);
       resetPassword(username, newPassword);
   }
   ```
   Note: `resetPassword` is already `@Transactional(REQUIRED)` — it will join the outer transaction started by `resetPasswordWithOtp`.

4. In `AuthResource.resetPassword`, replace the three-call sequence:
   ```java
   // Before:
   otpService.validatePasswordResetOtp(request.getUsername(), request.getOtp());
   authService.resetPassword(request.getUsername(), request.getNewPassword());
   otpService.markOtpUsed(request.getUsername());

   // After:
   authService.resetPasswordWithOtp(request.getUsername(), request.getOtp(), request.getNewPassword());
   ```

5. Keep `OtpService.validatePasswordResetOtp` and `markOtpUsed` in place (do not delete) — `verifyResetOtp` endpoint still uses `validatePasswordResetOtp` for the two-step UI flow. Add a `@Deprecated` Javadoc note on `markOtpUsed` indicating it should not be used for the reset flow.

6. Verify there are no other call sites of `markOtpUsed` or the three-call sequence outside `AuthResource`.

### Edge Cases

| Case | Handling |
|------|----------|
| Two concurrent requests with same OTP | Pessimistic write lock serializes them; second request blocks until first commits, then finds `consumed=true` and throws `BadRequestException` |
| OTP expired between validation and consumption | `findActiveTokenForUserWithLock` filters `expiresAt > NOW()` — returns `null`, throws `BadRequestException` |
| `validateAndConsumeOtp` called from within an existing transaction | `@Transactional(REQUIRED)` joins the outer transaction — correct |
| DB lock timeout (long-running transaction holds lock) | Default DB lock timeout applies; configure `jakarta.persistence.lock.timeout` if needed |
| `attempts` already at `maxAttempts - 1` and correct OTP submitted | Code check passes before increment — OTP is consumed successfully, no spurious lockout |

### Test Cases

- [ ] Concurrent reset requests with the same OTP: only one succeeds, the other gets `400 Bad Request`
- [ ] Correct OTP on attempt 5 (last allowed): succeeds without lockout
- [ ] Wrong OTP on attempt 5: `attempts` increments to 5, token is consumed, returns `400`
- [ ] Wrong OTP on attempt 4: `attempts` increments to 4, token is NOT consumed yet
- [ ] Expired OTP returns `400 "OTP expired or not requested"`
- [ ] Already-consumed OTP returns `400 "OTP already used"`
- [ ] `resetPasswordWithOtp` rolls back entirely if `resetPassword` throws (e.g. user not found)
- [ ] `POST /api/auth/password/reset` calls `resetPasswordWithOtp` and returns `204`
- [ ] `POST /api/auth/otp/verify-reset` still works via the old `validatePasswordResetOtp` path

---


## S5 — JWT Parse Failure Fallback to -1L

### Summary
`AccessRequestResource.resolveUserIdFromJwt()` returns `-1L` on `NumberFormatException`, which is then passed as `approverUserId` to service methods, creating corrupt audit records. All four `@RolesAllowed` annotations are commented out, so unauthenticated requests reach this code path. Fix: change `resolveUserIdFromJwt` to return `null` on failure, throw `NotAuthorizedException` in `approve` and `reject` when the resolved ID is null, re-enable all `@RolesAllowed` annotations, and add a defence-in-depth guard in `AccessRequestService`.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/java/com/chatrah/school/resource/AccessRequestResource.java` | Modify | Fix `resolveUserIdFromJwt` return value; add null-check throws in `approve`/`reject`; re-enable all `@RolesAllowed` annotations |
| `src/main/java/com/chatrah/school/service/AccessRequestService.java` | Modify | Add `approverUserId > 0` guard in `approveRequest` and `rejectRequest` as defence-in-depth |

### New Files

| File | Reason |
|------|--------|
| _(none)_ | All changes are in existing classes |

### Method Signatures

```java
// AccessRequestResource.java — return type unchanged (already Long), sentinel value removed
private Long resolveUserIdFromJwt()

// AccessRequestService.java — signatures unchanged, guard added internally
public AccessRequestDTO approveRequest(Long id, Long approverUserId)
public AccessRequestDTO rejectRequest(Long id, Long approverUserId)
```

### Implementation Steps

1. In `AccessRequestResource.resolveUserIdFromJwt`, change the fallback return:
   ```java
   private Long resolveUserIdFromJwt() {
       if (jwt != null && jwt.getSubject() != null) {
           try {
               return Long.parseLong(jwt.getSubject());
           } catch (NumberFormatException ignored) {
           }
       }
       return null;  // was: return -1L
   }
   ```

2. In `approve`, update the null-check to throw `NotAuthorizedException`:
   ```java
   public void approve(@PathParam("id") Long id,
                       @QueryParam("approverUserId") Long approverUserId) {
       if (approverUserId == null) {
           approverUserId = resolveUserIdFromJwt();
       }
       if (approverUserId == null) {
           throw new NotAuthorizedException("Valid authentication required");
       }
       accessRequestService.approveRequest(id, approverUserId);
   }
   ```

3. Apply the same pattern to `reject`.

4. Re-enable all four `@RolesAllowed` annotations:
   ```java
   // requestFeeAccess
   @RolesAllowed(SecurityRoles.TEACHER)

   // listPending, approve, reject
   @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
   ```

5. In `AccessRequestService.approveRequest` and `rejectRequest`, add a guard at the top of each method:
   ```java
   if (approverUserId == null || approverUserId <= 0) {
       throw new IllegalArgumentException("approverUserId must be a valid positive user ID");
   }
   ```

6. Add `import jakarta.ws.rs.NotAuthorizedException;` to `AccessRequestResource`.

7. Evaluate removing `@QueryParam("approverUserId")` — confirm with the team whether any client passes it. If no clients use it, remove it in a follow-up PR to eliminate approver-ID spoofing entirely.

### Edge Cases

| Case | Handling |
|------|----------|
| No bearer token present (unauthenticated request) | `@RolesAllowed` rejects with `401` before the method body executes |
| Bearer token present but subject is non-numeric | `resolveUserIdFromJwt` returns `null`; `NotAuthorizedException` thrown (401) |
| Bearer token present but subject is `null` | Same as above — `null` check in `resolveUserIdFromJwt` handles it |
| `@QueryParam("approverUserId")` passed as `0` or negative | Service-level guard throws `IllegalArgumentException` (maps to 400) |
| `@RolesAllowed` re-enabled but Quarkus JWT extension not configured | Application fails at startup — correct; JWT extension must be active |

### Test Cases

- [ ] `resolveUserIdFromJwt` returns `null` when JWT subject is `"not-a-number"`
- [ ] `resolveUserIdFromJwt` returns `null` when no JWT is present
- [ ] `resolveUserIdFromJwt` returns the correct `Long` for a valid numeric subject
- [ ] `POST /api/access-requests/{id}/approve` with no token returns `401`
- [ ] `POST /api/access-requests/{id}/approve` with a STUDENT role token returns `403`
- [ ] `POST /api/access-requests/{id}/approve` with a valid PRINCIPAL token succeeds
- [ ] `AccessRequestService.approveRequest(id, -1L)` throws `IllegalArgumentException`
- [ ] `AccessRequestService.approveRequest(id, null)` throws `IllegalArgumentException`
- [ ] No `FeePayment` or `AccessRequest` record is persisted with `approvedBy = -1`

---

## D1 — handlePaymentSuccess Null-Guard

### Summary
`FeeService.initiateOnlinePayment` calls `feePaymentRepository.persist(payment)` without validating that `amount` and `mode` are non-null and valid. A null `amount` produces an opaque `PersistenceException` at flush time instead of a clean `400`. Fix: add Bean Validation annotations (`@NotNull`, `@Min(1)`) to `OnlineFeePaymentRequestDTO` and add an explicit inline guard in `initiateOnlinePayment` for the `amount > 0` business rule.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/java/com/chatrah/school/dto/OnlineFeePaymentRequestDTO.java` | Modify | Add `@NotNull` and `@Min(1)` to `amount`; add `@NotBlank` to `mode` |
| `src/main/java/com/chatrah/school/service/FeeService.java` | Modify | Add explicit null/range guard before entity construction in `initiateOnlinePayment` |
| `pom.xml` | Modify (if needed) | Confirm `quarkus-hibernate-validator` dependency is present; add if missing |

### New Files

| File | Reason |
|------|--------|
| _(none)_ | |

### Method Signatures

```java
// FeeService.java — signature unchanged, guard added at top of method body
@Transactional
public FeeSummaryDTO initiateOnlinePayment(Long studentId, OnlineFeePaymentRequestDTO request)
```

### Implementation Steps

1. Check `pom.xml` for `quarkus-hibernate-validator`. If absent, add:
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-hibernate-validator</artifactId>
   </dependency>
   ```

2. Annotate `OnlineFeePaymentRequestDTO`:
   ```java
   import jakarta.validation.constraints.Min;
   import jakarta.validation.constraints.NotBlank;
   import jakarta.validation.constraints.NotNull;

   @NotNull(message = "amount is required")
   @Min(value = 1, message = "amount must be at least 1")
   private Integer amount;

   @NotBlank(message = "mode is required")
   private String mode;
   ```

3. In `FeeService.initiateOnlinePayment`, add the guard immediately after the student null-check:
   ```java
   if (request.getAmount() == null || request.getAmount() <= 0) {
       throw new IllegalArgumentException("Amount must be a positive integer");
   }
   if (request.getMode() == null || request.getMode().isBlank()) {
       throw new IllegalArgumentException("Payment mode is required");
   }
   ```

4. Confirm the REST resource layer (`FeeResource`) applies `@Valid` to the request body parameter. If not, add `@Valid` to the `OnlineFeePaymentRequestDTO` parameter in the resource method.

5. Verify `PaymentService.createOrder` already has the `amount` guard (it does — `if (amount == null || amount <= 0) throw new IllegalArgumentException(...)`) — no change needed there.

### Edge Cases

| Case | Handling |
|------|----------|
| `amount = null` | Bean Validation (`@NotNull`) rejects at REST layer; service guard catches if called directly |
| `amount = 0` | `@Min(1)` rejects at REST layer; `<= 0` guard catches in service |
| `amount` negative | Same as `amount = 0` |
| `mode = null` | Service guard throws `IllegalArgumentException` |
| `mode = "  "` (whitespace) | `isBlank()` check in service guard catches it |
| Student not found | Existing `NotFoundException` fires before the amount guard — correct order |

### Test Cases

- [ ] `initiateOnlinePayment` with `amount = null` throws `IllegalArgumentException`
- [ ] `initiateOnlinePayment` with `amount = 0` throws `IllegalArgumentException`
- [ ] `initiateOnlinePayment` with `amount = -100` throws `IllegalArgumentException`
- [ ] `initiateOnlinePayment` with `mode = null` throws `IllegalArgumentException`
- [ ] `initiateOnlinePayment` with valid inputs persists a `FeePayment` with correct fields
- [ ] `POST /api/fees/{studentId}/pay` with `amount = null` returns `400` (Bean Validation)
- [ ] `POST /api/fees/{studentId}/pay` with `amount = 500` and valid `mode` returns `200`

---


## D2 — Webhook Idempotency

### Summary
Razorpay delivers webhooks at-least-once. Without idempotency, each duplicate delivery of `payment.captured` calls `handlePaymentSuccess` again, creating a second `FeePayment` row with the same `pgPaymentId` and double-crediting the student. Fix: add a `UNIQUE` constraint on `FeePayment.pgPaymentId` via a Flyway migration, and catch `ConstraintViolationException` in `PaymentResource.handleWebhook` to return `200 OK` on duplicates (so Razorpay stops retrying).

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/java/com/chatrah/school/entity/FeePayment.java` | Modify | Add `@Column(unique = true)` to `pgPaymentId` field |
| `src/main/java/com/chatrah/school/resource/PaymentResource.java` | Modify | Wrap `handlePaymentSuccess` call in try/catch for `ConstraintViolationException`; return `200` on duplicate |
| `src/main/resources/db/migration/V2__data_integrity_constraints.sql` | Create (or add to) | Add `UNIQUE` constraint on `fee_payments.pg_payment_id` |

### New Files

| File | Reason |
|------|--------|
| `src/main/resources/db/migration/V2__data_integrity_constraints.sql` | Flyway migration for all data integrity constraints (shared with D3, D4, D5) |

### Method Signatures

```java
// PaymentResource.java — signature unchanged, exception handling added
@POST
@Path("/webhook")
public Response handleWebhook(String payload, @HeaderParam("X-Razorpay-Signature") String signature)
```

### Implementation Steps

1. In `FeePayment`, annotate `pgPaymentId`:
   ```java
   @Column(unique = true)
   private String pgPaymentId;
   ```

2. In `V2__data_integrity_constraints.sql`, add (MySQL syntax — adjust for PostgreSQL if needed):
   ```sql
   ALTER TABLE fee_payments
       ADD CONSTRAINT uq_fee_payments_pg_payment_id UNIQUE (pg_payment_id);
   ```
   Use `IF NOT EXISTS` / `ADD CONSTRAINT IF NOT EXISTS` if the DB supports it, to make the migration re-runnable in dev.

3. In `PaymentResource.handleWebhook`, wrap the `handlePaymentSuccess` call:
   ```java
   try {
       paymentService.handlePaymentSuccess(orderId, paymentId, amount, signature, paidAt);
   } catch (jakarta.persistence.PersistenceException ex) {
       Throwable cause = ex.getCause();
       if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
           // Duplicate webhook delivery — already processed
           LOG.warning("Duplicate webhook for paymentId=" + paymentId + " — ignoring");
           return Response.ok().build();
       }
       throw ex;
   }
   ```
   Add a static logger to `PaymentResource`.

4. Also add an application-level pre-check in `PaymentService.handlePaymentSuccess` as a fast path:
   ```java
   FeePayment existing = feePaymentRepository.find("pgPaymentId", paymentId).firstResult();
   if (existing != null) {
       return; // already processed
   }
   ```
   This avoids the exception path in the common case, but the DB constraint remains the race-safe backstop.

5. Ensure `handlePaymentSuccess` sets `pgPaymentId` before calling `persist` — it already does (`payment.setPgPaymentId(paymentId)`).

### Edge Cases

| Case | Handling |
|------|----------|
| Two concurrent webhook deliveries for same `paymentId` | Both pass the pre-check; one wins the DB unique constraint; the other catches `ConstraintViolationException` and returns `200` |
| `pgPaymentId` is null (non-Razorpay payment row) | `UNIQUE` constraint allows multiple NULLs — cash payments unaffected |
| `handlePaymentSuccess` called with no matching `pgOrderId` (fallback path creates new row) | New row is created with `pgPaymentId` set; subsequent duplicate is caught by constraint |
| `ConstraintViolationException` wrapping differs across Hibernate versions | Catch `PersistenceException` and inspect `getCause()` chain — handles both direct and wrapped cases |

### Test Cases

- [ ] First webhook delivery for a `paymentId` creates exactly one `FeePayment` row with `status=SUCCESS`
- [ ] Second webhook delivery for the same `paymentId` returns `200 OK` and does NOT create a second row
- [ ] Concurrent duplicate webhook deliveries: exactly one row persisted, both return `200`
- [ ] `FeePayment` with `pgPaymentId = null` (cash payment) is not affected by the unique constraint
- [ ] `POST /api/payments/webhook` with valid signature and duplicate `paymentId` returns `200`
- [ ] Fee summary for the student shows the correct total (not doubled) after duplicate webhook

---

## D3 — initiateOnlinePayment Idempotency Key

### Summary
`FeeService.initiateOnlinePayment` uses `UUID.randomUUID()` for `transactionId`, so every call is unique — a client retry or double-click creates two `FeePayment` rows with `status=SUCCESS`. Fix: add an `idempotencyKey` (UUID) field to `OnlineFeePaymentRequestDTO` and `FeePayment`, add a `UNIQUE` constraint on `idempotency_key`, and check for an existing payment with that key before persisting. Also pass the key as the Razorpay `receipt` field and as the `X-Razorpay-Idempotency-Key` header when the real Razorpay integration is wired.

### Files to Change

| File | Change Type | Reason |
|------|-------------|--------|
| `src/main/java/com/chatrah/school/dto/OnlineFeePaymentRequestDTO.java` | Modify | Add `idempotencyKey` (UUID string) field |
| `src/main/java/com/chatrah/school/entity/FeePayment.java` | Modify | Add `idempotencyKey` field with `@Column(unique = true)` |
| `src/main/java/com/chatrah/school/service/FeeService.java` | Modify | Add pre-check in `initiateOnlinePayment`; set `idempotencyKey` on the entity |
| `src/main/java/com/chatrah/school/service/PaymentService.java` | Modify | Pass `idempotencyKey` as Razorpay `receipt` in `createOrder` |
| `src/main/resources/db/migration/V2__data_integrity_constraints.sql` | Modify | Add `UNIQUE` constraint on `fee_payments.idempotency_key` |

### New Files

| File | Reason |
|------|--------|
| _(none)_ | |

### Method Signatures

```java
// FeeService.java — signature unchanged, idempotency logic added internally
@Transactional
public FeeSummaryDTO initiateOnlinePayment(Long studentId, OnlineFeePaymentRequestDTO request)

// FeePaymentRepository.java — new finder
public FeePayment findByIdempotencyKey(String idempotencyKey)
```

### Implementation Steps

1. Add `idempotencyKey` to `OnlineFeePaymentRequestDTO`:
   ```java
   private String idempotencyKey; // UUID string, client-generated
   // getter + setter
   ```

2. Add `idempotencyKey` to `FeePayment` entity:
   ```java
   @Column(name = "idempotency_key", unique = true)
   private String idempotencyKey;
   // getter + setter
   ```

3. Add `findByIdempotencyKey` to `FeePaymentRepository`:
   ```java
   public FeePayment findByIdempotencyKey(String key) {
       return find("idempotencyKey", key).firstResult();
   }
   ```

4. In `FeeService.initiateOnlinePayment`, add the idempotency pre-check after the student lookup:
   ```java
   String idempotencyKey = request.getIdempotencyKey();
   if (idempotencyKey != null && !idempotencyKey.isBlank()) {
       FeePayment existing = feePaymentRepository.findByIdempotencyKey(idempotencyKey);
       if (existing != null) {
           // Idempotent retry — return the existing summary without re-persisting
           return computeFeeSummary(studentId);
       }
   }
   ```
   Then set the key on the new entity:
   ```java
   payment.setIdempotencyKey(idempotencyKey);
   ```

5. In `PaymentService.createOrder`, use the idempotency key as the Razorpay `receipt`:
   ```java
   String receipt = request.getIdempotencyKey() != null
       ? "IK-" + request.getIdempotencyKey().substring(0, 8)
       : "RCP-" + System.currentTimeMillis();
   orderRequest.put("receipt", receipt);
   ```

6. In `V2__data_integrity_constraints.sql`, add:
   ```sql
   ALTER TABLE fee_payments
       ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(36),
       ADD CONSTRAINT uq_fee_payments_idempotency_key UNIQUE (idempotency_key);
   ```

### Edge Cases

| Case | Handling |
|------|----------|
| Client does not send `idempotencyKey` (null) | Pre-check is skipped; behaviour is unchanged (no idempotency protection) |
| Same `idempotencyKey` sent twice concurrently | First request persists; second hits DB unique constraint — catch `ConstraintViolationException` and return the existing summary |
| `idempotencyKey` is not a valid UUID | Treated as an opaque string — no format validation required |
| Legitimate second payment (different installment) | Client must generate a new `idempotencyKey` UUID for each distinct payment intent |
| `idempotencyKey` reused across different students | DB constraint is per-row; a key used by student A cannot be reused by student B (unique globally) |

### Test Cases

- [ ] `initiateOnlinePayment` with a new `idempotencyKey` creates exactly one `FeePayment` row
- [ ] `initiateOnlinePayment` with the same `idempotencyKey` a second time returns the existing summary without creating a new row
- [ ] `initiateOnlinePayment` with `idempotencyKey = null` behaves as before (no idempotency check)
- [ ] Concurrent calls with the same `idempotencyKey`: exactly one row persisted
- [ ] `FeePayment` entity has `idempotencyKey` set correctly after a successful call
- [ ] `POST /api/fees/{studentId}/pay` with the same idempotency key twice returns `200` both times with the same fee summary

---
