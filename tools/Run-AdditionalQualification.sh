#!/usr/bin/env bash
# Separate, additive matrix. It never changes the frozen application checkout or publishes packages.
set -euo pipefail
profile=${1:?Qualification profile is required}
case "$profile" in
  runtime-16k) api=35; tag=google_apis_ps16k ;;
  talkback) api=36; tag=google_apis_playstore ;;
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
cleanup() {
  result=$?; trap - EXIT
  if [[ -n "$emulator_pid" ]]; then
    timeout 15s adb logcat -d | head -c 4000000 > "$output/logcat.txt" || true
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
bash tools/Assert-CiDeviceHealth.sh extra-boot
observed_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
page_size=$(adb shell getconf PAGE_SIZE | tr -d '\r')
if [[ "$observed_api" != "$api" || ! "$page_size" =~ ^[0-9]+$ ]]; then echo 'Unexpected device API/page size.' >&2; exit 1; fi
if [[ "$profile" == runtime-16k && "$page_size" != 16384 ]]; then echo 'This device is not actually running 16KB pages.' >&2; exit 1; fi
{
  printf 'source=%s\ntooling=%s\nprofile=%s\napi=%s\npageSize=%s\n' "$(git rev-parse HEAD)" "$GITHUB_SHA" "$profile" "$observed_api" "$page_size"
  adb shell getprop ro.build.fingerprint
  adb shell getprop ro.product.model
} > "$output/device-profile.txt"
adb shell settings put system screen_off_timeout 2147483647
adb shell wm dismiss-keyguard
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$setting" 0; done
# No unlock key, launcher coordinates, dialog dismissal or test retry is used.
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk > "$output/native-packages.sha256"
pwsh -NoProfile -File ./tools/Test-Android.ps1 -AdbPath "$(command -v adb)" -Suite all \
  -ExcludeClasses dev.whiteboard.app.MemoryQualificationTest,dev.whiteboard.app.FrameQualificationTest,dev.whiteboard.app.RealTalkBackTest -ReportPrefix additional-native 2>&1 | tee "$output/native.log"
pwsh -NoProfile -Command '
  $ErrorActionPreference="Stop"
  foreach($suite in @{app=140;data=13}.GetEnumerator()) {
    $text=Get-Content "artifacts/additional-native-$($suite.Key)-tests.txt" -Raw
    if($text -notmatch ("OK \("+$suite.Value+" tests\)") -or $text -match "FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED") { throw "Unexpected native inventory or failure" }
  }
'
bash tools/Assert-CiDeviceHealth.sh extra-native
talkback_result=0
if [[ "$profile" == talkback ]]; then
  pwsh -NoProfile -File ../tools/Test-RealTalkBack.ps1 -AdbPath "$(command -v adb)" 2>&1 | tee "$output/talkback.log" || talkback_result=$?
fi
# Keep regular/service APK hashes above; the opt-in external R8 client is a different pair.
bash ./gradlew :app:assembleDebugAndroidTest -Pwhiteboard.releaseUiClient=true -Pwhiteboard.leakDetection=false \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --console=plain
bash ./gradlew --stop
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$output/ui-client-packages.sha256"
pwsh -NoProfile -File ./tools/Smoke-Test.ps1 -AdbPath "$(command -v adb)" -Package dev.whiteboard.app \
  -ApkPath "$output/qualified-preview.apk" -CheckRotation -CheckLayers -CheckAlignment -CheckObjectMenu -CheckConnections -CheckMindMaps 2>&1 | tee "$output/release-smoke.log"
pwsh -NoProfile -File ../tools/Test-ThemeAppearance.ps1 -AdbPath "$(command -v adb)" 2>&1 | tee "$output/theme-appearance.log"
bash tools/Assert-CiDeviceHealth.sh extra-release
if ((talkback_result != 0)); then echo 'Real TalkBack integration failed; retained separate native and R8 evidence is not a whole-job pass.' >&2; exit "$talkback_result"; fi
printf 'PASS: pinned candidate additional qualification (%s), API %s, page size %s.\n' "$profile" "$observed_api" "$page_size" | tee "$output/passed.txt"
