#!/usr/bin/env bash
# Preserve the original test exit status while collecting diagnostics before the runner stops its emulator.
set -uo pipefail

mkdir -p artifacts
scope=${WHITEBOARD_CI_TEST_SCOPE:-full}
case "$scope" in full|mind-map|diagnostic) ;; *) echo 'Unsupported CI test scope.' >&2; exit 2 ;; esac
printf '%s\n' "$scope" > artifacts/ci-test-scope.txt
# A recorded System UI startup timeout arrived ~47s after emulator launch, after the
# runner had already reported boot completed. Let early boot services settle before
# launching Gradle/install/input work; this is not a retry or dismissal of an ANR.
boot_deadline=$((SECONDS + 60))
while :; do
  guest_uptime=$(timeout 5s adb shell cat /proc/uptime | awk '{print int($1)}' | tr -d '\r')
  if [[ ! "$guest_uptime" =~ ^[0-9]+$ ]]; then
    echo 'Cannot establish emulator uptime for startup qualification.' >&2
    exit 1
  fi
  if ((guest_uptime >= 60)); then break; fi
  if ((SECONDS >= boot_deadline)); then
    echo 'Emulator did not reach a stable startup observation window.' >&2
    exit 1
  fi
  sleep 1
done
echo "Starting device qualification at guest uptime ${guest_uptime}s."
# Any ANR during this fresh boot blocks reliable framework accessibility/input assertions.
# dumpsys lastanr reports "ANR time:" and an activity/process record, not necessarily
# "ANR in com.android.systemui"; require its explicit clean-boot marker instead.
adb shell dumpsys activity lastanr > artifacts/device-boot-anr.txt
if ! grep -qi 'no ANR has occurred since boot' artifacts/device-boot-anr.txt; then
  echo 'Emulator boot health is not clean; application tests cannot run reliably.' >&2
  mkdir -p artifacts/device-failures
  timeout 15s adb logcat -d > artifacts/device-failures/logcat.txt 2>&1 || true
  timeout 15s adb exec-out screencap -p > artifacts/device-failures/current-screen.png 2>/dev/null || true
  exit 1
fi
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
  # External UI client controls the optimized package through connected semantics.
  # These debug/test APKs stay separate; neither is part of the release payload.
  adb install -r app/build/outputs/apk/debug/app-debug.apk || return $?
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || return $?
  adb shell pm list instrumentation > artifacts/device-release/ui-client-instrumentation.txt || return $?
  grep -F 'dev.whiteboard.app.debug.test/dev.whiteboard.app.ReleaseUiBridgeRunner' \
    artifacts/device-release/ui-client-instrumentation.txt || return $?
  sha256sum app/build/outputs/apk/debug/app-debug.apk \
    app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > artifacts/device-release/ui-client-packages.sha256 || return $?
  if [[ "$api" == 36 && "$scope" == full ]]; then
    pwsh -NoProfile -File ./tools/Get-UpgradeBaseline.ps1 || return $?
    local baseline=artifacts/device-release/baseline-0.7.2-ci-signed.apk
    "${ANDROID_HOME}/build-tools/36.0.0/apksigner" sign \
      --ks "$key_directory/ci.jks" --ks-pass pass:android --key-pass pass:android \
      --out "$baseline" artifacts/device-release/baseline-0.7.2-unsigned.apk || return $?
    pwsh -NoProfile -File ./tools/Smoke-Test.ps1 -AdbPath "$(command -v adb)" \
      -Package dev.whiteboard.app -ApkPath "$baseline" -SkipAdaptiveResize \
      2>&1 | tee artifacts/device-release/upgrade-baseline-smoke.log || return $?
    pwsh -NoProfile -File ./tools/Test-ReleaseUpgrade.ps1 -AdbPath "$(command -v adb)" \
      -NewApkPath "$signed" 2>&1 | tee artifacts/device-release/release-upgrade.log || return $?
    bash tools/Assert-CiDeviceHealth.sh after-upgrade || return $?
  fi
  local -a smoke_options=(-CheckRotation -CheckLayers -CheckAlignment -CheckObjectMenu -CheckConnections -CheckMindMaps)
  # API 26 touch injection after a runtime density override is unreliable. Its tablet
  # coverage uses a separate real AVD profile; modern API 36 retains all resize cases.
  if [[ "$api" == 26 ]]; then smoke_options+=(-SkipAdaptiveResize); fi
  pwsh -NoProfile -File ./tools/Smoke-Test.ps1 -AdbPath "$(command -v adb)" \
    -Package dev.whiteboard.app -ApkPath "$signed" "${smoke_options[@]}" 2>&1 | tee artifacts/device-release/smoke.log || return $?
  if [[ "$api" == 36 ]]; then
    pwsh -NoProfile -File ./tools/Test-WindowModes.ps1 -AdbPath "$(command -v adb)" \
      -Package dev.whiteboard.app -ArtifactsDirectory artifacts/device-release \
      2>&1 | tee artifacts/device-release/window-modes.log || return $?
  fi
  pwsh -NoProfile -File ./tools/Measure-ReleaseRuntime.ps1 -AdbPath "$(command -v adb)" \
    -Package dev.whiteboard.app 2>&1 | tee artifacts/device-release/runtime-profile.log || return $?
  if [[ "$api" == 36 ]]; then
    pwsh -NoProfile -File ./tools/Test-Adaptive.ps1 -AdbPath "$(command -v adb)" \
      -Package dev.whiteboard.app -VersionName 0.8.0 -ArtifactsDirectory artifacts/device-release \
      2>&1 | tee artifacts/device-release/adaptive.log || return $?
  fi
  pwsh -NoProfile -File ./tools/Test-AppLanguage.ps1 -AdbPath "$(command -v adb)" \
    -Package dev.whiteboard.app 2>&1 | tee artifacts/device-release/app-language.log
}

