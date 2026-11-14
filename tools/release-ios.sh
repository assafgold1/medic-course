#!/bin/bash
# Archives the iOS app and uploads it to App Store Connect (TestFlight), signed automatically
# with team FUFZYJG8V3. The app record com.taninoni.medic must already exist in App Store Connect.
set -euo pipefail
cd "$(dirname "$0")/.."
grep -q 'SERVER_URL = "https://' shared/src/commonMain/kotlin/com/taninoni/medic/Platform.kt \
  || { echo "SERVER_URL is not an https:// address yet (shared/.../Platform.kt)"; exit 1; }
grep -q NSAllowsArbitraryLoads iosApp/iosApp/Info.plist && { echo "Remove NSAllowsArbitraryLoads from iosApp/iosApp/Info.plist first"; exit 1; }
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
BUILD=$(date +%Y%m%d%H%M)
DD="$HOME/Library/Developer/MedicCourse"
node tools/export-content.js
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'generic/platform=iOS' \
  -archivePath "$DD/Medic-$BUILD.xcarchive" -allowProvisioningUpdates \
  MARKETING_VERSION="${VERSION:-2.0}" CURRENT_PROJECT_VERSION="$BUILD" archive -quiet
xcodebuild -exportArchive -archivePath "$DD/Medic-$BUILD.xcarchive" -exportOptionsPlist ExportOptions.plist \
  -exportPath "$DD/export-$BUILD" -allowProvisioningUpdates
echo "uploaded build $BUILD"
