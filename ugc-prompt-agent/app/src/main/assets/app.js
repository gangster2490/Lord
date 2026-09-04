(() => {
  const native = typeof AndroidBridge !== "undefined";
  const OPENERS = [
    "Hand greift ins Bild",
    "Person schaut kurz irritiert",
    "Produkt liegt schon angefangen benutzt im Bild",
    "Kamera wird zufällig draufgehalten",
  ];
  const ENVIRONMENTS = [
    "normale Küche mit Alltagsunordnung",
    "Rücksitz Auto",
    "Camping-Tisch mit anderen Gegenständen drauf",
    "Badezimmer-Ablage",
  ];
  const ACTIONS = [
    "beiläufiges Ausprobieren",
    "kurzes Zögern dann Zufriedenheit",
    "Weiterreichen an zweite Person",
    "Reaktion mit leichtem Lachen",
  ];
  const FORBIDDEN = [
    /\bbeste[rsn]?\b/i,
    /\beinzigartig\b/i,
    /\bgarantiert\b/i,
    /\b100%\b/i,
    /\bheilt\b/i,
    /\bkuriert\b/i,
    /\blindert\s+schmerzen\b/i,
    /\bschnell\s+geliefert\b/i,
    /\bversandkostenfrei\s+garantiert\b/i,
    /\bnur\s+heute\b/i,
    /\blimitiert\b/i,
  ];

  const state = {
    lang: "de",
    hasApiKey: false,
    maskedKey: "",
    images: [],
    firstFrameId: null,
    analysis: null,
    scenes: [],
    selectedScene: null,
    lastSceneKey: localStorage.getItem("ugc_last_scene") || null,
    prompt: "",
    improved: false,
    similarity: null,
    pending: null,
    browserImages: [],
  };

  const $ = (id) => document.getElementById(id);
  const t = (key) => (I18N[state.lang] || I18N.de)[key] || key;
  function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
    });
  }

  function applyI18n() {
    document.documentElement.lang = state.lang;
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      el.textContent = t(el.getAttribute("data-i18n"));
    });
    $("caption").placeholder = t("captionPh");
    $("lang-de").classList.toggle("active", state.lang === "de");
    $("lang-ru").classList.toggle("active", state.lang === "ru");
    renderThumbs();
    renderAnalysis();
    renderScenes();
    renderPrompt();
    renderExport();
    renderHistory(state.historyCache || []);
    updateCount();
    $("masked-key").textContent = state.maskedKey || "";
  }

  function showPage(name) {
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.classList.toggle("active", tab.dataset.tab === name);
    });
    document.querySelectorAll(".page").forEach((page) => {
      page.classList.toggle("active", page.id === "page-" + name);
    });
  }

  function errorHtml(code) {
    const map = {
      NO_API_KEY: "needKey",
      INVALID_API_KEY: "invalidKey",
      NEED_PHOTOS: "needPhotos",
      NETWORK: "network",
      TIMEOUT: "timeout",
      RATE_LIMIT: "rateLimit",
      PARSE_ERROR: "parse",
      IMAGE_TOO_LARGE: "tooLarge",
    };
    const msg = t(map[code] || "generic");
    const settings = code === "NO_API_KEY" || code === "INVALID_API_KEY";
    return `<div class="err-box">${msg}<div class="btn-row">${
      settings
        ? `<button class="btn btn-outline" id="goto-settings">${t("openSettings")}</button>`
        : `<button class="btn btn-outline" id="retry-last">${t("retry")}</button>`
    }</div></div>`;
  }

  function bindErrorActions(container) {
    const go = container.querySelector("#goto-settings");
    if (go) go.onclick = () => $("settings").classList.add("open");
    const retry = container.querySelector("#retry-last");
    if (retry) retry.onclick = () => state.pending && state.pending();
  }

  function updateCount() {
    const n = state.images.length;
    $("count-hint").textContent = n ? `${n} / 20` : "";
    $("btn-analyze").disabled = n < 15 || n > 20;
    $("dropzone").classList.toggle("has-files", n > 0);
  }

  function renderThumbs() {
    const root = $("thumbs");
    root.innerHTML = "";
    state.images.forEach((img) => {
      const wrap = document.createElement("div");
      wrap.className = "thumb" + (img.id === state.firstFrameId ? " selected" : "");
      wrap.innerHTML = `<img alt="" src="${img.thumb}"/><span class="thumb-mark">${
        img.id === state.firstFrameId ? t("firstFrame") : ""
      }</span>`;
      wrap.onclick = () => {
        state.firstFrameId = img.id;
        if (native) AndroidBridge.setFirstFrame(img.id);
        renderThumbs();
        renderExport();
        runSimilarity();
      };
      root.appendChild(wrap);
    });
  }

  function renderAnalysis() {
    const box = $("dna-box");
    if (!state.analysis) {
      box.classList.add("hidden");
      return;
    }
    const a = state.analysis;
    box.classList.remove("hidden");
    box.innerHTML = `
      <div><b>${t("useCase")}:</b> ${esc(a.use_case)}</div>
      <div><b>${t("audience")}:</b> ${esc(a.target_audience)}</div>
      <div><b>${t("context")}:</b> ${esc(a.everyday_context)}</div>
      <div><b>${t("pain")}:</b> ${esc(a.pain_point_solved)}</div>
      ${a.ambiguity_warning ? `<div><b>${t("ambiguity")}:</b> ${esc(a.ambiguity_warning)}</div>` : ""}
    `;
  }

  function renderSimilarity() {
    const box = $("sim-box");
    const card = $("sim-card");
    if (!state.similarity) {
      box.textContent = "";
      card.classList.remove("warn");
      return;
    }
    if (state.similarity.warning) {
      card.classList.add("warn");
      box.innerHTML = `<div class="warn-box">${t("similarityWarn")}</div>`;
    } else {
      card.classList.remove("warn");
      box.innerHTML = `<div class="ok-box">${t("similarityOk")}</div>`;
    }
  }

  function renderScenes() {
    const root = $("scenes");
    root.innerHTML = "";
    state.scenes.forEach((scene) => {
      const el = document.createElement("div");
      el.className = "scene" + (state.selectedScene && state.selectedScene.key === scene.key ? " selected" : "");
      el.innerHTML = `
        <div class="k">${t("opener")}</div><p>${esc(scene.opener)}</p>
        <div class="k">${t("environment")}</div><p>${esc(scene.environment)}</p>
        <div class="k">${t("action")}</div><p>${esc(scene.action)}</p>`;
      el.onclick = () => {
        state.selectedScene = scene;
        renderScenes();
      };
      root.appendChild(el);
    });
    $("btn-prompt").disabled = !state.selectedScene || !state.analysis;
  }

  function wordCount(text) {
    return text.trim() ? text.trim().split(/\s+/).length : 0;
  }

  function renderPrompt() {
    const box = $("prompt-box");
    $("btn-improve").disabled = !state.prompt;
    if (!state.prompt) {
      box.classList.add("hidden");
      $("word-count").textContent = "";
      return;
    }
    box.classList.remove("hidden");
    box.textContent = state.prompt;
    $("word-count").textContent = `${wordCount(state.prompt)} ${t("words")} · 9:16 · ≤8s`;
  }

  function renderExport() {
    const first = state.images.find((i) => i.id === state.firstFrameId) || state.images[0];
    const card = $("ref-card");
    if (first) card.innerHTML = `<img alt="first-frame" src="${first.thumb}"/>`;
    $("export-prompt").textContent = state.prompt || "—";
  }

  function renderHistory(entries) {
    state.historyCache = entries;
    const root = $("history-list");
    if (!entries.length) {
      root.innerHTML = `<p class="hint">${t("historyEmpty")}</p>`;
      return;
    }
    root.innerHTML = "";
    entries.forEach((item) => {
      const el = document.createElement("div");
      el.className = "history-item";
      el.innerHTML = `
        ${item.firstFrameThumb ? `<img src="${item.firstFrameThumb}" alt=""/>` : ""}
        <div style="flex:1">
          <h4>${esc(item.label || "UGC")}</h4>
          <p>${esc(item.prompt || "")}</p>
          <div class="btn-row">
            <button class="btn btn-outline reuse">${t("reuse")}</button>
            <button class="btn btn-danger del">${t("remove")}</button>
          </div>
        </div>`;
      el.querySelector(".reuse").onclick = () => reuseHistory(item);
      el.querySelector(".del").onclick = () => {
        if (native) AndroidBridge.deleteHistory(item.id);
        else {
          const next = (JSON.parse(localStorage.getItem("ugc_history") || "[]")).filter((x) => x.id !== item.id);
          localStorage.setItem("ugc_history", JSON.stringify(next));
          renderHistory(next);
        }
      };
      root.appendChild(el);
    });
  }

  function reuseHistory(item) {
    try { state.analysis = JSON.parse(item.analysisJson); } catch (_) { state.analysis = null; }
    try { state.selectedScene = JSON.parse(item.sceneJson); } catch (_) { state.selectedScene = null; }
    if (state.selectedScene) state.scenes = [state.selectedScene];
    state.prompt = item.prompt || "";
    $("caption").value = item.caption || "";
    renderAnalysis();
    renderScenes();
    renderPrompt();
    renderExport();
    showPage("veo");
  }

  function esc(s) {
    return String(s || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function runSimilarity() {
    if (native) AndroidBridge.checkSimilarity();
    else browserSimilarity();
  }

  function pickLocalScenes() {
    const all = [];
    OPENERS.forEach((opener) => {
      ENVIRONMENTS.forEach((environment) => {
        ACTIONS.forEach((action) => {
          all.push({ opener, environment, action, key: `${opener}|${environment}|${action}` });
        });
      });
    });
    const filtered = all.filter((s) => s.key !== state.lastSceneKey);
    for (let i = filtered.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [filtered[i], filtered[j]] = [filtered[j], filtered[i]];
    }
    return filtered.slice(0, 4);
  }

  function localCompliance(prompt, caption) {
    const text = `${prompt}\n${caption}`;
    const forbiddenHits = FORBIDDEN.filter((re) => re.test(text)).map((re) => re.source);
    const hasAdDisclosure = /\b(werbung|anzeige)\b/i.test(text);
    return {
      forbiddenHits,
      hasAdDisclosure,
      hasForbiddenLanguage: forbiddenHits.length > 0,
      missingAdDisclosure: !hasAdDisclosure,
    };
  }

  function showCompliance(result) {
    const hits = result.forbiddenHits || [];
    const hitHtml = hits.length
      ? hits.map((h) => `<span class="hit">${esc(h)}</span>`).join("")
      : `<div class="ok-box">${t("noForbidden")}</div>`;
    const disc = result.hasAdDisclosure
      ? `<div class="ok-box">${t("disclosureOk")}</div>`
      : `<div class="warn-box">${t("disclosureMissing")}</div>`;
    $("check-result").innerHTML = `
      <p class="hint" style="margin-top:12px">${t("forbidden")}</p>
      ${hitHtml}
      ${disc}`;
  }

  async function compressFile(file) {
    const bmp = await createImageBitmap(file);
    const max = 1568;
    const scale = Math.min(1, max / Math.max(bmp.width, bmp.height));
    const w = Math.max(1, Math.round(bmp.width * scale));
    const h = Math.max(1, Math.round(bmp.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    canvas.getContext("2d").drawImage(bmp, 0, 0, w, h);
    const blob = await new Promise((res) => canvas.toBlob(res, "image/jpeg", 0.85));
    const thumbCanvas = document.createElement("canvas");
    const ts = Math.min(1, 160 / Math.max(w, h));
    thumbCanvas.width = Math.max(1, Math.round(w * ts));
    thumbCanvas.height = Math.max(1, Math.round(h * ts));
    thumbCanvas.getContext("2d").drawImage(canvas, 0, 0, thumbCanvas.width, thumbCanvas.height);
    const hash = averageHash(canvas);
    return {
      id: uuid(),
      thumb: thumbCanvas.toDataURL("image/jpeg", 0.7),
      dataUrl: canvas.toDataURL("image/jpeg", 0.85),
      hash,
    };
  }

  function averageHash(canvas) {
    const c = document.createElement("canvas");
    c.width = 8;
    c.height = 8;
    const ctx = c.getContext("2d");
    ctx.drawImage(canvas, 0, 0, 8, 8);
    const data = ctx.getImageData(0, 0, 8, 8).data;
    const gray = [];
    for (let i = 0; i < data.length; i += 4) {
      gray.push((data[i] * 30 + data[i + 1] * 59 + data[i + 2] * 11) / 100);
    }
    const mean = gray.reduce((a, b) => a + b, 0) / gray.length;
    let bits = 0n;
    gray.forEach((v, i) => {
      if (v >= mean) bits |= 1n << BigInt(i);
    });
    return bits;
  }

  function hamming(a, b) {
    let x = a ^ b;
    let n = 0;
    while (x) {
      n += Number(x & 1n);
      x >>= 1n;
    }
    return n;
  }

  function browserSimilarity() {
    const first = state.browserImages.find((i) => i.id === state.firstFrameId) || state.browserImages[0];
    if (!first) return;
    const rest = state.browserImages.filter((i) => i.id !== first.id);
    const outliers = rest.filter((i) => hamming(first.hash, i.hash) > 18);
    state.similarity = {
      warning: rest.length > 0 && outliers.length * 3 >= rest.length,
      outlierIds: outliers.map((i) => i.id),
    };
    renderSimilarity();
  }

  const VISION_PROMPT = `Du analysierst 15-20 Fotos EINES EINZIGEN Produkts für ein TikTok-Video-Skript.

KRITISCHE REGEL — KEINE HALLUZINATION:
- Beschreibe ausschließlich, was auf DIESEN Fotos tatsächlich zu sehen ist.
- Erfinde KEINE Eigenschaften, Funktionen, Zubehörteile oder Varianten, die auf den Bildern nicht sichtbar sind.
- Wenn die Fotos uneindeutig oder widersprüchlich sind, melde das im JSON-Feld "ambiguity_warning".
- Gehe NICHT von einem ähnlichen/bekannten Produkt aus deinem Trainingswissen aus.

VERBOTEN: Form, Farbe, Material, Größe, Marke, Optik-Adjektive.
ERLAUBT: Funktion, Zielgruppe, Alltagskontext.

JSON:
{"use_case":"...","target_audience":"...","everyday_context":"...","pain_point_solved":"...","ambiguity_warning":""}`;

  const VIDEO_PROMPT = `Du generierst einen Video-Prompt für Veo/Kling.
HARTE REGEL: Beschreibe NIEMALS Form, Farbe, Material oder Marke.
UGC, Handy, 9:16, MAXIMAL 8 Sekunden, EIN Mikro-Moment, kein Hochglanz.
OUTPUT: nur der Prompt, max. 80 Wörter, Deutsch.`;

  const IMPROVE_PROMPT = `Verfeinere den Veo-Prompt. Keine Produktbeschreibung. Keine KI-Muster. Max 80 Wörter, 9:16, 8s, ein Clip. Nur der Prompt.`;

  async function geminiBrowser(system, user, dataUrls, temperature) {
    const key = ($("api-key").value || localStorage.getItem("ugc_api_key") || "").trim();
    if (!key) {
      const err = new Error("NO_API_KEY");
      err.code = "NO_API_KEY";
      throw err;
    }
    const parts = [{ text: system + "\n\n" + user }];
    (dataUrls || []).forEach((url) => {
      const data = url.split(",")[1];
      if (data) parts.push({ inline_data: { mime_type: "image/jpeg", data } });
    });
    const models = ["gemini-2.0-flash", "gemini-1.5-flash"];
    let last;
    for (const model of models) {
      const res = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ parts }],
            generationConfig: { temperature, maxOutputTokens: 1024 },
          }),
        },
      );
      const raw = await res.text();
      if (res.status === 429) {
        const e = new Error("RATE_LIMIT");
        e.code = "RATE_LIMIT";
        throw e;
      }
      if (res.status === 400 || res.status === 401 || res.status === 403) {
        const e = new Error("INVALID_API_KEY");
        e.code = "INVALID_API_KEY";
        throw e;
      }
      if (res.status === 404) {
        last = raw;
        continue;
      }
      if (!res.ok) {
        const e = new Error("GENERIC");
        e.code = "GENERIC";
        throw e;
      }
      const json = JSON.parse(raw);
      const text = (json.candidates || [])
        .flatMap((c) => ((c.content || {}).parts || []).map((p) => p.text || ""))
        .join("")
        .trim();
      if (!text) {
        const e = new Error("PARSE_ERROR");
        e.code = "PARSE_ERROR";
        throw e;
      }
      return text;
    }
    const e = new Error("GENERIC");
    e.code = "GENERIC";
    throw e;
  }

  function extractJson(text) {
    const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
    const candidate = (fenced ? fenced[1] : text).trim();
    const start = candidate.indexOf("{");
    const end = candidate.lastIndexOf("}");
    return JSON.parse(candidate.slice(start, end + 1));
  }

  window.__nativeEvent = function (b64) {
    const json = JSON.parse(decodeURIComponent(escape(atob(b64))));
    handleNative(json.event, json.payload || {});
  };

  function handleNative(event, payload) {
    if (event === "images" && !payload.cancelled) {
      state.images = payload.images || [];
      state.firstFrameId = payload.firstFrameId || (state.images[0] && state.images[0].id);
      renderThumbs();
      updateCount();
      renderExport();
      if (state.images.length) runSimilarity();
    }
    if (event === "apiKey") {
      state.hasApiKey = !!payload.hasApiKey;
      state.maskedKey = payload.maskedKey || "";
      $("masked-key").textContent = state.maskedKey;
    }
    if (event === "language") {
      state.lang = payload.language === "ru" ? "ru" : "de";
      applyI18n();
    }
    if (event === "similarity") {
      state.similarity = payload;
      renderSimilarity();
    }
    if (event === "analysis") {
      $("analyze-load").classList.add("hidden");
      state.analysis = payload;
      renderAnalysis();
    }
    if (event === "scenes") {
      state.scenes = payload.scenes || [];
      state.selectedScene = null;
      renderScenes();
    }
    if (event === "prompt") {
      $("prompt-load").classList.add("hidden");
      state.prompt = payload.prompt || "";
      state.improved = !!payload.improved;
      if (payload.scene) state.selectedScene = payload.scene;
      renderPrompt();
      renderExport();
    }
    if (event === "compliance") showCompliance(payload);
    if (event === "history") renderHistory(payload.entries || []);
    if (event === "copied") {
      $("export-msg").innerHTML = `<div class="ok-box">${t("copied")}</div>`;
    }
    if (event === "error") {
      $("analyze-load").classList.add("hidden");
      $("prompt-load").classList.add("hidden");
      const target = payload.event === "prompt" ? $("prompt-error") : $("analyze-error");
      target.innerHTML = errorHtml(payload.code);
      bindErrorActions(target);
    }
  }

  document.querySelectorAll(".tab").forEach((tab) => {
    tab.onclick = () => {
      showPage(tab.dataset.tab);
      if (tab.dataset.tab === "history") {
        if (native) AndroidBridge.loadHistory();
        else renderHistory(JSON.parse(localStorage.getItem("ugc_history") || "[]"));
      }
    };
  });

  $("btn-settings").onclick = () => $("settings").classList.add("open");
  $("btn-close-settings").onclick = () => $("settings").classList.remove("open");
  $("settings").addEventListener("click", (e) => {
    if (e.target.id === "settings") $("settings").classList.remove("open");
  });

  $("lang-de").onclick = () => {
    state.lang = "de";
    if (native) AndroidBridge.setLanguage("de");
    else localStorage.setItem("ugc_lang", "de");
    applyI18n();
  };
  $("lang-ru").onclick = () => {
    state.lang = "ru";
    if (native) AndroidBridge.setLanguage("ru");
    else localStorage.setItem("ugc_lang", "ru");
    applyI18n();
  };

  $("btn-save-key").onclick = () => {
    const key = $("api-key").value.trim();
    if (native) AndroidBridge.saveApiKey(key);
    else {
      localStorage.setItem("ugc_api_key", key);
      state.hasApiKey = !!key;
      state.maskedKey = key ? key.slice(0, 4) + "••••" + key.slice(-4) : "";
      $("masked-key").textContent = t("apiSaved") + " · " + state.maskedKey;
    }
  };
  $("btn-clear-key").onclick = () => {
    $("api-key").value = "";
    if (native) AndroidBridge.saveApiKey("");
    else {
      localStorage.removeItem("ugc_api_key");
      state.hasApiKey = false;
      state.maskedKey = "";
      $("masked-key").textContent = "";
    }
  };

  $("dropzone").onclick = () => {
    if (native) AndroidBridge.pickImages();
    else $("file-input").click();
  };
  $("file-input").onchange = async (e) => {
    const files = Array.from(e.target.files || []).slice(0, 20);
    const imported = [];
    for (const file of files) imported.push(await compressFile(file));
    state.browserImages = imported;
    state.images = imported.map((i) => ({ id: i.id, thumb: i.thumb }));
    state.firstFrameId = imported[0] && imported[0].id;
    renderThumbs();
    updateCount();
    renderExport();
    runSimilarity();
  };

  $("btn-analyze").onclick = async () => {
    state.pending = () => $("btn-analyze").click();
    $("analyze-error").innerHTML = "";
    $("analyze-load").textContent = t("analyzing");
    $("analyze-load").classList.remove("hidden");
    if (native) {
      AndroidBridge.analyze();
      return;
    }
    try {
      if (state.browserImages.length < 15) throw Object.assign(new Error("NEED_PHOTOS"), { code: "NEED_PHOTOS" });
      const text = await geminiBrowser(
        VISION_PROMPT,
        "Analysiere diese Produktfotos. Nur JSON.",
        state.browserImages.map((i) => i.dataUrl),
        0.2,
      );
      state.analysis = extractJson(text);
      renderAnalysis();
    } catch (err) {
      $("analyze-error").innerHTML = errorHtml(err.code || "GENERIC");
      bindErrorActions($("analyze-error"));
    } finally {
      $("analyze-load").classList.add("hidden");
    }
  };

  $("btn-scenes").onclick = () => {
    if (native) AndroidBridge.generateScenes();
    else {
      state.scenes = pickLocalScenes();
      state.selectedScene = null;
      renderScenes();
    }
  };

  $("btn-prompt").onclick = async () => {
    if (!state.selectedScene) return;
    state.pending = () => $("btn-prompt").click();
    $("prompt-error").innerHTML = "";
    $("prompt-load").textContent = t("building");
    $("prompt-load").classList.remove("hidden");
    if (native) {
      AndroidBridge.buildPrompt(JSON.stringify(state.selectedScene), JSON.stringify(state.analysis || {}));
      return;
    }
    try {
      const first = state.browserImages.find((i) => i.id === state.firstFrameId) || state.browserImages[0];
      const user = `Kontext-JSON: ${JSON.stringify(state.analysis)}
Szene: ${state.selectedScene.opener} / ${state.selectedScene.environment} / ${state.selectedScene.action}
First-Frame ist das Foto. Produkt nicht beschreiben.`;
      state.prompt = await geminiBrowser(VIDEO_PROMPT, user, first ? [first.dataUrl] : [], 0.85);
      state.lastSceneKey = state.selectedScene.key;
      localStorage.setItem("ugc_last_scene", state.lastSceneKey);
      renderPrompt();
      renderExport();
    } catch (err) {
      $("prompt-error").innerHTML = errorHtml(err.code || "GENERIC");
      bindErrorActions($("prompt-error"));
    } finally {
      $("prompt-load").classList.add("hidden");
    }
  };

  $("btn-improve").onclick = async () => {
    if (!state.prompt || !state.selectedScene) return;
    state.pending = () => $("btn-improve").click();
    $("prompt-load").textContent = t("improving");
    $("prompt-load").classList.remove("hidden");
    if (native) {
      AndroidBridge.improvePrompt(state.prompt, JSON.stringify(state.selectedScene));
      return;
    }
    try {
      state.prompt = await geminiBrowser(
        IMPROVE_PROMPT,
        `Aktueller Prompt:\n${state.prompt}\nSzene bleibt: ${state.selectedScene.opener}`,
        [],
        0.5,
      );
      renderPrompt();
      renderExport();
    } catch (err) {
      $("prompt-error").innerHTML = errorHtml(err.code || "GENERIC");
      bindErrorActions($("prompt-error"));
    } finally {
      $("prompt-load").classList.add("hidden");
    }
  };

  function exportPackage() {
    return `${t("promptNote")}\n\n${state.prompt}\n\nCAPTION:\n${$("caption").value}`.trim();
  }

  $("btn-copy-prompt").onclick = () => {
    if (native) AndroidBridge.copyText(state.prompt);
    else navigator.clipboard.writeText(state.prompt).then(() => {
      $("export-msg").innerHTML = `<div class="ok-box">${t("copied")}</div>`;
    });
  };
  $("btn-copy-all").onclick = () => {
    const pack = exportPackage();
    if (native) AndroidBridge.copyText(pack);
    else navigator.clipboard.writeText(pack).then(() => {
      $("export-msg").innerHTML = `<div class="ok-box">${t("copied")}</div>`;
    });
  };
  $("btn-save").onclick = () => {
    const first = state.images.find((i) => i.id === state.firstFrameId) || state.images[0];
    const scene = state.selectedScene || {};
    const payload = {
      id: uuid(),
      label: scene.environment || "UGC",
      analysisJson: JSON.stringify(state.analysis || {}),
      sceneJson: JSON.stringify(scene),
      prompt: state.prompt,
      caption: $("caption").value,
      firstFrameThumb: first ? first.thumb : "",
    };
    if (native) AndroidBridge.saveHistory(JSON.stringify(payload));
    else {
      const items = JSON.parse(localStorage.getItem("ugc_history") || "[]");
      items.unshift(payload);
      localStorage.setItem("ugc_history", JSON.stringify(items.slice(0, 50)));
    }
    $("export-msg").innerHTML = `<div class="ok-box">${t("saved")}</div>`;
  };

  $("btn-check").onclick = () => {
    if (native) AndroidBridge.checkCompliance(state.prompt || "", $("caption").value || "");
    else showCompliance(localCompliance(state.prompt || "", $("caption").value || ""));
  };

  function bootstrap() {
    if (native) {
      try {
        const boot = JSON.parse(AndroidBridge.getBootstrap());
        state.hasApiKey = !!boot.hasApiKey;
        state.maskedKey = boot.maskedKey || "";
        state.lang = boot.language === "ru" ? "ru" : "de";
      } catch (_) {}
    } else {
      state.lang = localStorage.getItem("ugc_lang") === "ru" ? "ru" : "de";
      const key = localStorage.getItem("ugc_api_key") || "";
      state.hasApiKey = !!key;
      state.maskedKey = key ? key.slice(0, 4) + "••••" + key.slice(-4) : "";
    }
    applyI18n();
    if (native) AndroidBridge.loadHistory();
  }

  bootstrap();
})();
