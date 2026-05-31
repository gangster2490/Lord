'use strict';

// ── DOM refs ──────────────────────────────────────────────────────────────────
const dropZone      = document.getElementById('drop-zone');
const dropZoneInner = document.getElementById('drop-zone-inner');
const fileInput     = document.getElementById('file-input');
const previewImg    = document.getElementById('preview-img');
const backendUrlInput = document.getElementById('backend-url');
const btnGenerate   = document.getElementById('btn-generate');
const btnLabel      = document.getElementById('btn-label');
const btnSpinner    = document.getElementById('btn-spinner');
const errorBox      = document.getElementById('error-box');
const errorMsg      = document.getElementById('error-msg');
const resultsSection = document.getElementById('results');
const btnCopyAll    = document.getElementById('btn-copy-all');

// Result content elements
const titleContent    = document.getElementById('title-content');
const hashtagsContent = document.getElementById('hashtags-content');
const bannerContent   = document.getElementById('banner-content');
const veoContent      = document.getElementById('veo-content');
const liveContent     = document.getElementById('live-content');

// ── State ─────────────────────────────────────────────────────────────────────
let selectedFile = null;

// ── Init ──────────────────────────────────────────────────────────────────────
(function init() {
  const saved = localStorage.getItem('backendUrl');
  if (saved) backendUrlInput.value = saved;
  else backendUrlInput.value = 'http://localhost:3001';
})();

backendUrlInput.addEventListener('input', () => {
  localStorage.setItem('backendUrl', backendUrlInput.value.trim());
});

// ── Drop Zone ─────────────────────────────────────────────────────────────────
dropZone.addEventListener('click', (e) => {
  if (e.target !== fileInput) fileInput.click();
});
dropZone.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') fileInput.click();
});

dropZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});
dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
dropZone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (file) setFile(file);
});

fileInput.addEventListener('change', () => {
  if (fileInput.files[0]) setFile(fileInput.files[0]);
});

function setFile(file) {
  if (!file.type.startsWith('image/')) {
    showError('Bitte nur Bilddateien hochladen.');
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    showError('Datei zu groß (max. 10 MB).');
    return;
  }
  selectedFile = file;
  hideError();

  const reader = new FileReader();
  reader.onload = (ev) => {
    previewImg.src = ev.target.result;
    previewImg.classList.add('visible');
    dropZoneInner.style.display = 'none';
  };
  reader.readAsDataURL(file);
}

// ── Error helpers ─────────────────────────────────────────────────────────────
function showError(msg) {
  errorMsg.textContent = msg;
  errorBox.hidden = false;
}
function hideError() {
  errorBox.hidden = true;
  errorMsg.textContent = '';
}

// ── Loading state ─────────────────────────────────────────────────────────────
function setLoading(on) {
  btnGenerate.disabled = on;
  btnLabel.hidden = on;
  btnSpinner.hidden = !on;
}

// ── Generate ──────────────────────────────────────────────────────────────────
btnGenerate.addEventListener('click', generate);

async function generate() {
  hideError();

  if (!selectedFile) {
    showError('Bitte zuerst ein Produktbild auswählen.');
    return;
  }

  const backendUrl = (backendUrlInput.value.trim() || 'http://localhost:3001').replace(/\/$/, '');

  const formData = new FormData();
  formData.append('image', selectedFile);
  formData.append('category', document.getElementById('category').value);
  formData.append('videoStyle', document.getElementById('videoStyle').value);
  formData.append('audience', document.getElementById('audience').value);
  formData.append('tone', document.getElementById('tone').value);

  setLoading(true);
  resultsSection.hidden = true;

  try {
    const response = await fetch(backendUrl + '/api/generate', {
      method: 'POST',
      body: formData,
      // No Authorization header — API key is on backend only
    });

    let data;
    try {
      data = await response.json();
    } catch {
      throw new Error('Server returned invalid response.');
    }

    if (!response.ok) {
      throw new Error(data.error || `Server-Fehler ${response.status}`);
    }

    renderResults(data);
  } catch (err) {
    showError(err.message || 'Unbekannter Fehler. Bitte Backend-URL prüfen.');
  } finally {
    setLoading(false);
  }
}

// ── Render results ────────────────────────────────────────────────────────────
function renderResults(data) {
  // Title
  titleContent.textContent = data.title || '—';

  // Hashtags
  hashtagsContent.innerHTML = '';
  const pillsWrapper = document.createElement('div');
  pillsWrapper.className = 'hashtag-pills';
  const tags = Array.isArray(data.hashtags) ? data.hashtags : [];
  tags.forEach(tag => {
    const pill = document.createElement('span');
    pill.className = 'hashtag-pill';
    pill.textContent = tag;
    pillsWrapper.appendChild(pill);
  });
  hashtagsContent.appendChild(pillsWrapper);

  // Banner
  bannerContent.innerHTML = '';
  const lines = Array.isArray(data.banner) ? data.banner : [];
  lines.forEach(line => {
    const div = document.createElement('div');
    div.className = 'banner-line';
    div.textContent = line;
    bannerContent.appendChild(div);
  });

  // Veo
  veoContent.textContent = data.veo || '—';

  // Live script — timestamps as colored pills
  liveContent.innerHTML = '';
  const liveRaw = data.live || '';
  liveRaw.split('\n').forEach(line => {
    const span = document.createElement('span');
    span.className = 'live-line';
    const timeMatch = line.match(/^(\d+:\d+)\s*\|(.*)$/);
    if (timeMatch) {
      const timePill = document.createElement('span');
      timePill.className = 'live-time';
      timePill.textContent = timeMatch[1];
      span.appendChild(timePill);
      span.appendChild(document.createTextNode(timeMatch[2].trim()));
    } else {
      span.textContent = line;
    }
    liveContent.appendChild(span);
  });

  resultsSection.hidden = false;
  resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ── Copy buttons ──────────────────────────────────────────────────────────────
document.addEventListener('click', (e) => {
  const btn = e.target.closest('.btn-copy');
  if (!btn) return;
  const targetId = btn.dataset.target;
  const el = targetId ? document.getElementById(targetId) : null;
  if (!el) return;
  copyToClipboard(getPlainText(el), btn);
});

btnCopyAll.addEventListener('click', () => {
  const all = [
    '=== TikTok Titel ===\n' + getPlainText(titleContent),
    '=== Hashtags ===\n' + getPlainText(hashtagsContent),
    '=== Banner Text ===\n' + getPlainText(bannerContent),
    '=== Veo 3.1 Prompt ===\n' + getPlainText(veoContent),
    '=== TikTok Live Skript ===\n' + getPlainText(liveContent),
  ].join('\n\n');
  copyToClipboard(all, btnCopyAll);
});

function getPlainText(el) {
  return (el.innerText || el.textContent || '').trim();
}

function copyToClipboard(text, btn) {
  const originalText = btn.textContent;
  const originalClass = btn.className;

  const onSuccess = () => {
    btn.textContent = '✓ Kopiert';
    btn.classList.add('copied');
    setTimeout(() => {
      btn.textContent = originalText;
      btn.className = originalClass;
    }, 2000);
  };

  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(onSuccess).catch(() => fallbackCopy(text, onSuccess));
  } else {
    fallbackCopy(text, onSuccess);
  }
}

function fallbackCopy(text, onSuccess) {
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.cssText = 'position:fixed;opacity:0;top:0;left:0;';
  document.body.appendChild(ta);
  ta.focus();
  ta.select();
  try {
    document.execCommand('copy');
    onSuccess();
  } catch {}
  document.body.removeChild(ta);
}
