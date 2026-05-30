'use strict';

// ── DOM refs ──────────────────────────────────────────────────────────────────
const apiKeyInput   = document.getElementById('apiKey');
const toggleKeyBtn  = document.getElementById('toggleKey');
const dropZone      = document.getElementById('dropZone');
const dropInner     = document.getElementById('dropInner');
const fileInput     = document.getElementById('fileInput');
const preview       = document.getElementById('preview');
const uploadMeta    = document.getElementById('uploadMeta');
const fileName      = document.getElementById('fileName');
const removeImg     = document.getElementById('removeImg');
const generateBtn   = document.getElementById('generateBtn');
const errorBox      = document.getElementById('errorBox');
const errorMsg      = document.getElementById('errorMsg');
const results       = document.getElementById('results');
const copyAllBtn    = document.getElementById('copyAll');

const out = {
  title:    document.getElementById('outTitle'),
  hashtags: document.getElementById('outHashtags'),
  banner:   document.getElementById('outBanner'),
  veo:      document.getElementById('outVeo'),
  live:     document.getElementById('outLive'),
};

// ── State ─────────────────────────────────────────────────────────────────────
let imageBase64 = null;
let imageMime   = 'image/jpeg';

// ── API Key toggle ────────────────────────────────────────────────────────────
toggleKeyBtn.addEventListener('click', () => {
  const isHidden = apiKeyInput.type === 'password';
  apiKeyInput.type = isHidden ? 'text' : 'password';
  toggleKeyBtn.querySelector('svg').style.opacity = isHidden ? '0.5' : '1';
});

// ── Enable generate when both key + image present ─────────────────────────────
function checkReady() {
  generateBtn.disabled = !(apiKeyInput.value.trim() && imageBase64);
}
apiKeyInput.addEventListener('input', checkReady);

// ── Drop Zone ─────────────────────────────────────────────────────────────────
dropZone.addEventListener('click', () => fileInput.click());

dropZone.addEventListener('dragover', e => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});
dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
dropZone.addEventListener('drop', e => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (file) loadFile(file);
});

fileInput.addEventListener('change', () => {
  if (fileInput.files[0]) loadFile(fileInput.files[0]);
});

removeImg.addEventListener('click', e => {
  e.stopPropagation();
  clearImage();
});

function loadFile(file) {
  if (!file.type.startsWith('image/')) return showError('Bitte nur Bilddateien hochladen.');
  if (file.size > 10 * 1024 * 1024) return showError('Datei zu groß (max. 10 MB).');

  imageMime = file.type;
  const reader = new FileReader();
  reader.onload = ev => {
    const dataUrl = ev.target.result;
    imageBase64 = dataUrl.split(',')[1];
    preview.src = dataUrl;
    preview.classList.remove('hidden');
    dropInner.classList.add('hidden');
    uploadMeta.classList.remove('hidden');
    fileName.textContent = file.name;
    hideError();
    checkReady();
  };
  reader.readAsDataURL(file);
}

function clearImage() {
  imageBase64 = null;
  preview.src = '';
  preview.classList.add('hidden');
  dropInner.classList.remove('hidden');
  uploadMeta.classList.add('hidden');
  fileName.textContent = '';
  fileInput.value = '';
  checkReady();
}

// ── Error helpers ─────────────────────────────────────────────────────────────
function showError(msg) {
  errorMsg.textContent = msg;
  errorBox.classList.remove('hidden');
}
function hideError() {
  errorBox.classList.add('hidden');
}

// ── Generate ──────────────────────────────────────────────────────────────────
generateBtn.addEventListener('click', generate);

async function generate() {
  hideError();
  setLoading(true);
  results.classList.add('hidden');

  const apiKey   = apiKeyInput.value.trim();
  const category = document.getElementById('category').value;
  const style    = document.getElementById('videoStyle').value;
  const audience = document.getElementById('audience').value;
  const tone     = document.getElementById('tone').value;

  const prompt = buildPrompt(category, style, audience, tone);

  try {
    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'Content-Type':         'application/json',
        'x-api-key':            apiKey,
        'anthropic-version':    '2023-06-01',
        'anthropic-dangerous-direct-browser-calls': 'true',
      },
      body: JSON.stringify({
        model: 'claude-opus-4-8',
        max_tokens: 2048,
        messages: [{
          role: 'user',
          content: [
            {
              type: 'image',
              source: {
                type:       'base64',
                media_type: imageMime,
                data:       imageBase64,
              },
            },
            { type: 'text', text: prompt },
          ],
        }],
      }),
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error?.message || `API Fehler ${response.status}`);
    }

    const data  = await response.json();
    const text  = data.content?.[0]?.text || '';
    const parsed = parseResponse(text);
    renderResults(parsed);

  } catch (err) {
    showError(err.message || 'Unbekannter Fehler. Bitte API Key prüfen.');
  } finally {
    setLoading(false);
  }
}

function setLoading(on) {
  generateBtn.disabled = on;
  document.querySelector('.btn-text').classList.toggle('hidden', on);
  document.querySelector('.btn-loading').classList.toggle('hidden', !on);
}

