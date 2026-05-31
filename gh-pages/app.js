'use strict';

/* ── DOM ── */
const apiKeyEl   = document.getElementById('apiKey');
const btnEye     = document.getElementById('btnEye');
const eyeIcon    = document.getElementById('eyeIcon');
const dropzone   = document.getElementById('dropzone');
const dzInner    = document.getElementById('dzInner');
const fileInput  = document.getElementById('fileInput');
const preview    = document.getElementById('preview');
const btnGen     = document.getElementById('btnGen');
const btnLabel   = document.getElementById('btnLabel');
const btnSpinner = document.getElementById('btnSpinner');
const errbox     = document.getElementById('errbox');
const errMsg     = document.getElementById('errMsg');
const results    = document.getElementById('results');
const btnCopyAll = document.getElementById('btnCopyAll');

const outTitle  = document.getElementById('out-title');
const outTags   = document.getElementById('out-tags');
const outBanner = document.getElementById('out-banner');
const outVeo    = document.getElementById('out-veo');
const outLive   = document.getElementById('out-live');

/* ── State ── */
let imageBase64 = null;
let imageMime   = 'image/jpeg';

/* ── API Key eye toggle ── */
btnEye.addEventListener('click', () => {
  const show = apiKeyEl.type === 'password';
  apiKeyEl.type = show ? 'text' : 'password';
  eyeIcon.style.opacity = show ? '0.45' : '1';
});

/* ── Dropzone ── */
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

/* ── Error helpers ── */
function showErr(msg) { errMsg.textContent = msg; errbox.hidden = false; }
function hideErr()    { errbox.hidden = true; }
function setLoading(on) {
  btnGen.disabled = on;
  btnLabel.hidden = on;
  btnSpinner.hidden = !on;
}

/* ── Generate ── */
btnGen.addEventListener('click', generate);

async function generate() {
  hideErr();
  const key = apiKeyEl.value.trim();
  if (!key)         return showErr('Bitte Anthropic API Key eingeben.');
  if (!imageBase64) return showErr('Bitte zuerst ein Produktbild hochladen.');

  const category = document.getElementById('category').value;
  const style    = document.getElementById('style').value;
  const audience = document.getElementById('audience').value;
  const tone     = document.getElementById('tone').value;

  setLoading(true);
  results.hidden = true;

  const systemPrompt = `Du bist ein TikTok-Shop-Marketing-Experte für den deutschen Markt.
REGELN: Keine Preise, keine Rabatte, keine falschen Versprechen, TikTok-safe.
Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor oder danach, keine Markdown-Codeblöcke.

Gib exakt dieses JSON zurück:
{
  "title": "TikTok Titel mit Emoji, max 80 Zeichen",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
  "banner": ["Zeile 1 (max 28 Zeichen)","Zeile 2","Zeile 3","Call-to-Action"],
  "veo": "Ausführlicher englischer Veo 3.1 Prompt für 10-Sekunden 9:16 TikTok-Produktvideo",
  "live": "0:00 | Hook\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | Call-to-Action\\n1:45 | Abschluss"
}`;

  const userPrompt = `Analysiere dieses Produktbild und erstelle TikTok-Shop-Content für Deutschland.

Kategorie: ${category}
Video-Stil: ${style}
Zielgruppe: ${audience}
Ton: ${tone}

1. title – Mitreißender Titel mit Emoji, max 80 Zeichen
2. hashtags – Genau 5 deutsche TikTok-Hashtags
3. banner – 4 kurze Zeilen: 3 Highlights + 1 CTA (je max 28 Zeichen)
4. veo – Detaillierter Veo 3.1 Prompt auf Englisch: Kamera, Licht, Bewegung, Stil, Sound für 10-Sek. 9:16 Video
5. live – 2-Minuten-Live-Skript mit Timestamps "M:SS | Text"

Nur JSON zurückgeben.`;

  try {
    const resp = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type':         'application/json',
        'x-api-key':            key,
        'anthropic-version':    '2023-06-01',
        'anthropic-dangerous-direct-browser-calls': 'true',
      },
      body: JSON.stringify({
        model:      'claude-opus-4-8',
        max_tokens: 2048,
        system:     systemPrompt,
        messages: [{
          role: 'user',
          content: [
            { type: 'image', source: { type: 'base64', media_type: imageMime, data: imageBase64 } },
            { type: 'text',  text: userPrompt },
          ],
        }],
      }),
    });

    if (!resp.ok) {
      const e = await resp.json().catch(() => ({}));
      throw new Error(e.error?.message || `API-Fehler ${resp.status}`);
    }

    const data = await resp.json();
    const raw  = data.content?.[0]?.text?.trim() || '';
    const json = raw.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
    const parsed = JSON.parse(json);
    render(parsed);

  } catch (err) {
    showErr(err.message || 'Unbekannter Fehler. API Key prüfen.');
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

  /* Veo */
  outVeo.textContent = d.veo || '—';

  /* Live script */
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
    } else {
      span.textContent = line;
    }
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
    '=== TikTok Titel ===\n'       + (outTitle.innerText  || ''),
    '=== Hashtags ===\n'           + (outTags.innerText   || ''),
    '=== Banner Text ===\n'        + (outBanner.innerText || ''),
    '=== Veo 3.1 Prompt ===\n'     + (outVeo.innerText    || ''),
    '=== TikTok Live Script ===\n' + (outLive.innerText   || ''),
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
