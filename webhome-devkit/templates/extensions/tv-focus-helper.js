// WebHome TV remote-focus helper.
// Makes a third-party website usable with a D-pad remote:
// - marks cards / list items focusable (tabindex)
// - injects layout-safe focus styles
// - maps OK/Enter to a native .click()
// - keeps text inputs readonly until the user confirms editing
// - restores focus after App resume
//
// Designed to be loaded as a shared dependency:
//   { "id": "tv-focus-helper", "js": ["./tv-focus-helper.js"] }
//   { "id": "my-site", "depends": ["tv-focus-helper"], ... }
// It does nothing on mobile (fongmiClient.isLeanback === false).
(function () {
  const CONFIG = {
    // Things that should be reachable with the remote but are not natively
    // focusable. <a href> and <button> are skipped automatically.
    focusSelector: ".card,.item,.vod-item,.module-item,.swiper-slide,[role='button']",
    // Inputs guarded against accidental IME popups while moving focus.
    inputSelector: "input[type='text'],input[type='search'],input:not([type]),textarea",
    focusClass: "fm-tv-focusable",
    scanDelay: 160
  };

  if (!(window.fongmiClient && window.fongmiClient.isLeanback)) return;

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-tv]"].concat(args));
  }

  // ---------- focusable marking ----------

  function markFocusable() {
    document.querySelectorAll(CONFIG.focusSelector).forEach(function (el) {
      if (el.dataset.fmTvReady === "1") return;
      el.dataset.fmTvReady = "1";
      el.classList.add(CONFIG.focusClass);
      const tag = el.tagName;
      if (tag !== "A" && tag !== "BUTTON" && tag !== "INPUT" && tag !== "TEXTAREA" && tag !== "SELECT" && !el.hasAttribute("tabindex")) {
        el.setAttribute("tabindex", "0");
      }
    });
    guardInputs();
  }

  // ---------- OK / Enter handling ----------

  document.addEventListener("keydown", function (event) {
    if (event.key !== "Enter" && event.keyCode !== 13 && event.keyCode !== 23) return;
    const el = document.activeElement;
    if (!el || el === document.body) return;
    // Inputs: first OK enters edit mode, handled by guardInputs below.
    if (el.matches && el.matches(CONFIG.inputSelector)) return;
    // Native buttons and links already handle Enter; only synthesize for
    // tabindex elements that have no default activation behavior.
    if (el.tagName === "A" || el.tagName === "BUTTON") return;
    if (el.dataset.fmTvReady === "1") {
      event.preventDefault();
      // Prefer native .click(): synthetic MouseEvent("click") is unreliable
      // on some old Android WebViews.
      el.click();
    }
  }, true);

  // ---------- input edit-mode guard ----------

  // TV inputs stay readonly while focus passes over them; OK / touch unlocks
  // editing, blur or Back restores readonly. Prevents the IME from popping up
  // on every focus move across a search box.
  function guardInputs() {
    document.querySelectorAll(CONFIG.inputSelector).forEach(function (input) {
      if (input.dataset.fmTvInput === "1") return;
      input.dataset.fmTvInput = "1";
      if (!input.readOnly) {
        input.readOnly = true;
        input.dataset.fmTvGuarded = "1";
      }
      input.addEventListener("keydown", function (event) {
        if ((event.key === "Enter" || event.keyCode === 13 || event.keyCode === 23) && input.readOnly && input.dataset.fmTvGuarded === "1") {
          event.preventDefault();
          input.readOnly = false;
          input.focus();
        }
      });
      input.addEventListener("click", function () {
        if (input.dataset.fmTvGuarded === "1") input.readOnly = false;
      });
      input.addEventListener("blur", function () {
        if (input.dataset.fmTvGuarded === "1") input.readOnly = true;
      });
    });
  }

  // ---------- focus restore ----------

  let lastFocusKey = "";

  document.addEventListener("focusin", function (event) {
    const el = event.target;
    if (el && el.dataset && el.dataset.fmTvReady === "1") {
      lastFocusKey = focusKey(el);
    }
  }, true);

  function focusKey(el) {
    if (el.id) return "#" + el.id;
    const href = el.getAttribute && el.getAttribute("href");
    return href ? el.tagName + "[href='" + href + "']" : "";
  }

  window.addEventListener("fmresume", function () {
    setTimeout(function () {
      const active = document.activeElement;
      if (active && active !== document.body) return;
      let target = null;
      if (lastFocusKey) {
        try { target = document.querySelector(lastFocusKey); } catch (e) { target = null; }
      }
      if (!target) target = document.querySelector("." + CONFIG.focusClass);
      if (target && target.focus) target.focus({ preventScroll: true });
    }, 120);
  });

  // ---------- styles ----------

  function injectStyle() {
    // Focus feedback must never change layout: use outline/background/transform,
    // never width/height/margin/border-width.
    const css = ""
      + "." + CONFIG.focusClass + ":focus,a:focus,button:focus{"
      + "outline:2px solid rgba(255,255,255,.85)!important;outline-offset:1px;"
      + "border-radius:6px;background-color:rgba(255,255,255,.08);}"
      + "\n." + CONFIG.focusClass + ":focus{transform:scale(1.02);transition:transform .12s;}"
      + "\ninput:focus,textarea:focus{outline:2px solid rgba(255,214,102,.9)!important;}";
    if (typeof GM_addStyle === "function") GM_addStyle(css);
  }

  // ---------- boot ----------

  function schedule() {
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(markFocusable, CONFIG.scanDelay);
  }

  injectStyle();
  markFocusable();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener("fmurlchange", schedule);
  log("ready", location.href);
})();
