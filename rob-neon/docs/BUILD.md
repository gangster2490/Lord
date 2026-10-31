# Build and install

Requirements: JDK 17+ (built with 21), Android SDK platform 35 + build-tools 35.0.0, Python 3 with `pillow numpy scipy` (only to regenerate art).

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties        # not committed
./gradlew testDebugUnitTest assembleDebug                      # APK: app/build/outputs/apk/debug/app-debug.apk
```

Regenerate icons / wallpapers / appfilter / catalog (deterministic, ~2 min):
```bash
python3 tools/verify_components.py <dir-with-lawnicons.xml-and-arctic.xml>   # re-checks every activity
python3 tools/build_assets.py .
python3 tools/verify_assets.py .
```
Screenshots (JVM render of the real UI, not a device): `./gradlew -Drob.shots=$PWD/dist/screenshots testDebugUnitTest`.

Install: `adb install -r dist/ROB-Neon-1.0.0-debug.apk`, or copy the APK to the phone and allow installs from your file manager.

**Signing.** The test build uses the auto-generated Android *debug* key. No keystore or secret is in this repository.
For lasting use create your own key and sign with `apksigner`; an update only keeps app data (the checklist state) if it is signed with the same key and has a higher `versionCode`. Debug-signed and own-key builds cannot update each other: uninstall first.
