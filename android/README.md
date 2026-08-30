# TikTok Shop Creator — Native Android

Modern native Android rebuild of the legacy TikTok Shop Creator / VEO Photo Cleaner web app.

**Stack:** Kotlin 2.0 · Jetpack Compose · Material 3 · OkHttp · DataStore · Coil · coroutines

Package ID: `de.spardirekt.tiktokshop`  
minSdk 26 · targetSdk 35

## Screens

| Tab | Source of truth | What it does |
| --- | --- | --- |
| **Creator** | Root `index.html` / `app.js` | Upload 1–3 product photos + optional spec screenshot, pick video style/tone, call the existing Anthropic CORS proxy, render all 12 result cards with copy actions |
| **VEO Cleaner** | `veo.html` | Analyze up to 9 product photos with GPT-4o, generate a locked 9:16 DALL·E reference still, history + download |
| **Einstellungen** | both | Persist API keys, proxy URL, models, and creator defaults on-device |

The Creator system prompt, Veo product-lock language, 8-second rule, and copy-bundle formats (`Master`, `Veo Komplett`, `Alles kopieren`) are ported verbatim from the web app.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # or your JDK 17+
export ANDROID_HOME=$HOME/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Runtime

Creator still talks to the repo's Node proxy (`proxy/server.js`):

```bash
cd ../proxy && node server.js
```

- **Emulator:** default proxy `http://10.0.2.2:3001` (host loopback)
- **Physical device:** set the proxy to your machine's LAN URL, e.g. `http://192.168.1.20:3001`

The Anthropic key is sent per request as `x-api-key-fwd` and is never uploaded anywhere else. VEO Cleaner calls OpenAI directly with the locally stored key.
