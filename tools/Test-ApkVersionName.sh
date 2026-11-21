#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
parser=tools/Read-ApkVersionName.awk
for version in 0.8.0 0.8.1 1.0.0; do
  result=$(printf "package: name='dev.whiteboard.app' versionCode='18' versionName='%s' platformBuildVersionName='16'\n" "$version" | awk -f "$parser")
  [[ "$result" == "$version" ]] || { echo 'APK version changed during parsing.' >&2; exit 1; }
done
for invalid in \
  "package: name='dev.whiteboard.app' versionCode='18'" \
  "package: versionName=''" \
  "package: versionName='0.8.1' versionName='0.8.0'" \
  "package: versionName='0.8.1 trailing'" \
  "package: versionName='0.8.1';unexpected" \
  "versionName='0.8.1'" \
  $'package: versionName=\'0.8.1\'\npackage: versionName=\'0.8.0\''; do
  if printf '%s\n' "$invalid" | awk -f "$parser" >/dev/null; then
    echo 'Missing or ambiguous APK version was accepted.' >&2
    exit 1
  fi
done
echo 'APK version parsing: release versions accepted; missing, malformed and ambiguous inputs rejected.'
