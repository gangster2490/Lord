'use strict';

/* ── DOM ── */
const apiKeyEl      = document.getElementById('apiKey');
const btnEye        = document.getElementById('btnEye');
const eyeIcon       = document.getElementById('eyeIcon');
const proxyUrlEl    = document.getElementById('proxyUrl');
const btnGen        = document.getElementById('btnGen');
const btnLabel      = document.getElementById('btnLabel');
const btnSpinner    = document.getElementById('btnSpinner');
const errbox        = document.getElementById('errbox');
const errMsg        = document.getElementById('errMsg');
const results       = document.getElementById('results');
const btnCopyAll    = document.getElementById('btnCopyAll');
const btnNeueHooks  = document.getElementById('btnNeueHooks');

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
const outHooks        = $id('out-hooks');
const outHashtags     = $id('out-hashtags');
const outTitle        = $id('out-title');
const outBanner       = $id('out-banner');
const outVeo          = $id('out-veo');
const outLive         = $id('out-live');
const outBannerPrompt = $id('out-banner-prompt');

const rcTitle        = $id('rc-title');
const rcBanner       = $id('rc-banner');
const rcVeo          = $id('rc-veo');
const rcLive         = $id('rc-live');
const rcBannerPrompt = $id('rc-banner-prompt');

/* ── Image state (3 slots) ── */
const images = [
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
];

/* ── Workflow state ── */
let step1Data        = null;  // { productFacts, hashtags, hooks }
let allHookBatches   = [];    // array of hook arrays, one per generation
let selectedHookGlobal = null; // { batchIdx, hookIdx, hook }

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

/* ── API call helper ── */
async function callClaude(payload) {
  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/,  '');

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
  console.log('[TikTok Creator] status:', resp.status, text.slice(0, 300));

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
  try { return JSON.parse(jsonStr); } catch {
    throw new Error(`Claude-Antwort kein gültiges JSON:<br><code>${rawText.slice(0, 400)}</code>`);
  }
}

/* ══════════════════════════════════════
   STEP 1 – Produktdaten + Hooks + Tags
══════════════════════════════════════ */
const HOOK_RULES = `HOOK-REGELN (strikt einhalten):
- Genau 5 Hooks, einen pro psychologischem Winkel
- Maximal 6 Wörter pro Hook
- Sprache: Deutsch
- Plattform: TikTok Shop Deutschland
- Videolänge: 8 Sekunden
- GROSSBUCHSTABEN für den Hook-Text
- Bewerte jeden Hook von 0–100 nach Scroll-Stop-Potenzial
- Stärksten Hook zuerst (höchste Punktzahl zuerst sortieren)
- Psychologische Winkel: Pain, Curiosity, Convenience, Benefit, Emotional

VERBOTEN:
- Keine Emojis im Hook-Text
- Keine Markennamen
- Keine Rabatt- oder Preis-Sprache
- Keine generischen Marketing-Phrasen
- Kein Clickbait der nicht beweisbar ist
- Keine erfundenen Produkteigenschaften

FOKUS: Echte Kundenschmerzpunkte, konkrete Vorteile, ehrliche Neugier.`;

const SYSTEM_STEP1 = `Du bist ein TikTok-Shop-Marketing-Experte für den deutschen Markt.

BILDNUTZUNG (OCR auf allen hochgeladenen Bildern):
- Bild 1: Produktbild – visuelle Erkennung (Aussehen, Farbe, Form, Verpackung).
- Bild 2 (falls vorhanden): Produktbeschreibung / Spezifikationen – extrahiere alle lesbaren Fakten.
- Bild 3 (falls vorhanden): Zusatzbild – weitere Fakten (Verpackung, Etikett, Lieferumfang, Zertifizierungen).

PRODUKTFAKTEN: Nur aus sichtbaren Informationen. Nicht erkennbare Werte = "Nicht erkennbar". Nichts erfinden.

${HOOK_RULES}

HASHTAGS: 7 relevante deutsche TikTok-Hashtags.

REGELN: Keine Preise, Rabatte, falschen Versprechen. TikTok-safe.
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
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5","#Tag6","#Tag7"],
  "hooks": [
    {"score": 95, "angle": "Pain",        "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 92, "angle": "Curiosity",   "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 89, "angle": "Convenience", "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 86, "angle": "Benefit",     "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 82, "angle": "Emotional",   "text": "HOOK TEXT MAX 6 WÖRTER"}
  ]
}`;

btnGen.addEventListener('click', runStep1);

