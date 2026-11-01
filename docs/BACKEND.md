# Backend development

The first Play release includes cloud, collaboration and AI. The backend is an in-progress Kotlin/Ktor service with optional account flows connected to the Android UI for development. It is not publicly deployed. No hosting or AI subscription has been purchased.

## Run locally

From the repository root, with the same JDK used for Android:

```powershell
.\gradlew.bat :server:test
.\gradlew.bat :server:postgresTest
.\gradlew.bat :server:run
```

Defaults bind only `127.0.0.1:8080`. Development uses an H2 file under `server/build/`, in PostgreSQL compatibility mode. Verification/reset messages go to `server/build/dev-mail.jsonl`, which is ignored by Git. This file contains development action codes and must stay private. They expire, are single-use and are never included in public API responses. Do not use real email/password data in local testing.

The same 36 backend cases pass against H2 and a real isolated PostgreSQL 18.6 process, including Unicode content, competing writes, delta replay/conflicts, invitation-history privacy, concurrent owner quotas, strict request/authentication envelopes and schema 1 → 2 migration preserving original receipts. `postgresTest` starts a test-only loopback process with a fresh schema per fixture, then shuts it down; it does not install a system service or use production data. Native test binaries stay out of the production distribution and APK. This establishes local database behavior, not deployed TLS, operational migration qualification or backup/restore. Actual SMTP delivery has not been exercised.

## Android connection

Debug builds use `http://127.0.0.1:8080`; forward that loopback service with `adb reverse tcp:8080 tcp:8080`. Only debug resources permit loopback cleartext traffic. Release builds allow HTTPS, with the origin supplied at build time using `'-Pwhiteboard.cloudOrigin=https://api.example.com'` (quote the full argument in PowerShell). An unset release origin shows account unavailability; such a build is not a finished cloud release. API/provider keys never belong in this setting or in the APK.

Settings → Whiteboard account provides registration, email confirmation/resend, login, password reset, logout and current-password-confirmed deletion. Local boards are never reassigned or automatically uploaded by logging in. Session credentials are sealed with AES-256-GCM and Android Keystore, with app/server identity authenticated as associated data. Password and code inputs are excluded from saved instance state. Offline logout clears local credentials and reports that remote revocation could not be confirmed.

`tools/Test-CloudAccounts.ps1` prepares a disposable local account, owns a development server and forwards it to an emulator. The explicit Android check covers login, secure restore, logout/deletion and a real document protocol round trip: fractional-coordinate strokes, styled text, grouped objects, an idempotent retry and change-log replay. Build `:server:installDist :app:assembleDebug :app:assembleDebugAndroidTest` first. It refuses to replace an existing signed-in cloud session and tears down its server after the check. The separate `:data:connectedDebugAndroidTest` task exercises actual Keystore persistence, tampering rejection, server-origin binding and portable format compatibility. The optional cloud integration case is skipped in the default app suite unless its disposable-account argument is supplied.

## API v1 currently implemented

All request bodies use UTF-8 JSON. There is no browser cookie authentication or CORS allowance. Authorized calls require `Authorization: Bearer <accessToken>`. Tokens must never appear in URLs. Responses use stable error codes and do not expose SQL or exception messages. API responses disable caching. Request bodies are bounded while streaming, even without Content-Length; compressed bodies are refused. Identity bodies are limited to 4 KiB and document requests to 8 MiB, with a JSON nesting limit of 32.

| Method and path | Behavior |
|---|---|
| GET `/health` | Process health, no secrets/configuration |
| POST `/v1/auth/register` | `email`, `password`; generic accepted response; email confirmation required |
| POST `/v1/auth/verify` | Consume confirmation `code` |
| POST `/v1/auth/resend-verification` | Generic response for `email` |
| POST `/v1/auth/forgot-password` | Generic response for `email` |
| POST `/v1/auth/reset-password` | `code`, `password`; revoke all account sessions |
| POST `/v1/auth/login` | `email`, `password`; access/refresh pair |
| POST `/v1/auth/refresh` | Rotate `refreshToken`; replay revokes the family |
| POST `/v1/auth/logout` | Revoke current token family |
| GET `/v1/account` | Current account ID/email |
| DELETE `/v1/account` | Current `password` required; cascade owned boards, memberships, sessions and action codes |
| GET `/v1/boards?after=...` | Page of up to 100 accessible summaries; `next` cursor or null |
| POST `/v1/boards` | `operationId` UUID and `document`; idempotent creation, distinct server ID, initial revision 0 |
| GET `/v1/boards/{id}` | Shared `document`, `revision`, current `role` |
| PUT `/v1/boards/{id}` | `operationId` UUID, `expectedRevision`, `document`; returns revision |
| POST `/v1/boards/{id}/operations` | `operationId`, `baseRevision`, versioned `delta`; merge independent changes and persist receipt/tombstones/log atomically |
| GET `/v1/boards/{id}/changes?after=...` | Bounded contiguous operation cursor; pre-invitation history is excluded |
| DELETE `/v1/boards/{id}` | Owner only; permanent server deletion |
| POST `/v1/boards/{id}/invites` | Owner supplies recipient `email`, `role`; receives single-use invitation `code` to share |
| DELETE `/v1/boards/{id}/invites` | Owner revokes pending invitations |
| POST `/v1/invites/accept` | Verified recipient accepts `code` |
| PUT `/v1/boards/{id}/members/{member}` | Owner changes existing member `role` |
| DELETE `/v1/boards/{id}/members/{member}` | Owner revokes membership and matching pending invites |

