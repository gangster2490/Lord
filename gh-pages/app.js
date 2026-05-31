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

function $id(id) { return document.getElementById(id); }
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

/* ── Image slots ── */
const images = [
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
];

/* ── State ── */
let step1Data = null;

/* ── Proxy URL persistence ── */
(function () {
  proxyUrlEl.value = localStorage.getItem('proxyUrl') || 'http://localhost:3001';
})();
proxyUrlEl.addEventListener('input', () => localStorage.setItem('proxyUrl', proxyUrlEl.value.trim()));

/* ── Eye toggle ── */
btnEye.addEventListener('click', () => {
  const show = apiKeyEl.type === 'password';
  apiKeyEl.type = show ? 'text' : 'password';
  eyeIcon.style.opacity = show ? '0.45' : '1';
});

/* ── Dropzone ── */
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

function wireSlot(idx, dzId, inputId, previewId, dzInnerId, metaId, nameId, removeId) {
  const dz      = $id(dzId),   input  = $id(inputId);
  const preview = $id(previewId), dzInner = $id(dzInnerId);
  const meta    = $id(metaId), name   = $id(nameId), btnRem = $id(removeId);
  if (!dz || !input) return;
  wireDropzone(dz, input, file => {
    readImageFile(file, (dataUrl, mime, fname) => {
      images[idx].base64 = dataUrl.split(',')[1];
      images[idx].mime   = mime;
      if (preview)  { preview.src = dataUrl; preview.classList.add('show'); }
      if (dzInner)  dzInner.style.display = 'none';
      if (name)     name.textContent = fname;
      if (meta)     meta.hidden = false;
    });
  });
  btnRem?.addEventListener('click', () => {
    images[idx].base64 = null;
    if (preview)  { preview.src = ''; preview.classList.remove('show'); }
    if (dzInner)  dzInner.style.display = '';
    if (meta)     meta.hidden = true;
    input.value = '';
  });
}

wireSlot(0, 'dropzone',  'fileInput',  'preview',  'dzInner',  'imgMeta',  'imgName',  'btnRemove');
wireSlot(1, 'dropzone2', 'fileInput2', 'preview2', 'dzInner2', 'imgMeta2', 'imgName2', 'btnRemove2');
wireSlot(2, 'dropzone3', 'fileInput3', 'preview3', 'dzInner3', 'imgMeta3', 'imgName3', 'btnRemove3');

/* ── Helpers ── */
function showErr(msg) { if (errMsg) errMsg.innerHTML = msg; if (errbox) errbox.hidden = false; }
function hideErr()    { if (errbox) errbox.hidden = true; }
function setLoading(on) { btnGen.disabled = on; btnLabel.hidden = on; btnSpinner.hidden = !on; }