run_native_tests() {
  local -a options=(-Pwhiteboard.leakDetection=false -Pkotlin.compiler.execution.strategy=in-process
    --max-workers=1 --no-parallel --console=plain)
  bash tools/Assert-CiDeviceHealth.sh native-start || return $?
  if [[ "$scope" == full ]]; then
    bash ./gradlew :data:connectedDebugAndroidTest "${options[@]}" || return $?
    bash tools/Assert-CiDeviceHealth.sh after-data || return $?
    bash ./gradlew :app:connectedDebugAndroidTest "${options[@]}" || return $?
  else
    bash ./gradlew :app:connectedDebugAndroidTest "${options[@]}" \
      -Pandroid.testInstrumentationRunnerArguments.class=dev.whiteboard.app.AlignmentFlowTest,dev.whiteboard.app.MindMapEditorFlowTest,dev.whiteboard.app.DialogActionTest,dev.whiteboard.app.InputDialogWindowTest || return $?
  fi
  bash tools/Assert-CiDeviceHealth.sh after-native
}

if [[ "$scope" == diagnostic ]]; then
  api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
  if [[ "$api" != 36 ]]; then
    echo 'Diagnostic-only qualification requires the API 36 profile.' >&2
    test_exit=2
  else
    mkdir -p artifacts/device-release
    if bash tools/Assert-CiDeviceHealth.sh native-start &&
        bash tools/Run-CiDiagnostics.sh 2>&1 | tee artifacts/device-release/diagnostic.log &&
        bash tools/Assert-CiDeviceHealth.sh after-diagnostic; then
      test_exit=0
    else
      test_exit=$?
    fi
  fi
elif run_native_tests; then
  test_exit=0
else
  test_exit=$?
fi

if ((test_exit == 0)) && [[ "$scope" != diagnostic ]]; then
  if ! api=$(adb shell getprop ro.build.version.sdk | tr -d '\r') || [[ ! "$api" =~ ^[0-9]+$ ]]; then
    echo "Cannot establish device API for optimized package qualification." >&2
    test_exit=1
  elif [[ ( "$api" == 26 && ( "$scope" == full || "$scope" == mind-map ) ) || ( "$api" == 36 && "$scope" == full ) ]]; then
    if run_release_smoke && bash tools/Assert-CiDeviceHealth.sh after-release; then
      echo "Optimized release smoke passed on API $api."
      if [[ "$api" == 36 ]]; then
        if bash tools/Run-CiDiagnostics.sh 2>&1 | tee artifacts/device-release/diagnostic.log &&
            bash tools/Assert-CiDeviceHealth.sh after-diagnostic; then
          echo 'Diagnostic memory/frame qualification passed on API 36.'
        else
          test_exit=$?
        fi
      fi
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
