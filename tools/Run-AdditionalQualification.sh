#!/usr/bin/env bash
# Separate, additive matrix. It never changes the frozen application checkout or publishes packages.
set -euo pipefail
profile=${1:?Qualification profile is required}
case "$profile" in
  runtime-16k) api=35; tag=google_apis_ps16k ;;
  runtime-16k-standard) api=35; tag=google_apis_ps16k ;;
  release-16k) api=35; tag=google_apis_ps16k ;;
  themes-16k) api=36; tag=google_apis_playstore_ps16k ;;
  talkback) api=36; tag=google_apis_playstore ;;
  talkback-service) api=36; tag=google_apis_playstore ;;
  library-reopen) api=36; tag=google_apis_playstore ;;
  *) echo 'Unsupported additional qualification profile.' >&2; exit 2 ;;
esac
if [[ "${GITHUB_ACTIONS:-}" != true || "$(uname -s)" != Linux ]]; then
  echo 'Only the disposable Linux Actions device is supported; no local SDK/AVD changes were made.' >&2
  exit 2
fi
if [[ "$(git rev-parse HEAD)" != 36cd15a6415d66eff7be10c97802ddc61f522f59 ]]; then
  echo 'Application checkout differs from the pinned qualified source.' >&2; exit 2
fi
: "${ANDROID_HOME:?}" "${RUNNER_TEMP:?}"
export ANDROID_SERIAL=emulator-5554
export ANDROID_AVD_HOME="$RUNNER_TEMP/whiteboard-additional-avd"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
output=artifacts/qa-extra
mkdir -p "$output" "$ANDROID_AVD_HOME"
emulator_pid=''
assert_extra_health() {
  local phase=$1
  bash tools/Assert-CiDeviceHealth.sh "$phase"
  timeout 15s adb logcat -b crash -d -v brief > "$output/$phase-native-crashes.txt"
  if grep -Eq 'Fatal signal [0-9]+|signal [0-9]+ \(SIG' "$output/$phase-native-crashes.txt"; then
    echo "Native process crash recorded at $phase; environment is not qualified." >&2
    return 1
  fi
}
cleanup() {
  result=$?; trap - EXIT
  if [[ -n "$emulator_pid" ]]; then
    # Only generated fixture snapshots; never pull arbitrary app files or heaps.
    for label in failure-physicalLongPressOpensTheSharedMenuAndDuplicateUndoRedoSurviveSaveReopen object-menu-long-press; do
      for suffix in png xml input-method.txt; do
        remote="/sdcard/Android/data/dev.whiteboard.app.debug/files/$label.$suffix"
        size=$(timeout 5s adb shell stat -c %s "$remote" 2>/dev/null | tr -d '\r' || true)
        if [[ "$size" =~ ^[0-9]+$ ]] && ((size <= 3000000)); then
          timeout 15s adb pull "$remote" "$output/$label.$suffix" >/dev/null 2>&1 || true
        fi
      done
    done
    timeout 15s adb logcat -d | tail -c 4000000 > "$output/logcat.txt" || true
    timeout 15s adb logcat -b crash -d -v brief | tail -c 524288 > "$output/native-crashes.txt" || true
    timeout 10s adb shell dumpsys activity lastanr > "$output/last-anr.txt" 2>&1 || true
    timeout 10s adb exec-out screencap -p > "$output/final-screen.png" 2>/dev/null || true
    timeout 10s adb emu kill >/dev/null 2>&1 || true
    if kill -0 "$emulator_pid" 2>/dev/null; then kill "$emulator_pid" 2>/dev/null || true; fi
  fi
  printf '%s\n' "$result" > "$output/process-exit-code.txt"
  exit "$result"
}
trap cleanup EXIT
image="system-images;android-$api;$tag;x86_64"
printf '%s\n' "$image" > "$output/requested-image.txt"
timeout 600s sdkmanager --install emulator "$image" --channel=0 2>&1 | tee "$output/sdk-install.log"
printf 'no\n' | avdmanager create avd --force --name whiteboard-extra --package "$image" --device pixel_2
config="$ANDROID_AVD_HOME/whiteboard-extra.avd/config.ini"
awk -v guest_ram=3072 -f tools/Configure-CiAvd.awk "$config" > "$config.tmp"
mv "$config.tmp" "$config"
cp "$config" "$output/avd-config.txt"
bash ./gradlew --stop
emulator -avd whiteboard-extra -port 5554 -no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim -feature -Vulkan,-QuickbootFileBacked > "$output/emulator.log" 2>&1 &
emulator_pid=$!
deadline=$((SECONDS + 600))
booted=false
while ((SECONDS < deadline)); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then echo 'Emulator exited before boot.' >&2; exit 1; fi
  boot=$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  uptime=$(timeout 5s adb shell cat /proc/uptime 2>/dev/null | awk '{print int($1)}' | tr -d '\r' || true)
  if [[ "$boot" == 1 && "$uptime" =~ ^[0-9]+$ ]] && ((uptime >= 60)); then booted=true; break; fi
  sleep 2
