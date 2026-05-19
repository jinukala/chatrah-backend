# Auth & Notifications — Phase 1 Brainstorm
**Date:** 2026-05-14  
**Project:** chatrah-backend (Quarkus / Jakarta EE)  
**Scope:** 5 topics — JWT Refresh/Revocation, Password Strength + Email Verification, OAuth OIDC, MFA/TOTP, SMS via MSG91

---

## Topic 1 — JWT Refresh Token + Revocation

### Problem
`AuthService.login()` issues a single short-lived JWT via `JwtService.generateToken()`. There is no refresh mechanism, so users are forced to re-login when the token expires. There is also no logout endpoint, meaning a stolen token remains valid until natural expiry. The `User` entity has no refresh-token relationship.

### Constraints
- Stack: Quarkus + SmallRye JWT (`@RolesAllowed`, `JsonWebToken`). Cannot swap to session-based auth.
- Tokens are stateless by design; revocation requires a server-side store.
- Must not break existing `/api/auth/login` response shape (`LoginResponseDTO`).
- DB: Hibernate/Panache with PostgreSQL (inferred from existing entity style).
- Refresh tokens must survive server restarts → must be persisted, not in-memory.

### Existing Patterns
- `OtpToken` entity: `user`, `code`, `expiresAt`, `consumed`, `attempts` — a clean template for a persisted token entity.
- `OtpTokenRepository.findActiveTokenForUser()` — pattern for querying non-expired, non-consumed tokens.
- `AuthResource` already has `@PermitAll` and `@RolesAllowed` patterns.
- `JwtService.generateToken(user)` is the single token-generation point — easy to extend.

### Options

**Option A — Opaque refresh token stored in DB (recommended)**  
- New entity `RefreshToken`: `id`, `tokenHash` (SHA-256 of the raw token), `user`, `issuedAt`, `expiresAt`, `revoked`, `deviceHint`.
- `POST /auth/refresh`: accept raw token → hash → lookup → validate → issue new access JWT + rotate refresh token.
- `POST /auth/logout`: accept raw token → hash → mark `revoked = true`.
- Token rotation on every refresh (old token revoked, new one issued).
- Pros: full revocation, rotation prevents replay, minimal JWT changes.
- Cons: one DB read per refresh call.

**Option B — JWT refresh token (signed, long-lived)**  
- Issue a second signed JWT with `type=refresh` claim and longer TTL.
- Revocation requires a blocklist table (same DB cost as Option A but less flexible).
- Pros: stateless validation possible without DB if blocklist is skipped.
- Cons: can't revoke without blocklist; blocklist grows unbounded without cleanup.

**Option C — Redis-backed token store**  
- Store refresh tokens in Redis with TTL-based auto-expiry.
- Pros: fast lookups, automatic cleanup.
- Cons: adds Redis as a new infrastructure dependency; overkill for a school portal.

### Decision
**Option A.** Opaque DB-persisted refresh token with rotation. Consistent with the existing `OtpToken` pattern, no new infrastructure, full revocation support.

### Open Questions
1. What should the refresh token TTL be? (Suggested: 30 days, configurable via `application.properties`.)
2. Should logout revoke all sessions (all devices) or only the presented token?
3. Should `LoginResponseDTO` return `refreshToken` in the body or set it as an `HttpOnly` cookie?
4. Do we need a `GET /auth/sessions` endpoint to list active refresh tokens per user?
5. Cleanup job for expired refresh tokens — Quarkus `@Scheduled`?

### Next Step
- Create `RefreshToken` entity mirroring `OtpToken` structure.
- Add `RefreshTokenRepository` with `findByTokenHash(hash)`.
- Extend `JwtService` with `generateRefreshToken(user): String`.
- Add `POST /auth/refresh` and `POST /auth/logout` to `AuthResource`.
- Extend `LoginResponseDTO` with `refreshToken` field.

---

## Topic 2 — Password Strength Validation + Email Verification on Registration

### Problem
`AuthService.hashPassword()` accepts any string — there is no strength policy. `AuthService.resetPassword()` also has no validation. The `User` entity has an `email` field but no `emailVerified` flag, so accounts can be created with unverified emails. There is no registration endpoint visible in `AuthResource` (users appear to be created by admins), but password reset is open to all.

