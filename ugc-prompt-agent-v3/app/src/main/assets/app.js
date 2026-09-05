(() => {
  const native = window.UgcNative;
  const STEPS = ["photos", "check", "analyse", "firstFrame", "scene", "prompt", "compliance", "export"];
  const state = {
    lang: "de",
    screen: "home",
    data: {},
    busy: false,
    history: [],
  };

  function t(key) {
    const pack = window.I18N[state.lang] || window.I18N.de;
    return pack[key] || window.I18N.de[key] || key;
  }

  function $(id) { return document.getElementById(id); }

  function toast(msg) {
    const el = $("toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    setTimeout(() => el.classList.add("hidden"), 2200);
  }

  function modal(title, body, actions) {
    $("modalTitle").textContent = title;
    $("modalBody").textContent = body;
    const box = $("modalActions");
    box.innerHTML = "";
    actions.forEach((a) => {
      const b = document.createElement("button");
      b.textContent = a.label;
      if (a.secondary) b.className = "secondary";
      b.onclick = () => { hideModal(); a.onClick && a.onClick(); };
      box.appendChild(b);
    });
    $("modal").classList.remove("hidden");
  }
  function hideModal() { $("modal").classList.add("hidden"); }

  function applyI18n() {
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      el.textContent = t(el.getAttribute("data-i18n"));
    });
  }

  function fmtBytes(n) {
    if (!n) return "0 B";
    if (n < 1024) return n + " B";
    if (n < 1048576) return (n / 1024).toFixed(1) + " KB";
    return (n / 1048576).toFixed(1) + " MB";
  }

  function call(name, ...args) {
    if (!native || typeof native[name] !== "function") {
      toast("Native bridge missing");
      return;
    }
    native[name](...args);
  }

  function ensurePrivacy(next) {
    if (state.data.settings && state.data.settings.privacyAccepted) {
      next();
      return;
    }
    const provider = (state.data.settings && state.data.settings.provider) || "OPENAI";
    modal(t("privacy"), provider, [
      { label: t("accept"), onClick: () => { call("saveSettings", JSON.stringify({ privacyAccepted: true })); next(); } },
      { label: t("ignore"), secondary: true },
    ]);
  }

  function render() {
    applyI18n();
    const pages = $("pages");
    const steps = $("steps");
    const homeNav = $("homeNav");
    const title = $("screenTitle");
    const showSteps = STEPS.includes(state.screen);
    steps.hidden = !showSteps;
    homeNav.style.display = ["home", "history", "settings"].includes(state.screen) ? "flex" : "none";
    if (showSteps) {
      steps.innerHTML = STEPS.map((s, i) => `<li class="${s === state.screen ? "on" : ""}">${i + 1}. ${t(stepKey(s))}</li>`).join("");
    }
    title.textContent = titleFor(state.screen);
    pages.className = state.busy ? "busy" : "";
    pages.innerHTML = htmlFor(state.screen);
    bind(state.screen);
  }

  function stepKey(s) {
    return ({ photos: "photos", check: "check", analyse: "analyse", firstFrame: "first_frame", scene: "scene", prompt: "prompt", compliance: "compliance", export: "export" })[s];
  }

  function titleFor(screen) {
    const map = {
      home: t("home_title"), history: t("history"), settings: t("settings"),
      photos: t("photos"), check: t("check"), analyse: t("analyse"), firstFrame: t("first_frame"),
      scene: t("scene"), prompt: t("prompt"), compliance: t("compliance"), export: t("export"),
    };
    return map[screen] || t("home_title");
  }

  function htmlFor(screen) {
    switch (screen) {
      case "home": return homeHtml();
      case "history": return historyHtml();
      case "settings": return settingsHtml();
      case "photos": return photosHtml();
      case "check": return checkHtml();
      case "analyse": return analyseHtml();
      case "firstFrame": return firstFrameHtml();
      case "scene": return sceneHtml();
      case "prompt": return promptHtml();
      case "compliance": return complianceHtml();
      case "export": return exportHtml();
      default: return homeHtml();
    }
  }

  function homeHtml() {
    return `<section class="card">
      <p>${t("app_eyebrow")}</p>
      <div class="row">
        <button id="goNew">${t("new_project")}</button>
        <button id="goHistory" class="secondary">${t("history")}</button>
      </div>
    </section>`;
  }

  function photosHtml() {
    const images = (state.data.images || []);
    const payload = state.data.payload || {};
    const enabled = !!state.data.analyseEnabled;
    return `<section class="card">
      <p>${enabled ? "" : t("analyse_need")}</p>
      <div class="thumbs">${images.map((img) => `
        <div class="thumb ${img.isFirstFrame ? "ff" : ""}" data-id="${img.id}">
          <img src="${img.thumb}" alt="" />
          <span class="badge">${img.isFirstFrame ? "FF" : ""} ${img.width}×${img.height}</span>
        </div>`).join("")}</div>
      <div class="kv"><span>${t("payload")}</span><span>${payload.imageCount || 0} · ${fmtBytes(payload.originalBytes)} → ${fmtBytes(payload.compressedBytes)}</span></div>
      ${payload.warning ? `<p class="warn">Payload groß — extra Kompression möglich.</p>` : ""}
      <div class="row">
        <button id="pick">${t("upload")}</button>
        <button id="clear" class="secondary">${t("clear")}</button>
      </div>
      <div class="row">
        <button id="toCheck" ${enabled ? "" : "disabled"}>${t("check")}</button>
      </div>
    </section>`;
  }

  function checkHtml() {
    const c = (state.data.project && state.data.project.consistency) || {};
    const warn = c.same_product === false || (typeof c.confidence === "number" && c.confidence < 0.8);
    const hasResult = c && Object.keys(c).length > 0;
    const override = !!(state.data.project && state.data.project.consistencyOverride);
    const canAnalyse = hasResult && (!warn || override);
    return `<section class="card">
      <pre>${JSON.stringify(c, null, 2)}</pre>
      ${warn ? `<p class="warn">${t("mismatch")}</p>` : ""}
      <div class="row">
        <button id="runCheck">${t("check")}</button>
        <button id="review" class="secondary">${t("review_photos")}</button>
        ${warn ? `<button id="anyway" class="secondary">${t("continue_anyway")}</button>` : ""}
        <button id="toAnalyse" ${canAnalyse ? "" : "disabled"}>${t("analyse")}</button>
      </div>
    </section>`;
  }

  function analyseHtml() {
    const a = (state.data.project && state.data.project.analysis) || {};
    return `<section class="card">
      <pre>${JSON.stringify(a, null, 2)}</pre>
      <div class="row">
        <button id="runAnalyse">${t("analyse")}</button>
        <button id="toFf" class="secondary" ${a.observed_use_case ? "" : "disabled"}>${t("first_frame")}</button>
      </div>
    </section>`;
  }

  function firstFrameHtml() {
    const images = state.data.images || [];
    const q = (state.data.project && state.data.project.firstFrameQuality) || {};
    return `<section class="card">
      <div class="thumbs">${images.map((img) => `
        <div class="thumb ${img.isFirstFrame ? "ff" : ""}" data-pick="${img.id}">
          <img src="${img.thumb}" alt="" />
        </div>`).join("")}</div>
      <pre>${JSON.stringify(q, null, 2)}</pre>
      <div class="row">
        <button id="quality">${t("check")}</button>
        <button id="confirmFf">${t("confirm_ff")}</button>
      </div>
    </section>`;
  }

  function sceneHtml() {
    const scene = (state.data.project && state.data.project.scene) || {};
    const speech = (state.data.project && state.data.project.speechLanguage) || "DEUTSCH";
    return `<section class="card">
      <label>${t("speech")}</label>
      <select id="speech">
        <option value="OFF" ${speech === "OFF" ? "selected" : ""}>${t("speech_off")}</option>
        <option value="DEUTSCH" ${speech === "DEUTSCH" ? "selected" : ""}>${t("speech_de")}</option>
        <option value="РУССКИЙ" ${speech === "РУССКИЙ" ? "selected" : ""}>${t("speech_ru")}</option>
      </select>
      <pre>${JSON.stringify(scene, null, 2)}</pre>
      <div class="row">
        <button id="genScene">${t("generate_scene")}</button>
        <button id="newScene" class="secondary">${t("new_scene")}</button>
        <button id="toPrompt" ${scene.main_action ? "" : "disabled"}>${t("prompt")}</button>
      </div>
    </section>`;
  }

  function promptHtml() {
    const p = state.data.activePrompt || "";
    return `<section class="card">
      <textarea id="promptBox" readonly>${escapeHtml(p)}</textarea>
      <div class="row">
        <button id="genPrompt">${t("generate_prompt")}</button>
        <button id="improve" class="secondary">${t("improve")}</button>
        <button id="newSpeech" class="secondary">${t("new_speech")}</button>
        <button id="toComp" ${p ? "" : "disabled"}>${t("compliance")}</button>
      </div>
    </section>`;
  }

  function complianceHtml() {
    const c = (state.data.project && state.data.project.compliance) || {};
    const blocked = c.status === "BLOCK";
    return `<section class="card">
      <p class="${c.status === "PASS" ? "ok" : c.status === "BLOCK" ? "err" : "warn"}">${c.status || "-"} · ${c.policy_version || ""}</p>
      <pre>${JSON.stringify(c, null, 2)}</pre>
      <div class="row">
        <button id="runComp">${t("run_compliance")}</button>
        <button id="addW" class="secondary">${t("add_werbung")}</button>
        <button id="ign" class="secondary">${t("ignore")}</button>
        <button id="toExport" ${blocked ? "disabled" : ""}>${t("export")}</button>
      </div>
    </section>`;
  }

  function exportHtml() {
    const p = state.data.project || {};
    const prompt = state.data.activePrompt || "";
    const ff = (state.data.images || []).find((i) => i.isFirstFrame);
    return `<section class="card">
      ${ff ? `<img src="${ff.thumb}" alt="" style="width:100%;border-radius:12px;margin-bottom:12px" />` : ""}
      <h3>${t("prompt")}</h3><pre>${escapeHtml(prompt)}</pre>
      <h3>${t("caption")}</h3><pre>${escapeHtml(p.caption || "")}</pre>
      <h3>${t("hashtags")}</h3><pre>${(p.hashtags || []).join(" ")}</pre>
      <pre>${JSON.stringify(p.compliance || {}, null, 2)}</pre>
      <div class="row">
        <button id="cap">${t("regenerate_caption")}</button>
        <button id="cp" class="secondary">${t("copy_prompt")}</button>
        <button id="cc" class="secondary">${t("copy_caption")}</button>
        <button id="ch" class="secondary">${t("copy_hashtags")}</button>
        <button id="ca" class="secondary">${t("copy_all")}</button>
        <button id="sff" class="secondary">${t("share_ff")}</button>
        <button id="save" class="secondary">${t("save_project")}</button>
        <button id="share" class="secondary">${t("share_project")}</button>
      </div>
    </section>`;
  }

  function historyHtml() {
    const items = state.history || [];
    if (!items.length) return `<section class="card"><p>—</p></section>`;
    return `<section class="card list">${items.map((it) => `
      <div class="card">
        <strong>${it.useCase || it.id}</strong>
        <p>${it.imageCount} · ${it.provider} · ${it.targetGenerator}</p>
        <div class="row">
          <button data-open="${it.id}">${t("open")}</button>
          <button class="secondary" data-dup="${it.id}">${t("duplicate")}</button>
          <button class="danger" data-del="${it.id}">${t("delete")}</button>
        </div>
      </div>`).join("")}</section>`;
  }

  function settingsHtml() {
    const s = state.data.settings || {};
    const st = state.data.providerStatus || {};
    return `<section class="card">
      <label>${t("nav_settings")}</label>
      <select id="appLang">
        <option value="de" ${s.appLanguage === "de" ? "selected" : ""}>Deutsch</option>
        <option value="ru" ${s.appLanguage === "ru" ? "selected" : ""}>Русский</option>
      </select>
      <label>${t("provider")}</label>
      <select id="provider">
        <option value="OPENAI" ${s.provider === "OPENAI" ? "selected" : ""}>OpenAI</option>
        <option value="GEMINI" ${s.provider === "GEMINI" ? "selected" : ""}>Gemini</option>
      </select>
      <label>${t("generator")}</label>
      <select id="gen">
        <option value="VEO">VEO</option>
        <option value="KLING" ${s.targetGenerator === "KLING" ? "selected" : ""}>KLING</option>
        <option value="GENERIC" ${s.targetGenerator === "GENERIC" ? "selected" : ""}>GENERIC</option>
      </select>
      <label>${t("speech")}</label>
      <select id="speechSet">
        <option value="OFF" ${s.speechLanguage === "OFF" ? "selected" : ""}>${t("speech_off")}</option>
        <option value="DEUTSCH" ${s.speechLanguage !== "OFF" && s.speechLanguage !== "РУССКИЙ" ? "selected" : ""}>${t("speech_de")}</option>
        <option value="РУССКИЙ" ${s.speechLanguage === "РУССКИЙ" ? "selected" : ""}>${t("speech_ru")}</option>
      </select>
      <label>${t("caption_lang")}</label>
      <select id="capLang">
        <option value="DEUTSCH" ${s.captionLanguage !== "РУССКИЙ" ? "selected" : ""}>${t("speech_de")}</option>
        <option value="РУССКИЙ" ${s.captionLanguage === "РУССКИЙ" ? "selected" : ""}>${t("speech_ru")}</option>
      </select>
      <label>${t("lock")}</label>
      <select id="lock">
        <option value="true" ${s.strictProductLock !== false ? "selected" : ""}>ON</option>
        <option value="false" ${s.strictProductLock === false ? "selected" : ""}>OFF</option>
      </select>
      <p>Compliance ${s.policyVersion || ""} · ${s.policyUpdated || ""}</p>
    </section>
    ${providerCard("OPENAI", st.OPENAI)}
    ${providerCard("GEMINI", st.GEMINI)}`;
  }

  function providerCard(name, status) {
    const st = status || {};
    return `<section class="card">
      <h3>${name}</h3>
      <p>${st.status || t("not_configured")}</p>
      <label>${t("api_key")}</label>
      <input id="key-${name}" type="password" autocomplete="off" />
      <div class="row">
        <button data-save="${name}">${t("save_key")}</button>
        <button class="secondary" data-delkey="${name}">${t("delete_key")}</button>
        <button class="secondary" data-test="${name}">${t("test")}</button>
      </div>
    </section>`;
  }

  function bind(screen) {
    $("btnSettings").onclick = () => show("settings");
    document.querySelectorAll(".bottom-nav button").forEach((b) => {
      b.classList.toggle("active", b.getAttribute("data-nav") === screen);
      b.onclick = () => {
        const nav = b.getAttribute("data-nav");
        if (nav === "history") call("listHistory");
        show(nav);
      };
    });
    if (screen === "home") {
      $("goNew").onclick = () => { call("newProject"); show("photos"); };
      $("goHistory").onclick = () => { call("listHistory"); show("history"); };
    }
    if (screen === "photos") {
      $("pick").onclick = () => call("startPickImages");
      $("clear").onclick = () => call("clearImages");
      $("toCheck").onclick = () => { ensurePrivacy(() => { call("runConsistency"); show("check"); }); };
      document.querySelectorAll(".thumb[data-id]").forEach((el) => {
        el.onclick = () => call("setFirstFrame", el.getAttribute("data-id"));
        el.ondblclick = () => call("removeImage", el.getAttribute("data-id"));
      });
    }
    if (screen === "check") {
      $("runCheck").onclick = () => ensurePrivacy(() => call("runConsistency"));
      $("review").onclick = () => show("photos");
      const anyway = $("anyway"); if (anyway) anyway.onclick = () => call("continueAnyway");
      $("toAnalyse").onclick = () => { call("runAnalysis"); show("analyse"); };
    }
    if (screen === "analyse") {
      $("runAnalyse").onclick = () => call("runAnalysis");
      $("toFf").onclick = () => show("firstFrame");
    }
    if (screen === "firstFrame") {
      document.querySelectorAll("[data-pick]").forEach((el) => {
        el.onclick = () => call("setFirstFrame", el.getAttribute("data-pick"));
      });
      $("quality").onclick = () => call("runFirstFrameQuality");
      $("confirmFf").onclick = () => show("scene");
    }
    if (screen === "scene") {
      $("speech").onchange = (e) => call("saveSettings", JSON.stringify({ speechLanguage: e.target.value }));
      $("genScene").onclick = () => call("generateScene");
      $("newScene").onclick = () => call("newScene");
      $("toPrompt").onclick = () => { call("generatePrompt"); show("prompt"); };
    }
    if (screen === "prompt") {
      $("genPrompt").onclick = () => call("generatePrompt");
      $("improve").onclick = () => call("improvePrompt");
      $("newSpeech").onclick = () => call("newSpeech");
      $("toComp").onclick = () => { call("generateCaption"); call("runCompliance"); show("compliance"); };
    }
    if (screen === "compliance") {
      $("runComp").onclick = () => call("runCompliance");
      $("addW").onclick = () => call("addWerbung");
      $("ign").onclick = () => call("ignoreDisclosure");
      $("toExport").onclick = () => show("export");
    }
    if (screen === "export") {
      const p = state.data.project || {};
      const prompt = state.data.activePrompt || "";
      $("cap").onclick = () => call("regenerateCaption");
      $("cp").onclick = () => call("copyText", prompt);
      $("cc").onclick = () => call("copyText", p.caption || "");
      $("ch").onclick = () => call("copyText", (p.hashtags || []).join(" "));
      $("ca").onclick = () => call("copyText", [prompt, p.caption || "", (p.hashtags || []).join(" ")].join("\n\n"));
      $("sff").onclick = () => call("shareFirstFrame");
      $("save").onclick = () => call("saveProjectNow");
      $("share").onclick = () => call("shareProject");
    }
    if (screen === "history") {
      document.querySelectorAll("[data-open]").forEach((b) => b.onclick = () => { call("openProject", b.getAttribute("data-open")); show("export"); });
      document.querySelectorAll("[data-dup]").forEach((b) => b.onclick = () => call("duplicateProject", b.getAttribute("data-dup")));
      document.querySelectorAll("[data-del]").forEach((b) => b.onclick = () => {
        modal(t("confirm_delete"), "", [
          { label: t("delete"), onClick: () => call("deleteProject", b.getAttribute("data-del")) },
          { label: t("ignore"), secondary: true },
        ]);
      });
    }
    if (screen === "settings") {
      const save = () => call("saveSettings", JSON.stringify({
        appLanguage: $("appLang").value,
        provider: $("provider").value,
        targetGenerator: $("gen").value,
        speechLanguage: $("speechSet").value,
        captionLanguage: $("capLang").value,
        strictProductLock: $("lock").value === "true",
      }));
      ["appLang", "provider", "gen", "speechSet", "capLang", "lock"].forEach((id) => {
        $(id).onchange = () => {
          if (id === "appLang") state.lang = $("appLang").value;
          save();
        };
      });
      document.querySelectorAll("[data-save]").forEach((b) => b.onclick = () => {
        const name = b.getAttribute("data-save");
        const input = $("key-" + name);
        call("saveProviderKey", name, input.value);
        input.value = "";
      });
      document.querySelectorAll("[data-delkey]").forEach((b) => b.onclick = () => call("deleteProviderKey", b.getAttribute("data-delkey")));
      document.querySelectorAll("[data-test]").forEach((b) => b.onclick = () => call("testConnection", b.getAttribute("data-test")));
    }
  }

  function show(screen) {
    state.screen = screen;
    render();
  }

  function escapeHtml(s) {
    return String(s || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  window.UgcV3App = {
    onNativeEvent(event, payload) {
      if (payload && payload.settings) state.data = Object.assign(state.data, payload);
      if (event === "ready" || event === "project" || event === "images" || event === "settings" ||
          event === "consistency" || event === "analysis" || event === "firstFrame" || event === "firstFrameQuality" ||
          event === "scene" || event === "prompt" || event === "caption" || event === "compliance" || event === "saved") {
        state.data = Object.assign(state.data, payload);
        if (payload.settings && payload.settings.appLanguage) state.lang = payload.settings.appLanguage;
        render();
      }
      if (event === "history") {
        state.history = payload.items || [];
        render();
      }
      if (event === "busy") {
        state.busy = !!payload.busy;
        render();
      }
      if (event === "copied") toast(t("copied"));
      if (event === "error") toast(payload.message || payload.code || "error");
      if (event === "providerStatus") {
        state.data.providerStatus = payload;
        if (state.screen === "settings") render();
      }
    }
  };

  document.addEventListener("DOMContentLoaded", () => {
    render();
    call("ready");
  });
})();
