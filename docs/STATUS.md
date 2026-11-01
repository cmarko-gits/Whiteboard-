> Istorijski status verzije 0.2. Aktuelna lokalna verzija i dokazi su u [LOCAL-STATUS.md](LOCAL-STATUS.md). Online funkcije su van trenutnog obima.

# Implementation status

Updated: 31 October 2026. App version: 0.2.0 / versionCode 2. Development is ongoing. The agreed first Play release includes cloud, collaboration and AI; that public release is not complete.

## Current engineering work

The project now has eleven bounded modules: core, format, data, canvas, core:ui, feature:workspace, feature:cloud, platform, sync, app and server. Domain contracts cover settings, document storage and dispatchers. EditorPersistence owns revision-aware Save/recovery/discard coordination. Presentation consumes those ports; checkArchitecture enforces the dependency/import boundaries.

The editor includes a visual color picker and a fill tool for vector shapes, notes and page backgrounds. Fill preserves group identities and Undo/Redo; dragging/cancelled gestures do not commit a fill. This is vector-surface filling, not arbitrary image/freehand flood fill.

Typed storage failures expose scoped retry actions for suitable operations. Notice identities prevent old snackbar acknowledgments from clearing newer errors. Small dialog fields/IDs survive saved-instance-state restoration; documents and Android Views are not saved into Bundles.

Account-scoped WorkManager delivery handles already queued captures/edits with network/battery constraints, bounded passes, finite transient retries and account/origin checks. Conflict/access/certificate failures stop automatic sending while retaining queued content. The HTTP adapter uses cancellable bounded OkHttp requests with platform TLS validation, no credential redirects and no automatic POST replay. Cloud UI currently provides publication, previews and independent local copies; shared editing is not enabled.

Small-screen qualification exposed insufficient library space on a 720x1280 phone. The library now uses its compact header below 700 dp height and reserves remaining space for the board grid. Compose tests use explicit Espresso 3.7.0 instead of the obsolete transitive 3.5.0. Menu ordering, nonblocking empty-canvas guidance and keyboard dismissal were corrected. The color dialog now respects keyboard/system insets and handles a zero-size palette during keyboard/rotation transitions. Tests refresh stale accessibility text and scroll the actual board grid instead of the horizontal filters. The final offline app suite passes, as does the separate local-backend cloud integration, including real WorkManager delivery.

The color dialog keeps HEX input visible and places actions beside it when a large keyboard leaves very little height. Its palette/details remain scrollable in larger layouts. New-board content scrolls above the keyboard, templates have 48 dp minimum touch rows, and IME Done uses the dialog's focus owner. Theme choices flow onto another row at large font sizes; Settings reads the actual BuildConfig version instead of a stale literal. Final release smoke explicitly waits for the software keyboard to be shown again after landscape configuration changes. A raw shell Enter event is not treated as an IME Done action; the dedicated Compose IME-action test and an actual on-screen Done tap verify that interaction.

## Fresh verification evidence

| Check | Result |
|---|---|
| Core JVM tests | 63 passed |
| Format JVM tests | 33 passed |
| Data JVM tests, including HTTP cancellation/isolation | 18 passed |
| Workspace ViewModel JVM tests | 9 passed |
| Backend tests on H2 | 36 passed |
| Same backend tests on isolated PostgreSQL 18.6 | 36 passed |
| Data/Room/Keystore device suite on API 37, 16 KB emulator | 16 passed |
| Final app-device suite, offline | 38 enumerated cases: 37 executed/pass, one disposable-cloud case intentionally gated; overall passing result |
| Separate local-backend cloud integration | 1 passed: login/encrypted restore, durable queue reopen, lost acknowledgment/retry, cursor replay, real WorkManager delivery, publication/preview/local copy, logout/deletion |
| Worker/Factory and fill-gesture regressions | 4 + 4 passed within the passing app suite; full fill/Undo/Save/reopen UI flow also passes |
| Architecture/style and debug/release lint after UI fixes | Passed; lint has zero errors and two Android API/locale warnings |
| Current debug application/test APK build | Passed, including keyboard/rotation crash correction |
| Runtime Maven advisory audit | 218 distinct package/version pairs, zero known OSV findings returned |
| Current-source secret scan | No findings in the recorded nonignored source snapshot; no history scan |
| Final optimized preview APK policy | Passed against the current signed APK; earlier debug negative control was correctly rejected |
| Final release preview signature and 16 KB ZIP/native checks | Passed with development v2/v3 signing; all four packaged 64-bit native libraries pass LOAD/RELRO checks |
| Final optimized release smoke | Passed: new-board Confirm with keyboard shown, HEX input/Apply with keyboard reopened in landscape, process-death recovery, Save/close/reopen and phone/tablet layouts |
| 150% system font on 720x1280 API 37 | Editor/library/settings navigation and dismissal pass; theme labels fit on one line and Settings shows Whiteboard 0.2.0; screenshots visually reviewed |
| Packaged backend startup/migration and HTTP probe | Passed using an isolated in-memory database; health, 401/404 and response headers checked |

