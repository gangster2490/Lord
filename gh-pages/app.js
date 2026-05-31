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

const outTitle      = document.getElementById('out-title');
const outTags       = document.getElementById('out-tags');
const outBanner     = document.getElementById('out-banner');
const outVeo        = document.getElementById('out-veo');
const outVoiceover  = document.getElementById('out-voiceover');
const outMusic      = document.getElementById('out-music');
const outSfx        = document.getElementById('out-sfx');
const outLive       = document.getElementById('out-live');

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
function showErr(msg) { errMsg.innerHTML = msg; errbox.hidden = false; }
function hideErr()    { errbox.hidden = true; }
function setLoading(on) {
  btnGen.disabled = on;
  btnLabel.hidden = on;
  btnSpinner.hidden = !on;
}

/* ── System prompt ── */
const SYSTEM_PROMPT = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

BILDNUTZUNG:
- Bild 1 (Produktbild): Nutze es zur visuellen Erkennung des Produkts – Aussehen, Farbe, Form, Verpackung.
- Bild 2 (Beschreibungsbild, falls vorhanden): Lese daraus alle faktischen Produktinfos: Titel, Features, Spezifikationen, Material, Größe, Kapazität, Anwendungsfälle, Warnhinweise.
- Erfinde keine Features. Wenn eine Eigenschaft nur im Beschreibungsbild steht, darfst du sie verwenden.
- Wenn Informationen unklar sind, bleibe allgemein.

REGELN:
- Keine Preise, keine Rabatte, keine falschen Versprechen
- TikTok-safe: keine irreführenden Claims
- Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor oder danach, keine Markdown-Codeblöcke

Gib exakt dieses JSON zurück:
{
  "title": "TikTok Titel mit Emoji, max 80 Zeichen",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
  "banner": ["Zeile 1 (max 28 Zeichen)","Zeile 2","Zeile 3","Call-to-Action"],
  "veoPrompt": "Detaillierter englischer Veo 3.1 Prompt für ein 10-Sekunden 9:16 vertikales TikTok-Produktvideo. Enthält: Szenenaufbau, Kamerabewegungen (z.B. slow push-in, arc shot), Beleuchtung, Produktplatzierung, Übergänge, On-Screen-Text-Overlays mit Timing, Farbpalette und visuellen Stil.",
  "voiceoverText": "Deutschsprachiger Voiceover-Text im natürlichen TikTok-Shop-Verkäufer-Stil. Kurze Sätze. Jede Zeile passt zu einem 1-2 Sek. Videoabschnitt. Optimiert für ElevenLabs-TTS. Format: eine Zeile pro Timing-Beat, z.B.: '0s – Satz 1\\n2s – Satz 2\\n4s – Satz 3'",
  "musicSuggestion": "Konkrete Musikbeschreibung für das Video: Genre, Tempo (BPM), Energie, Instrumente, Stimmung, Referenz-Stil (z.B. 'Lo-fi Hip-Hop Beat, 95 BPM, entspannt aber fokussiert, Klavier + subtile Drums, ähnlich wie typische TikTok-Shop-Backgroundmusik')",
  "soundEffects": "Liste der Sound-Effekte mit Timing, z.B.: '0s – Whoosh-Intro\\n2s – Soft Impact beim Produktclose-up\\n6s – Subtle Riser\\n9s – Ding/Chime beim CTA'",
  "live": "0:00 | Hook\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen für den Käufer\\n1:30 | Call-to-Action\\n1:45 | Abschluss & Verabschiedung"
}`;

/* ── Generate ── */
btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();

  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/, '');

  if (!key)                return showErr('Bitte Anthropic API Key eingeben.');
  if (!productImageBase64) return showErr('Bitte zuerst ein Produktbild hochladen.');

  const category = document.getElementById('category').value;
  const style    = document.getElementById('style').value;
  const audience = document.getElementById('audience').value;
  const tone     = document.getElementById('tone').value;

  setLoading(true);
  results.hidden = true;

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
    text: `Analysiere ${hasDesc ? 'diese beiden Bilder' : 'dieses Produktbild'} und erstelle vollständigen TikTok-Shop-Content für Deutschland.
${hasDesc ? '\nBild 1 = Produktbild (visuell). Bild 2 = Produktbeschreibung (Fakten, Spezifikationen, Features).\nNutze beide Bilder zusammen. Erfinde nichts.' : ''}
Kategorie: ${category}
Video-Stil: ${style}
Zielgruppe: ${audience}
Ton: ${tone}

Erstelle alle Felder vollständig ausgefüllt:
- title: mitreißender Titel mit passendem Emoji
- hashtags: genau 5 deutsche TikTok-Hashtags (Nischen + Trend-Mix)
- banner: 4 kurze Zeilen für Video-Overlay (3 Highlights + 1 CTA)
- veoPrompt: detaillierter Veo 3.1 Produktionsprompt auf Englisch mit Kamera, Licht, Timing und On-Screen-Text
- voiceoverText: deutschen Voiceover mit Timing-Beats für ElevenLabs, natürlicher TikTok-Stil, kurze Sätze
- musicSuggestion: konkrete Musikbeschreibung mit Genre, BPM und Stimmung
- soundEffects: Sound-Effekte-Liste mit sekundengenauem Timing
- live: 2-Minuten TikTok-Live-Skript mit Timestamps

Nur JSON zurückgeben – kein erklärender Text.`,
  });

  const payload = {
    model:      'claude-opus-4-5',
    max_tokens: 3000,
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
  /* Title */
  outTitle.textContent = d.title || '—';

  /* Hashtags */
  outTags.innerHTML = '';
  const wrap = document.createElement('div');
  wrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => {
    const s = document.createElement('span');
    s.className = 'tag'; s.textContent = t;
    wrap.appendChild(s);
  });
  outTags.appendChild(wrap);

  /* Banner */
  outBanner.innerHTML = '';
  (d.banner || []).forEach(l => {
    const div = document.createElement('div');
    div.className = 'bline'; div.textContent = l;
    outBanner.appendChild(div);
  });

  /* Veo Prompt */
  outVeo.textContent = d.veoPrompt || d.veo || '—';

  /* Voiceover – render timing beats */
  outVoiceover.innerHTML = '';
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
      outVoiceover.appendChild(row);
    });
  } else {
    outVoiceover.textContent = '—';
  }

  /* Music */
  outMusic.textContent = d.musicSuggestion || d.music || '—';

  /* Sound Effects – render timing beats */
  outSfx.innerHTML = '';
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
      outSfx.appendChild(row);
    });
  } else {
    outSfx.textContent = '—';
  }

  /* Live Script */
  outLive.innerHTML = '';
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
    outLive.appendChild(span);
  });

  results.hidden = false;
  results.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/* ── Copy ── */
document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const card = document.getElementById(btn.dataset.src);
  if (card) clip(card.querySelector('.rcard-body')?.innerText || '', btn);
});

btnCopyAll.addEventListener('click', () => {
  const all = [
    '=== TikTok Titel ===\n'        + (outTitle.innerText     || ''),
    '=== Hashtags ===\n'            + (outTags.innerText      || ''),
    '=== Banner Text ===\n'         + (outBanner.innerText    || ''),
    '=== Veo 3.1 Prompt ===\n'      + (outVeo.innerText       || ''),
    '=== Voiceover Text ===\n'      + (outVoiceover.innerText || ''),
    '=== Music Suggestion ===\n'    + (outMusic.innerText     || ''),
    '=== Sound Effects ===\n'       + (outSfx.innerText       || ''),
    '=== TikTok Live Script ===\n'  + (outLive.innerText      || ''),
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