### Constraints
- Password policy must be enforced at the service layer (not just the UI) to cover API clients.
- `OtpService` already uses `Mailer` for OTP emails — email verification can reuse the same infrastructure.
- `User.email` is `@Email`-validated by Bean Validation but not verified as reachable.
- Must not break existing `resetPassword` flow.
- Roles like STUDENT/TEACHER are created by PRINCIPAL/CLERK — verification flow must accommodate admin-created accounts where the user sets their own password on first login.

### Existing Patterns
- `OtpToken.Purpose` enum — add `EMAIL_VERIFICATION` purpose.
- `OtpService.sendPasswordResetOtp()` / `validatePasswordResetOtp()` — reusable pattern for email verification OTP.
- `BCrypt` already used in `AuthService` — no need to change hashing.
- Bean Validation (`@NotBlank`, `@Email`) already on `User` entity.

### Options

**Option A — Jakarta Bean Validation `@Pattern` on DTO + service-layer guard**  
- Add `@Pattern(regexp = "...")` to `RegisterRequestDTO.password` / `ResetPasswordRequestDTO.newPassword`.
- Also validate in `AuthService.hashPassword()` as a defence-in-depth check.
- Email verification: new `OtpToken.Purpose.EMAIL_VERIFICATION`, send on account creation, block login until verified (add `emailVerified` boolean to `User`).
- Pros: declarative, consistent with existing Bean Validation usage.
- Cons: regex in annotation is hard to read; error messages need customisation.

**Option B — Passay library for policy**  
- Add `org.passay:passay` dependency; define `PasswordValidator` bean with rules (length, uppercase, digit, special char).
- Inject into `AuthService`, call before hashing.
- Pros: rich rule set, human-readable violation messages.
- Cons: new dependency; may be overkill for a school portal.

**Option C — Simple inline validation method**  
- `PasswordPolicyService.validate(String password)` with explicit checks (length ≥ 8, ≥1 digit, ≥1 uppercase, ≥1 special char).
- Throws `WebApplicationException(400)` with a descriptive message.
- Pros: zero new dependencies, easy to adjust rules.
- Cons: slightly more boilerplate than Option A.

### Decision
**Option A for email verification** (reuse `OtpToken` + `Mailer`). **Option C for password strength** — keeps dependencies minimal and rules explicit. Combine: `PasswordPolicyService` validates, `@NotBlank` + `@Size(min=8)` on DTOs as first gate.

### Open Questions
1. Should login be blocked entirely until email is verified, or just show a warning?
2. For admin-created accounts (TEACHER/STUDENT), should a "set your password" email be sent instead of a verification OTP?
3. What is the exact password policy? (Proposed: min 8 chars, 1 uppercase, 1 digit, 1 special character.)
4. Should `resetPassword` also enforce the new policy? (Yes — same `PasswordPolicyService`.)
5. Is there a registration endpoint at all, or only admin-created users?

### Next Step
- Add `emailVerified` boolean to `User` entity (default `false` for new accounts, `true` for existing to avoid lockout).
- Add `EMAIL_VERIFICATION` to `OtpToken.Purpose`.
- Create `PasswordPolicyService.validate(String)`.
- Call `validate()` in `AuthService.resetPassword()` and any future registration flow.
- Add `POST /auth/email/verify` endpoint to `AuthResource`.

---

## Topic 3 — OAuth / OIDC Login (Google + Microsoft) via quarkus-oidc

### Problem
Login is currently username+password only (`AuthService.login()`). Schools increasingly want staff/teachers to log in with their Google Workspace or Microsoft 365 accounts. There is no OAuth callback endpoint, no OIDC provider configuration, and no mapping between an OIDC subject (`sub`) and a local `User` record.

### Constraints
- Quarkus `quarkus-oidc` extension supports multiple OIDC tenants — both Google and Microsoft can be configured.
- The existing `User` entity must be extended to store the OIDC `sub` (subject) per provider.
- Local password login must continue to work alongside OIDC.
- `JwtService.generateToken(user)` must still be the single source of app-level JWTs (OIDC tokens are not used directly for API calls — they are exchanged for an internal JWT).
- School portal context: OIDC login is likely only for TEACHER/PRINCIPAL roles, not students.

