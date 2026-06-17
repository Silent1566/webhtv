# Nostr Demo Patterns

Use `assets/demo/nostr.html` as a working example of a complex WebHome homepage. It is not only a Nostr sample; it shows how to structure a large single-file App homepage under old WebView constraints.

## High-Value Sections

- Lines 8-214: ES5 compatibility bootstrap, polyfills, feature detection, and fallback classes.
- Main CSS: transparent App background, panel variables, safe-area variables, TV mode, no-gap and no-aspect-ratio fallbacks.
- `window.WEBHOME_CONFIG`: centralized TMDB, Nostr, PanSou, cache keys, relay list, disk types.
- `sdk()` and `waitForNativeSdk()`: App SDK adapter plus browser-preview fallback.
- Render functions: stable render keys, grid batching, double `requestAnimationFrame` content rendering.
- Remote key handling: focus domains, local back handling, readonly input edit mode, deterministic grid navigation.
- PanSou functions: view token, polling merge, tabs, visible checks, health ranking, playback return restore.
- Watch tracking: pending watch in `fm.cache`, `fm.stat()` sampling, `fm.history()` compensation.
- Resume/snapshot handling: `_fm_restore=1`, `fmresume`, `fmpause`, `pageshow`, `pagehide`, `visibilitychange`.

## Compatibility Design

The demo uses:

- First script in ES5 so it can run on old WebViews.
- Business code at ES2017 or lower.
- No optional chaining, nullish coalescing, logical assignment, named capture, lookbehind, or module scripts.
- Runtime class fallbacks: `no-layout-gap`, `no-css-functions`, `no-aspect-ratio`, and `legacy-detail-layout`.
- Margin fallbacks for flex gap.
- Padding-top or fixed-height fallbacks for posters and landscape cards.
- `:focus`, not `:focus-visible`.
- `top/right/bottom/left` fallbacks before `inset`.
- Semi-transparent panel backgrounds paired with optional blur.

## Data Model Pattern

The Nostr recommendation system uses a compact user vector rather than one event per media item:

- `kind = 30078`
- `HOT_VECTOR_D = "heat:user:90d:v2"`
- `HOT_VECTOR_VERSION = 5`
- 90-day window
- one user contributes at most one count per media
- watch threshold around 10 minutes
- local IndexedDB stores `media`, `userVector`, and `relayCursor`
- incoming vector diffs update media counts instead of recalculating everything
- relay cursors track recent and history backfill
- local delete tombstones block old local events from being re-ingested

Use this pattern for decentralized or multi-device recommendation systems.

## Relay Sync Pattern

- Render cached IndexedDB hot list first.
- Subscribe to recent relay events with limits and timeouts.
- Ingest relay events through a queued batch writer.
- Close subscription after EOSE or timeout.
- Backfill recent and history pages with bounded page counts.
- Select a primary relay for deeper historical backfill.
- Use exponential backoff for failures.
- Show connection/subscription/backfill status in a user-visible diagnostics panel.
- If Nostr data is absent, prefetch TMDB fallback and switch back to Nostr when data arrives.

## Render Pattern

For every grid:

- Keep a render record keyed by grid ID.
- Store `source`, `sourceLength`, `total`, `rendered`, item keys, render keys, and item array.
- First render only enough items for about three rows.
- Append about two rows when the sentinel is visible or focus approaches the rendered end.
- If keys are unchanged and enough items are rendered, return without DOM replacement.
- If item keys share a prefix and render keys match, append only new cards.

For PanSou results:

- Patch existing nodes when possible.
- Reorder existing nodes instead of replacing when key sets overlap.
- Restore focus by `data-pan-key`.

## Focus Pattern

The demo separates fast paths and fallback:

- Homepage grid uses `data-card-index` and cached `gridColumns()`.
- Horizontal rails use previous/next siblings.
- Tabs and search form use explicit directional rules.
- PanSou tabs/results are a local focus domain.
- Search suggestions are a local focus domain.
- Connection/status panel is a local focus domain.
- Detail/image overlays become the active `focusScopeRoot()`.
- `nearestFocusable()` geometry search only handles irregular fallback cases.

Use `focusRemoteTarget()` as the only focus entry point. It should:

- call `focus({ preventScroll: true })` with fallback,
- maybe append more grid items when near the end,
- schedule scroll correction through `requestAnimationFrame`,
- avoid scrolling homepage top chips when the user has scrolled down.

## PanSou Pattern

Core flow:

1. Reset PanSou state but keep existing results visible if useful.
2. Create a view token tied to the selected detail item.
3. POST `/api/search`.
4. Normalize `merged_by_type`.
5. Merge into existing result map by stable key.
6. Render tabs and active result list.
7. Poll after configured intervals and merge new results.
8. Observe visible result rows and batch `fm.pan.check()`.
9. Save playback return state before `fm.pan.play()`.
10. Restore active type, list scroll, detail scroll, and focus when returning.

Password behavior:

- Use `password` parameter for 115-like domains.
- Use `pwd` for most other pan links when adding password into URL.
- Pass `password` to `fm.pan.play()` regardless.

## Watch Tracking Pattern

Before opening native playback/search:

- save a pending watch item in `fm.cache`;
- start periodic `fm.stat()` sampling when possible.

On pause, pagehide, resume, or return:

- save UI snapshot;
- use `fm.history()` to compensate if WebView timers were paused;
- publish/sync only after the threshold and only once per media/user;
- clear pending watch after success or near completion.

## Playback Artwork Pattern

The demo separates poster/default artwork from playback background:

- `pic` comes from the selected media, history item, or native search result and is used as poster/default artwork.
- `playbackWallPic(item)` picks the best landscape image from explicit `wallPic`, current detail backdrop, selected media landscape image, or matched media landscape image. When high-resolution backdrop mode is enabled, TMDB backdrop URLs are upgraded to `original` for native playback.
- `vodPlaybackOptions(item)` returns `{ wallPic }` for `fm.vod()` when a backdrop exists.
- Detail search playback calls `fm.search(title, { direct: true, pic, wallPic })` so native search-result playback can retain the WebHome backdrop.
- `buildPanPlayPayload(item)` passes `pic` and `wallPic` to `fm.pan.play()` so pan/push playback uses the same native artwork.
- `preloadPlaybackArtwork(item)` calls `fm.preloadArtwork(pic, wallPic)` once per media/artwork pair and ignores preload failure.
- Native playback backgrounds are not derived from `pic`; if `wallPic` is missing, the player uses the App default background/wallpaper.

## What Not To Copy Blindly

- The bundled TMDB API key and relay list are example defaults; production pages should make them configurable or user-provided.
- Nostr relay behavior depends on remote relay policy; deletion is best-effort.
- PanSou API shape can vary; keep normalizers defensive.
- The demo is large. For smaller homepages, reuse its compatibility, SDK, focus, render, and restore patterns without copying unnecessary Nostr/PanSou machinery.
