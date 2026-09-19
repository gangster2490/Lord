# Deutschland – Buchhaltungslogik für die App

## Zweck
Diese Datei beschreibt nur die Darstellung/Erfassungslogik in der App. Sie ersetzt keine Steuerberatung.

## EÜR-orientierte Trennung
Die App soll klar unterscheiden zwischen:
1. Provision/Ertrag wird in einer Plattform angezeigt.
2. Geld ist eingefroren oder nicht auszahlbar.
3. Geld ist verfügbar.
4. Geld wurde tatsächlich ausgezahlt.

Für Steuerzwecke ist der Zeitpunkt der Verfügungsmacht relevant. Deshalb darf die App sichtbare Plattform-Provisionen nicht automatisch mit `ausgezahlt` gleichsetzen.

## Ausgaben
Tatsächlich bezahlte, betriebliche Ausgaben werden erfasst. Bei gemischter Nutzung wird ein Geschäftsanteil gespeichert.

Beispiel:
- Smartphone-Kosten 600 EUR
- Geschäftsanteil 40 %
- anrechenbarer App-Wert: 240 EUR

Die App zeigt diesen Wert nur als Dokumentations-/Arbeitswert; finale steuerliche Behandlung kann abweichen.

## Kleinunternehmer
Die App soll kein Umsatzsteuer-Modul erzwingen. Optionales Profilfeld:
- `Kleinunternehmerregelung aktiv: Ja/Nein`

Wenn Ja:
- keine automatische Vorsteuer-Erstattung berechnen
- Bruttobeträge als primäre Ausgabenbeträge darstellen

## Jahreswechsel
Einträge müssen nach Kalenderjahr filterbar sein.
Eine Provision, die 2026 angezeigt und 2027 tatsächlich auszahlbar/ausgezahlt wird, muss beide Ereignisse nachvollziehbar speichern können.

## Audit Trail
Statusänderungen einer Provision protokollieren:
- alter Status
- neuer Status
- Datum/Uhrzeit
- optional neuer Beleg

So bleibt nachvollziehbar, wann aus `FROZEN` später `PAID_OUT` wurde.