### Existing Patterns
- `AuthService.login()` returns `LoginResponseDTO` — OIDC callback should produce the same DTO.
- `User` entity has `email` field — can be used to match an OIDC identity to an existing user.
- `AuthResource` uses `@PermitAll` for public endpoints — OIDC callback will also be `@PermitAll`.

### Options

**Option A — quarkus-oidc with multiple named tenants + tenant resolver**  
- Add `quarkus-oidc` and `quarkus-oidc-client` extensions.
- Configure `quarkus.oidc.google.*` and `quarkus.oidc.microsoft.*` tenants in `application.properties`.
- Implement `TenantConfigResolver` to route `/auth/callback/google` and `/auth/callback/microsoft` to the correct tenant.
- On callback: extract `email` from ID token → find `User` by email → issue internal JWT.
- Add `oidcSub` + `oidcProvider` columns to `User` (or a separate `UserOidcIdentity` table for multiple providers per user).
- Pros: native Quarkus support, well-documented, handles token validation automatically.
- Cons: requires careful tenant routing; callback URL registration in Google/Microsoft consoles.

**Option B — Manual OAuth2 code exchange (no quarkus-oidc)**  
- Implement the authorization code flow manually using `quarkus-rest-client` to call Google/Microsoft token endpoints.
- Validate the ID token JWT manually.
- Pros: full control.
- Cons: significant boilerplate, reinvents what quarkus-oidc provides, harder to maintain.

**Option C — Auth0 / Keycloak as identity broker**  
- Route all OIDC through a third-party IdP (Auth0 or self-hosted Keycloak) that federates Google/Microsoft.
- The app only talks to one OIDC provider.
- Pros: simplifies app-side code; Keycloak handles provider federation.
- Cons: new infrastructure dependency; Keycloak self-hosting adds ops burden; Auth0 has cost implications.

### Decision
**Option A.** Native `quarkus-oidc` multi-tenant is the idiomatic Quarkus approach, avoids new infrastructure, and keeps the existing `User`/`JwtService` model intact.

### Open Questions
1. Should OIDC login auto-provision a new `User` record if the email is not found, or reject unknown emails?
2. Which roles are eligible for OIDC login? (Proposed: TEACHER, PRINCIPAL, CLERK — not STUDENT.)
3. Where does the frontend redirect after OIDC callback? (Need a `redirect_uri` strategy for SPA.)
4. Should the OIDC `sub` be stored per-provider in a separate `UserOidcIdentity` table to allow one user to link both Google and Microsoft?
5. Token refresh for OIDC sessions — use the internal refresh token (Topic 1) or OIDC refresh token?

### Next Step
- Add `quarkus-oidc` to `pom.xml`.
- Add `oidcSub`, `oidcProvider` to `User` entity (nullable, for OIDC-linked accounts).
- Create `OidcCallbackResource` with `GET /auth/callback/{provider}`.
- Implement `UserOidcLinkService` to find-or-reject user by email + issue internal JWT.
- Configure Google and Microsoft tenants in `application.properties` (with placeholder client IDs).

---

## Topic 4 — MFA / TOTP Second Factor

### Problem
After a successful password login, there is no second factor. For roles with elevated privileges (PRINCIPAL, SYS_ADMIN, CLERK), a TOTP second factor (Google Authenticator / Authy compatible) would significantly reduce account takeover risk. The current `login()` method returns a full JWT immediately — it needs to return a partial/pending state when MFA is required.

### Constraints
- TOTP (RFC 6238) is the standard; no SMS-based MFA to avoid dependency on MSG91 for security-critical flows.
- `User` entity needs `mfaEnabled` (boolean) and `mfaTotpSecret` (encrypted string) fields.
- The login flow must remain backward-compatible: users without MFA enabled get a JWT immediately (current behaviour).
- TOTP secrets must be stored encrypted at rest (not plain text in the DB).
- Quarkus has no built-in TOTP library — need a small dependency (e.g., `dev.samstevens.totp:totp`).

