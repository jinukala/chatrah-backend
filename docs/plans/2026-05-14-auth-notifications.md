# Auth & Notifications — Implementation Plans
**Date:** 2026-05-14
**Status:** DRAFT
**Phase:** 2 — Plan
**Scope:** 5 features — JWT Refresh/Revocation, Password Strength + Email Verification, OAuth OIDC, MFA/TOTP, SMS via MSG91

---

## Feature 1 — JWT Refresh Token + Revocation

### Summary
Extend the login flow to issue an opaque refresh token alongside the existing access JWT. Add `POST /auth/refresh` to rotate the refresh token and issue a new access JWT, and `POST /auth/logout` to revoke the presented refresh token. Mirrors the existing `OtpToken` pattern: persisted entity, SHA-256 hashed storage, expiry-based validity.

### Files to Change
| File | Change |
|------|--------|
| `entity/OtpToken.java` | No change — `RefreshToken` is a separate entity |
| `dto/LoginResponseDTO.java` | Add `refreshToken` field + getter/setter |
| `service/AuthService.java` | Inject `RefreshTokenService`; call `issueRefreshToken(user)` in `login()`; add `refresh()` and `logout()` delegates |
| `resource/AuthResource.java` | Add `POST /auth/refresh` and `POST /auth/logout` endpoints |
| `security/JwtService.java` | No change — access token generation is unchanged |
| `src/main/resources/application.properties` | Add `app.refresh-token.ttl-days=30` |

### New Files
| File | Purpose |
|------|---------|
| `entity/RefreshToken.java` | Persisted refresh token entity |
| `repository/RefreshTokenRepository.java` | `findByTokenHash(hash)`, `revokeAllForUser(userId)` |
| `service/RefreshTokenService.java` | Issue, validate, rotate, revoke refresh tokens |
| `dto/RefreshRequestDTO.java` | `{ "refreshToken": "..." }` request body |

### Method Signatures

```java
// RefreshTokenService
public String issueRefreshToken(User user);                        // returns raw token, persists hashed
public LoginResponseDTO refresh(String rawToken);                  // rotate: revoke old, issue new pair
public void revoke(String rawToken);                               // logout: mark revoked=true

// RefreshTokenRepository
public RefreshToken findByTokenHash(String sha256Hex);
public long revokeAllForUser(Long userId);                         // for "logout all devices"

// AuthResource additions
@POST @Path("/refresh") @PermitAll
public LoginResponseDTO refresh(RefreshRequestDTO req);

@POST @Path("/logout") @PermitAll
public void logout(RefreshRequestDTO req);
```

### Implementation Steps
1. **`RefreshToken` entity** — fields: `id` (Long, PK), `tokenHash` (String, unique, not null), `user` (@ManyToOne), `issuedAt` (LocalDateTime), `expiresAt` (LocalDateTime), `revoked` (boolean, default false), `deviceHint` (String, nullable). `@PrePersist` sets `issuedAt`.
2. **`RefreshTokenRepository`** — `findByTokenHash`: query `tokenHash = ?1 and revoked = false and expiresAt > now()`. `revokeAllForUser`: bulk update `revoked = true where user.id = ?1`.
3. **`RefreshTokenService.issueRefreshToken(user)`** — generate 32-byte `SecureRandom` token, Base64-URL encode → raw token. SHA-256 hash → store in `RefreshToken`. TTL read from `@ConfigProperty(name="app.refresh-token.ttl-days", defaultValue="30")`. Return raw token.
4. **`RefreshTokenService.refresh(rawToken)`** — hash input → `findByTokenHash` → validate not null/not revoked/not expired → mark old token `revoked=true` → call `issueRefreshToken(token.getUser())` → call `jwtService.generateToken(user)` → return new `LoginResponseDTO`.
5. **`RefreshTokenService.revoke(rawToken)`** — hash input → `findByTokenHash` → if found, set `revoked=true`. If not found, silently succeed (idempotent).
6. **`AuthService.login()`** — after building `LoginResponseDTO`, call `refreshTokenService.issueRefreshToken(user)` and set `response.setRefreshToken(rawToken)`.
7. **`LoginResponseDTO`** — add `private String refreshToken` with getter/setter.
8. **`AuthResource`** — add two endpoints; both `@PermitAll`, `@Consumes(APPLICATION_JSON)`.
9. **`application.properties`** — add `app.refresh-token.ttl-days=30`.
10. **Cleanup job (optional, deferred)** — `@Scheduled(every="24h")` in a `TokenCleanupService` to delete rows where `expiresAt < now()`.

