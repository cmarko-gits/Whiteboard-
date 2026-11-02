# Success requires a focused launcher and its own drawn window, not a drawn System UI surface.
/mCurrentFocus=/ && /com\.android\.launcher3\// { focused = 1 }
/^[[:space:]]*Window #[0-9]+ Window\{/ {
    launcher_window = ($0 ~ /com\.android\.launcher3\//)
}
launcher_window && /mDrawState=HAS_DRAWN/ { drawn = 1 }
END { exit !(focused && drawn) }
