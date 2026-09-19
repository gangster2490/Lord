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

## Mehrfach-Transaktions-Scan (ein Screenshot -> mehrere Buchungen)

Ein einzelner Screenshot (z. B. Google Play "Budget & Verlauf" oder eine TikTok-Shop-Liste)
enthält oft mehrere Transaktionen. Die App erkennt sie automatisch als getrennte Kandidaten
statt als einen einzigen Beleg:

1. **`ocr/TextRecognizerEngine.recognizeStructured`** liefert ML-Kit-Zeilen inkl. vertikaler
   Position (`ocr/RecognizedLine`), nicht nur Fließtext.
2. **`ocr/TransactionGrouper`** gruppiert Zeilen anhand des vertikalen Abstands zu Blöcken -
   reine Layout-Heuristik, kein Textverständnis. Innerhalb eines Blocks liegen Zeilen eng
   beieinander, zwischen Blöcken ist die Lücke (Listenpadding) deutlich größer.
3. **`scan/TransactionFieldExtractor`** extrahiert pro Block Datum/Betrag (per
   `AmountParser`/`GermanDateParser`), den Händler (erste Zeile, die keine Monats-/Kopfzeile
   ist, siehe `ocr/IgnoreTerms` - verhindert genau den gemeldeten Bug, dass "Juli" als
   Händler durchgeht) sowie Einnahme- vs. Ausgabe-Typ (`ocr/IncomeTypeSuggester` /
   `ocr/CategorySuggester`).
4. **`scan/MultiTransactionExtractor`** orchestriert das Ganze und wendet gelernte Regeln an
   (`data/repository/RuleLearningRepository`, Room-Tabelle `merchant_rules`): bestätigt der
   Nutzer einmal "ChatGPT Plus -> AI-Dienste -> 100%", wird das beim nächsten Scan automatisch
   vorgeschlagen.
5. **AI-Vision-Fallback** (`ai/`): nur wenn lokal keine oder mehrheitlich unsichere Kandidaten
   gefunden wurden, ruft `ai/BackendAiVisionProvider` den **eigenen Backend-Proxy** auf
   (`ai/BackendAiVisionApi`, Retrofit + kotlinx.serialization). Die Backend-URL wird zur
   Laufzeit in der App konfiguriert (`ai/BackendConfigStore`, DataStore) und ein optionales
   Bearer-Token liegt Android-Keystore-verschlüsselt (`androidx.security:security-crypto`) -
   **kein AI-Anbieter-API-Key steckt im APK**. Erwartetes Proxy-Schema:
   `multipart POST /v1/vision/analyze` -> `{"transactions": [{date, merchant, amount,
   currency, type, category, businessPercentage, status, confidence}]}`, jedes Feld nullable;
   die AI darf nichts erfinden (`ai/dto/AiVisionMapper` überträgt `null` 1:1, rät nie).
   Der eigentliche Proxy-Server ist **nicht** Teil dieses Android-Repos.
6. **`ui/scan/ScanScreen` + `ui/scan/TransactionReviewScreen`** (geteilte
   `ui/scan/ScanViewModel`, gescoped auf den `scan_graph`-Nav-Subgraphen): Titel "X
   Transaktionen erkannt", pro Kandidat Checkbox + alle editierbaren Felder + Confidence,
   Buttons "Alle auswählen", "Auswahl speichern", "Erneut erkennen", "Abbrechen". Gespeichert
   wird ausschließlich über "Auswahl speichern" - nie automatisch.

Erreichbar über den neuen Dashboard-Button "Beleg / Screenshot scannen". Die bisherigen
Einzel-Erfassungsscreens (`ui/income/IncomeCaptureScreen`, `ui/expense/ExpenseCaptureScreen`)
bleiben für die manuelle Erfassung eines einzelnen Belegs erhalten.

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

- **TC01** – `StatusClassifierTest`: eingefrorene Provision wird nicht als `PAID_OUT` erkannt
  (inkl. `REFUNDED`-Status).
- **TC02** – `ExpenseEntryTest`: Geschäftsanteil-Berechnung.
- **TC03** – `AmountParserTest`, `ExportFormatterTest`: deutsche Dezimalzahlen.
- **TC04** – `DuplicateDetectorTest`: Dublettenerkennung.
- **TC06** – `AmountParserTest`: mehrdeutiger Betrag wird nicht erfunden (`null` statt Raten).
- **TC07** – `ExportFormatterTest`: Export filtert korrekt nach Zeitraum.
- **Mehrfach-Transaktions-Scan** – `TransactionGrouperTest` (3 Transaktionen aus einem
  Screenshot -> 3 Blöcke, per Spezifikationsbeispiel Google AI Pro/ChatGPT Plus/TikTok Multi
  Quantity), `TransactionFieldExtractorTest` (Kategorie-/Typ-Erkennung pro Block, "Juli" wird
  nie als Händler übernommen), `CategorySuggesterTest`/`IncomeTypeSuggesterTest` (alle
  vorgegebenen Auto-Kategorien), `IgnoreTermsTest` (Monate/Kopfzeilen), `AiVisionMapperTest`
  (AI-DTO -> Domain erfindet nie fehlende Felder).

**TC05** (Statushistorie) und **TC08** (Backup/Restore-Rundlauf) benötigen eine echte
Room-Datenbank bzw. Datei-I/O und sind als Instrumented Tests (`androidTest`) vorgesehen,
aber in diesem Durchgang nicht ausprogrammiert.

```bash
cd tiktok-shop-buchhaltung
./gradlew testDebugUnitTest
```

> **Build-Status**: `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` und
> `./gradlew assembleRelease` (R8/Minify) wurden tatsächlich ausgeführt (Android SDK 35 +
> Build-Tools 35.0.0) – Ergebnis: **BUILD SUCCESSFUL**, alle 65 Unit-Tests grün. Beim ersten
> Durchlauf des Grundgerüsts wurden mehrere reale Fehler gefunden und behoben, die eine reine
> Code-Review nicht aufgedeckt hätte: fehlende Farbe `ic_launcher_background`
> (aapt2-Linking-Fehler), fehlende `kotlinx-coroutines-play-services`-Abhängigkeit für
> `Task.await()`, `Icons.Filled.ArrowBack` als nicht importierte Extension-Property. Beim
> Mehrfach-Transaktions-Scan wurde zusätzlich ein echter `AmountParser`-Bug gefunden und
> behoben: Zeilen, die Datum und Betrag zusammen enthalten (z. B. "03.07.2026 10,99 €"),
> ließen den Betrag fälschlich als mehrdeutig gelten, weil das Datum wie ein zweiter
> Betragskandidat aussah - jetzt werden Datumsangaben vor der Betragssuche herausgefiltert.

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
- **Backend-Proxy für den AI-Vision-Fallback**: Dieses Repo enthält nur den Android-Client
  (`ai/BackendAiVisionApi`, Vertrag siehe oben) - der eigentliche Proxy-Server, der den
  AI-Anbieter-Key hält und die AI Vision API aufruft, muss separat gebaut/gehostet und seine
  URL in der App unter Einstellungen (`ai/BackendConfigStore`) eingetragen werden. Ohne
  konfiguriertes Backend funktioniert die App weiterhin, nur ohne AI-Fallback (rein lokales
  OCR).
- Ein eigener Einstellungsscreen für `BackendConfigStore` (Backend-URL/Token eingeben) fehlt
  noch - aktuell nur über die Klasse selbst nutzbar.
- Instrumented Test für die Room-Migration `MIGRATION_1_2` (`merchant_rules`-Tabelle).
