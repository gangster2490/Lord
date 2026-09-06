# UGC Clean

New Android app (`de.spardirekt.ugcclean`) built from scratch. It does not reuse Veo Prompt Pro, TikTok Shop Creator, or UGC Prompt Agent V1–V3.

Native Kotlin + Jetpack Compose. One flow:

1. Settings → OpenAI and Gemini key cards, pick which one START uses (or `sk-demo`)
2. Create → 3–15 product photos
3. Deutsch or Русский
4. **START** once
5. Copy **VIDEO PACKAGE**, DETAILS, VIDEO PROMPT, CAPTION, HASHTAGS, or **COPY ALL** — then SAVE PROJECT

The app does not generate video. You copy the 8.0s Veo prompt into Gemini / Veo.

## Why this is a clean rebuild

Previous apps stacked extra engines until prompts leaked kitchen/microwave/fishing language onto other products and duplicated Veo headings. UGC Clean does three things only:

- One vision call → a `ProductPlan` JSON for **this** product
- A local `PromptComposer` that always emits the same 12 headings, once each
- One caption/hashtag call, then TikTok Shop disclosure (`Werbung` / `Реклама`)

Providers (Settings only, not on START):

- OpenAI: `gpt-5.6-sol` → `gpt-5.6-terra` → `gpt-4o`
- Gemini: `gemini-2.5-flash` → `gemini-2.0-flash` → `gemini-1.5-flash`

Keys stay in EncryptedSharedPreferences, one per provider. If the selected provider fails and the other key is saved, START retries once. `sk-demo` still runs locally with no network.

No leftover templates. Demo mode (`sk-demo`) is first-frame locked and category-unknown, so it cannot leak another product family.

## Build

```bash
cd ugc-clean
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if needed
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions: `.github/workflows/ugc-clean.yml`

Artifacts:

- `ugc-clean-debug.apk`
- `ugc-clean-release.apk`

If release signing secrets are missing, CI uses the debug keystore so `assembleRelease` still produces an installable APK.

| Field | Value |
|---|---|
| applicationId | `de.spardirekt.ugcclean` |
| versionName | `1.0.0` |
| minSdk | 26 |
| targetSdk | 35 |
