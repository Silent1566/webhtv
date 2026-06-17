---
name: webhome-homepage-builder
description: Build, review, debug, reverse-engineer data sources for, and package FongMi/WebHome custom homepage single-file HTML. Use when Codex is asked to create or modify a WebHome `homePage`, App custom homepage, `nostr.html`-style recommendation page, TMDB/Nostr/PanSou homepage, transparent WebView UI, WebHome fm SDK app, TV remote-focus homepage, chrome mode integration, `fm.req`/`fm.res`/`fm.cache`/`fm.search`/`fm.pan`/`fm.ui` usage, old Android WebView compatibility fixes, JS/API reverse engineering for homepage data, or Cloudflare/WAF feasibility diagnosis for homepage-vs-extension decisions.
---

# WebHome Homepage Builder

## Non-Negotiable Compatibility Gate

Treat old Android WebView compatibility as a release blocker. App `minSdk` is 24 and many TV boxes stay near Chromium 51-70. A syntax-level mistake can make the whole single-file homepage white-screen before any `try/catch` runs.

Before finishing any generated or edited WebHome HTML:

1. Keep business JavaScript at ES2017 or lower.
2. Put an ES5-only compatibility bootstrap as the first inline script.
3. Avoid fragile CSS selectors and values unless a fallback is present.
4. Run `python3 scripts/check_webhome_compat.py <html-file>` from this skill.
5. Fix all reported errors. Treat warnings as items to justify or add fallbacks for.

## Source Material

Read only what is needed:

- `references/webhome-app-guide.md`: complete app development document. For homepage work, read chapters 14-25, 29-32; read chapter 13 for local HTTP/debug endpoints.
- `references/webhome-homepage-patterns.md`: distilled homepage architecture, SDK, UI, routing, PanSou, Nostr, and performance guidance.
- `references/old-webview-compatibility.md`: detailed WebView compatibility rules and review checklist.
- `references/nostr-demo-patterns.md`: patterns extracted from `nostr.html`.
- `references/js-reverse-and-waf-workflow.md`: read when homepage data depends on hidden APIs/signatures/player resources, runtime request capture, local JS reproduction, or Cloudflare/WAF diagnosis.
- `assets/demo/nostr.html`: full production-grade single-file homepage example. Reuse its bootstrap, fallback CSS patterns, SDK wrapper, grid batching, focus-domain handling, and restore flow when relevant.
- `scripts/check_webhome_compat.py`: static compatibility scanner for generated HTML/CSS/JS.
- `scripts/probe_webhome_target.py`: passive remote target/WAF classifier for deciding whether direct `fm.req()` homepage data fetching is realistic.

## Default Deliverable

Prefer one self-contained HTML file unless the user explicitly asks for a multi-file project. Include:

- `<!doctype html>`, `lang`, `charset=utf-8`, and viewport with `viewport-fit=cover`.
- Transparent App background with a non-App browser fallback background.
- A first inline ES5 bootstrap that polyfills small API gaps and adds fallback classes such as `fm-native`, `no-layout-gap`, `no-css-functions`, and `no-aspect-ratio`.
- A business script that waits for `fmsdk` when App SDK data is required, and provides browser-preview fallbacks.
- Native playback/search calls that pass known artwork: use `pic` for poster/default artwork and `wallPic` for playback-page background/backdrop.
- A config snippet showing `sites[].homePage` usage.

Configuration example:

```json
{
  "key": "webhome_site_key",
  "name": "WebHome",
  "type": 3,
  "api": "csp_Builtin",
  "homePage": "./home.html",
  "chromeMode": "edge"
}
```

## Architecture Workflow

Design the homepage in these layers:

1. Compatibility bootstrap: ES5 only, no dependencies, runs before all CSS-sensitive or SDK-sensitive code.
2. Configuration: one `window.WEBHOME_CONFIG` object for API bases, feature toggles, cache keys, relay lists, PanSou options, and visual defaults.
3. SDK adapter: expose one `sdk()` helper that uses `window.fm` in App and browser fallbacks during preview.
4. Data layer: use `fm.req()` for cross-origin API JSON; use `fm.res()` for images, video, subtitles, and CSS background resources that need headers or cookies.
5. Persistence: use `fm.cache` after SDK readiness for durable App state; use `localStorage` only as browser-preview fallback or disposable UI state.
6. UI state: centralize state; render lists by stable keys; keep panels, detail sheets, PanSou results, and status panels independently patchable.
7. Routing: use History API for detail, image, sync, and nested panels; let App return handling work with same-origin history boundaries.
8. Restore: save short TTL UI snapshots to `fm.cache`; restore deep UI only on `_fm_restore=1`, `fmresume`, `pageshow`, or playback return.

