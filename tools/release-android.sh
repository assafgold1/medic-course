#!/bin/bash
# Builds a signed Android App Bundle for Google Play (needs keystore.properties at the repo root).
# Output: androidApp/build/outputs/bundle/release/androidApp-release.aab
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f keystore.properties ] || { echo "keystore.properties is missing"; exit 1; }
grep -q 'SERVER_URL = "https://' shared/src/commonMain/kotlin/com/taninoni/medic/Platform.kt \
  || { echo "SERVER_URL is not an https:// address yet (shared/.../Platform.kt)"; exit 1; }
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
CODE=$(date +%y%m%d%H)
node tools/export-content.js
./gradlew :androidApp:bundleRelease -PversionCode="$CODE" -PversionName="${VERSION:-2.0}" --console=plain -q
echo "versionCode $CODE -> androidApp/build/outputs/bundle/release/androidApp-release.aab"
