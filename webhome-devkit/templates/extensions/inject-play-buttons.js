// Generic "inject App play button" template.
// Use this when the website already shows resource rows/cards but you want to avoid changing original clicks.
(function () {
  const CONFIG = {
    itemSelector: ".download-item,.resource-item,.episode,.card,li",
    linkSelector: "[data-url],[data-href],[data-link],a[href]",
    titleSelector: "h1,h2,.title,.name",
    buttonClass: "fm-native-play",
    buttonText: "App播放",
    scanDelay: 120
  };

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-button]"].concat(args));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => window.addEventListener("fmsdk", () => resolve(window.fm), { once: true }));
  }

  function urlFrom(el) {
    for (const key of ["data-url", "data-href", "data-link", "href"]) {
      const value = (el.getAttribute(key) || "").trim();
      if (!value || value === "#" || /^javascript:/i.test(value)) continue;
      if (/^(magnet:|ed2k:|thunder:)/i.test(value)) return value;
      try {
        return new URL(value, location.href).href;
      } catch (e) {
        return value;
      }
    }
    return "";
  }

  function typeOf(url) {
    if (/^magnet:/i.test(url)) return "magnet";
    if (/^ed2k:/i.test(url)) return "ed2k";
    if (/^thunder:/i.test(url)) return "thunder";
    if (/pan\.quark\.cn/i.test(url)) return "quark";
    if (/aliyundrive\.com|alipan\.com/i.test(url)) return "aliyun";
    if (/pan\.baidu\.com/i.test(url)) return "baidu";
    if (/drive\.uc\.cn/i.test(url)) return "uc";
    if (/pan\.xunlei\.com/i.test(url)) return "xunlei";
    if (/cloud\.189\.cn/i.test(url)) return "tianyi";
    if (/123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i.test(url)) return "123";
    if (/115\.com|115cdn\.com/i.test(url)) return "115";
    if (/yun\.139\.com|caiyun\.139\.com/i.test(url)) return "mobile";
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url)) return "media";
    return "http";
  }

  function isHlsUrl(url) {
    return /\.m3u8(\?|#|$)/i.test(String(url || ""));
  }

  function isDashUrl(url) {
    return /\.mpd(\?|#|$)/i.test(String(url || ""));
  }

  function mediaPlayOptions(url) {
    if (isHlsUrl(url)) return { format: "application/x-mpegURL" };
    if (isDashUrl(url)) return { format: "application/dash+xml" };
    return { headers: { Referer: location.href }, credentials: "include" };
  }

  function titleFor(button) {
    const item = button.closest(CONFIG.itemSelector);
    const local = item && item.querySelector("[title],.title,.name,h1,h2,h3");
    const page = document.querySelector(CONFIG.titleSelector);
    return (local && (local.getAttribute("title") || local.textContent || "").trim())
      || (page && page.textContent || "").trim()
      || document.title
      || location.href;
  }

  function isCandidate(url) {
    return /^(magnet:|ed2k:|thunder:)/i.test(url)
      || /pan\.quark\.cn|aliyundrive\.com|alipan\.com|pan\.baidu\.com|drive\.uc\.cn|pan\.xunlei\.com|cloud\.189\.cn|123pan/i.test(url)
      || /\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url);
  }

  function setButtonData(button, link, url) {
    button.dataset.url = url;
    button.dataset.type = typeOf(url);
    button.dataset.sourceSelector = link.tagName || "";
  }

  function liveUrlForButton(button) {
    const item = button.closest(CONFIG.itemSelector);
    if (!item) return button.dataset.url || "";
    const link = item.querySelector(CONFIG.linkSelector);
    const url = link ? urlFrom(link) : "";
    return isCandidate(url) ? url : (button.dataset.url || "");
  }

  function scan() {
    document.querySelectorAll(CONFIG.itemSelector).forEach((item) => {
      const link = item.querySelector(CONFIG.linkSelector);
      if (!link) return;
      const url = urlFrom(link);
      if (!isCandidate(url)) return;
      const existing = item.querySelector("." + CONFIG.buttonClass);
      if (existing) {
        setButtonData(existing, link, url);
        item.dataset.fmButtonReady = "1";
        return;
      }
      if (item.dataset.fmButtonReady === "1") return;
      item.dataset.fmButtonReady = "1";
      const button = document.createElement("button");
      button.type = "button";
      button.className = CONFIG.buttonClass;
      button.textContent = CONFIG.buttonText;
      setButtonData(button, link, url);
      item.appendChild(button);
    });
  }

  document.addEventListener("click", function (event) {
    const button = event.target.closest("." + CONFIG.buttonClass);
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();

    const url = liveUrlForButton(button);
    button.dataset.url = url;
    const type = typeOf(url);
    whenFm().then((sdk) => {
      if (type === "media") return sdk.play(url, titleFor(button), mediaPlayOptions(url));
      return sdk.pan.play({ type, url, title: titleFor(button) });
    }).catch((error) => log("play error", error && (error.stack || error.message) || error));
  }, true);

  function schedule() {
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(scan, CONFIG.scanDelay);
  }

  function style() {
    const css = `
      .${CONFIG.buttonClass} {
        margin: 4px 0 4px 8px;
        padding: 5px 12px;
        border: 1px solid #0f766e;
        border-radius: 6px;
        background: #0f766e;
        color: #fff;
        font-size: 13px;
        font-weight: 700;
      }
      .${CONFIG.buttonClass}:focus {
        outline: 2px solid rgba(255,255,255,.85);
        outline-offset: 1px;
        background: #0d9488;
      }
    `;
    if (typeof GM_addStyle === "function") GM_addStyle(css);
  }

  style();
  scan();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener("fmurlchange", schedule);
  log("ready", location.href);
})();
