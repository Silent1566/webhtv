# WebHome Templates and Examples

Use this catalog to choose which bundled file to read or adapt. Templates are starting points; always adjust `CONFIG`, selectors, titles, URL extraction, and UI placement for the target site.

## Template Catalog

| File | Use When | Key Pattern |
| --- | --- | --- |
| `assets/templates/site-enhance-skeleton.js` | Building a full site enhancement with page routing, SPA rescans, mobile/TV classes, and native resource routing | IIFE, `CONFIG.routes`, debounced `MutationObserver`, `fmurlchange`, `markTvFocus`, classified `route()` |
| `assets/templates/page-analyzer.js` | Collecting page candidates in the Debug workbench before generating a script | Returns JSON with headings, resource-like elements, media entries, and selector guesses; sends nothing externally |
| `assets/templates/auto-resource-router.js` | Existing resource controls should be routed to App playback/push | Capture-phase click interception, narrow selectors, public HLS/DASH direct `fm.play`, `fm.pan.play` for pan/push |
| `assets/templates/inject-play-buttons.js` | Keep original site clicks unchanged but add explicit App play buttons | Scans resource rows, appends one button, refreshes existing button data, re-reads the live sibling/source URL on click |
| `assets/templates/pan-link-router.js` | Pan share links need App play buttons and optional validity status | Adds App buttons, checks visible pan links with `fm.pan.check()` only when `fm.config().driveCheck` allows it |
| `assets/templates/inline-episodes.js` | Episode list exists, but media URL is resolved lazily or per episode | Registers `window.__fmWebHomeInlineResolver`; calls `fm.vodInline()` with `resolve: true` episodes |
| `assets/templates/media-sniffer.js` | Media URL only appears at runtime after player/API actions | Hooks page `fetch`/XHR and DOM/performance media; use for analysis, then remove broad sniffing if a specific extractor is found |
| `assets/templates/site-cleanup.js` | Popups, ads, scroll locks, or `window.open` hurt WebHome usage | Conservative selector hiding, scroll unlock, same-window `window.open`, context/select unblock |
| `assets/templates/tv-focus-helper.js` | A site needs TV remote support and can share a helper dependency | Adds focusability, layout-safe `:focus`, OK/Enter `.click()`, input readonly guard, focus restore |

## Repository Layout

Top-level repository examples are grouped by deliverable type:

- `examples/extensions/<site>/`: extension examples with co-located JS and manifest.
- `examples/homepages/`: single-file WebHome homepage examples.
- `templates/extensions/`: extension starter templates and analysis helpers.
- `templates/homepages/`: homepage starter templates; use `templates/homepages/basic-homepage.html` for the smallest safe-area/chrome baseline, `templates/homepages/app-capabilities-showcase.html` to test every exposed App SDK capability with domestic demo resources and a pan-check matrix, and `examples/homepages/nostr.html` or `webhome-homepage-builder/assets/demo/nostr.html` for the full homepage pattern.

## Example Patterns

### `assets/examples/pomo.mom.js`

Use as the reference for a heavier detail-page resource panel:

- Groups resources into online media, pan links, and magnet-like links.
- Reads a separate online player page with `fm.req()`, parses `rawData`, resolves media through a site API, then calls `fm.play()`.
- Routes pan/magnet items with `fm.pan.play()`.
- Rebuilds list/detail mobile layout and keeps TV focusable elements.
- Demonstrates tabs, busy states, empty states, duplicate suppression, and preserving copy buttons.

Manifest: `assets/examples/pomo.manifest.json`.

### `assets/examples/dm.xueximeng.com.js`

Use as the reference for a standard direct-DOM resource site:

- Enhances resource detail links and injects `App播放` buttons.
- Preserves original structure but routes verified resources to `fm.play()` or `fm.pan.play()`.
- Pulls nearby row metadata such as password and notes.
- Adds robust TV focus navigation, input edit guards, focus restore, and mobile detail-page cleanup.

Manifest: `assets/examples/dm.xueximeng.manifest.json`.

### `assets/examples/ymvid.com.js`

Use as the reference for `fm.vodInline` and runtime decryption:

- Adds an App play panel on play pages.
- Registers `window.__fmWebHomeInlineResolver` and builds an inline VOD payload from the episode list.
- Reuses site runtime decryption via a page-context bridge and falls back to Artplayer/Hls media hooks.
- Fetches non-current episode pages with `fm.req()`, extracts encrypted player parts, then resolves m3u8 URLs inside the page context.
- Shows how to keep the WebHome page alive while native playback performs on-demand episode resolution.

Manifest: `assets/examples/ymvid.manifest.json`.

### `assets/examples/gying-extension.js`

Use as the reference for dynamic online playback groups where the visible button opens a site player page before the final media URL exists:

- Injects a right-aligned `App播放` button into online playback rows without replacing the site's own button.
- Refreshes existing button data during every scan and re-reads the exact sibling/source element on click, avoiding stale line URLs after tab or route changes.
- Compares the site play-page URL and final media URL path before changing resolver logic when desktop playback works but App playback fails.
- Plays public HLS/DASH URLs directly with `format` instead of forcing `/webResource` headers/cookies, so relative playlists are not broken.
- Applies mobile top safe-area padding only in fullscreen/fused chrome states such as `edge` or `immersive`, then removes it after normal chrome is restored.
- Avoids custom challenge popups; when verification/WAF appears, use browser/App WebView observation and user interaction rather than adding bypass logic.

Manifest: `assets/examples/gying-extension.manifest.json`.

## Resource Type Rules

Use this classification consistently:

- `magnet:` -> `magnet`
- `ed2k:` -> `ed2k`
- `thunder:` -> `thunder`
- `pan.quark.cn` -> `quark`
- `aliyundrive.com` or `alipan.com` -> `aliyun`
- `pan.baidu.com` -> `baidu`
- `drive.uc.cn` -> `uc`
- `pan.xunlei.com` -> `xunlei`
- `cloud.189.cn` -> `tianyi`
- `123pan`, `123684`, `123685`, `123912`, `123592`, `123865` domains -> `123`
- `115.com` or `115cdn.com` -> `115`
- `yun.139.com` or `caiyun.139.com` -> `mobile`
- direct media extensions such as `.m3u8`, `.mp4`, `.mkv`, `.flv`, `.mov`, `.avi`, `.webm` -> `media`

Route `media` with `fm.play()`. Route all other pushable links with `fm.pan.play()`.

## Manifest Patterns

Site-bound extension:

```json
{
  "key": "site-key",
  "name": "Site Name",
  "type": 3,
  "api": "csp_Builtin",
  "homePage": "https://example.com/",
  "extensions": [
    {
      "id": "site-native-router",
      "name": "Site native router",
      "version": "1.0.0",
      "runAt": "document-end",
      "js": ["https://example.com/webhome/site.js"]
    }
  ]
}
```

Global extension:

```json
{
  "webHomeExtensions": [
    {
      "id": "site-native-router",
      "name": "Site native router",
      "version": "1.0.0",
      "runAt": "document-end",
      "cspKeyRegex": ["^site-key$"],
      "enabled": true,
      "js": ["https://example.com/webhome/site.js"]
    }
  ]
}
```
