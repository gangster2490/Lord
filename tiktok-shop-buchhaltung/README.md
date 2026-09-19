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

## TikTok-Excel-Import ("TikTok Excel importieren")

Importiert offizielle TikTok-Shop-Earnings-Reports (`.xlsx`, ein oder mehrere gleichzeitig)
als `IncomeEntry`s - ohne OCR, direkt aus der Tabelle:

1. **`data/xlsx/XlsxReader`**: minimaler, selbst geschriebener OOXML-SpreadsheetML-Reader
   (ZIP + `javax.xml.parsers`/`org.w3c.dom`, beides Standard-Java/Android-APIs) - **bewusst
   ohne Apache POI**, da `poi-ooxml` auf Android AWT-/ImageIO-Klassen nachzieht, die dort nicht
   existieren, und eine häufige Absturzursache ist. Behandelt XLSX' "sparse rows" korrekt
   (komplett leere Zeilen fehlen im XML ganz und dürfen nicht einfach nach Position
   durchnummeriert werden, siehe Testfall "reads Sheet1 header row").
2. **`data/importer/TikTokExcelParser`**: sucht die Kopfzeile dynamisch (sie steht real in
   Zeile 6, davor stehen Disclaimer/Metadaten wie "Date period"/"Creator name"), liest Spalten
   über ihren Namen statt feste Position (Anzahl VAT-Spalten variiert), mappt
   `Type of earnings` über `TikTokEarningTypeMapper` (Standard/Shop ads commission -> Provision,
   Seller/Affiliate partner bonus -> Bonus, Rewards -> Rewards). Beträge sind reine
   Dezimalstrings mit Punkt (z. B. "2.37"), kein deutsches Komma-Format.
