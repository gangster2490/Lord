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
const outTitle        = $id('out-title');
const outHashtags     = $id('out-hashtags');
const outBanner       = $id('out-banner');
const outVeo          = $id('out-veo');
const outVoiceover    = $id('out-voiceover');
const outMusic        = $id('out-music');
const outSfx          = $id('out-sfx');
const outLive         = $id('out-live');
const outBannerPrompt = $id('out-banner-prompt');

/* ── Image slots ── */
const images = [
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
  { base64: null, mime: 'image/jpeg' },
];

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
  const dz      = $id(dzId),   input   = $id(inputId);
  const preview = $id(previewId), dzInner = $id(dzInnerId);
  const meta    = $id(metaId), name    = $id(nameId), btnRem = $id(removeId);
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

/* ═══════════════════════════════════════════════════
   SYSTEM PROMPT – Single request, all outputs
═══════════════════════════════════════════════════ */
const SYSTEM_PROMPT = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

BILDNUTZUNG (OCR auf allen hochgeladenen Bildern):
- Bild 1: Produktbild – erkenne Aussehen, Farbe, Form, Verpackung.
- Bild 2 (falls vorhanden): Produktbeschreibung – extrahiere alle lesbaren Texte und Fakten via OCR.
- Bild 3 (falls vorhanden): Zusatzbild – extrahiere weitere Fakten via OCR.
Fakten aus allen Bildern zusammenführen und konsolidieren.

PRODUKTFAKTEN: Nur aus sichtbaren oder lesbaren Informationen. Unbekannte Werte = "Nicht erkennbar".

TIKTOK TITEL: Auf Deutsch, mit Emoji, maximal 80 Zeichen, scroll-stopping, kein Preis.

HASHTAGS: 7 relevante deutsche TikTok-Hashtags.

BANNER TEXT: 4 kurze Zeilen für ein visuelles Banner (Deutsch). Zeile 1-3 max. 28 Zeichen. Zeile 4 = CTA.

VEO 3.1 PROMPT: English. Realistic high-quality vertical 9:16 TikTok Shop Germany advertisement. Photorealistic commercial quality.

VIDEO LENGTH RULE: All Veo 3.1 advertisements must be exactly 8 seconds long. Generate all scenes, hooks, pacing and camera movements specifically for an 8-second video. Do not create prompts for 15s, 30s or longer commercials.

PRODUCT LOCK RULE: The uploaded product image is a locked asset. The product must appear identically in every frame. Do NOT describe the product at all — Veo reads the image directly.

Always begin the veoPrompt with this exact block (copy word for word):
"VIDEO LENGTH: Exactly 8 seconds. 9:16 vertical. TikTok Shop Germany. Use the uploaded image as the exact locked product reference. CRITICAL RULE: The uploaded product image is the visual source of truth. The product from the uploaded image is a protected asset. The product may move, rotate, swing, wrinkle naturally and be handled by people, but its design must never change. Keep 100% identical: shape, proportions, dimensions, color, material, texture, pockets, seams, and all visible details. Do not add, remove, redesign, improve, modify or reinterpret any part of the product. IMPORTANT: Treat the product as locked. Animate the scene around the product, not the product design. If there is a conflict between creativity and product accuracy, product accuracy always wins."

After the product lock block, add this exact HUMAN INTERACTION block (copy word for word):
"HUMAN INTERACTION RULES: The video must focus on human interaction with the product. At least 80% of the video must contain real people using, wearing, touching or demonstrating the product. Every scene must include at least one person interacting with the product. Do not create a product-only showcase. Do not create a rotating product presentation. Do not keep the product isolated on screen. The main subject of the video is people actively using the product in real-life conditions."

After the human interaction block, describe the scene using the extracted productFacts (useCases, keyFeatures) to determine the most realistic and natural usage scenario for this specific product. Show natural real-life usage: a person wearing or handling the product in its intended environment, in action, with close-up shots of real use. Everything around the product may be generated or modified: people, nature, environment, lighting, weather, camera movement, cinematic effects.

Scene timing structure (total: exactly 8 seconds):
0–2s: HOOK — Strong opening. Product immediately visible on person actively using it. Hook text overlay.
2–5s: PRODUCT DEMONSTRATION — Dynamic realistic usage scene. Natural environment, natural light, human motion. Camera moves freely around the person and product.
5–7s: MAIN BENEFIT — Close-up shot of person interacting with product in use. Natural light. Do not alter product.
7–8s: CTA — Medium shot of person with product. Final CTA overlay: "Jetzt unten im Warenkorb".