async function runStep1() {
  hideErr();

  const key = apiKeyEl.value.trim();
  if (!key)               return showErr('Bitte Anthropic API Key eingeben.');
  if (!images[0].base64) return showErr('Bitte zuerst ein Produktbild hochladen (Bild 1).');

  const style = document.getElementById('style').value;
  const tone  = document.getElementById('tone').value;

  // Reset workflow
  step1Data           = null;
  allHookBatches      = [];
  selectedHookGlobal  = null;
  if (results) results.hidden = true;
  if (btnNeueHooks) btnNeueHooks.hidden = true;
  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });

  setLoading(true);

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
    text: `Analysiere ${imgCount === 1 ? 'dieses Produktbild' : `diese ${imgCount} Bilder`} und erstelle Produktdaten, Hashtags und 5 Hooks.\n\n${imgDesc}\n\nVideo-Stil: ${style}\nTon: ${tone}\n\nNur JSON zurückgeben – kein erklärender Text.`,
  });

  try {
    const parsed = await callClaude({
      model: 'claude-opus-4-5', max_tokens: 2000,
      system: SYSTEM_STEP1,
      messages: [{ role: 'user', content: userContent }],
    });

    step1Data = parsed;
    allHookBatches = [parsed.hooks || []];
    renderStep1(parsed);

    if (results) {
      results.hidden = false;
      results.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  } catch (err) {
    console.error('[TikTok Creator] Step 1 error:', err);
    showErr(err.message || 'Unbekannter Fehler.');
  } finally {
    setLoading(false);
  }
}

/* ── Render Step 1 ── */
function renderStep1(d) {
  /* Produktdaten */
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

  addFactRow('Produktname', pf.name);
  addFactRow('Maße',        pf.dimensions);
  addFactRow('Kapazität',   pf.capacity);
  addFactRow('Material',    pf.material);
  addFactRow('Gewicht',     pf.weight);
  addFactRow('Farbe',       pf.color);
  addFactTags('Lieferumfang', pf.includedItems);
  addFactTags('Features',     pf.keyFeatures);
  addFactTags('Warnhinweise', pf.warnings);
  addFactTags('Anwendung',    pf.useCases);
  $append(outFacts, factsGrid);

  /* Hooks */
  $clear(outHooks);
  renderHookBatch(d.hooks || [], 0, true);

  /* Show Neue Hooks button */
  if (btnNeueHooks) btnNeueHooks.hidden = false;

  /* Hashtags */
  $clear(outHashtags);
  const tagWrap = document.createElement('div');
  tagWrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => {
    const s = document.createElement('span'); s.className = 'tag'; s.textContent = t;
    tagWrap.appendChild(s);
  });
  $append(outHashtags, tagWrap);

  /* Hide step-2 cards */
  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });
}

/* ── Render a hook batch ── */
function renderHookBatch(hooks, batchIdx, isFirst) {
  if (!isFirst) {
    /* Divider between batches */
    const div = document.createElement('div');
    div.className = 'hook-batch-divider';
    div.textContent = `Batch ${batchIdx + 1}`;
    $append(outHooks, div);
  }

  const list = document.createElement('div');
  list.className = 'hook-batch';
  list.dataset.batchIdx = batchIdx;

  hooks.forEach((hook, hookIdx) => {
    const item = document.createElement('div');
    item.className = 'hook-item';
    item.dataset.batchIdx = batchIdx;
    item.dataset.hookIdx  = hookIdx;

    const meta = document.createElement('div');
    meta.className = 'hook-meta';

    const top = document.createElement('div');
    top.className = 'hook-top';

    const score = document.createElement('span');
    score.className = 'hook-score';
    score.textContent = `${hook.score}/100`;

    const angle = document.createElement('span');
    angle.className = 'hook-angle';
    angle.textContent = hook.angle || '';

    top.appendChild(score);
    top.appendChild(angle);

    const text = document.createElement('div');
    text.className = 'hook-text';
    text.textContent = hook.text || '';

    meta.appendChild(top);
    meta.appendChild(text);

    const btn = document.createElement('button');
    btn.className = 'btn-hook';
    btn.textContent = `Hook ${hookIdx + 1}`;
    btn.addEventListener('click', () => onHookSelect(batchIdx, hookIdx, btn));

    item.appendChild(meta);
    item.appendChild(btn);
    list.appendChild(item);
  });

  $append(outHooks, list);

  /* Hint line (only after last batch) */
  const existing = outHooks?.querySelector('.hook-hint');
  if (existing) existing.remove();
  const hint = document.createElement('p');
  hint.className = 'hook-hint';
  hint.textContent = '👆 Wähle einen Hook – der Content wird automatisch generiert';
  $append(outHooks, hint);
}

/* ── Hook selection ── */
function onHookSelect(batchIdx, hookIdx, clickedBtn) {
  const hook = allHookBatches[batchIdx]?.[hookIdx];
  if (!hook || !step1Data) return;

  /* Update visual selection state */
  document.querySelectorAll('.hook-item').forEach(el => el.classList.remove('selected'));
  document.querySelectorAll('.btn-hook').forEach(el => el.classList.remove('selected'));
  clickedBtn.closest('.hook-item')?.classList.add('selected');
  clickedBtn.classList.add('selected');

  selectedHookGlobal = { batchIdx, hookIdx, hook };

  runStep2(hook);
}

