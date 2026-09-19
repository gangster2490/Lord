# Akzeptanztests / Testfälle

## TC01 – Eingefrorene TikTok-Provision
Gegeben: Screenshot enthält `702,61 €`, `eingefroren`, `Abheben nicht möglich`.
Erwartet:
- Betrag 702.61 EUR
- Typ Provision
- Status FROZEN
- nicht automatisch PAID_OUT
- Originalbild verknüpft

## TC02 – Ausgabe mit Geschäftsanteil
Beleg 100 EUR, Kategorie Telefon/Internet, Geschäftsanteil 40 %.
Erwartet:
- Brutto 100
- Geschäftsanteil 40
- anrechenbarer Arbeitswert 40

## TC03 – Deutsche Dezimalzahl
OCR `1.234,56 €`.
Erwartet: 1234.56, nicht 1.23456.

## TC04 – Dublette
Gleicher Betrag, Datum, Händler und identischer Bildhash.
Erwartet: Warnung vor erneutem Speichern.

## TC05 – Statusänderung
Provision FROZEN wird später PAID_OUT.
Erwartet:
- bestehende Provision bleibt erhalten
- Statushistorie sichtbar
- Auszahlungsdatum und ausgezahlter Betrag ergänzt

## TC06 – OCR unsicher
Betrag nicht eindeutig.
Erwartet: kein erfundener Betrag, Feld muss bestätigt/ergänzt werden.

## TC07 – Export
Zeitraum 01.05.2026–31.12.2026.
Erwartet: CSV enthält nur Buchungen des Zeitraums und eindeutige Belegreferenzen.

## TC08 – Backup/Restore
Backup erstellen, App-Daten löschen, Restore.
Erwartet: Buchungen + Belegverknüpfungen vollständig wiederhergestellt.
