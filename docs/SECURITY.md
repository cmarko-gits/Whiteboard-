> Istorijski izveštaj za online verziju 0.2. Aktuelni lokalni bezbednosni obim i rezultati su u [LOCAL-SECURITY.md](LOCAL-SECURITY.md).

# Security verification — 30–31 October 2026

Scope: Android application, portable import/archive format, local identity/session storage, synchronization and the Ktor backend in this checkout. This is an engineering security assessment with automated regression checks, not a certification or an independent penetration test. Public infrastructure and real service credentials are not configured.

## Assets and trust boundaries

Protect private boards/attachments, recoverable unsaved work, account sessions, membership permissions and cloud operation identities. Treat imported files, URI streams, API bodies/responses, invite/action codes and other accounts as untrusted. The APK is a public client: it contains no server/provider secret. A rooted/unlocked compromised device and a malicious authorized document recipient are outside the guarantees of Android sandboxing.

Private local data remains in app-private storage, protected by the OS sandbox/device encryption. The Room document database is not independently encrypted with an app password. Session tokens receive additional AES-256-GCM encryption with Android Keystore keys, random IVs and package/server associated data. Automatic backup/device transfer is excluded; explicit user exports intentionally leave the sandbox. Recovery is not a backup.

## Controls and verification

| Area | Implemented control | Verification |
|---|---|---|
| Authentication | Verified email, Argon2id passwords, random hashed bearer tokens, short access expiry, rotating refresh families and replay revocation | Backend identity/session negative tests on H2 and PostgreSQL; Android session tests |
| Authorization | Account checks on every board service operation, owner-only membership changes, recipient-bound expiring invites, role checks on edits | Cross-account/role denial, revocation, invitation-history privacy and concurrency tests |
| Request ambiguity | Exact JSON envelope fields, duplicate-key/strict UTF-8 checks, integer revisions, one bearer header | API security regression cases; rejected inputs must leave documents/mail unchanged |
| Resource exhaustion | Streaming request/response limits, JSON nesting/numeric bounds, request/hash/document concurrency gates, quotas and bounded queues | Format/backend/HTTP boundary cases; no public load or distributed-abuse qualification |
| Transport | HTTPS release, platform TLS/hostname checks, no credential redirects/cookies, cancellable bounded requests | HTTP redirect/oversize/malformed UTF-8/cancellation tests and APK manifest inspection |
| Local session | AES-GCM, Keystore key isolation, authenticated origin, no password persistence | Device tampering/origin-binding/restore tests |
| Imported content | Validated asset IDs, ZIP entry/path/size limits, no destructive overwrite, bounded sampled media | Archive/codec/media tests; native PDF/image parser fuzzing is not completed |
| Persistence | Transactions, checksummed nondestructive migrations, captured save revision, independent recovery draft | JVM workflow tests, Room migration/cancellation/full-database checks |
| Background sync | Account/origin partition, same queued IDs on retry, no implicit private upload, finite transient retry, stop on access/conflict/TLS failure | Domain runner tests and real Worker/Factory tests with disposable account fixtures |
| Exposure | Scoped FileProvider URI grants, backup exclusion, no broad storage permission, protected framework services/receivers | Final APK policy script inspects binary manifest and referenced compiled XML resources |
| Logs | Stable sanitized client errors; server logs exception class/SQL state without bodies, secrets or content | Source review and negative-error tests; production log pipeline still needs configuration |
| Supply chain | Fixed dependency versions, resolved runtime inventory, advisory scan, redacted source-secret scan, debug leak tools excluded from release | OSV, Gitleaks and final APK checks; OS/JRE/container/build-tool packages need separate audit |

## Reproducible commands

Use the JDK/Android SDK documented in README. On this workstation use one Gradle worker and avoid concurrent release optimization/emulator tests due to RAM pressure.