/* ══════════════════════════════
   NEUE HOOKS – no image re-scan
══════════════════════════════ */
const SYSTEM_NEUE_HOOKS = `Du bist ein TikTok-Shop-Marketing-Experte für den deutschen Markt.

${HOOK_RULES}

Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor/danach, keine Markdown-Blöcke.

Gib exakt dieses JSON zurück:
{
  "hooks": [
    {"score": 95, "angle": "Pain",        "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 92, "angle": "Curiosity",   "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 89, "angle": "Convenience", "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 86, "angle": "Benefit",     "text": "HOOK TEXT MAX 6 WÖRTER"},
    {"score": 82, "angle": "Emotional",   "text": "HOOK TEXT MAX 6 WÖRTER"}
  ]
}`;

btnNeueHooks?.addEventListener('click', async () => {
  if (!step1Data) return;

  btnNeueHooks.disabled = true;
  btnNeueHooks.textContent = '…';

  const pf = step1Data.productFacts || {};
  const prev = allHookBatches.flat().map(h => h.text).join(', ');

  const factsText = [
    `Produktname: ${pf.name || 'Nicht erkennbar'}`,
    `Features: ${(pf.keyFeatures || []).join(', ') || 'Nicht erkennbar'}`,
    `Anwendung: ${(pf.useCases || []).join(', ') || 'Nicht erkennbar'}`,
    `Material: ${pf.material || 'Nicht erkennbar'}`,
  ].join('\n');

  try {
    const parsed = await callClaude({
      model: 'claude-opus-4-5', max_tokens: 800,
      system: SYSTEM_NEUE_HOOKS,
      messages: [{
        role: 'user',
        content: [{
          type: 'text',
          text: `Generiere 5 NEUE, völlig andere Hooks für dieses Produkt.\n\nProduktfakten:\n${factsText}\n\nBereits verwendete Hooks (NICHT wiederholen):\n${prev}\n\nErstelle komplett andere Hooks mit anderen Formulierungen und Perspektiven.\nNur JSON zurückgeben.`,
        }],
      }],
    });

    const newHooks = parsed.hooks || [];
    const newBatchIdx = allHookBatches.length;
    allHookBatches.push(newHooks);
    renderHookBatch(newHooks, newBatchIdx, false);

    /* Scroll to new batch */
    outHooks?.lastElementChild?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

  } catch (err) {
    console.error('[TikTok Creator] Neue Hooks error:', err);
    showErr(err.message || 'Fehler beim Generieren neuer Hooks.');
  } finally {
    btnNeueHooks.disabled = false;
    btnNeueHooks.textContent = '🎣 Neue Hooks';
  }
});

/* ══════════════════════════════
   STEP 2 – Hook → Full Content
══════════════════════════════ */
const SYSTEM_STEP2 = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

Der ausgewählte Hook ist die kreative Hauptrichtung für ALLEN generierten Content.
Der Hook ist die Eröffnung, das Leitmotiv und die rote Linie durch alle Outputs.

VEO 3.1 MASTER PROMPT REGELN:
Der veoPrompt muss ALLES enthalten – vollständiger Produktionsplan:
- Szenenaufbau: Kamerabewegungen, Beleuchtung, Bildkomposition (9:16 vertikal)
- Timing: Exakte Sekunden-Beats (0s, 2s, 4s, 6s, 8s)
- On-Screen Text: Hook als erstes Overlay, dann echte Produktfakten
- Voiceover: Männliche deutsche Stimme, Skript mit Timing-Beats, Hook als Eröffnungszeile
- Hintergrundmusik: Genre, BPM, Energie, Stimmung
- Sound Effects: Timing-Beats mit Beschreibung
Schreibe veoPrompt auf Englisch, detailliert und produktionsfähig.

