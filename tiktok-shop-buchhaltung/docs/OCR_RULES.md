# OCR- und Klassifikationsregeln

## Allgemein
1. OCR-Rohtext vollständig speichern.
2. Extrahierte Werte nur als Vorschlag behandeln.
3. Nutzer muss vor dem endgültigen Speichern bestätigen.
4. Bei Unsicherheit Feld leer lassen statt raten.

## Deutsche Beträge
Beispiele:
- `702,61 €` -> 702.61
- `1.234,56 €` -> 1234.56
- `€4.22` -> 4.22

## TikTok Status-Erkennung
### FROZEN
Indikatoren u. a.:
- `eingefroren`
- `Provision wurde ... eingefroren`
- `Abheben nicht möglich`
- `withdrawal unavailable`
- `frozen`

### AVAILABLE
Indikatoren:
- `verfügbar`
- `available for withdrawal`
- auszahlbar

### PAID_OUT
Indikatoren:
- `ausgezahlt`
- `payout completed`
- `withdrawn`
- klar dokumentierter Auszahlungsvorgang

### ACCRUED
Provision sichtbar, aber ohne sicheren Nachweis für Auszahlung.

### REVERSED
- storniert
- zurückgebucht
- reversed
- cancelled commission

## Beispiel aus Nutzer-Screenshot
Text enthält sinngemäß:
- `Provision ... eingefroren`
- `Verfügbare Auszahlung 702,61 €`
- `Abheben nicht möglich`

Klassifikation:
- Betrag: 702.61 EUR
- Plattform: TikTok Shop
- Typ: Provision
- Status: FROZEN
- Auszahlung: 0 / unbekannt

## Ausgaben-OCR
Erkennen, falls vorhanden:
- Händlername
- Rechnungs-/Belegdatum
- Gesamtbetrag
- MwSt.
- Zahlungsart

Kategorievorschlag nur als Vorschlag, z. B.:
- ElevenLabs -> AI-Dienste / Software
- CapCut -> Software / Abos
- Versandlabel -> Versandkosten
- Mikrofon -> Technik/Zubehör

## Confidence
Jedes extrahierte Feld soll optional einen Confidence-Wert 0..1 besitzen.
Unter z. B. 0.75 sichtbare Warnung `Bitte prüfen`.