// ── Prompt builder ────────────────────────────────────────────────────────────
function buildPrompt(category, style, audience, tone) {
  const styleMap = {
    premium:   'dunkel, premium, Sonar-Blau Glow, Orange-Akzente',
    energetic: 'hell, energetisch, kräftige Farben, schnelle Schnitte',
    minimal:   'minimalistisch, viel Weißraum, clean, modern',
    lifestyle: 'Lifestyle, Outdoor, natürliches Licht, authentisch',
  };
  const audienceMap = {
    general: 'allgemeines Publikum',
    young:   '18–25 Jahre, Gen Z',
    adult:   '25–45 Jahre',
    senior:  '45+ Jahre',
  };
  const toneMap = {
    exciting:     'aufregend und mitreißend',
    professional: 'professionell und vertrauenswürdig',
    friendly:     'freundlich und nahbar',
    humorous:     'humorvoll und leicht',
  };

  return `Du bist ein TikTok Shop Marketing-Experte. Analysiere dieses Produktfoto und erstelle hochwertigen deutschen Marketing-Content.

Einstellungen:
- Kategorie: ${category === 'auto' ? 'automatisch erkennen' : category}
- Video-Stil: ${styleMap[style] || style}
- Zielgruppe: ${audienceMap[audience] || audience}
- Ton: ${toneMap[tone] || tone}

WICHTIGE REGELN:
- Kein Preis nennen
- Keine Rabatte oder Prozente
- Keine falschen Versprechen
- TikTok-sicher (keine übertriebenen Health-Claims)
- Produkt muss klar erkennbar sein
- Sprache: Deutsch (außer Veo-Prompt = Englisch)

Erstelle exakt dieses JSON-Format (nur JSON, kein Text drumherum):

{
  "title": "TikTok Titel mit max 80 Zeichen, emoji am Anfang",
  "hashtags": ["#Hashtag1", "#Hashtag2", "#Hashtag3", "#Hashtag4", "#Hashtag5"],
  "banner": [
    "Kurztext Zeile 1 (max 30 Zeichen)",
    "Kurztext Zeile 2 (max 30 Zeichen)",
    "Kurztext Zeile 3 (max 30 Zeichen)",
    "Call-to-Action Zeile 4"
  ],
  "veo": "Vollständiger Veo 3.1 Prompt auf Englisch für ein 10-Sekunden vertikales 9:16 TikTok-Produktvideo. Beschreibe Stil, Kameraführung, Lighting, Übergänge, Text-Overlays und Sound.",
  "live": "0:00 | Hook-Satz\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Social Proof / Nutzen\\n1:30 | Call-to-Action\\n1:45 | Abschluss"
}`;
}

// ── Response parser ───────────────────────────────────────────────────────────
function parseResponse(text) {
  const jsonMatch = text.match(/\{[\s\S]*\}/);
  if (!jsonMatch) throw new Error('Antwort konnte nicht verarbeitet werden. Bitte erneut versuchen.');
  try {
    return JSON.parse(jsonMatch[0]);
  } catch {
    throw new Error('JSON-Parsing fehlgeschlagen. Bitte erneut versuchen.');
  }
}

// ── Render results ────────────────────────────────────────────────────────────
function renderResults(data) {
  // Title
  out.title.textContent = data.title || '—';

  // Hashtags
  out.hashtags.innerHTML = '';
  const tags = Array.isArray(data.hashtags) ? data.hashtags : [];
  tags.forEach(tag => {
    const pill = document.createElement('span');
    pill.className = 'hashtag-pill';
    pill.textContent = tag;
    out.hashtags.appendChild(pill);
  });

  // Banner
  out.banner.innerHTML = '';
  const lines = Array.isArray(data.banner) ? data.banner : [];
  lines.forEach(line => {
    const div = document.createElement('div');
    div.className = 'banner-line';
    div.textContent = line;
    out.banner.appendChild(div);
  });

  // Veo
  out.veo.textContent = data.veo || '—';

  // Live script — format timestamps
  const liveRaw = data.live || '';
  out.live.innerHTML = '';
  liveRaw.split('\n').forEach(line => {
    const span = document.createElement('span');
    span.className = 'live-line';
    const timeMatch = line.match(/^(\d+:\d+)\s*\|(.*)$/);
    if (timeMatch) {
      const time = document.createElement('span');
      time.className = 'live-time';
      time.textContent = timeMatch[1];
      span.appendChild(time);
      span.appendChild(document.createTextNode(timeMatch[2].trim()));
    } else {
      span.textContent = line;
    }
    out.live.appendChild(span);
  });

  results.classList.remove('hidden');
  results.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ── Copy buttons ──────────────────────────────────────────────────────────────
document.querySelectorAll('.btn-copy').forEach(btn => {
  btn.addEventListener('click', () => {
    const targetId = btn.dataset.target;
    const el = document.getElementById(targetId);
    if (!el) return;

    const text = getPlainText(el);
    copyToClipboard(text, btn);
  });
});

copyAllBtn.addEventListener('click', () => {
  const all = [
    `=== TikTok Titel ===\n${getPlainText(out.title)}`,
    `=== Hashtags ===\n${getPlainText(out.hashtags)}`,
    `=== Banner Text ===\n${getPlainText(out.banner)}`,
    `=== Veo 3.1 Prompt ===\n${getPlainText(out.veo)}`,
    `=== TikTok Live Script ===\n${getPlainText(out.live)}`,
  ].join('\n\n');
  copyToClipboard(all, copyAllBtn);
});

function getPlainText(el) {
  return el.innerText || el.textContent || '';
}

function copyToClipboard(text, btn) {
  navigator.clipboard.writeText(text).then(() => {
    const original = btn.textContent;
    btn.textContent = '✓ Kopiert';
    btn.classList.add('copied');
    setTimeout(() => {
      btn.textContent = original;
      btn.classList.remove('copied');
    }, 2000);
  }).catch(() => {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
  });
}
