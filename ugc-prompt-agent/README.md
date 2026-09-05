# UGC Prompt Agent v2

Android-App (`de.spardirekt.ugcagent`) im WebView-Stil von LordApp / SDGEN: eine Activity, lokale HTML/JS-Oberfläche, Kotlin nur für Dateien, Kompression, OpenAI-Calls (GPT-5.6 Sol) und sichere Speicherung.

Aus 15–20 Produktfotos entstehen natürlich wirkende Veo/Kling-Prompts für TikTok Shop DE — **ohne** Form, Farbe, Material oder Marke zu beschreiben. Das gewählte Originalfoto bleibt First-Frame.

## v2 Final Build

| Feld | Wert |
|---|---|
| applicationId | `de.spardirekt.ugcagent` |
| versionName | `2.0.0` |
| versionCode | 2 |
| minSdk | 26 |
| targetSdk | 34 |

Neu gegenüber v1:

- Prompt-Gate: max. 80 Wörter, Markdown/Anführungszeichen weg, Warnung bei Form/Farbe/Material/Marke (kein Auto-Fix)
- Ein automatischer Retry bei Netzwerk/Timeout zusätzlich zur UI-Retry-Option
- Fotos einzeln entfernen oder alle leeren, First-Frame bleibt erhalten
- Verlauf gruppiert **pro Produkt** (`use_case`)
- Live-Compliance auf dem VEO-Tab (Verbotsliste + Werbung/Anzeige, kein Auto-Insert)
- Fullscreen-Theme entfernt, damit Notch, Statusleiste und Tastatur in Settings funktionieren
- Similarity-Ausreißer an den Thumbnails markiert

## Flow

1. **Settings** — eigener OpenAI-API-Key (EncryptedSharedPreferences), Sprache DE/RU
2. **Upload** — 15–20 Fotos, automatische Kompression (max. 1568px, JPEG ~85%)
3. **Analyse** — GPT-5.6 Sol Vision, nur funktionale Fakten + optional `ambiguity_warning`
4. **Similarity-Check** — First-Frame gegen die übrigen Fotos; bei Abweichung Warnung, kein Auto-Fix
5. **Szenen** — 3–5 Ideen aus dem UGC-Pattern-Pool (nie dieselbe Kombination hintereinander)
6. **Prompt-Builder** — 9:16, max. 8 Sekunden, ein Mikro-Moment; Button **Verbessern** (zweiter Pass)
7. **Compliance** — TikTok-DE-Verbotsliste **und** Pflicht-Check auf „Werbung“/„Anzeige“ (kein Auto-Insert)
8. **Export** — Prompt + First-Frame-Hinweis, Copy, lokaler Verlauf pro Produkt

## Build

Kein lokaler SDK-Build nötig. GitHub Actions (`.github/workflows/build.yml`) erzeugt die signierte APK.

```bash
cd ugc-prompt-agent
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

## Signing (Actions)

Release-Builds sind mit einem PKCS12-Keystore signiert (`keystore/release.keystore`, Alias `ugcagent`). Gradle liest `keystore/keystore.properties`.

Optionale Repo-Secrets überschreiben den committed Keystore:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## Prinzip

Das Foto ist die visuelle Quelle. Der Text beschreibt Kamera, Licht, Ton und Handlung — nie das Produkt selbst.
