'use strict';

/* ── DOM ── */
const apiKeyEl      = document.getElementById('apiKey');
const btnEye        = document.getElementById('btnEye');
const eyeIcon       = document.getElementById('eyeIcon');
const proxyUrlEl    = document.getElementById('proxyUrl');
const dropzone      = document.getElementById('dropzone');
const dzInner       = document.getElementById('dzInner');
const fileInput     = document.getElementById('fileInput');
const preview       = document.getElementById('preview');
const imgMeta       = document.getElementById('imgMeta');
const imgName       = document.getElementById('imgName');
const btnRemove     = document.getElementById('btnRemove');
const dropzone2     = document.getElementById('dropzone2');
const dzInner2      = document.getElementById('dzInner2');
const fileInput2    = document.getElementById('fileInput2');
const preview2      = document.getElementById('preview2');
const imgMeta2      = document.getElementById('imgMeta2');
const imgName2      = document.getElementById('imgName2');
const btnRemove2    = document.getElementById('btnRemove2');
const btnGen        = document.getElementById('btnGen');
const btnLabel      = document.getElementById('btnLabel');
const btnSpinner    = document.getElementById('btnSpinner');
const errbox        = document.getElementById('errbox');
const errMsg        = document.getElementById('errMsg');
const results       = document.getElementById('results');
const btnCopyAll    = document.getElementById('btnCopyAll');

/* ── Safe element lookup ── */
function $id(id) {
  const el = document.getElementById(id);
  if (!el) console.warn('[TikTok Creator] Missing DOM element: #' + id);
  return el;
}
function $set(el, prop, val) { if (el) el[prop] = val; }
function $clear(el) { if (el) el.innerHTML = ''; }
function $append(el, child) { if (el && child) el.appendChild(child); }

const outFacts        = $id('out-facts');
const outCategory     = $id('out-category');
const outAudience     = $id('out-audience');
const outVarA         = $id('out-varA');
const outVarB         = $id('out-varB');
const outVarC         = $id('out-varC');
const outBannerPrompt = $id('out-banner-prompt');
const outVeo          = $id('out-veo');
const outVoiceover    = $id('out-voiceover');
const outMusic        = $id('out-music');
const outSfx          = $id('out-sfx');
const outLive         = $id('out-live');

/* ── State ── */
let productImageBase64     = null;
let productImageMime       = 'image/jpeg';
let descriptionImageBase64 = null;
let descriptionImageMime   = 'image/jpeg';

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
    e.preventDefault();
    dz.classList.remove('over');
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

/* Product image dropzone */
wireDropzone(dropzone, fileInput, file => {
  readImageFile(file, (dataUrl, mime, name) => {
    productImageBase64 = dataUrl.split(',')[1];
    productImageMime   = mime;
    preview.src        = dataUrl;
    preview.classList.add('show');
    dzInner.style.display = 'none';
    imgName.textContent   = name;
    imgMeta.hidden        = false;
  });
});

btnRemove.addEventListener('click', () => {
  productImageBase64    = null;
  preview.src           = '';
  preview.classList.remove('show');
  dzInner.style.display = '';
  imgMeta.hidden        = true;
  fileInput.value       = '';
});

/* Description image dropzone */
wireDropzone(dropzone2, fileInput2, file => {
  readImageFile(file, (dataUrl, mime, name) => {
    descriptionImageBase64 = dataUrl.split(',')[1];
    descriptionImageMime   = mime;
    preview2.src           = dataUrl;
    preview2.classList.add('show');
    dzInner2.style.display = 'none';
    imgName2.textContent   = name;
    imgMeta2.hidden        = false;
  });
});

btnRemove2.addEventListener('click', () => {
  descriptionImageBase64 = null;
  preview2.src           = '';
  preview2.classList.remove('show');
  dzInner2.style.display = '';
  imgMeta2.hidden        = true;
  fileInput2.value       = '';
});

/* ── Helpers ── */
function showErr(msg) { if (errMsg) errMsg.innerHTML = msg; if (errbox) errbox.hidden = false; }
function hideErr()    { if (errbox) errbox.hidden = true; }
function setLoading(on) {
  btnGen.disabled = on;
  btnLabel.hidden = on;
  btnSpinner.hidden = !on;
}

/* ── System prompt ── */
const SYSTEM_PROMPT = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt. Version 3.0.

