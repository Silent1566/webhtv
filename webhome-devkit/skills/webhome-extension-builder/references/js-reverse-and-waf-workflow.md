# WebHome JS Reverse And WAF Workflow

Use this reference when a WebHome extension depends on hidden page APIs, request signatures, player state, encrypted URLs, anti-hotlink headers, runtime media requests, or WAF/Cloudflare feasibility.

## Boundary

- Work only on targets the user owns, is authorized to analyze, or can normally access.
- Do not build Cloudflare, CAPTCHA, Turnstile, rate-limit, fingerprint, or login bypass flows.
- Do not use stealth plugins, CAPTCHA solvers, stolen cookies, or token cracking.
- It is acceptable to diagnose a WAF, compare normal access paths, inspect first-party page runtime after legitimate access, and recommend an authorized API/proxy or site-owner configuration.
- Redact cookies, authorization headers, personal account data, and private tokens from logs and final output.

## Verified Tool Tiers

The following public projects were checked through GitHub metadata on 2026-06-14 and are strong enough to mention as optional tooling. Re-check versions before installation in a task.

| Tier | Tools | Use |
| --- | --- | --- |
| Browser observation | `microsoft/playwright` (~90k stars), `puppeteer/puppeteer` (~94k), Chrome DevTools Protocol, `microsoft/playwright-mcp` (~33k) | Open pages, record network, inspect frames, scripts, console, screenshots, request initiators, and runtime values. |
| CDP low-level | `ChromeDevTools/devtools-protocol`, `cyrus-and/chrome-remote-interface` (~4.5k) | Direct CDP sessions when an MCP/browser wrapper is unavailable. |
| Network capture | `mitmproxy/mitmproxy` (~43k) | Authorized device/browser traffic capture, HAR-like evidence, replay shape comparison. |
| Static JS/AST | `babel/babel` (~44k), `acornjs/acorn` (~11k), `beautifier/js-beautify` (~9k) | Parse, search, beautify, and transform scripts without brittle regex edits. |
| Deobfuscation/debundle | `j4k0xb/webcrack` (~2.7k) | Unminify, unpack webpack/browserify, and deobfuscate common obfuscator output after evidence identifies the relevant bundle. |
| JS reverse MCP | `zhizhuodemao/js-reverse-mcp` (~1.8k) plus local `libs/mcp-js-reverse-playbook` | Agent-oriented page observation, network listing, source search, XHR/fetch breakpoints, paused-frame inspection. |

Local baseline observed on 2026-06-14: Node 20.11.1, npm 10.2.4, Python 3.10.10, curl 8.4.0. Playwright, Babel parser, acorn, and mitmproxy were not installed as local Node/Python dependencies at that time.

CLI smoke tests with temporary caches on 2026-06-14:

- `npx playwright@latest --version` succeeded: Playwright 1.60.0.
- `npx @playwright/mcp@latest --version` succeeded: Playwright MCP 0.0.76.
- `npx js-beautify@latest --version` succeeded: js-beautify 1.15.4.
- `npx acorn@latest --ecma2017 --silent` parsed a sample script successfully.
- `npm view @babel/parser@latest version` returned 7.29.7.
- `python3 -m pip index versions mitmproxy` returned latest 11.0.2.
- `npx webcrack@latest --version` failed on this machine because webcrack 2.16.0 requires Node 22+ or 24+. Use it only in a matching Node runtime, or pin a tested compatible version after checking its package constraints.

## Feasibility Probe

Before choosing homepage scraping, extension injection, or an authorized backend path for an unknown site, run:

```bash
python3 scripts/probe_webhome_target.py https://target.example
```

If local Python certificate validation fails before HTTP headers are visible, rerun once with `--insecure` and record that TLS verification was disabled for diagnostics only.

Interpretation:

- `waf-blocked`: direct `curl`/`fm.req`-style fetching is not reliable. Build an extension only if the App WebView can normally load the page. If the App WebView is also blocked, require an authorized API, owner-controlled proxy, or user-supplied HAR/HTML.
- `waf-mixed`: proceed only with browser/WebView evidence; expect direct homepage requests to be brittle.
- `browser-js-site`: extension is usually the safest first implementation. Build a homepage only after API requests replay through `fm.req` without missing same-origin/session/runtime assumptions.
- `fetchable`: homepage or extension can both work; choose based on UX and data ownership.