done
if [[ "$booted" != true ]]; then echo 'Boot timed out; no application tests ran.' >&2; exit 1; fi
assert_extra_health extra-boot
observed_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
page_size=$(adb shell getconf PAGE_SIZE | tr -d '\r')
if [[ "$observed_api" != "$api" || ! "$page_size" =~ ^[0-9]+$ ]]; then echo 'Unexpected device API/page size.' >&2; exit 1; fi
if [[ "$profile" == *16k* && "$page_size" != 16384 ]]; then echo 'This device is not actually running 16KB pages.' >&2; exit 1; fi
{
  printf 'source=%s\ntooling=%s\nprofile=%s\napi=%s\npageSize=%s\n' "$(git rev-parse HEAD)" "$GITHUB_SHA" "$profile" "$observed_api" "$page_size"
  adb shell getprop ro.build.fingerprint
  adb shell getprop ro.product.model
} > "$output/device-profile.txt"
adb shell settings put system screen_off_timeout 2147483647
adb shell wm dismiss-keyguard
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$setting" 0; done
# No unlock key, launcher coordinates, dialog dismissal or test retry is used.
# Record the original production binary independently of instrumentation installation.
adb install --no-streaming -r "$output/qualified-preview.apk" 2>&1 | tee "$output/install-qualified-preview.log"
adb shell am start -W -n dev.whiteboard.app/.MainActivity > "$output/qualified-preview-start.txt"
assert_extra_health extra-preview-start
adb shell am force-stop dev.whiteboard.app
verify_installed() {
  local package=$1 expected_apk=$2 location actual expected
  location=$(adb shell pm path "$package" | tr -d '\r')
  location=${location#package:}
  if [[ ! "$location" =~ ^/data/app/[A-Za-z0-9._~+/=-]+/base\.apk$ ]]; then echo 'Unexpected installed APK path.' >&2; return 1; fi
  actual=$(adb shell sha256sum "$location" | awk '{print $1}' | tr -d '\r')
  expected=$(sha256sum "$expected_apk" | awk '{print $1}')
  if [[ "$actual" != "$expected" ]]; then echo "Installed APK differs: $package" >&2; return 1; fi
}
if [[ "$profile" == library-reopen ]]; then
  adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee "$output/install-debug-app.log"
  adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | tee "$output/install-app-test.log"
  verify_installed dev.whiteboard.app.debug app/build/outputs/apk/debug/app-debug.apk
  verify_installed dev.whiteboard.app.debug.test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$output/focused-packages.sha256"
  timeout 600s adb shell am instrument -w -r -e class dev.whiteboard.app.CanvasAccessibilityTest \
    dev.whiteboard.app.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee artifacts/additional-reopen-app-tests.txt
  pwsh -NoProfile -Command '$text=Get-Content artifacts/additional-reopen-app-tests.txt -Raw; if($text -notmatch "OK \(8 tests\)" -or $text -match "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed") { throw "Focused accessibility/reopen cases failed" }'
  assert_extra_health extra-reopen
  printf 'PASS: eight focused CanvasAccessibilityTest cases only; this does not replace the failed full140 result.\n' > "$output/passed.txt"
  exit 0
fi
if [[ "$profile" != release-16k && "$profile" != themes-16k && "$profile" != talkback-service ]]; then
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk > "$output/native-packages.sha256"
# adb -t permits genuine instrumentation APKs. Keep the raw installer failure rather
# than losing it behind the frozen helper's generic installation exception.
adb install --no-streaming -r -t data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk 2>&1 | tee "$output/install-data-test.log"
adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee "$output/install-debug-app.log"
adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | tee "$output/install-app-test.log"
# The frozen helper verifies the data APK SHA. The full UI suite has a separate
# 30-minute bound and streams every case, so a timeout retains its actual progress.
pwsh -NoProfile -File ./tools/Test-Android.ps1 -AdbPath "$(command -v adb)" -Suite data -SkipInstall -ReportPrefix additional-native 2>&1 | tee "$output/native.log"
verify_installed dev.whiteboard.app.debug app/build/outputs/apk/debug/app-debug.apk
verify_installed dev.whiteboard.app.debug.test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 1800s adb shell am instrument -w -r \
  -e notClass dev.whiteboard.app.MemoryQualificationTest,dev.whiteboard.app.FrameQualificationTest,dev.whiteboard.app.RealTalkBackTest \
  dev.whiteboard.app.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee artifacts/additional-native-app-tests.txt
pwsh -NoProfile -Command '
  $ErrorActionPreference="Stop"
  foreach($suite in @{app=140;data=13}.GetEnumerator()) {
    $text=Get-Content "artifacts/additional-native-$($suite.Key)-tests.txt" -Raw
    if($text -notmatch ("OK \("+$suite.Value+" tests\)") -or $text -match "FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED") { throw "Unexpected native inventory or failure" }
  }
'
assert_extra_health extra-native
else
  printf 'Full 140/13 not attempted: explicitly separate %s scope.\n' "$profile" > "$output/native-not-attempted.txt"
fi
talkback_result=0
if [[ "$profile" == talkback-service ]]; then
  adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee "$output/install-debug-app.log"
  adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | tee "$output/install-app-test.log"
  verify_installed dev.whiteboard.app.debug app/build/outputs/apk/debug/app-debug.apk
  verify_installed dev.whiteboard.app.debug.test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$output/service-packages.sha256"
fi
if [[ "$profile" == talkback || "$profile" == talkback-service ]]; then
  pwsh -NoProfile -File ../tools/Test-RealTalkBack.ps1 -AdbPath "$(command -v adb)" 2>&1 | tee "$output/talkback.log" || talkback_result=$?
fi
if [[ "$profile" == talkback-service ]]; then
  assert_extra_health extra-service
  if ((talkback_result == 0)); then
    printf 'PASS: two real TalkBack integration cases only; no full140/13, R8 journey or spoken-audio review.\n' > "$output/passed.txt"
  fi
  exit "$talkback_result"
fi
# Keep regular/service APK hashes above; the opt-in external R8 client is a different pair.
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pwhiteboard.releaseUiClient=true -Pwhiteboard.leakDetection=false \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --console=plain
bash ./gradlew --stop
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$output/ui-client-packages.sha256"
smoke_options=(-CheckRotation -CheckLayers -CheckAlignment -CheckObjectMenu -CheckConnections -CheckMindMaps)
if [[ "$profile" == themes-16k ]]; then smoke_options=(); fi
pwsh -NoProfile -File ../tools/Smoke-Test.ps1 -ArtifactsDirectory artifacts -AdbPath "$(command -v adb)" -Package dev.whiteboard.app \
  -ApkPath "$output/qualified-preview.apk" "${smoke_options[@]}" 2>&1 | tee "$output/release-smoke.log"
pwsh -NoProfile -File ../tools/Test-ThemeAppearance.ps1 -AdbPath "$(command -v adb)" 2>&1 | tee "$output/theme-appearance.log"
assert_extra_health extra-release
if ((talkback_result != 0)); then echo 'Real TalkBack integration failed; retained separate native and R8 evidence is not a whole-job pass.' >&2; exit "$talkback_result"; fi
printf 'PASS: pinned candidate additional qualification (%s), API %s, page size %s.\n' "$profile" "$observed_api" "$page_size" | tee "$output/passed.txt"