### Edge Cases
- **Token not found / already revoked:** `refresh()` throws `WebApplicationException(401, "Invalid or expired refresh token")`.
- **Expired token presented:** `findByTokenHash` query excludes expired rows → same 401 response.
- **Replay after rotation:** old token is revoked on first use; second use returns 401. Consider revoking the entire user's tokens on replay detection (theft signal).
- **Concurrent refresh calls:** two simultaneous refreshes with the same token — second call finds the token already revoked → 401. Acceptable; client retries login.
- **`login()` transaction:** `issueRefreshToken` must run inside the same `@Transactional` as `login` or in its own transaction — annotate `RefreshTokenService` methods with `@Transactional`.
- **Existing users after deploy:** `LoginResponseDTO.refreshToken` will be null for tokens issued before this change — clients must handle null gracefully.

### Test Cases
1. `login()` returns non-null `refreshToken` alongside `token`.
2. `POST /auth/refresh` with valid token returns new `token` and new `refreshToken`; old refresh token is revoked.
3. `POST /auth/refresh` with the old (rotated) token returns 401.
4. `POST /auth/refresh` with an expired token returns 401.
5. `POST /auth/logout` with valid token returns 204; subsequent `refresh` with same token returns 401.
6. `POST /auth/logout` with unknown token returns 204 (idempotent).
7. `POST /auth/refresh` with a token belonging to an inactive user returns 401.

---

## Feature 2 — Password Strength Validation + Email Verification on Registration

### Summary
Add a `PasswordPolicyService` that enforces a minimum strength rule (length ≥ 8, ≥1 uppercase, ≥1 digit, ≥1 special character) and call it from `AuthService.resetPassword()`. Add an `emailVerified` flag to `User` and a new `OtpToken.Purpose.EMAIL_VERIFICATION` to send a verification OTP when an account is created, blocking login until verified.

### Files to Change
| File | Change |
|------|--------|
| `entity/User.java` | Add `emailVerified` (boolean, default false) |
| `entity/OtpToken.java` | Add `EMAIL_VERIFICATION` to `Purpose` enum |
| `service/AuthService.java` | Call `passwordPolicyService.validate(newPassword)` in `resetPassword()`; check `emailVerified` in `login()` |
| `service/OtpService.java` | Add `sendEmailVerificationOtp(String username)` and `verifyEmailOtp(String username, String otp)` |
| `resource/AuthResource.java` | Add `POST /auth/email/verify` and `POST /auth/email/send-verification` |

### New Files
| File | Purpose |
|------|---------|
| `service/PasswordPolicyService.java` | `validate(String password)` — throws 400 with message on violation |

### Method Signatures

```java
// PasswordPolicyService
public void validate(String rawPassword);   // throws WebApplicationException(400) on failure

// OtpService additions
public void sendEmailVerificationOtp(String username);
public void verifyEmailOtp(String username, String otp);  // marks consumed + sets emailVerified=true

// AuthResource additions
@POST @Path("/email/send-verification") @PermitAll
public void sendEmailVerification(ForgotPasswordRequestDTO req);  // reuse existing DTO

@POST @Path("/email/verify") @PermitAll
public void verifyEmail(VerifyOtpRequestDTO req);                 // reuse existing DTO
```

