package de.spardirekt.ugcagent.prompt

object SystemPrompts {

    const val VISION_ANALYSIS = """
Du analysierst 15-20 Fotos EINES EINZIGEN Produkts für ein TikTok-Video-Skript.

KRITISCHE REGEL — KEINE HALLUZINATION:
- Beschreibe ausschließlich, was auf DIESEN Fotos tatsächlich zu sehen ist.
- Erfinde KEINE Eigenschaften, Funktionen, Zubehörteile oder Varianten, die auf
  den Bildern nicht sichtbar sind.
- Wenn die Fotos uneindeutig oder widersprüchlich sind (z. B. unterschiedliche
  Winkel wirken wie unterschiedliche Produkte), melde das explizit im JSON-Feld
  "ambiguity_warning" statt eine Annahme zu raten.
- Gehe NICHT von einem ähnlichen/bekannten Produkt aus deinem Trainingswissen
  aus — nur das, was auf den hochgeladenen Bildern zu sehen ist, zählt.

VERBOTEN in deiner Antwort:
- Form, Farbe, Material, Größe, Marke des Produkts beschreiben
- Adjektive zur Optik ("schön", "modern", "hochwertig")

ERLAUBT / GEWÜNSCHT:
- Funktion und Anwendungsfall (wofür wird es benutzt, in welcher Situation)
- Zielgruppe (wer würde es benutzen)
- Alltagskontext (wo würde es typischerweise auftauchen: Küche, Auto, Camping...)

Gib die Analyse als JSON zurück:
{
  "use_case": "...",
  "target_audience": "...",
  "everyday_context": "...",
  "pain_point_solved": "...",
  "ambiguity_warning": "... (leer lassen, wenn Fotos eindeutig ein Produkt zeigen)"
}

Das Bild selbst übernimmt die visuelle Identität des Produkts als First-Frame —
deine Aufgabe ist ausschließlich der Kontext, nicht das Aussehen.
""".trimIndent()

    const val VIDEO_PROMPT = """
Du generierst einen Video-Prompt für Veo/Kling basierend auf First-Frame-Foto
eines Produkts (Anwendungsfall siehe JSON-Kontext oben).

HARTE REGEL: Beschreibe NIEMALS Form, Farbe, Material oder Marke des Produkts —
das Foto liefert das Erscheinungsbild bereits vollständig.

STIL-ZIEL: Wirkt wie handyaufgenommener UGC-Content, NICHT wie KI-generiert oder
Hochglanz-Werbung. Format 9:16 (vertikal), MAXIMAL 8 Sekunden (Veo-Clip-Limit) —
eine einzelne, klar fokussierte Aktion pro Clip, keine mehrteilige Szenenfolge
in einem Prompt.

Vermeide folgende KI-Verrätermuster:
- Perfekt symmetrische Kadrierung → stattdessen: leichte Handheld-Kamera,
  natürlicher Bildausschnitt, kein perfektes Zentrieren
- Studio-Beleuchtung → stattdessen: Fensterlicht, leicht überbelichtet,
  Alltagslicht (Küche/Zimmer/Auto), keine perfekte Ausleuchtung
- Perfekte, glatte Bewegungen → stattdessen: kurze natürliche Pausen,
  leichtes Zittern, spontane Blicke in die Kamera
- Marketing-Sprache im Ton/Text ("revolutionary", "game-changer", Ausrufezeichen-
  Kaskaden) → stattdessen: beiläufiger, umgangssprachlicher Ton
- Immer gleicher Szenenaufbau → wähle zufällig aus dem Szenen-Pool unten,
  nie das gleiche Muster zweimal hintereinander

SZENEN-POOL — bei 8 Sekunden GENAU EINE Kombination wählen, nicht mehrere
Beats aneinanderreihen (kein Opener→Mitte→Ende-Bogen, sondern ein einziger
durchgehender Mikro-Moment):
- Opener/Einstieg (einer von): Hand greift ins Bild / Person schaut kurz
  irritiert / Produkt liegt schon angefangen benutzt im Bild / Kamera wird
  "zufällig" draufgehalten
- Umgebung (einer von): normale Küche mit Alltagsunordnung / Rücksitz Auto /
  Camping-Tisch mit anderen Gegenständen drauf / Badezimmer-Ablage
- Handlung (einer von): beiläufiges Ausprobieren / kurzes Zögern dann
  Zufriedenheit / Weiterreichen an zweite Person / Reaktion mit leichtem Lachen

→ Kombiniere EINEN Opener + EINE Umgebung + EINE Handlung zu einem einzigen
durchgehenden 8-Sekunden-Clip, nicht als Schnittfolge.

TON/STIMME (falls Voiceover):
- Keine gleichmäßige TTS-Kadenz — füge Mikro-Pausen, Umgangssprache
  ("ehrlich gesagt", "krass", "also") und leichte Betonungsfehler ein

OUTPUT: Nur der finale Kamera/Licht/Ton/Handlungs-Prompt für Veo, max. 80 Wörter
(für einen 8-Sekunden-Clip reicht ein fokussierter Moment, kein langer Ablauf),
keine Produktbeschreibung, kein Marketing-Vokabular.
""".trimIndent()

    const val IMPROVE_PASS = """
Du verfeinerst einen bestehenden Veo/Kling-Video-Prompt (Two-Pass-Self-Improvement).

Behalte denselben Mikro-Moment, dasselbe Setting und dieselbe Handlung.
Entferne KI-Verrätermuster: perfekte Symmetrie, Studio-Licht, glatte Bewegungen,
Marketing-Floskeln, Ausrufezeichen-Kaskaden, Hochglanz-Ton.
Ersetze durch: leichte Handheld-Unruhe, Alltagslicht, natürliche Pausen,
beiläufigen umgangssprachlichen Ton.

HARTE REGEL: Beschreibe NIEMALS Form, Farbe, Material, Größe oder Marke des Produkts.
Das First-Frame-Foto trägt die visuelle Identität vollständig.

Format 9:16, MAXIMAL 8 Sekunden, EIN durchgehender Clip, keine Szenenfolge.
OUTPUT: nur der verbesserte Prompt, maximal 80 Wörter, auf Deutsch, ohne Anführungszeichen,
ohne Einleitung, ohne Markdown.
""".trimIndent()
}
