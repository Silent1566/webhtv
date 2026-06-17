#!/usr/bin/env python3
"""Classify a WebHome target URL for direct fetch, WAF, and extension strategy.

The probe is intentionally passive: it sends ordinary HTTP GET requests with
plain user agents and never attempts challenge solving or fingerprint evasion.
"""

from __future__ import print_function

import argparse
import datetime
import json
import ssl
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BROWSER_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/125.0.0.0 Safari/537.36"
)

ANDROID_WEBVIEW_UA = (
    "Mozilla/5.0 (Linux; Android 10; WebHome) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Version/4.0 Chrome/70.0.3538.80 Mobile Safari/537.36"
)

UA_PROFILES = {
    "plain": "Python-urllib/3 WebHomeTargetProbe",
    "browser": BROWSER_UA,
    "android-webview": ANDROID_WEBVIEW_UA,
}


def now_iso():
    return datetime.datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def lower_headers(headers):
    out = {}
    for key, value in headers.items():
        out[key.lower()] = value
    return out


def decode_body(data):
    if not data:
        return ""
    for encoding in ("utf-8", "gb18030", "latin-1"):
        try:
            return data.decode(encoding, "replace")
        except Exception:
            pass
    return data.decode("latin-1", "replace")


def fetch(url, ua_name, ua, timeout, max_bytes, insecure):
    headers = {
        "User-Agent": ua,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
    }
    req = Request(url, headers=headers, method="GET")
    started = time.time()
    result = {
        "profile": ua_name,
        "ok": False,
        "status": None,
        "elapsedMs": None,
        "headers": {},
        "bodyBytes": 0,
        "title": None,
        "signals": [],
        "error": None,
    }
    try:
        context = ssl._create_unverified_context() if insecure else None
        resp = urlopen(req, timeout=timeout, context=context)
        try:
            status = getattr(resp, "status", resp.getcode())
            raw_headers = dict(resp.headers.items())
            body = resp.read(max_bytes)
        finally:
            resp.close()
    except HTTPError as exc:
        status = exc.code
        raw_headers = dict(exc.headers.items())
        body = exc.read(max_bytes)
    except URLError as exc:
        result["elapsedMs"] = int((time.time() - started) * 1000)
        result["error"] = str(exc.reason)
        return result
    except Exception as exc:
        result["elapsedMs"] = int((time.time() - started) * 1000)
        result["error"] = repr(exc)
        return result

    text = decode_body(body)
    headers_l = lower_headers(raw_headers)
    result["ok"] = 200 <= int(status) < 400
    result["status"] = int(status)
    result["elapsedMs"] = int((time.time() - started) * 1000)
    result["headers"] = raw_headers
    result["bodyBytes"] = len(body)
    result["title"] = extract_title(text)
    result["signals"] = classify_signals(status, headers_l, text)
    if insecure:
        result["signals"].append("tls-verification-disabled")
    return result


def extract_title(text):
    lower = text.lower()
    start = lower.find("<title")
    if start < 0:
        return None
    start = lower.find(">", start)
    end = lower.find("</title>", start)
    if start < 0 or end < 0:
        return None
    title = text[start + 1 : end].strip()
    return " ".join(title.split())[:160] or None


def classify_signals(status, headers, text):
    body = text[:262144].lower()
    server = headers.get("server", "").lower()
    set_cookie = headers.get("set-cookie", "").lower()
    signals = []

    if "cloudflare" in server or "cf-ray" in headers or "cf-cache-status" in headers:
        signals.append("cloudflare-edge")
    if "cf-ray" in headers:
        signals.append("cf-ray:" + headers.get("cf-ray", ""))
    if "__cf_bm" in set_cookie:
        signals.append("cf-bot-cookie")
    if "cf_clearance" in set_cookie:
        signals.append("cf-clearance-cookie")
    if status in (401, 403, 429, 503):
        signals.append("blocked-or-challenged-status")
    if "sorry, you have been blocked" in body:
        signals.append("cloudflare-block-page")
    if "attention required" in body and "cloudflare" in body:
        signals.append("cloudflare-attention-page")
    if "/cdn-cgi/challenge-platform" in body or "cf_chl_" in body:
        signals.append("cloudflare-js-challenge")
    if "cf-turnstile" in body or "turnstile" in body:
        signals.append("turnstile-challenge")
    if "captcha" in body or "hcaptcha" in body or "g-recaptcha" in body:
        signals.append("captcha-challenge")
    if "x-frame-options" in headers:
        signals.append("x-frame-options:" + headers.get("x-frame-options", ""))
    if "content-security-policy" in headers:
        signals.append("content-security-policy")
    if "<script" in body:
        signals.append("html-scripts-present")
    if "fetch(" in body or "xmlhttprequest" in body:
        signals.append("client-api-hints")
    return signals


