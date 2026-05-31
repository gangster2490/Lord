'use strict';

/* ── DOM ── */
const apiKeyEl   = document.getElementById('apiKey');
const btnEye     = document.getElementById('btnEye');
const eyeIcon    = document.getElementById('eyeIcon');
const proxyUrlEl = document.getElementById('proxyUrl');
const btnGen     = document.getElementById('btnGen');
const btnLabel   = document.getElementById('btnLabel');
const btnSpinner = document.getElementById('btnSpinner');
const errbox     = document.getElementById('errbox');
const errMsg     = document.getElementById('errMsg');
const results    = document.getElementById('results');
const btnCopyAll = document.getElementById('btnCopyAll');

/* ── Safe helpers ── */
function $id(id) {
  const el = document.getElementById(id);
  if (!el) console.warn('[TikTok Creator] Missing DOM element: #' + id);
  return el;
}
function $set(el, prop, val) { if (el) el[prop] = val; }
function $clear(el) { if (el) el.innerHTML = ''; }
function $append(el, child) { if (el && child) el.appendChild(child); }

const outFacts        = $id('out-facts');
const outTitle        = $id('out-title');
const outHashtags     = $id('out-hashtags');
const outBanner       = $id('out-banner');
const outVeo          = $id('out-veo');
const outLive         = $id('out-live');
const outBannerPrompt = $id('out-banner-prompt');

/* ── Image state (3 slots) ── */
const images = [
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
];

/* ── Persist proxy URL ── */
(function init() {
  const saved = localStorage.getItem('proxyUrl');
  proxyUrlEl.value = saved || 'http://localhost:3001';
})();
proxyUrlEl.addEventListener('input', () => {
  localStorage.setItem('proxyUrl', proxyUrlEl.value.trim());
});

/* ── Eye toggle ── */
btnEye.addEventListener('click', () => {
  const show = apiKeyEl.type === 'password';
  apiKeyEl.type = show ? 'text' : 'password';
  eyeIcon.style.opacity = show ? '0.45' : '1';
});

/* ── Dropzone helpers ── */
function wireDropzone(dz, input, onFile) {
  dz.addEventListener('click', e => { if (e.target !== input) input.click(); });
  dz.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') input.click(); });
  dz.addEventListener('dragover', e => { e.preventDefault(); dz.classList.add('over'); });
  dz.addEventListener('dragleave', () => dz.classList.remove('over'));
  dz.addEventListener('drop', e => {
    e.preventDefault(); dz.classList.remove('over');
    if (e.dataTransfer.files[0]) onFile(e.dataTransfer.files[0]);
  });
  input.addEventListener('change', () => { if (input.files[0]) onFile(input.files[0]); });
}

function readImageFile(file, onDone) {
  if (!file.type.startsWith('image/')) { showErr('Nur Bilddateien erlaubt (JPG, PNG, WEBP).'); return; }
  if (file.size > 10 * 1024 * 1024)   { showErr('Datei zu groß – max. 10 MB.'); return; }
  const reader = new FileReader();
  reader.onload = ev => { onDone(ev.target.result, file.type, file.name); hideErr(); };
  reader.readAsDataURL(file);
}

function wireSlot(idx, dzId, inputId, previewId, dzInnerId, imgMetaId, imgNameId, btnRemoveId) {
  const dz      = document.getElementById(dzId);
  const input   = document.getElementById(inputId);
  const preview = document.getElementById(previewId);
  const dzInner = document.getElementById(dzInnerId);
  const imgMeta = document.getElementById(imgMetaId);
  const imgName = document.getElementById(imgNameId);
  const btnRem  = document.getElementById(btnRemoveId);
  if (!dz || !input) return;

  wireDropzone(dz, input, file => {
    readImageFile(file, (dataUrl, mime, name) => {
      images[idx].base64 = dataUrl.split(',')[1];
      images[idx].mime   = mime;
      if (preview)  { preview.src = dataUrl; preview.classList.add('show'); }
      if (dzInner)  dzInner.style.display = 'none';
      if (imgName)  imgName.textContent = name;
      if (imgMeta)  imgMeta.hidden = false;
    });
  });

  btnRem?.addEventListener('click', () => {
    images[idx].base64 = null;
    if (preview)  { preview.src = ''; preview.classList.remove('show'); }
    if (dzInner)  dzInner.style.display = '';
    if (imgMeta)  imgMeta.hidden = true;
    input.value = '';
  });
}

