# MASTER PROMPT FÜR CLAUDE

Du bist Senior Android Engineer, Product Designer und QA Engineer. Baue eine robuste, einfache Android-App mit dem Arbeitstitel **TikTok Shop Buchhaltung** für einen Kleinunternehmer in Deutschland.

## Nutzerprofil
- Android-Smartphone als Hauptgerät.
- Einnahmen überwiegend aus TikTok Shop Affiliate-Provisionen.
- Einnahmen können in TikTok angezeigt, aber gleichzeitig eingefroren oder nicht auszahlbar sein.
- Ausgaben entstehen u. a. durch Produkte für Videos, Versand, Software-Abos, AI-Dienste, Telefon/Internet, Technik, Werbung und Fahrtkosten.
- Der Nutzer möchte möglichst wenig manuell eingeben.

## Kern-Workflow
### Einnahme
1. Nutzer lädt Screenshot aus TikTok Shop hoch oder fotografiert ihn.
2. OCR erkennt relevante Daten.
3. App schlägt vor:
   - Datum
   - Plattform
   - Typ
   - Betrag
   - Status
   - Währung
   - Notiz
4. Nutzer bestätigt oder korrigiert.
5. Originalbild wird mit der Buchung verknüpft.

### Ausgabe
1. Nutzer fotografiert Rechnung/Kassenbon oder importiert Screenshot/PDF-Bild.
2. OCR erkennt:
   - Datum
   - Händler
   - Gesamtbetrag
   - ggf. MwSt.-Betrag
   - Kategorie
   - geschäftlicher Anteil in %
3. Nutzer bestätigt oder korrigiert.
4. Originalbeleg wird gespeichert und mit der Ausgabe verknüpft.

## Sehr wichtig bei TikTok-Einnahmen
Die App muss mindestens diese Zustände getrennt führen:
- `ACCRUED` = provisioniert/angezeigt, aber noch nicht ausgezahlt
- `FROZEN` = eingefroren / Auszahlung nicht möglich
- `AVAILABLE` = verfügbar, auszahlbar
- `PAID_OUT` = tatsächlich ausgezahlt
- `REVERSED` = storniert/zurückgebucht

Eine angezeigte Provision darf nicht automatisch als tatsächliche Auszahlung behandelt werden.

## Dashboard
Zeige für frei wählbaren Zeitraum:
- Provisionen insgesamt
- Eingefroren
- Verfügbar
- Tatsächlich ausgezahlt
- Ausgaben gesamt
- Vorläufiges Ergebnis
- Anzahl Belege
- Fehlende/ungeprüfte Belege

## Steuerliche Darstellung
Die App darf keine verbindliche Steuerberatung behaupten. Zeige stattdessen getrennte Werte:
- `Angezeigte Provision`
- `Tatsächlich ausgezahlt`
- `Betriebsausgaben`
- `Vorläufiger Überschuss`

Alle steuerrelevanten Zuordnungen müssen manuell änderbar sein.

## Technik
Bevorzugt:
- Kotlin
- Jetpack Compose
- Room
- MVVM / Clean Architecture
- CameraX / Android Photo Picker
- Google ML Kit Text Recognition on-device oder gleichwertige lokale OCR
- Keine Cloud-Pflicht für MVP
- Daten standardmäßig lokal
- Export als CSV; optional XLSX und ZIP mit Belegen
- Backup/Restore als ZIP/JSON

## Datenschutz
- Keine Belege ohne ausdrückliche Zustimmung in eine Cloud senden.
- Lokale Speicherung bevorzugen.
- Sensible Daten im App-Speicher ablegen.
- Optional App-Sperre per Biometrie/PIN.

## UX
- Deutsch als Standardsprache.
- Große klare Buttons.
- Wenige Pflichtfelder.
- Nach OCR immer Bestätigungsbildschirm vor dem Speichern.
- Farbcodierung nur ergänzend, Status immer als Text anzeigen.

## Export
Jahres-/Zeitraumexport mit mindestens:
`Datum;Typ;Plattform/Händler;Kategorie;Bruttobetrag;Geschäftsanteil;Anrechenbarer_Betrag;Status;Belegdatei;Notiz`

Zusätzlicher TikTok-Export:
`Datum;Provision_angezeigt;Status;Ausgezahlt_am;Ausgezahlter_Betrag;Beleg`

## Fehlervermeidung
- Keine OCR-Werte stillschweigend speichern.
- Keine Dubletten ohne Warnung.
- Beträge mit deutschem Dezimaltrennzeichen korrekt erkennen.
- Negative/stornierte Buchungen unterstützen.
- Bilder bleiben unverändert als Originalbelege erhalten.

## Liefere als erstes
1. Projektstruktur.
2. Datenmodell.
3. UI-Screens.
4. OCR-Pipeline.
5. Exportlogik.
6. Danach implementiere das MVP schrittweise.

Nutze die übrigen Dateien dieses Pakets als verbindliche Spezifikation. Wenn etwas unklar ist, wähle die einfachste, robuste MVP-Lösung und dokumentiere die Annahme.