### Implementation Steps
1. **`User.emailVerified`** — add `private boolean emailVerified = false` with getter/setter. Migration: existing rows default to `false`; add a Flyway/Liquibase migration or set `columnDefinition = "boolean default false"`. To avoid locking out existing users, a one-time data migration sets `emailVerified = true` for all pre-existing active accounts.
2. **`OtpToken.Purpose`** — add `EMAIL_VERIFICATION` to the enum.
3. **`PasswordPolicyService.validate()`** — check: `length >= 8`, contains `[A-Z]`, contains `[0-9]`, contains `[^A-Za-z0-9]`. Collect all violations into a single message string; throw `WebApplicationException(message, 400)` if any fail.
4. **`AuthService.resetPassword()`** — call `passwordPolicyService.validate(newPassword)` before `hashPassword()`.
5. **`AuthService.login()`** — after credential check, if `!user.isEmailVerified()` throw `WebApplicationException("Email not verified. Check your inbox.", 403)`.
6. **`OtpService.sendEmailVerificationOtp(username)`** — same pattern as `sendPasswordResetOtp`: invalidate existing `EMAIL_VERIFICATION` OTP, generate new 6-digit code, persist `OtpToken`, send email via `Mailer`.
7. **`OtpService.verifyEmailOtp(username, otp)`** — validate OTP (reuse validation logic), mark consumed, set `user.setEmailVerified(true)`, persist user.
8. **`AuthResource`** — add two new `@PermitAll` endpoints.
9. **Admin-created accounts** — when PRINCIPAL/CLERK creates a user (outside this scope), the caller should invoke `sendEmailVerificationOtp` so the user receives a verification email. Document this as a follow-up integration point.

### Edge Cases
- **Existing users locked out:** data migration sets `emailVerified = true` for all rows created before this feature ships.
- **Email not set on User:** if `user.getEmail()` is null, skip email verification requirement (or throw a setup error). Log a warning.
- **OTP resend rate limiting:** `sendEmailVerificationOtp` invalidates the previous token before issuing a new one — prevents OTP flooding but doesn't rate-limit by time. Add a cooldown check (e.g., `createdAt > now() - 1 minute`) if needed.
- **Password policy on existing `resetPassword` callers:** any integration test that resets to a weak password will now fail — update test fixtures.
- **Special characters in password regex:** ensure the regex `[^A-Za-z0-9]` correctly matches all intended special chars including spaces.

### Test Cases
1. `validate("short1A!")` passes; `validate("alllower1!")` fails (no uppercase); `validate("ALLUPPER1!")` fails (no lowercase — note: policy only requires uppercase, not lowercase, adjust if needed); `validate("NoDigit!")` fails; `validate("NoSpecial1")` fails; `validate("short")` fails (length).
2. `resetPassword()` with weak password throws 400 with descriptive message.
3. `login()` with unverified email returns 403.
4. `sendEmailVerificationOtp()` persists an `OtpToken` with purpose `EMAIL_VERIFICATION` and sends an email.
5. `verifyEmailOtp()` with correct OTP sets `user.emailVerified = true` and marks token consumed.
6. `verifyEmailOtp()` with wrong OTP increments attempts; after max attempts, token is consumed and further attempts fail.
7. `login()` succeeds after email is verified.


---

## Feature 3 — OAuth / OIDC Login (Google + Microsoft) via quarkus-oidc

### Summary
Add native `quarkus-oidc` multi-tenant support for Google and Microsoft login. A `GET /auth/callback/{provider}` endpoint receives the authorization code, exchanges it via quarkus-oidc, extracts the email from the ID token, finds the matching local `User`, and issues an internal JWT + refresh token using the existing `JwtService` and `RefreshTokenService`. OIDC tokens are never used directly for API calls.

### Files to Change
| File | Change |
|------|--------|
| `entity/User.java` | Add `oidcSub` (String, nullable), `oidcProvider` (String, nullable) |
| `resource/AuthResource.java` | No change — OIDC callback goes in a new resource |
| `pom.xml` | Add `quarkus-oidc` extension |
| `src/main/resources/application.properties` | Add Google + Microsoft tenant config (placeholder values) |

