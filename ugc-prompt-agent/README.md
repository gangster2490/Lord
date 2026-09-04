# UGC Prompt Agent

Android-App (`de.spardirekt.ugcagent`) im WebView-Stil von LordApp / SDGEN: eine Activity, lokale HTML/JS-Oberfläche, Kotlin nur für Dateien, Kompression, Gemini-Calls und sichere Speicherung.

Aus 15–20 Produktfotos entstehen natürlich wirkende Veo/Kling-Prompts für TikTok Shop DE — **ohne** Form, Farbe, Material oder Marke zu beschreiben. Das gewählte Originalfoto bleibt First-Frame.

## Flow

1. **Settings** — eigener Gemini-API-Key (EncryptedSharedPreferences), Sprache DE/RU
2. **Upload** — 15–20 Fotos, automatische Kompression (max. 1568px, JPEG ~85%)
3. **Analyse** — Gemini Vision, nur funktionale Fakten + optional `ambiguity_warning`
4. **Similarity-Check** — First-Frame gegen die übrigen Fotos; bei Abweichung Warnung, kein Auto-Fix
5. **Szenen** — 3–5 Ideen aus dem UGC-Pattern-Pool (nie dieselbe Kombination hintereinander)
6. **Prompt-Builder** — 9:16, max. 8 Sekunden, ein Mikro-Moment; Button **Verbessern** (zweiter Pass)
7. **Compliance** — TikTok-DE-Verbotsliste **und** Pflicht-Check auf „Werbung“/„Anzeige“ (kein Auto-Insert)
8. **Export** — Prompt + First-Frame-Hinweis, Copy, lokaler Verlauf

## Build

Kein lokaler SDK-Build nötig. GitHub Actions (`.github/workflows/build.yml`) erzeugt die APK.

```bash
cd ugc-prompt-agent
./gradlew assembleRelease
```

Unit-Tests (Compliance, Szenen-Pool, Similarity):

```bash
cd ugc-prompt-agent
./gradlew testDebugUnitTest
```

## Signing (Actions)

Repo-Secrets in `gangster2490/ugc-prompt-agent` bzw. diesem Repo:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keystore einmalig:

```bash
keytool -genkey -v -keystore release.keystore -alias ugcagent \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore
```

Ohne Secrets baut der Workflow eine debug-signierte Release-APK (installierbar, analog SD Agents). Mit Secrets wird die injizierte Release-Signatur verwendet.

## Prinzip

Das Foto ist die visuelle Quelle. Der Text beschreibt Kamera, Licht, Ton und Handlung — nie das Produkt selbst.