def summarize(url, results):
    all_signals = []
    statuses = []
    for item in results:
        statuses.append(item.get("status"))
        all_signals.extend(item.get("signals") or [])

    signal_set = set(all_signals)
    cloudflare = any(sig.startswith("cloudflare") or sig.startswith("cf-") for sig in signal_set)
    challenge = any(
        sig in signal_set
        for sig in (
            "cloudflare-block-page",
            "cloudflare-attention-page",
            "cloudflare-js-challenge",
            "turnstile-challenge",
            "captcha-challenge",
        )
    )
    blocked_status = any(status in (401, 403, 429, 503) for status in statuses if status is not None)
    success = any(item.get("ok") for item in results)
    scripts = "html-scripts-present" in signal_set or "client-api-hints" in signal_set

    if cloudflare and (challenge or blocked_status) and not success:
        classification = "waf-blocked"
        homepage = "blocked"
        extension = "only-if-app-webview-can-load"
        needs_authorized = True
    elif cloudflare and (challenge or blocked_status):
        classification = "waf-mixed"
        homepage = "risky"
        extension = "prefer-if-app-webview-can-load"
        needs_authorized = True
    elif success and scripts:
        classification = "browser-js-site"
        homepage = "possible-after-api-validation"
        extension = "good-candidate"
        needs_authorized = False
    elif success:
        classification = "fetchable"
        homepage = "possible"
        extension = "optional"
        needs_authorized = False
    else:
        classification = "unreachable-or-unknown"
        homepage = "unknown"
        extension = "unknown"
        needs_authorized = True

    notes = []
    if classification.startswith("waf"):
        notes.append("Direct fm.req/curl style scraping is not reliable against this response.")
        notes.append("Use a WebHome extension only when the App WebView can normally open the page.")
        notes.append("If the App WebView is also blocked, use an authorized API, site-owner proxy, or supplied HAR/HTML.")
    else:
        notes.append("Validate target APIs in a real browser/WebView before committing to homepage scraping.")

    return {
        "url": url,
        "classification": classification,
        "cloudflareOrWaf": bool(cloudflare),
        "challengeOrBlock": bool(challenge or blocked_status),
        "webhomeStrategy": {
            "homepageFmReq": homepage,
            "extension": extension,
            "needsAuthorizedPath": needs_authorized,
            "notes": notes,
        },
    }


def main(argv):
    parser = argparse.ArgumentParser(description="Passive WebHome target/WAF probe")
    parser.add_argument("url")
    parser.add_argument("--profile", choices=["all"] + sorted(UA_PROFILES.keys()), default="all")
    parser.add_argument("--timeout", type=float, default=12.0)
    parser.add_argument("--max-bytes", type=int, default=262144)
    parser.add_argument("--insecure", action="store_true", help="Disable TLS certificate verification for diagnostics only")
    args = parser.parse_args(argv)

    profiles = sorted(UA_PROFILES.keys()) if args.profile == "all" else [args.profile]
    results = []
    for name in profiles:
        results.append(fetch(args.url, name, UA_PROFILES[name], args.timeout, args.max_bytes, args.insecure))

    output = {
        "checkedAt": now_iso(),
        "probe": "passive-http-get",
        "warning": "No challenge solving, stealth fingerprinting, or access-control bypass was attempted.",
        "summary": summarize(args.url, results),
        "results": results,
    }
    print(json.dumps(output, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