When the data source is an existing website rather than a documented API, first run `python3 scripts/probe_webhome_target.py <url>` and follow `references/js-reverse-and-waf-workflow.md`. If the probe reports `waf-blocked`, do not build a direct scraping homepage unless the user provides an authorized API, owner-controlled proxy, public feed, or HAR/HTML from an authorized session. Prefer a WebHome extension when the App WebView can normally load the page and the useful data only exists in same-origin runtime state.

## SDK Rules

Use these WebHome SDK APIs instead of browser-only assumptions:

- `fm.req(url, options)` for API data. It bypasses CORS through Native OkHttp and returns `{ ok, status, headers, body, error }`.
- `fm.res(url, options)` for DOM resources. It returns a local `/webResource` URL and supports headers, cookies, Range, and CORS.
- `fm.search(keyword, { direct: true, pic, wallPic })` to jump into App search with fewer return layers while preserving WebHome detail artwork for later native playback from search results.
- `fm.play(url, title, options)` for direct media URLs. Include `options.pic` and `options.wallPic` when known; `wallPic` is the only playback-page background source.
- `fm.vod(siteKey, vodId, title, pic, options)` for native CSP detail/playback. Pass `options.wallPic` when the homepage knows a backdrop.
- `fm.vodInline(payload)` for temporary multi-episode native playback. Include `vod_pic`/`pic` and `wallPic` in the payload.
- `fm.preloadArtwork(pic, wallPic)` after detail artwork is known, so Native can prewarm the player images. Do not block the user click waiting for this preload.
- `fm.pan.play({ type, url, password, title, pic, wallPic })` for pan shares, magnet, ed2k, thunder, jianpian, and push-style playback.
- `fm.config()` before `fm.pan.check()`. If `driveCheck` is false, do not call detection.
- `fm.history()` and `fm.stat()` to compensate watch progress after native playback.
- `fm.ui.setChrome()`, `fm.ui.restoreChrome()`, and `fm.ui.getViewport()` for homepage chrome and safe-area integration.

Never hard-code the local HTTP port. Use SDK methods or `fm.device().ip` when a base URL is genuinely needed.

Do not embed stealth fingerprint patches, CAPTCHA solving, Cloudflare clearance harvesting, account cookies, or per-account tokens in homepage code or config.

## UI And UX Rules

Build an actual App homepage, not a landing page. For media homepages, prioritize: continue watching, recommendations, category lists, search, detail, and playback actions.

Transparent WebView:

- Keep `html`, `body`, and top-level page background transparent in App.
- Provide a non-App fallback background via `html:not(.fm-native)`.
- Put text on semi-transparent panels, buttons, chips, cards, or detail surfaces. Do not place body text directly on wallpaper.
- For transparent full-screen overlays, hide the underlying WebHome layer while the overlay is active to avoid stacked content.

Safe area and chrome:

- Use `max(var(--fm-safe-*), env(safe-area-inset-*, 0px))`; never add `--fm-safe-*` and `env()` together.
- If mobile mode has top buttons, tabs, search bars, back buttons, or any fixed/sticky operation area near the top edge, reserve top safe space only while the WebView is fullscreen/fused with system bars, such as `chromeMode: "edge"` or `immersive`. In normal chrome after exiting fullscreen, remove the top reservation so the page does not show a blank row. Use `fm.ui.getViewport()` and `fmviewport`, write a fallback declaration first, then the `max(var(--fm-safe-top, 0px), env(safe-area-inset-top, 0px))` version for newer WebViews.
- Use `var(--fm-web-height, 100vh)` for full-height layouts.
- Use `chromeMode: "edge"` or `fm.ui.setChrome({ mode: "edge", startup: true })` for homepage fusion.
- Use `immersive` only for focused detail/fullscreen experiences with a page-owned back button, then restore chrome when closing.

TV remote:

