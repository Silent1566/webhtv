// Generic WebHome resource router.
// Edit CONFIG for the target website before use.
(function () {
  const CONFIG = {
    // Keep this selector narrow. Do not blindly intercept every link on a normal website.
    actionSelector: [
      "[data-url]",
      "[data-href]",
      "[data-link]",
      "[data-clipboard-text]",
      "a[href^='magnet:']",
      "a[href^='ed2k:']",
      "a[href^='thunder:']",
      "a[href*='pan.quark.cn']",
      "a[href*='aliyundrive.com']",
      "a[href*='alipan.com']",
      "a[href*='pan.baidu.com']",
      "a[href*='drive.uc.cn']",
      "a[href*='pan.xunlei.com']",
      "a[href*='cloud.189.cn']",
      "a[href*='123pan']"
    ].join(","),
    titleSelector: "h1,h2,.title,.vod-title,.detail-title",
    resourceTextRegex: /播放|下载|资源|网盘|磁力|電驢|迅雷|play|download/i,
    ignoreSelector: "input,textarea,select,[contenteditable='true']"
  };

  const PAN_TYPES = [
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

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-router]"].concat(args));
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => {
      window.addEventListener("fmsdk", () => resolve(window.fm), { once: true });
    });
  }

  function titleFromPage() {
    const el = document.querySelector(CONFIG.titleSelector);
    const value = el && el.textContent ? el.textContent.trim() : "";
    return value || document.title || location.href;
  }

  function titleFromElement(el) {
    const card = el.closest("[data-title],.card,.item,.vod,.video,.download-item,.resource-item,li");
    const explicit = card && card.getAttribute("data-title");
    if (explicit) return explicit.trim();
    const titleEl = card && card.querySelector("[title],.title,.name,h1,h2,h3");
    const title = titleEl && (titleEl.getAttribute("title") || titleEl.textContent || "").trim();
    return title || (el.textContent || "").trim() || titleFromPage();
  }

  function attr(el, name) {
    return (el.getAttribute(name) || "").trim();
  }

  function urlFromElement(el) {
    const values = [
      attr(el, "data-url"),
      attr(el, "data-href"),
      attr(el, "data-link"),
      attr(el, "data-clipboard-text"),
      attr(el, "href")
    ];
    for (const value of values) {
      const normalized = normalizeUrl(value);
      if (normalized) return normalized;
    }
    return "";
  }

  function normalizeUrl(value) {
    if (!value || value === "#" || /^javascript:/i.test(value)) return "";
    if (/^(magnet:|ed2k:|thunder:)/i.test(value)) return value;
    try {
      return new URL(value, location.href).href;
    } catch (e) {
      return value;
    }
  }

  function classify(url) {
    if (/^magnet:/i.test(url)) return "magnet";
    if (/^ed2k:/i.test(url)) return "ed2k";
    if (/^thunder:/i.test(url)) return "thunder";
    for (const item of PAN_TYPES) if (item[1].test(url)) return item[0];
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm|mp3|aac)(\?|#|$)/i.test(url)) return "media";
    if (/^https?:/i.test(url)) return "http";
    return "unknown";
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

  function isResource(el, url) {
    if (!url) return false;
    const type = classify(url);
    if (type !== "http" && type !== "unknown") return true;
    const text = (el.textContent || "").trim();
    return CONFIG.resourceTextRegex.test(text);
  }

  async function route(url, title) {
    const sdk = await whenFm();
    const type = classify(url);
    log("route", type, title, url);
    if (type === "media") {
      return sdk.play(url, title, mediaPlayOptions(url));
    }
    return sdk.pan.play({ type: type === "unknown" ? "http" : type, url, title });
  }

  document.addEventListener("click", function (event) {
    if (event.target.closest(CONFIG.ignoreSelector)) return;
    const el = event.target.closest(CONFIG.actionSelector);
    if (!el) return;
    const url = urlFromElement(el);
    if (!isResource(el, url)) return;

    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    route(url, titleFromElement(el)).catch((error) => {
      log("route error", error && (error.stack || error.message) || error);
      if (window.fm && fm.ext) fm.ext.toast("调用原生播放失败");
    });
  }, true);

  log("ready", location.href);
})();
