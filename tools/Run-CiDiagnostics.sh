#!/usr/bin/env bash
# Diagnostic-only builds: their heap/frame observations are not release-binary or hardware guarantees.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "$(uname -s)" != Linux || "${WHITEBOARD_CI_TEST_SCOPE:-full}" != full ]]; then
  echo 'Diagnostic qualification is restricted to the full Linux CI emulator run.' >&2
  exit 2
fi
output=artifacts/device-release
mkdir -p "$output"
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -Pwhiteboard.qualificationTests=true -Pwhiteboard.leakDetection=true \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --console=plain
bash ./gradlew --stop
pwsh -NoProfile -File ./tools/Test-Android.ps1 -AdbPath "$(command -v adb)" \
  -Suite app -Classes dev.whiteboard.app.MemoryQualificationTest,dev.whiteboard.app.FrameQualificationTest \
  -ReportPrefix qualification-ci
cp artifacts/qualification-ci-app-tests.txt "$output/diagnostic-instrumentation.txt"
for name in qualification-memory.json qualification-frames.json qualification-cpu.trace; do
  adb pull "/sdcard/Android/data/dev.whiteboard.app.debug/files/$name" "$output/$name"
done
pwsh -NoProfile -File ./tools/Record-CiDiagnostics.ps1
