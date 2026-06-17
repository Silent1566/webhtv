---
name: webhome-extension-builder
description: Build, review, debug, reverse-engineer, and package WebHome injected extension scripts for FongMi/WebHome App WebView pages. Use when Codex is asked to create or improve WebHome extensions, `webHomeExtensions`, `sites[].extensions`, fm SDK scripts, native App play buttons, pan/magnet/native playback routing, `fm.vodInline` episode resolvers, TV remote focus helpers, WebHome extension manifests/configuration, JS/API/player reverse engineering for WebHome extensions, or Cloudflare/WAF feasibility diagnosis for extension-vs-homepage decisions.
---

# WebHome Extension Builder

## Core Workflow

Use this skill to produce production-ready WebHome extension scripts that enhance real websites loaded in the App WebView. Prefer small, site-specific enhancements over replacing the site: route verified resources to native playback, add clear App play controls, clean harmful UI, support TV focus, and keep the original site's search, filtering, login, pagination, and detail navigation intact.

Before writing a non-trivial script, read the relevant source material:

- `references/webhome-extension-guide.md`: full WebHome extension guide and API reference. Read sections 4, 7, 9-15 for most scripts; read section 8 for `fm.vodInline`; read section 13 before final compatibility review.
- `references/template-and-example-catalog.md`: choose templates and example patterns.
- `references/js-reverse-and-waf-workflow.md`: read when the target requires hidden API/signature/player/resource discovery, runtime request capture, local JS reproduction, or Cloudflare/WAF diagnosis.
- `assets/templates/*.js`: copy or adapt the relevant template source instead of recreating the skeleton from memory.
- `assets/examples/*.js` and `*.manifest.json`: consult for full real-world patterns after choosing a strategy.
- `scripts/probe_webhome_target.py`: run on unknown remote targets before choosing direct homepage fetching, injected extension work, or an authorized backend path.

## Input Handling

Establish these facts from the user, local files, target HTML, browser inspection, or analyzer output:

- Target website URL and target WebHome site key. If the key is unknown, propose a kebab-case key and use exact `cspKeyRegex` such as `^site-key$`.
- Desired behavior: click interception, injected App play buttons, pan-link validation, media sniffing, layout cleanup, TV focus, or `fm.vodInline` episode playback.
- Page roles and URL patterns: home, list/search, detail, play.
- Stable selectors for title, resource container, resource item, resource button/link, episode list, active episode, poster.
- Resource source: DOM attributes, `href`, `onclick`, nearby text, copied text, API response, player constructor, encrypted page state, or runtime media requests.
- Access/WAF status: direct HTTP result, App WebView result, Cloudflare/WAF/challenge signals, login/session assumptions, and whether an authorized API/proxy/HAR exists.

If page access is unavailable, ask for one of: target HTML, screenshots plus DOM snippets, or output from `assets/templates/page-analyzer.js`.

For login, PoW, browser safety checks, or shield/WAF pages that the user can legitimately pass, start a visible browser or attach to an App WebView with CDP/DevTools, have the user complete the interaction in that session, then capture the post-verification network, DOM, console, frames, and media evidence from the same session. Do not replace this with curl guessing, stealth automation, CAPTCHA solving, or cookie/clearance harvesting.

If the target is unknown or may be protected, first run `python3 scripts/probe_webhome_target.py <url>` from this skill. Treat `waf-blocked` as: direct `fm.req` scraping is not reliable; build an extension only if the App WebView can normally load the page. If the App WebView is also blocked, require authorized API access, owner-controlled proxying, or user-supplied HAR/HTML instead of attempting to bypass the challenge.

## Strategy Selection

Choose the lowest-risk strategy that fits the page:

- Existing resource links/buttons: adapt `assets/templates/auto-resource-router.js`.
- Need a separate native button without changing original clicks: adapt `assets/templates/inject-play-buttons.js`.
- Pan links with availability status: adapt `assets/templates/pan-link-router.js`; call `fm.config()` and only run `fm.pan.check()` when `driveCheck` is enabled.
- Lazy episode parsing or encrypted per-episode media: adapt `assets/templates/inline-episodes.js`; register `window.__fmWebHomeInlineResolver` before calling `fm.vodInline()`.
- Runtime-only media URLs: use `assets/templates/media-sniffer.js` during analysis, then keep only the specific hook/extraction logic required.
- Hidden signatures, encrypted player state, lazy chunks, or runtime-only APIs: follow `references/js-reverse-and-waf-workflow.md`; observe network and scripts first, add narrow hooks second, and use local Node reproduction only after real page evidence identifies the entry function.
- Broad mobile/TV site enhancement: start from `assets/templates/site-enhance-skeleton.js`.
- TV-only remote support shared by another extension: package `assets/templates/tv-focus-helper.js` as a dependency and declare `depends`.
- Popups, scroll locks, or ad overlays: adapt `assets/templates/site-cleanup.js` conservatively.

Default to `runAt: "document-end"`. Use `document-start` only for early hooks such as `window.open`, `fetch`, `XMLHttpRequest`, history routing, or player constructor wrapping, and make the script tolerate downgrade to document-end. Do not add stealth fingerprint patches, CAPTCHA solving, Cloudflare clearance harvesting, or token cracking to extension scripts.