wireSlot(0, 'dropzone',  'fileInput',  'preview',  'dzInner',  'imgMeta',  'imgName',  'btnRemove');
wireSlot(1, 'dropzone2', 'fileInput2', 'preview2', 'dzInner2', 'imgMeta2', 'imgName2', 'btnRemove2');
wireSlot(2, 'dropzone3', 'fileInput3', 'preview3', 'dzInner3', 'imgMeta3', 'imgName3', 'btnRemove3');

/* ── Helpers ── */
function showErr(msg) { if (errMsg) errMsg.innerHTML = msg; if (errbox) errbox.hidden = false; }
function hideErr()    { if (errbox) errbox.hidden = true; }
function setLoading(on) {
  btnGen.disabled   = on;
  btnLabel.hidden   = on;
  btnSpinner.hidden = !on;
}

/* ── System prompt ── */
const SYSTEM_PROMPT = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

━━━ PRODUCT ACCURACY MODE – AKTIV ━━━
Die hochgeladenen Bilder und OCR-Texte sind die EINZIGE Wahrheitsquelle.

GENAUIGKEITS-PRIORITÄT:
1. Hochgeladene Bilder (höchste Priorität)
2. OCR-Text aus den Bildern
3. Extrahierte Produktfakten
4. Marketing-Kreativität (niedrigste Priorität)

VERBOTEN – erfinde NIEMALS:
- Taschen, Fächer oder Kompartimente die nicht sichtbar sind
- Zubehör, Griffe, Riemen oder Befestigungen die nicht sichtbar sind
- Materialien, Farben, Maße oder Gewichte die nicht bestätigt sind
- Produktfunktionen die nicht erkennbar oder im OCR-Text genannt sind
- Alternative, verbesserte, futuristische oder konzeptionelle Produktversionen

Bei fehlenden Informationen: generische Formulierungen verwenden. NICHT erfinden.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

BILDNUTZUNG (OCR auf allen hochgeladenen Bildern):
- Bild 1: Produktbild – visuelle Erkennung (Aussehen, Farbe, Form, Verpackung).
- Bild 2 (falls vorhanden): Produktbeschreibung / Spezifikationen – extrahiere alle lesbaren Fakten via OCR.
- Bild 3 (falls vorhanden): Zusatzbild – weitere Fakten via OCR (Verpackung, Etikett, Lieferumfang, Zertifizierungen).
Fakten aus allen Bildern zusammenführen und in productFacts eintragen.

PRODUKTFAKTEN: Nur aus sichtbaren Informationen. Nicht erkennbare Werte = "Nicht erkennbar". Nichts erfinden.

VEO 3.1 MASTER PROMPT REGELN:
Vor dem Schreiben des veoPrompt: erstelle intern eine Checkliste aller sichtbaren Produktfeatures.
Verwende NUR Features die auf dieser Checkliste stehen – kein Feature hinzufügen das nicht bestätigt ist.
Das Produkt im Video muss visuell identisch mit den hochgeladenen Bildern sein: gleiche Form, Farbe, Größe, Layout.

Der veoPrompt muss ALLES enthalten – er ist der vollständige Produktionsplan:
[PRODUKT] Beschreibe das Produkt exakt so wie es in den Bildern erscheint.
[SZENEN-TIMING] 0s-2s Intro/Hook, 2s-4s Produktreveal, 4s-6s Feature-Highlight, 6s-8s CTA.
[KAMERA] Spezifische Kamerabewegungen pro Szene (Schwenk, Zoom, Nahaufnahme etc.).
[BELEUCHTUNG] Lichtsetup und Stimmung.
[ON-SCREEN TEXT] Exakte deutsche Overlays mit bestätigten Produktfakten und Zeitangaben.
[VOICEOVER MÄNNLICH DEUTSCH] Vollständiges Skript mit Timing-Beats (0s – Satz, 2s – Satz, usw.).
[HINTERGRUNDMUSIK] Genre, BPM, Energie, Stimmung.
[SOUND EFFECTS] Timing-Beats mit Beschreibung.
Schreibe veoPrompt auf Englisch, detailliert und produktionsfähig. Videolänge: 8 Sekunden, 9:16 vertikal.