### New Files
| File | Purpose |
|------|---------|
| `resource/OidcCallbackResource.java` | `GET /auth/callback/{provider}` — receives OIDC redirect, issues internal JWT |
| `service/OidcUserService.java` | Find local `User` by email from ID token; optionally link `oidcSub` |
| `config/OidcTenantResolver.java` | `TenantConfigResolver` impl routing `google`/`microsoft` to correct tenant config |

### Method Signatures

```java
// OidcUserService
public LoginResponseDTO loginWithOidc(String email, String sub, String provider);
// throws WebApplicationException(403) if email not found in users table

// OidcCallbackResource
@GET @Path("/auth/callback/{provider}") @PermitAll
public Response callback(@PathParam("provider") String provider,
                         @QueryParam("code") String code,
                         @Context SecurityContext sec);
// Extracts email from injected JsonWebToken (OIDC ID token), delegates to OidcUserService,
// redirects to frontend with internal JWT as query param or fragment

// OidcTenantResolver implements TenantConfigResolver
public OidcTenantConfig resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext);
```

### Implementation Steps
1. **`pom.xml`** — add `<artifactId>quarkus-oidc</artifactId>` to dependencies.
2. **`application.properties`** — configure two named tenants:
   ```properties
   quarkus.oidc.google.auth-server-url=https://accounts.google.com
   quarkus.oidc.google.client-id=${GOOGLE_CLIENT_ID}
   quarkus.oidc.google.credentials.secret=${GOOGLE_CLIENT_SECRET}
   quarkus.oidc.google.application-type=web-app

   quarkus.oidc.microsoft.auth-server-url=https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0
   quarkus.oidc.microsoft.client-id=${AZURE_CLIENT_ID}
   quarkus.oidc.microsoft.credentials.secret=${AZURE_CLIENT_SECRET}
   quarkus.oidc.microsoft.application-type=web-app
   ```
3. **`OidcTenantResolver`** — inspect `RoutingContext.request().path()`: if path contains `/callback/google` return `google` tenant config; `/callback/microsoft` returns `microsoft`. Return null for all other paths (uses default tenant or no OIDC).
4. **`User` entity** — add `oidcSub` (String, nullable, column `oidc_sub`) and `oidcProvider` (String, nullable, column `oidc_provider`) with getters/setters.
5. **`OidcUserService.loginWithOidc(email, sub, provider)`** — `userRepository.find("email", email).firstResult()`. If null → throw 403 ("No account found for this email. Contact your administrator."). If found and `oidcSub` is null → set `oidcSub = sub`, `oidcProvider = provider`, persist (link on first OIDC login). Call `jwtService.generateToken(user)` + `refreshTokenService.issueRefreshToken(user)` → return `LoginResponseDTO`.
6. **`OidcCallbackResource`** — inject `@IdToken JsonWebToken idToken`. Extract `idToken.getClaim("email")` and `idToken.getSubject()`. Call `oidcUserService.loginWithOidc(...)`. Redirect to frontend URL (configurable via `app.oidc.frontend-redirect-url`) with token as a URL fragment (`#token=...`).
7. **`SecurityRoles`** — no change; OIDC users get the same role from the local `User` record.

### Edge Cases
- **Email not found:** return 403 with a clear message — do not auto-provision accounts (school portal, admin-controlled user base).
- **Email mismatch between providers:** a user with a Google account and a Microsoft account sharing the same email will link to the same `User` row. If different emails, they are separate users.
- **`oidcSub` conflict:** if a different user already has the same `sub` for the same provider, log a warning and reject (should not happen in practice).
- **`isActive = false`:** check `user.getIsActive()` in `OidcUserService` and throw 401 if inactive.
- **Email not verified by provider:** Google/Microsoft ID tokens include `email_verified` claim — check it and reject if false.
- **Frontend redirect URL:** must be configurable per environment (dev vs prod). Never embed the internal JWT in server logs.
- **CSRF on callback:** quarkus-oidc handles state parameter validation automatically for `web-app` application type.

