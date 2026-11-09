#!/usr/bin/env bash
# CI-only lifecycle: never inject an unlock key into an undrawn launcher window.
set -euo pipefail

api=${1:?API is required}
profile=${2:?AVD profile is required}
case "$api:$profile" in
  26:pixel_2|26:pixel_c|30:pixel_2|33:pixel_2|36:pixel_2) ;;
  *) echo 'Unsupported CI device configuration.' >&2; exit 2 ;;
esac
if [[ "${GITHUB_ACTIONS:-}" != true || "$(uname -s)" != Linux ]]; then
  echo 'This launcher only runs on Linux GitHub Actions; no local SDK or AVD changes were made.' >&2
  exit 2
fi
: "${ANDROID_HOME:?Android SDK is required}" "${RUNNER_TEMP:?CI temporary directory is required}"
export ANDROID_SERIAL=emulator-5554
export ANDROID_AVD_HOME="$RUNNER_TEMP/whiteboard-avd"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
mkdir -p artifacts "$ANDROID_AVD_HOME"
emulator_pid=''
# The build is already prepared. Release its up-to-3 GiB daemon before software-GPU boot.
bash ./gradlew --stop

cleanup() {
  result=$?
  trap - EXIT
  if [[ -n "$emulator_pid" ]]; then
    if ((result != 0)); then
      mkdir -p artifacts/device-failures
      timeout 10s adb logcat -d > artifacts/device-failures/lifecycle-logcat.txt 2>&1 || true
      timeout 10s adb shell dumpsys window -a > artifacts/device-failures/lifecycle-window.txt 2>&1 || true
      timeout 10s adb shell dumpsys activity lastanr > artifacts/device-failures/lifecycle-anr.txt 2>&1 || true
      timeout 10s adb exec-out screencap -p > artifacts/device-failures/lifecycle-screen.png 2>/dev/null || true
    fi
    timeout 10s adb emu kill >/dev/null 2>&1 || true
    if kill -0 "$emulator_pid" 2>/dev/null; then kill "$emulator_pid" 2>/dev/null || true; fi
  fi
  exit "$result"
}
trap cleanup EXIT

# Retry only external package installation. AVD boot and application suites run once.
installed=false
for attempt in 1 2 3; do
  echo "SDK installation attempt $attempt/3" | tee -a artifacts/ci-sdk-install.log
  if timeout 300s sdkmanager --install emulator "system-images;android-$api;default;x86_64" --channel=0 \
    2>&1 | tee -a artifacts/ci-sdk-install.log; then
    installed=true
    break
  fi
  if ((attempt < 3)); then sleep 5; fi
done
if [[ "$installed" != true ]]; then echo 'SDK installation failed; no device tests ran.' >&2; exit 1; fi
avd=whiteboard-ci
printf 'no\n' | avdmanager create avd --force --name "$avd" --package "system-images;android-$api;default;x86_64" --device "$profile"
config="$ANDROID_AVD_HOME/$avd.avd/config.ini"
# Some inherited profiles request 10 GiB userdata (12 GiB with emulator safety overhead).
# Use the upstream minimum of 6 GiB for this fresh, disposable test device.
avd_options=()
# API 36 System UI can miss its startup deadline in the inherited 2 GiB profile.
# Keep older/minimum-device profiles intact; qualify this modern OS with 3 GiB.
if [[ "$api" == 36 ]]; then avd_options=(-v guest_ram=3072); fi
awk "${avd_options[@]}" -f tools/Configure-CiAvd.awk "$config" > "$config.tmp"
mv "$config.tmp" "$config"
cp "$config" artifacts/ci-avd-config.txt
df -Pk "$ANDROID_AVD_HOME" > artifacts/ci-host-disk.txt
options=(-no-window -gpu software -no-snapshot -noaudio -no-boot-anim)
if [[ "$api" == 36 ]]; then options=(-no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim -feature -Vulkan); fi
# A bare `emulator -version` selects the GUI binary, which needs libpulse even on
# a headless host. Record package metadata; the launched headless binary logs its version.
if [[ -f "$ANDROID_HOME/emulator/source.properties" ]]; then
  cat "$ANDROID_HOME/emulator/source.properties" > artifacts/ci-emulator-version.txt
else
  cat "$ANDROID_HOME/emulator/package.xml" > artifacts/ci-emulator-version.txt
fi
emulator -avd "$avd" -port 5554 "${options[@]}" > artifacts/ci-emulator.log 2>&1 &
emulator_pid=$!

booted=false
deadline=$((SECONDS + 600))
while ((SECONDS < deadline)); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then echo 'Emulator process exited during boot.' >&2; exit 1; fi
  boot=$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [[ "$boot" == 1 ]]; then booted=true; break; fi
  sleep 2
done
if [[ "$booted" != true ]]; then echo 'Emulator boot timed out.' >&2; exit 1; fi
adb shell settings put system screen_off_timeout 2147483647

# sys.boot_completed is earlier than a usable launcher. Wait before any input/Gradle work.
deadline=$((SECONDS + 90))
while :; do
  uptime=$(timeout 5s adb shell cat /proc/uptime | awk '{print int($1)}' | tr -d '\r')
  if [[ ! "$uptime" =~ ^[0-9]+$ ]]; then echo 'Unknown guest uptime.' >&2; exit 1; fi
  if ((uptime >= 60)); then break; fi
  if ((SECONDS >= deadline)); then echo 'Guest startup observation timed out.' >&2; exit 1; fi
  sleep 2
done
adb shell dumpsys activity lastanr > artifacts/ci-startup-anr.txt
if ! grep -qi 'no ANR has occurred since boot' artifacts/ci-startup-anr.txt; then
  echo 'Startup ANR or unknown health output; no application tests ran.' >&2
  exit 1
fi
bash tools/Assert-CiDeviceHealth.sh after-boot

# Use WindowManager's supported binder command, not an input event awaiting window focus.
adb shell wm dismiss-keyguard
ready=false
deadline=$((SECONDS + 120))
while ((SECONDS < deadline)); do
  timeout 10s adb shell dumpsys window -a > artifacts/ci-launcher-window.txt
  if awk -f tools/Check-LauncherWindow.awk artifacts/ci-launcher-window.txt; then ready=true; break; fi
  sleep 2
done
if [[ "$ready" != true ]]; then echo 'Launcher never had a focused, drawn window.' >&2; exit 1; fi
echo "Launcher is focused and drawn at guest uptime $(adb shell cat /proc/uptime)." | tee artifacts/ci-launcher-ready.txt
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$setting" 0.0
done
adb exec-out screencap -p > artifacts/ci-launcher-ready.png
bash tools/Assert-CiDeviceHealth.sh before-tests
# The existing runner rechecks clean boot after the WindowManager/configuration operations.
bash tools/Run-DeviceCi.sh
