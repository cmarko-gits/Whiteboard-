#!/usr/bin/env bash
# Preserve the original test exit status while collecting diagnostics before the runner stops its emulator.
set -uo pipefail

mkdir -p artifacts
{
  echo "Profile: ${WHITEBOARD_CI_PROFILE:-pixel_2}"
  adb shell wm size
  adb shell wm density
  adb shell getprop ro.product.model
} > artifacts/device-profile.txt
if [[ "${WHITEBOARD_CI_PROFILE:-}" == pixel_c ]]; then
  size=$(adb shell wm size | sed -n 's/Physical size: //p' | tr -d '\r')
  density=$(adb shell wm density | sed -n 's/Physical density: //p' | tr -d '\r')
  IFS=x read -r width height <<< "$size"
  if [[ ! "$width" =~ ^[0-9]+$ || ! "$height" =~ ^[0-9]+$ || ! "$density" =~ ^[1-9][0-9]*$ ]] ||
    ((width * 160 / density < 800 || height * 160 / density < 800)); then
    echo "The tablet profile did not provide an actual large display." >&2
    exit 1
  fi
fi

run_release_smoke() {
  bash ./gradlew :app:assembleRelease -Pwhiteboard.leakDetection=false \
    -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 --no-parallel --console=plain || return $?
  mkdir -p artifacts/device-release || return $?
  local unsigned=app/build/outputs/apk/release/app-release-unsigned.apk
  local signed=artifacts/device-release/whiteboard-ci-preview.apk
  local key_directory
  key_directory=$(mktemp -d "${RUNNER_TEMP}/whiteboard-signing.XXXXXX") || return $?
  # Ephemeral CI-only identity; never include the key in uploaded reports.
  keytool -genkeypair -keystore "$key_directory/ci.jks" -storetype JKS \
    -alias whiteboard-ci -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Whiteboard CI' \
    -storepass android -keypass android -noprompt || return $?
  "${ANDROID_HOME}/build-tools/36.0.0/apksigner" sign \
    --ks "$key_directory/ci.jks" --ks-pass pass:android --key-pass pass:android \
    --out "$signed" "$unsigned" || return $?
  "${ANDROID_HOME}/build-tools/36.0.0/apksigner" verify "$signed" || return $?
  sha256sum "$signed" > artifacts/device-release/package.sha256 || return $?
  local -a smoke_options=()
  # API 26 touch injection after a runtime density override is unreliable. Its tablet
  # coverage uses a separate real AVD profile; modern API 36 retains all resize cases.
  if [[ "$api" == 26 ]]; then smoke_options+=(-SkipAdaptiveResize); fi
  pwsh -NoProfile -File ./tools/Smoke-Test.ps1 -AdbPath "$(command -v adb)" \
    -Package dev.whiteboard.app -ApkPath "$signed" "${smoke_options[@]}" 2>&1 | tee artifacts/device-release/smoke.log
}

if bash ./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest \
  -Pwhiteboard.leakDetection=false -Pkotlin.compiler.execution.strategy=in-process \
  --max-workers=2 --no-parallel --console=plain; then
  test_exit=0
else
  test_exit=$?
fi

if ((test_exit == 0)); then
  if ! api=$(adb shell getprop ro.build.version.sdk | tr -d '\r') || [[ ! "$api" =~ ^[0-9]+$ ]]; then
    echo "Cannot establish device API for optimized package qualification." >&2
    test_exit=1
  elif [[ "$api" == 26 || "$api" == 36 ]]; then
    if run_release_smoke; then
      echo "Optimized release smoke passed on API $api."
    else
      test_exit=$?
    fi
  fi
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
