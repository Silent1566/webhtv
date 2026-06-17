# WebHome Homepage Patterns

This reference distills the homepage-specific parts of `webhome-app-guide.md`.

## App Contract

A WebHome homepage is a real web page loaded by a CSP site configuration:

```json
{
  "key": "home_key",
  "name": "Home",
  "type": 3,
  "api": "csp_Builtin",
  "homePage": "./home.html"
}
```

If the config is remote, relative `homePage` resolves relative to the config URL. The App injects `window.fongmi`, `window.fm`, and `window.fongmiClient`, then dispatches `fmsdk`. Early inline scripts may run before SDK injection, so wait for `fmsdk` before reading/writing critical App state.

Important events:

- `fmsdk`: SDK ready.
- `fmresume`: WebHome resumes from background.
- `fmpause`: WebHome goes background.
- `fmviewport`: safe area, viewport, keyboard, gesture, and chrome mode changed.
- `fmurlchange`: History API route changed.

## SDK Adapter

Use one `sdk()` function with App and browser-preview paths. In App, return `window.fm` plus normalized `pan` and `check`. In browser preview, provide no-op or localStorage-backed fallbacks so the page can be opened directly.

Wait helper:

```js
function waitForNativeSdk(timeout) {
  if (window.fm || !window.fongmiBridge) return Promise.resolve(!!window.fm);
  return new Promise(function (resolve) {
    var done = false;
    var finish = function () {
      if (done) return;
      done = true;
      window.removeEventListener("fmsdk", finish);
      resolve(!!window.fm);
    };
    window.addEventListener("fmsdk", finish);
    setTimeout(finish, timeout == null ? 1500 : timeout);
  });
}
```

Use `fm.cache` for durable App state after SDK readiness; use localStorage only for preview fallback. Store JSON as strings.

## Networking

- Use `fm.req()` for API JSON or text. It uses Native OkHttp and bypasses browser CORS.
- Use `fm.res()` for images, video, subtitles, CSS backgrounds, and other DOM resources. It returns a local resource-gateway URL.
- Config-level headers are not automatically injected into `fm.req()`; pass needed headers explicitly.
- Never hard-code `127.0.0.1:9978`; ports can vary.

## Chrome And Viewport

Recommended homepage mode:

```js
await fm.ui.setChrome({
  mode: "edge",
  statusBarStyle: "light",
  navigationBarStyle: "light",
  restoreAffordance: "auto",
  startup: true
});
```

Use `immersive` for detail/fullscreen modes only when the page draws its own back button. Call `fm.ui.restoreChrome()` or set `edge`/`normal` when closing.

Use injected CSS variables:

- `--fm-web-width`, `--fm-web-height`
- `--fm-safe-top/right/bottom/left`
- `--fm-gesture-left/right/bottom`
- `--fm-status-bar-height`, `--fm-navigation-bar-height`
- `--fm-keyboard-bottom`
- `--fm-chrome-mode`

For full-height UI use `var(--fm-web-height, 100vh)`.

## Transparent UI

Keep App pages transparent:

- `html`, `body`, and `.app`: `background: transparent`.
- `html:not(.fm-native)` should provide a browser fallback background.
- Text must sit on semi-transparent panels or controls.
- Transparent detail/episode overlays should hide underlying content with a body class such as `detail-active` or `episode-active`.

Recommended panel variables:

```css
:root {
  --panel-rgb: 76, 88, 98;
  --panel: rgba(var(--panel-rgb), .58);
  --panel-soft: rgba(var(--panel-rgb), .44);
  --panel-strong: rgba(var(--panel-rgb), .82);
  --line: rgba(255, 255, 255, .2);
}
```

## Routing And Restore

Use History API for nested views:

- `#detail` for detail sheet.
- `#image` for image or episode viewer.
- `#sync` or similar for secondary full-screen panels.

On `popstate`, close the active topmost panel rather than tearing down all state. For local panel returns, intercept before App/global return, such as PanSou results -> PanSou tabs -> detail -> home.

Save short TTL UI snapshots to `fm.cache`:

- active list/tab
- scroll positions
- selected detail item
- detail route
- PanSou keyword, active disk type, compact results, health, list scroll, focus key
- playback return context

Restore deep state only for `_fm_restore=1`, `fmresume`, `pageshow`, or native playback return. Normal cold start should open the homepage.

## Mobile UX

- Put frequent actions in thumb-reachable lower areas.
- Keep bottom controls away from safe/gesture areas.
- Use vertical page flow: search/status, continue watching, recommendations, category grid.
- Use History API so system back gestures work.
- Lock background scroll while detail/image overlays are open, and restore scroll on close.
- Search suggestions must respond to real input, not merely focus.

## TV UX

Remote control is current focus + arrows + OK + Back. Build explicit focus domains:

- Homepage chips/grid.
- Search form and suggestions.
- Connection/status/settings panel.
- Detail sheet.
- PanSou tabs and result list.
- Image/episode viewer.

Rules:

- Do not default focus to search inputs.
- Inputs are readonly until OK/touch enters edit mode.
- Direction keys stay inside an open local domain.
- Back first handles local domain state, then WebView history, then `fm.back()`.
- Dynamic lists use stable `data-*` keys and restore focus after patching.
- `focus({ preventScroll: true })`, then schedule scroll correction in `requestAnimationFrame`.

## PanSou Integration

Search:

```http
POST /api/search
```

Request:

```json
{
  "kw": "title",
  "res": "merge",
  "src": "all",
  "cloud_types": ["quark", "aliyun"]
}
```

Process `data.merged_by_type`; filter enabled disk types; dedupe by `diskType + normalizedUrl`; render per-type tabs. Some services add results asynchronously, so poll a few times and merge new results without clearing old ones.

Detection:

- Call `fm.config()` and require `driveCheck !== false`.
- Only check App-supported disk types.
- Observe visible result rows and batch about 10 links per `fm.pan.check()`.
- Sort health states so playable resources surface first.

Playback:

```js
await fm.pan.play({ type: item.diskType, url: item.url, password: item.password, title: item.title });
```

Save playback return context before calling native playback.

## Performance

- Use stable render keys.
- Batch homepage grid rendering by rows.
- Append near bottom or when focus approaches the rendered end.
- Cache grid column counts.
- Keep old content visible during async refresh.
- Patch lists instead of full replacement.
- During scrolling or remote key repeat, avoid full DOM scans and layout reads.
- Use `IntersectionObserver` when available, passive scroll fallback otherwise.

## Debugging

Enable App debug logs:

- App: Settings -> Enhanced features -> Debug logs.
- HTTP: `http://127.0.0.1:{port}/debug/logs`
- Text: `/debug/logs.txt`

Look for `webview`, `webhome`, `webhome-console`, `webhome-net`, `web-resource`, `webhome-key`, `webhome-focus`, `pan.check`, and player logs.
