'use strict';

const apiKeyEl     = document.getElementById('apiKey');
const btnEye       = document.getElementById('btnEye');
const eyeIcon      = document.getElementById('eyeIcon');
const proxyUrlEl   = document.getElementById('proxyUrl');
const dropzone     = document.getElementById('dropzone');
const dzInner      = document.getElementById('dzInner');
const fileInput    = document.getElementById('fileInput');
const preview      = document.getElementById('preview');
const btnGen       = document.getElementById('btnGen');
const btnLabel     = document.getElementById('btnLabel');
const btnSpinner   = document.getElementById('btnSpinner');
const errbox       = document.getElementById('errbox');
const errMsg       = document.getElementById('errMsg');
const results      = document.getElementById('results');
const btnCopyAll   = document.getElementById('btnCopyAll');
const outTitle     = document.getElementById('out-title');
const outTags      = document.getElementById('out-tags');
const outBanner    = document.getElementById('out-banner');
const outVeo       = document.getElementById('out-veo');
const outVoiceover = document.getElementById('out-voiceover');
const outMusic     = document.getElementById('out-music');
const outSfx       = document.getElementById('out-sfx');
const outLive      = document.getElementById('out-live');

let imageBase64 = null;
let imageMime   = 'image/jpeg';

(function init() {
  const saved = localStorage.getItem('proxyUrl');
  proxyUrlEl.value = saved || 'http://localhost:3001';
})();
proxyUrlEl.addEventListener('input', () => localStorage.setItem('proxyUrl', proxyUrlEl.value.trim()));

btnEye.addEventListener('click', () => {
  const show = apiKeyEl.type === 'password';
  apiKeyEl.type = show ? 'text' : 'password';
  eyeIcon.style.opacity = show ? '0.45' : '1';
});

dropzone.addEventListener('click', e => { if (e.target !== fileInput) fileInput.click(); });
dropzone.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') fileInput.click(); });
dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('over'); });
dropzone.addEventListener('dragleave', () => dropzone.classList.remove('over'));
dropzone.addEventListener('drop', e => {
  e.preventDefault(); dropzone.classList.remove('over');
  if (e.dataTransfer.files[0]) loadFile(e.dataTransfer.files[0]);
});
fileInput.addEventListener('change', () => { if (fileInput.files[0]) loadFile(fileInput.files[0]); });

function loadFile(file) {
  if (!file.type.startsWith('image/')) return showErr('Nur Bilddateien erlaubt (JPG, PNG, WEBP).');
  if (file.size > 10 * 1024 * 1024) return showErr('Datei zu groß – max. 10 MB.');
  imageMime = file.type;
  const reader = new FileReader();
  reader.onload = ev => {
    imageBase64 = ev.target.result.split(',')[1];
    preview.src = ev.target.result;
    preview.classList.add('show');
    dzInner.style.display = 'none';
    hideErr();
  };
  reader.readAsDataURL(file);
}

function showErr(msg) { errMsg.innerHTML = msg; errbox.hidden = false; }
function hideErr()    { errbox.hidden = true; }
function setLoading(on) { btnGen.disabled = on; btnLabel.hidden = on; btnSpinner.hidden = !on; }

const SYSTEM_PROMPT = `Du bist ein TikTok-Shop-Marketing-Experte und Videoproduktions-Spezialist für den deutschen Markt.

REGELN:
- Keine Preise, keine Rabatte, keine falschen Versprechen
- TikTok-safe: keine irreführenden Claims
- Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor oder danach, keine Markdown-Codeblöcke

Gib exakt dieses JSON zurück:
{
  "title": "TikTok Titel mit Emoji, max 80 Zeichen",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
  "banner": ["Zeile 1 (max 28 Zeichen)","Zeile 2","Zeile 3","Call-to-Action"],
  "veoPrompt": "Detaillierter englischer Veo 3.1 Prompt für 10-Sek 9:16 TikTok-Video: Szenenaufbau, Kamerabewegungen, Beleuchtung, On-Screen-Text-Overlays mit Timing, Farbpalette",
  "voiceoverText": "0s – Erster Satz\n2s – Zweiter Satz\n4s – Dritter Satz\n6s – Vierter Satz\n8s – Call-to-Action",
  "musicSuggestion": "Genre, BPM, Stimmung, Instrumente, Referenz-Stil",
  "soundEffects": "0s – Whoosh-Intro\n2s – Soft Impact\n6s – Subtle Riser\n9s – Chime CTA",
  "live": "0:00 | Hook\n0:15 | Produktvorstellung\n0:30 | Feature 1\n0:45 | Feature 2\n1:00 | Feature 3\n1:15 | Nutzen\n1:30 | Call-to-Action\n1:45 | Abschluss"
}`;

btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();
  const key      = apiKeyEl.value.trim();
  const proxyUrl = (proxyUrlEl.value.trim() || 'http://localhost:3001').replace(/\/$/, '');
  if (!key)         return showErr('Bitte Anthropic API Key eingeben.');
  if (!imageBase64) return showErr('Bitte zuerst ein Produktbild hochladen.');

  const category = document.getElementById('category').value;
  const style    = document.getElementById('style').value;
  const audience = document.getElementById('audience').value;
  const tone     = document.getElementById('tone').value;

  setLoading(true);
  results.hidden = true;

  const payload = {
    model: 'claude-opus-4-5',
    max_tokens: 3000,
    system: SYSTEM_PROMPT,
    messages: [{
      role: 'user',
      content: [
        { type: 'image', source: { type: 'base64', media_type: imageMime, data: imageBase64 } },
        { type: 'text', text: `Analysiere dieses Produktbild und erstelle vollständigen TikTok-Shop-Content für Deutschland.\n\nKategorie: ${category}\nVideo-Stil: ${style}\nZielgruppe: ${audience}\nTon: ${tone}\n\nAlle 8 Felder vollständig ausfüllen. Nur JSON zurückgeben.` },
      ],
    }],
  };

  try {
    console.log('[TikTok Creator] → proxy:', proxyUrl);
    let resp;
    try {
      resp = await fetch(`${proxyUrl}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-api-key-fwd': key },
        body: JSON.stringify(payload),
      });
    } catch (e) {
      console.error('[TikTok Creator] Network error:', e);
      throw new Error(`Proxy nicht erreichbar (<code>${proxyUrl}</code>).<br>Starte: <code>cd proxy &amp;&amp; node server.js</code>`);
    }

    const text = await resp.text();
    console.log('[TikTok Creator] status:', resp.status, 'body:', text.slice(0, 400));

    let data;
    try { data = JSON.parse(text); } catch {
      throw new Error(`Ungültige Proxy-Antwort (${resp.status}):<br><code>${text.slice(0,200)}</code>`);
    }
    if (!resp.ok) throw new Error(`API-Fehler ${resp.status}: ${data.error?.message || data.error || JSON.stringify(data)}`);

    const rawText = data.content?.[0]?.text?.trim() || '';
    if (!rawText) throw new Error('Anthropic hat eine leere Antwort zurückgegeben.');
    console.log('[TikTok Creator] Claude raw:', rawText.slice(0, 300));

    const jsonStr = rawText.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
    let parsed;
    try { parsed = JSON.parse(jsonStr); } catch {
      throw new Error(`Claude-Antwort kein gültiges JSON:<br><code>${rawText.slice(0,400)}</code>`);
    }
    render(parsed);

  } catch (err) {
    console.error('[TikTok Creator] Error:', err);
    showErr(err.message || 'Unbekannter Fehler.');
  } finally {
    setLoading(false);
  }
}

function renderTimedLines(el, text, tsClass) {
  el.innerHTML = '';
  if (!text) { el.textContent = '—'; return; }
  text.split('\n').forEach(line => {
    const row = document.createElement('span');
    row.className = 'live-line';
    const m = line.match(/^(\d+s|\d+:\d+)\s*[–-]\s*(.*)/) || line.match(/^(\d+s|\d+:\d+)\s*\|(.*)/);
    if (m) {
      const ts = document.createElement('span');
      ts.className = 'live-ts' + (tsClass ? ' ' + tsClass : '');
      ts.textContent = m[1];
      row.appendChild(ts);
      row.appendChild(document.createTextNode(m[2].trim()));
    } else { row.textContent = line; }
    el.appendChild(row);
  });
}

function render(d) {
  outTitle.textContent = d.title || '—';

  outTags.innerHTML = '';
  const wrap = document.createElement('div');
  wrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => {
    const s = document.createElement('span'); s.className = 'tag'; s.textContent = t; wrap.appendChild(s);
  });
  outTags.appendChild(wrap);

  outBanner.innerHTML = '';
  (d.banner || []).forEach(l => {
    const div = document.createElement('div'); div.className = 'bline'; div.textContent = l; outBanner.appendChild(div);
  });

  outVeo.textContent = d.veoPrompt || d.veo || '—';
  outMusic.textContent = d.musicSuggestion || d.music || '—';

  renderTimedLines(outVoiceover, d.voiceoverText || d.voiceover || '', '');
  renderTimedLines(outSfx,       d.soundEffects  || d.sfx        || '', 'sfx-ts');
  renderTimedLines(outLive,      d.live           || '',              '');

  results.hidden = false;
  results.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

document.addEventListener('click', e => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const card = document.getElementById(btn.dataset.src);
  if (card) clip(card.querySelector('.rcard-body')?.innerText || '', btn);
});

btnCopyAll.addEventListener('click', () => {
  const sections = [
    ['TikTok Titel',     outTitle],
    ['Hashtags',         outTags],
    ['Banner Text',      outBanner],
    ['Veo 3.1 Prompt',   outVeo],
    ['Voiceover Text',   outVoiceover],
    ['Music Suggestion', outMusic],
    ['Sound Effects',    outSfx],
    ['TikTok Live Script', outLive],
  ];
  clip(sections.map(([k,el]) => `=== ${k} ===\n${el.innerText||''}`).join('\n\n'), btnCopyAll);
});

function clip(text, btn) {
  const orig = btn.textContent;
  const done = () => {
    btn.textContent = '✓ Kopiert'; btn.classList.add('ok');
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