- Detect App form factor with `window.fongmiClient.isLeanback` or `fm.device().type`; use viewport heuristics only for browser preview.
- Give every actionable card, tab, button, result item, and panel control a stable focus target and key.
- Trap directional keys inside active local domains such as search suggestions, settings/status panels, PanSou results, image viewers, and detail sheets.
- Make text fields `readonly` by default on TV; OK/touch enters edit mode, blur/back exits edit mode.
- Prefer deterministic grid/list navigation by index and cached column count. Use geometry search only as a fallback.
- Focus style must not change layout dimensions. Use outline, existing border, background, or light transform.

## Performance Rules

- Render large grids in batches: first about three rows, append about two rows.
- Store render state per grid: source, total, rendered count, item keys, and render keys.
- If keys are unchanged, patch active/count/status only; do not replace whole lists.
- Use `IntersectionObserver` for infinite append and visible PanSou checks, with passive scroll fallback.
- Delay non-critical content rendering until after first paint with double `requestAnimationFrame` when useful.
- During scroll or remote-key repeat, avoid full-page `querySelectorAll()`, full-list `getBoundingClientRect()`, and synchronous `scrollIntoView()`.
- Cache grid column counts and invalidate on viewport/device-mode/layout changes.

## PanSou And Playback Rules

For PanSou-like resource search:

- Read `data.merged_by_type`, filter enabled disk types, dedupe by `diskType + normalizedUrl`, and render per-disk tabs.
- Keep old results until new results are ready; merge poll results without clearing the list.
- Use a view token so old detail-page responses cannot overwrite the current detail.
- Detect only supported disk types and only visible results; batch `fm.pan.check()` in groups of about 10.
- Rank health states as playable first: `ok`, `locked`, pending/idle, unsupported/uncertain, then `bad`.
- Before `fm.pan.play()`, save detail scroll, result scroll, active type, focus key, and selected result so native-playback return can restore context.
- Before `fm.search()` from a detail page, pass `{ direct: true, pic, wallPic }` so native search-result playback can still use the WebHome backdrop.
- Before `fm.play()`, `fm.vod()`, `fm.vodInline()`, or `fm.pan.play()`, pass the best known `pic` and `wallPic`. Use poster art for `pic` and landscape/backdrop/still art for `wallPic`; do not rely on `pic` as a playback background fallback.

For watch preference or recommendation systems:

- Record watch intent before opening native search/playback.
- Sample `fm.stat()` while available.
- On resume or return, use `fm.history()` to compensate if WebView was paused during playback.
- Publish or sync only after a meaningful threshold, such as 10 minutes watched, and dedupe per media/user.

## Output Format

When creating a homepage, provide:

1. Page architecture summary and data sources.
2. Compatibility plan: bootstrap, JS baseline, CSS fallbacks, and validation command.
3. JS reverse/WAF evidence when used: target request, initiator/script/function, runtime samples, replay status, and why homepage or extension is the right vehicle.
4. Complete single-file HTML or direct file edits.
5. `sites[]` config snippet with `homePage` and chrome choice.
6. Test steps for browser preview, App WebHome, mobile, TV remote, native playback, PanSou detection, and debug logs.
7. Known risks: third-party library syntax baseline, API key handling, relay/service availability, selector/data assumptions, WAF/session assumptions, and old WebView residual risks.

When reviewing an existing homepage, lead with compatibility blockers, then SDK misuse, TV focus/return bugs, performance regressions, and visual/UX issues.

## Required Validation

Run:

```bash
python3 /path/to/webhome-homepage-builder/scripts/check_webhome_compat.py path/to/home.html
```

Also verify:

- No `?.`, `??`, logical assignment, `catch {}`, regex lookbehind/named capture, or `<script type="module">`.
- No unguarded `replaceAll`, `Promise.allSettled`, `Object.fromEntries`, `structuredClone`, `AbortController`, or `globalThis`.
- No `:is()`, `:where()`, `:has()`, or `:focus-visible`.
- `flex gap`, `aspect-ratio`, `clamp/min/max`, `inset`, `backdrop-filter`, `100dvh`, and `env()` have fallbacks or feature detection.
- App requests use `fm.req`/`fm.res`; SDK promises catch failures.
- No WAF/challenge bypass logic, stealth automation patching, CAPTCHA solving, clearance-cookie harvesting, account cookie embedding, or per-account token leakage.
- TV can move focus, activate controls, edit text intentionally, and return from each local panel.
- Debug logs show no `SyntaxError`, WebView render crash, or SDK call failures.
