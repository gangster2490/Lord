# UGC Prompt Agent V3

New Android app (`de.spardirekt.ugcagent.v3`) — not a fork of V1/V2.

Kotlin single-activity + WebView UI. All provider calls, keys, images, and storage stay in native Kotlin. JavaScript never receives a raw API key.

## Flow

HOME → NEW PROJECT → UPLOAD (3–20 images) → CONSISTENCY CHECK → PRODUCT SOURCE ANALYSIS → FIRST FRAME → UGC SCENE → SPEECH → FINAL PROMPT → IMPROVE / NEW SCENE / NEW SPEECH → TIKTOK SHOP COMPLIANCE → CAPTION + HASHTAGS → EXPORT → HISTORY

Minimum **3** images. Marketplace UI is ignored. First Frame is an original upload. Prompt never rebuilds product appearance.

## Providers

OpenAI and Gemini, native adapters, centralized `AiModelConfig`. Keys in Android Keystore (AES/GCM).

## Build

GitHub Actions: `.github/workflows/ugc-prompt-agent-v3.yml`

Artifacts:

- `ugc-prompt-agent-v3-debug.apk`
- `ugc-prompt-agent-v3-release.apk`

Release signing secrets (not committed):

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keystore path used in CI: `$GITHUB_WORKSPACE/ugc-prompt-agent-v3/app/release.keystore`

If secrets are missing, CI generates an ephemeral PKCS12 store so `assembleRelease` still produces an installable signed APK.

## Repository note

A standalone GitHub repo `ugc-prompt-agent-v3` could not be created from this agent (write GitHub API is unavailable). The project lives in this repository under `ugc-prompt-agent-v3/`.
