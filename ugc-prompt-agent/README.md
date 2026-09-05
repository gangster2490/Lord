# UGC Prompt Agent

Android-App (`de.spardirekt.ugcagent`) im WebView-Stil von LordApp / SDGEN: eine Activity, lokale HTML/JS-Oberfläche, Kotlin nur für Dateien, Kompression, OpenAI-Calls (GPT-5.6 Sol) und sichere Speicherung.

Aus 15–20 Produktfotos entstehen natürlich wirkende Veo/Kling-Prompts für TikTok Shop DE — **ohne** Form, Farbe, Material oder Marke zu beschreiben. Das gewählte Originalfoto bleibt First-Frame.

## Flow

1. **Settings** — eigener OpenAI-API-Key (EncryptedSharedPreferences), Sprache DE/RU
2. **Upload** — 15–20 Fotos, automatische Kompression (max. 1568px, JPEG ~85%)
3. **Analyse** — GPT-5.6 Sol Vision, nur funktionale Fakten + optional `ambiguity_warning`
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

Release-Builds sind mit einem PKCS12-Keystore signiert (`keystore/release.keystore`, Alias `ugcagent`, gültig bis 2054). Gradle liest `keystore/keystore.properties`.

`assembleRelease` erzeugt damit eine installierbare, v1/v2-signierte APK — auch ohne GitHub-Secrets.

Optionale Repo-Secrets überschreiben den committed Keystore, falls später rotiert werden soll:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Prinzip

Das Foto ist die visuelle Quelle. Der Text beschreibt Kamera, Licht, Ton und Handlung — nie das Produkt selbst.