### Existing Patterns
- `AuthService.login()` is the single login entry point — needs to branch on `mfaEnabled`.
- `OtpToken` pattern could be reused for a short-lived "MFA pending" session token (issued after password check, before TOTP check).
- `JwtService.generateToken(user)` — only called after full authentication is confirmed.
- `BCrypt` already used for passwords; a similar approach (AES-256 encryption) needed for TOTP secrets.

### Options

**Option A — Two-step login with a short-lived "pending" token**  
- Step 1 `POST /auth/login`: verify password → if `mfaEnabled`, return `{ "mfaRequired": true, "pendingToken": "<short-lived opaque token>" }` instead of a full JWT.
- Step 2 `POST /auth/mfa/verify`: accept `pendingToken` + TOTP code → validate → issue full JWT.
- `pendingToken` stored in DB (reuse `OtpToken` with `Purpose.MFA_PENDING`), expires in 5 minutes.
- Pros: clean separation, pending token is opaque and short-lived, no partial JWT needed.
- Cons: two round trips for MFA users.

**Option B — Single endpoint with optional `totpCode` field**  
- `LoginRequestDTO` gains an optional `totpCode` field.
- If `mfaEnabled` and `totpCode` is absent → return 202 with `{ "mfaRequired": true }`.
- If `totpCode` present → validate and return JWT.
- Pros: single endpoint.
- Cons: awkward DTO design; 202 is non-standard for login; harder to implement rate-limiting on TOTP attempts separately.

**Option C — MFA via email OTP instead of TOTP**  
- Reuse `OtpService` to send a 6-digit code to `user.email` as the second factor.
- Pros: no authenticator app needed; reuses existing infrastructure.
- Cons: email is not a true second factor (same channel as password reset); weaker security; not standard MFA.

### Decision
**Option A.** Two-step login with a pending token is the most secure and cleanest design. It separates concerns, allows independent rate-limiting of TOTP attempts, and is consistent with the existing `OtpToken` pattern.

### Open Questions
1. Which roles require MFA? (Proposed: mandatory for SYS_ADMIN, optional for PRINCIPAL/CLERK, not available for STUDENT/TEACHER initially.)
2. How is the TOTP secret encrypted at rest? (Proposed: AES-256-GCM with a key from `application.properties` / AWS Secrets Manager.)
3. Should there be backup codes (one-time recovery codes) for lost authenticator access?
4. MFA enrollment flow: `POST /auth/mfa/enroll` returns a QR code URI; `POST /auth/mfa/confirm` verifies first TOTP code and activates MFA.
5. Should MFA be enforced at the policy level (admin forces it for a role) or remain user-opt-in?

### Next Step
- Add `dev.samstevens.totp:totp` dependency to `pom.xml`.
- Add `mfaEnabled` (boolean, default false), `mfaTotpSecret` (encrypted string) to `User` entity.
- Add `MFA_PENDING` to `OtpToken.Purpose`.
- Create `MfaService` with `enroll()`, `confirmEnrollment()`, `verifyTotp()`, `issuePendingToken()`.
- Modify `AuthService.login()` to branch on `mfaEnabled`.
- Add `POST /auth/mfa/enroll`, `POST /auth/mfa/confirm`, `POST /auth/mfa/verify` to `AuthResource`.

---

## Topic 5 — SMS Notifications via MSG91

### Problem
`NotificationService` has a clear `// TODO: call MSG91` stub in `sendFeePaymentNotification()` and all other notification methods. The SMS channel is silently ignored — `Notification` records are persisted with `Channel.EMAIL` even when a mobile number is present. There is no `SmsService`, no delivery callback handling, and no `SmsLog` entity to track delivery status. MSG91 returns a `requestId` per SMS that can be used to poll or receive webhook callbacks for delivery receipts.

### Constraints
- MSG91 REST API (v2): `POST https://api.msg91.com/api/v5/flow/` for template-based SMS; `POST https://api.msg91.com/api/v5/report/` for delivery reports.
- API key must be stored in `application.properties` / environment variable — never hardcoded.
- `Notification` entity already has `recipientMobile` and `status` fields — SMS delivery status should update the same record or a linked `SmsLog`.
- Quarkus REST client (`@RegisterRestClient`) is the idiomatic way to call external HTTP APIs.
- MSG91 delivery callbacks are webhooks — need a public `POST /webhooks/msg91/delivery` endpoint.
- Must not block the main transaction — SMS dispatch should be fire-and-forget or async.