Reports are under artifacts/ and module build/reports directories. The current passing reports are android-app-tests.txt and cloud-account-test.txt. Earlier failures, including the keyboard/rotation crash, are retained separately. JVM counts are summarized in artifacts/jvm-test-summary.json: 195 tests, zero failures/errors/skips. Unchanged core/format/backend suites retain valid Gradle results; affected data/ViewModel suites reran. Software render measurements are recorded in artifacts/render-measurements.txt and PERFORMANCE.md. These results have different scopes and do not establish public Play readiness.

## Workstation capacity constraint

The agreed additional-storage budget remains 3.4 GB. Build and emulator execution are sequential, using the existing SDK/AVD and offline Gradle resolution, without new system-image downloads. At this run's checkpoints C: had approximately 12.6–12.8 GB free; this does not increase the agreed storage-growth budget. A previously proposed Gradle-cache cleanup was rejected by automatic policy and was not executed. The old 0.28 GB startup blocker is resolved. No physical Android device is currently visible through ADB.

The corrected app suite, disposable cloud integration and final optimized-preview checks pass within the storage budget. An early restart was killed by Android's low-memory manager before tests began; it remains an interrupted run. Local UI checks run offline after boot settles; cloud integration enables connectivity for real WorkManager constraints. Test-Android marks runs incomplete before starting, bounds ADB waits and optionally verifies installed APK hashes before execution. Smoke tests wait for enabled controls and the expected dialog before typing. No personal documents, app data, SDK installation or signing keys were removed to make space.

The current release APK/AAB and development-signed preview were rebuilt and inspected after the final UI changes. Artifact sizes/SHA-256 hashes are recorded in artifacts/qualified-package-hashes.json. The preview is artifacts/whiteboard-release-preview.apk; the AAB is app/build/outputs/bundle/release/app-release.aab. The preview uses a development key and the AAB has no publisher signing configuration. Release cloud origin remains unconfigured, so this preview is not the agreed cloud/AI Play release. No physical phone was visible for installation; the emulator was shut down after qualification to release resources.

## Product capabilities retained

Local editing includes pen/marker/highlighter, stroke eraser, shapes/lines/arrows, text/notes, selection/transforms, flat groups, private object clipboard, z-order, pages/backgrounds, manual Save, separate recovery drafts and optional autosave. The library supports folders, indexed text search, favorites, Trash and thumbnails. Image/camera import, crop/90-degree rotation, PDF annotation, PNG/JPG/PDF export and editable ZIP backups remain implemented. English and Serbian Latin resources are available.

Room migrations preserve earlier documents/recovery and account-partitioned queues. Document format writes version 3 and reads versions 1-3. Local private originals are never automatically uploaded on sign-in. Device automatic backup/transfer is excluded; explicit editable backups are separate from recovery.

## Outstanding release gates

- Qualify minimum API, current phones, large tablet/stylus, broader font/display scaling, screen-reader accessibility, GPU/input latency and memory behavior on agreed physical devices. The 150% emulator-font check is complete. Software render timings are not a 60 FPS guarantee; LeakCanary integration is not a completed leak investigation.
- Complete private cloud attachments, shared editor/conflict recovery and collaborative Undo, live delivery/presence/comments, account-cache deletion/retention and AI integration/consent/cost bounds.
- Configure public HTTPS hosting, production PostgreSQL/SMTP, operational backups/restore/abuse limits and service ownership. No public backend or AI service is deployed.
- Complete independent security assessment, production image/OS/JRE scanning, publisher-owned signing, privacy/Data safety and Play testing/publication.

See REMAINING-TASKS.md for all 105 currently open product-plan identifiers and the overlapping engineering checks, ENGINEERING-EXECUTION.md for evidence-driven completion, SECURITY.md for security coverage/limitations and DEPLOYMENT.md for operational requirements.
