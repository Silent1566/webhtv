# Old WebView Compatibility

Use this reference whenever creating or reviewing WebHome single-file HTML. Compatibility is stricter than ordinary web work because a single unsupported JavaScript syntax token can stop the whole page from executing on old TV WebViews.

## Baseline

- Target Android WebView: Chromium 50-70, even if desktop Chrome preview works.
- Business JavaScript: ES2017 or lower.
- First inline bootstrap: ES5 only, using `var` and `function`.
- Third-party libraries: lock versions and verify their distributed bundle syntax. A library upgrade can silently introduce Chromium 80+ syntax.

## Required Bootstrap

Place the bootstrap before main styles and main business script. Use `assets/demo/nostr.html` lines 8-214 as the canonical source.

It should:

- Add `fm-native` when `window.fongmiBridge`, `window.fm`, or `window.fongmi` is present.
- Polyfill small runtime gaps: `replaceChildren`, `matches`, `closest`, `Object.values`, `Object.entries`, `Array.from`, `NodeList.forEach`, `HTMLCollection.forEach`, `Array.includes`, `Array.flat`, `Array.flatMap`, `String.includes`, `String.startsWith`, `String.endsWith`, and `Promise.finally`.
- Detect flex/grid gap by rendering hidden test elements, not only by `CSS.supports`.
- Detect CSS functions and add `no-css-functions`.
- Detect `aspect-ratio` by rendering a hidden test element because some WebViews claim support but render it incorrectly.
- Add fallback classes such as `no-layout-gap` and `no-aspect-ratio`.

## JavaScript Syntax Errors

Do not emit these:

| Forbidden | Why | Replacement |
| --- | --- | --- |
| `?.` | Chromium 80; parse-time failure | `a && a.b` |
| `??` | Chromium 80; parse-time failure | `a == null ? fallback : a` |
| `&&=`, `||=`, `??=` | Chromium 85 | expand assignment |
| `catch {}` | Chromium 66 | `catch (e) {}` |
| regex `(?<=...)`, `(?<!...)` | parse-time regex failure | normal groups |
| regex `(?<name>...)` | parse-time regex failure | numbered groups |
| regex dotAll `s` flag | older parse-time failure | `[\s\S]` |
| class fields / `#private` | Chromium 72-74 | assign in constructor |
| BigInt literal `1n` | Chromium 67 | number/string |
| `<script type="module">` | not appropriate for single-file WebHome | classic script |
| function trailing comma | Chromium 58 risk | remove |

Allowed baseline: `let`/`const`, arrow functions, template strings, destructuring, classes without fields, `Map`, `Set`, Promise, `async/await`, and `for...of`.

## JavaScript API Hazards

These are runtime failures, not parse failures. Avoid or guard them:

- `String.prototype.replaceAll`: use `split/join` or global regex `replace`.
- `Promise.allSettled`: use `Promise.all(items.map(p => p.catch(...)))`.
- `Object.fromEntries`: use a loop.
- `Array.flat` / `flatMap`: only after bootstrap polyfill runs.
- `structuredClone`: use JSON clone for plain data.
- `AbortController`: use request sequence tokens if unavailable.
- `globalThis`: use `window`.
- `navigator.clipboard`: use `document.execCommand("copy")` fallback.
- `IntersectionObserver`: guard with `"IntersectionObserver" in window` and add passive scroll fallback.
- `scrollIntoView({ ... })`: wrap in `try/catch`; old WebViews may treat object as boolean.

## CSS Hazards

Selectors:

- Do not use `:is()`, `:where()`, `:has()`, or `:focus-visible`.
- Never mix a modern selector into a comma list with safe selectors; one unsupported selector invalidates the whole rule.
- TV focus styles should use `:focus` or JS-maintained classes.

Values and properties:

- `flex gap`: not reliable before Chromium 84. Provide `html.no-layout-gap` margin fallbacks.
- `aspect-ratio`: not reliable before Chromium 88 and may lie via `CSS.supports`. Provide `html.no-aspect-ratio` padding-top or fixed-height fallbacks.
- `min()`, `max()`, `clamp()`: provide fixed-value fallback or `html.no-css-functions` override.
- `backdrop-filter`: always pair with a readable semi-transparent solid background; reduce on TV.
- `inset`: write `top/right/bottom/left` first, then `inset`.
- `100dvh`: use `var(--fm-web-height, 100vh)` and optional `@supports` enhancement.
- `env(safe-area-inset-*)`: write a fallback declaration first; combine native variables with `max()`, not addition.
- `content-visibility`: avoid unless tested on target TV WebView; it can break focus visibility.

## Safe-Area Pattern

Use:

```css
:root {
  --safe-top: max(var(--fm-safe-top, 0px), env(safe-area-inset-top, 0px));
  --safe-bottom: max(var(--fm-safe-bottom, 0px), env(safe-area-inset-bottom, 0px));
}
```

Do not use:

```css
padding-bottom: calc(var(--fm-safe-bottom) + env(safe-area-inset-bottom));
```

The App already injects real `--fm-safe-*` values, so addition double-counts.

## Review Procedure

1. Run `scripts/check_webhome_compat.py`.
2. Search manually for `?.`, `??`, `||=`, `&&=`, `??=`, `(?<`, `replaceAll`, `allSettled`, `fromEntries`, `structuredClone`, `:focus-visible`, `:is(`, `:where(`, and `:has(`.
3. Confirm the first script is ES5 bootstrap and cannot be blocked by unsupported syntax.
4. Confirm CSS fallbacks exist for gap, aspect ratio, CSS functions, `inset`, `100dvh`, and `env`.
5. Test in App debug logs. A desktop browser is not sufficient.
6. On TV, long-press directional keys across tabs, grids, detail sheets, PanSou results, and settings fields.
