# Product Requirements Document (PRD)

## 1. Produktname
Arbeitstitel: **ShopLedger DE** / **TikTok Shop Buchhaltung**

## 2. Problem
TikTok-Shop-Affiliate-Einnahmen liegen häufig nur als App-Screenshots vor. Provisionen können sichtbar, aber eingefroren sein. Gleichzeitig fallen viele kleine Betriebsausgaben an. Eine normale Notiz-App trennt diese Zustände nicht sauber und Belege gehen verloren.

## 3. Ziel des MVP
Eine Android-App, die mit sehr wenig manueller Eingabe Einnahmen und Ausgaben erfasst, Belege archiviert und einen nachvollziehbaren Jahres-/Zeitraumexport erzeugt.

## 4. Muss-Funktionen
### 4.1 Belegimport
- Kamera
- Galerie/Photo Picker
- Screenshot-Import
- Mehrere Bilder pro Buchung optional

### 4.2 OCR
- Datum
- Betrag
- Händler/Plattform
- Textindikatoren für Status
- Währung

### 4.3 Einnahmen
Felder:
- ID
- Datum
- Plattform
- Typ
- Bruttobetrag
- Währung
- Status
- Auszahlungsdatum
- ausgezahlter Betrag
- Notiz
- Belegpfad
- OCR-Rohtext
- bestätigt ja/nein

### 4.4 Ausgaben
Felder:
- ID
- Belegdatum
- Händler
- Kategorie
- Bruttobetrag
- ggf. MwSt.-Info
- Geschäftsanteil %
- anrechenbarer Betrag
- Zahlungsart optional
- Notiz
- Belegpfad
- OCR-Rohtext
- bestätigt ja/nein

### 4.5 Kategorien Ausgaben
- Produkte für TikTok-Videos
- Versandkosten
- Software / Abos
- AI-Dienste
- Telefon / Internet
- Kamera / Mikrofon / Licht / Stativ
- Computer / Smartphone / Zubehör
- Werbung
- Fahrtkosten
- Bürobedarf
- Gebühren
- Sonstige Betriebsausgaben

### 4.6 Dashboard
Karten:
- Provisionen angezeigt
- Eingefroren
- Verfügbar
- Ausgezahlt
- Ausgaben
- Vorläufiges Ergebnis
- Ungeprüfte Einträge

Filter:
- Monat
- Quartal
- Jahr
- Benutzerdefiniert

### 4.7 Suche/Filter
- Typ
- Kategorie
- Status
- Betrag
- Datum
- Händler/Plattform
- Beleg vorhanden/fehlt

### 4.8 Export
- CSV
- ZIP mit CSV + Belegen
- Dateinamen eindeutig
- Zeitraum im Exportnamen

### 4.9 Backup
- Lokales Backup als ZIP/JSON
- Restore mit Vorschau

## 5. Soll-Funktionen
- Dublettenerkennung nach Datum/Betrag/Händler/Bildhash
- Wiederkehrende Abo-Ausgaben
- Monatsvergleich
- PDF-Export als Übersicht
- XLSX-Export
- Biometrische Sperre

## 6. Später möglich
- Cloud-Sync
- Steuerberater-Export
- ELSTER-nahe Feldzuordnung
- Automatischer Import aus E-Mail/CSV
- Mehrere Gewerbe/Projekte

## 7. Nicht-Ziele im MVP
- Keine vollautomatische Steuererklärung
- Keine verbindliche Steuerberatung
- Keine Bankkontenanbindung
- Keine automatische ELSTER-Übermittlung

## 8. Qualitätskriterien
- Kein Datenverlust bei App-Neustart
- OCR-Ergebnis immer editierbar
- Originalbeleg nie überschreiben
- Export reproduzierbar
- Status einer Provision nachvollziehbar