### Test Cases
1. `OidcUserService.loginWithOidc()` with a known email returns a valid `LoginResponseDTO` with token and refreshToken.
2. `OidcUserService.loginWithOidc()` with an unknown email throws 403.
3. `OidcUserService.loginWithOidc()` with an inactive user throws 401.
4. First OIDC login links `oidcSub` and `oidcProvider` on the `User` record.
5. Second OIDC login with same sub does not duplicate or overwrite the link.
6. `OidcTenantResolver` returns `google` tenant for `/auth/callback/google` and `microsoft` for `/auth/callback/microsoft`.
7. Callback with `email_verified=false` in ID token is rejected.


---

## Feature 4 — MFA / TOTP Second Factor

### Summary
Add optional TOTP-based MFA to the login flow. `AuthService.login()` branches: if `mfaEnabled`, it issues a short-lived opaque pending token (stored as `OtpToken` with purpose `MFA_PENDING`) and returns `{ mfaRequired: true, pendingToken: "..." }` instead of a full JWT. A second call to `POST /auth/mfa/verify` validates the TOTP code, consumes the pending token, and returns the full `LoginResponseDTO`. Enrollment is handled via `POST /auth/mfa/enroll` + `POST /auth/mfa/confirm`.

### Files to Change
| File | Change |
|------|--------|
| `entity/User.java` | Add `mfaEnabled` (boolean, default false), `mfaTotpSecret` (String, nullable, encrypted) |
| `entity/OtpToken.java` | Add `MFA_PENDING` to `Purpose` enum |
| `service/AuthService.java` | Branch on `user.isMfaEnabled()` in `login()`; inject `MfaService` |
| `resource/AuthResource.java` | Add `POST /auth/mfa/enroll`, `POST /auth/mfa/confirm`, `POST /auth/mfa/verify` |
| `pom.xml` | Add `dev.samstevens.totp:totp` dependency |

### New Files
| File | Purpose |
|------|---------|
| `service/MfaService.java` | Enroll, confirm enrollment, verify TOTP, issue/consume pending token |
| `dto/MfaEnrollResponseDTO.java` | `{ "qrCodeUri": "...", "secret": "..." }` |
| `dto/MfaVerifyRequestDTO.java` | `{ "pendingToken": "...", "totpCode": "..." }` |
| `dto/MfaPendingResponseDTO.java` | `{ "mfaRequired": true, "pendingToken": "..." }` |

### Method Signatures

```java
// MfaService
public MfaEnrollResponseDTO enroll(Long userId);
// Generates TOTP secret, encrypts, stores on User (mfaEnabled stays false until confirmed)

public void confirmEnrollment(Long userId, String totpCode);
// Validates first TOTP code against stored secret; sets mfaEnabled=true

public MfaPendingResponseDTO issuePendingToken(User user);
// Persists OtpToken(purpose=MFA_PENDING, code=<random 32-byte token>, expiresAt=+5min)
// Returns DTO with raw pending token

public LoginResponseDTO verifyTotp(String rawPendingToken, String totpCode);
// Finds OtpToken by code+purpose, validates not expired/consumed,
// validates TOTP code against user's secret, consumes token, returns full LoginResponseDTO

// AuthResource additions
@POST @Path("/mfa/enroll")
@RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
public MfaEnrollResponseDTO enroll();

@POST @Path("/mfa/confirm")
@RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
public void confirmEnrollment(VerifyOtpRequestDTO req);  // reuse DTO, otp field = totpCode

@POST @Path("/mfa/verify") @PermitAll
public LoginResponseDTO verifyMfa(MfaVerifyRequestDTO req);
```