BANNER PROMPT: NUR für KI-Bildgeneratoren. Englisch, 9:16, schwarzer Hintergrund, Neongrün (#39FF14), kein Preis, keine Rabatte.

REGELN:
- Hook als kreative Richtlinie. Keine erfundenen Fakten. TikTok-safe.
- title und bannerText auf Deutsch. veoPrompt und bannerPrompt auf Englisch.
- Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor/danach, keine Markdown-Blöcke.

Gib exakt dieses JSON zurück:
{
  "title": "TikTok-Titel ohne Emoji, max 80 Zeichen, Hook als Grundlage",
  "bannerText": ["Hook-Text oder Variante (max 28 Zeichen)","Zeile 2","Zeile 3","CTA-Zeile"],
  "veoPrompt": "Complete English Veo 3.1 production prompt for 8-second 9:16 TikTok video. Opens with hook as first on-screen overlay and voiceover. [SCENE TIMING] 0s-2s hook, 2s-4s product, 4s-6s feature, 6s-8s CTA. [CAMERA] movements per scene. [LIGHTING] setup. [ON-SCREEN TEXT] German overlays. [GERMAN MALE VOICEOVER] full script. [BACKGROUND MUSIC] genre, BPM, mood. [SOUND EFFECTS] timed.",
  "live": "0:00 | Hook-Eröffnung\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen & Mehrwert\\n1:30 | Community-Frage\\n1:45 | CTA & Abschluss",
  "bannerPrompt": "English AI image generation prompt for 9:16 TikTok Shop product banner. Hook text as main headline. Pure black background, neon green #39FF14 accent, product centered and lit, SparDirekt DE style, no prices, no discounts, photorealistic."
}`;

async function runStep2(hook) {
  /* Loading state inside hooks card */
  const hint = outHooks?.querySelector('.hook-hint');
  if (hint) {
    hint.innerHTML = `<div class="step2-spinner"><span class="spinner"></span><span>Content wird generiert…</span></div>`;
  }

  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });

  const pf = step1Data?.productFacts || {};
  const factsText = [
    `Produktname: ${pf.name || 'Nicht erkennbar'}`,
    `Maße: ${pf.dimensions || 'Nicht erkennbar'}`,
    `Kapazität: ${pf.capacity || 'Nicht erkennbar'}`,
    `Material: ${pf.material || 'Nicht erkennbar'}`,
    `Gewicht: ${pf.weight || 'Nicht erkennbar'}`,
    `Farbe: ${pf.color || 'Nicht erkennbar'}`,
    `Features: ${(pf.keyFeatures || []).join(', ') || 'Nicht erkennbar'}`,
    `Anwendung: ${(pf.useCases || []).join(', ') || 'Nicht erkennbar'}`,
  ].join('\n');

  const style = document.getElementById('style').value;
  const tone  = document.getElementById('tone').value;

  try {
    const parsed = await callClaude({
      model: 'claude-opus-4-5', max_tokens: 3000,
      system: SYSTEM_STEP2,
      messages: [{
        role: 'user',
        content: [{
          type: 'text',
          text: `Ausgewählter Hook: "${hook.text}" (Winkel: ${hook.angle}, Score: ${hook.score}/100)\n\nProduktfakten:\n${factsText}\n\nVideo-Stil: ${style}\nTon: ${tone}\n\nGeneriere TikTok Titel, Banner Text, Veo 3.1 Master Prompt, TikTok Live Script und Banner Prompt basierend auf diesem Hook.\nNur JSON zurückgeben.`,
        }],
      }],
    });

    renderStep2(parsed, hook);

  } catch (err) {
    console.error('[TikTok Creator] Step 2 error:', err);
    showErr(err.message || 'Fehler bei der Content-Generierung.');
    if (hint) hint.textContent = '👆 Wähle einen Hook – der Content wird automatisch generiert';
  }
}

/* ── Render Step 2 ── */
function renderStep2(d, hook) {
  const hint = outHooks?.querySelector('.hook-hint');
  if (hint) hint.textContent = `✓ Hook "${hook.text}" aktiv`;

  $set(outTitle, 'textContent', d.title || '—');
  if (rcTitle) rcTitle.hidden = false;

  $clear(outBanner);
  (d.bannerText || []).forEach(line => {
    const bline = document.createElement('div');
    bline.className = 'bline'; bline.textContent = line;
    $append(outBanner, bline);
  });
  if (rcBanner) rcBanner.hidden = false;

  $set(outVeo, 'textContent', d.veoPrompt || '—');
  if (rcVeo) rcVeo.hidden = false;

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
  if (rcLive) rcLive.hidden = false;

  $set(outBannerPrompt, 'textContent', d.bannerPrompt || '—');
  if (rcBannerPrompt) rcBannerPrompt.hidden = false;

  rcTitle?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/* ── Copy ── */
document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const card = document.getElementById(btn.dataset.src);
  if (card) clip(card.querySelector('.rcard-body')?.innerText || '', btn);
});

btnCopyAll?.addEventListener('click', () => {
  const sections = [
    ['Produktdaten',          outFacts],
    ['TikTok Titel',          outTitle],
    ['Hashtags',              outHashtags],
    ['Banner Text',           outBanner],
    ['Veo 3.1 Master Prompt', outVeo],
    ['TikTok Live Script',    outLive],
    ['Banner Prompt',         outBannerPrompt],
  ];
  const all = sections
    .filter(([, el]) => el && !el.closest('.rcard')?.hidden)
    .map(([label, el]) => `=== ${label} ===\n${el?.innerText || ''}`)
    .join('\n\n');
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
