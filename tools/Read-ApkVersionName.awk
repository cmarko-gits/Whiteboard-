# Read the expected UI version from aapt2 badging, never from the UI under test.
/^package: / {
    packages++
    for (field = 2; field <= NF; field++) {
        if ($field ~ /^versionName=/) {
            versions++
            version = $field
            if (version !~ /^versionName='[0-9]+\.[0-9]+\.[0-9]+'$/) invalid = 1
            sub(/^versionName='/, "", version)
            sub(/'$/, "", version)
        }
    }
}
END {
    if (packages != 1 || versions != 1 || invalid) exit 1
    print version
}
