# Security Critical Fixes — Brainstorm Bundle
> Phase 1 (Brainstorm) · Compound Workflow · 2026-05-14

---

# Brainstorm: Razorpay Webhook Signature Verification
Date: 2026-05-14

## Problem
`PaymentResource.handleWebhook` calls `RazorpayWebhookUtil.verifySignature(payload, signature, webhookSecret)` and correctly rejects invalid signatures — **but only if `webhookSecret` is non-empty**. In `application.properties` the value is the placeholder `your_webhook_secret_here`, meaning in the current deployed state the secret is either the literal placeholder string or an empty/wrong value. If the secret is wrong, `verifySignature` will always return `false` and every legitimate webhook will be rejected (or, if someone accidentally sets it to an empty string, HMAC of empty key will match trivially). Additionally, `RazorpayWebhookUtil` silently swallows all exceptions and returns `false`, so a misconfigured `Mac` algorithm would silently pass all events through as invalid with no log trace.

Separately, `handlePaymentSuccess` is called only after the signature check passes, so the verification path itself is structurally correct — the gap is purely in configuration and silent failure modes.

## Constraints
- Must not break the existing `PaymentResource` endpoint contract (`POST /api/payments/webhook`).
- `RazorpayWebhookUtil` is a static utility — it has no CDI context; logging must be added carefully (use `java.util.logging` or pass a logger in).
- The webhook secret must come from a real secret store, not `application.properties` in source control.
- Razorpay retries webhooks up to 3× on non-2xx responses; the fix must return `200 OK` for already-processed events (idempotency, item #5) and `400` only for genuinely invalid signatures.
- Quarkus `@ConfigProperty` injection is the existing pattern for config — stay consistent.

## Existing Patterns That Apply
- `PaymentConfig` already injects `razorpay.webhook_secret` via `@ConfigProperty` and passes it to `RazorpayWebhookUtil.verifySignature`. The wiring is correct; only the value is wrong.
- `RazorpayWebhookUtil.constantTimeEquals` already uses a timing-safe comparison — keep it.
- `RazorpayWebhookUtil.hmacSha256` already uses `HmacSHA256` with `StandardCharsets.UTF_8` — correct algorithm per Razorpay docs.
- `PaymentResource` already reads `X-Razorpay-Signature` from the header — correct header name.

## Approaches Considered

### Option A — Fix configuration only (env-var injection)
Replace the placeholder in `application.properties` with `${RAZORPAY_WEBHOOK_SECRET}` so the real secret is injected at runtime from an environment variable or AWS Secrets Manager. Add a startup `@Observes StartupEvent` check that throws if the secret is blank. No code changes to `RazorpayWebhookUtil`.

**Pros**: Minimal change; the HMAC logic is already correct.  
**Cons**: Silent exception swallowing in `verifySignature` remains; a misconfigured JCE would silently reject all webhooks with no log.

### Option B — Add startup validation + exception logging in util
Same as Option A, plus: replace the bare `catch (Exception e) { return false; }` in `verifySignature` with `log.error("HMAC computation failed", e); return false;` so failures are observable. Add a `@PostConstruct` bean that calls `verifySignature("test", "test", webhookSecret)` to smoke-test the JCE path at startup.

**Pros**: Catches misconfiguration early; makes silent failures visible in logs.  
**Cons**: Slightly more code; `RazorpayWebhookUtil` is static so logging requires a static logger.

### Option C — Replace static util with a CDI bean
Convert `RazorpayWebhookUtil` to an `@ApplicationScoped` bean, inject `PaymentConfig`, and validate the secret at `@PostConstruct`. Inject the bean into `PaymentResource`.

**Pros**: Cleaner CDI design; easier to test with mocks; natural place for startup validation.  
**Cons**: Larger refactor; not strictly necessary for the security fix itself.

## Decision
**Option B** — fix the configuration (env-var placeholder) and add exception logging to `verifySignature`. This is the minimum safe change: the HMAC logic is already correct, the wiring is already correct, and the only real gaps are the placeholder secret and the silent exception path. Option C is a good follow-up refactor but is out of scope for a critical security fix.

Concrete changes:
1. `application.properties`: `razorpay.webhook_secret=${RAZORPAY_WEBHOOK_SECRET}` (and same for `key_id`, `key_secret` — see item #2).
2. `RazorpayWebhookUtil.verifySignature`: log the exception before returning `false`.
3. Add a `@PostConstruct` startup check in `PaymentConfig` (or a dedicated `WebhookSecretValidator` bean) that asserts `webhookSecret` is non-blank and non-placeholder.

## Open Questions
- Should the webhook endpoint return `200 OK` (silently ignore) or `400` when the signature is invalid? Razorpay docs recommend `200` to stop retries on permanently invalid requests, but `400` is more semantically correct for a bad request. Current code returns `400` — confirm with Razorpay dashboard retry policy.
- Is the raw request body preserved exactly as received (no JSON re-serialisation) before passing to `verifySignature`? JAX-RS may buffer the body; confirm the `String payload` parameter receives the raw bytes.
- Should we log the first 8 chars of the received signature on mismatch to aid debugging without leaking the full value?

## Next Step
Implement Phase 2 (Spec) for this item: write the implementation spec covering (a) `application.properties` env-var substitution, (b) startup validation bean, (c) logging in `RazorpayWebhookUtil`.

---

# Brainstorm: Hardcoded Secrets / Environment Separation
Date: 2026-05-14

## Problem
`application.properties` contains live credentials in plaintext committed to source control:
- `quarkus.datasource.password=root`
- `quarkus.mailer.password=Esangam@123` (a real Gmail app password)
- `razorpay.key_id=rzp_test_yourKeyHere` / `razorpay.key_secret=yourTestSecretHere` / `razorpay.webhook_secret=your_webhook_secret_here`

Even the "placeholder" Razorpay values are dangerous because they establish a pattern where developers copy-paste real keys into the same file. The Gmail password `Esangam@123` appears to be a real credential. There is no profile separation (`%dev` / `%prod`), so the same file is used in all environments. Any developer with repo access — or any CI log that prints config — leaks production credentials.

## Constraints
- Quarkus supports `%dev`, `%test`, `%prod` profile prefixes natively — use them.
- The team likely uses a simple deployment (single server or Docker); AWS Secrets Manager or Vault may be over-engineering for now, but the config must at least not be in source control.
- `.gitignore` at the project root does not exist (only `.idea/.gitignore` was found) — this must be created.
- Existing `PaymentConfig`, `quarkus.mailer.*`, and `quarkus.datasource.*` injection patterns must be preserved; only the values change.
- CI/CD pipeline (GitHub Actions, per Section 4.6 of the enhancement doc) will need the secrets injected as GitHub Secrets → env vars.

## Existing Patterns That Apply
- `PaymentConfig` already uses `@ConfigProperty` — the injection mechanism is correct; only the source of the value needs to change.
- Quarkus natively reads environment variables that follow the MicroProfile Config naming convention (dots → underscores, uppercase): `QUARKUS_DATASOURCE_PASSWORD`, `QUARKUS_MAILER_PASSWORD`, etc.
- The enhancement doc (Section 4.5) already shows `docker-compose.yml` with env-var injection for DB credentials — the pattern is established.

## Approaches Considered

### Option A — Environment variables only
Replace all secret values in `application.properties` with `${ENV_VAR_NAME}` references. Add a root `.gitignore` that excludes `.env` files. Provide a `.env.example` with placeholder values for developer onboarding. Developers create a local `.env` file (never committed).

**Pros**: Simple; no new dependencies; works with Docker, docker-compose, and GitHub Actions out of the box.  
**Cons**: Developers must manage local `.env` files; no secret rotation without redeployment; no audit trail of secret access.

### Option B — Quarkus profile separation + env vars
Same as Option A, but also add `%dev`, `%prod` profile blocks. Dev profile uses safe local defaults (local Postgres, Mailtrap/mock SMTP, Razorpay test keys from `.env`). Prod profile requires all secrets from env vars with no defaults. Add `quarkus.swagger-ui.always-include=false` under `%prod` (item #25).

**Pros**: Clear separation of concerns; prevents accidental use of dev config in prod; aligns with Quarkus best practices.  
**Cons**: Slightly more `application.properties` lines; developers must set `QUARKUS_PROFILE=dev` locally.

### Option C — AWS Secrets Manager / HashiCorp Vault
Integrate `quarkus-config-aws-secretsmanager` or `quarkus-vault`. Secrets are fetched at startup from a managed store. Supports rotation without redeployment.

**Pros**: Enterprise-grade; rotation support; audit trail.  
**Cons**: Significant operational overhead for a small team; requires AWS account setup or Vault cluster; overkill for Phase 1 stabilisation.

## Decision
**Option B** — profile separation + environment variables. This is the right balance for Phase 1: it eliminates credentials from source control immediately, establishes a clean dev/prod split, and requires no new infrastructure. Option C is the right long-term target (noted in the enhancement doc) but belongs in a later phase.

Concrete changes:
1. Create root `.gitignore` with entries for `.env`, `*.pem`, `src/main/resources/*.pem`, `target/`.
2. Restructure `application.properties` with `%dev.*` and `%prod.*` profile prefixes.
3. Replace all secret values with `${ENV_VAR:}` references (empty default forces explicit injection in prod).
4. Create `.env.example` documenting all required variables.
5. Rotate the Gmail app password immediately (it is a real credential in the file).
6. Add `%prod.quarkus.swagger-ui.always-include=false`.

## Open Questions
- Is the Gmail password `Esangam@123` a real app password or a placeholder? If real, it must be rotated immediately and the git history must be scrubbed (BFG Repo Cleaner or `git filter-repo`).
- Should `quarkus.datasource.password=root` be treated as already-leaked (local dev only) or is this the production DB password?
- What is the deployment target — bare metal, Docker, ECS? This determines how env vars are injected in production.
- Should `%test` profile use H2 in-memory or a Testcontainers Postgres? Testcontainers is preferred for integration fidelity.

## Next Step
Implement Phase 2 (Spec): write the restructured `application.properties` with profile blocks and env-var references, the `.gitignore`, and the `.env.example`. Flag the Gmail credential for immediate rotation.

---

# Brainstorm: PEM Keys on Filesystem
Date: 2026-05-14

## Problem
`privateKey_pkcs8.pem` and `publicKey.pem` are present in `src/main/resources/` and `target/classes/` — they are packaged into the application JAR and almost certainly committed to git. `application.properties` references them by filename (`smallrye.jwt.sign.key-location=privateKey_pkcs8.pem`, `mp.jwt.verify.publickey.location=publicKey.pem`). There is no root `.gitignore` (only `.idea/.gitignore` exists). If the private key has been pushed to a remote repository, any token signed with it must be considered compromised — an attacker with the private key can forge arbitrary JWTs for any user, including `SYS_ADMIN`.

## Constraints
- SmallRye JWT supports loading keys from: (a) classpath resource path, (b) filesystem absolute path, (c) inline PEM string via config property, (d) JWKS URL. All four are valid Quarkus config options.
- The public key can remain semi-public (it is needed by any service verifying tokens), but the private key must never be in source control or the JAR.
- Key rotation requires updating the config reference and redeploying — plan for this.
- The fix must not break `JwtService.generateToken()` which uses `Jwt.builder().sign()` — SmallRye JWT picks up the key from `smallrye.jwt.sign.key-location` automatically.
- Quarkus dev mode needs a key too — a dev-only key pair (never the production key) is acceptable in `src/test/resources` or generated at dev startup.

## Existing Patterns That Apply
- `JwtService` uses `Jwt.issuer(...).sign()` with no explicit key reference — SmallRye JWT resolves the key from config. Changing the config property value is sufficient; no Java code changes needed.
- `mp.jwt.verify.publickey.location` and `smallrye.jwt.sign.key-location` both accept absolute filesystem paths, not just classpath paths. This is the escape hatch for production.
- The enhancement doc (Section 2, item #3) explicitly calls for env-var or mounted secret at runtime.

## Approaches Considered

### Option A — Filesystem path via environment variable
Set `smallrye.jwt.sign.key-location` to an absolute path like `/run/secrets/privateKey_pkcs8.pem` (Docker secret mount) or `/etc/chatrah/keys/privateKey_pkcs8.pem`. Inject the path via an env var: `smallrye.jwt.sign.key-location=${JWT_PRIVATE_KEY_PATH:/run/secrets/privateKey_pkcs8.pem}`. Remove PEM files from `src/main/resources`. Add `*.pem` to `.gitignore`.

**Pros**: Simple; no code changes; works with Docker secrets and Kubernetes secrets volumes.  
**Cons**: Requires the key file to exist at the configured path at startup; ops must provision the file.

### Option B — Inline PEM string via config property
SmallRye JWT supports `smallrye.jwt.sign.key.location` pointing to a string that starts with `-----BEGIN`. Store the PEM content as an environment variable (`JWT_PRIVATE_KEY_PEM`) and reference it: `smallrye.jwt.sign.key-location=${JWT_PRIVATE_KEY_PEM}`.

**Pros**: No filesystem dependency; works cleanly with Docker env vars, GitHub Actions secrets, and AWS Secrets Manager string values.  
**Cons**: PEM content in env vars can be tricky with newlines — must use `\n` escaping or base64 encoding; slightly harder to rotate.

### Option C — JWKS endpoint (public key only) + filesystem private key
Expose `GET /api/.well-known/jwks.json` for public key distribution. Keep private key on filesystem (Option A). This is relevant for the microservices phase where other services need to verify tokens without sharing a config file.

**Pros**: Standard OIDC pattern; enables future microservice JWT verification without shared config.  
**Cons**: Adds an endpoint; overkill for Phase 1 monolith stabilisation.

## Decision
**Option A for immediate fix** (filesystem path via env var, remove from classpath), with **Option B as the preferred production target** (inline PEM via env var / secrets manager). For Phase 1:

1. Add `*.pem` and `src/main/resources/*.pem` to root `.gitignore`.
2. Generate a new RSA key pair (the existing keys are potentially compromised).
3. Move PEM files out of `src/main/resources` — do not package them in the JAR.
4. Set `smallrye.jwt.sign.key-location=${JWT_PRIVATE_KEY_PATH}` and `mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_PATH}` in `application.properties`.
5. For dev profile: generate a throwaway key pair at project setup and document in `.env.example`.
6. Scrub git history if keys were ever committed (use `git filter-repo --path src/main/resources/privateKey_pkcs8.pem --invert-paths`).

## Open Questions
- Have the PEM files been pushed to a remote repository? Run `git log --all --full-history -- src/main/resources/privateKey_pkcs8.pem` to confirm. If yes, all existing JWTs are compromised and the key must be rotated immediately.
- What is the key algorithm and size? RSA-2048 minimum; RSA-4096 or EC P-256 preferred.
- For the dev profile, should a key pair be auto-generated at startup (SmallRye JWT can do this with `smallrye.jwt.sign.key.generate=true` in dev mode) or should developers generate one manually?
- Is there a plan for key rotation cadence in production?

## Next Step
Implement Phase 2 (Spec): document the key rotation procedure, the new config properties, and the `.gitignore` entries. Include the `git filter-repo` command for history scrubbing.

---

# Brainstorm: OTP Reset Race Condition — Merge resetPassword + markOtpUsed into One Transaction
Date: 2026-05-14

## Problem
The password reset flow involves three separate service calls, each in its own `@Transactional` boundary:

1. `OtpService.validatePasswordResetOtp(username, otp)` — validates the OTP but does **not** mark it consumed.
2. `AuthService.resetPassword(username, newPassword)` — updates the password hash.
3. `OtpService.markOtpUsed(username)` — marks the OTP as consumed.

Between steps 2 and 3 (or between steps 1 and 2), a second concurrent request with the same valid OTP can pass `validatePasswordResetOtp` because the OTP is still unconsumed. This creates a window where an attacker who intercepts the OTP can race the legitimate user and reset the password to their own value, or where the OTP can be replayed to reset the password a second time.

Additionally, `validatePasswordResetOtp` increments `attempts` **before** checking the code (item #10), giving users effectively 4 attempts instead of 5.

## Constraints
- Quarkus uses Narayana JTA for transactions; `@Transactional` on a method creates a new transaction (or joins an existing one with `REQUIRED` semantics by default).
- `OtpService` and `AuthService` are separate CDI beans — calling one from the other within a transaction is fine as long as both are `@ApplicationScoped` and the caller is `@Transactional`.
- The OTP token must be locked for update (pessimistic lock or optimistic lock with version) to prevent the race condition at the DB level, not just at the application level.
- The resource layer (wherever `resetPassword` is called) must be updated to call the merged method instead of the three separate calls.
- `markOtpUsed` is also called from other places — check all call sites before removing it.

## Existing Patterns That Apply
- `AuthService.resetPassword` is already `@Transactional`.
- `OtpService.validatePasswordResetOtp` and `OtpService.markOtpUsed` are already `@Transactional`.
- Quarkus Panache supports `LockModeType.PESSIMISTIC_WRITE` via `find(...).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult()` — use this on the OTP token fetch.
- The existing `OtpTokenRepository.findActiveTokenForUser` can be extended to accept a lock mode parameter.

## Approaches Considered

### Option A — Merge into a single method in OtpService
Add a new method `OtpService.validateAndConsumeOtp(username, otp)` that, within a single `@Transactional` block: (1) fetches the OTP with a pessimistic write lock, (2) checks attempts (fix item #10: check code first, then increment on failure), (3) marks consumed, and returns. Then add a separate `AuthService.resetPasswordWithOtp(username, otp, newPassword)` that calls `validateAndConsumeOtp` and then updates the password — all within one transaction.

**Pros**: Atomic; eliminates the race window; pessimistic lock prevents concurrent OTP use; fixes item #10 as a side effect.  
**Cons**: Requires updating all call sites; `OtpService` and `AuthService` must not create circular dependencies.

### Option B — Move all logic into AuthService
Add `AuthService.resetPasswordWithOtp(username, otp, newPassword)` that inlines the OTP validation, consumption, and password update in one `@Transactional` method. `OtpService` retains `sendPasswordResetOtp` only.

**Pros**: Single class owns the entire reset flow; no cross-bean transaction complexity.  
**Cons**: `AuthService` grows; OTP logic is duplicated or `AuthService` must call `OtpTokenRepository` directly, bypassing `OtpService` encapsulation.

### Option C — Optimistic locking with `@Version`
Add a `@Version` field to `OtpToken`. The first transaction to commit wins; the second gets an `OptimisticLockException` which is caught and translated to a `409 Conflict`. No pessimistic lock needed.

**Pros**: Better scalability under low contention; no DB-level lock held.  
**Cons**: Requires retry logic or clear error handling at the resource layer; `OptimisticLockException` must not be swallowed; more complex error path.

## Decision
**Option A** — new `validateAndConsumeOtp` method with pessimistic write lock, called from a new `AuthService.resetPasswordWithOtp`. This is the cleanest fix: it keeps OTP logic in `OtpService`, keeps auth logic in `AuthService`, and the pessimistic lock is the simplest correct solution for a low-traffic endpoint like password reset.

Also fix item #10 in the same change: in `validateAndConsumeOtp`, check `token.getCode().equals(otp)` first, then increment `attempts` only on failure.

Concrete changes:
1. `OtpTokenRepository`: add `findActiveTokenForUserWithLock(userId, purpose)` using `PESSIMISTIC_WRITE`.
2. `OtpService`: add `validateAndConsumeOtp(username, otp)` — single `@Transactional` method with lock, attempt check (code first), mark consumed.
3. `AuthService`: add `resetPasswordWithOtp(username, otp, newPassword)` — calls `otpService.validateAndConsumeOtp` then updates password, all in one `@Transactional` scope.
4. Update the resource layer to call `resetPasswordWithOtp` instead of the three separate calls.
5. Keep `markOtpUsed` for any other call sites but deprecate it for the reset flow.

## Open Questions
- Where is the resource endpoint that calls `resetPassword` + `markOtpUsed`? Need to find all call sites (likely `AuthResource` or `OtpResource`) to update them.
- Does `OtpTokenRepository.findActiveTokenForUser` already filter by `consumed=false AND expiresAt > NOW()`? Confirm the query to ensure the lock is applied to the right row.
- Should the pessimistic lock timeout be configured (e.g., 5 seconds) to avoid indefinite blocking?
- Is `OtpToken` using optimistic locking (`@Version`) already? If so, Option C may already be partially in place.

## Next Step
Implement Phase 2 (Spec): write the method signatures, the updated `OtpTokenRepository` query with lock, and the resource-layer call site changes.

---

# Brainstorm: JWT Parse Failure Fallback to -1L in AccessRequestResource
Date: 2026-05-14

## Problem
`AccessRequestResource.resolveUserIdFromJwt()` returns `-1L` when the JWT subject cannot be parsed as a `Long` (e.g., malformed token, missing subject claim, or `NumberFormatException`). This sentinel value is then passed as `approverUserId` to `accessRequestService.approveRequest(id, -1L)` and `rejectRequest(id, -1L)`. A user ID of `-1` is almost certainly invalid in the database, but the service may still persist the approval/rejection record with `approvedBy = -1`, creating corrupt audit data. Worse, if the `@RolesAllowed` annotations are commented out (which they are — all four endpoints have `//  @RolesAllowed(...)` commented out), an unauthenticated request with no JWT at all will reach `resolveUserIdFromJwt`, get `-1L`, and successfully approve or reject access requests.

The `resolveTeacherIdFromJwt()` method correctly returns `null` on failure and the caller throws `ForbiddenException` — but `resolveUserIdFromJwt()` does not follow the same pattern.

## Constraints
- The `@RolesAllowed` annotations are commented out — this is a separate issue (access control must be re-enabled), but the JWT parse fix must be safe even while they remain commented out.
- `JsonWebToken jwt` is injected via CDI; in Quarkus, if no bearer token is present, `jwt` may be a non-null proxy with a null subject — the null check `jwt.getSubject() != null` is already present but insufficient.
- The fix must not break the `@QueryParam("approverUserId")` fallback path — if the caller explicitly provides `approverUserId`, the JWT is not consulted.
- `AccessRequestService.approveRequest` and `rejectRequest` should also validate that `approverUserId > 0` as a defence-in-depth measure.

## Existing Patterns That Apply
- `resolveTeacherIdFromJwt()` in the same class already returns `null` on failure, and the caller throws `ForbiddenException("No teacherId present in token")` — this is the correct pattern to replicate.
- `jakarta.ws.rs.NotAuthorizedException` (maps to HTTP 401) is the correct exception when the token is absent or unparseable. `ForbiddenException` (403) is correct when the token is valid but lacks the required claim.
- The enhancement doc (item #18) explicitly says: "Throw `401 Unauthorized` on JWT parse failure; never use a sentinel ID."

## Approaches Considered

### Option A — Return null and throw in caller (mirror resolveTeacherIdFromJwt pattern)
Change `resolveUserIdFromJwt()` to return `null` on failure. In `approve()` and `reject()`, after the `resolveUserIdFromJwt()` call, throw `NotAuthorizedException` if the result is null (when `approverUserId` query param was also not provided).

**Pros**: Consistent with the existing `resolveTeacherIdFromJwt` pattern in the same class; minimal change.  
**Cons**: Two separate null checks needed in `approve` and `reject`.

### Option B — Throw directly in resolveUserIdFromJwt
Change `resolveUserIdFromJwt()` to throw `NotAuthorizedException("Valid JWT with numeric subject required")` instead of returning `-1L`. Remove the `if (approverUserId == null)` guard and always require JWT resolution.

**Pros**: Fail-fast; no sentinel value ever escapes; simpler caller code.  
**Cons**: Removes the `@QueryParam("approverUserId")` override path — but that path is itself a security issue (callers can pass any user ID as the approver, bypassing JWT entirely).

### Option C — Fix resolveUserIdFromJwt + re-enable @RolesAllowed + remove QueryParam override
Return `null` from `resolveUserIdFromJwt` on failure, throw `NotAuthorizedException` in callers, re-enable `@RolesAllowed` annotations, and remove the `@QueryParam("approverUserId")` parameter entirely (the approver ID should always come from the JWT, never from the request body).

**Pros**: Comprehensive fix; eliminates the entire class of approver-ID spoofing; correct security posture.  
**Cons**: Larger change; removing `@QueryParam("approverUserId")` may break existing clients if any are using it.

## Decision
**Option A as the immediate fix** (return `null`, throw `NotAuthorizedException` in callers) to eliminate the `-1L` sentinel. **Option C as the follow-up** in the same PR: re-enable `@RolesAllowed` and remove the `@QueryParam("approverUserId")` override — the approver must always be the authenticated user from the JWT.

Concrete changes:
1. `resolveUserIdFromJwt()`: change `return -1L` to `return null`, update return type to `Long` (already `Long` — no type change needed).
2. `approve()` and `reject()`: after `approverUserId = resolveUserIdFromJwt()`, add `if (approverUserId == null) throw new NotAuthorizedException("Valid authentication required");`.
3. Re-enable `@RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})` on `approve` and `reject`.
4. Re-enable `@RolesAllowed(SecurityRoles.TEACHER)` on `requestFeeAccess`.
5. Consider removing `@QueryParam("approverUserId")` after confirming no clients depend on it.
6. Add a guard in `AccessRequestService.approveRequest` / `rejectRequest`: `if (approverUserId == null || approverUserId <= 0) throw new IllegalArgumentException(...)` as defence-in-depth.

## Open Questions
- Are there any existing clients (frontend, tests, Postman collections) that pass `approverUserId` as a query param? If yes, a migration period is needed before removing it.
- Why were `@RolesAllowed` annotations commented out? Was it for testing convenience or a deliberate decision? This must be confirmed before re-enabling.
- Does `AccessRequestService.approveRequest` currently validate that the `approverUserId` corresponds to a real user with the right role, or does it trust the caller?
- Should the approved/rejected record store the approver's username (from JWT `upn` claim) rather than just the numeric ID, for better audit readability?

## Next Step
Implement Phase 2 (Spec): write the exact code diff for `resolveUserIdFromJwt`, the two caller methods, and the `@RolesAllowed` re-enablement. Document the `@QueryParam` deprecation plan.