## Script Rules

Write one top-level IIFE with all site-specific values in `CONFIG`. Include these common helpers unless the chosen template already has them:

- `log()` using `GM_log` first, console fallback second.
- `whenFm()` that waits for the `fmsdk` event.
- `ready(fn)` for DOM readiness.
- `cleanText()`, URL normalization, title fallback, and resource classification.
- A debounced scan loop using `MutationObserver`, `fmurlchange`, and `data-fm-*` markers to avoid duplicate injection.

Resource routing rules:

- Never intercept every `a[href]`; only intercept elements classified as resources.
- Preserve copy buttons and normal site navigation unless the user explicitly asks to replace them.
- Use capture-phase click handlers for resource routing and wrap async actions in `try/catch` or `.catch()`.
- Use `fm.play(url, title, { headers: { Referer: ... }, credentials: "include" })` for direct media.
- Use `fm.pan.play({ type, url, password, title })` for pan, magnet, ed2k, thunder, jianpian, and generic push links.
- Use `fm.req()` for JS/API reads that need native networking; use `fm.res()` for DOM media/image URLs that need the local resource gateway.

UI and TV rules:

- Add injected controls only where users naturally act: resource rows, episode areas, or player panels.
- Add async states: loading, empty, failure, and retry or toast where appropriate.
- On mobile/non-TV WebHome pages, top fixed/sticky operation areas must reserve status-bar safe space only while the WebView is in fullscreen/fused chrome states such as `chromeMode: "edge"` or `immersive`. In normal chrome after exiting fullscreen, remove the top reservation so the page does not show a blank row. Use `fm.ui.getViewport()` and `fmviewport` when available, write a fallback first, then use `max(var(--fm-safe-top, 0px), env(safe-area-inset-top, 0px))`; never add native `--fm-safe-top` and browser `env()` together.
- On TV, make actionable non-link elements focusable, use `:focus` styles, map OK/Enter to native `.click()` for custom focusables, and guard text inputs with readonly until confirmation.
- Focus styling must not change layout dimensions; use outline, background, box-shadow, or small transform.

## Compatibility Rules

Assume old Android WebViews and keep generated JavaScript at ES2017 or below. Do not emit:

- Optional chaining `?.`, nullish coalescing `??`, logical assignment, class fields, private fields, `catch {}` without binding.
- Regex lookbehind, named capture groups, or dotAll `s` regex literals.
- `replaceAll`, `Promise.allSettled`, `Object.fromEntries`, `Array.flat/flatMap`, `structuredClone`, or unguarded `AbortController`.

CSS must avoid fragile modern selectors and values:

- Do not use `:is()`, `:where()`, `:has()`, or `:focus-visible`.
- Avoid flex `gap` in injected panels unless a margin fallback is already present.
- Provide fallbacks for `aspect-ratio`, `clamp()`, `inset`, `backdrop-filter`, and viewport units.
- Use `var(--fm-web-height, 100vh)` instead of bare full-screen height when sizing WebHome overlays.

## Packaging

Always output a fixed manifest/config unless the user only requested a review:

```json
{
  "extensions": [
    {
      "id": "site-native-router",
      "name": "Site native router",
      "version": "1.0.0",
      "runAt": "document-end",
      "cspKeyRegex": ["^site-key$"],
      "js": ["./site.js"]
    }
  ]
}
```

For `sites[].extensions`, omit `cspKeyRegex` unless the user wants extra narrowing. For root-level `webHomeExtensions`, include `cspKeyRegex` and `enabled: true` only when the extension should load by default.

Use stable kebab-case IDs containing the site name. Start new scripts at `1.0.0`, and increment versions when editing an existing extension. Declare `depends` when sharing helpers such as `tv-focus-helper`.

## Output Format

When creating a new extension, provide:

1. Page role and URL-pattern summary.
2. Selector table with title, resource area, resource item, resource URL source, and episode selectors when relevant.
3. Strategy choice and why it is the least invasive reliable option.
4. JS reverse evidence when used: target request, initiator/script/function, runtime samples, reproduction status, and WAF classification.
5. Complete runnable JavaScript.
6. Manifest or `sites[].extensions` snippet.
7. Test steps for WebHome extension manager, Debug workbench, console/log tags, mobile path, and TV path when applicable.
8. Known risks and selectors, chunks, API contracts, or WAF/session assumptions most likely to break.

When editing files in a repo, create the JS and manifest files directly, then validate with the checklist below.

## Final Checklist

Verify before finishing:

- Script has no syntax/API/CSS redline listed above.
- No broad link interception; no account, cookie, or private data exfiltration.
- No WAF/challenge bypass logic, stealth automation patching, CAPTCHA solving, clearance-cookie harvesting, or per-account token leakage.
- `GM_getValue`/`GM_setValue`, `fm.req`, `fm.play`, `fm.pan.play`, and `fm.vodInline` calls are awaited or caught.
- SPA route changes and DOM re-rendering do not duplicate buttons.
- Empty pages do not get stray panels or buttons.
- Manifest has fixed `id`, `version`, `runAt`, and exact site key matching.
- Mobile controls have adequate touch targets; TV focus and OK activation work when TV support is in scope.