BILDNUTZUNG:
- Bild 1 (Produktbild): Nutze es zur visuellen Erkennung – Aussehen, Farbe, Form, Verpackung.
- Bild 2 (Beschreibungsbild, falls vorhanden): Lies daraus alle sichtbaren Produktfakten heraus.

PRODUKTFAKTEN EXTRAHIEREN (nur aus sichtbaren Informationen):
Wenn ein Wert nicht erkennbar ist, setze "Nicht erkennbar". Erfinde NICHTS.

KATEGORIE & ZIELGRUPPE:
- detectedCategory: Erkenne die Produktkategorie automatisch. Gib eine konkrete Kategorie zurück.
- detectedAudience: Format: "Zielgruppe – kurze Begründung".

3 CONTENT-VARIANTEN (jede eigenständig mit title, hashtags, banner, voiceoverText, cta):
- Variante A – Conversion/Sales: Faktenbasiert, klarer Nutzen, starker direkter CTA.
- Variante B – Viral/Scroll-Stop: Überraschender Hook, Emotion, "stop scrolling"-Moment, trendig.
- Variante C – TikTok Live: Interaktiv, Community einbeziehen, Live-Energie, "Jetzt im Stream".

BANNER PROMPT (Englisch, für KI-Bildgenerator):
9:16 vertical TikTok Shop product banner, pure black background, neon green (#39FF14) accent elements, large bold German product headline, product centered and dramatically lit, clean minimal SparDirekt DE style, no prices, no discount stickers, no medical claims, photorealistic product visualization.

REGELN:
- Nutze extrahierte Fakten aktiv in ALLEN Outputs. Keine erfundenen Fakten.
- Keine Preise, Rabatte, falschen Versprechen. TikTok-safe.
- Marketing-Text auf Deutsch. veoPrompt und bannerPrompt auf Englisch.
- Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor/danach, keine Markdown-Blöcke.

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
  "detectedCategory": "Erkannte Kategorie",
  "detectedAudience": "Zielgruppe – Begründung",
  "variants": {
    "A": {
      "title": "Conversion-Titel mit Emoji, max 80 Zeichen",
      "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
      "banner": ["Zeile 1 max 28 Zeichen","Zeile 2","Zeile 3","CTA-Zeile"],
      "voiceoverText": "0s – Satz 1\\n2s – Satz 2\\n4s – Satz 3\\n6s – Satz 4\\n8s – CTA",
      "cta": "Conversion-CTA-Text"
    },
    "B": {
      "title": "Viraler Hook-Titel mit Emoji",
      "hashtags": ["#Viral1","#Viral2","#Viral3","#Viral4","#Viral5"],
      "banner": ["Hook-Zeile","Überraschung","Emotion","Viral-CTA"],
      "voiceoverText": "0s – Hook\\n2s – Überraschung\\n4s – Emotion\\n6s – Payoff\\n8s – CTA",
      "cta": "Viraler CTA-Text"
    },
    "C": {
      "title": "🔴 Live-Titel mit Community-Energie",
      "hashtags": ["#TikTokLive","#Live2","#Live3","#Live4","#Live5"],
      "banner": ["Live-Eröffnung","Produktvorstellung","Community-Frage","Live-CTA"],
      "voiceoverText": "0s – Live-Begrüßung\\n2s – Produktvorstellung\\n4s – Feature\\n6s – Community\\n8s – CTA",
      "cta": "Live-CTA-Text"
    }
  },
  "bannerPrompt": "English AI image generation prompt for the product banner, highly detailed, mentioning exact product facts",
  "veoPrompt": "Detailed English Veo 3.1 prompt for 10-second 9:16 vertical TikTok product video with camera movements, lighting, on-screen text overlays with real product facts and timing",
  "voiceoverText": "0s – Allgemeiner Satz 1\\n2s – Satz 2\\n4s – Satz 3\\n6s – Satz 4\\n8s – CTA",
  "musicSuggestion": "Genre, BPM, Energie, Instrumente, Stimmung, Referenz-Stil",
  "soundEffects": "0s – Whoosh-Intro\\n2s – Soft Impact\\n6s – Subtle Riser\\n9s – Chime CTA",
  "live": "0:00 | Hook\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | CTA\\n1:45 | Abschluss"
}`;

/* ── Generate ── */
btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();

  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/, '');

  if (!key)                return showErr('Bitte Anthropic API Key eingeben.');
  if (!productImageBase64) return showErr('Bitte zuerst ein Produktbild hochladen.');

  const categoryVal = document.getElementById('category').value;
  const style       = document.getElementById('style').value;
  const audience    = document.getElementById('audience').value;
  const tone        = document.getElementById('tone').value;
  const autoCategory = categoryVal === 'auto';

  setLoading(true);
  if (results) results.hidden = true;

  const userContent = [
    {
      type: 'image',
      source: { type: 'base64', media_type: productImageMime, data: productImageBase64 },
    },
  ];

  if (descriptionImageBase64) {
    userContent.push({
      type: 'image',
      source: { type: 'base64', media_type: descriptionImageMime, data: descriptionImageBase64 },
    });
  }

  const hasDesc = !!descriptionImageBase64;
  userContent.push({
    type: 'text',
    text: `Analysiere ${hasDesc ? 'diese beiden Bilder' : 'dieses Produktbild'} und erstelle vollständigen TikTok-Shop-Content v3.0 für Deutschland.