German on-screen text overlays (use the generated bannerText lines with timing).
Final screen CTA: "Jetzt unten im Warenkorb" or "Produkt unten im Warenkorb" — do NOT use "Link in Bio".

End the veoPrompt with this exact negative prompt (copy word for word):
"Negative prompt: Do not change product shape. Do not add or remove pockets, straps, zippers or any detail. Do not change product color or material. Do not replace or reinterpret the product. Do not create a different version or model. No product redesign. No product hallucination. Product accuracy is more important than cinematic effects."

German voiceover and music go in their own separate fields (voiceoverText, musicSuggestion, soundEffects). Do NOT include them inside veoPrompt.

VOICEOVER TEXT: Deutsches Voiceover-Skript für das 8-Sekunden-Video. Männliche Stimme. Mit Timing (0s, 2s, 4s, 6s).

MUSIC SUGGESTION: Genre, BPM, Stimmung, Stil. Auf Deutsch.

SOUND EFFECTS: Liste relevanter Sound-Effekte mit Timing. Auf Deutsch.

TIKTOK LIVE SCRIPT: Moderationsplan für eine Live-Session. Zeitplan im Format "0:00 | Beschreibung". Auf Deutsch.

BANNER PROMPT: Englisch. Prompt für einen KI-Bildgenerator. 9:16, schwarzer Hintergrund, Neon-Grün (#39FF14), kein Preis.

REGELN: Keine Preise, keine Rabatte, keine falschen Versprechen. TikTok-safe.

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
  "title": "TikTok-Titel mit Emoji",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5","#Tag6","#Tag7"],
  "bannerText": ["Zeile 1","Zeile 2","Zeile 3","CTA"],
  "veoPrompt": "Complete English Veo 3.1 production prompt describing scenes, camera, lighting, text overlays...",
  "voiceoverText": "0s – Erster Satz\\n2s – Zweiter Satz\\n4s – Dritter Satz\\n6s – CTA",
  "musicSuggestion": "Genre, BPM, Stimmung und Stil",
  "soundEffects": "0s – Effekt 1\\n2s – Effekt 2\\n4s – Effekt 3",
  "liveScript": "0:00 | Hook-Eröffnung\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | Community-Frage\\n1:45 | CTA",
  "bannerPrompt": "English AI image generation prompt for 9:16 banner..."
}`;

/* ── Generate ── */
btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();
  if (!apiKeyEl.value.trim()) return showErr('Bitte Anthropic API Key eingeben.');
  if (!images[0].base64)      return showErr('Bitte zuerst ein Produktbild hochladen (Bild 1).');

  if (results) results.hidden = true;
  setLoading(true);

  const active   = images.filter(img => img.base64);
  const imgCount = active.length;

  const userContent = [];
  active.forEach(img => userContent.push({ type: 'image', source: { type: 'base64', media_type: img.mime, data: img.base64 } }));

  const imgDesc = imgCount === 1
    ? 'Nur Bild 1 vorhanden (Produktbild). Setze alle productFacts auf "Nicht erkennbar" sofern nicht sichtbar.'
    : imgCount === 2
      ? 'Bild 1 = Produktbild. Bild 2 = Beschreibung/Spezifikationen – OCR anwenden und Fakten extrahieren.'
      : 'Bild 1 = Produktbild. Bild 2 = Beschreibung/Spezifikationen. Bild 3 = Zusatzbild. OCR auf Bild 2 und 3 anwenden und alle Fakten zusammenführen.';

  userContent.push({ type: 'text', text: `${imgDesc}\n\nVideo-Stil: ${$id('style').value}\nTon: ${$id('tone').value}\n\nNur JSON zurückgeben.` });

  try {
    const parsed = await callClaude({
      model: 'claude-opus-4-5',
      max_tokens: 4000,
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content: userContent }],
    });
    renderResults(parsed);
    if (results) { results.hidden = false; results.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  } catch (err) {
    showErr(err.message || 'Unbekannter Fehler.');
  } finally {
    setLoading(false);
  }
}

/* ── Render ── */
function renderResults(d) {
  renderFacts(d.productFacts || {});
  $set(outTitle, 'textContent', d.title || '—');
  renderHashtags(d.hashtags || []);
  renderBanner(d.bannerText || []);
  $set(outVeo, 'textContent', d.veoPrompt || '—');
  renderVoiceover(d.voiceoverText || '');
  $set(outMusic, 'textContent', d.musicSuggestion || '—');
  renderSfx(d.soundEffects || '');
  renderLive(d.liveScript || '');
  $set(outBannerPrompt, 'textContent', d.bannerPrompt || '—');
}

function renderFacts(pf) {
  $clear(outFacts);
  const grid = document.createElement('div'); grid.className = 'facts-grid';
  function addRow(label, val) {
    const row = document.createElement('div'); row.className = 'fact-row';
    const k = document.createElement('span'); k.className = 'fact-key'; k.textContent = label;
    const v = document.createElement('span');
    v.className = 'fact-val' + (!val || val === 'Nicht erkennbar' ? ' unknown' : '');
    v.textContent = val || 'Nicht erkennbar';
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
}

function renderHashtags(tags) {
  $clear(outHashtags);
  const wrap = document.createElement('div'); wrap.className = 'tag-wrap';
  tags.forEach(t => { const s = document.createElement('span'); s.className = 'tag'; s.textContent = t; wrap.appendChild(s); });
  $append(outHashtags, wrap);
}

function renderBanner(lines) {
  $clear(outBanner);
  lines.forEach(line => {
    const b = document.createElement('div'); b.className = 'bline'; b.textContent = line;
    $append(outBanner, b);
  });
}

function renderVoiceover(text) {
  $clear(outVoiceover);
  (text || '').split('\n').forEach(line => {
    const span = document.createElement('span'); span.className = 'live-line';
    const m = line.match(/^(\d+s)\s*[–-]\s*(.*)/);
    if (m) {
      const ts = document.createElement('span'); ts.className = 'live-ts'; ts.textContent = m[1];
      span.appendChild(ts); span.appendChild(document.createTextNode(m[2].trim()));
    } else { span.textContent = line; }
    $append(outVoiceover, span);
  });
}

function renderSfx(text) {
  $clear(outSfx);
  (text || '').split('\n').forEach(line => {
    const span = document.createElement('span'); span.className = 'live-line';
    const m = line.match(/^(\d+s)\s*[–-]\s*(.*)/);
    if (m) {
      const ts = document.createElement('span'); ts.className = 'live-ts'; ts.textContent = m[1];
      span.appendChild(ts); span.appendChild(document.createTextNode(m[2].trim()));
    } else { span.textContent = line; }
    $append(outSfx, span);
  });
}

function renderLive(text) {
  $clear(outLive);
  (text || '').split('\n').forEach(line => {
    const span = document.createElement('span'); span.className = 'live-line';
    const m = line.match(/^(\d+:\d+)\s*\|(.*)/);
    if (m) {
      const ts = document.createElement('span'); ts.className = 'live-ts'; ts.textContent = m[1];
      span.appendChild(ts); span.appendChild(document.createTextNode(m[2].trim()));
    } else { span.textContent = line; }
    $append(outLive, span);
  });
}

/* ── Copy ── */
document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const card = $id(btn.dataset.src);
  if (card) clip(card.querySelector('.rcard-body')?.innerText || '', btn);
});

$id('btnVeoKomplett')?.addEventListener('click', () => {
  const text = [
    '=== VIDEO LENGTH ===\n8 Seconds',
    `=== VEO 3.1 PROMPT ===\n${outVeo?.innerText || ''}`,
    `=== GERMAN VOICEOVER ===\n${outVoiceover?.innerText || ''}`,
    `=== MUSIC ===\n${outMusic?.innerText || ''}`,
    `=== SOUND EFFECTS ===\n${outSfx?.innerText || ''}`,
  ].join('\n\n');
  clip(text, $id('btnVeoKomplett'));
});

btnCopyAll?.addEventListener('click', () => {
  const sections = [
    ['Produktdaten',     outFacts],
    ['TikTok Titel',     outTitle],
    ['Hashtags',         outHashtags],
    ['Banner Text',      outBanner],
    ['Veo 3.1 Prompt',   outVeo],
    ['Voiceover Text',   outVoiceover],
    ['Music Suggestion', outMusic],
    ['Sound Effects',    outSfx],
    ['Live Script',      outLive],
    ['Banner Prompt',    outBannerPrompt],
  ];
  const all = sections
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
