# Both inputs are bounded, filtered logcat snapshots from this fresh CI boot.
# dumpsys activity lastanr can report clean even while a System UI startup ANR is visible.
/ANR in / || /[[:space:]\/]am_anr[[:space:](]/ || /FATAL EXCEPTION/ { unhealthy = 1 }
END { exit unhealthy ? 1 : 0 }