BANNER PROMPT REGEL:
bannerPrompt ist NUR für KI-Bildgeneratoren (kein Video).
Beschreibe NUR sichtbare Produktfeatures – kein Feature das nicht in den Bildern sichtbar ist.
Englisch, 9:16, schwarzer Hintergrund, Neongrün (#39FF14), kein Preis, keine Rabatte.

REGELN: Keine Preise, Rabatte, falschen Versprechen. TikTok-safe.
Marketing-Text auf Deutsch. veoPrompt und bannerPrompt auf Englisch.
Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor/danach, keine Markdown-Blöcke.

Gib exakt dieses JSON zurück:
{
  "productFacts": {
    "name": "Produktname oder Nicht erkennbar",
    "dimensions": "Nicht erkennbar",
    "capacity": "Nicht erkennbar",
    "material": "Nicht erkennbar",
    "weight": "Nicht erkennbar",
    "color": "Nicht erkennbar",
    "includedItems": [],
    "keyFeatures": [],
    "warnings": [],
    "useCases": []
  },
  "title": "TikTok-Titel mit Emoji, max 80 Zeichen, Scroll-Stop-Hook",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5","#Tag6","#Tag7"],
  "bannerText": ["Zeile 1 max 28 Zeichen","Zeile 2","Zeile 3","CTA-Zeile"],
  "veoPrompt": "Complete English Veo 3.1 production prompt for an 8-second 9:16 vertical TikTok product video. [PRODUCT ACCURACY] show only the exact product from uploaded images – same shape, color, size, layout, no redesign. [PRODUCT CHECKLIST] list confirmed visible features used. [SCENE TIMING] 0s-2s hook/intro, 2s-4s product reveal, 4s-6s feature highlight, 6s-8s CTA. [CAMERA] movements per scene. [LIGHTING] setup and mood. [ON-SCREEN TEXT] exact German overlays at specific seconds using confirmed facts only. [GERMAN MALE VOICEOVER] full script: 0s – line, 2s – line, 4s – line, 6s – line, 8s – CTA. [BACKGROUND MUSIC] genre, BPM, mood, energy. [SOUND EFFECTS] 0s – effect, 2s – effect, 6s – effect, 8s – effect.",
  "live": "0:00 | Hook-Eröffnung\n0:15 | Produktvorstellung\n0:30 | Feature 1\n0:45 | Feature 2\n1:00 | Feature 3\n1:15 | Nutzen & Mehrwert\n1:30 | Community-Frage\n1:45 | CTA & Abschluss",
  "bannerPrompt": "English AI image generation prompt for 9:16 vertical TikTok Shop product banner. ACCURACY: only features visible in uploaded images. Pure black background, neon green #39FF14 accent, large bold German product headline, product dramatically centered and lit exactly as it appears in images – no modifications, SparDirekt DE minimal style, no prices, no discount stickers, photorealistic."
}`;

/* ── Generate ── */
btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();

  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/, '');

  if (!key)               return showErr('Bitte Anthropic API Key eingeben.');
  if (!images[0].base64) return showErr('Bitte zuerst ein Produktbild hochladen (Bild 1).');

  const style = document.getElementById('style').value;
  const tone  = document.getElementById('tone').value;

  setLoading(true);
  if (results) results.hidden = true;

  const activeImages = images.filter(img => img.base64);
  const imgCount     = activeImages.length;

  const userContent = [];
  activeImages.forEach(img => {
    userContent.push({ type: 'image', source: { type: 'base64', media_type: img.mime, data: img.base64 } });
  });

  const imgDesc = imgCount === 1
    ? 'Bild 1 = Produktbild (visuell). Kein Beschreibungsbild – setze alle productFacts-Felder auf "Nicht erkennbar".'
    : imgCount === 2
      ? 'Bild 1 = Produktbild (visuell). Bild 2 = Beschreibung/Spezifikationen – extrahiere alle sichtbaren Fakten via OCR. Erfinde nichts.'
      : 'Bild 1 = Produktbild (visuell). Bild 2 = Beschreibung/Spezifikationen. Bild 3 = Zusatzbild. Extrahiere und merge alle Fakten aus Bild 2 und 3 via OCR. Erfinde nichts.';

  userContent.push({
    type: 'text',
    text: `Analysiere ${imgCount === 1 ? 'dieses Produktbild' : `diese ${imgCount} Bilder`} und erstelle vollständigen TikTok-Shop-Content für Deutschland.

${imgDesc}

PRODUCT ACCURACY: Das Produkt im veoPrompt muss exakt den hochgeladenen Bildern entsprechen.
Erstelle intern eine Checkliste sichtbarer Features und verwende nur diese.

Video-Stil: ${style}
Ton: ${tone}

Erstelle ALLE Felder vollständig. Nur JSON zurückgeben – kein erklärender Text.`,
  });

  const payload = {
    model:      'claude-opus-4-5',
    max_tokens: 4000,
    system:     SYSTEM_PROMPT,
    messages:   [{ role: 'user', content: userContent }],
  };

  try {
    console.log('[TikTok Creator] → proxy:', proxyUrl);

    let resp;
    try {
      resp = await fetch(`${proxyUrl}/api/generate`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json', 'x-api-key-fwd': key },
        body:    JSON.stringify(payload),
      });
    } catch {
      throw new Error(
        `Proxy nicht erreichbar (<code>${proxyUrl}</code>).<br>` +
        `Starte: <code>cd proxy &amp;&amp; node server.js</code>`
      );
    }

    const text = await resp.text();
    console.log('[TikTok Creator] status:', resp.status, text.slice(0, 400));

    let data;
    try { data = JSON.parse(text); } catch {
      throw new Error(`Ungültige Proxy-Antwort (${resp.status}):<br><code>${text.slice(0, 200)}</code>`);
    }
    if (!resp.ok) {
      throw new Error(`API-Fehler ${resp.status}: ${data.error?.message || data.error || JSON.stringify(data)}`);
    }

    const rawText = data.content?.[0]?.text?.trim() || '';
    if (!rawText) throw new Error('Anthropic hat eine leere Antwort zurückgegeben.');

    const jsonStr = rawText.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
    let parsed;
    try { parsed = JSON.parse(jsonStr); } catch {
      throw new Error(`Claude-Antwort kein gültiges JSON:<br><code>${rawText.slice(0, 400)}</code>`);
    }

    render(parsed);

  } catch (err) {
    console.error('[TikTok Creator] Error:', err);
    showErr(err.message || 'Unbekannter Fehler.');
  } finally {
    setLoading(false);
  }
}

/* ── Render ── */
function render(d) {
  /* 1. Produktdaten */
  const pf = d.productFacts || {};
  $clear(outFacts);
  const factsGrid = document.createElement('div');
  factsGrid.className = 'facts-grid';

  function addFactRow(label, val) {
    const row = document.createElement('div'); row.className = 'fact-row';
    const k = document.createElement('span'); k.className = 'fact-key'; k.textContent = label;
    const v = document.createElement('span');
    v.className = 'fact-val' + (!val || val === 'Nicht erkennbar' ? ' unknown' : '');
    v.textContent = val || 'Nicht erkennbar';
    row.appendChild(k); row.appendChild(v); factsGrid.appendChild(row);
  }
  function addFactTags(label, arr) {
    if (!arr || !arr.length) { addFactRow(label, 'Nicht erkennbar'); return; }
    const row = document.createElement('div'); row.className = 'fact-row';
    const k = document.createElement('span'); k.className = 'fact-key'; k.textContent = label;
    const v = document.createElement('div'); v.className = 'fact-val fact-tags';
    arr.forEach(item => {
      const t = document.createElement('span'); t.className = 'fact-tag'; t.textContent = item;
      v.appendChild(t);
    });
    row.appendChild(k); row.appendChild(v); factsGrid.appendChild(row);
  }

  addFactRow('Produktname',  pf.name);
  addFactRow('Maße',         pf.dimensions);
  addFactRow('Kapazität',    pf.capacity);
  addFactRow('Material',     pf.material);
  addFactRow('Gewicht',      pf.weight);
  addFactRow('Farbe',        pf.color);
  addFactTags('Lieferumfang', pf.includedItems);
  addFactTags('Features',     pf.keyFeatures);
  addFactTags('Warnhinweise', pf.warnings);
  addFactTags('Anwendung',    pf.useCases);
  $append(outFacts, factsGrid);

  /* 2. TikTok Titel */
  $set(outTitle, 'textContent', d.title || '—');

  /* 3. Hashtags */
  $clear(outHashtags);
  const tagWrap = document.createElement('div');
  tagWrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => {
    const s = document.createElement('span'); s.className = 'tag'; s.textContent = t;
    tagWrap.appendChild(s);
  });
  $append(outHashtags, tagWrap);

  /* 4. Banner Text */
  $clear(outBanner);
  (d.bannerText || []).forEach(line => {
    const bline = document.createElement('div');
    bline.className = 'bline'; bline.textContent = line;
    $append(outBanner, bline);
  });

  /* 5. Veo 3.1 Master Prompt */
  $set(outVeo, 'textContent', d.veoPrompt || '—');

  /* 6. TikTok Live Script */
  $clear(outLive);
  (d.live || '').split('\n').forEach(line => {
    const span = document.createElement('span');
    span.className = 'live-line';
    const m = line.match(/^(\d+:\d+)\s*\|(.*)/);
    if (m) {
      const ts = document.createElement('span');
      ts.className = 'live-ts'; ts.textContent = m[1];
      span.appendChild(ts);
      span.appendChild(document.createTextNode(m[2].trim()));
    } else { span.textContent = line; }
    $append(outLive, span);
  });

  /* 7. Banner Prompt */
  $set(outBannerPrompt, 'textContent', d.bannerPrompt || '—');

  if (results) { results.hidden = false; results.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
}

/* ── Copy ── */
document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const card = document.getElementById(btn.dataset.src);
  if (card) clip(card.querySelector('.rcard-body')?.innerText || '', btn);
});

btnCopyAll?.addEventListener('click', () => {
  const all = [
    '=== Produktdaten ===\n'          + (outFacts?.innerText        || ''),
    '=== TikTok Titel ===\n'          + (outTitle?.innerText        || ''),
    '=== Hashtags ===\n'              + (outHashtags?.innerText     || ''),
    '=== Banner Text ===\n'           + (outBanner?.innerText       || ''),
    '=== Veo 3.1 Master Prompt ===\n' + (outVeo?.innerText          || ''),
    '=== TikTok Live Script ===\n'    + (outLive?.innerText         || ''),
    '=== Banner Prompt ===\n'         + (outBannerPrompt?.innerText || ''),
  ].join('\n\n');
  clip(all, btnCopyAll);
});

function clip(text, btn) {
  const orig = btn.textContent;
  const done = () => {
    btn.textContent = '✓ Kopiert';
    btn.classList.add('ok');
    setTimeout(() => { btn.textContent = orig; btn.classList.remove('ok'); }, 2000);
  };
  if (navigator.clipboard && location.protocol === 'https:') {
    navigator.clipboard.writeText(text).then(done).catch(() => fallback(text, done));
  } else { fallback(text, done); }
}

function fallback(text, done) {
  const ta = document.createElement('textarea');
  ta.value = text; ta.style.cssText = 'position:fixed;opacity:0';
  document.body.appendChild(ta); ta.select();
  try { document.execCommand('copy'); done(); } catch {}
  document.body.removeChild(ta);
}
