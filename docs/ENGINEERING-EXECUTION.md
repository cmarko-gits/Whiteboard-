> Istorijski plan/prethodna faza. Aktuelni lokalni obim i kriterijumi su u [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md), a provereni rezultati u [LOCAL-STATUS.md](LOCAL-STATUS.md). Online/AI zahtevi iz ranije faze trenutno nisu aktivni.

# Engineering execution checklist

Date: 30 October 2026. Scope: apply the supplied Android engineering roadmap to the existing Whiteboard project, preserving user documents and current behavior.

An item is complete only after its implementation and relevant checks pass. Source existence, emulator evidence, physical-device evidence and production deployment are separate results. This checklist does not replace the product requirements in REQUIREMENTS.md.

## Ordered work

- [x] E01 Establish domain contracts for settings, document files and dispatchers; remove concrete persistence adapters from presentation.
- [x] E02 Extract document lifecycle use cases and reduce the editor ViewModel's responsibilities. Preserve revision-aware Save, recovery and cancellation.
- [x] E03 Add deterministic ViewModel/coroutine tests for success, failure, cancellation, edits during Save and state restoration; retain existing integration tests.
- [x] E04 Isolate presentation/platform features into bounded modules and enforce dependency boundaries automatically.
- [ ] E05 Use explicit UI actions/state and typed recoverable errors; verify configuration changes and accessible UI controls.
- [x] E06 Add durable, account-scoped background synchronization with bounded retries and cancellation on account changes; preserve queued edits.
- [x] E07 Qualify the fill tool through geometry, history, grouped objects, save/reopen and gesture cancellation tests.
- [x] E08 Add focused Compose tests, debug memory-leak detection and reproducible rendering/performance measurements.
- [x] E09 Complete runtime dependency/security checks, backend negative tests, secure logging and release network/storage checks. Record coverage and unresolved findings.
- [x] E10 Add reproducible backend packaging/configuration and deployment checks without embedding credentials or weakening release HTTPS.
- [x] E11 Run the applicable JVM, backend H2/PostgreSQL, Android, lint, architecture and packaging checks; record fresh results.
- [ ] E12 Produce the tested APK/AAB and complete physical-device checks where a device is available.

## Engineering rules

- Pure Kotlin domain models, commands, repository contracts and orchestration. Android APIs belong in platform adapters.
- Compose receives state and action callbacks. ViewModels orchestrate domain operations and expose immutable StateFlow state.
- Domain use cases own meaningful workflows; simple value objects do not acquire artificial dependency-injection wrappers.
- Hilt builds the application graph using constructor injection and scoped bindings. Domain tests instantiate dependencies directly.
- Blocking I/O and CPU work run on appropriate injected dispatchers. Cancellation propagates. No GlobalScope.
- Durable changes, failures and acknowledgments are represented in state; ephemeral effects are used only where losing them is acceptable.
- Room migrations preserve data. Release credentials remain outside the APK/repository. Session encryption uses Android Keystore and authenticated encryption; passwords are not retained.
- Background work respects system scheduling. WorkManager is not a promise of immediate execution after force-stop.
- Performance improvements require measurement. Stable annotations must reflect actual immutability. Recomposition is distinct from compilation.
- Logging must not expose tokens, passwords, invite codes or document contents. Debug diagnostics are excluded from release.
- CI checks formatting, module boundaries, unit/integration tests, lint and known dependency vulnerabilities. A local run is not evidence of a hosted CI run.

## Product changes to carry through the checks

1. A visible fill tool and color picker, with precise geometric targeting and Undo/Redo.
2. Clear local Save/recovery state, with specific retryable errors instead of one generic failure.
3. Clear cloud availability, pending/offline/synchronizing/conflict states and explicit account ownership.
4. A compact editor layout on phones and a wider layout on tablets/landscape, preserving current editing state.

Advanced freehand/image flood fill, new layer-lock semantics and additional AI features require separate acceptance criteria; their names are not evidence of implementation.

## Verification progress

The final 31 October app regression passes on the API 37 / 16 KB emulator: 37 executed cases pass and the disposable-cloud case is intentionally gated (38 enumerated). Its separate local-backend run also passes, including the real WorkManager/WorkerFactory delivering a persisted capture with the original operation identity. Fill/history, saved colors, invalid HEX, keyboard/rotation, dialog IME Done, saved-instance text restoration and save/reopen cases pass. Qualification fixed keyboard overlap, a zero-size palette crash, theme-label wrapping at 150% font and a stale version label. Test navigation distinguishes the vertical grid from horizontal filters and refreshes stale accessibility text. Shell Enter is excluded as a substitute for IME Done; a proper Compose IME-action case and an actual keyboard Done tap verify that interaction.

JUnit reports contain 123 Android-independent/JVM cases (core 63, format 33, data 18, app ViewModel 9), 36 backend cases on each of H2/PostgreSQL, and no failures/errors. Unchanged suites retain valid Gradle results; affected data/ViewModel suites reran. All 16 data-device cases previously passed against the same data source. Architecture/style, debug/release lint, optimized APK/AAB, compiled APK policy, development signature, ZIP/native 16 KB checks, final release recovery/layout/keyboard smoke and the 150% font/version-label check pass. Rendering distributions are in PERFORMANCE.md; no physical 60 FPS or no-leak claim is made. Runtime advisory scan: 218 distinct package/version pairs, zero known OSV findings. E05 remains open for complete accessibility/interaction qualification; E12 has tested packages but awaits physical devices. Public deployment and independent security assessment remain separate gates. Detailed evidence is in STATUS.md and SECURITY.md.

## External dependencies

Public backend/SMTP/AI qualification requires the publisher's service accounts, HTTPS domain, deployment region/budget and provider credentials. Production signing and Play publication require publisher-owned credentials and approval. Local verification continues independently; public readiness must not be claimed while these dependencies remain open.

## Primary guidance

- Android architecture: https://developer.android.com/topic/architecture/recommendations
- UI events: https://developer.android.com/topic/architecture/ui-layer/events
- Dependency injection: https://developer.android.com/training/dependency-injection
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
- Security Crypto deprecation/platform Keystore: https://developer.android.com/jetpack/androidx/releases/security
- Mobile security assessment: https://mas.owasp.org/MASVS/
- API security assessment: https://api-security.owasp.org/editions/2023/en/0x00-header/
