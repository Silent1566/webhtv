// Generic site cleanup template.
// Disable only the parts that actually hurt WebHome usage. Keep selectors conservative.
(function () {
  const CONFIG = {
    // Fill this with target-site selectors after inspection.
    hideSelectors: [
      ".popup",
      ".modal-ad",
      ".ad",
      "[class*='advert']",
      "[id*='advert']"
    ],
    unlockScroll: true,
    keepSameWindow: true,
    neutralizeContextMenuBlock: true,
    scanDelay: 200
  };

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-cleanup]"].concat(args));
  }

  function addStyle() {
    const css = CONFIG.hideSelectors.map((selector) => `${selector}{display:none!important;visibility:hidden!important;}`).join("\n")
      + "\nhtml,body{overscroll-behavior:auto!important;}";
    if (typeof GM_addStyle === "function") GM_addStyle(css);
  }

  function cleanupDom() {
    if (CONFIG.unlockScroll) {
      document.documentElement.style.overflow = "";
      document.body && (document.body.style.overflow = "");
      document.body && (document.body.style.position = "");
    }
    CONFIG.hideSelectors.forEach((selector) => {
      document.querySelectorAll(selector).forEach((el) => {
        if (isProbablyContent(el)) return;
        el.style.setProperty("display", "none", "important");
        el.dataset.fmCleanupHidden = "1";
      });
    });
  }

  function isProbablyContent(el) {
    const text = (el.textContent || "").trim();
    return text.length > 2000 && el.querySelector("article,.content,.detail,.vod,.download");
  }

  function hookWindowOpen() {
    if (!CONFIG.keepSameWindow || window.__fmCleanupOpen) return;
    window.__fmCleanupOpen = true;
    const rawOpen = window.open;
    window.open = function (url, target, features) {
      if (url && /^https?:/i.test(url)) {
        location.href = url;
        return window;
      }
      return rawOpen ? rawOpen.apply(window, arguments) : null;
    };
  }

  function unblockEvents() {
    if (!CONFIG.neutralizeContextMenuBlock || window.__fmCleanupEvents) return;
    window.__fmCleanupEvents = true;
    document.addEventListener("contextmenu", function (event) {
      event.stopPropagation();
    }, true);
    document.addEventListener("selectstart", function (event) {
      event.stopPropagation();
    }, true);
    document.oncontextmenu = null;
    document.onselectstart = null;
    document.onkeydown = null;
  }

  function schedule() {
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(cleanupDom, CONFIG.scanDelay);
  }

  addStyle();
  hookWindowOpen();
  unblockEvents();
  cleanupDom();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["style", "class"] });
  window.addEventListener("fmurlchange", schedule);
  log("ready", location.href);
})();