async function callClaude(payload) {
  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/, '');
  let resp;
  try {
    resp = await fetch(`${proxyUrl}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-api-key-fwd': key },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new Error(`Proxy nicht erreichbar (<code>${proxyUrl}</code>).<br>Starte: <code>cd proxy &amp;&amp; node server.js</code>`);
  }
  const text = await resp.text();
  let data;
  try { data = JSON.parse(text); } catch {
    throw new Error(`Ungültige Proxy-Antwort (${resp.status}):<br><code>${text.slice(0, 200)}</code>`);
  }
  if (!resp.ok) throw new Error(`API-Fehler ${resp.status}: ${data.error?.message || JSON.stringify(data)}`);
  const raw = data.content?.[0]?.text?.trim() || '';
  if (!raw) throw new Error('Leere Antwort von Anthropic.');
  const json = raw.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
  try { return JSON.parse(json); } catch {
    throw new Error(`Kein gültiges JSON:<br><code>${raw.slice(0, 400)}</code>`);
  }
}

/* ═══════════════════════════════════
   STEP 1 – Produktdaten + Hooks
═══════════════════════════════════ */
const SYSTEM_STEP1 = `Du bist ein TikTok-Shop-Marketing-Experte für den deutschen Markt.

BILDNUTZUNG (OCR auf allen hochgeladenen Bildern):
- Bild 1: Produktbild – erkenne Aussehen, Farbe, Form, Verpackung.
- Bild 2 (falls vorhanden): Produktbeschreibung – extrahiere alle lesbaren Fakten via OCR.
- Bild 3 (falls vorhanden): Zusatzbild – extrahiere weitere Fakten via OCR.
Fakten aus allen Bildern zusammenführen.

PRODUKTFAKTEN: Nur aus sichtbaren Informationen. Unbekannte Werte = "Nicht erkennbar".

5 HOOKS:
- Auf Deutsch, maximal 6 Wörter, scroll-stopping
- Verschiedene psychologische Winkel: Pain, Curiosity, Convenience, Benefit, Emotional
- Keine Markennamen, keine Preise, kein Clickbait

HASHTAGS: 7 relevante deutsche TikTok-Hashtags.

Antworte NUR mit einem gültigen JSON-Objekt ohne Markdown-Blöcke:
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
    {"angle": "Pain",        "text": "Hook-Text"},
    {"angle": "Curiosity",   "text": "Hook-Text"},
    {"angle": "Convenience", "text": "Hook-Text"},
    {"angle": "Benefit",     "text": "Hook-Text"},
    {"angle": "Emotional",   "text": "Hook-Text"}
  ]
}`;

btnGen.addEventListener('click', runStep1);

async function runStep1() {
  hideErr();
  if (!apiKeyEl.value.trim()) return showErr('Bitte Anthropic API Key eingeben.');
  if (!images[0].base64)      return showErr('Bitte zuerst ein Produktbild hochladen (Bild 1).');

  step1Data = null;
  if (results) results.hidden = true;
  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });
  setLoading(true);

  const active   = images.filter(img => img.base64);
  const imgCount = active.length;

  const userContent = [];
  active.forEach(img => userContent.push({ type: 'image', source: { type: 'base64', media_type: img.mime, data: img.base64 } }));

  const imgDesc = imgCount === 1
    ? 'Nur Bild 1 vorhanden (Produktbild). Setze alle productFacts auf "Nicht erkennbar".'
    : imgCount === 2
      ? 'Bild 1 = Produktbild. Bild 2 = Beschreibung/Spezifikationen – OCR verwenden.'
      : 'Bild 1 = Produktbild. Bild 2 = Beschreibung/Spezifikationen. Bild 3 = Zusatzbild. OCR auf Bild 2 und 3 anwenden und Fakten zusammenführen.';

  userContent.push({ type: 'text', text: `${imgDesc}\n\nVideo-Stil: ${$id('style').value}\nTon: ${$id('tone').value}\n\nNur JSON zurückgeben.` });

  try {
    const parsed = await callClaude({ model: 'claude-opus-4-5', max_tokens: 1800, system: SYSTEM_STEP1, messages: [{ role: 'user', content: userContent }] });
    step1Data = parsed;
    renderStep1(parsed);
    if (results) { results.hidden = false; results.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  } catch (err) {
    showErr(err.message || 'Unbekannter Fehler.');
  } finally {
    setLoading(false);
  }
}

function renderStep1(d) {
  /* Produktdaten */
  const pf = d.productFacts || {};
  $clear(outFacts);
  const grid = document.createElement('div'); grid.className = 'facts-grid';
  function addRow(label, val) {
    const row = document.createElement('div'); row.className = 'fact-row';
    const k = document.createElement('span'); k.className = 'fact-key'; k.textContent = label;
    const v = document.createElement('span'); v.className = 'fact-val' + (!val || val === 'Nicht erkennbar' ? ' unknown' : ''); v.textContent = val || 'Nicht erkennbar';
    row.appendChild(k); row.appendChild(v); grid.appendChild(row);
  }
  function addTags(label, arr) {
    if (!arr || !arr.length) { addRow(label, 'Nicht erkennbar'); return; }
    const row = document.createElement('div'); row.className = 'fact-row';
    const k = document.createElement('span'); k.className = 'fact-key'; k.textContent = label;
    const v = document.createElement('div'); v.className = 'fact-val fact-tags';
    arr.forEach(item => { const t = document.createElement('span'); t.className = 'fact-tag'; t.textContent = item; v.appendChild(t); });
    row.appendChild(k); row.appendChild(v); grid.appendChild(row);
  }
  addRow('Produktname', pf.name); addRow('Maße', pf.dimensions); addRow('Kapazität', pf.capacity);
  addRow('Material', pf.material); addRow('Gewicht', pf.weight); addRow('Farbe', pf.color);
  addTags('Lieferumfang', pf.includedItems); addTags('Features', pf.keyFeatures);
  addTags('Warnhinweise', pf.warnings); addTags('Anwendung', pf.useCases);
  $append(outFacts, grid);

  /* Hooks */
  $clear(outHooks);
  const list = document.createElement('div'); list.className = 'hook-list';
  (d.hooks || []).forEach((hook, idx) => {
    const item = document.createElement('div'); item.className = 'hook-item';
    const meta = document.createElement('div'); meta.className = 'hook-meta';
    const angle = document.createElement('div'); angle.className = 'hook-angle'; angle.textContent = hook.angle || '';
    const text  = document.createElement('div'); text.className  = 'hook-text';  text.textContent  = hook.text  || '';
    meta.appendChild(angle); meta.appendChild(text);
    const btn = document.createElement('button'); btn.className = 'btn-hook'; btn.textContent = `Hook ${idx + 1}`;
    btn.addEventListener('click', () => onHookSelect(idx, btn));
    item.appendChild(meta); item.appendChild(btn); list.appendChild(item);
  });
  const hint = document.createElement('p'); hint.className = 'hook-hint'; hint.textContent = '👆 Wähle einen Hook – Content wird generiert';
  $append(outHooks, list); $append(outHooks, hint);

  /* Hashtags */
  $clear(outHashtags);
  const tagWrap = document.createElement('div'); tagWrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => { const s = document.createElement('span'); s.className = 'tag'; s.textContent = t; tagWrap.appendChild(s); });
  $append(outHashtags, tagWrap);

  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });
}

/* ═══════════════════════════════════
   STEP 2 – Hook → Full Content
═══════════════════════════════════ */
const SYSTEM_STEP2 = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

Der ausgewählte Hook ist die kreative Hauptrichtung für allen generierten Content.

VEO 3.1 MASTER PROMPT:
Der veoPrompt enthält den vollständigen Produktionsplan für ein 8-Sekunden 9:16 TikTok-Video:
- Szenen-Timing: 0s-2s Hook/Intro, 2s-4s Produktreveal, 4s-6s Feature, 6s-8s CTA
- Kamerabewegungen pro Szene
- Beleuchtung und Bildkomposition
- On-Screen Text Overlays (Deutsch, mit Zeitangaben)
- Voiceover: Männliche deutsche Stimme, vollständiges Skript mit Timing (0s – Satz, 2s – Satz usw.)
- Hintergrundmusik: Genre, BPM, Stimmung
- Sound Effects: Timing und Beschreibung
Schreibe veoPrompt auf Englisch.

BANNER PROMPT: Nur für KI-Bildgeneratoren. Englisch, 9:16, schwarzer Hintergrund, Neongrün (#39FF14), kein Preis, keine Rabatte.

REGELN: Keine Preise, Rabatte, falschen Versprechen. TikTok-safe.
title und bannerText auf Deutsch. veoPrompt und bannerPrompt auf Englisch.
Antworte NUR mit einem gültigen JSON-Objekt ohne Markdown-Blöcke:
{
  "title": "TikTok-Titel mit Emoji, max 80 Zeichen",
  "bannerText": ["Zeile 1 max 28 Zeichen","Zeile 2","Zeile 3","CTA"],
  "veoPrompt": "Complete English production prompt...",
  "live": "0:00 | Hook-Eröffnung\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | Community-Frage\\n1:45 | CTA",
  "bannerPrompt": "English AI image generation prompt..."
}`;

