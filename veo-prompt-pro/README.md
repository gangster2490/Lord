# Veo Prompt Pro

Native Kotlin + Jetpack Compose Android app. Analyzes product and listing photos, then generates an exact **8-second VEO 3.1** production prompt (plus voiceover, title, and hashtags) for copy into Gemini / Veo.

This app does **not** generate video. You copy the prompt into Veo.

## Version

| Field | Value |
|---|---|
| applicationId | `de.spardirekt.agents.pro` |
| versionName | `3.2.18` |
| versionCode | 23 |
| minSdk | 26 |
| targetSdk | 35 |

## Build

```bash
cd veo-prompt-pro
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if needed
./gradlew assembleDebug testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Flow

1. Settings → paste OpenAI API key (stored in EncryptedSharedPreferences / Android Keystore)
2. Create → pick product photos (Photo Picker, up to 15)
3. Optional wish + voice/mode
4. Generate → staged photo analysis → 8s VEO prompt
5. Result → copy / share package; History restores drafts and finished projects

To exercise generate → Result without a live OpenAI key, paste `sk-demo` as the API key (Settings or the generate dialog). The app runs the same pipeline stages with a local demo model. There is no extra Settings toggle.

Models: GPT-5.6 Sol (default), Terra, Luna.

## Screens

- **Create** — photos, creative mode, generate
- **History** — ready / draft / error projects
- **Settings** — API key add / replace / test / remove
- **Result** — VEO prompt, voiceover, title, 5 hashtags

## Visual baseline

The current light-lavender + navy-card + violet→blue gradient UI is locked. See [docs/visual-baseline/](docs/visual-baseline/README.md). Do not restyle `Theme.kt` unless a new look is explicitly requested.
