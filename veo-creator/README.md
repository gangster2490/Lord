# Veo Creator

Native Android app (Kotlin + Jetpack Compose + Material 3) that generates AI videos
directly from your phone using Google's official Gemini API / Veo 3.1 video generation
endpoints. No backend, no proxy — the app talks straight to
`https://generativelanguage.googleapis.com`.

Package: `com.rob.veocreator` · minSdk 26 · compileSdk/targetSdk 35

## Features

- Text-to-Video and Image-to-Video generation with Veo 3.1 / 3.1 Fast / 3.1 Lite
- Multi-image upload (Android Photo Picker + Files) with a primary "starting image"
  and up to 3 extra images sent as Veo reference images for product consistency
- Gemini-vision powered image analysis: classifies each photo's role (main photo,
  detail, packaging, spec sheet, dimension diagram, ...) and extracts only clearly
  legible spec text, without ever asking Veo to render that text on screen unless
  you explicitly enable the "Text overlays" toggle
- Aspect ratio (9:16 / 16:9), duration (4/6/8s) and resolution (720p/1080p/4K)
  pickers that disable combinations the selected model doesn't support
  (1080p/4K require 8s, matching Google's documented constraints)
- Long-running operation submit + poll (~10s interval) + cancel + timeout
- Media3/ExoPlayer playback with fullscreen, save to `Movies/VeoCreator` via
  MediaStore, and native Share sheet
- Room-backed History (last 100 generations) with delete/share
- Gemini API key stored only on-device via `EncryptedSharedPreferences`
  (AES-256-GCM), masked in the UI, never logged

## Build

```bash
cd veo-creator
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Setup

1. Open the app, go to **Settings**.
2. Tap **Get Gemini API Key** to open Google AI Studio and create a key.
3. Paste it into the field and tap **Save**. Use **Test Connection** to confirm it works.
4. Go to **Create**, pick Text-to-Video or Image-to-Video, write a prompt, and tap **Generate**.

## Notes on the Veo REST API used

- `POST /v1beta/models/{model}:predictLongRunning` to start generation
- `GET /v1beta/{operation.name}` to poll (every ~10s)
- Video is downloaded from the returned `video.uri` with the `x-goog-api-key` header
- Model ids: `veo-3.1-generate-preview`, `veo-3.1-fast-generate-preview`,
  `veo-3.1-lite-generate-preview`