function onHookSelect(idx, clickedBtn) {
  if (!step1Data) return;
  const hook = step1Data.hooks?.[idx];
  if (!hook) return;

  document.querySelectorAll('.hook-item').forEach(el => el.classList.remove('selected'));
  document.querySelectorAll('.btn-hook').forEach(el => el.classList.remove('selected'));
  clickedBtn.closest('.hook-item')?.classList.add('selected');
  clickedBtn.classList.add('selected');

  const hint = outHooks?.querySelector('.hook-hint');
  if (hint) hint.innerHTML = `<div class="hook-generating"><span class="spinner"></span><span>Content wird generiert…</span></div>`;

  [rcTitle, rcBanner, rcVeo, rcLive, rcBannerPrompt].forEach(el => { if (el) el.hidden = true; });

  const pf = step1Data.productFacts || {};
  const facts = [
    `Name: ${pf.name || 'Nicht erkennbar'}`,
    `Maße: ${pf.dimensions || 'Nicht erkennbar'}`,
    `Material: ${pf.material || 'Nicht erkennbar'}`,
    `Gewicht: ${pf.weight || 'Nicht erkennbar'}`,
    `Farbe: ${pf.color || 'Nicht erkennbar'}`,
    `Features: ${(pf.keyFeatures || []).join(', ') || 'Nicht erkennbar'}`,
    `Anwendung: ${(pf.useCases || []).join(', ') || 'Nicht erkennbar'}`,
  ].join('\n');

  callClaude({
    model: 'claude-opus-4-5', max_tokens: 3000,
    system: SYSTEM_STEP2,
    messages: [{
      role: 'user',
      content: [{ type: 'text', text: `Hook: "${hook.text}" (${hook.angle})\n\nProduktfakten:\n${facts}\n\nVideo-Stil: ${$id('style').value}\nTon: ${$id('tone').value}\n\nNur JSON zurückgeben.` }],
    }],
  }).then(parsed => {
    renderStep2(parsed, hook);
  }).catch(err => {
    showErr(err.message || 'Fehler bei der Content-Generierung.');
    if (hint) hint.textContent = '👆 Wähle einen Hook – Content wird generiert';
  });
}