The probe is passive. It does not solve challenges or alter fingerprints.

When a target requires login, PoW, browser safety verification, or a shield/WAF step that the user can legitimately complete, use a visible browser or App WebView debug session instead of continuing with curl-style guesses. Let the user finish the interaction in that same session, then capture the verified session's network, DOM, console, frames, scripts, and media evidence. This is normal observation; do not use stealth automation, CAPTCHA solving, clearance-cookie harvesting, or token cracking.

## Observe First

Collect evidence before writing extraction code:

- URL/page role: home, search/list, detail, play.
- Target request: method, URL, query/body fields, response type, status, timing, initiator.
- Script evidence: script URL, source map if present, bundle chunk name/hash, relevant function or string hits.
- Runtime source: DOM attribute, `onclick`, copied text, player constructor, global state, `localStorage`/`sessionStorage`, XHR/fetch response, WebSocket message, media element `src`.
- Session assumptions: same-origin requirement, cookies needed, referer/origin needed, user action needed, route timing, lazy chunk timing.

Prefer this tool order when available:

1. Browser/CDP/Playwright/MCP network list.
2. Request initiator and call stack.
3. Script list and source search for path/parameter names.
4. Narrow runtime hooks.
5. Breakpoints only where hooks and initiators are insufficient.
6. Local Node reproduction only after page evidence identifies the real entry.

## Runtime Hooks For Extensions

Use `document-start` only when the value appears before `document-end`, such as player constructors, `fetch`, `XMLHttpRequest`, WebSocket, `window.open`, or history routing.

Keep hooks narrow and reversible:

- Match target URL substrings or response shapes; never log every request body by default.
- Wrap original methods and call through faithfully.
- Log request URL, method, response content type, small redacted samples, and stack only when needed.
- Remove broad diagnostic hooks from production extension code after extracting the stable signal.
- For WebHome compatibility, write hook code at ES2017 or below. Avoid optional chaining, nullish coalescing, `replaceAll`, lookbehind regex, and other old WebView blockers.

Safe hook targets:

- `window.fetch`
- `XMLHttpRequest.prototype.open/send`
- `WebSocket`
- `HTMLMediaElement.prototype.src` setter and `play`
- Site-specific player constructors or global config objects
- `history.pushState/replaceState` and route events
- Copy/share button handlers when resource URLs only appear on user action

## Local Rebuild

Only rebuild what the page evidence proves:

1. Save the minimum relevant script or function.
2. Build a Node harness around the real entry function.
3. Stub host objects one at a time: `window`, `document`, `navigator`, `location`, `crypto`, storage, timers, text encoders, `atob`/`btoa`.
4. Record the first error or first divergence from the browser sample.
5. Add one minimal patch, retest, and record the result.
6. Stop when the extension can reliably get the needed URL/parameter, not when the whole site is emulated.

Use AST tools when source is too large or minified. Prefer Babel/acorn/webcrack/js-beautify over regex rewrites for structural changes.

## WAF And Cloudflare Decision Rule

For Cloudflare/WAF targets:

- If direct HTTP returns 403/429/503, `cf-ray`, challenge pages, Turnstile/CAPTCHA, or `/cdn-cgi/challenge-platform`, mark direct homepage data fetching as blocked or risky.
- If the App WebView can open the page through normal user access, prefer a WebHome extension that runs inside the loaded page and extracts same-origin runtime evidence.
- If the App WebView cannot open it, neither homepage nor extension is a reliable solution by itself. Ask for authorized API access, a site-owner allowlist/proxy, a public feed, or captured HAR/HTML from an authorized session.
- Do not try to bypass challenges by stealth browser patches, CAPTCHA solving, clearance-cookie harvesting, or token cracking.

## Deliverable Contract

When JS reverse work was needed, include:

- Target request table: URL pattern, method, parameters, headers required, response type.
- Evidence table: initiator, script URL/chunk, function/global, runtime value sample, source of sample.
- Reproduction status: browser-only, WebView-only, stable local Node, or not reproducible.
- WebHome strategy: extension, homepage, hybrid, or authorized backend required.
- Compatibility result: production JS remains ES2017-or-lower and old WebView blockers are absent.
- Risks: selectors/chunks likely to change, session/login dependency, same-origin dependency, WAF dependency, API rate/availability.
