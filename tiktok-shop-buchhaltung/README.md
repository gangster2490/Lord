# TikTok Shop Buchhaltung (Android MVP)

Android-App (Kotlin + Jetpack Compose) für die Buchhaltung eines TikTok-Shop-Affiliate/
Kleinunternehmers in Deutschland. Belege werden fotografiert/importiert, per On-Device-OCR
(Google ML Kit) ausgewertet, vom Nutzer bestätigt und lokal gespeichert. Kein Cloud-Zwang,
keine automatische Steuerberatung – siehe `docs/CLAUDE_MASTER_PROMPT.md`.

Dieses Projekt ist unabhängig vom bereits vorhandenen `android/`-Projekt in diesem Repo
(„TikTok Shop Creator“, ein Content-Generator, package `de.spardirekt.tiktokshop`) – andere
Zielgruppe, anderes Produkt.

## Spezifikation

Die vollständige Spezifikation liegt unverändert unter [`docs/`](docs/) (Produktpaket, wie
vom Nutzer bereitgestellt): `00_START_HERE.md`, `CLAUDE_MASTER_PROMPT.md`,
`PRODUCT_REQUIREMENTS.md`, `DATA_MODEL.json`, `OCR_RULES.md`, `TAX_LOGIC_DE.md`,
`TEST_CASES.md`, `UX_FLOW.md`, `sample_transactions.csv`, `examples/`.

## Architektur

- **Kotlin + Jetpack Compose**, MVVM, manuelle Service-Locator-DI (`TiktokBuchhaltungApplication`) statt Hilt – für den MVP-Umfang bewusst einfach.
- **Room** für lokale Persistenz (`IncomeEntry`, `ExpenseEntry`, `StatusHistory`).
- **Google ML Kit Text Recognition** (on-device) für OCR, gekapselt in `ocr/TextRecognizerEngine`.
- Reine, Android-unabhängige Business-Logik in `ocr/` (Betrags-/Datums-/Status-Parsing),
  `domain/` (Dashboard-Aggregation, Duplikaterkennung) und `export/ExportFormatter` – dadurch
  mit einfachen JUnit-Tests (ohne Emulator) testbar.
- Belege werden unveränderlich in den privaten App-Speicher kopiert (`data/receipts/ReceiptStorage`),
  nie überschrieben.
- Export als CSV/ZIP (`export/ExportManager`), Backup/Restore als ZIP mit JSON-Manifest
  (`export/BackupManager`).

## Wichtigste fachliche Regel

`IncomeStatus` trennt strikt zwischen angezeigter Provision (`ACCRUED`), `FROZEN`,
`AVAILABLE` und tatsächlich `PAID_OUT`. Die OCR-Statuserkennung (`ocr/StatusClassifier`)
prüft `FROZEN`-Indikatoren *vor* `AVAILABLE`-Indikatoren, damit ein Screenshot wie im
Beispiel (`docs/examples/tiktok_frozen_payout_example.png`: „eingefroren“ + „Verfügbare
Auszahlung 702,61 €“ + „Abheben nicht möglich“) korrekt als `FROZEN` und **nicht** als
`PAID_OUT` erkannt wird (TC01).

## Dokumentierte Annahmen (MVP-Vereinfachungen)

Der Master-Prompt erlaubt ausdrücklich, bei Unklarheiten die einfachste robuste
MVP-Lösung zu wählen und die Annahme zu dokumentieren:

1. **Kamera**: Statt einer eigenen CameraX-Vorschau wird die System-Kamera-App über
   `ActivityResultContracts.TakePicture()` aufgerufen. Funktional identisch für den
   Nutzer (Foto aufnehmen → Beleg importieren), deutlich weniger Code/Fehlerfläche.
   CameraX kann bei Bedarf später nachgerüstet werden, ohne das Datenmodell zu ändern.
