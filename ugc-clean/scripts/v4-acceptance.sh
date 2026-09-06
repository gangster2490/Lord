#!/usr/bin/env bash
# V4 acceptance: verify the current UGC Clean app. Do not add features here.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== V4 source audit =="
test -f app/src/main/java/de/spardirekt/ugcclean/gen/PromptComposer.kt
test -f app/src/main/java/de/spardirekt/ugcclean/net/GeminiClient.kt
test -f app/src/test/java/de/spardirekt/ugcclean/gen/V4AcceptanceTest.kt
grep -q 'applicationId = "de.spardirekt.ugcclean"' app/build.gradle.kts
grep -q 'INTERNET' app/src/main/AndroidManifest.xml
if grep -R -n --include='*.kt' --include='*.js' --include='*.html' 'localStorage.getItem' app/src/main; then
  echo "FAIL: localStorage"; exit 1
fi
echo "SOURCE AUDIT PASS"

echo "== V4 unit tests =="
./gradlew testDebugUnitTest --stacktrace

echo "== V4 APKs =="
./gradlew assembleDebug assembleRelease --stacktrace
DEBUG_APK=$(ls app/build/outputs/apk/debug/*.apk | head -n 1)
RELEASE_APK=$(ls app/build/outputs/apk/release/*.apk | head -n 1)
test -s "$DEBUG_APK"
test -s "$RELEASE_APK"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT=$(ls -d "$SDK"/build-tools/*/aapt 2>/dev/null | sort | tail -n 1)
SIGNER=$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort | tail -n 1)
"$AAPT" dump badging "$DEBUG_APK" | grep -q "package: name='de.spardirekt.ugcclean.debug'"
"$AAPT" dump permissions "$RELEASE_APK" | grep -q INTERNET
"$SIGNER" verify --print-certs "$RELEASE_APK" >/tmp/ugc-clean-v4-signer.txt
echo "DEBUG_APK=$DEBUG_APK"
echo "RELEASE_APK=$RELEASE_APK"
echo "V4 ACCEPTANCE PASS"
