// WebHome inline multi-episode template (fm.vodInline + per-episode resolver).
// Use this when the site has an episode list but each episode page resolves
// its real media URL lazily (encrypted player config, AJAX, etc).
//
// Flow:
// 1. Collect episodes from the page (name + pageUrl, mark the active one).
// 2. Register window.__fmWebHomeInlineResolver BEFORE calling fm.vodInline().
// 3. Call fm.vodInline() with resolve:true episodes. The native player page
//    shows the episode list; when the user picks an episode, native calls the
//    resolver inside THIS page to get the real media URL.
//
// Native behavior you must know:
// - Before opening the player, native pre-resolves the current episode
//   (active:true, or the one matching `mark`, else the first) synchronously.
// - Each resolve call has a 20s timeout. Keep the resolver fast.
// - The resolver runs in this WebHome page. If the page is reloaded or
//   navigated away, episodes that are not resolved yet will fail.
// - A successfully resolved episode is cached by native; re-clicking it
//   does not call the resolver again.
(function () {
  const CONFIG = {
    episodeSelector: ".play-list a, .episode-list a",   // change per site
    activeClass: "active",
    titleSelector: "h1,h2,.title,.video-title",
    posterSelector: ".poster img,.video-pic img",
    buttonText: "App播放",
    buttonClass: "fm-inline-play"
  };

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-inline]"].concat(args));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise(function (resolve) {
      window.addEventListener("fmsdk", function () { resolve(window.fm); }, { once: true });
    });
  }

  function cleanText(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function absoluteUrl(value) {
    try {
      return new URL(String(value || ""), location.href).href;
    } catch (e) {
      return String(value || "");
    }
  }

  // ---------- episode collection ----------

  function collectEpisodes() {
    const episodes = [];
    document.querySelectorAll(CONFIG.episodeSelector).forEach(function (el, index) {
      const pageUrl = absoluteUrl(el.getAttribute("href"));
      if (!pageUrl || /^javascript:/i.test(el.getAttribute("href") || "")) return;
      const item = el.closest("li,.item") || el;
      episodes.push({
        name: cleanText(el.textContent) || String(index + 1),
        // url doubles as the stable episode key shown to the player.
        url: pageUrl,
        pageUrl: pageUrl,
        // resolve:true => native calls __fmWebHomeInlineResolver on demand.
        resolve: true,
        active: item.classList.contains(CONFIG.activeClass) || el.classList.contains(CONFIG.activeClass)
      });
    });
    return episodes;
  }

  function title() {
    const el = document.querySelector(CONFIG.titleSelector);
    return cleanText(el && el.textContent) || cleanText(document.title) || location.href;
  }

  function poster() {
    const el = document.querySelector(CONFIG.posterSelector);
    return el ? absoluteUrl(el.currentSrc || el.src || el.getAttribute("data-src")) : "";
  }

  // ---------- resolver ----------

  // Receives one episode object from the episodes array (deep copy).
  // Must return { url, format?, headers?, referer?, credentials? }.
  // Throw or return an empty url to signal failure.
  window.__fmWebHomeInlineResolver = async function (episode) {
    const pageUrl = episode.pageUrl || episode.url;
    log("resolve", pageUrl);
    const sdk = await whenFm();
    const response = await sdk.req(pageUrl, {
      headers: { Referer: location.href },
      credentials: "include",
      timeout: 15
    });
    if (!response.ok) throw new Error("HTTP " + response.status);
    // Site-specific extraction. Common cases:
    // - direct m3u8 in HTML
    // - JSON config: var player_aaaa = {...}
    // - encrypted input the site decrypts client-side (then reuse the site's
    //   own decrypt function from this page context instead of fm.req).
    const match = String(response.body || "").match(/https?:\\?\/\\?\/[^"'\s]+?\.m3u8[^"'\s]*/i);
    if (!match) throw new Error("media url not found");
    const url = match[0].replace(/\\\//g, "/");
    return mediaResolveResult(url, pageUrl);
  };

  function mediaResolveResult(url, referer) {
    if (/\.m3u8(\?|#|$)/i.test(url)) {
      return { url: url, format: "application/x-mpegURL" };
    }
    if (/\.mpd(\?|#|$)/i.test(url)) {
      return { url: url, format: "application/dash+xml" };
    }
    return {
      url: url,
      headers: { Referer: referer || location.href },
      credentials: "include"
    };
  }

  // ---------- entry button ----------

  async function play() {
    const sdk = await whenFm();
    const episodes = collectEpisodes();
    if (!episodes.length) {
      if (sdk.ext) sdk.ext.toast("未找到剧集列表");
      return;
    }
    const active = episodes.filter(function (item) { return item.active; })[0];
    log("vodInline", episodes.length, "episodes, mark=", active && active.name);
    await sdk.vodInline({
      vod_id: location.pathname,
      vod_name: title(),
      vod_pic: poster(),
      vod_play_from: "WebHome",
      mark: active ? active.name : "",
      episodes: episodes
    });
  }

  function injectButton() {
    if (document.querySelector("." + CONFIG.buttonClass)) return;
    const anchor = document.querySelector(CONFIG.episodeSelector);
    if (!anchor) return;
    const host = anchor.closest("ul,.play-list,.episode-list") || anchor.parentElement;
    if (!host || !host.parentElement) return;
    const button = document.createElement("button");
    button.type = "button";
    button.className = CONFIG.buttonClass;
    button.textContent = CONFIG.buttonText;
    button.addEventListener("click", function (event) {
      event.preventDefault();
      play().catch(function (error) {
        log("play error", (error && (error.stack || error.message)) || error);
        if (window.fm && window.fm.ext) window.fm.ext.toast("App播放失败");
      });
    });
    host.parentElement.insertBefore(button, host);
  }

  function style() {
    const css = ""
      + "." + CONFIG.buttonClass + "{display:block;margin:8px 0;padding:8px 16px;"
      + "border:1px solid #0f766e;border-radius:8px;background:#0f766e;color:#fff;"
      + "font-size:14px;font-weight:700;}"
      + "." + CONFIG.buttonClass + ":focus{outline:2px solid rgba(255,255,255,.85);outline-offset:1px;}";
    if (typeof GM_addStyle === "function") GM_addStyle(css);
  }

  function schedule() {
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(injectButton, 160);
  }

  style();
  injectButton();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener("fmurlchange", schedule);
  log("ready", location.href);
})();
