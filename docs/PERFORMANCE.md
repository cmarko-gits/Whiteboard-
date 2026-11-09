# Performance and device qualification

## Current diagnostic observation — 9 November 2026

Source `9c52b9b6b8e9bb261909457dafa5df84b54c7565`, focused CI 42674959657 / API 36: both diagnostic cases passed. Six actual media/editor/recreate/close cycles watched 12 editor Views; LeakCanary passed and Shark found **0 WhiteboardView instances** in the final 51,345,386-byte heap. Per-cycle PSS was 214514/204860/205784/208449/200884/201280 KB, after GC 238833 KB. These observations are not peak memory or a guarantee for all workloads.

The same 1000-stroke / 100000-point workload recorded 89 Window frames: p50/p95/p99 **366.848/403.108/750.173 ms**. This remains high software-GPU emulator latency under debug/tracing and does not pass a physical 60 Hz target. The recorded trace SHA-256 is `2FF781F6A644DF8382978C1EB2C6741DC25BB4143333E814A7C095D771649D5E`. Its 8077 reconstructed main-thread events have 0 nonmonotonic records, with approximate attributed CPU/wall time 500.395/11554.304 ms. HardwareRenderer.nSyncAndDrawFrame accounts for 10457.957 ms wall (about 90.5%) and 58.226 ms main CPU; this again suggests emulator render-pipeline waiting, without native RenderThread/GPU evidence establishing the cause. Evidence: `artifacts/release-focus-9c52b9b-{evidence,trace-provenance,cpu-analysis}.json` and `release-focus-9c52b9b-diag-proof-diagnostic-qualification.json`, SHA-256 `860C77A618489F26509007D13132C080277395209855B92B38FBAB1FE3311BA7`. The final full matrix must repeat these diagnostics on this source after its release flows.

## Earlier diagnostic observation — 9 November 2026

Source `5c4f4af8462daf39df3eafac595311b2fa585032`, focused CI 42591085249, API 36 / 3 GiB software-GPU emulator: both separate diagnostic cases passed. Six actual media/editor/recreate/close cycles watched 12 editor Views; LeakCanary assertion passed and the final Shark heap contained **0 WhiteboardView instances**. The temporary 51,344,249-byte heap was removed by the fixture. Per-cycle PSS was 214985/205007/204694/205366/207142/207718 KB; post-GC PSS was 245268 KB. These snapshots are not a measured peak or a universal leak guarantee.

The 1000-stroke / 100000-point Window workload recorded 89 frames: TOTAL_DURATION p50/p95/p99 **450.146/500.191/996.957 ms** with debug diagnostics and sampling enabled. This is high emulator latency and does not pass a physical 60 Hz target. The optimized release binary and its hardware render pipeline were not measured by this diagnostic APK.

The source-bound ART v3 dual-clock trace SHA-256 is `A92BDF4AA20C5D808D8D1E0383E7709E05E0A126D4A24F5176C5C253A11729C0`. The existing bounded parser reconstructed 9325 main-thread events with 0 nonmonotonic records. Approximate attributed main CPU/wall time was 623.123/13953.613 ms. HardwareRenderer.nSyncAndDrawFrame accounted for 12630.738 ms wall (about 90.5%) and 49.852 ms main CPU time. This **suggests render-pipeline waiting in this emulator**; native RenderThread/GPU stacks are absent, so the trace does not establish a GPU cause or a justified domain/index optimization. Physical release profiling remains required.

Evidence: `artifacts/release-focus-5c4f4af-{evidence,trace-provenance,cpu-analysis}.json`, `release-focus-5c4f4af-diag-proof-diagnostic-qualification.json` and the retained 176331-byte trace. The focused run has three seven-case native profiles and a separate API 36 diagnostic profile; it is not the complete release matrix. Full-source repetition remains a release gate. The earlier failed memory fixture and its confirmed cold-install/onboarding cause are preserved in RELEASE-READINESS.md.

## Reproducible workload

`LargeDocumentTest` creates 1000 independent strokes with 100 points each and 200 Unicode notes. It saves a file-backed Room document, loads and compares every object/property, and renders the normal PNG export. It records JSON size, save/load/export elapsed time and process/native memory in the `WhiteboardBenchmark` instrumentation log. The database is removed afterward.

This measures persistence and software export on the selected device. It does not measure GPU frame time, input-to-display latency or pressure/palm behavior. Emulator results must not be presented as physical-device performance guarantees. PSS is sampled after export, not a measured peak.