2. **Geldbeträge** werden intern als ganzzahlige Cent-Werte (`Long`) statt als Fließkomma
   gespeichert, um Rundungsfehler beim Parsen/Export auszuschließen (`data/model/Money.kt`).
3. **Ausgaben-Status im allgemeinen CSV-Export**: Das Datenmodell kennt für Ausgaben keinen
   Auszahlungsstatus (nur `confirmed`). Die Status-Spalte im allgemeinen Export enthält daher
   `BESTAETIGT`/`UNBESTAETIGT` statt eines TikTok-spezifischen Werts.
4. **Kein Hilt/Dagger**: einfache manuelle DI über die `Application`-Klasse, angemessen für
   den MVP-Umfang.
5. **Biometrische Sperre und XLSX/PDF-Export** sind laut PRD „Soll“- bzw. „Später möglich“-
   Funktionen und in diesem MVP-Durchgang nicht implementiert (Abhängigkeit für Biometrie
   ist bereits eingebunden, aber nicht verdrahtet).
6. **Dublettenerkennung** blockiert das Speichern nicht, sondern zeigt eine Warnung; ein
   zweites Bestätigen speichert trotzdem (TC04: „Warnung vor erneutem Speichern“).

## Tests

Reine JUnit-Tests (kein Android-Gerät/Emulator nötig) unter `app/src/test/...` decken ab:

- **TC01** – `StatusClassifierTest`: eingefrorene Provision wird nicht als `PAID_OUT` erkannt.
- **TC02** – `ExpenseEntryTest`: Geschäftsanteil-Berechnung.
- **TC03** – `AmountParserTest`, `ExportFormatterTest`: deutsche Dezimalzahlen.
- **TC04** – `DuplicateDetectorTest`: Dublettenerkennung.
- **TC06** – `AmountParserTest`: mehrdeutiger Betrag wird nicht erfunden (`null` statt Raten).
- **TC07** – `ExportFormatterTest`: Export filtert korrekt nach Zeitraum.

**TC05** (Statushistorie) und **TC08** (Backup/Restore-Rundlauf) benötigen eine echte
Room-Datenbank bzw. Datei-I/O und sind als Instrumented Tests (`androidTest`) vorgesehen,
aber in diesem Durchgang nicht ausprogrammiert.

```bash
cd tiktok-shop-buchhaltung
./gradlew testDebugUnitTest
```

> **Build-Status**: `./gradlew assembleDebug` und `./gradlew testDebugUnitTest` wurden in
> diesem Durchgang tatsächlich ausgeführt (Android SDK 35 + Build-Tools 35.0.0 nachträglich
> installiert) – Ergebnis: **BUILD SUCCESSFUL**, alle 33 Unit-Tests grün, eine lauffähige
> `app-debug.apk` wurde erzeugt. Dabei wurden mehrere reale Fehler gefunden und behoben, die
> eine reine Code-Review nicht aufgedeckt hätte: fehlende Farbe `ic_launcher_background`
> (aapt2-Linking-Fehler), fehlende `kotlinx-coroutines-play-services`-Abhängigkeit für
> `Task.await()` im OCR-Wrapper, und `Icons.Filled.ArrowBack` als nicht importierte
> Extension-Property (falsch als vollqualifizierter Pfad referenziert).

## Build

```bash
cd tiktok-shop-buchhaltung
echo "sdk.dir=$ANDROID_HOME" > local.properties   # falls nicht gesetzt
./gradlew assembleDebug
./gradlew installDebug
```

Package id: `de.tiktokshop.buchhaltung`

## Offene Punkte für die nächste Iteration

- Instrumented Tests für TC05/TC08 (Room + Backup-Rundlauf) und Compose-UI-Tests.
- CameraX-Vorschau nachrüsten, falls die System-Kamera-App als UX nicht ausreicht.
- XLSX-/PDF-Export, biometrische App-Sperre (beides „Soll“/„Später“ laut PRD).
- Wiederkehrende Abo-Ausgaben, Monatsvergleich (PRD 5. Soll-Funktionen).