### Implementation Steps
1. **`pom.xml`** — add `dev.samstevens.totp:totp:1.5.1` (pinned version).
2. **`User` entity** — add `mfaEnabled` (boolean, default false), `mfaTotpSecret` (String, nullable). Secret stored AES-256-GCM encrypted; encryption key from `@ConfigProperty(name="app.mfa.encryption-key")` (base64-encoded 32-byte key, stored in env/secrets manager).
3. **`OtpToken.Purpose`** — add `MFA_PENDING`.
4. **`MfaService.enroll(userId)`** — use `dev.samstevens.totp.secret.DefaultSecretGenerator` to generate a secret. Encrypt with AES-256-GCM. Store encrypted secret on `user.mfaTotpSecret` (do NOT set `mfaEnabled=true` yet). Build a `QrData` URI using `dev.samstevens.totp.qr.QrDataFactory`. Return `MfaEnrollResponseDTO` with the QR URI and plain secret (for manual entry).
5. **`MfaService.confirmEnrollment(userId, totpCode)`** — decrypt `user.mfaTotpSecret`, use `dev.samstevens.totp.code.DefaultCodeVerifier` to validate `totpCode`. If valid, set `user.mfaEnabled = true`, persist. If invalid, throw 400.
6. **`MfaService.issuePendingToken(user)`** — generate 32-byte `SecureRandom` token, Base64-URL encode. Persist as `OtpToken(purpose=MFA_PENDING, code=rawToken, user=user, expiresAt=now+5min)`. Return `MfaPendingResponseDTO`.
7. **`MfaService.verifyTotp(rawPendingToken, totpCode)`** — find `OtpToken` by `code = rawPendingToken AND purpose = MFA_PENDING AND consumed = false AND expiresAt > now()`. If not found → 401. Decrypt `user.mfaTotpSecret`, verify `totpCode`. If invalid → increment attempts (max 5), throw 400. If valid → mark token consumed → call `jwtService.generateToken(user)` + `refreshTokenService.issueRefreshToken(user)` → return `LoginResponseDTO`.
8. **`AuthService.login()`** — after successful password check: if `user.isMfaEnabled()` → call `mfaService.issuePendingToken(user)` and return the `MfaPendingResponseDTO` cast to a `Response` with status 200. Otherwise proceed as today. The return type of `login()` changes from `LoginResponseDTO` to `Response` (or use a common sealed interface/`Object` — prefer `Response` for flexibility).
9. **`application.properties`** — add `app.mfa.encryption-key=${MFA_ENCRYPTION_KEY}`.

### Edge Cases
- **TOTP clock skew:** `DefaultCodeVerifier` allows ±1 time step (30s window) by default — sufficient for most devices.
- **Lost authenticator:** no backup codes in this plan (deferred). Document that SYS_ADMIN can set `mfaEnabled=false` and clear `mfaTotpSecret` via a protected admin endpoint.
- **Re-enrollment:** calling `enroll()` again overwrites `mfaTotpSecret` but leaves `mfaEnabled=true` until `confirmEnrollment` succeeds — this means a failed re-enrollment disables MFA. Mitigate by storing the new secret in a separate `pendingTotpSecret` field until confirmed.
- **Pending token brute-force:** max 5 TOTP attempts per pending token (tracked via `OtpToken.attempts`); after that, token is consumed and user must re-login with password.
- **`AuthResource.login()` return type change:** existing clients expect `LoginResponseDTO` JSON. When MFA is not enabled the shape is unchanged. When MFA is enabled the shape changes to `MfaPendingResponseDTO` — document this in API changelog.
- **Encryption key rotation:** not in scope for this plan; note as a future concern.

### Test Cases
1. `enroll()` returns a non-null QR URI and secret; `user.mfaEnabled` remains false.
2. `confirmEnrollment()` with a valid TOTP code sets `user.mfaEnabled = true`.
3. `confirmEnrollment()` with an invalid code throws 400; `mfaEnabled` stays false.
4. `login()` for an MFA-enabled user returns `{ mfaRequired: true, pendingToken: "..." }` (no JWT).
5. `login()` for a non-MFA user returns `LoginResponseDTO` with JWT (unchanged behaviour).
6. `verifyTotp()` with valid pending token + correct TOTP returns full `LoginResponseDTO`.
7. `verifyTotp()` with expired pending token returns 401.
8. `verifyTotp()` with wrong TOTP 5 times consumes the pending token; 6th attempt returns 401.
9. `verifyTotp()` with a pending token belonging to a different user returns 401.


---