Passwords must contain 12–128 characters and at most 512 UTF-8 bytes. Email addresses currently use normalized ASCII syntax. Roles are OWNER, EDITOR, COMMENTER, VIEWER; only owner/editor can commit. Comment storage/UI is not implemented yet. Invitations last seven days and require an account with the matching verified address. The caller shares the returned invitation code manually; the API does not send invitation email.

Board limits are provisional: 200 owned boards, 50 members per board, 100 active invitations per board and an 8 MiB encoded document. Each owner has an aggregate 64 MiB encoded snapshot/change-payload budget, up to 100,000 commit receipts, 50,000 retired page/object identities and 10,000 lifetime creation receipts. These are safety defaults pending the hosting budget and retention plan. Owner quota locking serializes concurrent editors across different boards. Attachment documents are explicitly rejected until private transfer/reference validation exists. Personal folders, favorites, Trash and viewports are stripped from shared snapshots. Snapshot commits reject stale revisions; delta operations merge independent edits against object fingerprints. A repeated operation UUID/actor/body returns its original revision. Conflicts preserve existing content. The cursor returns up to 100 operations with an aggregate payload bound; a legacy snapshot gap requires a fresh snapshot. See SYNCHRONIZATION.md for limits and pending client integration.

JSON is checked before object/number construction: nesting is limited to 32, numeric literals to 128 characters and absolute exponents to 400. Comments, single-quoted strings and other nonstandard JSON are rejected. Error responses contain stable codes; client exceptions retain only sanitized code/status metadata. Encoded or duplicate JDBC options cannot override the required verified TLS mode. SQL migration resources use enforced LF line endings to keep checksums consistent across operating systems.

## Production configuration

Set secrets through the deployment secret store. `.env` files and keystores must not enter version control. A `.env` file is not automatically loaded by this server.

| Variable | Requirement |
|---|---|
| WHITEBOARD_ENV | `production` (other accepted value: `development`) |
| WHITEBOARD_HOST / WHITEBOARD_PORT | Bind behind a configured HTTPS ingress; defaults `0.0.0.0` / `8080` in production |
| WHITEBOARD_JDBC_URL | PostgreSQL JDBC URL with `sslmode=verify-full`; credentials supplied separately |
| WHITEBOARD_DB_USER / WHITEBOARD_DB_PASSWORD | Dedicated database identity, secret password |
| WHITEBOARD_PUBLIC_ORIGIN | HTTPS origin without credentials, path, query or fragment |
| WHITEBOARD_SMTP_HOST / WHITEBOARD_SMTP_PORT | STARTTLS SMTP endpoint; default port 587 |
| WHITEBOARD_SMTP_USER / WHITEBOARD_SMTP_PASSWORD | SMTP credentials |
| WHITEBOARD_MAIL_FROM | Verified sender address |

Production refuses an H2 database, absent credentials/mail settings and an HTTP public origin. SMTP requires STARTTLS and certificate identity checks. Apply schema changes through new checksummed migrations, never edit an already installed migration. PostgreSQL startup migration takes an advisory lock. H2 is strictly a development convenience.

The process has a bounded connection pool, password-hash concurrency gate, request concurrency gate, a separate two-document concurrency bound and per-process request/identity/email limits. Forwarded client addresses are not trusted automatically. A production ingress must enforce distributed limits, request timeouts and the chosen trusted proxy policy; the current per-process limiter is not a substitute.

## Outstanding release gates

- Full registration/reset device-flow and SMTP validation; explicit board publishing and account-switch behavior for synchronized copies. Login, secure restore, logout and deletion pass in the local Android/backend integration check.
- Private assets, shared editor integration, full conflict recovery, two-client reconnect delivery, shared Undo/causal restoration and presence. Server deltas/tombstones/replay, typed Android requests, durable initial publishing/preview UI, a transactional device outbox and account-scoped bounded background scheduling are implemented; see SYNCHRONIZATION.md for verification and remaining gates.
- Comments, activity, data export, account deletion retention policy and recovery/backups.
- Durable email delivery/retry and tests against the chosen SMTP service; abuse handling across replicas.
- Deployed PostgreSQL TLS/operational migration qualification, encrypted backups and restore drill. The local PostgreSQL suite already passes.
- AI provider adapter, explicit content consent, validated previewable outputs, durable quotas/cost caps and provider outage handling.
- Hosting/region/budget, public privacy policy/Data safety, monitoring, operational ownership and signing/Play checks.

No public release is considered complete until these gates and the applicable TASKS.md items are satisfied.
