<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>TikTok Shop Creator</title>
  <link rel="stylesheet" href="style.css" />
</head>
<body>
  <div class="bg-glow"></div>

  <header>
    <div class="logo">
      <span class="logo-icon">⚡</span>
      <span class="logo-text">TikTok <span class="accent">Shop</span> Creator</span>
    </div>
    <div class="badge">Powered by Claude AI</div>
  </header>

  <main>
    <!-- API Key -->
    <section class="card api-card">
      <label class="section-label">
        <span class="label-icon">🔑</span> Anthropic API Key
      </label>
      <div class="input-row">
        <input
          type="password"
          id="apiKey"
          placeholder="sk-ant-api03-..."
          autocomplete="off"
        />
        <button class="btn-toggle-key" id="toggleKey" title="Show/Hide">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </button>
      </div>
      <p class="hint">Dein Key wird nur lokal im Browser verwendet — nie gespeichert oder übertragen.</p>
    </section>

    <!-- Upload -->
    <section class="card upload-card">
      <label class="section-label">
        <span class="label-icon">📸</span> Produktfoto hochladen
      </label>
      <div class="drop-zone" id="dropZone">
        <div class="drop-inner" id="dropInner">
          <div class="upload-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <p class="drop-title">Foto hier ablegen</p>
          <p class="drop-sub">oder klicken zum Auswählen</p>
          <span class="drop-formats">JPG · PNG · WEBP · max 10 MB</span>
        </div>
        <img id="preview" class="preview-img hidden" alt="Vorschau" />
        <input type="file" id="fileInput" accept="image/*" hidden />
      </div>
      <div class="upload-meta hidden" id="uploadMeta">
        <span id="fileName"></span>
        <button class="btn-remove" id="removeImg">✕ Entfernen</button>
      </div>
    </section>

    <!-- Options -->
    <section class="card options-card">
      <label class="section-label">
        <span class="label-icon">🎨</span> Stil & Sprache
      </label>
      <div class="options-grid">
        <div class="option-group">
          <label>Produktkategorie</label>
          <select id="category">
            <option value="auto">Automatisch erkennen</option>
            <option value="electronics">Elektronik / Gadgets</option>
            <option value="fishing">Angel & Outdoor</option>
            <option value="beauty">Beauty & Kosmetik</option>
            <option value="fashion">Mode & Accessoires</option>
            <option value="home">Haus & Garten</option>
            <option value="sports">Sport & Fitness</option>
            <option value="food">Lebensmittel</option>
          </select>
        </div>
        <div class="option-group">
          <label>Video-Stil</label>
          <select id="videoStyle">
            <option value="premium">Premium / Dark</option>
            <option value="energetic">Energetisch / Hell</option>
            <option value="minimal">Minimalistisch</option>
            <option value="lifestyle">Lifestyle / Outdoor</option>
          </select>
        </div>
        <div class="option-group">
          <label>Zielgruppe</label>
          <select id="audience">
            <option value="general">Allgemein</option>
            <option value="young">18–25 Jahre</option>
            <option value="adult">25–45 Jahre</option>
            <option value="senior">45+ Jahre</option>
          </select>
        </div>
        <div class="option-group">
          <label>Ton</label>
          <select id="tone">
            <option value="exciting">Aufregend</option>
            <option value="professional">Professionell</option>
            <option value="friendly">Freundlich</option>
            <option value="humorous">Humorvoll</option>
          </select>
        </div>
      </div>
    </section>

    <!-- Generate Button -->
    <div class="generate-wrap">
      <button class="btn-generate" id="generateBtn" disabled>
        <span class="btn-text">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          </svg>
          Content generieren
        </span>
        <span class="btn-loading hidden">
          <svg class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12a9 9 0 11-6.219-8.56"/>
          </svg>
          Analysiere Produkt...
        </span>
      </button>
    </div>

    <!-- Error -->
    <div class="error-box hidden" id="errorBox">
      <span class="error-icon">⚠️</span>
      <span id="errorMsg"></span>
    </div>

    <!-- Results -->
    <section class="results hidden" id="results">
      <div class="results-header">
        <h2>Generierter Content</h2>
        <button class="btn-copy-all" id="copyAll">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
            <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
          </svg>
          Alles kopieren
        </button>
      </div>

      <div class="result-grid">

        <!-- TikTok Title -->
        <div class="result-card" data-type="title">
          <div class="result-header">
            <span class="result-icon">🎬</span>
            <span class="result-label">TikTok Titel</span>
            <button class="btn-copy" data-target="outTitle">Kopieren</button>
          </div>
          <div class="result-content" id="outTitle"></div>
        </div>

        <!-- Hashtags -->
        <div class="result-card" data-type="hashtags">
          <div class="result-header">
            <span class="result-icon">#️⃣</span>
            <span class="result-label">5 Hashtags (DE)</span>
            <button class="btn-copy" data-target="outHashtags">Kopieren</button>
          </div>
          <div class="result-content hashtag-list" id="outHashtags"></div>
        </div>

        <!-- Banner Text -->
        <div class="result-card" data-type="banner">
          <div class="result-header">
            <span class="result-icon">🖼️</span>
            <span class="result-label">Banner Text</span>
            <button class="btn-copy" data-target="outBanner">Kopieren</button>
          </div>
          <div class="result-content banner-preview" id="outBanner"></div>
        </div>

        <!-- Veo Prompt -->
        <div class="result-card wide" data-type="veo">
          <div class="result-header">
            <span class="result-icon">🎥</span>
            <span class="result-label">Veo 3.1 Prompt</span>
            <button class="btn-copy" data-target="outVeo">Kopieren</button>
          </div>
          <div class="result-content veo-content" id="outVeo"></div>
        </div>

        <!-- Live Script -->
        <div class="result-card wide" data-type="live">
          <div class="result-header">
            <span class="result-icon">🎙️</span>
            <span class="result-label">TikTok Live Script</span>
            <button class="btn-copy" data-target="outLive">Kopieren</button>
          </div>
          <div class="result-content live-content" id="outLive"></div>
        </div>

      </div>
    </section>
  </main>

  <footer>
    <p>TikTok Shop Creator &mdash; Kein Preis, keine Rabatte, kein Spam. Nur guter Content.</p>
  </footer>

  <script src="app.js"></script>
</body>
</html>
