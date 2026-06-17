// WebHome page analyzer.
// Run this in the debug workbench to collect candidates for semi-automatic script generation.
// It does not send data anywhere. Copy the returned JSON into an AI or generator after review.
(function () {
  const MAX_TEXT = 160;
  const MAX_ATTR = 800;
  const MAX_ITEMS = 300;

  const RESOURCE_PATTERNS = [
    ["magnet", /^magnet:/i],
    ["ed2k", /^ed2k:/i],
    ["thunder", /^thunder:/i],
    ["quark", /pan\.quark\.cn/i],
    ["aliyun", /aliyundrive\.com|alipan\.com/i],
    ["baidu", /pan\.baidu\.com/i],
    ["uc", /drive\.uc\.cn/i],
    ["xunlei", /pan\.xunlei\.com/i],
    ["tianyi", /cloud\.189\.cn/i],
    ["123", /123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i],
    ["115", /115\.com|115cdn\.com/i],
    ["mobile", /yun\.139\.com|caiyun\.139\.com/i],
    ["media", /\.(m3u8|mp4|mkv|flv|mov|avi|webm|mp3|aac|m4a)(\?|#|$)/i]
  ];

  const ACTION_TEXT = /播放|下载|資源|资源|网盘|網盤|磁力|复制|複製|打开|打開|play|download|copy|source|link/i;
  const ATTRS = [
    "id",
    "class",
    "href",
    "src",
    "title",
    "alt",
    "role",
    "type",
    "onclick",
    "data-url",
    "data-href",
    "data-link",
    "data-src",
    "data-id",
    "data-title",
    "data-clipboard-text"
  ];

  function text(el) {
    return String(el.innerText || el.textContent || "").trim().replace(/\s+/g, " ").slice(0, MAX_TEXT);
  }

  function attr(el, name) {
    const value = el.getAttribute(name);
    return value ? String(value).trim().slice(0, MAX_ATTR) : "";
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

  function classify(value) {
    const url = normalizeUrl(value);
    if (!url) return "";
    for (const item of RESOURCE_PATTERNS) {
      if (item[1].test(url)) return item[0];
    }
    return /^https?:/i.test(url) ? "http" : "";
  }

  function selector(el) {
    if (!el || el.nodeType !== 1) return "";
    if (el.id) return "#" + cssEscape(el.id);
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && parts.length < 5) {
      let part = node.tagName.toLowerCase();
      const cls = classNames(node).slice(0, 3);
      if (cls.length) part += "." + cls.map(cssEscape).join(".");
      const parent = node.parentElement;
      if (parent) {
        const same = Array.prototype.filter.call(parent.children, (child) => child.tagName === node.tagName);
        if (same.length > 1) part += ":nth-of-type(" + (same.indexOf(node) + 1) + ")";
      }
      parts.unshift(part);
      node = parent;
      if (node && (node.id || node === document.body)) {
        if (node.id) parts.unshift("#" + cssEscape(node.id));
        break;
      }
    }
    return parts.join(" > ");
  }

  function classNames(el) {
    return String(el.className || "").split(/\s+/).filter(Boolean).filter((name) => !/^\d/.test(name));
  }

  function cssEscape(value) {
    if (window.CSS && CSS.escape) return CSS.escape(value);
    return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
  }

  function candidateUrl(attrs) {
    const keys = ["data-url", "data-href", "data-link", "data-clipboard-text", "href", "src", "onclick"];
    for (const key of keys) {
      const value = attrs[key] || "";
      if (!value) continue;
      const direct = normalizeUrl(value);
      if (classify(direct)) return direct;
      const embedded = value.match(/(magnet:\?[^'"<>\s]+|ed2k:\/\/[^'"<>\s]+|thunder:\/\/[^'"<>\s]+|https?:\/\/[^'"<>\s]+)/i);
      if (embedded && classify(embedded[1])) return normalizeUrl(embedded[1]);
    }
    return "";
  }

  function elementInfo(el, index) {
    const attrs = {};
    ATTRS.forEach((name) => {
      const value = attr(el, name);
      if (value) attrs[name] = value;
    });
    const url = candidateUrl(attrs);
    const type = classify(url);
    const label = text(el);
    const card = el.closest("[data-title],.card,.item,.vod,.video,.download-item,.resource-item,li,article");
    return {
      index,
      tag: el.tagName.toLowerCase(),
      selector: selector(el),
      text: label,
      attrs,
      url,
      type,
      actionLike: ACTION_TEXT.test(label),
      cardSelector: card && card !== el ? selector(card) : "",
      cardText: card && card !== el ? text(card) : ""
    };
  }

  function collectCandidates() {
    const nodes = document.querySelectorAll("a,button,[data-url],[data-href],[data-link],[data-clipboard-text],video,audio,source");
    const result = [];
    nodes.forEach((el, index) => {
      const info = elementInfo(el, index);
      if (info.url || info.actionLike || info.tag === "video" || info.tag === "audio" || info.tag === "source") result.push(info);
    });
    return result.slice(0, MAX_ITEMS);
  }

  function collectHeadings() {
    return Array.prototype.slice.call(document.querySelectorAll("h1,h2,h3,.title,.name,[data-title]"), 0, 80).map((el) => ({
      selector: selector(el),
      text: text(el),
      title: attr(el, "title") || attr(el, "data-title")
    })).filter((item) => item.text || item.title);
  }

  function collectMedia() {
    const media = [];
    document.querySelectorAll("video,audio,source").forEach((el) => {
      const url = normalizeUrl(el.currentSrc || el.src || attr(el, "src"));
      if (url) media.push({ tag: el.tagName.toLowerCase(), selector: selector(el), url, type: classify(url) });
    });
    if (performance && performance.getEntriesByType) {
      performance.getEntriesByType("resource").forEach((entry) => {
        const type = classify(entry.name);
        if (type) media.push({ tag: "performance", initiatorType: entry.initiatorType, url: entry.name, type });
      });
    }
    return media.slice(0, MAX_ITEMS);
  }

  function guessSelectors(candidates) {
    const resourceItems = candidates.filter((item) => item.url && item.type && item.selector);
    const byClass = {};
    resourceItems.forEach((item) => {
      const classes = item.attrs.class ? item.attrs.class.split(/\s+/).filter(Boolean) : [];
      classes.forEach((name) => {
        if (name.length > 2) byClass["." + name] = (byClass["." + name] || 0) + 1;
      });
    });
    const selectors = Object.keys(byClass).sort((a, b) => byClass[b] - byClass[a]).slice(0, 12);
    return {
      actionSelectors: selectors,
      urlAttributes: ["data-url", "data-href", "data-link", "data-clipboard-text", "href"],
      titleSelectors: ["h1", "h2", ".title", ".name"].filter((sel) => document.querySelector(sel))
    };
  }

  const candidates = collectCandidates();
  return {
    url: location.href,
    origin: location.origin,
    title: document.title,
    readyState: document.readyState,
    site: window.fm && fm.site ? "call fm.site() in console for native site key" : "",
    headings: collectHeadings(),
    candidates,
    media: collectMedia(),
    guesses: guessSelectors(candidates)
  };
})();
