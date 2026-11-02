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
echo 'PASS: focused/drawn launcher, four rejected states and local SDK/AVD guard.'