```powershell
.\gradlew.bat checkKotlinStyle checkArchitecture :core:test :format:test :data:testDebugUnitTest :app:testDebugUnitTest :server:test :server:postgresTest exportRuntimeDependencies
.\tools\Test-DependencySecurity.ps1
.\tools\Test-SourceSecrets.ps1 -GitleaksPath '<installed-gitleaks-executable>'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest '-Pwhiteboard.leakDetection=false'
.\tools\Test-Android.ps1 -DeviceSerial emulator-5554
.\tools\Test-CloudAccounts.ps1 -DeviceSerial emulator-5554
.\gradlew.bat :app:lintDebug :app:lintRelease :app:assembleRelease :app:bundleRelease
.\tools\Test-ReleaseSecurity.ps1 -ApkPath app/build/outputs/apk/release/app-release-unsigned.apk -Aapt2Path '<sdk-build-tools>/aapt2.exe'
```

Local Gitleaks 8.30.1 was downloaded from the official release and verified against its published SHA-256 checksum before execution. `Test-SourceSecrets` snapshots all nonignored current project files, including untracked/hidden files, into the ignored artifacts directory and produces a redacted report. It does not scan Git history or ignored local credential files, and a clean report cannot establish that every secret format is detectable.

Fresh OSV runtime inventory: **218 distinct Maven package/version pairs, zero known findings returned**. Coverage includes resolved Android release and backend runtime dependencies. Reports record their UTC check time. Advisory freshness, unpublished vulnerabilities and transitive native/OS packages remain outside this result. Do not use the phrase “no vulnerabilities” for this outcome.

The valid JVM/backend reports contain 195 passing cases across core, format, data, ViewModel, H2 and isolated PostgreSQL 18.6; unchanged suites retain Gradle results and affected suites reran. All 16 Room/Keystore device cases previously passed against the same data source. The final app suite passes: 37 executed cases plus one intentionally gated cloud case (38 enumerated). Separate disposable local-backend integration also passes, including actual WorkManager/WorkerFactory delivery after persistent queue reopen, unchanged operation identity and duplicate prevention. Keyboard overlap and a zero-size palette crash were fixed; final optimized-preview keyboard/recovery/layout checks and 150% font/theme/version-label checks pass. The compiled final APK policy, development v2/v3 signature and 16 KB ZIP/native checks pass. Package SHA-256 hashes are recorded in artifacts/qualified-package-hashes.json. Packaged backend startup/migration and unauthenticated HTTP policy passed with isolated in-memory H2. STATUS.md separates these scopes: no publisher signing, public deployment, independent penetration test or physical-device qualification is implied.

## Release gates that remain external

1. Configure the real HTTPS origin, production PostgreSQL verified TLS, actual SMTP delivery and deployment secret store. Verify certificates, ingress bounds, distributed limits and staging account lifecycle end to end.
2. Scan the production OS/JRE/container image and build supply chain. Pin the deployment image by digest and verify artifact provenance. No Docker engine is available on this workstation.
3. Rehearse encrypted backup restoration, establish deletion/retention, incident response, access review and operational ownership. Account-scoped device cloud caches currently remain private local data after server account deletion; a defined purge/retention workflow is still required. Private local originals must never be deleted as a side effect of cloud cleanup.
4. Complete shared editor/conflict recovery, private cloud attachments, presence/comments and AI provider integration/consent/cost bounds. The app currently exposes read-only cloud previews and independent local copies.
5. Verify production signing/publisher identity, final privacy policy and Play Data safety against the actual deployed behavior. Development-key preview APKs and unsigned AABs are not publication credentials.
6. Run independent penetration testing against staging and qualify physical devices, including minimum API, low storage, tablet/stylus and accessibility. Emulator checks cannot cover physical input or compromised devices.

Primary assessment references: [OWASP MASVS](https://mas.owasp.org/MASVS/), [OWASP API Security](https://api-security.owasp.org/editions/2023/en/0x00-header/), [OSV API](https://google.github.io/osv.dev/api/), [Gitleaks](https://github.com/gitleaks/gitleaks).
