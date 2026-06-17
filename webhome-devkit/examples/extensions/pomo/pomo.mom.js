// WebHome extension for https://pomo.mom/
// Optimized behavior:
// - Build one clean native playback panel on the detail page.
// - Group resources into 在线播放 / 网盘 / 磁力.
// - Click any row to call the App native playback/push ability directly.
// - Simplify Pomo mobile pages into a search-first movie UI with TV focus states.
(function () {
  const CONFIG = {
    panelId: "fm-pomo-panel",
    searchId: "fm-pomo-search",
    titleSelector: ".x-dbjs-title",
    detailCardSelector: ".x-dbjs-card",
    downloadSectionSelector: "#x-dbjs-download-section",
    scanDelay: 160,
    onlineTimeout: 20
  };

  const state = {
    activeTab: "online",
    onlineLoading: false,
    onlineLoaded: false,
    onlinePage: "",
    playOnlineWhenReady: false,
    items: {
      online: [],
      pan: [],
      magnet: []
    }
  };

  const PAN_TYPES = [
    ["quark", /pan\.quark\.cn/i, "夸克"],
    ["aliyun", /aliyundrive\.com|alipan\.com/i, "阿里"],
    ["baidu", /pan\.baidu\.com/i, "百度"],
    ["uc", /drive\.uc\.cn/i, "UC"],
    ["xunlei", /pan\.xunlei\.com/i, "迅雷"],
    ["tianyi", /cloud\.189\.cn/i, "天翼"],
    ["123", /123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i, "123"],
    ["115", /115\.com|115cdn\.com/i, "115"],
    ["mobile", /yun\.139\.com|caiyun\.139\.com/i, "移动云"]
  ];

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-pomo]"].concat(args));
  }

  function toast(message) {
    try {
      if (window.fm && fm.ext && fm.ext.toast) return fm.ext.toast(message);
    } catch (e) {
      // ignore
    }
    return Promise.resolve();
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => {
      window.addEventListener("fmsdk", () => resolve(window.fm), { once: true });
    });
  }

  function ready(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn, { once: true });
    } else {
      fn();
    }
  }

  function cleanText(text) {
    return String(text || "").replace(/\s+/g, " ").trim();
  }

  function pageTitle() {
    const detailTitle = document.querySelector(CONFIG.titleSelector);
    const text = detailTitle && detailTitle.textContent ? cleanText(detailTitle.textContent) : "";
    return text || cleanText(document.title.replace(/\s*-\s*(4K.*|在线播放).*$/i, "")) || location.href;
  }

  function isDetailPage() {
    return !!(document.querySelector(CONFIG.detailCardSelector) || document.querySelector(CONFIG.downloadSectionSelector));
  }

  function currentKeyword() {
    try {
      const params = new URLSearchParams(location.search);
      const keyword = cleanText(params.get("keyword"));
      if (keyword) return keyword;
    } catch (e) {
      // ignore
    }
    const input = document.querySelector("input[name='keyword']");
    return input ? cleanText(input.value || input.getAttribute("value")) : "";
  }

  function gid() {
    if (typeof window.current_logid !== "undefined" && window.current_logid) return String(window.current_logid);
    const playLink = findPlayPageLink();
    const match = playLink.match(/[?&]gid=(\d+)/i);
    if (match) return match[1];
    const path = location.pathname.match(/\/(\d+)(?:\/)?$/);
    return path ? path[1] : "";
  }

  function findPlayPageLink() {
    const links = document.querySelectorAll("a[href*='plugin=plyr_player']");
    for (let i = 0; i < links.length; i++) {
      const href = links[i].getAttribute("href");
      if (href) return absoluteUrl(href);
    }
    const id = gidFromPathOnly();
    return id ? location.origin + "/?plugin=plyr_player&gid=" + encodeURIComponent(id) : "";
  }

  function gidFromPathOnly() {
    const path = location.pathname.match(/\/(\d+)(?:\/)?$/);
    return path ? path[1] : "";
  }

  function absoluteUrl(url) {
    if (!url || url === "#" || /^javascript:/i.test(url)) return "";
    if (/^(magnet:|ed2k:|thunder:)/i.test(url)) return url;
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return url;
    }
  }

  function classify(url) {
    if (/^magnet:/i.test(url)) return { type: "magnet", group: "magnet", label: "磁力" };
    if (/^ed2k:/i.test(url)) return { type: "ed2k", group: "magnet", label: "电驴" };
    if (/^thunder:/i.test(url)) return { type: "thunder", group: "magnet", label: "迅雷" };
    for (let i = 0; i < PAN_TYPES.length; i++) {
      if (PAN_TYPES[i][1].test(url)) return { type: PAN_TYPES[i][0], group: "pan", label: PAN_TYPES[i][2] };
    }
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url)) return { type: "media", group: "online", label: "直链" };
    return { type: "http", group: "pan", label: "链接" };
  }

  function addItem(group, item) {
    if (!item || !item.url) return;
    const list = state.items[group];
    for (let i = 0; i < list.length; i++) {
      if (list[i].url === item.url) return;
    }
    item.index = list.length + 1;
    list.push(item);
  }

  function collectDownloadItems() {
    state.items.pan = [];
    state.items.magnet = [];

    const section = document.querySelector(CONFIG.downloadSectionSelector);
    if (!section) return;

    const rows = section.querySelectorAll(".download-item");
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const link = row.querySelector(".x-dbjs-download-link,[data-url],a[href]");
      const url = urlFrom(link);
      if (!url) continue;

      const info = classify(url);
      const category = categoryTitle(row);
      const sizeEl = row.querySelector(".file-size");
      const rawTitle = link ? cleanText(link.textContent) : "";
      const title = rawTitle || category || pageTitle();
      const subtitle = [category, sizeEl ? cleanText(sizeEl.textContent).replace(/^\[|\]$/g, "") : ""].filter(Boolean).join(" · ");
      addItem(info.group, {
        url: url,
        type: info.type,
        badge: info.label,
        title: title,
        subtitle: subtitle,
        source: "download"
      });
    }

    const actionLinks = document.querySelectorAll(".x-dbjs-actions a[href]");
    for (let i = 0; i < actionLinks.length; i++) {
      const url = urlFrom(actionLinks[i]);
      if (!url || /plugin=plyr_player/i.test(url)) continue;
      const info = classify(url);
      if (info.group !== "pan" && info.group !== "magnet") continue;
      addItem(info.group, {
        url: url,
        type: info.type,
        badge: info.label,
        title: pageTitle(),
        subtitle: "页面快捷入口",
        source: "action"
      });
    }
  }

  function urlFrom(element) {
    if (!element) return "";
    const attrs = ["data-url", "data-href", "data-link", "data-clipboard-text", "href"];
    for (let i = 0; i < attrs.length; i++) {
      const value = element.getAttribute(attrs[i]);
      const url = absoluteUrl(value);
      if (url) return url;
    }
    return "";
  }

  function categoryTitle(row) {
    const item = row.closest(".x-dbjs-accordion-item");
    const title = item && item.querySelector(".x-dbjs-accordion-title");
    return title ? cleanText(title.textContent) : "";
  }

  async function loadOnlineItems() {
    if (state.onlineLoaded || state.onlineLoading) return;
    const playPage = findPlayPageLink();
    if (!playPage) {
      state.onlineLoaded = true;
      render();
      return;
    }

    state.onlineLoading = true;
    state.onlinePage = playPage;
    render();

    try {
      const sdk = await whenFm();
      const response = await sdk.req(playPage, {
        responseType: "text",
        timeout: CONFIG.onlineTimeout,
        credentials: "include",
        headers: { Referer: location.href }
      });
      const html = response && response.body ? response.body : "";
      parseOnlinePage(html, playPage);
    } catch (error) {
      log("online page load failed", error && (error.message || error));
      addItem("online", {
        url: playPage,
        type: "http",
        badge: "网页",
        title: "在线播放页",
        subtitle: "未解析到选集，点击进入原播放页",
        source: "online-page"
      });
    } finally {
      state.onlineLoading = false;
      state.onlineLoaded = true;
      ensureActiveTab();
      render();
      if (state.playOnlineWhenReady && state.items.online.length) {
        state.playOnlineWhenReady = false;
        playItem("online", 0);
      }
    }
  }

  function parseOnlinePage(html, playPage) {
    const rawMatch = html.match(/const\s+rawData\s*=\s*(\[[\s\S]*?\]);/);
    if (!rawMatch) {
      log("rawData not found");
      return;
    }

    let rawData = [];
    try {
      rawData = JSON.parse(rawMatch[1]);
    } catch (e) {
      try {
        rawData = Function("return " + rawMatch[1])();
      } catch (error) {
        log("rawData parse failed", error && (error.message || error));
      }
    }

    if (!Array.isArray(rawData)) return;
    state.items.online = state.items.online.filter((item) => item.source !== "online-page");
    for (let i = 0; i < rawData.length; i++) {
      const parsed = parseEpisode(rawData[i], i, playPage);
      if (parsed) addItem("online", parsed);
    }
  }

  function parseEpisode(value, index, playPage) {
    const text = String(value || "");
    if (!text) return null;
    const split = text.indexOf("$");
    const title = split >= 0 ? text.substring(0, split) : "线路 " + (index + 1);
    const url = split >= 0 ? text.substring(split + 1) : text;
    if (!url) return null;
    return {
      url: absoluteUrl(url),
      type: "media",
      badge: "在线",
      title: cleanText(title) || "线路 " + (index + 1),
      subtitle: hostName(url),
      source: "online-page",
      playPage: playPage
    };
  }

  function hostName(url) {
    try {
      return new URL(url, location.href).hostname.replace(/^www\./, "");
    } catch (e) {
      return "";
    }
  }

  async function resolveOnlineUrl(item) {
    if (!item || item.type !== "media" || !item.playPage) return item.url;
    try {
      const api = location.origin + "/content/plugins/plyr_player/api.php?type=parse&url=" + encodeURIComponent(item.url);
      const sdk = await whenFm();
      const response = await sdk.req(api, {
        responseType: "json",
        timeout: CONFIG.onlineTimeout,
        credentials: "include",
        headers: { Referer: item.playPage }
      });
      const body = response && response.body ? response.body : null;
      if (body && Number(body.code) === 200 && body.data) return String(body.data);
    } catch (error) {
      log("online parse api failed", error && (error.message || error));
    }
    return item.url;
  }

  async function playItem(group, index) {
    const item = state.items[group] && state.items[group][index];
    if (!item) return;

    const sdk = await whenFm();
    const title = pageTitle() + " · " + item.title;
    setBusy(item, true);

    try {
      log("play", group, item.type, item.title, item.url);
      if (group === "online" && item.type === "media") {
        const url = await resolveOnlineUrl(item);
        return sdk.play(url, title, {
          headers: { Referer: item.playPage || location.href },
          credentials: "include"
        });
      }
      return sdk.pan.play({
        type: item.type,
        url: item.url,
        title: title
      });
    } catch (error) {
      log("play failed", error && (error.stack || error.message) || error);
      toast("调用原生播放失败");
    } finally {
      setBusy(item, false);
    }
  }

  function setBusy(item, busy) {
    item.busy = busy;
    render();
  }

  function ensureActiveTab() {
    if (state.items[state.activeTab] && state.items[state.activeTab].length) return;
    if (state.items.online.length) state.activeTab = "online";
    else if (state.items.pan.length) state.activeTab = "pan";
    else if (state.items.magnet.length) state.activeTab = "magnet";
  }

  function render() {
    let panel = document.getElementById(CONFIG.panelId);
    if (!panel) {
      panel = document.createElement("section");
      panel.id = CONFIG.panelId;
      panel.setAttribute("aria-label", "Pomo 播放列表");
      const anchor = document.querySelector(CONFIG.detailCardSelector) || document.querySelector(CONFIG.downloadSectionSelector);
      if (anchor && anchor.parentNode) anchor.parentNode.insertBefore(panel, anchor.nextSibling);
      else document.body.insertBefore(panel, document.body.firstChild);
    }

    const tabs = [
      ["online", "在线播放"],
      ["pan", "网盘"],
      ["magnet", "磁力"]
    ];
    const activeItems = state.items[state.activeTab] || [];
    const loading = state.activeTab === "online" && state.onlineLoading;
    const total = state.items.online.length + state.items.pan.length + state.items.magnet.length;

    panel.innerHTML = ""
      + "<div class='fm-pomo-head'>"
      + "  <div>"
      + "    <div class='fm-pomo-kicker'>Pomo</div>"
      + "    <div class='fm-pomo-title'>" + escapeHtml(pageTitle()) + "</div>"
      + "  </div>"
      + "  <div class='fm-pomo-count'>" + total + "</div>"
      + "</div>"
      + "<div class='fm-pomo-tabs' role='tablist'>"
      + tabs.map((tab) => tabButton(tab[0], tab[1])).join("")
      + "</div>"
      + "<div class='fm-pomo-list'>"
      + (loading ? "<div class='fm-pomo-empty'>正在解析在线播放...</div>" : activeItems.length ? activeItems.map(rowHtml).join("") : emptyHtml())
      + "</div>";
  }

  function tabButton(key, label) {
    const count = state.items[key].length;
    const active = key === state.activeTab ? " is-active" : "";
    return "<button type='button' class='fm-pomo-tab" + active + "' data-fm-tab='" + key + "'>"
      + "<span>" + escapeHtml(label) + "</span><b>" + count + "</b></button>";
  }

  function rowHtml(item, index) {
    const busy = item.busy ? " is-busy" : "";
    const subtitle = item.subtitle ? "<span class='fm-pomo-sub'>" + escapeHtml(item.subtitle) + "</span>" : "";
    return "<button type='button' class='fm-pomo-row" + busy + "' data-fm-group='" + groupOf(item) + "' data-fm-index='" + index + "'>"
      + "<span class='fm-pomo-badge'>" + escapeHtml(item.badge || "") + "</span>"
      + "<span class='fm-pomo-main'><span class='fm-pomo-name'>" + escapeHtml(item.title) + "</span>" + subtitle + "</span>"
      + "<span class='fm-pomo-action'>" + (item.busy ? "..." : "播放") + "</span>"
      + "</button>";
  }

  function groupOf(item) {
    if (state.items.online.indexOf(item) >= 0) return "online";
    if (state.items.pan.indexOf(item) >= 0) return "pan";
    return "magnet";
  }

  function emptyHtml() {
    return "<div class='fm-pomo-empty'>暂无可播放资源</div>";
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c];
    });
  }

  function onPanelClick(event) {
    const tab = event.target.closest("[data-fm-tab]");
    if (tab) {
      state.activeTab = tab.getAttribute("data-fm-tab");
      render();
      return;
    }

    const row = event.target.closest("[data-fm-group][data-fm-index]");
    if (!row) return;
    event.preventDefault();
    event.stopPropagation();
    playItem(row.getAttribute("data-fm-group"), Number(row.getAttribute("data-fm-index")));
  }

  function interceptOriginalClicks(event) {
    if (event.target.closest(".x-dbjs-copy-btn")) return;

    const original = event.target.closest(".x-dbjs-download-link,.x-dbjs-download-btn,.x-dbjs-actions a[href]");
    if (!original) return;

    const url = urlFrom(original);
    if (!url) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();

    if (/plugin=plyr_player/i.test(url)) {
      if (!state.items.online.length && !state.onlineLoaded) loadOnlineItems();
      state.activeTab = "online";
      render();
      if (state.items.online.length) playItem("online", 0);
      else {
        state.playOnlineWhenReady = true;
        toast("在线播放解析中");
      }
      return;
    }

    const info = classify(url);
    const title = cleanText(original.textContent) || pageTitle();
    playItemObject(info.group, {
      url: url,
      type: info.type,
      badge: info.label,
      title: title,
      subtitle: "页面入口"
    });
  }

  function playItemObject(group, item) {
    addItem(group, item);
    ensureActiveTab();
    const list = state.items[group];
    for (let i = 0; i < list.length; i++) {
      if (list[i].url === item.url) {
        state.activeTab = group;
        render();
        playItem(group, i);
        return;
      }
    }
  }

  function enhancePage() {
    cleanupSiteNotice();
    document.documentElement.classList.add("fm-pomo-enhanced");
    enhanceHeader();
    if (!isDetailPage()) enhanceListPage();
    enhanceDetailPage();
    enhanceFocusable();
  }

  function enhanceHeader() {
    const login = document.querySelector("a[href*='Ixc_login_but_login']");
    if (login) login.classList.add("fm-pomo-hide");

    const nav = document.querySelector("header nav");
    if (nav) nav.classList.add("fm-pomo-nav");
  }

  function cleanupSiteNotice() {
    const nodes = document.querySelectorAll("h1,h2,h3,h4,h5,h6,[role='heading'],[class*='title'],[class*='Title'],[class*='modal'],[class*='Modal'],[class*='notice'],[class*='Notice']");
    let removed = false;
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (!isNoticeTitle(cleanText(node.textContent))) continue;
      const root = noticeRoot(node);
      if (!root) continue;
      root.remove();
      removed = true;
    }
    if (!removed) return;
    cleanupNoticeBackdrops();
    document.documentElement.style.overflow = "";
    if (document.body) document.body.style.overflow = "";
  }

  function isNoticeTitle(text) {
    return /^(网站公告|站点公告|公告)$/.test(text) || text.indexOf("网站公告") >= 0 && text.length <= 120;
  }

  function noticeRoot(node) {
    let best = null;
    for (let el = node; el && el !== document.body && el !== document.documentElement; el = el.parentElement) {
      if (el.id === CONFIG.panelId || el.id === CONFIG.searchId) return null;
      if (isNoticeRootCandidate(el)) best = el;
    }
    if (best) return best;
    const fallback = node.closest("[role='dialog'],[class*='modal'],[class*='Modal'],[class*='dialog'],[class*='Dialog'],[class*='popup'],[class*='Popup'],[class*='notice'],[class*='Notice']");
    if (fallback && fallback !== document.body && fallback !== document.documentElement) return fallback;
    let parent = node;
    for (let depth = 0; depth < 4 && parent && parent.parentElement && parent.parentElement !== document.body; depth++) parent = parent.parentElement;
    return parent === node ? null : parent;
  }

  function isNoticeRootCandidate(el) {
    const cls = String(el.className || "");
    const role = String(el.getAttribute("role") || "");
    let fixed = /\b(fixed|inset-0|overlay|backdrop)\b/i.test(cls);
    try {
      const style = window.getComputedStyle(el);
      fixed = fixed || style.position === "fixed";
    } catch (e) {
      // ignore
    }
    return role === "dialog" || /\b(modal|dialog|popup|notice)\b/i.test(cls) || fixed;
  }

  function cleanupNoticeBackdrops() {
    const nodes = document.querySelectorAll(".modal-backdrop,.backdrop,[class*='overlay'],[class*='Overlay'],[class*='fixed'][class*='inset-0']");
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node.id === CONFIG.panelId || node.id === CONFIG.searchId) continue;
      const text = cleanText(node.textContent);
      if (text && !/公告/.test(text) && text.length > 20) continue;
      if (isNoticeRootCandidate(node)) node.remove();
    }
  }

  function enhanceListPage() {
    const hero = document.getElementById("hero-carousel");
    if (hero && hero.parentElement) hero.parentElement.classList.add("fm-pomo-hero-wrap");
    ensureSearchHero();
    enhanceMovieCards();
    enhanceFilters();
  }

  function ensureSearchHero() {
    if (document.getElementById(CONFIG.searchId)) {
      syncSearchHero();
      return;
    }

    const host = document.createElement("section");
    host.id = CONFIG.searchId;
    host.innerHTML = ""
      + "<form class='fm-pomo-search-form' action='" + escapeHtml(location.origin + "/") + "' method='get'>"
      + "  <div class='fm-pomo-search-label'>Pomo</div>"
      + "  <div class='fm-pomo-search-row'>"
      + "    <input class='fm-pomo-search-input' type='search' name='keyword' placeholder='搜索电影、剧集、演员' autocomplete='off'>"
      + "    <button class='fm-pomo-search-button' type='submit'>搜索</button>"
      + "  </div>"
      + "  <div class='fm-pomo-search-meta'></div>"
      + "</form>";

    const main = document.querySelector("main");
    if (main) main.insertBefore(host, main.firstChild);
    else document.body.insertBefore(host, document.body.firstChild);

    host.addEventListener("submit", function (event) {
      const input = host.querySelector("input[name='keyword']");
      const keyword = cleanText(input && input.value);
      if (!keyword) {
        event.preventDefault();
        if (input) input.focus();
      }
    });

    syncSearchHero();
  }

  function syncSearchHero() {
    const host = document.getElementById(CONFIG.searchId);
    if (!host) return;
    const keyword = currentKeyword();
    const input = host.querySelector("input[name='keyword']");
    const meta = host.querySelector(".fm-pomo-search-meta");
    if (input && document.activeElement !== input) input.value = keyword;
    if (meta) {
      const text = keyword ? "正在显示 “" + keyword + "” 的搜索结果" : "最新 4K 影视";
      if (meta.textContent !== text) meta.textContent = text;
    }
  }

  function enhanceMovieCards() {
    const grids = document.querySelectorAll("main .grid");
    for (let i = 0; i < grids.length; i++) {
      const grid = grids[i];
      if (!grid.querySelector("a[href] img")) continue;
      grid.classList.add("fm-pomo-grid");
      const cards = grid.children;
      for (let j = 0; j < cards.length; j++) enhanceMovieCard(cards[j]);
    }
  }

  function enhanceMovieCard(card) {
    if (!card || card.nodeType !== 1) return;
    const link = card.querySelector("a[href]");
    const image = card.querySelector("img");
    if (!link || !image) return;

    card.classList.add("fm-pomo-card");
    link.classList.add("fm-pomo-card-link");
    link.setAttribute("tabindex", "0");

    const poster = image.closest("a > div") || image.parentElement;
    if (poster) poster.classList.add("fm-pomo-card-poster");

    const heading = card.querySelector("h3,h4");
    const info = heading && heading.closest("a > div");
    if (info) info.classList.add("fm-pomo-card-info");

    const title = cleanText((heading || image).textContent || image.getAttribute("alt"));
    const sub = cleanText(card.querySelector(".text-gray-300,.text-gray-400") && card.querySelector(".text-gray-300,.text-gray-400").textContent);
    if (!card.querySelector(".fm-pomo-card-cta")) {
      const cta = document.createElement("span");
      cta.className = "fm-pomo-card-cta";
      cta.textContent = "详情";
      link.appendChild(cta);
    }
    if (title && !link.getAttribute("aria-label")) {
      link.setAttribute("aria-label", sub ? title + "，" + sub : title);
    }
  }

  function enhanceFilters() {
    const filter = document.querySelector(".filter-container");
    if (filter) filter.classList.add("fm-pomo-filter");
    const form = document.getElementById("filter-form");
    if (form) form.classList.add("fm-pomo-filter-form");
  }

  function enhanceDetailPage() {
    const card = document.querySelector(CONFIG.detailCardSelector);
    if (card) card.classList.add("fm-pomo-detail");

    const header = card && card.querySelector(".x-dbjs-header");
    if (header) header.classList.add("fm-pomo-detail-header");

    const poster = card && card.querySelector(".x-dbjs-poster");
    const rating = card && card.querySelector(".rating-badge");
    if (poster && rating && rating.parentElement !== poster) {
      poster.appendChild(rating);
    }

    const fav = card && card.querySelector(".fav-btn");
    if (fav) fav.classList.add("fm-pomo-hide");

    const desc = document.querySelector(".x-dbjs-desc-block");
    if (desc) {
      desc.classList.add("fm-pomo-detail-desc");
      const banner = detailBanner();
      const bannerImg = banner && banner.querySelector("img[src]");
      if (banner) banner.classList.add("fm-pomo-detail-banner");
      if (bannerImg) desc.style.setProperty("--fm-pomo-backdrop", "url(" + JSON.stringify(bannerImg.src) + ")");
    }

    const stills = document.querySelector(".pic-col5");
    if (stills) stills.classList.add("fm-pomo-stills");

    hideRelatedMovies();
  }

  function detailBanner() {
    const main = document.querySelector("main");
    if (!main) return null;
    const prev = main.previousElementSibling;
    if (prev && prev.querySelector("img[src]") && !prev.querySelector(CONFIG.detailCardSelector)) return prev;
    return null;
  }

  function hideRelatedMovies() {
    const headings = document.querySelectorAll("h2,h3,h4");
    for (let i = 0; i < headings.length; i++) {
      if (cleanText(headings[i].textContent) !== "探索更多") continue;
      const section = headings[i].closest(".mt-24") || headings[i].parentElement;
      if (section) section.classList.add("fm-pomo-related-hide");
    }
  }

  function enhanceFocusable() {
    const selectors = [
      "a[href]",
      "button",
      "input",
      "select",
      ".x-dbjs-accordion-header",
      ".x-dbjs-category-btn"
    ];
    const nodes = document.querySelectorAll(selectors.join(","));
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node.closest(".fm-pomo-hide")) continue;
      if (!node.hasAttribute("tabindex") && !/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/i.test(node.tagName)) {
        node.setAttribute("tabindex", "0");
      }
    }
  }

  function onKeyboardActivate(event) {
    if (event.key !== "Enter" && event.key !== " ") return;
    const target = event.target.closest(".x-dbjs-accordion-header,.x-dbjs-category-btn");
    if (!target) return;
    event.preventDefault();
    target.click();
  }

  function scan() {
    enhancePage();
    if (!document.querySelector(CONFIG.detailCardSelector) && !document.querySelector(CONFIG.downloadSectionSelector)) return;
    collectDownloadItems();
    ensureActiveTab();
    render();
    loadOnlineItems();
  }

  function scheduleScan() {
    clearTimeout(scheduleScan.timer);
    scheduleScan.timer = setTimeout(scan, CONFIG.scanDelay);
  }

  function installObserver() {
    new MutationObserver((mutations) => {
      for (let i = 0; i < mutations.length; i++) {
        if (!isOwnMutation(mutations[i])) {
          scheduleScan();
          return;
        }
      }
    }).observe(document.documentElement, { childList: true, subtree: true });
  }

  function isOwnMutation(mutation) {
    const panel = document.getElementById(CONFIG.panelId);
    if (!panel) return false;
    if (panel.contains(mutation.target)) return true;
    const nodes = Array.prototype.slice.call(mutation.addedNodes || []).concat(Array.prototype.slice.call(mutation.removedNodes || []));
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node === panel || node.nodeType === 1 && panel.contains(node)) return true;
    }
    return false;
  }

  function installStyle() {
    const css = `
      .fm-pomo-enhanced body {
        background: #f5f7fa !important;
        color: #111827;
      }
      .dark.fm-pomo-enhanced body {
        background: #0d1116 !important;
        color: #e5e7eb;
      }
      .fm-pomo-enhanced body > .absolute.top-0.left-0.w-full {
        display: none !important;
      }
      .fm-pomo-enhanced * {
        -webkit-tap-highlight-color: transparent;
      }
      .fm-pomo-hide,
      .fm-pomo-hero-wrap,
      .fm-pomo-detail-banner,
      .fm-pomo-related-hide,
      #mobile-search-btn,
      #mobile-search-box,
      #mobile-nav-btn,
      #mobile-nav-menu,
      header form[action],
      footer,
      #floating-tools {
        display: none !important;
      }
      .fm-pomo-enhanced header {
        max-width: 1228px !important;
        min-height: 58px !important;
        height: auto !important;
        padding: 10px 14px !important;
        position: sticky !important;
        top: 0;
        z-index: 80;
        background: rgba(10, 10, 10, .96);
        border-bottom: 1px solid rgba(255, 255, 255, .08);
      }
      .fm-pomo-enhanced header img {
        max-height: 30px !important;
      }
      .fm-pomo-enhanced header > div:first-child {
        min-width: 0;
        gap: 14px !important;
      }
      .fm-pomo-enhanced header .fm-pomo-nav,
      .fm-pomo-enhanced #mobile-nav-menu nav {
        display: flex !important;
        gap: 6px !important;
        overflow-x: auto;
        scrollbar-width: thin;
        -webkit-overflow-scrolling: touch;
      }
      .fm-pomo-enhanced header nav a,
      .fm-pomo-enhanced #mobile-nav-menu nav a {
        min-height: 44px;
        padding: 0 10px !important;
        border-radius: 8px !important;
        display: inline-flex !important;
        align-items: center;
        color: #d1d5db !important;
        white-space: nowrap;
        font-size: 12px !important;
        letter-spacing: 0;
      }
      .fm-pomo-enhanced header nav a span.absolute,
      .fm-pomo-enhanced #mobile-nav-menu nav a span.absolute {
        display: none !important;
      }
      .fm-pomo-enhanced main {
        max-width: 1228px !important;
        padding: 0 14px 28px !important;
      }
      .fm-pomo-enhanced #pjax-container {
        transition: none !important;
      }
      #${CONFIG.searchId} {
        max-width: 1228px;
        margin: 12px auto 14px;
      }
      .fm-pomo-search-form {
        padding: 12px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #fff;
        box-shadow: 0 8px 24px rgba(15, 23, 42, .07);
      }
      .dark .fm-pomo-search-form {
        border-color: #26313c;
        background: #121820;
        box-shadow: none;
      }
      .fm-pomo-search-label {
        color: #0f766e;
        font-size: 13px;
        font-weight: 900;
        line-height: 1;
      }
      .dark .fm-pomo-search-label {
        color: #5eead4;
      }
      .fm-pomo-search-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 8px;
        margin-top: 9px;
      }
      .fm-pomo-search-input {
        width: 100%;
        min-width: 0;
        min-height: 48px;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        background: #f8fafc;
        color: #111827;
        padding: 0 13px;
        font-size: 15px;
        outline: none;
      }
      .dark .fm-pomo-search-input {
        border-color: #334155;
        background: #0f141b;
        color: #f8fafc;
      }
      .fm-pomo-search-button {
        min-width: 72px;
        min-height: 48px;
        border: 1px solid #0f766e;
        border-radius: 8px;
        background: #0f766e;
        color: #fff;
        padding: 0 14px;
        font-size: 15px;
        font-weight: 900;
      }
      .fm-pomo-search-meta {
        min-height: 18px;
        margin-top: 8px;
        color: #64748b;
        font-size: 12px;
        line-height: 1.45;
      }
      .dark .fm-pomo-search-meta {
        color: #94a3b8;
      }
      .fm-pomo-enhanced .filter-container {
        margin-bottom: 12px !important;
      }
      .fm-pomo-enhanced .filter-container h2 {
        margin: 8px 0 10px !important;
        color: #111827 !important;
        font-size: 18px !important;
        line-height: 1.25 !important;
      }
      .dark.fm-pomo-enhanced .filter-container h2 {
        color: #f8fafc !important;
      }
      .fm-pomo-enhanced .compact-filter {
        padding: 0 !important;
      }
      .fm-pomo-enhanced .fm-pomo-filter-form {
        display: grid !important;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 8px !important;
        align-items: stretch !important;
      }
      .fm-pomo-enhanced .compact-select,
      .fm-pomo-enhanced .compact-btn,
      .fm-pomo-enhanced .compact-btn-outline {
        min-height: 44px;
        border-radius: 8px !important;
        font-size: 13px !important;
      }
      .fm-pomo-enhanced .compact-btn,
      .fm-pomo-enhanced .compact-btn-outline {
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }
      .fm-pomo-enhanced .fm-pomo-grid {
        display: grid !important;
        grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
        gap: 10px !important;
      }
      .fm-pomo-card {
        min-width: 0;
        border: 1px solid #e5e7eb !important;
        border-radius: 8px !important;
        background: #fff !important;
        box-shadow: 0 4px 14px rgba(15, 23, 42, .06) !important;
        overflow: hidden !important;
        transition: transform .15s ease, border-color .15s ease, box-shadow .15s ease !important;
      }
      .dark .fm-pomo-card {
        border-color: #26313c !important;
        background: #121820 !important;
        box-shadow: none !important;
      }
      .fm-pomo-card-link {
        position: relative;
        min-height: 100%;
        color: inherit !important;
        text-decoration: none !important;
      }
      .fm-pomo-card-poster {
        aspect-ratio: 2 / 3 !important;
        height: auto !important;
        background: #dbe3ec;
      }
      .fm-pomo-card img {
        width: 100% !important;
        height: 100% !important;
        object-fit: cover !important;
        transform: none !important;
        transition: none !important;
      }
      .fm-pomo-card .bg-gradient-to-t,
      .fm-pomo-card .absolute.inset-0:not(.w-full) {
        display: none !important;
      }
      .fm-pomo-card-info {
        margin-top: 0 !important;
        padding: 8px !important;
        background: #fff !important;
        position: relative !important;
      }
      .dark .fm-pomo-card-info {
        background: #121820 !important;
      }
      .fm-pomo-card h3,
      .fm-pomo-card h4 {
        margin: 0 0 5px !important;
        color: #111827 !important;
        font-size: 14px !important;
        line-height: 1.35 !important;
        font-weight: 900 !important;
        white-space: nowrap !important;
        overflow: hidden !important;
        text-overflow: ellipsis !important;
        text-shadow: none !important;
      }
      .dark .fm-pomo-card h3,
      .dark .fm-pomo-card h4 {
        color: #f8fafc !important;
      }
      .fm-pomo-card .text-gray-300,
      .fm-pomo-card .text-gray-400 {
        color: #64748b !important;
        font-size: 12px !important;
        line-height: 1.35 !important;
        text-shadow: none !important;
      }
      .dark .fm-pomo-card .text-gray-300,
      .dark .fm-pomo-card .text-gray-400 {
        color: #94a3b8 !important;
      }
      .fm-pomo-card .tag-container {
        gap: 5px !important;
        padding-top: 7px !important;
      }
      .fm-pomo-card .tag {
        min-height: 25px !important;
        border-radius: 6px !important;
        background: #eef2ff !important;
        color: #3730a3 !important;
        box-shadow: none !important;
        font-size: 11px !important;
      }
      .fm-pomo-card .tag .highlight {
        color: #be123c !important;
      }
      .fm-pomo-card-cta {
        position: absolute;
        top: 8px;
        right: 8px;
        min-height: 28px;
        padding: 0 8px;
        border-radius: 8px;
        background: rgba(15, 23, 42, .82);
        color: #fff;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 900;
        pointer-events: none;
      }
      .fm-pomo-enhanced .load-more-container {
        margin-top: 18px !important;
      }
      .fm-pomo-enhanced .load-more-btn,
      .fm-pomo-enhanced .pagination-container a,
      .fm-pomo-enhanced .pagination-container span {
        min-width: 44px !important;
        min-height: 44px !important;
        border-radius: 8px !important;
      }
      .fm-pomo-detail {
        border: 1px solid #e2e8f0 !important;
        border-radius: 8px !important;
        background: #fff !important;
        color: #111827 !important;
        padding: 14px !important;
        box-shadow: 0 6px 20px rgba(15, 23, 42, .07) !important;
      }
      .dark .fm-pomo-detail {
        border-color: #26313c !important;
        background: #121820 !important;
        color: #f8fafc !important;
        box-shadow: none !important;
      }
      .fm-pomo-detail .x-dbjs-header {
        display: grid !important;
        grid-template-columns: 128px minmax(0, 1fr);
        gap: 12px !important;
        align-items: start !important;
      }
      .fm-pomo-detail .x-dbjs-poster {
        width: auto !important;
        margin: 0 !important;
        position: relative !important;
      }
      .fm-pomo-detail .x-dbjs-poster img {
        width: 100% !important;
        max-height: none !important;
        aspect-ratio: 2 / 3;
        object-fit: cover;
        border-radius: 8px !important;
        box-shadow: none !important;
      }
      .fm-pomo-detail .rating-badge {
        position: absolute !important;
        top: 8px;
        right: 8px;
        z-index: 3;
        min-height: 28px;
        padding: 0 9px !important;
        border: 1px solid rgba(0, 0, 0, .12) !important;
        border-radius: 8px !important;
        background: rgba(245, 197, 24, .96) !important;
        color: #111827 !important;
        display: inline-flex !important;
        align-items: center;
        justify-content: center;
        font-size: 12px !important;
        font-weight: 900 !important;
        box-shadow: 0 6px 16px rgba(0, 0, 0, .20);
      }
      .fm-pomo-detail .x-dbjs-info {
        width: auto !important;
        min-width: 0;
        text-align: left !important;
      }
      .fm-pomo-detail .x-dbjs-title {
        margin: 0 0 10px !important;
        color: #111827 !important;
        font-size: 20px !important;
        line-height: 1.28 !important;
      }
      .dark .fm-pomo-detail .x-dbjs-title {
        color: #f8fafc !important;
      }
      .fm-pomo-detail .x-dbjs-meta {
        color: #475569 !important;
        font-size: 13px !important;
        line-height: 1.6 !important;
      }
      .dark .fm-pomo-detail .x-dbjs-meta {
        color: #cbd5e1 !important;
      }
      .fm-pomo-detail .meta-row {
        margin-bottom: 4px !important;
      }
      .fm-pomo-detail .meta-row span {
        color: #64748b !important;
      }
      .fm-pomo-detail .x-dbjs-actions {
        margin-top: 12px !important;
        justify-content: flex-start !important;
      }
      .fm-pomo-detail-desc {
        position: relative;
        overflow: hidden;
        margin: 14px 0 10px !important;
        padding: 14px !important;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #f8fafc;
      }
      .dark .fm-pomo-detail-desc {
        border-color: #26313c;
        background: #0f141b;
      }
      .fm-pomo-detail-desc::before {
        content: "";
        position: absolute;
        inset: 0;
        background-image: linear-gradient(90deg, rgba(248, 250, 252, .96), rgba(248, 250, 252, .82)), var(--fm-pomo-backdrop);
        background-size: cover;
        background-position: center;
        opacity: .55;
        pointer-events: none;
      }
      .dark .fm-pomo-detail-desc::before {
        background-image: linear-gradient(90deg, rgba(15, 20, 27, .96), rgba(15, 20, 27, .82)), var(--fm-pomo-backdrop);
        opacity: .72;
      }
      .fm-pomo-detail-desc h3,
      .fm-pomo-detail-desc p {
        position: relative;
        z-index: 1;
      }
      .fm-pomo-detail-desc h3 {
        margin: 0 0 8px !important;
        color: #111827 !important;
        font-size: 17px !important;
        line-height: 1.3 !important;
      }
      .dark .fm-pomo-detail-desc h3 {
        color: #f8fafc !important;
      }
      .fm-pomo-detail-desc p {
        color: #334155 !important;
        font-size: 14px !important;
        line-height: 1.75 !important;
      }
      .dark .fm-pomo-detail-desc p {
        color: #d1d5db !important;
      }
      .fm-pomo-stills {
        display: grid !important;
        grid-auto-flow: column;
        grid-auto-columns: minmax(132px, 42%);
        gap: 8px !important;
        overflow-x: auto;
        padding: 2px 0 10px !important;
        margin: 0 !important;
        flex-wrap: nowrap !important;
        scrollbar-width: thin;
        -webkit-overflow-scrolling: touch;
      }
      .fm-pomo-stills li {
        width: auto !important;
        margin: 0 !important;
      }
      .fm-pomo-stills li .still-wrap {
        border-radius: 8px !important;
      }
      .fm-pomo-stills img {
        transform: none !important;
      }
      .fm-pomo-enhanced a:focus,
      .fm-pomo-enhanced button:focus,
      .fm-pomo-enhanced input:focus,
      .fm-pomo-enhanced select:focus,
      .fm-pomo-enhanced [tabindex]:focus {
        outline: 3px solid #14b8a6 !important;
        outline-offset: 3px !important;
        box-shadow: 0 0 0 5px rgba(20, 184, 166, .18) !important;
      }
      .fm-pomo-card:focus-within {
        border-color: #14b8a6 !important;
        transform: translateY(-2px);
        box-shadow: 0 10px 28px rgba(15, 118, 110, .20) !important;
      }
      @media (min-width: 700px) {
        .fm-pomo-enhanced .fm-pomo-grid {
          grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
          gap: 12px !important;
        }
        .fm-pomo-enhanced .fm-pomo-filter-form {
          grid-template-columns: repeat(4, minmax(0, 1fr));
        }
        .fm-pomo-stills {
          grid-auto-columns: minmax(160px, 24%);
        }
      }
      @media (min-width: 1100px) {
        .fm-pomo-enhanced .fm-pomo-grid {
          grid-template-columns: repeat(6, minmax(0, 1fr)) !important;
        }
        .fm-pomo-enhanced .fm-pomo-filter-form {
          grid-template-columns: repeat(6, minmax(0, 1fr));
        }
        .fm-pomo-card h3,
        .fm-pomo-card h4 {
          font-size: 15px !important;
        }
      }
      @media (max-width: 420px) {
        .fm-pomo-enhanced header {
          padding-left: 10px !important;
          padding-right: 10px !important;
        }
        .fm-pomo-enhanced main {
          padding-left: 10px !important;
          padding-right: 10px !important;
        }
        .fm-pomo-detail .x-dbjs-header {
          grid-template-columns: 108px minmax(0, 1fr);
        }
        .fm-pomo-detail .x-dbjs-title {
          font-size: 18px !important;
        }
      }
      #${CONFIG.panelId} {
        margin: 16px 0 22px;
        padding: 14px;
        border: 1px solid rgba(15, 118, 110, .22);
        border-radius: 8px;
        background: #fff;
        color: #111827;
        box-shadow: 0 8px 22px rgba(15, 23, 42, .08);
      }
      .dark #${CONFIG.panelId} {
        border-color: rgba(20, 184, 166, .28);
        background: #101214;
        color: #f8fafc;
        box-shadow: 0 10px 28px rgba(0, 0, 0, .28);
      }
      #${CONFIG.panelId} * {
        box-sizing: border-box;
      }
      .fm-pomo-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
      }
      .fm-pomo-kicker {
        color: #0f766e;
        font-size: 12px;
        font-weight: 800;
        letter-spacing: .08em;
        text-transform: uppercase;
      }
      .dark .fm-pomo-kicker {
        color: #2dd4bf;
      }
      .fm-pomo-title {
        margin-top: 2px;
        font-size: 17px;
        line-height: 1.35;
        font-weight: 800;
      }
      .fm-pomo-count {
        min-width: 34px;
        height: 30px;
        padding: 0 10px;
        border-radius: 999px;
        background: #eef2ff;
        color: #4338ca;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 800;
      }
      .fm-pomo-tabs {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 8px;
        margin-top: 14px;
      }
      .fm-pomo-tab {
        min-width: 0;
        min-height: 44px;
        border: 1px solid #d7dee8;
        border-radius: 8px;
        background: #f8fafc;
        color: #334155;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 7px;
        font-size: 14px;
        font-weight: 800;
      }
      .fm-pomo-tab b {
        min-width: 20px;
        height: 20px;
        padding: 0 5px;
        border-radius: 999px;
        background: #e2e8f0;
        color: #334155;
        font-size: 12px;
        line-height: 20px;
      }
      .fm-pomo-tab.is-active {
        border-color: #0f766e;
        background: #0f766e;
        color: #fff;
      }
      .fm-pomo-tab.is-active b {
        background: rgba(255, 255, 255, .18);
        color: #fff;
      }
      .fm-pomo-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 12px;
      }
      .fm-pomo-row {
        width: 100%;
        min-height: 54px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #fff;
        color: #111827;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 10px;
        padding: 9px 10px;
        text-align: left;
      }
      .fm-pomo-row:active {
        transform: translateY(1px);
      }
      .fm-pomo-row.is-busy {
        opacity: .68;
      }
      .fm-pomo-badge {
        min-width: 42px;
        height: 28px;
        padding: 0 8px;
        border-radius: 999px;
        background: #ecfeff;
        color: #0e7490;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 800;
        white-space: nowrap;
      }
      .fm-pomo-main {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .fm-pomo-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 14px;
        line-height: 1.35;
        font-weight: 800;
      }
      .fm-pomo-sub {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: #64748b;
        font-size: 12px;
        line-height: 1.3;
      }
      .fm-pomo-action {
        min-width: 42px;
        color: #be123c;
        font-size: 13px;
        font-weight: 900;
        text-align: right;
      }
      .fm-pomo-empty {
        min-height: 56px;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        color: #64748b;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 14px;
        font-weight: 700;
      }
      .dark .fm-pomo-tab {
        border-color: #2f3a45;
        background: #171b20;
        color: #d5dde7;
      }
      .dark .fm-pomo-tab b {
        background: #26313c;
        color: #d5dde7;
      }
      .dark .fm-pomo-tab.is-active {
        border-color: #14b8a6;
        background: #0f766e;
        color: #fff;
      }
      .dark .fm-pomo-row {
        border-color: #2a343f;
        background: #15191e;
        color: #f8fafc;
      }
      .dark .fm-pomo-badge {
        background: rgba(45, 212, 191, .13);
        color: #5eead4;
      }
      .dark .fm-pomo-sub {
        color: #94a3b8;
      }
      .dark .fm-pomo-action {
        color: #fb7185;
      }
      .dark .fm-pomo-empty {
        border-color: #334155;
        color: #94a3b8;
      }
      .x-dbjs-actions .download-icon-btn,
      .x-dbjs-actions .play-btn,
      #x-dbjs-download-section {
        display: none !important;
      }
      @media (max-width: 640px) {
        #${CONFIG.panelId} {
          margin: 12px -2px 18px;
          padding: 12px;
          border-radius: 8px;
          box-shadow: none;
        }
        .fm-pomo-title {
          font-size: 16px;
        }
        .fm-pomo-tabs {
          gap: 6px;
        }
        .fm-pomo-tab {
          min-height: 42px;
          padding: 0 6px;
          font-size: 13px;
        }
        .fm-pomo-row {
          min-height: 58px;
          grid-template-columns: auto minmax(0, 1fr);
          gap: 9px;
        }
        .fm-pomo-action {
          grid-column: 2;
          min-width: 0;
          text-align: left;
          margin-top: -2px;
        }
      }
    `;
    if (typeof GM_addStyle === "function") GM_addStyle(css);
    else {
      const style = document.createElement("style");
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  ready(() => {
    installStyle();
    document.addEventListener("click", interceptOriginalClicks, true);
    document.addEventListener("click", onPanelClick, true);
    document.addEventListener("keydown", onKeyboardActivate, true);
    installObserver();
    scan();
    log("ready", location.href);
  });

  window.addEventListener("fmurlchange", () => {
    state.onlineLoading = false;
    state.onlineLoaded = false;
    state.onlinePage = "";
    state.playOnlineWhenReady = false;
    state.items.online = [];
    state.items.pan = [];
    state.items.magnet = [];
    scheduleScan();
  });
})();