### Existing Patterns
- `NotificationService` already persists a `Notification` record before sending — `SmsLog` can reference this.
- `Notification.Status` enum (`PENDING`, `SENT`, `FAILED`) — extend or reuse for SMS delivery states.
- `Mailer` injection pattern — `SmsService` follows the same `@ApplicationScoped` + `@Inject` pattern.
- `OtpService.sendOtpEmail()` — simple fire-and-forget send; SMS can follow the same pattern initially.

### Options

**Option A — Quarkus REST client (`@RegisterRestClient`) + `SmsLog` entity + webhook endpoint**  
- `Msg91Client` interface annotated with `@RegisterRestClient(configKey="msg91")` — calls MSG91 flow API.
- `SmsService`: inject `Msg91Client`, send SMS, persist `SmsLog` with MSG91 `requestId`.
- `SmsLog` entity: `id`, `notification` (FK), `mobile`, `requestId`, `status` (QUEUED/DELIVERED/FAILED/UNDELIVERED), `sentAt`, `deliveredAt`, `errorCode`.
- `POST /webhooks/msg91/delivery` (`@PermitAll`): receive MSG91 callback → find `SmsLog` by `requestId` → update status.
- `NotificationService` calls `SmsService.send(mobile, templateId, variables)` after persisting the `Notification`.
- Pros: idiomatic Quarkus, full delivery tracking, decoupled from `NotificationService`.
- Cons: requires MSG91 template setup; webhook URL must be publicly reachable (ngrok for dev).

**Option B — Direct `java.net.http.HttpClient` call, no separate `SmsLog`**  
- Inline HTTP call in `NotificationService`, update `Notification.status` directly.
- Pros: minimal new code.
- Cons: no delivery tracking, no retry, blocks the transaction thread, hard to test.

**Option C — Async via Quarkus `@Asynchronous` + in-memory queue, no webhook**  
- `SmsService.sendAsync()` annotated with `@Asynchronous` (CDI async).
- Poll MSG91 delivery report API on a `@Scheduled` job every few minutes.
- Pros: non-blocking, no public webhook needed.
- Cons: polling is less timely than webhooks; scheduled job adds complexity; still needs `SmsLog` for correlation.

### Decision
**Option A.** `@RegisterRestClient` is the Quarkus-native approach, `SmsLog` provides full auditability (important for fee payment confirmations), and the webhook gives real-time delivery status. Option C's polling is a fallback if the webhook URL cannot be made public in the deployment environment.

### Open Questions
1. Which MSG91 template IDs are needed? (At minimum: fee payment, attendance absent, OTP — need MSG91 account setup.)
2. Should SMS be sent for all `Notification.Type` values or only FEE_PAYMENT and ATTENDANCE_ABSENT?
3. Webhook authentication: MSG91 supports a secret key in the callback — should we validate it?
4. Should `SmsService` retry on failure? (Proposed: up to 2 retries with exponential backoff using `@Retry` from MicroProfile Fault Tolerance.)
5. Rate limits: MSG91 free tier has limits — should we queue bulk sends (e.g., event notifications to all students) to avoid throttling?
6. For OTP SMS (future): should `OtpService` call `SmsService` directly, or go through `NotificationService`?

### Next Step
- Create `SmsLog` entity with fields listed above; add `SmsLogRepository`.
- Create `Msg91Client` REST client interface (`@RegisterRestClient(configKey="msg91")`).
- Create `SmsService` with `send(String mobile, String templateId, Map<String,String> vars): String requestId`.
- Add `msg91.auth-key`, `msg91.sender-id`, `msg91.base-url` to `application.properties`.
- Replace the `// TODO` stub in `NotificationService` with `smsService.send(...)`.
- Add `POST /webhooks/msg91/delivery` to a new `WebhookResource`.
- Extend `Notification.Channel` enum with `SMS` and `BOTH` values if not already present.
