// WebHome extension for Gying/观影 original website.
// Strategy:
// - Keep the original site in the WebView for login, verification, search and navigation.
// - Use the domain configured by the user in WebHome homePage; do not redirect between domains.
// - On search/detail pages, read current-origin Gying data and route pan/magnet resources to the App.
(function () {
  "use strict";

  var CONFIG = {
    id: "gying-native-router",
    name: "gying",
    entryHosts: [
      "xn--ykq321c.com",
      "www.xn--ykq321c.com"
    ],
    urlopHosts: [
      "gying.net",
      "www.gying.net"
    ],
    maxSearchItems: 48,
    scanDelay: 160,
    rootId: "fm-gying-root",
    onlineTriggerSelector: [
      "a",
      "button",
      "[role='button']",
      "[onclick]",
      "[data-url]",
      "[data-href]",
      "[data-link]",
      ".btn",
      ".button",
      ".van-button",
      "[class*='play']",
      "[class*='Play']"
    ].join(","),
    directResourceSelector: [
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
      "a[href*='123pan']",
      "a[href*='123684.']",
      "a[href*='123685.']",
      "a[href*='123865.']",
      "a[href*='123912.']",
      "a[href*='115.com']",
      "a[href*='yun.139.com']",
      "a[href*='.m3u8']",
      "a[href*='.mp4']",
      "a[href*='.mkv']",
      "a[href*='.mpd']",
      "a[href*='/py/']",
      "a[href*='play']",
      "a[href*='/play/']",
      "a[href*='/player/']",
      "a[href*='/vodplay/']",
      "a[href*='/video/']",
      "[onclick*='/py/']",
      "[onclick*='play']",
      "[onclick*='/play/']",
      "[onclick*='/player/']",
      "[onclick*='/vodplay/']",
      "[onclick*='/video/']",
      "[data-url*='/py/']",
      "[data-href*='/py/']",
      "[data-link*='/py/']",
      "[data-url*='play']",
      "[data-href*='play']",
      "[data-link*='play']",
      "[data-url*='/play/']",
      "[data-href*='/play/']",
      "[data-link*='/play/']",
      "a[data-url]",
      "[data-clipboard-text]"
    ].join(",")
  };

  var TYPE_CODE = {
    0: "xunlei",
    1: "baidu",
    2: "quark",
    3: "tianyi",
    4: "mobile",
    5: "115",
    6: "123",
    7: "uc",
    8: "aliyun"
  };

  var TYPE_LABEL = {
    quark: "夸克",
    aliyun: "阿里",
    baidu: "百度",
    uc: "UC",
    xunlei: "迅雷",
    tianyi: "天翼",
    "123": "123",
    "115": "115",
    mobile: "移动云",
    magnet: "磁力",
    ed2k: "电驴",
    thunder: "迅雷",
    media: "直链",
    online: "在线播放",
    http: "网页"
  };

  var state = {
    scanTimer: 0,
    searchItems: [],
    links: [],
    activeTitle: "",
    activePic: "",
    activeWallPic: "",
    panelMode: "idle",
    busy: false,
    mediaHooksInstalled: false,
    lastMediaUrl: "",
    lastMediaReferer: "",
    autoPlayTimer: 0,
    autoPlayedUrl: "",
    autoTriedUrl: "",
    autoIntentTime: 0,
    lastRoutedOriginalUrl: "",
    lastRoutedOriginalAt: 0,
    safeChromeMode: "",
    safeSystemBarsHidden: false
  };

  function log() {
    var args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else if (window.console && console.log) console.log.apply(console, ["[gying-native]"].concat(args));
  }

  function ready(fn) {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fn, { once: true });
    else fn();
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise(function (resolve) {
      window.addEventListener("fmsdk", function () { resolve(window.fm); }, { once: true });
    });
  }

  function toast(message) {
    if (window.fm && window.fm.ext && window.fm.ext.toast) {
      window.fm.ext.toast(message).catch(function () {});
    } else {
      log(message);
    }
  }

  function cleanText(value) {
    return String(value || "").replace(/\s+/g, " ").replace(/^\s+|\s+$/g, "");
  }

  function hostName() {
    return String(location.hostname || "").toLowerCase();
  }

  function hasValue(list, value) {
    for (var i = 0; i < list.length; i++) {
      if (list[i] === value) return true;
    }
    return false;
  }

  function getAttr(el, name) {
    return el && el.getAttribute ? cleanText(el.getAttribute(name)) : "";
  }

  function matchesSelector(el, selector) {
    if (!el || el.nodeType !== 1) return false;
    var fn = el.matches || el.msMatchesSelector || el.webkitMatchesSelector;
    if (!fn) return false;
    try {
      return fn.call(el, selector);
    } catch (e) {
      return false;
    }
  }

  function closest(el, selector) {
    var node = el;
    while (node && node.nodeType === 1) {
      if (matchesSelector(node, selector)) return node;
      node = node.parentElement;
    }
    return null;
  }

  function queryAll(selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  }

  function absoluteUrl(value) {
    var text = cleanText(value);
    if (!text || text === "#" || /^javascript:/i.test(text)) return "";
    if (/^(magnet:|ed2k:|thunder:)/i.test(text)) return text;
    return absolutizeUrl(text, location.href);
  }

  function absolutizeUrl(value, base) {
    var text = cleanText(value);
    if (!text || text === "#" || /^javascript:/i.test(text)) return "";
    if (/^(magnet:|ed2k:|thunder:)/i.test(text)) return text;
    try {
      return new URL(text, base || location.href).href;
    } catch (e) {
      var a = document.createElement("a");
      a.href = text;
      return a.href || text;
    }
  }

  function requestText(url, options) {
    var opts = options || {};
    if (window.fm && window.fm.req) {
      return window.fm.req(url, {
        method: opts.method || "GET",
        headers: opts.headers || {},
        body: opts.body || "",
        responseType: "text",
        timeout: opts.timeout || 20,
        credentials: opts.credentials || "include"
      }).then(function (resp) {
        return resp || { ok: false, status: 500, body: "", error: "empty response" };
      });
    }
    if (!window.fetch) {
      return Promise.resolve({ ok: false, status: 500, body: "", error: "fetch unavailable" });
    }
    var fetchOpts = {
      method: opts.method || "GET",
      headers: opts.headers || {},
      credentials: "include"
    };
    if (opts.body && fetchOpts.method !== "GET" && fetchOpts.method !== "HEAD") fetchOpts.body = opts.body;
    return window.fetch(url, fetchOpts).then(function (resp) {
      return resp.text().then(function (text) {
        return { ok: resp.ok, status: resp.status, url: resp.url, body: text, error: resp.ok ? "" : "HTTP " + resp.status };
      });
    }).catch(function (err) {
      return { ok: false, status: 500, body: "", error: err && err.message ? err.message : String(err) };
    });
  }

  function isChallengeText(text) {
    text = String(text || "");
    return text.indexOf("浏览器安全验证") >= 0 ||
      text.indexOf("正在进行浏览器计算验证") >= 0 ||
      text.indexOf("正在确认你是不是机器人") >= 0;
  }

  function isLoginText(text) {
    text = String(text || "");
    return text.indexOf("_BT.PC.HTML('login')") >= 0 ||
      text.indexOf('_BT.PC.HTML("login")') >= 0 ||
      text.indexOf("_BT.PC.HTML('nologin')") >= 0 ||
      text.indexOf('_BT.PC.HTML("nologin")') >= 0 ||
      text.indexOf("未登录，访问受限") >= 0;
  }

  function maybeShowDomainNotice() {
    var host = hostName();
    var path = location.pathname || "/";
    var isEntry = hasValue(CONFIG.entryHosts, host);
    var isUrlop = hasValue(CONFIG.urlopHosts, host) && path.indexOf("/urlop") === 0;
    var looksLikeJsonList = /^\s*\{/.test(document.body ? document.body.textContent || "" : "") &&
      (document.body ? (document.body.textContent || "").indexOf('"host"') >= 0 : false);
    if (!isEntry && !isUrlop && !looksLikeJsonList) return false;

    renderNotice("当前是观影域名发布页或验证入口，不作为 WebHome 主站使用。请把真实可用的观影原站域名填到站点 homePage 后再进入。", "warn");
    return true;
  }

  function currentTitle() {
    var selectors = ["h1", "h2", ".title", ".name", ".vod-title", ".detail-title"];
    for (var i = 0; i < selectors.length; i++) {
      var el = document.querySelector(selectors[i]);
      var text = cleanText(el && el.textContent);
      if (text) return text;
    }
    return cleanText(document.title).replace(/[-_].*$/, "") || "Gying";
  }

  function currentArtwork() {
    var pic = "";
    var wallPic = "";
    var meta = document.querySelector("meta[property='og:image'],meta[name='og:image']");
    if (meta) pic = absoluteUrl(getAttr(meta, "content"));
    if (!pic) {
      var img = document.querySelector(".poster img,.cover img,.pic img,.thumb img,.vod-pic img,img");
      if (img) pic = absoluteUrl(getAttr(img, "src") || getAttr(img, "data-src"));
    }
    var bg = document.querySelector("[data-backdrop],[data-wallpic],[data-wall-pic]");
    if (bg) wallPic = absoluteUrl(getAttr(bg, "data-backdrop") || getAttr(bg, "data-wallpic") || getAttr(bg, "data-wall-pic"));
    return { pic: pic, wallPic: wallPic };
  }

  function parseSearchDataFromPage() {
    if (window._obj && window._obj.search) return window._obj.search;
    var scripts = document.scripts || [];
    for (var i = 0; i < scripts.length; i++) {
      var text = scripts[i].textContent || "";
      if (text.indexOf("_obj.search") < 0 && text.indexOf("_obj . search") < 0) continue;
      var match = text.match(/_obj\s*\.\s*search\s*=\s*(\{[\s\S]*?\})\s*;/);
      if (match && match[1]) {
        try { return JSON.parse(match[1]); } catch (e) {}
      }
    }
    return null;
  }

  function safeList(obj, name) {
    return obj && obj[name] && obj[name].length ? obj[name] : [];
  }

  function buildSearchItems(data) {
    var list = data && data.l ? data.l : {};
    var titles = safeList(list, "title");
    var years = safeList(list, "year");
    var types = safeList(list, "d");
    var ids = safeList(list, "i");
    var infos = safeList(list, "info");
    var directors = safeList(list, "daoyan");
    var actors = safeList(list, "zhuyan");
    var items = [];
    for (var i = 0; i < ids.length && items.length < CONFIG.maxSearchItems; i++) {
      var title = cleanText(titles[i]);
      if (!title || !types[i] || !ids[i]) continue;
      var year = Number(years[i]) || 0;
      var parts = [];
      if (infos[i]) parts.push(cleanText(infos[i]));
      if (directors[i]) parts.push("导演: " + cleanText(directors[i]));
      if (actors[i]) parts.push("主演: " + cleanText(actors[i]));
      items.push({
        resourceType: String(types[i]),
        resourceId: String(ids[i]),
        title: year > 0 ? title + "（" + year + "）" : title,
        rawTitle: title,
        year: year,
        content: parts.join(" | ")
      });
    }
    return items;
  }

  function detailFromPath() {
    var match = location.pathname.match(/^\/(mv|tv|ac)\/([A-Za-z0-9_-]+)/);
    if (!match) return null;
    return {
      resourceType: match[1],
      resourceId: match[2],
      title: currentTitle(),
      content: ""
    };
  }

  function detailUrl(item) {
    return location.origin + "/res/downurl/" + encodeURIComponent(item.resourceType) + "/" + encodeURIComponent(item.resourceId);
  }

  function fetchDetail(item) {
    if (!item || !item.resourceType || !item.resourceId) return;
    state.busy = true;
    state.activeTitle = item.title || currentTitle();
    var art = currentArtwork();
    state.activePic = art.pic;
    state.activeWallPic = art.wallPic;
    renderLoading("正在读取资源：" + state.activeTitle);
    requestText(detailUrl(item), {
      method: "GET",
      headers: { Referer: location.href },
      timeout: 20,
      credentials: "include"
    }).then(function (resp) {
      state.busy = false;
      var text = String(resp && resp.body || "");
      if (!resp || !resp.ok || resp.status === 403) {
        renderNotice("详情接口不可用：" + (resp && (resp.error || ("HTTP " + resp.status)) || "unknown"), "error");
        return;
      }
      if (isChallengeText(text)) {
        hidePanel();
        return;
      }
      if (isLoginText(text)) {
        renderNotice("当前账号未登录或登录失效。请使用原站登录后再读取资源。", "warn");
        return;
      }
      var detail;
      try {
        detail = JSON.parse(text);
      } catch (e) {
        renderNotice("详情 JSON 解析失败，可能是站点结构变化。", "error");
        return;
      }
      if (!detail || detail.code === 403) {
        renderNotice("详情返回未登录，请在原站登录后重试。", "warn");
        return;
      }
      state.links = extractLinks(detail, state.activeTitle);
      preloadArtwork();
      renderLinks(state.links, state.activeTitle);
    }).catch(function (err) {
      state.busy = false;
      renderNotice("读取详情失败：" + (err && err.message ? err.message : String(err)), "error");
    });
  }

  function extractLinks(detail, title) {
    var links = [];
    var seen = {};
    extractPanLinks(detail, title, links, seen);
    extractMagnetLinks(detail, title, links, seen);
    return links;
  }

  function extractPanLinks(detail, title, links, seen) {
    var pan = detail && detail.panlist ? detail.panlist : {};
    var urls = safeList(pan, "url");
    var names = safeList(pan, "name");
    var passwords = safeList(pan, "p");
    var types = safeList(pan, "type");
    var typeNames = safeList(pan, "tname");
    var times = safeList(pan, "time");
    for (var i = 0; i < urls.length; i++) {
      var raw = cleanText(urls[i]);
      var typeCode = Number(types[i]);
      var typeName = cleanText(typeNames[typeCode]) || cleanText(typeNames[i]);
      var linkType = classifyResource(raw, typeCode, typeName);
      var normalized = normalizePanUrl(raw, linkType);
      if (!normalized || linkType === "others") continue;
      var key = linkType + ":" + normalized.toLowerCase();
      if (seen[key]) continue;
      seen[key] = true;
      links.push({
        type: linkType,
        url: normalized,
        password: extractPassword(raw, passwords[i]),
        title: buildWorkTitle(title, names[i]),
        time: cleanText(times[i])
      });
    }
  }

  function extractMagnetLinks(detail, title, links, seen) {
    var down = detail && detail.downlist && detail.downlist.list ? detail.downlist.list : {};
    var hashes = safeList(down, "m");
    var names = safeList(down, "t");
    var sizes = safeList(down, "s");
    var times = safeList(down, "n");
    for (var i = 0; i < hashes.length; i++) {
      var hash = cleanText(hashes[i]).toLowerCase();
      if (!/^[a-f0-9]{40}$/.test(hash)) continue;
      var key = "magnet:" + hash;
      if (seen[key]) continue;
      seen[key] = true;
      var resourceName = cleanText(names[i]) || cleanText(sizes[i]);
      var magnet = "magnet:?xt=urn:btih:" + hash;
      if (resourceName) magnet += "&dn=" + encodeURIComponent(resourceName);
      links.push({
        type: "magnet",
        url: magnet,
        password: "",
        title: buildWorkTitle(title, resourceName),
        time: cleanText(times[i])
      });
    }
  }

  function classifyResource(url, typeCode, typeName) {
    var lower = cleanText(url).toLowerCase();
    var name = cleanText(typeName).toLowerCase();
    if (/^magnet:/i.test(lower)) return "magnet";
    if (/^ed2k:/i.test(lower)) return "ed2k";
    if (/^thunder:/i.test(lower)) return "thunder";
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(lower)) return "media";
    if (lower.indexOf("pan.quark.cn") >= 0) return "quark";
    if (lower.indexOf("aliyundrive.com") >= 0 || lower.indexOf("alipan.com") >= 0) return "aliyun";
    if (lower.indexOf("pan.baidu.com") >= 0) return "baidu";
    if (lower.indexOf("drive.uc.cn") >= 0) return "uc";
    if (lower.indexOf("pan.xunlei.com") >= 0) return "xunlei";
    if (lower.indexOf("cloud.189.cn") >= 0 || lower.indexOf("content.21cn.com") >= 0 || lower.indexOf("tianyi.cloud") >= 0) return "tianyi";
    if (lower.indexOf("123pan.") >= 0 || lower.indexOf("123684.") >= 0 || lower.indexOf("123685.") >= 0 || lower.indexOf("123912.") >= 0 || lower.indexOf("123592.") >= 0 || lower.indexOf("123865.") >= 0) return "123";
    if (lower.indexOf("115.com") >= 0 || lower.indexOf("115cdn.com") >= 0 || lower.indexOf("anxia.com") >= 0) return "115";
    if (lower.indexOf("yun.139.com") >= 0 || lower.indexOf("caiyun.139.com") >= 0 || lower.indexOf("feixin.10086.cn") >= 0) return "mobile";
    if (isOnlinePageUrl(lower)) return "online";
    if (TYPE_CODE[typeCode]) return TYPE_CODE[typeCode];
    if (name.indexOf("夸克") >= 0) return "quark";
    if (name.indexOf("阿里") >= 0) return "aliyun";
    if (name.indexOf("百度") >= 0) return "baidu";
    if (name.indexOf("迅雷") >= 0) return "xunlei";
    if (name.indexOf("天翼") >= 0) return "tianyi";
    if (name.indexOf("移动") >= 0 || name.indexOf("彩云") >= 0) return "mobile";
    if (name.indexOf("115") >= 0) return "115";
    if (name.indexOf("123") >= 0) return "123";
    if (name.indexOf("uc") >= 0) return "uc";
    return "others";
  }

  function isOnlinePageUrl(url) {
    var text = cleanText(url).toLowerCase();
    if (!/^https?:\/\//.test(text)) return false;
    try {
      var parsed = new URL(text, location.href);
      if (parsed.origin !== location.origin) return false;
      return /\/(py|play|player|vodplay|video)\//i.test(parsed.pathname);
    } catch (e) {
      return false;
    }
  }

  function firstMatch(text, regex) {
    var match = String(text || "").match(regex);
    return match ? match[0] : "";
  }

  function normalizePanUrl(raw, type) {
    raw = String(raw || "").replace(/[（(]\s*访问码[:：]\s*[^)）]+[)）]/g, "");
    raw = cleanText(raw);
    if (type === "baidu") return firstMatch(raw, /https?:\/\/pan\.baidu\.com\/s\/[a-zA-Z0-9_-]+(?:\?pwd=[a-zA-Z0-9]{4})?/);
    if (type === "quark") return firstMatch(raw, /https?:\/\/pan\.quark\.cn\/s\/[a-zA-Z0-9]+/);
    if (type === "aliyun") return firstMatch(raw, /https?:\/\/(?:www\.)?(?:alipan|aliyundrive)\.com\/s\/[a-zA-Z0-9]+/);
    if (type === "xunlei") return firstMatch(raw, /https?:\/\/pan\.xunlei\.com\/s\/[a-zA-Z0-9]+(?:\?pwd=[a-zA-Z0-9]{4})?/);
    if (type === "tianyi") {
      var code = extractTianyiCode(raw);
      if (code) return "https://cloud.189.cn/t/" + code;
      return firstMatch(raw, /https?:\/\/cloud\.189\.cn\/(?:t\/|web\/share\?code=)[a-zA-Z0-9]+/) ||
        firstMatch(raw, /https?:\/\/(?:www\.)?tianyi\.cloud\/[^\s<>"']+/);
    }
    if (type === "mobile") {
      return firstMatch(raw, /https?:\/\/yun\.139\.com\/shareweb\/#\/w\/i\/[a-zA-Z0-9]+/) ||
        firstMatch(raw, /https?:\/\/(?:www\.)?caiyun\.139\.com\/(?:w\/i\/[a-zA-Z0-9]+|m\/i\?[a-zA-Z0-9]+)[^\s<>"']*/) ||
        firstMatch(raw, /https?:\/\/caiyun\.feixin\.10086\.cn\/[a-zA-Z0-9]+/);
    }
    if (type === "115") return firstMatch(raw, /https?:\/\/(?:115\.com|115cdn\.com|anxia\.com)\/s\/[a-zA-Z0-9]+(?:\?password=[a-zA-Z0-9]{4,8})?/);
    if (type === "123") return firstMatch(raw, /https?:\/\/(?:www\.)?123(?:684|685|865|912|pan|592)\.(?:com|cn)\/s\/[a-zA-Z0-9_-]+(?:\?pwd=[a-zA-Z0-9]{4,8})?/);
    if (type === "uc") return firstMatch(raw, /https?:\/\/drive\.uc\.cn\/s\/[a-zA-Z0-9]+(?:\?public=\d+)?/);
    return "";
  }

  function extractTianyiCode(raw) {
    var match = String(raw || "").match(/sharecode=([a-zA-Z0-9]+)/i);
    return match && match[1] ? match[1] : "";
  }

  function extractPassword(rawText, fallback) {
    var text = String(rawText || "") + " " + String(fallback || "");
    var patterns = [
      /[?&]pwd=([a-zA-Z0-9]{4,8})/i,
      /[?&]password=([a-zA-Z0-9]{4,8})/i,
      /访问码[:：]\s*([a-zA-Z0-9]{4,8})/i,
      /提取码[:：]\s*([a-zA-Z0-9]{4,8})/i,
      /密码[:：]\s*([a-zA-Z0-9]{4,8})/i
    ];
    for (var i = 0; i < patterns.length; i++) {
      var match = text.match(patterns[i]);
      if (match && match[1]) return normalizePassword(match[1]);
    }
    return normalizePassword(fallback);
  }

  function normalizePassword(raw) {
    var password = cleanText(raw).replace(/^[.。!！,，;；:：#*·\s]+|[.。!！,，;；:：#*·\s]+$/g, "");
    if (!password) return "";
    var lower = password.toLowerCase();
    if (lower === "无提取码" || lower.indexOf("无密码") >= 0 || password.indexOf("无需") >= 0) return "";
    return /^[a-zA-Z0-9]{4,8}$/.test(password) ? password : "";
  }

  function normalizeTitle(title) {
    return cleanText(title)
      .replace(/[（(]\d{4}[)）]/g, "")
      .replace(/[ \-_.：:（）()【】\[\]\/]/g, "")
      .toLowerCase();
  }

  function buildWorkTitle(resultTitle, resourceName) {
    resultTitle = cleanText(resultTitle);
    resourceName = cleanText(resourceName);
    if (!resourceName) return resultTitle || "Gying";
    if (!resultTitle) return resourceName;
    if (normalizeTitle(resourceName).indexOf(normalizeTitle(resultTitle)) >= 0) return resourceName;
    return resultTitle + " - " + resourceName;
  }

  function addClass(el, name) {
    if (!el || !name) return;
    var classes = " " + (el.className || "") + " ";
    if (classes.indexOf(" " + name + " ") >= 0) return;
    el.className = cleanText((el.className || "") + " " + name);
  }

  function removeClass(el, name) {
    if (!el || !name) return;
    el.className = cleanText((" " + (el.className || "") + " ").replace(" " + name + " ", " "));
  }

  function isDirectMediaUrl(url) {
    return /\.(m3u8|mp4|mkv|flv|mov|avi|webm|mpd)(\?|#|$)/i.test(cleanText(url));
  }

  function isHlsUrl(url) {
    return /\.m3u8(\?|#|$)/i.test(cleanText(url));
  }

  function isDashUrl(url) {
    return /\.mpd(\?|#|$)/i.test(cleanText(url));
  }

  function mediaPlayOptions(url, referer, pic, wallPic) {
    var options = {
      pic: pic,
      wallPic: wallPic
    };
    if (isHlsUrl(url)) {
      options.format = "application/x-mpegURL";
      return options;
    }
    if (isDashUrl(url)) {
      options.format = "application/dash+xml";
      return options;
    }
    options.headers = { Referer: referer || location.href };
    options.credentials = "include";
    return options;
  }

  function normalizeCandidateUrl(value, baseUrl) {
    var text = cleanText(value);
    if (!text) return "";
    text = text.split("\\/").join("/");
    text = text.replace(/&amp;/g, "&");
    text = text.replace(/\\u0026/g, "&");
    text = text.replace(/^['"]+|['"]+$/g, "");
    if (text.indexOf("$") >= 0) text = text.substring(text.lastIndexOf("$") + 1);
    if (/^\/\//.test(text)) text = location.protocol + text;
    if (/^(magnet:|ed2k:|thunder:)/i.test(text)) return text;
    if (/^https?:\/\//i.test(text) || /^\//.test(text) || /^\.\.?\//.test(text)) return absolutizeUrl(text, baseUrl || location.href);
    return "";
  }

  function extractResourceUrl(raw, baseUrl) {
    var text = String(raw || "");
    var url = firstMatch(text, /magnet:\?xt=urn:btih:[a-zA-Z0-9]+[^\s<>"']*/i) ||
      firstMatch(text, /ed2k:\/\/[^\s<>"']+/i) ||
      firstMatch(text, /thunder:\/\/[^\s<>"']+/i) ||
      firstMatch(text, /https?:\/\/[^\s<>"']+/i) ||
      firstMatch(text, /\/(?:py|play|player|vodplay|video)\/[^\s<>"']+/i);
    return normalizeCandidateUrl(url || text, baseUrl || location.href);
  }

  function resourceUrlFromScript(text) {
    var source = String(text || "");
    var quoted = /['"]([^'"]+)['"]/g;
    var match;
    while ((match = quoted.exec(source))) {
      var url = extractResourceUrl(match[1], location.href);
      if (url) return url;
    }
    return extractResourceUrl(source, location.href);
  }

  function inlineButtonHost(node) {
    var host = closest(node, "li,.item,.list-item,.resource-item,.res-item,.result-item,.van-cell,.card,.cell,.row,.list-group-item,.media");
    if (host && host !== document.body && host !== document.documentElement) return host;
    var current = node ? node.parentElement : null;
    var depth = 0;
    while (current && current !== document.body && current !== document.documentElement && depth < 5) {
      var text = cleanText(current.textContent);
      if (text.length >= 12 && /(今天|昨天|前天|\d+\s*天前|做种|大小|提取码|网盘|磁力链接)/.test(text)) return current;
      current = current.parentElement;
      depth++;
    }
    return node.parentElement;
  }

  function isMetaText(text) {
    text = cleanText(text);
    if (!text || text.length > 24) return false;
    return /^(今天|昨天|前天|刚刚|\d+\s*分钟前|\d+\s*小时前|\d{1,2}[-/.]\d{1,2}|\d{4}[-/.]\d{1,2}[-/.]\d{1,2})$/.test(text) ||
      /^提取码\b/.test(text) ||
      /(?:今天|昨天|前天)$/.test(text);
  }

  function findMetaAnchor(host) {
    if (!host) return null;
    var nodes = queryAll("span,b,i,em,small,strong,p,div", host);
    for (var i = nodes.length - 1; i >= 0; i--) {
      var node = nodes[i];
      if (node.getAttribute && node.getAttribute("data-fm-gying-action")) continue;
      if (isMetaText(node.textContent)) return node;
    }
    if (document.createTreeWalker && window.NodeFilter) {
      var walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT, null, false);
      var textNode;
      var match = null;
      while ((textNode = walker.nextNode())) {
        if (isMetaText(textNode.nodeValue)) match = textNode;
      }
      if (match) return match;
    }
    return null;
  }

  function placeInlineButton(host, originalNode, button, link) {
    if (link && link.type === "online" && originalNode && originalNode.parentNode) {
      addClass(originalNode.parentNode, "fm-gying-online-host");
      addClass(button, "fm-gying-online-inline");
      originalNode.parentNode.insertBefore(button, originalNode.nextSibling);
      return;
    }
    addClass(host, "fm-gying-button-host");
    addClass(button, "fm-gying-inline-fixed");
    host.appendChild(button);
  }

  function isInlinePlayButton(el) {
    return !!(el && getAttr(el, "data-fm-gying-action") === "play-direct");
  }

  function setInlineButtonData(button, link) {
    if (!button || !link) return;
    button.setAttribute("data-fm-gying-action", "play-direct");
    button.setAttribute("data-fm-gying-url", link.url || "");
    button.setAttribute("data-fm-gying-type", link.type || "");
    button.setAttribute("data-fm-gying-title", link.title || "");
    button.setAttribute("data-fm-gying-password", link.password || "");
  }

  function findInlineButtonForNode(node, link) {
    if (!node) return null;
    if (link && link.type === "online" && isInlinePlayButton(node.nextElementSibling)) {
      return node.nextElementSibling;
    }
    var parent = inlineButtonHost(node);
    if (!parent) return null;
    var buttons = queryAll(".fm-gying-inline[data-fm-gying-action='play-direct']", parent);
    if (buttons.length === 1) return buttons[0];
    return null;
  }

  function directResourceFromElement(el) {
    var raw = getAttr(el, "data-url") || getAttr(el, "data-href") || getAttr(el, "data-link") || getAttr(el, "data-clipboard-text") || getAttr(el, "href");
    var url = extractResourceUrl(raw, location.href) || resourceUrlFromScript(getAttr(el, "onclick"));
    var type = classifyResource(url, -1, "");
    if (!url || type === "others") return null;
    return {
      type: type,
      url: url,
      password: extractPassword(url, ""),
      title: cleanText(el.textContent) || currentTitle()
    };
  }

  function shouldRouteOriginalClick(el, link) {
    if (!link || !link.url) return false;
    if (link.type === "media") return true;
    if (link.type !== "online") return false;
    var hint = [
      cleanText(el.textContent),
      getAttr(el, "class"),
      getAttr(el, "href"),
      getAttr(el, "data-url"),
      getAttr(el, "data-href"),
      getAttr(el, "data-link"),
      getAttr(el, "onclick")
    ].join(" ");
    return /(在线播放|正片|播放|\/py\/|play|player|vodplay)/i.test(hint);
  }

  function isLikelyOnlineArea(el) {
    if (detailFromPath()) return true;
    var host = closest(el, ".play-list,.playlist,.episode,.episodes,.vod-play,.player-list,.line,.source,.tab-content,.tab-pane,.van-tab__pane");
    var text = cleanText((host || el.parentElement || document.body).textContent);
    return /(在线播放|线路[:：]|正片|选集|剧集)/.test(text);
  }

  function onlineTriggerElement(target) {
    var trigger = closest(target, CONFIG.onlineTriggerSelector);
    if (!trigger || trigger === document.body || trigger === document.documentElement) return null;
    if (closest(trigger, "[data-fm-gying-action]")) return null;
    var text = cleanText(trigger.textContent);
    var hint = [
      text,
      getAttr(trigger, "class"),
      getAttr(trigger, "href"),
      getAttr(trigger, "data-url"),
      getAttr(trigger, "data-href"),
      getAttr(trigger, "data-link"),
      getAttr(trigger, "onclick")
    ].join(" ");
    if (!/(^正片$|立即播放|开始播放|(?:^|\s)播放(?:\s|$)|第\s*\d+\s*(集|期|话)?)/.test(hint)) return null;
    return isLikelyOnlineArea(trigger) ? trigger : null;
  }

  function routeOriginalResourceClick(event) {
    return routeOriginalResourceEvent(event, "click");
  }

  function routeOriginalResourceEvent(event, reason) {
    var node = closest(event.target, CONFIG.directResourceSelector);
    if (!node) return false;
    var link = directResourceFromElement(node);
    if (!shouldRouteOriginalClick(node, link)) return false;
    if (event.cancelable !== false) event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    if (!isDuplicateOriginalRoute(link.url)) playLink(link);
    return true;
  }

  function isDuplicateOriginalRoute(url) {
    var now = Date.now();
    if (state.lastRoutedOriginalUrl === url && now - state.lastRoutedOriginalAt < 1500) return true;
    state.lastRoutedOriginalUrl = url;
    state.lastRoutedOriginalAt = now;
    return false;
  }

  function routeOnlineTriggerClick(event) {
    var trigger = onlineTriggerElement(event.target);
    if (!trigger) return false;
    var link = directResourceFromElement(trigger);
    markAutoPlaybackIntent("online-trigger");
    if (link && link.url && (link.type === "online" || link.type === "media")) {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      playLink(link);
      return true;
    }
    toast("正在等待播放地址...");
    return false;
  }

  function enhanceDirectResourceLinks() {
    var nodes = queryAll(CONFIG.directResourceSelector);
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var link = directResourceFromElement(node);
      if (!link) continue;
      var existing = findInlineButtonForNode(node, link);
      if (existing) {
        setInlineButtonData(existing, link);
        node.setAttribute("data-fm-gying-ready", "1");
        continue;
      }
      if (node.getAttribute("data-fm-gying-ready") === "1") continue;
      node.setAttribute("data-fm-gying-ready", "1");
      injectInlineButton(node, link);
    }
  }

  function injectInlineButton(node, link) {
    var parent = inlineButtonHost(node);
    if (!parent) return;
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "fm-gying-inline";
    btn.textContent = "App播放";
    setInlineButtonData(btn, link);
    placeInlineButton(parent, node, btn, link);
  }

  function extractMediaUrlFromText(text, baseUrl) {
    var normalized = String(text || "");
    normalized = normalized.split("\\/").join("/");
    normalized = normalized.replace(/&amp;/g, "&");
    normalized = normalized.replace(/\\u0026/g, "&");
    var media = "(?:m3u8|mp4|mkv|flv|mov|avi|webm|mpd)";
    var patterns = [
      new RegExp("(https?:\\/\\/[^\"'<>\\\\\\s]+\\." + media + "(?:\\?[^\"'<>\\\\\\s]*)?)", "i"),
      new RegExp("[\"'](?:url|src|file|video|playUrl|play_url)[\"']\\s*[:=]\\s*[\"']([^\"']+\\." + media + "(?:\\?[^\"']*)?)[\"']", "i"),
      new RegExp("(?:url|src|file|video|playUrl|play_url)\\s*[:=]\\s*[\"']([^\"']+\\." + media + "(?:\\?[^\"']*)?)[\"']", "i")
    ];
    for (var i = 0; i < patterns.length; i++) {
      var match = normalized.match(patterns[i]);
      if (match && match[1]) return absolutizeUrl(match[1], baseUrl || location.href);
    }
    return "";
  }

  function decodePlayerUrl(raw, mode) {
    var value = cleanText(raw).split("\\/").join("/");
    try {
      if (mode === "1") value = decodeURIComponent(value);
      else if (mode === "2" && window.atob) value = decodeURIComponent(window.atob(value));
    } catch (e) {
      try {
        if (mode === "2" && window.atob) value = window.atob(value);
      } catch (ignore) {}
    }
    return value;
  }

  function extractRawDataCandidate(text, baseUrl) {
    var match = String(text || "").match(/(?:const|let|var)?\s*rawData\s*=\s*\[([\s\S]*?)\]\s*;/);
    if (!match || !match[1]) return "";
    var itemRegex = /["']([^"']+)["']/g;
    var item;
    while ((item = itemRegex.exec(match[1]))) {
      var url = normalizeCandidateUrl(item[1], baseUrl);
      if (url) return url;
    }
    return "";
  }

  function extractPlayerCandidate(text, baseUrl) {
    var source = String(text || "");
    var modeMatch = source.match(/["']?encrypt["']?\s*:\s*["']?([012])["']?/);
    var mode = modeMatch && modeMatch[1] ? modeMatch[1] : "0";
    var patterns = [
      /_obj\s*\.\s*player\s*=\s*\{[\s\S]{0,5000}?["']?url["']?\s*:\s*["']([^"']+)["']/i,
      /(?:player_[A-Za-z0-9_$]+|player)\s*=\s*\{[\s\S]{0,3000}?["']?url["']?\s*:\s*["']([^"']+)["']/i,
      /["'](?:url|src|file|video|playUrl|play_url)["']\s*[:=]\s*["']([^"']+)["']/i
    ];
    for (var i = 0; i < patterns.length; i++) {
      var match = source.match(patterns[i]);
      if (!match || !match[1]) continue;
      var decoded = decodePlayerUrl(match[1], mode);
      var url = normalizeCandidateUrl(decoded, baseUrl);
      if (url) return url;
    }
    return "";
  }

  function extractOnlineCandidate(text, baseUrl) {
    return extractMediaUrlFromText(text, baseUrl) ||
      extractRawDataCandidate(text, baseUrl) ||
      extractPlayerCandidate(text, baseUrl);
  }

  function runtimePlayerCandidate() {
    var player = window._obj && window._obj.player ? window._obj.player : null;
    if (!player) return "";
    var url = normalizeCandidateUrl(player.url || player.play_url || player.playUrl || "", location.href);
    if (url) return url;
    if (player.data) {
      url = findUrlInJson(player.data, location.href, 0);
      if (url) return url;
    }
    return findUrlInJson(player, location.href, 0);
  }

  function findUrlInJson(value, baseUrl, depth) {
    if (depth > 4 || value == null) return "";
    if (typeof value === "string") {
      var url = normalizeCandidateUrl(value, baseUrl);
      if (url) return url;
      return extractMediaUrlFromText(value, baseUrl);
    }
    if (Object.prototype.toString.call(value) === "[object Array]") {
      for (var i = 0; i < value.length; i++) {
        var fromArray = findUrlInJson(value[i], baseUrl, depth + 1);
        if (fromArray) return fromArray;
      }
      return "";
    }
    if (typeof value === "object") {
      var keys = ["data", "url", "play_url", "playUrl", "src", "file"];
      for (var k = 0; k < keys.length; k++) {
        if (Object.prototype.hasOwnProperty.call(value, keys[k])) {
          var direct = findUrlInJson(value[keys[k]], baseUrl, depth + 1);
          if (direct) return direct;
        }
      }
      for (var name in value) {
        if (!Object.prototype.hasOwnProperty.call(value, name)) continue;
        var nested = findUrlInJson(value[name], baseUrl, depth + 1);
        if (nested) return nested;
      }
    }
    return "";
  }

  function extractMediaFromResponseText(text, baseUrl) {
    var media = extractMediaUrlFromText(text, baseUrl);
    if (media) return media;
    try {
      return findUrlInJson(JSON.parse(String(text || "")), baseUrl, 0);
    } catch (e) {
      return extractOnlineCandidate(text, baseUrl);
    }
  }

  function mediaCandidateFromRuntime(win, doc, baseUrl) {
    if (!win || !doc) return "";
    try {
      var player = win._obj && win._obj.player ? win._obj.player : null;
      var fromPlayer = player ? findUrlInJson(player, baseUrl, 0) : "";
      if (fromPlayer) return fromPlayer;
    } catch (e) {}
    var nodes = [];
    try {
      nodes = Array.prototype.slice.call(doc.querySelectorAll("video,video source,source"));
    } catch (queryError) {}
    for (var i = nodes.length - 1; i >= 0; i--) {
      var node = nodes[i];
      try {
        if (node.tagName === "VIDEO") {
          node.muted = true;
          node.volume = 0;
          if (node.pause) node.pause();
        }
      } catch (muteError) {}
      var raw = cleanText(node.currentSrc || node.src || getAttr(node, "src") || getAttr(node, "data-src"));
      var media = extractMediaUrlFromText(raw, baseUrl) || (isDirectMediaUrl(raw) ? normalizeCandidateUrl(raw, baseUrl) : "");
      if (media) return media;
    }
    try {
      if (win.performance && win.performance.getEntriesByType) {
        var entries = win.performance.getEntriesByType("resource") || [];
        for (var j = entries.length - 1; j >= 0; j--) {
          var entryUrl = entries[j] && entries[j].name ? String(entries[j].name) : "";
          var entryMedia = extractMediaUrlFromText(entryUrl, baseUrl) || (isDirectMediaUrl(entryUrl) ? normalizeCandidateUrl(entryUrl, baseUrl) : "");
          if (entryMedia) return entryMedia;
        }
      }
    } catch (perfError) {}
    var scripts = [];
    try {
      scripts = doc.scripts || [];
    } catch (scriptError) {}
    for (var k = scripts.length - 1; k >= 0; k--) {
      var candidate = extractOnlineCandidate(scripts[k].textContent || "", baseUrl);
      if (candidate) return candidate;
    }
    return "";
  }

  function resolveOnlineByRuntimeFrame(link) {
    return new Promise(function (resolve) {
      if (!document.body || !link || !link.url) {
        resolve("");
        return;
      }
      var frame = document.createElement("iframe");
      var done = false;
      var pollTimer = 0;
      var timeoutTimer = 0;
      var baseUrl = link.url;
      frame.setAttribute("aria-hidden", "true");
      frame.setAttribute("tabindex", "-1");
      frame.style.cssText = "position:absolute!important;left:-99999px!important;top:-99999px!important;width:1px!important;height:1px!important;opacity:0!important;pointer-events:none!important;border:0!important;";

      function cleanup() {
        clearInterval(pollTimer);
        clearTimeout(timeoutTimer);
        try {
          if (frame.parentNode) frame.parentNode.removeChild(frame);
        } catch (removeError) {}
      }

      function finish(url) {
        if (done) return;
        done = true;
        cleanup();
        resolve(url || "");
      }

      function scan() {
        if (done) return;
        try {
          var win = frame.contentWindow;
          var doc = frame.contentDocument || (win && win.document);
          if (!win || !doc) return;
          var media = mediaCandidateFromRuntime(win, doc, baseUrl);
          if (media) finish(media);
        } catch (e) {}
      }

      frame.onload = function () {
        scan();
      };
      pollTimer = setInterval(scan, 300);
      timeoutTimer = setTimeout(function () {
        finish("");
      }, 12000);
      frame.src = link.url;
      document.body.appendChild(frame);
    });
  }

  function safeSessionGet(key) {
    try {
      return window.sessionStorage ? sessionStorage.getItem(key) : "";
    } catch (e) {
      return "";
    }
  }

  function safeSessionSet(key, value) {
    try {
      if (window.sessionStorage) sessionStorage.setItem(key, value);
    } catch (e) {}
  }

  function safeSessionRemove(key) {
    try {
      if (window.sessionStorage) sessionStorage.removeItem(key);
    } catch (e) {}
  }

  function markAutoPlaybackIntent(reason) {
    state.autoIntentTime = Date.now();
    safeSessionSet("fm-gying-autoplay", String(state.autoIntentTime) + "|" + cleanText(reason || "play"));
  }

  function clearAutoPlaybackIntent() {
    state.autoIntentTime = 0;
    safeSessionRemove("fm-gying-autoplay");
  }

  function hasRecentAutoPlaybackIntent() {
    var now = Date.now();
    if (state.autoIntentTime && now - state.autoIntentTime < 90000) return true;
    var raw = safeSessionGet("fm-gying-autoplay");
    var time = Number(String(raw || "").split("|")[0]) || 0;
    if (time && now - time < 90000) {
      state.autoIntentTime = time;
      return true;
    }
    if (time) clearAutoPlaybackIntent();
    return false;
  }

  function isPlaybackPath() {
    return /\/(py|play|player|vodplay|video)(\/|$)/i.test(location.pathname || "");
  }

  function shouldAutoPlayRuntimeMedia() {
    if (hasRecentAutoPlaybackIntent()) return true;
    if (isPlaybackPath()) return true;
    return false;
  }

  function rememberMediaUrl(url, referer) {
    var media = extractMediaUrlFromText(url, location.href) || (isDirectMediaUrl(url) ? normalizeCandidateUrl(url, location.href) : "");
    if (!media) return;
    state.lastMediaUrl = media;
    state.lastMediaReferer = referer || location.href;
    scheduleAutoPlayMedia("runtime");
  }

  function rememberMediaFromBody(body, baseUrl) {
    var media = "";
    if (typeof body === "string") media = extractMediaFromResponseText(body, baseUrl || location.href);
    else if (body && typeof body === "object") media = findUrlInJson(body, baseUrl || location.href, 0);
    if (media) rememberMediaUrl(media, baseUrl || location.href);
  }

  function collectRuntimeMediaCandidate() {
    var playerUrl = runtimePlayerCandidate();
    if (playerUrl) return { url: playerUrl, referer: location.href };
    var mediaNodes = queryAll("video,video source,source");
    for (var i = mediaNodes.length - 1; i >= 0; i--) {
      var node = mediaNodes[i];
      var url = cleanText(node.currentSrc || getAttr(node, "src") || getAttr(node, "data-src"));
      var media = extractMediaUrlFromText(url, location.href) || (isDirectMediaUrl(url) ? normalizeCandidateUrl(url, location.href) : "");
      if (media) return { url: media, referer: location.href };
    }
    if (window.performance && performance.getEntriesByType) {
      try {
        var entries = performance.getEntriesByType("resource") || [];
        for (var j = entries.length - 1; j >= 0; j--) {
          var entryUrl = entries[j] && entries[j].name ? String(entries[j].name) : "";
          var entryMedia = extractMediaUrlFromText(entryUrl, location.href) || (isDirectMediaUrl(entryUrl) ? normalizeCandidateUrl(entryUrl, location.href) : "");
          if (entryMedia) return { url: entryMedia, referer: location.href };
        }
      } catch (e) {}
    }
    var scripts = document.scripts || [];
    for (var k = scripts.length - 1; k >= 0; k--) {
      var candidate = extractOnlineCandidate(scripts[k].textContent || "", location.href);
      if (candidate) return { url: candidate, referer: location.href };
    }
    return null;
  }

  function scheduleAutoPlayMedia(reason) {
    clearTimeout(state.autoPlayTimer);
    state.autoPlayTimer = setTimeout(function () {
      tryAutoPlayRuntimeMedia(reason || "scan");
    }, 260);
  }

  function tryAutoPlayRuntimeMedia(reason) {
    if (!shouldAutoPlayRuntimeMedia()) return;
    var candidate = state.lastMediaUrl ? { url: state.lastMediaUrl, referer: state.lastMediaReferer || location.href } : collectRuntimeMediaCandidate();
    if (!candidate || !candidate.url) return;
    if (state.autoTriedUrl === candidate.url && state.autoPlayedUrl) return;
    state.autoTriedUrl = candidate.url;
    resolveParsedMediaUrl(candidate.url, candidate.referer || location.href).then(function (mediaUrl) {
      if (!mediaUrl || state.autoPlayedUrl === mediaUrl) return;
      state.autoPlayedUrl = mediaUrl;
      clearAutoPlaybackIntent();
      playMediaUrl(mediaUrl, { title: currentTitle() }, candidate.referer || location.href);
    }).catch(function (err) {
      log("auto media failed", reason, err && (err.message || err));
    });
  }

  function installMediaHooks() {
    if (state.mediaHooksInstalled) return;
    state.mediaHooksInstalled = true;
    if (typeof window.fetch === "function") {
      try {
        var rawFetch = window.fetch;
        window.fetch = function () {
          var input = arguments[0];
          var url = typeof input === "string" ? input : (input && input.url ? input.url : "");
          rememberMediaUrl(url, location.href);
          return rawFetch.apply(this, arguments).then(function (response) {
            rememberMediaUrl(response && response.url, location.href);
            if (shouldAutoPlayRuntimeMedia() && response && response.clone) {
              try {
                response.clone().text().then(function (text) {
                  rememberMediaFromBody(text, response.url || url || location.href);
                }).catch(function (e) {});
              } catch (copyError) {}
            }
            return response;
          });
        };
      } catch (e) {}
    }
    if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
      try {
        var rawOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
          this.__fmGyingUrl = url;
          rememberMediaUrl(url, location.href);
          return rawOpen.apply(this, arguments);
        };
      } catch (xhrOpenError) {}
      try {
        var rawSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function () {
          var xhr = this;
          if (xhr && xhr.addEventListener && !xhr.__fmGyingLoadHooked) {
            xhr.__fmGyingLoadHooked = true;
            xhr.addEventListener("loadend", function () {
              if (!shouldAutoPlayRuntimeMedia()) return;
              var baseUrl = xhr.responseURL || xhr.__fmGyingUrl || location.href;
              try {
                if (!xhr.responseType || xhr.responseType === "text") rememberMediaFromBody(xhr.responseText, baseUrl);
                else rememberMediaFromBody(xhr.response, baseUrl);
              } catch (xhrBodyError) {}
            });
          }
          return rawSend.apply(this, arguments);
        };
      } catch (xhrSendError) {}
    }
    if (window.HTMLMediaElement && HTMLMediaElement.prototype) {
      try {
        var rawPlay = HTMLMediaElement.prototype.play;
        if (rawPlay && !rawPlay.__fmGyingWrapped) {
          HTMLMediaElement.prototype.play = function () {
            rememberMediaUrl(this.currentSrc || this.src, location.href);
            return rawPlay.apply(this, arguments);
          };
          HTMLMediaElement.prototype.play.__fmGyingWrapped = true;
        }
      } catch (mediaError) {}
    }
  }

  function resolveParsedMediaUrl(candidate, referer) {
    var url = normalizeCandidateUrl(candidate, referer || location.href);
    if (!url) return Promise.resolve("");
    if (isDirectMediaUrl(url)) return Promise.resolve(url);
    var origins = [location.origin];
    try {
      var refererOrigin = new URL(referer || location.href, location.href).origin;
      if (refererOrigin && refererOrigin !== origins[0]) origins.push(refererOrigin);
    } catch (e) {}
    var apis = [];
    for (var i = 0; i < origins.length; i++) {
      apis.push(origins[i] + "/content/plugins/plyr_player/api.php?type=parse&url=" + encodeURIComponent(url));
    }
    return tryResolveParseApis(apis, referer || location.href, 0).then(function (resolved) {
      if (resolved) return resolved;
      return isDirectMediaUrl(url) ? url : "";
    });
  }

  function tryResolveParseApis(apis, referer, index) {
    if (index >= apis.length) return Promise.resolve("");
    return requestText(apis[index], {
      method: "GET",
      headers: { Referer: referer || location.href },
      timeout: 15,
      credentials: "include"
    }).then(function (resp) {
      var text = String(resp && resp.body || "");
      if (resp && resp.ok && text) {
        var media = extractMediaFromResponseText(text, apis[index]);
        if (media) return media;
      }
      return tryResolveParseApis(apis, referer, index + 1);
    }).catch(function () {
      return tryResolveParseApis(apis, referer, index + 1);
    });
  }

  function playMediaUrl(url, link, referer) {
    var art = currentArtwork();
    var pic = state.activePic || art.pic || "";
    var wallPic = state.activeWallPic || art.wallPic || "";
    var options = mediaPlayOptions(url, referer, pic, wallPic);
    whenFm().then(function (sdk) {
      if (!sdk.play) throw new Error("fm.play unavailable");
      return sdk.play(url, link.title || state.activeTitle || currentTitle(), options);
    }).catch(function (err) {
      log("play failed", err && (err.stack || err.message) || err);
      toast("调用 App 播放失败");
    });
  }

  function pushLink(link) {
    var art = currentArtwork();
    var pic = state.activePic || art.pic || "";
    var wallPic = state.activeWallPic || art.wallPic || "";
    whenFm().then(function (sdk) {
      return sdk.pan.play({
        type: link.type === "online" ? "http" : (link.type || "http"),
        url: link.url,
        password: link.password || "",
        title: link.title || state.activeTitle || currentTitle(),
        pic: pic,
        wallPic: wallPic
      });
    }).catch(function (err) {
      log("push failed", err && (err.stack || err.message) || err);
      toast("调用 App 播放失败");
    });
  }

  function playOnlineLink(link) {
    toast("正在解析在线播放...");
    requestText(link.url, {
      method: "GET",
      headers: { Referer: location.href, "Accept-Encoding": "gzip" },
      timeout: 18,
      credentials: "include"
    }).then(function (resp) {
      var body = String(resp && resp.body || "");
      if (resp && resp.ok && !isChallengeText(body) && !isLoginText(body)) {
        var candidate = extractOnlineCandidate(body, resp.url || link.url);
        if (candidate) return resolveParsedMediaUrl(candidate, resp.url || link.url);
      }
      return "";
    }).then(function (mediaUrl) {
      if (mediaUrl) return mediaUrl;
      toast("正在启动页面播放器解析...");
      return resolveOnlineByRuntimeFrame(link).then(function (runtimeUrl) {
        return runtimeUrl ? resolveParsedMediaUrl(runtimeUrl, link.url) : "";
      });
    }).then(function (mediaUrl) {
      if (mediaUrl) playMediaUrl(mediaUrl, link, link.url);
      else toast("未解析到 App 可播放地址");
    }).catch(function () {
      toast("在线播放解析失败");
    });
  }

  function playLink(link) {
    if (!link || !link.url) return;
    if (link.type === "media") {
      playMediaUrl(link.url, link, location.href);
      return;
    }
    if (link.type === "online") {
      playOnlineLink(link);
      return;
    }
    pushLink(link);
  }

  function preloadArtwork() {
    if (!window.fm || !window.fm.preloadArtwork) return;
    if (!state.activePic && !state.activeWallPic) return;
    window.fm.preloadArtwork(state.activePic || "", state.activeWallPic || "").catch(function () {});
  }

  function ensureRoot() {
    var root = document.getElementById(CONFIG.rootId);
    if (root) return root;
    root = document.createElement("div");
    root.id = CONFIG.rootId;
    root.className = "fm-gying-root fm-gying-collapsed";
    root.innerHTML = ""
      + "<div class=\"fm-gying-panel\">"
      + "<div class=\"fm-gying-head\"><b>Gying App 资源</b><button type=\"button\" data-fm-gying-action=\"toggle\">收起</button></div>"
      + "<div class=\"fm-gying-body\" id=\"fm-gying-body\"></div>"
      + "</div>";
    document.body.appendChild(root);
    return root;
  }

  function bodyEl() {
    ensureRoot();
    return document.getElementById("fm-gying-body");
  }

  function escapeHtml(text) {
    return String(text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function openPanel() {
    var root = ensureRoot();
    root.className = root.className.replace(/\bfm-gying-collapsed\b/g, "");
  }

  function hidePanel() {
    var root = document.getElementById(CONFIG.rootId);
    state.panelMode = "idle";
    if (root && root.className.indexOf("fm-gying-collapsed") < 0) root.className += " fm-gying-collapsed";
    var body = document.getElementById("fm-gying-body");
    if (body) body.innerHTML = "";
  }

  function renderNotice(text, tone) {
    var root = ensureRoot();
    var body = bodyEl();
    state.panelMode = "notice";
    body.innerHTML = "<div class=\"fm-gying-msg fm-gying-" + (tone || "info") + "\">" + escapeHtml(text) + "</div>";
    if (tone === "error" || tone === "warn" || tone === "loading") openPanel();
    return root;
  }

  function renderLoading(text) {
    state.panelMode = "loading";
    openPanel();
    bodyEl().innerHTML = "<div class=\"fm-gying-msg fm-gying-loading\">" + escapeHtml(text || "加载中...") + "</div>";
  }

  function renderSearch(items) {
    state.panelMode = "search";
    state.searchItems = items || [];
    var body = bodyEl();
    if (!state.searchItems.length) {
      body.innerHTML = "<div class=\"fm-gying-msg\">当前搜索页未解析到资源条目。</div>";
      return;
    }
    var html = "<div class=\"fm-gying-msg\">解析到 " + state.searchItems.length + " 个条目，点“资源”读取网盘/磁力。</div>";
    for (var i = 0; i < state.searchItems.length; i++) {
      var item = state.searchItems[i];
      html += "<div class=\"fm-gying-item\">"
        + "<div class=\"fm-gying-title\">" + escapeHtml(item.title) + "</div>"
        + "<div class=\"fm-gying-sub\">" + escapeHtml(item.content || item.resourceType + "/" + item.resourceId) + "</div>"
        + "<button type=\"button\" data-fm-gying-action=\"fetch-search\" data-fm-gying-index=\"" + i + "\">资源</button>"
        + "</div>";
    }
    body.innerHTML = html;
    openPanel();
  }

  function renderDetailPrompt(item) {
    state.panelMode = "detail";
    state.searchItems = [item];
    var body = bodyEl();
    body.innerHTML = "<div class=\"fm-gying-msg\">当前详情页可直接读取 App 资源。</div>"
      + "<div class=\"fm-gying-item\">"
      + "<div class=\"fm-gying-title\">" + escapeHtml(item.title) + "</div>"
      + "<div class=\"fm-gying-sub\">" + escapeHtml(item.resourceType + "/" + item.resourceId) + "</div>"
      + "<button type=\"button\" data-fm-gying-action=\"fetch-search\" data-fm-gying-index=\"0\">读取资源</button>"
      + "</div>";
  }

  function renderLinks(links, title) {
    var body = bodyEl();
    if (!links || !links.length) {
      body.innerHTML = "<div class=\"fm-gying-msg fm-gying-warn\">没有读取到可播放网盘或磁力链接。</div>";
      openPanel();
      return;
    }
    var html = "<div class=\"fm-gying-msg fm-gying-ok\">" + escapeHtml(title || "资源") + " · " + links.length + " 条链接</div>";
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      html += "<div class=\"fm-gying-link\">"
        + "<div class=\"fm-gying-link-title\"><b>" + escapeHtml(link.title || title || "资源") + "</b><span>" + escapeHtml(TYPE_LABEL[link.type] || link.type) + "</span></div>"
        + "<div class=\"fm-gying-sub\">" + escapeHtml(link.url) + "</div>"
        + "<div class=\"fm-gying-sub\">" + escapeHtml((link.password ? "提取码: " + link.password + " · " : "") + (link.time || "")) + "</div>"
        + "<button type=\"button\" data-fm-gying-action=\"play-link\" data-fm-gying-index=\"" + i + "\">App播放</button>"
        + "</div>";
    }
    body.innerHTML = html;
    openPanel();
  }

  function scanPage() {
    if (!document.body) return;
    ensureRoot();
    document.body.classList.toggle("fm-gying-tv", !!(window.fongmiClient && window.fongmiClient.isLeanback));
    enhanceDirectResourceLinks();
    scheduleAutoPlayMedia("scan");

    if (maybeShowDomainNotice()) return;

    var bodyText = document.body ? document.body.textContent || "" : "";
    if (isChallengeText(bodyText)) {
      hidePanel();
      return;
    }

    if (/\/user\/login/.test(location.pathname) || isLoginText(bodyText)) {
      renderNotice("请使用原站页面登录。登录成功后搜索或进入详情页即可使用 App 资源。", "info");
      return;
    }

    var searchData = parseSearchDataFromPage();
    if (searchData) {
      var items = buildSearchItems(searchData);
      renderSearch(items);
      return;
    }

    var detail = detailFromPath();
    if (detail) {
      renderDetailPrompt(detail);
      return;
    }

    if (state.panelMode === "idle") {
      renderNotice("在原站搜索页或详情页会显示 App 资源入口。", "info");
    }
  }

  function scheduleScan() {
    clearTimeout(state.scanTimer);
    state.scanTimer = setTimeout(scanPage, CONFIG.scanDelay);
  }

  function onPanelClick(event) {
    var target = closest(event.target, "[data-fm-gying-action]");
    if (!target) {
      if (routeOriginalResourceClick(event)) return;
      routeOnlineTriggerClick(event);
      return;
    }
    var action = getAttr(target, "data-fm-gying-action");
    if (action === "toggle") {
      event.preventDefault();
      var root = ensureRoot();
      if (/\bfm-gying-collapsed\b/.test(root.className)) root.className = root.className.replace(/\bfm-gying-collapsed\b/g, "");
      else root.className += " fm-gying-collapsed";
      return;
    }
    if (action === "fetch-search") {
      event.preventDefault();
      event.stopPropagation();
      var index = Number(getAttr(target, "data-fm-gying-index"));
      fetchDetail(state.searchItems[index]);
      return;
    }
    if (action === "play-link") {
      event.preventDefault();
      event.stopPropagation();
      var linkIndex = Number(getAttr(target, "data-fm-gying-index"));
      playLink(state.links[linkIndex]);
      return;
    }
    if (action === "play-direct") {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      var directLink = {
        type: getAttr(target, "data-fm-gying-type") || "http",
        url: getAttr(target, "data-fm-gying-url"),
        password: getAttr(target, "data-fm-gying-password"),
        title: getAttr(target, "data-fm-gying-title") || currentTitle()
      };
      var liveLink = liveResourceForInlineButton(target);
      if (liveLink && liveLink.url) {
        directLink = liveLink;
        setInlineButtonData(target, liveLink);
      }
      playLink(directLink);
      return;
    }
    if (routeOriginalResourceClick(event)) return;
    routeOnlineTriggerClick(event);
  }

  function liveResourceForInlineButton(button) {
    if (!button) return null;
    var node = button.previousElementSibling;
    var link = directResourceFromElement(node);
    if (link && link.url) return link;
    var parent = button.parentElement;
    if (!parent) return null;
    var nodes = queryAll(CONFIG.directResourceSelector, parent);
    var found = null;
    for (var i = 0; i < nodes.length; i++) {
      if (closest(nodes[i], "[data-fm-gying-action]")) continue;
      link = directResourceFromElement(nodes[i]);
      if (!link || !link.url) continue;
      if (found) return null;
      found = link;
    }
    return found;
  }

  function onInlinePress(event) {
    var target = closest(event.target, ".fm-gying-inline");
    if (!target) {
      if (event.type === "mousedown" && event.button && event.button !== 0) return;
      if (routeOriginalResourceEvent(event, event.type || "press")) return;
      if (onlineTriggerElement(event.target)) markAutoPlaybackIntent("online-touch");
      return;
    }
    event.stopPropagation();
    event.stopImmediatePropagation();
  }

  function onKeydown(event) {
    var key = event.key || "";
    if (key !== "Enter" && event.keyCode !== 13 && event.keyCode !== 23) return;
    var target = event.target;
    if (!target || target.tagName === "INPUT" || target.tagName === "TEXTAREA") return;
    if (matchesSelector(target, ".fm-gying-item,.fm-gying-link,.fm-gying-inline")) {
      target.click();
    }
  }

  function installStyle() {
    if (document.documentElement) addClass(document.documentElement, "fm-gying-safe-ready");
    var css = ""
      + ":root{--fm-gying-safe-top:var(--fm-safe-top,0px);--fm-gying-safe-bottom:var(--fm-safe-bottom,0px);}"
      + "@supports (top:max(0px,1px)){:root{--fm-gying-safe-top:max(var(--fm-safe-top,0px),env(safe-area-inset-top,0px));--fm-gying-safe-bottom:max(var(--fm-safe-bottom,0px),env(safe-area-inset-bottom,0px));}}"
      + "html.fm-gying-safe-top-active body{padding-top:var(--fm-gying-safe-top,0px)!important;}"
      + "html.fm-gying-safe-top-active body>header,html.fm-gying-safe-top-active header[style*='position: fixed'],html.fm-gying-safe-top-active header[style*='position:fixed']{top:var(--fm-gying-safe-top,0px)!important;}"
      + "#" + CONFIG.rootId + "{position:fixed;right:12px;bottom:calc(12px + var(--fm-gying-safe-bottom,0px));z-index:2147483647;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;color:#f5fff6;}"
      + "#" + CONFIG.rootId + " *{box-sizing:border-box;}"
      + ".fm-gying-panel{width:360px;max-width:calc(100vw - 24px);max-height:68vh;margin-bottom:8px;border:1px solid rgba(255,255,255,.18);border-radius:16px;background:rgba(15,28,24,.96);box-shadow:0 20px 60px rgba(0,0,0,.45);overflow:hidden;}"
      + ".fm-gying-collapsed .fm-gying-panel{display:none!important;}"
      + ".fm-gying-head{min-height:44px;padding:10px 12px;border-bottom:1px solid rgba(255,255,255,.12);}"
      + ".fm-gying-head b{font-size:15px;line-height:24px;}"
      + ".fm-gying-head button{float:right;min-height:28px;border:1px solid rgba(255,255,255,.18);border-radius:8px;background:rgba(255,255,255,.08);color:#f5fff6;}"
      + ".fm-gying-body{max-height:calc(68vh - 46px);overflow:auto;padding:10px;}"
      + ".fm-gying-msg{padding:10px 11px;margin-bottom:8px;border:1px solid rgba(255,255,255,.14);border-radius:12px;background:rgba(255,255,255,.06);font-size:13px;line-height:1.5;color:rgba(245,255,246,.82);}"
      + ".fm-gying-warn{border-color:rgba(255,208,110,.5);color:#ffd06e;}"
      + ".fm-gying-error{border-color:rgba(255,130,110,.5);color:#ff9a88;}"
      + ".fm-gying-ok{border-color:rgba(117,230,163,.42);color:#8ff0b7;}"
      + ".fm-gying-loading{border-color:rgba(212,255,120,.42);color:#dfff83;}"
      + ".fm-gying-item,.fm-gying-link{padding:10px;margin-bottom:8px;border:1px solid rgba(255,255,255,.14);border-radius:12px;background:rgba(255,255,255,.07);}"
      + ".fm-gying-title{font-size:14px;font-weight:800;line-height:1.35;margin-bottom:4px;color:#fff;}"
      + ".fm-gying-sub{font-size:12px;line-height:1.45;color:rgba(245,255,246,.68);word-break:break-all;margin-top:3px;}"
      + ".fm-gying-item button,.fm-gying-link button,.fm-gying-inline{min-height:30px;margin-top:6px;margin-right:6px;padding:5px 11px;border:1px solid rgba(20,184,166,.55);border-radius:8px;background:#0f766e;color:#fff;font-size:12px;line-height:18px;font-weight:800;box-shadow:0 4px 12px rgba(15,118,110,.2);white-space:nowrap;}"
      + ".fm-gying-inline{display:inline-flex;align-items:center;justify-content:center;margin-left:6px;margin-top:0;vertical-align:middle;}"
      + ".fm-gying-button-host{position:relative!important;min-height:54px;padding-right:108px!important;}"
      + ".fm-gying-button-host>.fm-gying-inline-fixed{position:absolute!important;right:10px;top:50%;margin:0!important;z-index:3;-webkit-transform:translateY(-50%);transform:translateY(-50%);}"
      + ".fm-gying-online-host{position:relative!important;min-height:46px;padding-right:108px!important;}"
      + ".fm-gying-online-host>.fm-gying-online-inline{position:absolute!important;right:10px;top:50%;margin:0!important;z-index:3;-webkit-transform:translateY(-50%);transform:translateY(-50%);}"
      + ".fm-gying-inline:active,#" + CONFIG.rootId + " button:active{background:#115e59;}"
      + ".fm-gying-link-title b{display:block;margin-right:54px;font-size:13px;color:#fff;}"
      + ".fm-gying-link-title span{float:right;font-size:12px;color:#dfff83;}"
      + "#" + CONFIG.rootId + " button:focus,.fm-gying-inline:focus{outline:2px solid rgba(255,255,255,.9);outline-offset:2px;}"
      + ".fm-gying-tv #" + CONFIG.rootId + "{right:44px;bottom:36px;}"
      + ".fm-gying-tv .fm-gying-panel{width:520px;max-height:72vh;}"
      + ".fm-gying-tv .fm-gying-body{max-height:calc(72vh - 48px);}"
      + ".fm-gying-tv .fm-gying-title{font-size:21px;}"
      + ".fm-gying-tv .fm-gying-sub,.fm-gying-tv .fm-gying-msg{font-size:18px;}"
      + ".fm-gying-tv .fm-gying-item button,.fm-gying-tv .fm-gying-link button{min-height:52px;font-size:20px;}";
    if (typeof GM_addStyle === "function") GM_addStyle(css);
    else {
      var style = document.createElement("style");
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  function readCssPixels(name) {
    if (!document.documentElement || !window.getComputedStyle) return 0;
    var value = "";
    try {
      value = getComputedStyle(document.documentElement).getPropertyValue(name);
    } catch (e) {}
    var number = parseFloat(value);
    return isNaN(number) ? 0 : number;
  }

  function shouldUseTopSafeArea(detail) {
    detail = detail || {};
    var mode = cleanText(detail.chromeMode || state.safeChromeMode || "").toLowerCase();
    var systemBarsHidden = detail.systemBarsHidden;
    if (typeof systemBarsHidden === "undefined") systemBarsHidden = state.safeSystemBarsHidden;
    var safeTop = Number(detail.safeTop);
    if (isNaN(safeTop)) safeTop = readCssPixels("--fm-safe-top");
    var statusBarHeight = Number(detail.statusBarHeight);
    if (isNaN(statusBarHeight)) statusBarHeight = readCssPixels("--fm-status-bar-height");
    if (window.fongmiClient && window.fongmiClient.isLeanback) return false;
    if (systemBarsHidden && safeTop <= 0 && statusBarHeight <= 0) return false;
    if (mode === "edge" || mode === "immersive") return safeTop > 0 || statusBarHeight > 0;
    return false;
  }

  function updateSafeAreaMode(detail) {
    detail = detail || {};
    if (detail.chromeMode) state.safeChromeMode = cleanText(detail.chromeMode).toLowerCase();
    if (typeof detail.systemBarsHidden !== "undefined") state.safeSystemBarsHidden = !!detail.systemBarsHidden;
    if (!document.documentElement) return;
    if (shouldUseTopSafeArea(detail)) addClass(document.documentElement, "fm-gying-safe-top-active");
    else removeClass(document.documentElement, "fm-gying-safe-top-active");
  }

  function initSafeAreaMode() {
    updateSafeAreaMode({});
    whenFm().then(function (sdk) {
      if (!sdk.ui || !sdk.ui.getViewport) return;
      return sdk.ui.getViewport().then(function (viewport) {
        updateSafeAreaMode(viewport || {});
      });
    }).catch(function () {});
  }

  function boot() {
    installMediaHooks();
    installStyle();
    initSafeAreaMode();
    ready(function () {
      ensureRoot();
      document.addEventListener("pointerdown", onInlinePress, true);
      document.addEventListener("touchstart", onInlinePress, true);
      document.addEventListener("mousedown", onInlinePress, true);
      document.addEventListener("click", onPanelClick, true);
      document.addEventListener("keydown", onKeydown, true);
      new MutationObserver(scheduleScan).observe(document.documentElement, { childList: true, subtree: true });
      scanPage();
    });
    window.addEventListener("fmurlchange", scheduleScan);
    window.addEventListener("popstate", scheduleScan);
    window.addEventListener("fmresume", scheduleScan);
    window.addEventListener("fmviewport", function (event) {
      updateSafeAreaMode(event && event.detail ? event.detail : {});
    });
    log("ready", location.href);
  }

  boot();
})();
