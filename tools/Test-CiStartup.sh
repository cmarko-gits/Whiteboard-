#!/usr/bin/env bash
set -euo pipefail

assert_window() {
  expected=$1
  actual=0
  awk -f tools/Check-LauncherWindow.awk || actual=$?
  if [[ "$actual" != "$expected" ]]; then
    echo "Launcher readiness check returned $actual; expected $expected." >&2
    exit 1
  fi
}

assert_window 0 <<'WINDOW'
  Window #0 Window{a u0 com.android.launcher3/.Launcher}:
    mDrawState=HAS_DRAWN
  Window #1 Window{b u0 StatusBar}:
    mDrawState=HAS_DRAWN
  mCurrentFocus=Window{a u0 com.android.launcher3/.Launcher}
WINDOW

# A drawn System UI surface must not make an undrawn launcher look ready.
assert_window 1 <<'WINDOW'
  Window #0 Window{a u0 com.android.launcher3/.uioverrides.QuickstepLauncher}:
    mDrawState=DRAW_PENDING
  Window #1 Window{b u0 StatusBar}:
    mDrawState=HAS_DRAWN
  mCurrentFocus=Window{a u0 com.android.launcher3/.uioverrides.QuickstepLauncher}
WINDOW

assert_window 1 <<'WINDOW'
  Window #0 Window{a u0 com.android.launcher3/.Launcher}:
    mDrawState=HAS_DRAWN
  mCurrentFocus=null
WINDOW

assert_window 1 <<'WINDOW'
  Window #0 Window{a u0 com.android.launcher3/.Launcher}:
    mDrawState=HAS_DRAWN
  mCurrentFocus=Window{b u0 StatusBar}
WINDOW

assert_window 1 <<'WINDOW'
Unknown or empty readiness format
WINDOW

status=0
GITHUB_ACTIONS=false bash tools/Run-CiEmulator.sh 26 pixel_2 || status=$?
if ((status != 2)); then echo 'Local SDK/AVD guard did not refuse execution.' >&2; exit 1; fi

# Test the pure config filter without touching any SDK or real AVD directory.
configured=$(awk -f tools/Configure-CiAvd.awk <<'CONFIG'
hw.device.name=pixel_2
disk.dataPartition.size=10G
hw.cpu.ncore=8
 hw.keyboard = yes
disk.dataPartition.size = 20G
hw.lcd.width=1080
CONFIG
)
expected=$'hw.device.name=pixel_2\nhw.lcd.width=1080\nhw.cpu.ncore=2\nhw.keyboard=no\ndisk.dataPartition.size=6G'
if [[ "$configured" != "$expected" ]]; then echo 'Unexpected CI AVD configuration.' >&2; exit 1; fi
again=$(printf '%s\n' "$configured" | awk -f tools/Configure-CiAvd.awk)
if [[ "$again" != "$configured" ]]; then echo 'CI AVD configuration is not idempotent.' >&2; exit 1; fi
echo 'PASS: launcher readiness, local SDK/AVD guard and bounded/idempotent CI device configuration.'

check_health() {
  expected=$1
  actual=0
  awk -f tools/Check-CiHealthLog.awk || actual=$?
  if [[ "$actual" != "$expected" ]]; then echo "Health check returned $actual; expected $expected." >&2; exit 1; fi
}
check_health 0 <<'LOG'
--------- beginning of system
E/ActivityManager( 643): Some unrelated informational error
LOG
# The actual failure was recorded before launcher readiness, while lastanr remained clean.
check_health 1 <<'LOG'
E/ActivityManager( 643): ANR in com.android.systemui
E/ActivityManager( 643): Reason: Process failed to complete startup
LOG
check_health 1 <<'LOG'
I/am_anr  ( 643): [0,976,com.android.systemui,0,failed to complete startup]
LOG
check_health 1 <<'LOG'
E/AndroidRuntime( 123): FATAL EXCEPTION: main
LOG
echo 'PASS: System UI startup ANR, event-buffer ANR and fatal runtime errors block qualification.'
