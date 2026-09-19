# TikTok Shop Buchhaltung – Claude Projektpaket

Dieses Paket beschreibt eine Android-App für die einfache Buchhaltung eines TikTok-Shop-Affiliate/Kleinunternehmers in Deutschland.

## Ziel
Der Nutzer fotografiert oder lädt Screenshots/Belege hoch. Die App erkennt Einnahmen und Ausgaben, speichert den Originalbeleg, lässt die erkannten Werte bestätigen und erstellt am Jahresende einen nachvollziehbaren Export für EÜR/Steuerunterlagen.

## Wichtigster Grundsatz
Die App ist ein **Buchhaltungshelfer**, keine Steuerberatung. Steuerliche Felder müssen transparent, editierbar und mit Beleg verknüpft sein.

## Reihenfolge für Claude
1. `CLAUDE_MASTER_PROMPT.md` lesen.
2. `PRODUCT_REQUIREMENTS.md` als verbindliche Produktanforderung verwenden.
3. `DATA_MODEL.json` als Ausgangspunkt für das lokale Datenmodell nutzen.
4. `OCR_RULES.md` für Erkennungslogik beachten.
5. `TAX_LOGIC_DE.md` für Deutschland-spezifische Darstellung beachten.
6. `TEST_CASES.md` als Akzeptanztests implementieren.
7. Beispielbild unter `examples/` für den Status „eingefroren / Auszahlung nicht möglich“ verwenden.

## Gewünschtes Ergebnis
Eine lauffähige Android-App als MVP mit lokalem Speicher, Belegimport, OCR, Bestätigungsdialog, Dashboard, Einnahmen/Ausgaben, Filter, Export und Backup.
