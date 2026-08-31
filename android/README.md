# TikTok Shop Creator for Android

Native Kotlin + Jetpack Compose app. Same product as the web client: upload product photos, generate German TikTok Shop content (title, hooks, hashtags, Veo 3.1 prompt, live script) through the existing CORS proxy.

## Requirements

- Android Studio Ladybug / Koala or newer, or JDK 17+
- Android SDK 35
- Physical device or emulator (API 26+)

## Build

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if not already set
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected device:

```bash
./gradlew installDebug
```

## Tests

```bash
./gradlew testDebugUnitTest
```

## Setup

1. Start the proxy from the repo root: `cd proxy && node server.js`
2. Open the app and paste your Anthropic API key (kept in memory only, never persisted).
3. Set **Proxy URL**:
   - Emulator → `http://10.0.2.2:3001` (or `adb reverse tcp:3001 tcp:3001` then `http://127.0.0.1:3001`)
   - Physical device → `http://<LAN-IP>:3001` or your deployed HTTPS proxy
4. Pick at least **Bild 1** (product photo), then tap **Content generieren**.

Package id: `de.spardirekt.tiktokshop`