${hasDesc ? '\nBild 1 = Produktbild (visuell). Bild 2 = Produktbeschreibung – extrahiere alle sichtbaren Fakten. Erfinde nichts.' : '\nKein Beschreibungsbild. Setze alle productFacts auf "Nicht erkennbar".'}

Kategorie: ${autoCategory ? 'Bitte automatisch aus den Bildern erkennen und in detectedCategory eintragen.' : categoryVal}
Video-Stil: ${style}
Ton: ${tone}
Zielgruppe: ${audience === 'Allgemein' ? 'Bitte optimal aus Produkt ableiten und in detectedAudience eintragen.' : audience + ' (auch in detectedAudience eintragen)'}

Erstelle ALLE Felder vollständig:
- productFacts: extrahierte Fakten aus Bild 2
- detectedCategory: erkannte Kategorie
- detectedAudience: Zielgruppe mit Begründung
- variants.A (Conversion), variants.B (Viral), variants.C (TikTok Live): je title, 5 hashtags, 4-zeiliges banner, voiceoverText mit Timing-Beats, cta
- bannerPrompt: englischer KI-Bildgenerator-Prompt für 9:16 Produktbanner
- veoPrompt: englischer Veo 3.1 Produktionsprompt
- voiceoverText: allgemeiner Voiceover mit Timing-Beats
- musicSuggestion: Genre, BPM, Stimmung
- soundEffects: Timing-Beats
- live: 2-Minuten Live-Skript

