// Generic page-level media sniffer.
// It hooks fetch/XHR created by the page and scans video/audio/source/performance entries.
// This is not full Chrome DevTools capture; it only sees what page JavaScript and WebView expose.
(function () {
  const CONFIG = {
    panel: true,
    maxItems: 80,
    mediaRegex: /\.(m3u8|mp4|mkv|flv|mov|avi|webm|mp3|aac|m4a)(\?|#|$)/i,
    apiHintRegex: /play|source|m3u8|url|video|episode/i
  };

  const found = new Map();

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-sniffer]"].concat(args));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => window.addEventListener("fmsdk", () => resolve(window.fm), { once: true }));
  }

  function add(url, source, detail) {
    if (!url || !/^https?:/i.test(url)) return;
    if (!CONFIG.mediaRegex.test(url) && !CONFIG.apiHintRegex.test(url)) return;
    if (found.has(url)) return;
    found.set(url, { url, source, detail: detail || "", time: Date.now() });
    while (found.size > CONFIG.maxItems) found.delete(found.keys().next().value);
    log("found", source, url);
    render();
  }

  function hookFetch() {
    if (!window.fetch || window.__fmSnifferFetch) return;
    window.__fmSnifferFetch = true;
    const rawFetch = window.fetch;
    window.fetch = function () {
      const input = arguments[0];
      const url = typeof input === "string" ? input : input && input.url;
      if (url) add(resolveUrl(url), "fetch");
      return rawFetch.apply(this, arguments).then((response) => {
        try {
          add(response.url, "fetch-response", String(response.status));
          const type = response.headers && response.headers.get && response.headers.get("content-type") || "";
          if (/json|text|javascript/i.test(type) && CONFIG.apiHintRegex.test(response.url || "")) {
            response.clone().text().then((text) => scanText(text, response.url)).catch(() => {});
          }
        } catch (e) {
          // ignore
        }
        return response;
      });
    };
  }

  function hookXhr() {
    if (!window.XMLHttpRequest || window.__fmSnifferXhr) return;
    window.__fmSnifferXhr = true;
    const proto = XMLHttpRequest.prototype;
    const rawOpen = proto.open;
    const rawSend = proto.send;
    proto.open = function (method, url) {
      this.__fmUrl = resolveUrl(url || "");
      add(this.__fmUrl, "xhr-open", method || "GET");
      return rawOpen.apply(this, arguments);
    };
    proto.send = function () {
      this.addEventListener("load", function () {
        try {
          add(this.responseURL || this.__fmUrl, "xhr-response", String(this.status));
          const type = this.getResponseHeader("content-type") || "";
          if (/json|text|javascript/i.test(type) && typeof this.responseText === "string") {
            scanText(this.responseText, this.responseURL || this.__fmUrl);
          }
        } catch (e) {
          // ignore
        }
      });
      return rawSend.apply(this, arguments);
    };
  }

  function resolveUrl(url) {
    if (!url) return "";
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return url;
    }
  }

  function scanText(text, sourceUrl) {
    if (!text || text.length > 2000000) return;
    const regex = /https?:\\?\/\\?\/[^"'\\\s<>]+/g;
    let match;
    while ((match = regex.exec(text))) {
      const url = match[0].replace(/\\\//g, "/");
      add(url, "body", sourceUrl);
    }
  }

  function scanDom() {
    document.querySelectorAll("video,audio,source").forEach((el) => {
      add(resolveUrl(el.currentSrc || el.src || el.getAttribute("src") || ""), el.tagName.toLowerCase());
    });
    if (performance && performance.getEntriesByType) {
      performance.getEntriesByType("resource").forEach((entry) => add(entry.name, "performance", entry.initiatorType));
    }
  }

  function title() {
    const el = document.querySelector("h1,h2,.title,.name");
    return (el && el.textContent ? el.textContent.trim() : "") || document.title || location.href;
  }

  function render() {
    if (!CONFIG.panel) return;
    let panel = document.getElementById("fm-sniffer-panel");
    if (!panel) {
      panel = document.createElement("div");
      panel.id = "fm-sniffer-panel";
      panel.innerHTML = "<div class='fm-sniffer-head'>媒体嗅探 <button type='button' data-fm-close>×</button></div><div class='fm-sniffer-list'></div>";
      document.documentElement.appendChild(panel);
      panel.addEventListener("click", onPanelClick, true);
      addStyle();
    }
    const list = panel.querySelector(".fm-sniffer-list");
    list.innerHTML = Array.from(found.values()).reverse().map((item, index) => {
      return `<button type="button" data-url="${escapeHtml(item.url)}" title="${escapeHtml(item.url)}">${index + 1}. ${escapeHtml(item.source)} ${escapeHtml(shortUrl(item.url))}</button>`;
    }).join("");
  }

  function onPanelClick(event) {
    const close = event.target.closest("[data-fm-close]");
    if (close) {
      event.preventDefault();
      const panel = document.getElementById("fm-sniffer-panel");
      if (panel) panel.remove();
      CONFIG.panel = false;
      return;
    }
    const button = event.target.closest("[data-url]");
    if (!button) return;
    event.preventDefault();
    const url = button.dataset.url;
    whenFm().then((sdk) => sdk.play(url, title(), mediaPlayOptions(url)));
  }

  function mediaPlayOptions(url) {
    if (/\.m3u8(\?|#|$)/i.test(String(url || ""))) return { format: "application/x-mpegURL" };
    if (/\.mpd(\?|#|$)/i.test(String(url || ""))) return { format: "application/dash+xml" };
    return { headers: { Referer: location.href }, credentials: "include" };
  }

  function shortUrl(url) {
    return url.length > 90 ? url.slice(0, 45) + "..." + url.slice(-35) : url;
  }

  function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c]));
  }

  function addStyle() {
    if (document.getElementById("fm-sniffer-style")) return;
    const css = `
      #fm-sniffer-panel {
        position: fixed;
        left: 8px;
        right: 8px;
        bottom: 8px;
        z-index: 2147483647;
        max-height: 42vh;
        background: rgba(17,24,39,.94);
        color: #fff;
        border: 1px solid rgba(255,255,255,.18);
        border-radius: 8px;
        overflow: auto;
        font: 13px/1.4 system-ui, sans-serif;
      }
      #fm-sniffer-panel .fm-sniffer-head {
        position: sticky;
        top: 0;
        display: flex;
        justify-content: space-between;
        padding: 8px;
        background: #111827;
        font-weight: 700;
      }
      #fm-sniffer-panel button {
        display: block;
        width: 100%;
        padding: 8px;
        border: 0;
        border-top: 1px solid rgba(255,255,255,.1);
        background: transparent;
        color: #fff;
        text-align: left;
        white-space: nowrap;
      }
      #fm-sniffer-panel [data-fm-close] {
        width: auto;
        padding: 0 8px;
        border: 0;
      }
      #fm-sniffer-panel button:focus {
        outline: 2px solid rgba(255,255,255,.85);
        outline-offset: -2px;
        background: rgba(255,255,255,.12);
      }
    `;
    const style = document.createElement("style");
    style.id = "fm-sniffer-style";
    style.textContent = css;
    document.documentElement.appendChild(style);
  }

  hookFetch();
  hookXhr();
  scanDom();
  setInterval(scanDom, 2500);
  window.addEventListener("fmurlchange", () => setTimeout(scanDom, 500));
  log("ready", location.href);
})();