## Feature 5 — SMS Notifications via MSG91

### Summary
Replace the `// TODO: call MSG91` stubs in `NotificationService` with a real SMS dispatch path. A `Msg91Client` REST client calls the MSG91 Flow API. `SmsService` wraps the client, persists a `SmsLog` record with the MSG91 `requestId`, and is called from `NotificationService` after the email path. A new `POST /webhooks/msg91/delivery` endpoint receives delivery callbacks and updates `SmsLog.status`. SMS dispatch is fire-and-forget (`@Asynchronous`) to avoid blocking the main transaction.

### Files to Change
| File | Change |
|------|--------|
| `service/NotificationService.java` | Inject `SmsService`; replace `// TODO` stubs with `smsService.send(...)` calls |
| `entity/Notification.java` | Add `SMS` and `BOTH` to `Channel` enum (if not present) |
| `src/main/resources/application.properties` | Add MSG91 config keys |
| `pom.xml` | No new dependency — uses existing `quarkus-rest-client-reactive` |

### New Files
| File | Purpose |
|------|---------|
| `entity/SmsLog.java` | Tracks each SMS dispatch: requestId, status, timestamps, error |
| `repository/SmsLogRepository.java` | `findByRequestId(String)` |
| `gateway/Msg91Client.java` | `@RegisterRestClient` interface for MSG91 Flow API |
| `service/SmsService.java` | Orchestrates send + persist `SmsLog`; `@Asynchronous` |
| `resource/WebhookResource.java` | `POST /webhooks/msg91/delivery` — delivery status callback |
| `dto/Msg91SendRequestDTO.java` | Request body for MSG91 Flow API |
| `dto/Msg91DeliveryCallbackDTO.java` | Incoming webhook payload from MSG91 |

### Method Signatures

```java
// SmsService
@Asynchronous
public CompletableFuture<Void> send(Notification notification, String templateId, Map<String, String> variables);
// Calls Msg91Client, persists SmsLog with requestId, updates notification.channel if SMS sent

// Msg91Client (@RegisterRestClient(configKey="msg91"))
@POST @Path("/api/v5/flow/")
Msg91SendResponseDTO send(@HeaderParam("authkey") String authKey, Msg91SendRequestDTO body);

// SmsLogRepository
public SmsLog findByRequestId(String requestId);

// WebhookResource
@POST @Path("/webhooks/msg91/delivery") @PermitAll
public Response deliveryCallback(Msg91DeliveryCallbackDTO payload);
```

