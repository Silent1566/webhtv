// Generic pan-link enhancer.
// Scans visible page links, injects native play buttons, and optionally checks pan availability.
(function () {
  const CONFIG = {
    linkSelector: "a[href],[data-url],[data-href],[data-link],[data-clipboard-text]",
    itemSelector: ".item,.card,.download-item,.resource-item,li,article",
    titleSelector: "h1,h2,.title,.name",
    buttonClass: "fm-pan-play",
    statusClass: "fm-pan-status",
    enableCheck: true,
    checkBatchSize: 10,
    scanDelay: 160
  };

  const TYPES = [
    ["quark", /pan\.quark\.cn/i],
    ["aliyun", /aliyundrive\.com|alipan\.com/i],
    ["baidu", /pan\.baidu\.com/i],
    ["uc", /drive\.uc\.cn/i],
    ["xunlei", /pan\.xunlei\.com/i],
    ["tianyi", /cloud\.189\.cn/i],
    ["123", /123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i],
    ["115", /115\.com|115cdn\.com/i],
    ["mobile", /yun\.139\.com|caiyun\.139\.com/i]
  ];

  const checked = new Set();

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-pan]"].concat(args));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => window.addEventListener("fmsdk", () => resolve(window.fm), { once: true }));
  }

  function ready(fn) {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fn, { once: true });
    else fn();
  }

  function getUrl(el) {
    const keys = ["data-url", "data-href", "data-link", "data-clipboard-text", "href"];
    for (const key of keys) {
      const value = (el.getAttribute(key) || "").trim();
      if (!value || value === "#" || /^javascript:/i.test(value)) continue;
      try {
        return /^https?:/i.test(value) ? value : new URL(value, location.href).href;
      } catch (e) {
        return value;
      }
    }
    return "";
  }

  function typeOf(url) {
    for (const item of TYPES) if (item[1].test(url)) return item[0];
    return "";
  }

  function pageTitle() {
    const el = document.querySelector(CONFIG.titleSelector);
    return (el && el.textContent ? el.textContent.trim() : "") || document.title || location.href;
  }

  function itemTitle(el) {
    const item = el.closest(CONFIG.itemSelector);
    const titleEl = item && item.querySelector("[title],.title,.name,h1,h2,h3");
    return (titleEl && (titleEl.getAttribute("title") || titleEl.textContent || "").trim()) || (el.textContent || "").trim() || pageTitle();
  }

  function scan() {
    document.querySelectorAll(CONFIG.linkSelector).forEach((link) => {
      if (link.dataset.fmPanReady === "1") return;
      const url = getUrl(link);
      const type = typeOf(url);
      if (!type) return;
      link.dataset.fmPanReady = "1";
      link.dataset.fmPanUrl = url;
      link.dataset.fmPanType = type;
      injectButton(link, url, type);
    });
    checkVisible();
  }

  function injectButton(link, url, type) {
    const parent = link.closest(CONFIG.itemSelector) || link.parentElement;
    if (!parent || hasButton(parent, url)) return;
    const button = document.createElement("button");
    button.type = "button";
    button.className = CONFIG.buttonClass;
    button.textContent = "App播放";
    button.dataset.url = url;
    button.dataset.type = type;
    parent.appendChild(button);

    const status = document.createElement("span");
    status.className = CONFIG.statusClass;
    status.dataset.url = url;
    status.textContent = "";
    parent.appendChild(status);
  }

  function hasButton(parent, url) {
    const buttons = parent.querySelectorAll("." + CONFIG.buttonClass);
    for (let i = 0; i < buttons.length; i++) {
      if (buttons[i].dataset.url === url) return true;
    }
    return false;
  }

  document.addEventListener("click", function (event) {
    const button = event.target.closest("." + CONFIG.buttonClass);
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const url = button.dataset.url || "";
    const type = button.dataset.type || typeOf(url) || "http";
    whenFm()
      .then((sdk) => sdk.pan.play({ type, url, title: itemTitle(button) }))
      .catch((error) => {
        log("play error", error && (error.stack || error.message) || error);
        if (window.fm && fm.ext) fm.ext.toast("调用网盘播放失败");
      });
  }, true);

  async function checkVisible() {
    if (!CONFIG.enableCheck || !window.fm) return;
    let config;
    try {
      config = await fm.config();
    } catch (e) {
      return;
    }
    if (!config || config.driveCheck === false) return;

    const items = [];
    document.querySelectorAll("[data-fm-pan-url]").forEach((el) => {
      const url = el.dataset.fmPanUrl;
      const type = el.dataset.fmPanType;
      if (!url || !type || checked.has(type + "|" + url) || !isVisible(el)) return;
      checked.add(type + "|" + url);
      items.push({ type, url, password: "" });
    });
    if (!items.length) return;

    for (let i = 0; i < items.length; i += CONFIG.checkBatchSize) {
      const batch = items.slice(i, i + CONFIG.checkBatchSize);
      try {
        const result = await fm.pan.check(batch);
        updateStatus(result && result.results || []);
      } catch (error) {
        log("check error", error && (error.message || error));
      }
    }
  }

  function updateStatus(results) {
    results.forEach((item) => {
      document.querySelectorAll("." + CONFIG.statusClass).forEach((el) => {
        if (el.dataset.url !== item.url && el.dataset.url !== item.normalized_url) return;
        el.textContent = statusText(item.state);
        el.dataset.state = item.state || "";
        el.title = item.summary || "";
      });
    });
  }

  function statusText(state) {
    if (state === "ok") return "有效";
    if (state === "bad") return "失效";
    if (state === "locked") return "需码";
    if (state === "unsupported") return "";
    if (state === "uncertain") return "未知";
    return "";
  }

  function isVisible(el) {
    const rect = el.getBoundingClientRect();
    return rect.bottom >= 0 && rect.top <= (innerHeight || document.documentElement.clientHeight);
  }

  function scheduleScan() {
    clearTimeout(scheduleScan.timer);
    scheduleScan.timer = setTimeout(scan, CONFIG.scanDelay);
  }

  function installStyle() {
    const css = `
      .${CONFIG.buttonClass} {
        margin-left: 8px;
        padding: 4px 10px;
        border: 1px solid #0f766e;
        border-radius: 6px;
        background: #0f766e;
        color: #fff;
        font-size: 13px;
      }
      .${CONFIG.buttonClass}:focus {
        outline: 2px solid rgba(255,255,255,.85);
        outline-offset: 1px;
        background: #0d9488;
      }
      .${CONFIG.statusClass} {
        margin-left: 6px;
        font-size: 12px;
        color: #0f766e;
      }
      .${CONFIG.statusClass}[data-state='bad'] {
        color: #b91c1c;
      }
      .${CONFIG.statusClass}[data-state='locked'] {
        color: #a16207;
      }
    `;
    if (typeof GM_addStyle === "function") GM_addStyle(css);
  }

  ready(() => {
    installStyle();
    scan();
    new MutationObserver(scheduleScan).observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener("scroll", scheduleScan, { passive: true });
    window.addEventListener("fmurlchange", scheduleScan);
    log("ready", location.href);
  });
})();
