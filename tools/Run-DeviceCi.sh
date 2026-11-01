#!/usr/bin/env bash
# Preserve the original test exit status while collecting diagnostics before the runner stops its emulator.
set -uo pipefail

if bash ./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest \
  -Pwhiteboard.leakDetection=false -Pkotlin.compiler.execution.strategy=in-process \
  --max-workers=2 --no-parallel --console=plain; then
  test_exit=0
else
  test_exit=$?
fi

if ((test_exit != 0)); then
  capture=artifacts/device-failures
  mkdir -p "$capture"
  timeout 15s adb logcat -d > "$capture/logcat.txt" 2>&1 || true
  timeout 15s adb shell dumpsys input_method > "$capture/input-method.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity activities > "$capture/activities.txt" 2>&1 || true
  timeout 15s adb exec-out screencap -p > "$capture/current-screen.png" 2>/dev/null || true
  timeout 20s adb shell uiautomator dump /sdcard/whiteboard-ci-failure.xml >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/whiteboard-ci-failure.xml "$capture/current-ui.xml" >/dev/null 2>&1 || true
  timeout 30s adb pull /sdcard/Android/data/dev.whiteboard.app.debug/files "$capture/app-files" >/dev/null 2>&1 || true
fi

exit "$test_exit"
