'use strict';

/* ── DOM ── */
const apiKeyEl   = document.getElementById('apiKey');
const btnEye     = document.getElementById('btnEye');
const eyeIcon    = document.getElementById('eyeIcon');
const proxyUrlEl = document.getElementById('proxyUrl');
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

/* ── Dropzone ── */
dropzone.addEventListener('click', e => { if (e.target !== fileInput) fileInput.click(); });
dropzone.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') fileInput.click(); });
dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('over'); });
dropzone.addEventListener('dragleave', () => dropzone.classList.remove('over'));
dropzone.addEventListener('drop', e => {
  e.preventDefault();
  dropzone.classList.remove('over');
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

/* ── Helpers ── */
function showErr(msg) { errMsg.innerHTML = msg; errbox.hidden = false; }
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
    model:      'claude-opus-4-5',
    max_tokens: 2048,
    system: `Du bist ein TikTok-Shop-Marketing-Experte für den deutschen Markt.
REGELN: Keine Preise, keine Rabatte, keine falschen Versprechen, TikTok-safe.
Antworte NUR mit einem gültigen JSON-Objekt – kein Text davor oder danach, keine Markdown-Codeblöcke.

Gib exakt dieses JSON zurück:
{
  "title": "TikTok Titel mit Emoji, max 80 Zeichen",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
  "banner": ["Zeile 1 (max 28 Zeichen)","Zeile 2","Zeile 3","Call-to-Action"],
  "veo": "Ausführlicher englischer Veo 3.1 Prompt für 10-Sekunden 9:16 TikTok-Produktvideo",
  "live": "0:00 | Hook\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | Call-to-Action\\n1:45 | Abschluss"
}`,
    messages: [{
      role: 'user',
      content: [
        { type: 'image', source: { type: 'base64', media_type: imageMime, data: imageBase64 } },
        { type: 'text', text: `Analysiere dieses Produktbild und erstelle TikTok-Shop-Content für Deutschland.\n\nKategorie: ${category}\nVideo-Stil: ${style}\nZielgruppe: ${audience}\nTon: ${tone}\n\nNur JSON zurückgeben.` },
      ],
    }],
  };

  try {
    console.log('[TikTok Creator] Sending request to proxy:', proxyUrl);

    let resp;
    try {
      resp = await fetch(`${proxyUrl}/api/generate`, {
        method:  'POST',
        headers: {
          'Content-Type':   'application/json',
          'x-api-key-fwd': key,
        },
        body: JSON.stringify(payload),
      });
    } catch (networkErr) {
      console.error('[TikTok Creator] Network / CORS error:', networkErr);
      const isCors = networkErr instanceof TypeError;
      if (isCors) {
        throw new Error(
          `Proxy nicht erreichbar (<code>${proxyUrl}</code>).<br>` +
          `<b>Starte den Proxy lokal:</b><br>` +
          `<code>cd proxy &amp;&amp; npm install &amp;&amp; node server.js</code><br>` +
          `Danach diese Seite neu laden.`
        );
      }
      throw new Error(`Netzwerkfehler: ${networkErr.message}`);
    }

    const text = await resp.text();
    console.log('[TikTok Creator] Proxy response status:', resp.status);
    console.log('[TikTok Creator] Proxy response body:', text);

    let data;
    try { data = JSON.parse(text); } catch {
      throw new Error(`Ungültige Antwort vom Proxy (Status ${resp.status}):<br><code>${text.slice(0, 200)}</code>`);
    }

    if (!resp.ok) {
      throw new Error(`Fehler ${resp.status}: ${data.error?.message || data.error || JSON.stringify(data)}`);
    }

    const rawText = data.content?.[0]?.text?.trim() || '';
    if (!rawText) throw new Error('Anthropic hat eine leere Antwort zurückgegeben.');

    const jsonStr = rawText.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
    let parsed;
    try { parsed = JSON.parse(jsonStr); } catch {
      console.error('[TikTok Creator] JSON parse failed. Raw text:', rawText);
      throw new Error(`Claude-Antwort ist kein gültiges JSON:<br><code>${rawText.slice(0, 300)}</code>`);
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
  outTitle.textContent = d.title || '—';

  outTags.innerHTML = '';
  const wrap = document.createElement('div');
  wrap.className = 'tag-wrap';
  (d.hashtags || []).forEach(t => {
    const s = document.createElement('span');
    s.className = 'tag'; s.textContent = t;
    wrap.appendChild(s);
  });
  outTags.appendChild(wrap);

  outBanner.innerHTML = '';
  (d.banner || []).forEach(l => {
    const div = document.createElement('div');
    div.className = 'bline'; div.textContent = l;
    outBanner.appendChild(div);
  });

  outVeo.textContent = d.veo || '—';

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