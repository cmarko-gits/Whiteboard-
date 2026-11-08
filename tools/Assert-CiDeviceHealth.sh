#!/usr/bin/env bash
# Observe only: never dismiss an error dialog, clear logcat or retry application tests.
set -euo pipefail
phase=${1:?Health checkpoint is required}
if [[ ! "$phase" =~ ^[a-z-]+$ ]]; then echo 'Invalid health checkpoint.' >&2; exit 2; fi
mkdir -p artifacts
prefix="artifacts/ci-health-$phase"
timeout 15s adb logcat -b events -d -v brief 'am_anr:I' '*:S' > "$prefix-events.txt"
timeout 15s adb logcat -b main -b system -b crash -d -v brief 'ActivityManager:E' 'AndroidRuntime:E' '*:S' > "$prefix-errors.txt"
if ! awk -f tools/Check-CiHealthLog.awk "$prefix-events.txt" "$prefix-errors.txt"; then
  echo "Device health failed at $phase; retained ANR/crash evidence invalidates qualification." >&2
  exit 1
fi
echo "PASS: no recorded ANR or fatal runtime exception at $phase."