function renderStep2(d, hook) {
  const hint = outHooks?.querySelector('.hook-hint');
  if (hint) hint.textContent = `✓ Hook "${hook.text}" aktiv`;

  $set(outTitle, 'textContent', d.title || '—');
  if (rcTitle) rcTitle.hidden = false;

  $clear(outBanner);
  (d.bannerText || []).forEach(line => {
    const b = document.createElement('div'); b.className = 'bline'; b.textContent = line; $append(outBanner, b);
  });
  if (rcBanner) rcBanner.hidden = false;

  $set(outVeo, 'textContent', d.veoPrompt || '—');
  if (rcVeo) rcVeo.hidden = false;

  $clear(outLive);
  (d.live || '').split('\n').forEach(line => {
    const span = document.createElement('span'); span.className = 'live-line';
    const m = line.match(/^(\d+:\d+)\s*\|(.*)/);
    if (m) { const ts = document.createElement('span'); ts.className = 'live-ts'; ts.textContent = m[1]; span.appendChild(ts); span.appendChild(document.createTextNode(m[2].trim())); }
    else { span.textContent = line; }
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
  const card = $id(btn.dataset.src);
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
  const done = () => { btn.textContent = '✓ Kopiert'; btn.classList.add('ok'); setTimeout(() => { btn.textContent = orig; btn.classList.remove('ok'); }, 2000); };
  if (navigator.clipboard && location.protocol === 'https:') navigator.clipboard.writeText(text).then(done).catch(() => fallback(text, done));
  else fallback(text, done);
}

function fallback(text, done) {
  const ta = document.createElement('textarea'); ta.value = text; ta.style.cssText = 'position:fixed;opacity:0';
  document.body.appendChild(ta); ta.select();
  try { document.execCommand('copy'); done(); } catch {}
  document.body.removeChild(ta);
}