Initial measured result, 19 October 2026, debug build on the Android 17 emulator with 16 KB pages: 1,923,294 JSON bytes; Save 640 ms; Load 658 ms; PNG 832 ms; PSS after export 220,324 KB. One run is evidence that the scenario completes, not a statistical latency or memory guarantee. Repeat on the agreed physical release devices.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.whiteboard.app.LargeDocumentTest'
```

## Physical qualification still required

Select a minimum supported API 26 device, a current midrange phone, a large tablet and a pressure-capable stylus tablet. Record OS, chipset/RAM, refresh rate, battery/thermal state, app version and release build signature. Add agreed real-world PDF/image fixtures and deliberately sparse boards with very distant objects.

For drawing and navigation, measure p50/p95/p99 frame time and long frames with Android profiling tools, then input-to-display latency with suitable hardware. The design target is smooth drawing at 60 Hz; the release gate must be established against the agreed reference devices. Do not equate a short software-render loop with this criterion.

Repeat save/load/export measurements for a small document, the deterministic large fixture, large text/recovery payloads, imported media and content near format safety limits. Record failed writes, low-storage behavior, process interruption and the last recoverable revision. Verify background/foreground, rotation, keyboard visibility, multi-window and large font separately.

## Implemented safeguards

- Incremental live stroke paths; no complete path/list rebuilding on every frame.
- Viewport culling and an estimated 32 MB committed-path cache.
- Asynchronous media loading with a 24 MB bitmap cache and bounded sampled image/PDF previews.
- PNG/JPG safeguards of 16 million pixels and a default 4096 maximum dimension.
- Bounded command history and chunked transactional SQLite persistence.
- Linear lookup maps/sets for batch editing and z-order changes, avoiding pairwise object searches.

More complex indexing/rendering should follow measured evidence. Qualify optimized signed release builds as well as debug builds before distribution.

## Rendering distribution and memory diagnostics

`RenderMeasurementTest` uses the actual `WhiteboardView` with 1000 strokes/100,000 points and a 1080×1200 software Canvas. It records 30 warm samples after five warmups for visible and offscreen-culled content, reporting p50/p95/p99 in the `WhiteboardRender` instrumentation log. No timing threshold is imposed on a shared emulator. It measures software drawing/cache/culling, not hardware GPU frames, display refresh, Compose recomposition or input latency. Record fresh results and device metadata in STATUS.md after execution.

31 October final passing regression, API 37 / 16 KB `sdk_gphone16k_x86_64`, debug without heap analysis, no concurrent Gradle build: visible p50/p95/p99 **30.49/42.34/44.09 ms**; offscreen-culled **4.43/17.76/18.89 ms**. The persistence/export fixture recorded Save 609 ms, Load 718 ms, PNG 847 ms and PSS after export 235,264 KB. Raw records are in `artifacts/render-measurements.txt`. Host scheduling changes these distributions; these measurements do not establish a hardware 60 FPS or physical-memory release gate.

Debug builds include LeakCanary 2.14 by default. Automated tests use `'-Pwhiteboard.leakDetection=false'` to keep asynchronous heap analysis outside the measured/test workload. Release has no LeakCanary dependency; the final APK policy script also checks DEX strings for its analysis classes. Integration of a leak detector is not evidence of a completed physical-device leak investigation. For manual qualification, use a diagnostic debug build, repeatedly open/close documents, rotate, import media and leave the app, then retain and investigate any heap-analysis report. Confirm fixes under the same workload.

## Write-failure checks

`FailedWriteTest` limits a disposable file-backed SQLite database with `max_page_count`, forces a larger document write, and checks the previous saved board and independent draft after `SQLiteFullException`. It uses TRUNCATE mode for deterministic connection-local limits and does not exhaust the emulator's disk. A second test cancels a transaction before commit and verifies the previous revision. Both passed on the Android 17 emulator. Actual power loss during an OS write is not simulated; SQLite transactional behavior, explicit cancellation and host process-death recovery are separate evidence.

## Qualification update — 1 November 2026, local 0.4.0

Fresh API 37 diagnostic regression: Save/Load/PNG **617/807/809 ms**, PSS after export **235684 KB**. Software visible draw p50/p95/p99 **30.818/43.171/46.736 ms**; culled **4.009/11.661/14.810 ms**. Raw evidence: artifacts/qualification-api37-benchmarks.txt. These numbers use a debug diagnostic package and an emulator, not a physical release performance budget.

Memory qualification now executed: six real editor open/recreate/close cycles with eight bitmap attachments, 12 watched editor views, LeakCanary assertion passed, and 0 WhiteboardView instances in the final Shark heap. The temporary heap was deleted after analysis. Actual Window frame metrics and a sampled CPU trace were also recorded. High emulator frame latency is explicitly reported and is not a 60 Hz pass. Detailed metrics, trace interpretation and reproducible commands are in [LOCAL-QUALIFICATION.md](LOCAL-QUALIFICATION.md).

Physical-device and OEM/stylus qualification is excluded from this run at the user's request. API 26/30/33 CI recipes are prepared, not locally executed results. Release packages contain neither LeakCanary nor Shark; the APK manifest/DEX policy verifies this separately.
