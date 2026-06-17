// WebHome extension for https://dm.xueximeng.com/
// Enhancements:
// - Adds native App play buttons to resource detail links.
// - Routes pan, magnet, ed2k, thunder and online links through the WebHome SDK.
// - Improves TV remote focus for cards, tabs, resource rows, modals and inputs.
(function () {
  const CONFIG = {
    rootClass: "fm-dm-enhanced",
    tvClass: "fm-dm-tv",
    buttonClass: "fm-dm-play",
    focusClass: "fm-dm-focus",
    scanDelay: 160,
    focusSelector: [
      "a[href]",
      "button",
      "input",
      "select",
      "textarea",
      "[role='button']",
      ".resource-card",
      ".view-details-btn",
      ".table-row",
      ".tab-btn",
      ".thumbnail",
      ".image-nav-button",
      ".nav-button",
      ".page-btn",
      ".pagination-btn",
      ".floating-btn",
      ".history-item",
      ".stream-card",
      ".episode-card-info"
    ].join(",")
  };

  const TYPE_LABELS = {
    magnet: "磁力",
    ed2k: "电驴",
    thunder: "迅雷",
    uc: "UC",
    mobile: "移动云",
    tianyi: "天翼",
    quark: "夸克",
    "115": "115",
    aliyun: "阿里",
    pikpak: "PikPak",
    baidu: "百度",
    "123": "123",
    xunlei: "迅雷",
    online: "在线",
    others: "其他"
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
    ["mobile", /yun\.139\.com|caiyun\.139\.com/i],
    ["pikpak", /mypikpak\.com|pikpakdrive/i]
  ];

  const state = {
    lastPath: "",
    lastFocusedKey: "",
    focusRaf: 0,
    scrollRaf: 0,
    inputEditing: false,
    resourceImagesCache: Object.create(null),
    resourceImageRequests: Object.create(null)
  };

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-dm]"].concat(args));
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
    if (/^(magnet:|ed2k:|thunder:)/i.test(value)) return value;
    try {
      return new URL(value, location.href).href;
    } catch (e) {
      return value;
    }
  }

  function resourceTitle() {
    const title = document.querySelector(".resource-detail .title,.resource-header .title,.resource-title,.title-cn,h1");
    const subtitle = document.querySelector(".resource-detail .subtitle,.resource-header .subtitle,.title-en,h2");
    const text = [title && title.textContent, subtitle && subtitle.textContent].map(cleanText).filter(Boolean).join(" / ");
    return text || cleanText(document.title.replace(/[-|].*$/, "")) || location.href;
  }

  function classify(url, hint) {
    const lowerHint = String(hint || "").toLowerCase();
    if (/^magnet:/i.test(url) || lowerHint === "magnet") return { type: "magnet", direct: false, label: "磁力" };
    if (/^ed2k:/i.test(url) || lowerHint === "ed2k") return { type: "ed2k", direct: false, label: "电驴" };
    if (/^thunder:/i.test(url) || lowerHint === "thunder") return { type: "thunder", direct: false, label: "迅雷" };
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm|mpd)(\?|#|$)/i.test(url)) return { type: "media", direct: true, label: "在线" };
    if (lowerHint === "online") return { type: "http", direct: false, label: "在线" };
    for (let i = 0; i < PAN_TYPES.length; i++) if (PAN_TYPES[i][1].test(url)) return { type: PAN_TYPES[i][0], direct: false, label: TYPE_LABELS[PAN_TYPES[i][0]] || "网盘" };
    if (TYPE_LABELS[lowerHint]) return { type: lowerHint, direct: false, label: TYPE_LABELS[lowerHint] };
    return { type: "http", direct: false, label: "链接" };
  }

  function categoryFromElement(el) {
    const panel = el.closest(".links-card,.resource-detail,.links-content") || document;
    const activeTab = panel.querySelector(".links-tabs .tab-btn.active,.tab-btn.active") || document.querySelector(".links-tabs .tab-btn.active,.resource-detail .tab-btn.active");
    const text = activeTab ? cleanText(activeTab.textContent).replace(/\d+$/, "") : "";
    for (const key in TYPE_LABELS) if (text.indexOf(TYPE_LABELS[key]) >= 0) return key;
    return "";
  }

  function passwordFor(row) {
    const pass = row && row.querySelector(".password-container span,.col-password span");
    const value = cleanText(pass && pass.textContent);
    return value === "-" ? "" : value;
  }

  function noteFor(row) {
    const note = row && row.querySelector(".note-text,.col-note span");
    const value = cleanText(note && note.textContent);
    return value === "-" ? "" : value;
  }

  function titleFor(el) {
    const row = el.closest(".table-row,.link-item,.resource-card,.stream-card") || el;
    const note = noteFor(row);
    return [resourceTitle(), note].filter(Boolean).join(" · ");
  }

  async function play(url, typeHint, title, row) {
    const resolved = absoluteUrl(url);
    if (!resolved) return;
    const info = classify(resolved, typeHint);
    const sdk = await whenFm();
    setBusy(row, true);
    try {
      log("play", info.type, title, resolved);
      if (info.direct) {
        return sdk.play(resolved, title || resourceTitle(), {
          headers: { Referer: location.href },
          credentials: "include"
        });
      }
      return sdk.pan.play({
        type: info.type,
        url: resolved,
        password: passwordFor(row),
        title: title || resourceTitle()
      });
    } catch (error) {
      log("play failed", error && (error.stack || error.message) || error);
      toast("调用 App 播放失败");
    } finally {
      setBusy(row, false);
    }
  }

  function setBusy(row, busy) {
    if (!row) return;
    row.classList.toggle("fm-dm-busy", !!busy);
    const button = row.querySelector("." + CONFIG.buttonClass);
    if (button) button.textContent = busy ? "处理中" : "App播放";
  }

  function toast(message) {
    try {
      if (window.fm && fm.ext && fm.ext.toast) return fm.ext.toast(message);
    } catch (e) {
      // ignore
    }
    return Promise.resolve();
  }

  function enhanceResourceLinks() {
    const links = document.querySelectorAll(".resource-detail .table-row a.link-url,.resource-detail .link-url,.links-list a[href],.links-table a[href]");
    for (let i = 0; i < links.length; i++) enhanceLink(links[i]);
  }

  function enhanceHomeCards() {
    const cards = document.querySelectorAll(".resource-card");
    for (let i = 0; i < cards.length; i++) {
      const card = cards[i];
      if (card.dataset.fmDmCard === "1") continue;
      const front = card.querySelector(".card-front");
      if (!front) continue;
      card.dataset.fmDmCard = "1";
      if (!front.querySelector(".fm-dm-card-cta")) {
        const cta = document.createElement("span");
        cta.className = "fm-dm-card-cta";
        cta.textContent = "详情";
        front.appendChild(cta);
      }
    }
  }

  function resourceIdFromLocation() {
    const match = location.pathname.match(/\/resource\/(\d+)/);
    return match ? match[1] : "";
  }

  function proxyImageUrl(url) {
    const resolved = absoluteUrl(url);
    if (!resolved) return "";
    try {
      const parsed = new URL(resolved, location.href);
      if (parsed.origin === location.origin) return parsed.href;
      return location.origin + "/app/proxy?url=" + encodeURIComponent(parsed.href);
    } catch (e) {
      return resolved;
    }
  }

  function imageRatioOf(item) {
    const width = Number(item && (item.width || item.w || item.naturalWidth)) || 0;
    const height = Number(item && (item.height || item.h || item.naturalHeight)) || 0;
    if (width > 0 && height > 0) return width / height;
    return 0;
  }

  function normalizeResourceImages(resource) {
    const raw = Array.isArray(resource && resource.images) ? resource.images : [];
    const images = raw.map((entry, index) => {
      if (typeof entry === "string") return { url: absoluteUrl(entry), index: index };
      return {
        url: absoluteUrl(entry && (entry.url || entry.src || entry.file_path || entry.path || entry.image)),
        width: entry && (entry.width || entry.w),
        height: entry && (entry.height || entry.h),
        index: index
      };
    }).filter((item) => item.url);

    const posterImage = absoluteUrl(resource && resource.poster_image);
    let poster = "";
    if (posterImage) poster = posterImage;
    else if (images.length) poster = images[0].url;

    const stills = images
      .filter((item) => item.url !== poster)
      .filter((item) => {
        const ratio = imageRatioOf(item);
        return !ratio || ratio >= 1.2;
      })
      .map((item) => item.url);

    return {
      poster: poster,
      stills: stills.length ? stills : images.filter((item) => item.url !== poster).map((item) => item.url)
    };
  }

  function requestResourceImages(id) {
    if (!id) return;
    if (state.resourceImagesCache[id] || state.resourceImageRequests[id]) return;
    state.resourceImageRequests[id] = true;
    fetch("/app/api/resources/" + encodeURIComponent(id), { credentials: "include" })
      .then((response) => response.ok ? response.json() : null)
      .then((resource) => {
        if (resource) state.resourceImagesCache[id] = normalizeResourceImages(resource);
      })
      .catch((error) => log("load resource images failed", error && (error.message || error)))
      .finally(() => {
        delete state.resourceImageRequests[id];
        scheduleEnhance();
      });
  }

  function collectDomImages() {
    const nodes = Array.prototype.slice.call(document.querySelectorAll(".resource-detail .media-section img,.resource-detail .main-image-container img,.resource-detail .thumbnails-container img"));
    const images = nodes.map((img, index) => ({
      url: absoluteUrl(img.currentSrc || img.src),
      width: img.naturalWidth,
      height: img.naturalHeight,
      index: index
    })).filter((item) => item.url);
    if (!images.length) return { poster: "", stills: [] };
    const portrait = images.find((item) => imageRatioOf(item) > 0 && imageRatioOf(item) < 1);
    const poster = portrait ? portrait.url : images[0].url;
    return {
      poster: poster,
      stills: images.filter((item) => item.url !== poster && (!imageRatioOf(item) || imageRatioOf(item) >= 1.2)).map((item) => item.url)
    };
  }

  function ensureCustomMedia() {
    const detail = document.querySelector(".resource-detail");
    const content = detail && detail.querySelector(".resource-content");
    const description = detail && detail.querySelector(".description-card");
    if (!detail || !content || !description) return;

    const id = resourceIdFromLocation();
    if (id && !state.resourceImagesCache[id]) requestResourceImages(id);
    const data = (id && state.resourceImagesCache[id]) || collectDomImages();
    if (!data.poster && !data.stills.length) return;

    let media = detail.querySelector(".fm-dm-media");
    if (!media) {
      media = document.createElement("div");
      media.className = "fm-dm-media";
      content.insertBefore(media, content.firstChild);
    }

    const poster = proxyImageUrl(data.poster || (data.stills && data.stills[0]));
    const stills = (data.stills || []).filter(Boolean).slice(0, 4).map(proxyImageUrl).filter(Boolean);
    const signature = [poster].concat(stills).join("|");
    if (media.dataset.fmDmSignature === signature) return;

    media.dataset.fmDmSignature = signature;
    media.innerHTML = "";
    detail.querySelectorAll(".fm-dm-stills").forEach((node) => node.remove());

    if (poster) {
      const posterWrap = document.createElement("div");
      posterWrap.className = "fm-dm-poster";
      const img = document.createElement("img");
      img.src = poster;
      img.alt = resourceTitle();
      posterWrap.appendChild(img);
      media.appendChild(posterWrap);
    }

    if (stills.length) {
      const stillWrap = document.createElement("div");
      stillWrap.className = "fm-dm-stills";
      for (let i = 0; i < stills.length; i++) {
        const item = document.createElement("div");
        item.className = "fm-dm-still";
        const img = document.createElement("img");
        img.src = stills[i];
        img.alt = resourceTitle() + " 剧照";
        item.appendChild(img);
        stillWrap.appendChild(item);
      }
      const linksCard = detail.querySelector(".links-card");
      if (linksCard && linksCard.parentElement) linksCard.parentElement.insertBefore(stillWrap, linksCard);
      else if (description.parentElement) description.parentElement.appendChild(stillWrap);
      else content.appendChild(stillWrap);
    }
  }

  function enhanceLink(link) {
    if (!link || link.dataset.fmDmReady === "1") return;
    const url = absoluteUrl(link.getAttribute("href") || link.textContent);
    if (!url) return;
    const row = link.closest(".table-row,.link-item") || link.parentElement;
    if (!row) return;

    link.dataset.fmDmReady = "1";
    link.dataset.fmDmUrl = url;
    link.setAttribute("tabindex", "0");
    row.setAttribute("tabindex", "0");
    row.dataset.fmFocusKey = row.dataset.fmFocusKey || "link:" + url;

    if (!row.querySelector("." + CONFIG.buttonClass)) {
      const host = row.querySelector(".col-link") || link.parentElement || row;
      const button = document.createElement("button");
      button.type = "button";
      button.className = CONFIG.buttonClass;
      button.textContent = "App播放";
      button.dataset.fmDmUrl = url;
      host.appendChild(button);
    }
  }

  function interceptLinkClick(event) {
    const button = event.target.closest("." + CONFIG.buttonClass);
    if (button) {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      const row = button.closest(".table-row,.link-item");
      play(button.dataset.fmDmUrl, categoryFromElement(button), titleFor(button), row);
      return;
    }

    const link = event.target.closest(".resource-detail .table-row a.link-url,.resource-detail .link-url");
    if (!link) return;
    const url = link.dataset.fmDmUrl || link.getAttribute("href");
    if (!url) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const row = link.closest(".table-row,.link-item");
    play(url, categoryFromElement(link), titleFor(link), row);
  }

  function enhanceFocusables() {
    const nodes = document.querySelectorAll(CONFIG.focusSelector);
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (!shouldMakeFocusable(node)) continue;
      if (!node.hasAttribute("tabindex")) node.setAttribute("tabindex", "0");
      if (!node.dataset.fmFocusKey) node.dataset.fmFocusKey = focusKey(node, i);
    }

    if (isTv()) {
      const inputs = document.querySelectorAll("input[type='text'],input[type='search'],input:not([type]),textarea");
      for (let i = 0; i < inputs.length; i++) makeTvInput(inputs[i]);
    }
  }

  function shouldMakeFocusable(node) {
    if (!node || node.disabled || node.getAttribute("aria-hidden") === "true") return false;
    if (node.matches("script,style,[hidden],.d-none")) return false;
    return true;
  }

  function makeTvInput(input) {
    if (input.dataset.fmDmInput === "1") return;
    input.dataset.fmDmInput = "1";
    input.setAttribute("tabindex", "0");
    input.setAttribute("readonly", "readonly");
    input.addEventListener("click", () => enterInput(input), true);
    input.addEventListener("focus", () => {
      if (!state.inputEditing) input.setAttribute("readonly", "readonly");
    });
    input.addEventListener("blur", () => exitInput(input));
  }

  function enterInput(input) {
    state.inputEditing = true;
    input.removeAttribute("readonly");
    setTimeout(() => input.focus(), 0);
  }

  function exitInput(input) {
    state.inputEditing = false;
    if (input) input.setAttribute("readonly", "readonly");
  }

  function focusKey(node, index) {
    if (node.dataset.fmDmUrl) return "url:" + node.dataset.fmDmUrl;
    if (node.id) return "id:" + node.id;
    const text = cleanText(node.getAttribute("aria-label") || node.getAttribute("title") || node.textContent).slice(0, 40);
    return node.tagName.toLowerCase() + ":" + index + ":" + text;
  }

  function focusableIn(scope) {
    const root = scope || document;
    const nodes = Array.prototype.slice.call(root.querySelectorAll(CONFIG.focusSelector + ",[tabindex='0']"));
    return nodes.filter(isVisibleFocusable);
  }

  function isVisibleFocusable(el) {
    if (!el || el.disabled || el.getAttribute("tabindex") === "-1") return false;
    for (let p = el; p && p !== document.documentElement; p = p.parentElement) {
      if (p.getAttribute && p.getAttribute("aria-hidden") === "true") return false;
      if (p.hidden) return false;
    }
    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return false;
    const style = window.getComputedStyle(el);
    return style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || 1) !== 0;
  }

  function activeScope() {
    const modal = document.querySelector(".custom-modal,.modal.show,.modal-overlay,.share-modal,.actor-details-overlay,.actor-details-modal");
    if (modal && isVisibleElement(modal)) return modal;
    const detail = document.querySelector(".resource-detail");
    return detail || document;
  }

  function isVisibleElement(el) {
    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return false;
    const style = window.getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden";
  }

  function restoreFocus() {
    if (!isTv()) return;
    const active = document.activeElement;
    if (isVisibleFocusable(active)) return;
    const scope = activeScope();
    const nodes = focusableIn(scope);
    if (!nodes.length) return;
    const previous = nodes.find((node) => node.dataset.fmFocusKey === state.lastFocusedKey);
    focusTarget(previous || nodes[0]);
  }

  function focusTarget(el) {
    if (!el || !isVisibleFocusable(el)) return false;
    state.lastFocusedKey = el.dataset.fmFocusKey || "";
    if (state.focusRaf) cancelAnimationFrame(state.focusRaf);
    state.focusRaf = requestAnimationFrame(() => {
      try {
        el.focus({ preventScroll: true });
      } catch (e) {
        el.focus();
      }
      scrollIntoViewSoon(el);
    });
    return true;
  }

  function scrollIntoViewSoon(el) {
    if (state.scrollRaf) cancelAnimationFrame(state.scrollRaf);
    state.scrollRaf = requestAnimationFrame(() => {
      try {
        el.scrollIntoView({ block: "nearest", inline: "nearest" });
      } catch (e) {
        el.scrollIntoView(false);
      }
    });
  }

  function nextByDirection(current, key) {
    const scope = activeScope();
    const nodes = focusableIn(scope);
    if (!nodes.length) return null;
    if (!current || !isVisibleFocusable(current) || nodes.indexOf(current) === -1) return nodes[0];

    const from = center(current.getBoundingClientRect());
    const horizontal = key === "ArrowLeft" || key === "ArrowRight";
    const positive = key === "ArrowRight" || key === "ArrowDown";
    let best = null;
    let bestScore = Infinity;

    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node === current) continue;
      const to = center(node.getBoundingClientRect());
      const dx = to.x - from.x;
      const dy = to.y - from.y;
      const primary = horizontal ? dx : dy;
      const secondary = Math.abs(horizontal ? dy : dx);
      if (positive ? primary <= 4 : primary >= -4) continue;
      const score = Math.abs(primary) * 1.4 + secondary * 2.2;
      if (score < bestScore) {
        best = node;
        bestScore = score;
      }
    }

    if (best) return best;
    const index = nodes.indexOf(current);
    if (key === "ArrowLeft" || key === "ArrowUp") return nodes[Math.max(0, index - 1)];
    return nodes[Math.min(nodes.length - 1, index + 1)];
  }

  function center(rect) {
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  }

  function onKeyDown(event) {
    if (!isTv()) return;
    const key = event.key;
    if (key === "Escape" || key === "Backspace") {
      if (state.inputEditing && isInput(document.activeElement)) {
        exitInput(document.activeElement);
        event.preventDefault();
        event.stopImmediatePropagation();
      }
      return;
    }
    if (key === "Enter" || key === " ") {
      const active = document.activeElement;
      if (isInput(active) && active.hasAttribute("readonly")) {
        enterInput(active);
        event.preventDefault();
        event.stopImmediatePropagation();
      }
      return;
    }
    if (!/^Arrow(Left|Right|Up|Down)$/.test(key)) return;
    if (state.inputEditing && isInput(document.activeElement)) return;
    const next = nextByDirection(document.activeElement, key);
    if (!next) return;
    event.preventDefault();
    event.stopPropagation();
    focusTarget(next);
  }

  function isInput(el) {
    return el && /^(INPUT|TEXTAREA)$/i.test(el.tagName);
  }

  function onFocusIn(event) {
    const el = event.target;
    if (!el || !el.classList) return;
    state.lastFocusedKey = el.dataset.fmFocusKey || state.lastFocusedKey;
    document.querySelectorAll("." + CONFIG.focusClass).forEach((node) => node.classList.remove(CONFIG.focusClass));
    el.classList.add(CONFIG.focusClass);
  }

  function onRouteChange() {
    if (state.lastPath === location.href) return;
    state.lastPath = location.href;
    scheduleEnhance();
  }

  function enhance() {
    document.documentElement.classList.add(CONFIG.rootClass);
    document.documentElement.classList.toggle(CONFIG.tvClass, isTv());
    enhanceHomeCards();
    ensureCustomMedia();
    enhanceResourceLinks();
    enhanceFocusables();
    restoreFocus();
  }

  function scheduleEnhance() {
    clearTimeout(scheduleEnhance.timer);
    scheduleEnhance.timer = setTimeout(enhance, CONFIG.scanDelay);
  }

  function installObserver() {
    const observer = new MutationObserver(scheduleEnhance);
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  function installStyle() {
    const css = `
      html.${CONFIG.rootClass} {
        scroll-padding: 72px;
      }
      html.${CONFIG.rootClass} .resource-card,
      html.${CONFIG.rootClass} .table-row,
      html.${CONFIG.rootClass} .tab-btn,
      html.${CONFIG.rootClass} .history-item,
      html.${CONFIG.rootClass} .stream-card {
        outline: none;
      }
      .${CONFIG.buttonClass} {
        margin-left: 10px;
        padding: 6px 12px;
        border: 1px solid #2563eb;
        border-radius: 6px;
        background: #2563eb;
        color: #fff;
        font-size: 13px;
        line-height: 1.2;
        white-space: nowrap;
      }
      .table-row .${CONFIG.buttonClass} {
        align-self: center;
      }
      .table-row.fm-dm-busy {
        opacity: .72;
      }
      .fm-dm-card-cta {
        display: none;
      }
      html.${CONFIG.tvClass} .${CONFIG.focusClass},
      html.${CONFIG.tvClass} a:focus,
      html.${CONFIG.tvClass} button:focus,
      html.${CONFIG.tvClass} input:focus,
      html.${CONFIG.tvClass} select:focus,
      html.${CONFIG.tvClass} textarea:focus,
      html.${CONFIG.tvClass} [tabindex='0']:focus {
        outline: 2px solid rgba(37, 99, 235, .95) !important;
        outline-offset: 3px !important;
        box-shadow: 0 0 0 5px rgba(37, 99, 235, .18) !important;
      }
      html.${CONFIG.tvClass} .resource-card:focus,
      html.${CONFIG.tvClass} .stream-card:focus,
      html.${CONFIG.tvClass} .history-item:focus {
        transform: translateY(-2px);
      }
      html.${CONFIG.tvClass} .table-row:focus {
        background: rgba(37, 99, 235, .08);
      }
      html.${CONFIG.tvClass} input[readonly],
      html.${CONFIG.tvClass} textarea[readonly] {
        cursor: default;
      }
      @media (min-width: 900px) {
        .${CONFIG.buttonClass} {
          padding: 8px 14px;
          font-size: 14px;
        }
      }
      @media (max-width: 768px) {
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}),
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) body {
          background: #f6f7fb !important;
          color: #111827 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) * {
          -webkit-tap-highlight-color: transparent;
          letter-spacing: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) a:focus,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) button:focus,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) input:focus,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) select:focus,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) textarea:focus,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) [tabindex='0']:focus {
          outline: none !important;
          box-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .main-content {
          padding: 8px 0 18px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .content-container,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .home-container {
          max-width: none !important;
          padding: 0 10px !important;
          animation: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .app-header {
          position: sticky !important;
          top: 0;
          z-index: 80;
          margin: 0 !important;
          border-radius: 0 !important;
          background: rgba(255, 255, 255, .96) !important;
          border-bottom: 1px solid #e5e7eb !important;
          box-shadow: none !important;
          backdrop-filter: blur(10px);
          -webkit-backdrop-filter: blur(10px);
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .header-inner {
          gap: 8px !important;
          padding: 8px 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .brand-link {
          max-width: 154px !important;
          color: #111827 !important;
          font-size: 18px !important;
          line-height: 1.2 !important;
          text-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .header-actions,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .button-group {
          gap: 6px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .btn-custom {
          min-width: 40px !important;
          min-height: 40px !important;
          padding: 0 10px !important;
          border-radius: 8px !important;
          box-shadow: none !important;
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .btn-custom:before,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .floating-btn:before,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .view-details-btn:before {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .header-search {
          flex: 1 0 100% !important;
          order: 3 !important;
          width: 100% !important;
          max-width: none !important;
          min-width: 0 !important;
          margin: 0 !important;
          padding: 0 10px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .header-search-container {
          width: 100% !important;
          max-width: none !important;
          margin: 0 !important;
          transition: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .header-search-container:hover {
          width: 100% !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-wrapper {
          width: 100% !important;
          margin-left: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-box {
          min-height: 44px !important;
          padding: 0 11px !important;
          border-radius: 8px !important;
          background: #fff !important;
          border: 1px solid #d8dee8 !important;
          box-shadow: none !important;
          animation: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-icon,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-wrapper.active .search-icon {
          margin-left: 0 !important;
          color: #64748b !important;
          text-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-input,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-wrapper.active .search-input {
          min-width: 0 !important;
          color: #111827 !important;
          font-size: 15px !important;
          text-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-input::placeholder {
          color: #94a3b8 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-results-popup {
          left: 0 !important;
          right: auto !important;
          width: 100% !important;
          max-height: min(420px, 70vh) !important;
          border-radius: 8px !important;
          box-shadow: 0 10px 26px rgba(15, 23, 42, .14) !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .hero-section {
          margin: 8px 0 10px !important;
          padding: 8px 0 !important;
          background: transparent !important;
          border: 0 !important;
          border-radius: 0 !important;
          box-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .search-results-title {
          justify-content: flex-start !important;
          margin: 0 0 10px !important;
          font-size: 17px !important;
          line-height: 1.35 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .sort-options {
          justify-content: flex-start !important;
          gap: 8px !important;
          margin: 0 !important;
          padding: 2px 0 4px !important;
          overflow-x: auto;
          scrollbar-width: none;
          -webkit-overflow-scrolling: touch;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .sort-options::-webkit-scrollbar {
          display: none;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .sort-btn {
          flex: 0 0 auto;
          min-height: 38px !important;
          padding: 0 13px !important;
          border-radius: 8px !important;
          background: #fff !important;
          border: 1px solid #e2e8f0 !important;
          color: #334155 !important;
          box-shadow: none !important;
          transform: none !important;
          font-size: 13px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .sort-btn.active {
          background: #0f766e !important;
          border-color: #0f766e !important;
          color: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-gallery {
          display: grid !important;
          grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
          gap: 10px !important;
          margin-top: 10px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-card {
          min-width: 0 !important;
          height: auto !important;
          perspective: none !important;
          transform: none !important;
          transition: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-card:hover {
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .card-inner {
          height: auto !important;
          min-height: 100% !important;
          transform: none !important;
          transform-style: flat !important;
          transition: none !important;
          border-radius: 8px !important;
          overflow: hidden !important;
          background: #fff !important;
          box-shadow: 0 3px 12px rgba(15, 23, 42, .08) !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-card:hover .card-inner {
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .card-front {
          position: relative !important;
          height: auto !important;
          min-height: 100% !important;
          border: 0 !important;
          border-radius: 8px !important;
          background: #fff !important;
          box-shadow: none !important;
          overflow: hidden !important;
          backface-visibility: visible !important;
          -webkit-backface-visibility: visible !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .card-back {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .image-wrapper {
          width: 100% !important;
          height: auto !important;
          aspect-ratio: 2 / 3 !important;
          background: #e5e7eb !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .image-wrapper:after {
          background: linear-gradient(to top, rgba(15, 23, 42, .62), rgba(15, 23, 42, .08) 48%, transparent 74%) !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .poster-image {
          width: 100% !important;
          height: 100% !important;
          object-fit: cover !important;
          filter: none !important;
          transform: none !important;
          transition: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-card:hover .poster-image {
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .tag-container {
          gap: 5px !important;
          padding: 8px !important;
          background: transparent !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-tag {
          max-width: 100%;
          min-height: 22px !important;
          padding: 0 6px !important;
          border: 0 !important;
          border-radius: 6px !important;
          background: rgba(239, 246, 255, .92) !important;
          color: #1d4ed8 !important;
          box-shadow: none !important;
          font-size: 11px !important;
          line-height: 22px !important;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-tag:nth-child(3n+2) {
          background: rgba(236, 253, 245, .92) !important;
          color: #047857 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-tag:nth-child(3n) {
          background: rgba(255, 247, 237, .92) !important;
          color: #c2410c !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .card-content {
          height: auto !important;
          min-height: 58px !important;
          padding: 8px 8px 9px !important;
          border-radius: 0 !important;
          background: #fff !important;
          box-shadow: none !important;
          justify-content: flex-start !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-title {
          gap: 3px !important;
          height: auto !important;
          min-width: 0 !important;
          justify-content: flex-start !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-cn,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-en {
          display: block !important;
          max-width: 100% !important;
          text-shadow: none !important;
          overflow: hidden !important;
          text-overflow: ellipsis !important;
          white-space: nowrap !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-cn {
          color: #111827 !important;
          font-size: 14px !important;
          font-weight: 800 !important;
          line-height: 1.35 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-en {
          color: #64748b !important;
          font-size: 12px !important;
          font-style: normal !important;
          line-height: 1.25 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .fm-dm-card-cta {
          position: absolute;
          right: 7px;
          bottom: 66px;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          min-height: 26px;
          padding: 0 8px;
          border-radius: 8px;
          background: rgba(15, 23, 42, .82);
          color: #fff;
          font-size: 12px;
          font-weight: 800;
          line-height: 1;
          pointer-events: none;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .pagination-container {
          margin-top: 16px !important;
          padding: 6px 0 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .pagination-wrapper {
          gap: 10px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .pagination-btn,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .page-btn {
          min-width: 40px !important;
          width: 40px !important;
          height: 40px !important;
          border-radius: 8px !important;
          box-shadow: none !important;
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .page-btn.active {
          background: #2563eb !important;
          color: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .page-size-control {
          width: 100%;
          justify-content: center;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail {
          padding: 0 12px 20px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .resource-header {
          margin: 10px 0 12px !important;
          padding: 0 !important;
          border: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          box-shadow: none !important;
          overflow: visible !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .title-wrapper {
          display: flex !important;
          align-items: center !important;
          gap: 8px !important;
          flex-wrap: wrap !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .title {
          flex: 1 1 auto;
          min-width: 0 !important;
          margin: 0 !important;
          color: #0f172a !important;
          font-size: 20px !important;
          line-height: 1.25 !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .subtitle {
          margin: 3px 0 0 !important;
          color: #64748b !important;
          font-size: 13px !important;
          line-height: 1.3 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .resource-type-badge {
          flex: 0 0 auto;
          min-height: 22px !important;
          padding: 0 7px !important;
          border-radius: 6px !important;
          background: #dcfce7 !important;
          color: #047857 !important;
          font-size: 11px !important;
          line-height: 22px !important;
          white-space: nowrap !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .action-buttons {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .resource-content {
          display: grid !important;
          grid-template-columns: 132px minmax(0, 1fr) !important;
          gap: 12px !important;
          align-items: start !important;
          margin: 0 !important;
          padding: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-media {
          grid-column: 1 !important;
          display: block !important;
          min-width: 0 !important;
          margin: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-poster {
          width: 132px !important;
          aspect-ratio: 2 / 3 !important;
          border-radius: 10px !important;
          background: #e5e7eb !important;
          overflow: hidden !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-poster img {
          display: block !important;
          width: 100% !important;
          height: 100% !important;
          object-fit: cover !important;
          object-position: center center !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-stills {
          grid-column: 1 / -1 !important;
          display: flex !important;
          gap: 8px !important;
          width: 100% !important;
          min-width: 0 !important;
          margin: 2px 0 0 !important;
          overflow-x: auto;
          scrollbar-width: none;
          -webkit-overflow-scrolling: touch;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-stills::-webkit-scrollbar {
          display: none;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-still {
          flex: 0 0 min(68vw, 260px);
          aspect-ratio: 16 / 9 !important;
          border-radius: 8px !important;
          background: #e5e7eb !important;
          overflow: hidden !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-still img {
          display: block !important;
          width: 100% !important;
          height: 100% !important;
          object-fit: cover !important;
          object-position: center center !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .media-section {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .info-section {
          display: contents !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .main-image-container {
          width: 132px !important;
          height: auto !important;
          aspect-ratio: 2 / 3 !important;
          border-radius: 10px !important;
          background: #e5e7eb !important;
          box-shadow: none !important;
          overflow: hidden !important;
          position: relative !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .resource-poster,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .main-image,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .lazy-image {
          display: block !important;
          width: 100% !important;
          height: 100% !important;
          min-height: 0 !important;
          border: 0 !important;
          border-radius: 10px !important;
          object-fit: cover !important;
          object-position: center center !important;
          filter: none !important;
          transform: none !important;
          transition: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .image-navigation,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .nav-button,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .image-nav-button,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .thumbnails-container,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .thumbnails-scroll,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .thumbnail {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card {
          grid-column: 2 !important;
          align-self: stretch !important;
          margin: 0 !important;
          padding: 0 !important;
          border: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          box-shadow: none !important;
          overflow: visible !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card .card-header {
          min-height: auto !important;
          padding: 0 0 7px !important;
          background: transparent !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card .card-header h3 {
          display: inline-flex !important;
          align-items: center !important;
          gap: 7px !important;
          margin: 0 !important;
          color: #0f172a !important;
          font-size: 16px !important;
          line-height: 1.2 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card .card-header h3:before,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-card .card-header h3:before {
          content: "";
          width: 3px;
          height: 16px;
          border-radius: 999px;
          background: #0f766e;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card .card-body {
          padding: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card p {
          display: -webkit-box !important;
          -webkit-box-orient: vertical;
          -webkit-line-clamp: 8;
          overflow: hidden !important;
          margin: 0 !important;
          color: #475569 !important;
          font-size: 14px !important;
          line-height: 1.62 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-card {
          grid-column: 1 / -1 !important;
          margin: 16px 0 0 !important;
          border: 0 !important;
          border-radius: 10px !important;
          background: #fff !important;
          box-shadow: 0 1px 5px rgba(15, 23, 42, .06) !important;
          overflow: hidden !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-card .card-header {
          display: flex !important;
          align-items: center !important;
          justify-content: space-between !important;
          gap: 10px !important;
          min-height: 54px !important;
          padding: 12px 12px 8px !important;
          background: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-card .card-header h3 {
          display: inline-flex !important;
          align-items: center !important;
          gap: 8px !important;
          margin: 0 !important;
          color: #0f172a !important;
          font-size: 17px !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-card .header-actions {
          display: flex !important;
          flex: 0 0 auto !important;
          gap: 8px !important;
          margin: 0 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .pan-search-button,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .stream-button {
          min-width: 64px !important;
          min-height: 42px !important;
          padding: 0 10px !important;
          border: 0 !important;
          border-radius: 8px !important;
          box-shadow: none !important;
          transform: none !important;
          font-size: 13px !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .pan-search-button {
          background: #334155 !important;
          color: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .stream-button {
          background: #0f766e !important;
          color: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-content {
          padding: 0 12px 12px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-tabs {
          display: flex !important;
          justify-content: center !important;
          gap: 8px !important;
          margin: 0 0 10px !important;
          padding: 0 !important;
          background: transparent !important;
          overflow-x: auto;
          scrollbar-width: none;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-tabs .tab-btn {
          min-height: 40px !important;
          padding: 0 14px !important;
          border: 0 !important;
          border-radius: 8px !important;
          background: #f1f5f9 !important;
          color: #334155 !important;
          box-shadow: none !important;
          font-size: 13px !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .links-tabs .tab-btn.active {
          background: #0f766e !important;
          color: #fff !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .table-body {
          display: grid !important;
          gap: 10px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .table-row {
          display: grid !important;
          grid-template-columns: minmax(0, 1fr) auto !important;
          gap: 8px 10px !important;
          align-items: start !important;
          padding: 12px !important;
          border: 1px solid #edf2f7 !important;
          border-radius: 10px !important;
          background: #fff !important;
          box-shadow: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .col-link {
          display: block !important;
          grid-column: 1 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .link-url {
          display: -webkit-box !important;
          -webkit-box-orient: vertical;
          -webkit-line-clamp: 2;
          overflow: hidden !important;
          color: #2563eb !important;
          font-size: 14px !important;
          line-height: 1.45 !important;
          text-decoration: none !important;
          word-break: break-word;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .${CONFIG.buttonClass} {
          grid-column: 2 !important;
          min-width: 82px !important;
          min-height: 46px !important;
          margin: 0 !important;
          padding: 0 12px !important;
          border: 0 !important;
          border-radius: 8px !important;
          background: #0f766e !important;
          color: #fff !important;
          box-shadow: none !important;
          font-size: 14px !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .col-password,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .col-note {
          grid-column: 1 / -1 !important;
          display: grid !important;
          grid-template-columns: 52px minmax(0, 1fr) !important;
          gap: 8px !important;
          align-items: center !important;
          color: #64748b !important;
          font-size: 12px !important;
          line-height: 1.4 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .col-password:before,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .col-note:before {
          color: #94a3b8 !important;
          font-size: 12px !important;
          font-weight: 800 !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .password-container,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .note-text {
          color: #64748b !important;
          overflow-wrap: anywhere;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .floating-buttons {
          display: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .stream-button,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .pan-search-button,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .view-details-btn {
          min-height: 44px !important;
          border-radius: 8px !important;
          box-shadow: none !important;
          transform: none !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .app-footer {
          margin: 14px 0 0 !important;
          border-radius: 0 !important;
          box-shadow: none !important;
          background: transparent !important;
          border: 0 !important;
        }
      }
      @media (max-width: 380px) {
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .content-container,
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .home-container {
          padding: 0 8px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-gallery {
          gap: 8px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .card-content {
          min-height: 56px !important;
          padding: 7px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-cn {
          font-size: 13px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .title-en {
          font-size: 11px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .fm-dm-card-cta {
          bottom: 62px;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .resource-content {
          grid-template-columns: 116px minmax(0, 1fr) !important;
          gap: 10px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .main-image-container {
          width: 116px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .fm-dm-poster {
          width: 116px !important;
        }
        html.${CONFIG.rootClass}:not(.${CONFIG.tvClass}) .resource-detail .description-card p {
          -webkit-line-clamp: 7;
          font-size: 13px !important;
          line-height: 1.55 !important;
        }
      }
    `;
    if (typeof GM_addStyle === "function") {
      GM_addStyle(css);
      return;
    }
    const style = document.createElement("style");
    style.textContent = css;
    document.head.appendChild(style);
  }

  ready(() => {
    installStyle();
    enhance();
    installObserver();
    document.addEventListener("click", interceptLinkClick, true);
    document.addEventListener("keydown", onKeyDown, true);
    document.addEventListener("focusin", onFocusIn, true);
    window.addEventListener("fmsdk", scheduleEnhance);
    window.addEventListener("fmurlchange", onRouteChange);
    window.addEventListener("popstate", scheduleEnhance);
    setInterval(onRouteChange, 500);
    log("installed");
  });
})();