3. **Dublettenschutz (`data/repository/ImportRepository`, §8)**: Schlüssel ist die Kombination
   aus TikTok **Transaction ID + Type of earnings**, nicht die Transaction ID allein (siehe
   "Reale Datenbefunde" unten - wichtige Abweichung von der ursprünglichen Annahme "Transaction
   ID ist eindeutig"). `preview()` liest+hasht die Datei und prüft gegen die Datenbank, OHNE
   etwas zu speichern; erst `commit()` (nach Nutzerbestätigung) persistiert. Dateien mit
   identischem Hash werden als "bereits importiert" erkannt (`SourceDocument.hash`).
4. **`data/model/SourceDocument`**: jede importierte Datei wird unverändert und dauerhaft unter
   `filesDir/imports/` gesichert (nie gelöscht, auch nicht mit den daraus erzeugten Buchungen) -
   Audit-Trail.
5. **`data/model/ImportBatch`** ("Import-Verlauf"): pro Importversuch (auch bei 0 neuen
   Zeilen) ein Protokolleintrag mit Dateiname, Zeitraum, Zeilenzahl, neu/Dubletten, Summe.
6. **UI**: `ui/importer/ImportExcelScreen` (Mehrfachauswahl über
   `ActivityResultContracts.OpenMultipleDocuments`, pro Datei eine Vorschau-Karte mit "X neue
   Transaktionen / X bereits vorhanden / Gesamteinnahmen / Zeitraum", Fehler bleiben sichtbar
   bis der Nutzer explizit auf "Fertig" tippt - kein automatisches Wegnavigieren) und
   `ui/importer/ImportHistoryScreen`.

Erreichbar über die neuen Dashboard-Buttons "TikTok Excel importieren" und "Import-Verlauf".

### Datenbefunde aus der Entwicklung (Test-Fixtures sind anonymisiert)

Parser und Importer wurden während der Entwicklung gegen alle 8 vom Nutzer bereitgestellten
echten Monats-Reports (Januar-August) geprüft, bevor sie fertiggestellt wurden. Die dabei
gefundenen strukturellen Eigenheiten sind wichtig für die Korrektheit und deshalb weiterhin
über anonymisierte Fixtures abgedeckt (`app/src/test/resources/tiktok_reports/`,
Erzeugungsskript nicht im Repo) - **die echten Dateien selbst (mit echten Beträgen/
Geschäftspartnernamen) wurden bewusst nicht committet**, die konkreten Zahlen aus der
Original-Analyse hat der Nutzer separat im Chat erhalten. Zwei Befunde haben die ursprüngliche
Spezifikationsannahme korrigiert:

1. **Eine TikTok Transaction ID ist NICHT immer eindeutig.** Dieselbe ID kann mit
   unterschiedlichem "Type of earnings" mehrfach auftreten (z. B. einmal "Seller bonus", einmal
   "Standard commission" für denselben zugrunde liegenden Verkauf) - zwei echte, unterschiedliche
   Einnahmen. Ein einfacher `UNIQUE`-Index nur auf die Transaction ID hätte beim Import eine der
   beiden Zeilen still verworfen (SQLite `INSERT OR REPLACE` beim Constraint-Konflikt). Der
   Dublettenschlüssel ist deshalb `externalTransactionId` + `externalEarningType` gemeinsam
   (composite `UNIQUE INDEX`), siehe Kommentar an `IncomeEntry`. `TikTokExcelParserTest`
   ("the same Transaction ID with two different earning types...") und `ImportRepositoryTest`
   decken das gegen eine In-Memory-Room-DB ab.
2. **Eine Zeile kann ein leeres "Income"-Feld haben.** Der Parser erfindet hier keinen Betrag,
   sondern meldet die Zeile als überspringbaren Fehler (`TikTokEarningsRowError`) - sichtbar in
   der Import-Vorschau, nicht stillschweigend als 0 € gezählt (`TikTokExcelParserTest`).
3. **Beobachtung beim Original-Datensatz (nicht Teil der Test-Fixtures):** die Summe einer der
   acht echten Monatsdateien wich von der in den Steuerunterlagen dokumentierten Arbeitsstand-
   Summe für denselben Monat ab - vermutlich eine andere/spätere Export-Version als die in der
   Steuer-Arbeitsmappe verwendete. Die App zeigt bewusst nur, was in den tatsächlich
   importierten Dateien steht, und erfindet nichts, um eine extern dokumentierte Summe zu
   treffen. **Falls Steuerunterlagen und App-Summe abweichen, deutet das auf eine andere/
   aktuellere Report-Datei hin, die nachimportiert werden sollte - kein App-Fehler.**

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
- **TikTok-Excel-Import** – `XlsxReaderTest` und `TikTokExcelParserTest` laufen gegen
  anonymisierte Fixtures, die strukturell echte TikTok-Earnings-Reports nachbilden (siehe
  "Datenbefunde aus der Entwicklung" oben); `ImportRepositoryTest` ist ein Robolectric-
  Integrationstest gegen eine echte In-Memory-Room-Datenbank (§34: "import a report", "import
  the same report twice" ohne Dubletten, "import zwei Monate" ohne Cross-Contamination).

**TC05** (Statushistorie) benötigt eine echte Room-Datenbank/Instrumented-Test-Setup und ist
in diesem Durchgang nicht ausprogrammiert; **TC08** (Backup/Restore) ist jetzt für das
erweiterte Schema (siehe unten) angepasst, aber nur durch Kompilierung/manuelle Prüfung
abgesichert, nicht durch einen dedizierten neuen Test (ehrlich benannt, nicht als "getestet"
behauptet).

```bash
cd tiktok-shop-buchhaltung
./gradlew testDebugUnitTest
```

> **Build-Status**: `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` und
> `./gradlew assembleRelease` (R8/Minify) wurden tatsächlich ausgeführt (Android SDK 35 +
> Build-Tools 35.0.0) – Ergebnis: **BUILD SUCCESSFUL**, alle 79 Unit-Tests grün (mehrfach mit
> `--rerun` gegengeprüft, nicht nur Gradle-Cache-Treffer). Beim ersten Durchlauf des
> Grundgerüsts wurden mehrere reale Fehler gefunden und behoben, die eine reine Code-Review
> nicht aufgedeckt hätte: fehlende Farbe `ic_launcher_background` (aapt2-Linking-Fehler),
> fehlende `kotlinx-coroutines-play-services`-Abhängigkeit für `Task.await()`,
> `Icons.Filled.ArrowBack` als nicht importierte Extension-Property. Beim Mehrfach-
> Transaktions-Scan wurde zusätzlich ein echter `AmountParser`-Bug gefunden und behoben:
> Zeilen, die Datum und Betrag zusammen enthalten (z. B. "03.07.2026 10,99 €"), ließen den
> Betrag fälschlich als mehrdeutig gelten. Beim Excel-Import wurden zwei weitere reale Bugs
> gefunden und behoben (siehe "Reale Datenbefunde" oben): das Sparse-Row-Problem im
> XLSX-Reader und die Transaction-ID-Eindeutigkeitsannahme.

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
- Instrumented Test für die Room-Migrationen `MIGRATION_1_2`/`MIGRATION_2_3` (Schema-SQL wurde
  manuell gegen Rooms generierte `schemas/3.json` abgeglichen, aber kein
  `MigrationTestHelper`-Test).

### Noch nicht umgesetzt aus der 37-Punkte-Spezifikation (bewusst nicht als "fertig" gemeldet)

Diese Session hat PHASE 1-3 umgesetzt (Analyse, Plan, TikTok-Excel-Import mit
Dublettenschutz/Import-Verlauf). Noch offen, in der vom Nutzer vorgegebenen Reihenfolge:

- **PHASE 4/5** (teilweise aus einer früheren Session vorhanden): Mehrfach-Transaktions-OCR
  und AI-Vision-Fallback existieren bereits (`scan/`, `ai/`), aber der Excel-Import nutzt sie
  noch nicht für die `payoutStatus`/`taxRelevance`-Feinsteuerung aus §4.
- **PHASE 6**: "Steuer-Arbeitsstand"-Screen, "Offene Nachweise" (`OpenEvidenceItem`,
  §23 mit den 6 vorgegebenen Punkten), `BusinessAsset` (§24, MSI-Laptop-Beispiel) - noch keine
  Entities, kein Screen.
- **PHASE 7**: Export/Backup/Restore für die neuen Entities (`SourceDocument`, `ImportBatch`,
  `MerchantRule`, künftige `OpenEvidenceItem`/`BusinessAsset`) - aktuell sichert Backup nur
  `IncomeEntry`/`ExpenseEntry`/`StatusHistory` (jetzt inkl. der in Phase 3 neuen Felder) sowie
  `MerchantRule` NICHT und importierte Excel-Dateien/`SourceDocument`s NICHT. EÜR-Style-
  Zusammenfassungsexport (§25) und PDF/Steuerdokument-Referenzbereich (§26) fehlen.
- **PHASE 8**: Der EÜR-Datei-Tab "Prüfen" nennt Kie.ai/Picir.ai/Kiti-USD-Ausgaben, die laut
  Steuerübersicht bewusst noch NICHT in die Summe übernommen sind - das entspricht §23 (Offene
  Nachweise) und wird erst mit deren Umsetzung in der App abbildbar.

Kein Punkt aus dieser Liste wird hier als "erledigt" behauptet.