Nur JSON zurückgeben – kein erklärender Text.`,
  });

  const payload = {
    model:      'claude-opus-4-5',
    max_tokens: 5000,
    system:     SYSTEM_PROMPT,
    messages: [{ role: 'user', content: userContent }],
  };

  try {
    console.log('[TikTok Creator] → proxy:', proxyUrl);

    let resp;
    try {
      resp = await fetch(`${proxyUrl}/api/generate`, {
        method:  'POST',
        headers: {
          'Content-Type':  'application/json',
          'x-api-key-fwd': key,
        },
        body: JSON.stringify(payload),
      });
    } catch (networkErr) {
      console.error('[TikTok Creator] Network error:', networkErr);
      throw new Error(
        `Proxy nicht erreichbar (<code>${proxyUrl}</code>).<br>` +
        `Starte: <code>cd proxy &amp;&amp; node server.js</code>`
      );
    }

    const text = await resp.text();
    console.log('[TikTok Creator] status:', resp.status);
    console.log('[TikTok Creator] body:', text.slice(0, 500));

    let data;
    try { data = JSON.parse(text); } catch {
      throw new Error(`Ungültige Proxy-Antwort (${resp.status}):<br><code>${text.slice(0, 200)}</code>`);
    }

    if (!resp.ok) {
      throw new Error(`API-Fehler ${resp.status}: ${data.error?.message || data.error || JSON.stringify(data)}`);
    }

    /* unwrap Anthropic envelope */
    const rawText = data.content?.[0]?.text?.trim() || '';
    if (!rawText) throw new Error('Anthropic hat eine leere Antwort zurückgegeben.');
    console.log('[TikTok Creator] Claude raw:', rawText.slice(0, 300));

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
  /* Produktdaten */
  const pf = d.productFacts || {};
  $clear(outFacts);
  const factsGrid = document.createElement('div');
  factsGrid.className = 'facts-grid';

  function addFactRow(label, val) {
    const row = document.createElement('div');
    row.className = 'fact-row';
    const k = document.createElement('span');
    k.className = 'fact-key';
    k.textContent = label;
    const v = document.createElement('span');
    v.className = 'fact-val' + (!val || val === 'Nicht erkennbar' ? ' unknown' : '');
    v.textContent = val || 'Nicht erkennbar';
    row.appendChild(k);
    row.appendChild(v);
    factsGrid.appendChild(row);
  }

  function addFactTags(label, arr) {
    if (!arr || !arr.length) { addFactRow(label, 'Nicht erkennbar'); return; }
    const row = document.createElement('div');
    row.className = 'fact-row';
    const k = document.createElement('span');
    k.className = 'fact-key';
    k.textContent = label;
    const v = document.createElement('div');
    v.className = 'fact-val fact-tags';
    arr.forEach(item => {
      const t = document.createElement('span');
      t.className = 'fact-tag';
      t.textContent = item;
      v.appendChild(t);
    });
    row.appendChild(k);
    row.appendChild(v);
    factsGrid.appendChild(row);
  }

  addFactRow('Produktname',  pf.name);
  addFactRow('Maße',         pf.dimensions);
  addFactRow('Kapazität',    pf.capacity);
  addFactRow('Material',     pf.material);
  addFactRow('Gewicht',      pf.weight);
  addFactRow('Farbe',        pf.color);
  addFactTags('Lieferumfang',  pf.includedItems);
  addFactTags('Features',      pf.keyFeatures);
  addFactTags('Warnhinweise',  pf.warnings);
  addFactTags('Anwendung',     pf.useCases);

  $append(outFacts, factsGrid);

  /* Auto Category */
  $clear(outCategory);
  const catVal = document.createElement('div');
  catVal.className = 'detect-val';
  catVal.textContent = d.detectedCategory || '—';
  $append(outCategory, catVal);

  /* Detected Audience */
  $clear(outAudience);
  const audText = d.detectedAudience || '';
  const dashIdx = audText.indexOf(' – ');
  if (dashIdx > -1) {
    const av = document.createElement('div');
    av.className = 'detect-val';
    av.textContent = audText.slice(0, dashIdx);
    const ar = document.createElement('div');
    ar.className = 'detect-reason';
    ar.textContent = audText.slice(dashIdx + 3);
    $append(outAudience, av);
    $append(outAudience, ar);
  } else {
    const av = document.createElement('div');
    av.className = 'detect-val';
    av.textContent = audText || '—';
    $append(outAudience, av);
  }

  /* Variants */
  const variantDefs = [
    { el: outVarA, key: 'A' },
    { el: outVarB, key: 'B' },
    { el: outVarC, key: 'C' },
  ];
  variantDefs.forEach(({ el, key }) => {
    $clear(el);
    const v = d.variants?.[key] || {};

    /* Title */
    const secTitle = document.createElement('div');
    secTitle.className = 'variant-section';
    const lblTitle = document.createElement('div');
    lblTitle.className = 'variant-label';
    lblTitle.textContent = 'TikTok Titel';
    const titleEl = document.createElement('div');
    titleEl.className = 'variant-title';
    titleEl.textContent = v.title || '—';
    secTitle.appendChild(lblTitle);
    secTitle.appendChild(titleEl);
    $append(el, secTitle);

    /* Hashtags */
    const secTags = document.createElement('div');
    secTags.className = 'variant-section';
    const lblTags = document.createElement('div');
    lblTags.className = 'variant-label';
    lblTags.textContent = 'Hashtags';
    const tagWrap = document.createElement('div');
    tagWrap.className = 'tag-wrap';
    (v.hashtags || []).forEach(t => {
      const s = document.createElement('span');
      s.className = 'tag'; s.textContent = t;
      tagWrap.appendChild(s);
    });
    secTags.appendChild(lblTags);
    secTags.appendChild(tagWrap);
    $append(el, secTags);

    const hr1 = document.createElement('hr'); hr1.className = 'variant-divider'; $append(el, hr1);

    /* Banner */
    const secBanner = document.createElement('div');
    secBanner.className = 'variant-section';
    const lblBanner = document.createElement('div');
    lblBanner.className = 'variant-label';
    lblBanner.textContent = 'Banner Text';
    secBanner.appendChild(lblBanner);
    (v.banner || []).forEach(l => {
      const bline = document.createElement('div');
      bline.className = 'bline'; bline.textContent = l;
      secBanner.appendChild(bline);
    });
    $append(el, secBanner);

    const hr2 = document.createElement('hr'); hr2.className = 'variant-divider'; $append(el, hr2);

    /* Voiceover */
    const secVo = document.createElement('div');
    secVo.className = 'variant-section voiceover-out';
    const lblVo = document.createElement('div');
    lblVo.className = 'variant-label';
    lblVo.textContent = 'Voiceover';
    secVo.appendChild(lblVo);
    (v.voiceoverText || '').split('\n').forEach(line => {
      const row = document.createElement('span');
      row.className = 'live-line';
      const m = line.match(/^(\d+s)\s*[–-]\s*(.*)/);
      if (m) {
        const ts = document.createElement('span');
        ts.className = 'live-ts'; ts.textContent = m[1];
        row.appendChild(ts);
        row.appendChild(document.createTextNode(m[2].trim()));
      } else { row.textContent = line; }
      secVo.appendChild(row);
    });
    $append(el, secVo);

    /* CTA */
    const ctaBox = document.createElement('div');
    ctaBox.className = 'cta-box';
    ctaBox.textContent = v.cta || '—';
    $append(el, ctaBox);
  });

  /* Banner Prompt */
  $set(outBannerPrompt, 'textContent', d.bannerPrompt || '—');

  /* Veo Prompt */
  $set(outVeo, 'textContent', d.veoPrompt || d.veo || '—');

  /* Voiceover – render timing beats */
  $clear(outVoiceover);
  const voText = d.voiceoverText || d.voiceover || '';
  if (voText) {
    voText.split('\n').forEach(line => {
      const row = document.createElement('span');
      row.className = 'live-line';
      const m = line.match(/^(\d+s)\s*[–-]\s*(.*)/);
      if (m) {
        const ts = document.createElement('span');
        ts.className = 'live-ts'; ts.textContent = m[1];
        row.appendChild(ts);
        row.appendChild(document.createTextNode(m[2].trim()));
      } else {
        row.textContent = line;
      }
      $append(outVoiceover, row);
    });
  } else {
    $set(outVoiceover, 'textContent', '—');
  }

  /* Music */
  $set(outMusic, 'textContent', d.musicSuggestion || d.music || '—');

  /* Sound Effects – render timing beats */
  $clear(outSfx);
  const sfxText = d.soundEffects || d.sfx || '';
  if (sfxText) {
    sfxText.split('\n').forEach(line => {
      const row = document.createElement('span');
      row.className = 'live-line';
      const m = line.match(/^(\d+s)\s*[–-]\s*(.*)/);
      if (m) {
        const ts = document.createElement('span');
        ts.className = 'live-ts sfx-ts'; ts.textContent = m[1];
        row.appendChild(ts);
        row.appendChild(document.createTextNode(m[2].trim()));
      } else {
        row.textContent = line;
      }
      $append(outSfx, row);
    });
  } else {
    $set(outSfx, 'textContent', '—');
  }

  /* Live Script */
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
    '=== Produktdaten ===\n'         + (outFacts.innerText        || ''),
    '=== Auto-Kategorie ===\n'       + (outCategory.innerText     || ''),
    '=== Erkannte Zielgruppe ===\n'  + (outAudience.innerText     || ''),
    '=== Variante A – Conversion ===\n' + (outVarA.innerText      || ''),
    '=== Variante B – Viral ===\n'   + (outVarB.innerText         || ''),
    '=== Variante C – Live ===\n'    + (outVarC.innerText         || ''),
    '=== Banner Prompt ===\n'        + (outBannerPrompt.innerText || ''),
    '=== Veo 3.1 Prompt ===\n'       + (outVeo.innerText          || ''),
    '=== Voiceover Text ===\n'       + (outVoiceover.innerText    || ''),
    '=== Music Suggestion ===\n'     + (outMusic.innerText        || ''),
    '=== Sound Effects ===\n'        + (outSfx.innerText          || ''),
    '=== TikTok Live Script ===\n'   + (outLive.innerText         || ''),
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