### Implementation Steps
1. **`SmsLog` entity** — fields: `id` (Long, PK), `notification` (@ManyToOne, nullable — OTP SMS won't have a Notification), `mobile` (String), `requestId` (String, unique, index), `status` (enum: QUEUED, DELIVERED, FAILED, UNDELIVERED), `sentAt` (LocalDateTime), `deliveredAt` (LocalDateTime, nullable), `errorCode` (String, nullable), `createdAt` (LocalDateTime, `@PrePersist`).
2. **`SmsLogRepository`** — `findByRequestId`: `find("requestId", requestId).firstResult()`.
3. **`Msg91Client`** — `@RegisterRestClient(configKey="msg91")` interface. Single method: `POST /api/v5/flow/` with `authkey` header and JSON body. Response contains `{ "type": "success", "request_id": "..." }`.
4. **`Msg91SendRequestDTO`** — fields: `template_id` (String), `recipients` (List of `{ "mobiles": "...", "var1": "...", ... }`). Matches MSG91 v5 Flow API shape.
5. **`application.properties`**:
   ```properties
   quarkus.rest-client.msg91.url=https://api.msg91.com
   msg91.auth-key=${MSG91_AUTH_KEY}
   msg91.sender-id=${MSG91_SENDER_ID}
   msg91.fee-payment-template-id=${MSG91_FEE_TEMPLATE_ID}
   msg91.attendance-template-id=${MSG91_ATTENDANCE_TEMPLATE_ID}
   ```
6. **`SmsService.send(notification, templateId, variables)`** — annotated `@Asynchronous` (MicroProfile `@Asynchronous` from `quarkus-smallrye-fault-tolerance` or CDI async). Build `Msg91SendRequestDTO` from `notification.getRecipientMobile()` + variables. Call `msg91Client.send(authKey, body)`. On success: persist `SmsLog(status=QUEUED, requestId=response.requestId)`. On exception: persist `SmsLog(status=FAILED, errorCode=e.getMessage())`. Add `@Retry(maxRetries=2, delay=2, delayUnit=SECONDS)` from MicroProfile Fault Tolerance.
7. **`NotificationService`** — inject `SmsService`. In `sendFeePaymentNotification()`, after the email block, add:
   ```java
   if (notification.getRecipientMobile() != null && !notification.getRecipientMobile().isBlank()) {
       smsService.send(notification, feePaymentTemplateId, buildFeeVars(student, payment, summary));
   }
   ```
   Apply the same pattern to `sendAttendanceAbsentNotification()`. Leave `sendExamResultNotification()` and `sendEventNotificationToStudents()` as email-only for now (configurable later).
8. **`WebhookResource.deliveryCallback(payload)`** — find `SmsLog` by `payload.requestId`. If found, update `status` from payload (`delivered` → `DELIVERED`, `failed` → `FAILED`, etc.) and set `deliveredAt`. Return 200. If not found, return 200 anyway (idempotent — MSG91 may retry). Optionally validate a shared secret header (`X-Msg91-Secret`) against `${MSG91_WEBHOOK_SECRET}`.
9. **`Notification.Channel` enum** — add `SMS` and `BOTH` if not already present. `NotificationService` can set `channel = BOTH` when both email and SMS are sent.

### Edge Cases
- **Mobile number format:** MSG91 requires numbers without `+` prefix and with country code (e.g., `919876543210`). Normalize in `SmsService` before sending: strip `+`, prepend `91` if not already present.
- **`@Asynchronous` and `@Transactional`:** the async method runs in a new thread outside the caller's transaction. `SmsLog` persistence must use its own `@Transactional` within `SmsService`. Do not pass managed JPA entities across transaction boundaries — pass IDs or plain values instead.
- **MSG91 template not configured:** if `templateId` is null/blank, log a warning and skip SMS rather than sending a malformed request.
- **Webhook URL not publicly reachable in dev:** use ngrok or skip webhook in dev profile. Add `%dev.msg91.webhook-enabled=false` guard in `WebhookResource`.
- **Duplicate webhook callbacks:** MSG91 may send the same callback multiple times. `findByRequestId` + idempotent status update handles this.
- **Bulk event notifications:** `sendEventNotificationToStudents()` loops over all students — each SMS is a separate async call. For large cohorts this may hit MSG91 rate limits. Deferred: batch the recipients into a single MSG91 request (MSG91 Flow API supports multiple recipients in one call).
- **`@Retry` and idempotency:** MSG91 Flow API is not guaranteed idempotent — two retries may send two SMS messages. Mitigate by checking `SmsLog` for an existing QUEUED entry before retrying, or accept the low-probability duplicate for now.

### Test Cases
1. `SmsService.send()` with a valid mobile calls `Msg91Client.send()` and persists a `SmsLog` with status `QUEUED` and a non-null `requestId`.
2. `SmsService.send()` when `Msg91Client` throws an exception persists `SmsLog` with status `FAILED`.
3. `NotificationService.sendFeePaymentNotification()` with a student that has a mobile number triggers `smsService.send()`.
4. `NotificationService.sendFeePaymentNotification()` with no mobile number does not call `smsService.send()`.
5. `POST /webhooks/msg91/delivery` with a known `requestId` updates `SmsLog.status` to `DELIVERED` and sets `deliveredAt`.
6. `POST /webhooks/msg91/delivery` with an unknown `requestId` returns 200 without error.
7. Mobile number normalisation: `+91 98765 43210` → `919876543210` before sending.
8. `@Retry`: on first call failure, `Msg91Client` is called up to 3 times total (1 + 2 retries).
