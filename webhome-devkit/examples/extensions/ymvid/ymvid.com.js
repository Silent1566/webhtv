// WebHome extension for https://www.ymvid.com/
// Enhancements:
// - Adds a native App play panel on play pages.
// - Resolves the current m3u8 from the site's own decrypted player data.
// - Removes first-view popups and player ad layers.
// - Improves mobile layout and TV remote focus for cards and episode links.
(function () {
  const CONFIG = {
    rootClass: "fm-ymvid-enhanced",
    tvClass: "fm-ymvid-tv",
    panelId: "fm-ymvid-panel",
    episodeId: "fm-ymvid-episodes",
    mediaEvent: "fm-ymvid-media",
    mediaRequestEvent: "fm-ymvid-request-media",
    primaryClass: "fm-ymvid-primary",
    statusClass: "fm-ymvid-status",
    focusClass: "fm-ymvid-focus",
    scanDelay: 140,
    focusSelector: [
      "a[href]",
      "button",
      "input",
      "select",
      "textarea",
      "[role='button']",
      ".grid-content",
      ".feature-post-box",
      ".swiper-slide",
      ".item-row",
      ".aside-body li",
      ".play-list .item a",
      "#fm-ymvid-episodes a",
      ".tabs-nav-link"
    ].join(",")
  };

  const state = {
    scanTimer: 0,
    focusRaf: 0,
    inputEditing: false,
    lastPath: "",
    lastMediaUrl: "",
    lastEpisodeKey: "",
    nativeBusy: false,
    artplayerWrapped: false,
    hlsWrapped: false,
    pageBridgeInjected: false,
    mediaResolvers: {}
  };

  installArtplayerHook();
  installHlsHook();
  installPageBridge();
  window.__fmYmvidResolveEpisode = resolveInlineEpisode;
  window.__fmWebHomeInlineResolver = resolveInlineEpisode;
  injectBaseStyle();
  ready(init);

  window.addEventListener(CONFIG.mediaEvent, handleMediaEvent, false);
  window.addEventListener("fmurlchange", scheduleScan);
  window.addEventListener("popstate", scheduleScan);
  window.addEventListener("focusin", handleFocusIn, true);
  window.addEventListener("focusout", handleFocusOut, true);

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-ymvid]"].concat(args));
  }

  function ready(fn) {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fn, { once: true });
    else fn();
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => window.addEventListener("fmsdk", () => resolve(window.fm), { once: true }));
  }

  function isTv() {
    return !!(window.fongmiClient && window.fongmiClient.isLeanback);
  }

  function cleanText(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function absoluteUrl(url) {
    const value = cleanText(url);
    if (!value || value === "#" || /^javascript:/i.test(value)) return "";
    try {
      return new URL(value, location.href).href;
    } catch (e) {
      return value;
    }
  }

  function init() {
    document.body.classList.add(CONFIG.rootClass);
    if (isTv()) document.body.classList.add(CONFIG.tvClass);
    observeDom();
    scheduleScan();
  }

  function scheduleScan() {
    clearTimeout(state.scanTimer);
    state.scanTimer = setTimeout(enhancePage, CONFIG.scanDelay);
  }

  function enhancePage() {
    if (!document.body) return;
    document.body.classList.add(CONFIG.rootClass);
    document.body.classList.toggle(CONFIG.tvClass, isTv());
    if (location.pathname !== state.lastPath) {
      state.lastPath = location.pathname;
      state.lastMediaUrl = "";
      state.lastEpisodeKey = "";
    }
    resetMediaCacheIfEpisodeChanged();
    clearPopups();
    enhanceCards();
    enhanceEpisodeList();
    enhancePlayPage();
  }

  function observeDom() {
    const observer = new MutationObserver(() => {
      clearPopups();
      scheduleScan();
    });
    observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
  }

  function injectBaseStyle() {
    const css = `
body.${CONFIG.rootClass} .first-pop-layer,
body.${CONFIG.rootClass} .detail-ads-img,
body.${CONFIG.rootClass} .ads-view-btn,
body.${CONFIG.rootClass} .side_toolbar_code {
  display: none !important;
}
body.${CONFIG.rootClass} .fm-ymvid-hidden {
  display: none !important;
}
body.${CONFIG.rootClass} #${CONFIG.panelId} {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  background: #f7f8fa;
  color: #1f2937;
}
body.${CONFIG.rootClass} #${CONFIG.panelId} button {
  border: 0;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
  line-height: 38px;
  white-space: nowrap;
}
body.${CONFIG.rootClass} .${CONFIG.primaryClass} {
  background: #16a34a;
  color: #fff;
}
body.${CONFIG.rootClass} .fm-ymvid-secondary {
  background: #e5e7eb;
  color: #111827;
}
body.${CONFIG.rootClass} .${CONFIG.statusClass} {
  flex: 1;
  min-width: 0;
  color: #4b5563;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} {
  margin-top: 10px;
  padding: 12px;
  background: #fff;
  color: #111827;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 800;
  line-height: 22px;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-current {
  color: #64748b;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(52px, 1fr));
  gap: 8px;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-grid a {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 40px;
  padding: 0 8px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #111827;
  font-size: 14px;
  font-weight: 800;
  line-height: 40px;
  text-align: center;
  white-space: nowrap;
}
body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-grid a.fm-ymvid-active {
  background: #16a34a;
  color: #fff;
}
body.${CONFIG.rootClass} .fm-ymvid-quick {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  min-height: 30px;
  margin-left: 8px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: #16a34a;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
body.${CONFIG.tvClass} .${CONFIG.focusClass},
body.${CONFIG.tvClass} a:focus-visible,
body.${CONFIG.tvClass} button:focus-visible,
body.${CONFIG.tvClass} input:focus-visible {
  outline: 3px solid #f59e0b !important;
  outline-offset: 3px !important;
}
body.${CONFIG.tvClass} .grid-content.${CONFIG.focusClass},
body.${CONFIG.tvClass} .feature-post-box.${CONFIG.focusClass},
body.${CONFIG.tvClass} .item-row.${CONFIG.focusClass},
body.${CONFIG.tvClass} .aside-body li.${CONFIG.focusClass} {
  transform: translateY(-2px);
}
body.${CONFIG.tvClass} #${CONFIG.panelId} button {
  min-height: 48px;
  padding: 0 20px;
  font-size: 16px;
}
@media (max-width: 760px) {
  body.${CONFIG.rootClass} {
    background: #f7f8fa !important;
  }
  body.${CONFIG.rootClass} .header {
    position: sticky;
    top: 0;
    z-index: 20;
  }
  body.${CONFIG.rootClass} #main {
    padding-bottom: 60px;
  }
  body.${CONFIG.rootClass} .side-toolbar,
  body.${CONFIG.rootClass} .comment-section,
  body.${CONFIG.rootClass} #footer,
  body.${CONFIG.rootClass} .toolbar,
  body.${CONFIG.rootClass} .search-play-aside {
    display: none !important;
  }
  body.${CONFIG.rootClass} .section-content,
  body.${CONFIG.rootClass} .section-container,
  body.${CONFIG.rootClass} .section-main,
  body.${CONFIG.rootClass} .main-left,
  body.${CONFIG.rootClass} .play-aside {
    width: 100% !important;
    max-width: none !important;
    margin-left: 0 !important;
    margin-right: 0 !important;
  }
  body.${CONFIG.rootClass} .player-section {
    padding: 0 !important;
    margin: 0 !important;
    background: #0f172a !important;
  }
  body.${CONFIG.rootClass} #player {
    width: 100% !important;
    aspect-ratio: 16 / 9;
    min-height: 210px;
    background: #0f172a !important;
  }
  body.${CONFIG.rootClass} #${CONFIG.panelId} {
    margin: 0;
    padding: 10px 12px 12px;
    background: #fff;
  }
  body.${CONFIG.rootClass} #${CONFIG.panelId} button {
    min-height: 40px;
    border-radius: 8px;
    line-height: 40px;
  }
  body.${CONFIG.rootClass} article.section-container {
    display: grid !important;
    grid-template-columns: 92px 1fr;
    column-gap: 12px;
    row-gap: 8px;
    padding: 14px 12px !important;
    background: #fff !important;
  }
  body.${CONFIG.rootClass} article.section-container .media-thumb {
    width: 92px !important;
    height: 128px !important;
    margin: 0 !important;
  }
  body.${CONFIG.rootClass} article.section-container .media-thumb img {
    width: 100% !important;
    height: 100% !important;
    object-fit: cover;
    border-radius: 8px;
  }
  body.${CONFIG.rootClass} article.section-container .media-info {
    min-width: 0;
    padding: 0 !important;
  }
  body.${CONFIG.rootClass} article.section-container .media-info h1 {
    margin: 0 0 6px !important;
    font-size: 18px !important;
    line-height: 1.25 !important;
    color: #111827 !important;
  }
  body.${CONFIG.rootClass} article.section-container .type-row,
  body.${CONFIG.rootClass} article.section-container .play-count-row {
    display: inline-flex !important;
    align-items: center;
    margin: 0 6px 6px 0 !important;
    padding: 2px 7px !important;
    border-radius: 6px;
    background: #eef2f7;
    color: #374151 !important;
    font-size: 12px !important;
    line-height: 20px !important;
  }
  body.${CONFIG.rootClass} article.section-container .intro-row {
    grid-column: 1 / -1;
    margin: 2px 0 0 !important;
    color: #4b5563 !important;
  }
  body.${CONFIG.rootClass} article.section-container .intro-detail {
    max-height: none !important;
    margin: 0 !important;
    font-size: 13px !important;
    line-height: 1.65 !important;
  }
  body.${CONFIG.rootClass} .intro-mobile-mask,
  body.${CONFIG.rootClass} .intro-detail-container {
    display: none !important;
  }
  body.${CONFIG.rootClass} .play-aside {
    padding: 0 12px 12px !important;
    background: #fff !important;
  }
  body.${CONFIG.rootClass}.fm-ymvid-has-native-episodes .play-aside {
    display: none !important;
  }
  body.${CONFIG.rootClass} .play-aside .right-aside:first-child {
    margin: 0 !important;
    padding: 0 !important;
    background: transparent !important;
  }
  body.${CONFIG.rootClass} .play-aside .right-aside:not(:first-child) {
    display: none !important;
  }
  body.${CONFIG.rootClass} .play-aside .header-box {
    display: flex !important;
    align-items: center;
    justify-content: space-between;
    margin: 0 0 8px !important;
  }
  body.${CONFIG.rootClass} .play-aside .header-box .title {
    font-size: 15px !important;
    font-weight: 800 !important;
    color: #111827 !important;
  }
  body.${CONFIG.rootClass} #${CONFIG.episodeId} {
    margin: 0;
    padding: 12px;
  }
  body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-grid {
    grid-template-columns: repeat(auto-fill, minmax(46px, 1fr));
    gap: 8px;
  }
  body.${CONFIG.rootClass} #${CONFIG.episodeId} .fm-ymvid-episode-grid a {
    min-height: 42px;
    line-height: 42px;
  }
  body.${CONFIG.rootClass} .live-anime {
    display: none !important;
  }
  body.${CONFIG.rootClass} .swiper-section {
    display: none !important;
  }
  body.${CONFIG.rootClass} .section-title {
    padding: 0 12px !important;
    margin: 14px 0 8px !important;
  }
  body.${CONFIG.rootClass} .el-row {
    display: grid !important;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px 8px;
    width: 100% !important;
    max-width: none !important;
    margin: 0 !important;
    padding: 0 12px !important;
  }
  body.${CONFIG.rootClass} .el-col,
  body.${CONFIG.rootClass} .el-col-adapt {
    width: auto !important;
    max-width: none !important;
    min-width: 0 !important;
    padding: 0 !important;
    float: none !important;
  }
  body.${CONFIG.rootClass} .grid-content {
    width: 100% !important;
    max-width: none !important;
    background: transparent !important;
  }
  body.${CONFIG.rootClass} .grid-content .img-container,
  body.${CONFIG.rootClass} .grid-content .img-box,
  body.${CONFIG.rootClass} .grid-content .img-box a {
    display: block !important;
    width: 100% !important;
    max-width: none !important;
    min-width: 0 !important;
    margin: 0 !important;
  }
  body.${CONFIG.rootClass} .grid-content .img-container {
    position: relative;
    aspect-ratio: 3 / 4;
    border-radius: 8px;
    overflow: hidden;
    background: #e5e7eb;
  }
  body.${CONFIG.rootClass} .grid-content .img-box,
  body.${CONFIG.rootClass} .grid-content .img-box a {
    height: 100% !important;
  }
  body.${CONFIG.rootClass} .grid-content .img-container img {
    display: block;
    width: 100% !important;
    height: 100% !important;
    object-fit: cover;
  }
  body.${CONFIG.rootClass} .grid-content .title {
    margin-top: 5px !important;
    font-size: 13px !important;
    line-height: 1.35 !important;
  }
  body.${CONFIG.rootClass} .grid-content .title a {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  body.${CONFIG.rootClass} .search-list .item-row,
  body.${CONFIG.rootClass} .ranking-content .item-row {
    display: grid !important;
    grid-template-columns: 28px 82px 1fr;
    gap: 10px;
    padding: 12px !important;
    background: #fff !important;
  }
  body.${CONFIG.rootClass} .item-row .item-thumb img {
    width: 82px !important;
    height: 112px !important;
    object-fit: cover;
    border-radius: 8px;
  }
  body.${CONFIG.rootClass} .item-row .item-title {
    font-size: 16px !important;
    line-height: 1.3 !important;
  }
  body.${CONFIG.rootClass} .item-row .item-sub,
  body.${CONFIG.rootClass} .item-row .item-desc {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    font-size: 12px !important;
    line-height: 1.5 !important;
  }
}
@media (max-width: 340px) {
  body.${CONFIG.rootClass} .el-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
`;
    addStyle(css);
  }

  function addStyle(css) {
    if (typeof GM_addStyle === "function") {
      GM_addStyle(css);
      return;
    }
    const style = document.createElement("style");
    style.textContent = css;
    const target = document.head || document.documentElement;
    if (target) target.appendChild(style);
  }

  function handleMediaEvent(event) {
    try {
      const detail = event && event.detail;
      const requestId = detail && detail.requestId;
      const url = typeof detail === "string" ? detail : detail && detail.url;
      if (requestId && state.mediaResolvers[requestId]) {
        state.mediaResolvers[requestId](playableMediaUrl(url));
        delete state.mediaResolvers[requestId];
        return;
      }
      const source = detail && detail.source || "page";
      rememberPlayerUrl(url, source);
    } catch (e) {
      log("media event skipped", e && e.message || e);
    }
  }

  function installArtplayerHook() {
    wrapArtplayerIfReady();
    if (state.artplayerWrapped) return;
    try {
      let current = window.Artplayer;
      Object.defineProperty(window, "Artplayer", {
        configurable: true,
        get: function () {
          return current;
        },
        set: function (value) {
          current = wrapArtplayer(value);
        }
      });
    } catch (e) {
      log("artplayer hook skipped", e && e.message || e);
    }
  }

  function wrapArtplayerIfReady() {
    if (typeof window.Artplayer !== "function") return;
    window.Artplayer = wrapArtplayer(window.Artplayer);
  }

  function wrapArtplayer(Original) {
    if (typeof Original !== "function" || Original.__fmYmvidWrapped) return Original;
    function WrappedArtplayer() {
      const args = Array.prototype.slice.call(arguments);
      rememberPlayerUrl(args[0] && args[0].url, "artplayer");
      return Reflect.construct(Original, args, WrappedArtplayer);
    }
    try {
      Object.setPrototypeOf(WrappedArtplayer, Original);
      WrappedArtplayer.prototype = Original.prototype;
      Object.defineProperty(WrappedArtplayer, "__fmYmvidWrapped", { value: true });
    } catch (e) {
      // Best effort only. The wrapper still captures constructor options.
    }
    state.artplayerWrapped = true;
    return WrappedArtplayer;
  }

  function installHlsHook() {
    wrapHlsIfReady();
    if (state.hlsWrapped) return;
    try {
      let current = window.Hls;
      Object.defineProperty(window, "Hls", {
        configurable: true,
        get: function () {
          return current;
        },
        set: function (value) {
          current = wrapHls(value);
        }
      });
    } catch (e) {
      log("hls hook skipped", e && e.message || e);
    }
  }

  function wrapHlsIfReady() {
    if (typeof window.Hls !== "function") return;
    window.Hls = wrapHls(window.Hls);
  }

  function wrapHls(Original) {
    if (typeof Original !== "function" || Original.__fmYmvidWrapped) return Original;
    const proto = Original.prototype;
    if (proto && typeof proto.loadSource === "function" && !proto.loadSource.__fmYmvidWrapped) {
      const loadSource = proto.loadSource;
      proto.loadSource = function () {
        rememberPlayerUrl(arguments[0], "hls");
        return loadSource.apply(this, arguments);
      };
      try {
        Object.defineProperty(proto.loadSource, "__fmYmvidWrapped", { value: true });
      } catch (e) {
        // Best effort only.
      }
    }
    try {
      Object.defineProperty(Original, "__fmYmvidWrapped", { value: true });
    } catch (e) {
      // Best effort only.
    }
    state.hlsWrapped = true;
    return Original;
  }

  function installPageBridge() {
    if (state.pageBridgeInjected) return;
    state.pageBridgeInjected = true;
    try {
      const script = document.createElement("script");
      script.textContent = "(" + function (mediaEvent, requestEvent) {
        if (window.__fmYmvidPageBridge) {
          window.dispatchEvent(new Event(requestEvent));
          return;
        }
        window.__fmYmvidPageBridge = true;

        function clean(value) {
          return String(value || "").replace(/\s+/g, " ").trim();
        }

        function absoluteUrl(url) {
          const value = clean(url);
          if (!value || value === "#" || /^javascript:/i.test(value)) return "";
          try {
            return new URL(value, location.href).href;
          } catch (e) {
            return value;
          }
        }

        function emit(url, source, requestId) {
          const resolved = absoluteUrl(url);
          if (!resolved) return;
          if (!/^https?:\/\//i.test(resolved) && !/\.m3u8(?:[?#]|$)/i.test(resolved)) return;
          window.dispatchEvent(new CustomEvent(mediaEvent, { detail: { url: resolved, source: source || "page", requestId: requestId || "" } }));
        }

        function buildMediaUrlFromParts(encrypted, listId, videoId) {
          if (!encrypted || !listId || !videoId) return "";
          let decrypted = "";
          try {
            decrypted = clean(window.decryptByAES(encrypted));
          } catch (e) {
            return "";
          }
          const marker = "?t=";
          const index = decrypted.indexOf(marker);
          if (index < 0) return "";
          const base = decrypted.slice(0, index);
          const token = decrypted.slice(index + marker.length);
          const listParts = String(listId || "").split("-");
          const seriesId = listParts[0] || "";
          const route = listParts[1] || "0";
          return route === "0" || !seriesId ? base + "?t=" + token + "&vId=" + videoId : base + "/" + seriesId + "?t=" + token + "&vId=" + videoId;
        }

        function buildMediaUrl() {
          const list = document.querySelector(".play-list");
          const input = document.querySelector(".section-content > input[type='hidden'],.section-content > input");
          const main = document.getElementById("main");
          if (!list || !input || !main || typeof window.decryptByAES !== "function") return "";
          return buildMediaUrlFromParts(clean(input.value), clean(list.getAttribute("data-id")), clean(main.getAttribute("data-id")));
        }

        function computeAndEmit(event) {
          const detail = event && event.detail;
          if (detail && detail.requestId) {
            emit(buildMediaUrlFromParts(clean(detail.encrypted), clean(detail.listId), clean(detail.videoId)), "decrypt", detail.requestId);
            return;
          }
          const url = buildMediaUrl();
          if (url) emit(url, "compute");
          const video = document.querySelector("#player video,video");
          if (video) emit(video.currentSrc || video.src, "video");
        }

        function poll() {
          let count = 0;
          const tick = function () {
            computeAndEmit();
            count += 1;
            if (count < 80) setTimeout(tick, 150);
          };
          tick();
        }

        function wrapArtplayer(Original) {
          if (typeof Original !== "function" || Original.__fmYmvidPageWrapped) return Original;
          function WrappedArtplayer() {
            const args = Array.prototype.slice.call(arguments);
            emit(args[0] && args[0].url, "artplayer");
            return Reflect.construct(Original, args, WrappedArtplayer);
          }
          try {
            Object.setPrototypeOf(WrappedArtplayer, Original);
            WrappedArtplayer.prototype = Original.prototype;
            Object.defineProperty(WrappedArtplayer, "__fmYmvidPageWrapped", { value: true });
          } catch (e) {
            // Best effort only.
          }
          return WrappedArtplayer;
        }

        function wrapHls(Original) {
          if (typeof Original !== "function" || Original.__fmYmvidPageWrapped) return Original;
          const proto = Original.prototype;
          if (proto && typeof proto.loadSource === "function" && !proto.loadSource.__fmYmvidPageWrapped) {
            const loadSource = proto.loadSource;
            proto.loadSource = function () {
              emit(arguments[0], "hls");
              return loadSource.apply(this, arguments);
            };
            try {
              Object.defineProperty(proto.loadSource, "__fmYmvidPageWrapped", { value: true });
            } catch (e) {
              // Best effort only.
            }
          }
          try {
            Object.defineProperty(Original, "__fmYmvidPageWrapped", { value: true });
          } catch (e) {
            // Best effort only.
          }
          return Original;
        }

        function installConstructorHooks() {
          try {
            let artplayer = window.Artplayer;
            Object.defineProperty(window, "Artplayer", {
              configurable: true,
              get: function () {
                return artplayer;
              },
              set: function (value) {
                artplayer = wrapArtplayer(value);
              }
            });
            if (typeof artplayer === "function") window.Artplayer = artplayer;
          } catch (e) {
            // Best effort only.
          }
          try {
            let hls = window.Hls;
            Object.defineProperty(window, "Hls", {
              configurable: true,
              get: function () {
                return hls;
              },
              set: function (value) {
                hls = wrapHls(value);
              }
            });
            if (typeof hls === "function") window.Hls = hls;
          } catch (e) {
            // Best effort only.
          }
        }

        installConstructorHooks();
        window.addEventListener(requestEvent, computeAndEmit, false);
        if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", poll, { once: true });
        else poll();
      } + ")(" + JSON.stringify(CONFIG.mediaEvent) + "," + JSON.stringify(CONFIG.mediaRequestEvent) + ");";
      const target = document.head || document.documentElement;
      if (target) {
        target.appendChild(script);
        if (script.parentNode) script.parentNode.removeChild(script);
      }
    } catch (e) {
      state.pageBridgeInjected = false;
      log("page bridge skipped", e && e.message || e);
    }
  }

  function requestPageMediaUrl() {
    try {
      window.dispatchEvent(new CustomEvent(CONFIG.mediaRequestEvent));
    } catch (e) {
      // Best effort only.
    }
  }

  function playableMediaUrl(url) {
    const resolved = absoluteUrl(url);
    if (!resolved || /^(?:blob|data):/i.test(resolved)) return "";
    if (/\.m3u8(?:[?#]|$)/i.test(resolved)) return resolved;
    if (/^https?:\/\//i.test(resolved) && !/\.(?:avif|css|gif|jpe?g|js|png|svg|webp)(?:[?#]|$)/i.test(resolved)) return resolved;
    return "";
  }

  function rememberPlayerUrl(url, source) {
    const resolved = playableMediaUrl(url);
    if (resolved) {
      if (resolved === state.lastMediaUrl) return;
      state.lastMediaUrl = resolved;
      updatePanelStatus("已捕获播放地址");
      log("captured media", source || "unknown", resolved);
    }
  }

  function clearPopups() {
    const selectors = [
      ".first-pop-layer",
      ".art-layer-ads",
      ".ads-view-btn",
      ".detail-ads-img"
    ];
    selectors.forEach((selector) => {
      const nodes = document.querySelectorAll(selector);
      for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const adLayer = node.closest && (node.closest(".art-layer") || node.closest(".first-pop-layer"));
        const target = adLayer || node;
        if (target && target.parentNode) target.parentNode.removeChild(target);
      }
    });
  }

  function enhancePlayPage() {
    if (!isPlayPage()) {
      removeNativeEpisodes();
      return;
    }
    const playerSection = document.querySelector(".player-section") || document.getElementById("player");
    if (!playerSection) return;
    let panel = document.getElementById(CONFIG.panelId);
    if (!panel) {
      panel = document.createElement("div");
      panel.id = CONFIG.panelId;
      panel.innerHTML = [
        '<button type="button" class="' + CONFIG.primaryClass + '">App播放</button>',
        '<button type="button" class="fm-ymvid-secondary">刷新地址</button>',
        '<span class="' + CONFIG.statusClass + '">准备当前集</span>'
      ].join("");
      const primary = panel.querySelector("." + CONFIG.primaryClass);
      const refresh = panel.querySelector(".fm-ymvid-secondary");
      primary.addEventListener("click", function () {
        nativePlay();
      });
      refresh.addEventListener("click", function () {
        state.lastMediaUrl = "";
        updatePanelStatus(currentEpisodeText() || "已刷新");
        requestPageMediaUrl();
        scheduleScan();
      });
      playerSection.appendChild(panel);
    }
    updatePanelStatus(currentEpisodeText() || "当前集");
    requestPageMediaUrl();
    renderNativeEpisodes();
  }

  function isPlayPage() {
    return /^\/(?:hk\/)?play\/\d+(?:\/\d+)?/i.test(location.pathname) &&
      !!document.getElementById("main") &&
      !!document.querySelector(".player-section,#player") &&
      !!encryptedPlayerValue();
  }

  function currentEpisodeText() {
    const active = document.querySelector(".play-list .item.active a em,.play-list .item.active a");
    const episode = cleanText(active && active.textContent);
    if (!episode) return "";
    return "当前第 " + episode + " 集";
  }

  function pageTitle() {
    const title = document.querySelector("article .media-info h1,.media-info h1,h1");
    const text = cleanText(title && title.textContent);
    const episode = currentEpisodeText();
    return [text || cleanText(document.title.replace(/\s*\|\s*粤漫之家\s*$/i, "")), episode].filter(Boolean).join(" ");
  }

  async function nativePlay(selectedEpisode) {
    if (state.nativeBusy) return;
    state.nativeBusy = true;
    updatePanelBusy(true);
    try {
      resetMediaCacheIfEpisodeChanged();
      const sdk = await whenFm();
      if (sdk.vodInline) {
        const payload = await buildInlineVodPayload(sdk, selectedEpisode);
        if (payload && payload.episodes && payload.episodes.length) {
          updatePanelStatus(selectedEpisode && selectedEpisode.label ? "打开 " + selectedEpisode.label : "打开 App 播放列表");
          return sdk.vodInline(payload);
        }
      }
      const mediaUrl = await waitForMediaUrl(6000, { allowCached: false });
      if (!mediaUrl) {
        updatePanelStatus("未取得播放地址");
        toast("播放地址未准备好");
        return;
      }
      updatePanelStatus("交给 App 播放");
      return sdk.play(mediaUrl, pageTitle(), {
        headers: { Referer: location.href, "User-Agent": navigator.userAgent },
        credentials: "include"
      });
    } catch (error) {
      log("native play failed", error && (error.stack || error.message) || error);
      toast("调用 App 播放失败");
      updatePanelStatus("App 播放失败");
    } finally {
      state.nativeBusy = false;
      updatePanelBusy(false);
    }
  }

  async function buildInlineVodPayload(sdk, selectedEpisode) {
    const episodes = collectEpisodes();
    if (!episodes.length) return null;
    const title = resourceTitle();
    const pic = posterUrl();
    const selected = normalizeEpisodeSelection(selectedEpisode);
    const selectedPath = selected ? normalizePath(selected.href) : "";
    const current = selectedPath ? episodes.find((item) => normalizePath(item.href) === selectedPath) || selected : episodes.find((item) => item.active) || episodes[0];
    const currentPath = current ? normalizePath(current.href) : "";
    const inlineEpisodes = episodes.map((item) => ({
      name: item.label,
      label: item.label,
      active: currentPath ? normalizePath(item.href) === currentPath : !!item.active,
      url: item.href,
      pageUrl: item.href,
      resolve: true,
      credentials: "include",
      headers: { Referer: item.href, "User-Agent": navigator.userAgent }
    }));
    return {
      vod_id: "ymvid-" + (mainVideoId() || normalizePath(location.href)),
      vod_name: title,
      title: title,
      vod_pic: pic,
      pic: pic,
      vod_content: cleanText(document.querySelector(".intro-detail,.intro-row") && document.querySelector(".intro-detail,.intro-row").textContent),
      vod_play_from: "粤漫之家",
      mark: current && current.label || "",
      credentials: "include",
      headers: { Referer: location.href, "User-Agent": navigator.userAgent },
      episodes: inlineEpisodes
    };
  }

  function normalizeEpisodeSelection(episode) {
    if (!episode) return null;
    const href = absoluteUrl(episode.href || episode.url || episode.pageUrl);
    if (!href) return null;
    return {
      href: href,
      label: cleanText(episode.label || episode.name || episode.title)
    };
  }

  async function resolveInlineEpisode(payload) {
    const data = payload || {};
    const sdk = await whenFm();
    const pageUrl = absoluteUrl(data.pageUrl || data.url || data.href || location.href);
    if (!pageUrl) throw new Error("missing episode page");
    const samePage = normalizePath(pageUrl) === normalizePath(location.href);
    let mediaUrl = "";
    if (!mediaUrl) {
      const parts = samePage ? currentEpisodeParts() : await fetchEpisodeParts(pageUrl, sdk);
      mediaUrl = parts ? await decryptEpisodeParts(parts) : "";
    }
    if (!mediaUrl && samePage) mediaUrl = currentMediaUrl({ allowCached: false }) || await waitForMediaUrl(5000, { allowCached: false });
    mediaUrl = playableMediaUrl(mediaUrl);
    if (!mediaUrl) throw new Error("episode media unavailable");
    return {
      url: mediaUrl,
      format: "application/x-mpegURL",
      credentials: "include",
      headers: { Referer: pageUrl, "User-Agent": navigator.userAgent }
    };
  }

  async function fetchEpisodeParts(url, sdk) {
    const response = await sdk.req(url, {
      headers: { Referer: location.href, "User-Agent": navigator.userAgent },
      credentials: "include",
      timeout: 20
    });
    if (!response || !response.ok || !response.body) return null;
    return parseEpisodeHtml(response.body, url);
  }

  function currentEpisodeParts() {
    const encrypted = encryptedPlayerValue();
    const listId = playListId();
    const videoId = mainVideoId();
    if (!encrypted || !listId || !videoId) return null;
    return { encrypted: encrypted, listId: listId, videoId: videoId };
  }

  function parseEpisodeHtml(html, pageUrl) {
    const text = String(html || "");
    const sectionIndex = text.search(/<section\b[^>]*\bsection-content\b/i);
    const section = sectionIndex >= 0 ? text.slice(sectionIndex, sectionIndex + 1600) : text;
    const encrypted = attrFrom(section, /<input\b(?=[^>]*\btype=(["'])hidden\1)[^>]*\bvalue=(["'])(.*?)\2[^>]*>/i, 3) ||
      attrFrom(section, /<input\b[^>]*\bvalue=(["'])(.*?)\1[^>]*>/i, 2);
    const mainTag = tagById(text, "main");
    const listTag = tagByClass(text, "play-list");
    const videoId = attrFrom(mainTag, /\bdata-id=(["'])(.*?)\1/i, 2) || mainVideoId();
    const listId = attrFrom(listTag, /\bdata-id=(["'])(.*?)\1/i, 2);
    if (!encrypted || !listId || !videoId) {
      log("episode parts missing", pageUrl, !!encrypted, listId, videoId);
      return null;
    }
    return {
      encrypted: htmlDecode(encrypted),
      listId: htmlDecode(listId),
      videoId: htmlDecode(videoId)
    };
  }

  function attrFrom(text, pattern, group) {
    const match = String(text || "").match(pattern);
    return cleanText(match && match[group || 1]);
  }

  function tagById(html, id) {
    const pattern = new RegExp("<[^>]+\\bid=[\"']" + escapeRegex(id) + "[\"'][^>]*>", "i");
    const match = String(html || "").match(pattern);
    return match ? match[0] : "";
  }

  function tagByClass(html, className) {
    const pattern = new RegExp("<[^>]+\\bclass=[\"'][^\"']*\\b" + escapeRegex(className) + "\\b[^\"']*[\"'][^>]*>", "i");
    const match = String(html || "").match(pattern);
    return match ? match[0] : "";
  }

  function htmlDecode(value) {
    const text = String(value || "");
    if (text.indexOf("&") < 0) return text;
    const box = document.createElement("textarea");
    box.innerHTML = text;
    return box.value;
  }

  function escapeRegex(value) {
    return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function decryptEpisodeParts(parts) {
    if (!parts || !parts.encrypted || !parts.listId || !parts.videoId) return Promise.resolve("");
    const requestId = "fm_ymvid_media_" + Date.now() + "_" + Math.random().toString(36).slice(2);
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        if (state.mediaResolvers[requestId]) {
          delete state.mediaResolvers[requestId];
          resolve("");
        }
      }, 5000);
      state.mediaResolvers[requestId] = function (url) {
        clearTimeout(timer);
        resolve(url || "");
      };
      try {
        window.dispatchEvent(new CustomEvent(CONFIG.mediaRequestEvent, {
          detail: {
            requestId: requestId,
            encrypted: parts.encrypted,
            listId: parts.listId,
            videoId: parts.videoId
          }
        }));
      } catch (e) {
        clearTimeout(timer);
        delete state.mediaResolvers[requestId];
        resolve("");
      }
    });
  }

  function mainVideoId() {
    const main = document.getElementById("main");
    return cleanText(main && main.getAttribute("data-id"));
  }

  function resourceTitle() {
    const title = document.querySelector("article .media-info h1,.media-info h1,h1");
    return cleanText(title && title.textContent) || cleanText(document.title.replace(/\s*\|\s*粤漫之家\s*$/i, ""));
  }

  function posterUrl() {
    const image = document.querySelector("article .media-thumb img,.media-thumb img,.grid-content img");
    return image ? absoluteUrl(image.getAttribute("src") || image.src) : "";
  }

  function updatePanelBusy(busy) {
    const panel = document.getElementById(CONFIG.panelId);
    if (!panel) return;
    const button = panel.querySelector("." + CONFIG.primaryClass);
    if (button) button.textContent = busy ? "处理中" : "App播放";
  }

  function updatePanelStatus(message) {
    const panel = document.getElementById(CONFIG.panelId);
    if (!panel) return;
    const status = panel.querySelector("." + CONFIG.statusClass);
    if (status && message) status.textContent = message;
  }

  function waitForMediaUrl(timeout, options) {
    const deadline = Date.now() + timeout;
    return new Promise((resolve) => {
      const tick = function () {
        requestPageMediaUrl();
        const mediaUrl = currentMediaUrl(options);
        if (mediaUrl || Date.now() >= deadline) {
          resolve(mediaUrl);
          return;
        }
        setTimeout(tick, 120);
      };
      tick();
    });
  }

  function currentMediaUrl(options) {
    const allowCached = !options || options.allowCached !== false;
    const computed = computeMediaUrl();
    if (computed) {
      state.lastMediaUrl = computed;
      return computed;
    }
    if (allowCached && state.lastMediaUrl) return state.lastMediaUrl;
    const video = document.querySelector("#player video,video");
    if (video && video.currentSrc) return playableMediaUrl(video.currentSrc);
    if (video && video.src) return playableMediaUrl(video.src);
    return "";
  }

  function resetMediaCacheIfEpisodeChanged() {
    const key = [normalizePath(location.href), encryptedPlayerValue(), playListId(), mainVideoId()].join("|");
    if (!key || key === state.lastEpisodeKey) return;
    state.lastEpisodeKey = key;
    state.lastMediaUrl = "";
  }

  function computeMediaUrl() {
    const encrypted = encryptedPlayerValue();
    const listId = playListId();
    const main = document.getElementById("main");
    const videoId = main && main.getAttribute("data-id");
    if (!encrypted || !listId || !videoId || typeof window.decryptByAES !== "function") return "";
    let decrypted = "";
    try {
      decrypted = window.decryptByAES(encrypted);
    } catch (e) {
      log("decrypt failed", e && e.message || e);
    }
    if (!decrypted) return "";
    const marker = "?t=";
    const index = decrypted.indexOf(marker);
    if (index < 0) return "";
    const base = decrypted.slice(0, index);
    const token = decrypted.slice(index + marker.length);
    const listParts = String(listId || "").split("-");
    const seriesId = listParts[0] || "";
    const route = listParts[1] || "0";
    const url = route === "0" || !seriesId ? base + "?t=" + token + "&vId=" + videoId : base + "/" + seriesId + "?t=" + token + "&vId=" + videoId;
    return playableMediaUrl(url);
  }

  function encryptedPlayerValue() {
    const input = document.querySelector(".section-content > input[type='hidden'],.section-content > input");
    return input ? cleanText(input.value) : "";
  }

  function playListId() {
    const list = document.querySelector(".play-list");
    return list ? cleanText(list.getAttribute("data-id")) : "";
  }

  function renderNativeEpisodes() {
    const episodes = collectEpisodes();
    if (!episodes.length) {
      removeNativeEpisodes();
      return;
    }

    let container = document.getElementById(CONFIG.episodeId);
    if (!container) {
      container = document.createElement("section");
      container.id = CONFIG.episodeId;
      const panel = document.getElementById(CONFIG.panelId);
      const playerSection = document.querySelector(".player-section") || document.getElementById("player");
      if (panel && panel.parentNode) panel.parentNode.insertBefore(container, panel.nextSibling);
      else if (playerSection) playerSection.appendChild(container);
      else return;
    }

    const signature = episodes.map((item) => [item.href, item.label, item.active ? "1" : "0"].join("#")).join("|");
    if (container.dataset.fmYmvidSignature !== signature) {
      container.dataset.fmYmvidSignature = signature;
      const current = episodes.find((item) => item.active);
      container.innerHTML = [
        '<div class="fm-ymvid-episode-head">',
        '<span class="fm-ymvid-episode-title">剧集</span>',
        '<span class="fm-ymvid-episode-current">' + escapeHtml(current ? "当前 " + current.label : "") + '</span>',
        '</div>',
        '<div class="fm-ymvid-episode-grid">',
        episodes.map((item) => {
          const activeClass = item.active ? " fm-ymvid-active" : "";
          return '<a class="' + activeClass + '" href="' + escapeAttr(item.href) + '" data-fm-ymvid-native-episode="1">' + escapeHtml(item.label) + '</a>';
        }).join(""),
        '</div>'
      ].join("");
    }

    document.body.classList.add("fm-ymvid-has-native-episodes");
    const links = container.querySelectorAll("a[href]");
    for (let i = 0; i < links.length; i++) {
      bindEpisodeLink(links[i]);
    }
    const active = container.querySelector(".fm-ymvid-active");
    if (active && active.scrollIntoView && !active.dataset.fmYmvidSeen) {
      active.dataset.fmYmvidSeen = "1";
      setTimeout(() => active.scrollIntoView({ block: "nearest", inline: "center" }), 180);
    }
  }

  function removeNativeEpisodes() {
    const container = document.getElementById(CONFIG.episodeId);
    if (container && container.parentNode) container.parentNode.removeChild(container);
    if (document.body) document.body.classList.remove("fm-ymvid-has-native-episodes");
  }

  function collectEpisodes() {
    const links = document.querySelectorAll(".play-list .item a[href]");
    const seen = {};
    const episodes = [];
    for (let i = 0; i < links.length; i++) {
      const link = links[i];
      const href = absoluteUrl(link.getAttribute("href"));
      if (!href || seen[href]) continue;
      seen[href] = true;
      const em = link.querySelector("em");
      const rawLabel = cleanText(em && em.textContent || link.textContent);
      const label = rawLabel || String(i + 1).padStart(2, "0");
      const item = link.closest && link.closest(".item");
      const active = !!(item && item.classList && item.classList.contains("active")) || normalizePath(href) === normalizePath(location.href);
      episodes.push({ href: href, label: label, active: active });
    }
    return episodes;
  }

  function normalizePath(url) {
    try {
      const parsed = new URL(url, location.href);
      return parsed.pathname.replace(/\/+$/, "");
    } catch (e) {
      return String(url || "").replace(/\/+$/, "");
    }
  }

  function escapeHtml(value) {
    return String(value || "").replace(/[&<>"']/g, function (char) {
      return ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char];
    });
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, "&#96;");
  }

  function enhanceEpisodeList() {
    const links = document.querySelectorAll(".play-list .item a[href]");
    for (let i = 0; i < links.length; i++) {
      bindEpisodeLink(links[i]);
    }
    const active = document.querySelector(".play-list .item.active a");
    if (active && active.scrollIntoView && !active.dataset.fmYmvidSeen) {
      active.dataset.fmYmvidSeen = "1";
      setTimeout(() => active.scrollIntoView({ block: "nearest", inline: "center" }), 180);
    }
  }

  function bindEpisodeLink(link) {
    if (!link || link.dataset.fmYmvidEpisode === "1") return;
    link.dataset.fmYmvidEpisode = "1";
    link.setAttribute("tabindex", "0");
    link.addEventListener("click", function (event) {
      handleEpisodeClick(link, event);
    });
    link.addEventListener("keydown", function (event) {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        handleEpisodeClick(link, event);
      }
    });
  }

  function handleEpisodeClick(link, event) {
    if (!canNativePlay()) return false;
    if (event && (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button > 0)) return false;
    const episode = episodeFromLink(link);
    if (!episode) return false;
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    nativePlay(episode);
    return true;
  }

  function episodeFromLink(link) {
    const href = absoluteUrl(link && link.getAttribute("href"));
    if (!href) return null;
    const path = normalizePath(href);
    const fromList = collectEpisodes().find((item) => normalizePath(item.href) === path);
    if (fromList) return fromList;
    return {
      href: href,
      label: cleanText(link && (link.querySelector("em") && link.querySelector("em").textContent || link.textContent))
    };
  }

  function canNativePlay() {
    return !!(window.fm || window.fongmiBridge || window.fongmiClient);
  }

  function enhanceCards() {
    const cards = document.querySelectorAll(".grid-content,.feature-post-box,.swiper-slide,.item-row,.aside-body li");
    for (let i = 0; i < cards.length; i++) enhanceCard(cards[i]);
    const direct = document.querySelectorAll(CONFIG.focusSelector);
    for (let i = 0; i < direct.length; i++) {
      if (direct[i].dataset.fmYmvidFocus === "1") continue;
      direct[i].dataset.fmYmvidFocus = "1";
      direct[i].addEventListener("focus", focusCurrent, true);
    }
  }

  function enhanceCard(card) {
    if (!card || card.dataset.fmYmvidCard === "1") return;
    const link = card.querySelector("a[href]");
    if (!link) return;
    card.dataset.fmYmvidCard = "1";
    card.setAttribute("tabindex", "0");
    card.setAttribute("role", "link");
    card.addEventListener("click", function (event) {
      if (event.target && event.target.closest && event.target.closest("a,button,input,textarea,select")) return;
      link.click();
    });
    card.addEventListener("keydown", function (event) {
      if (state.inputEditing) return;
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        link.click();
      }
    });
    card.addEventListener("focus", focusCurrent, true);
    card.addEventListener("blur", function () {
      card.classList.remove(CONFIG.focusClass);
    }, true);
  }

  function focusCurrent(event) {
    if (!isTv()) return;
    const target = event.currentTarget || event.target;
    if (!target || !target.classList) return;
    clearTimeout(state.focusRaf);
    target.classList.add(CONFIG.focusClass);
    state.focusRaf = setTimeout(() => {
      try {
        target.scrollIntoView({ block: "nearest", inline: "nearest" });
      } catch (e) {
        // ignore
      }
    }, 40);
  }

  function handleFocusIn(event) {
    const tag = event.target && event.target.tagName;
    state.inputEditing = /^(INPUT|TEXTAREA|SELECT)$/i.test(tag || "");
  }

  function handleFocusOut(event) {
    const target = event.target;
    if (target && target.classList) target.classList.remove(CONFIG.focusClass);
    const tag = target && target.tagName;
    if (/^(INPUT|TEXTAREA|SELECT)$/i.test(tag || "")) state.inputEditing = false;
  }

  function toast(message) {
    try {
      if (window.fm && fm.ext && fm.ext.toast) return fm.ext.toast(message);
    } catch (e) {
      // ignore
    }
    return Promise.resolve();
  }
})();
