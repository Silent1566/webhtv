#!/usr/bin/env python3
"""Scan WebHome HTML/CSS/JS for old Android WebView compatibility risks."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ERROR_PATTERNS = [
    ("optional chaining", re.compile(r"\?\."), "Replace `a?.b` with guarded access such as `a && a.b`."),
    ("nullish coalescing", re.compile(r"\?\?"), "Replace `a ?? b` with explicit null checks."),
    ("logical assignment", re.compile(r"(\|\|=|&&=|\?\?=)"), "Expand logical assignment into ordinary assignment."),
    ("catch without binding", re.compile(r"\bcatch\s*\{"), "Use `catch (e) {}`."),
    ("regex lookbehind or named capture", re.compile(r"\(\?<"), "Avoid lookbehind and named capture groups."),
    ("module script", re.compile(r"<script\b[^>]*\btype\s*=\s*['\"]?module", re.I), "Use classic scripts for single-file WebHome."),
    ("focus-visible selector", re.compile(r":focus-visible\b"), "Use `:focus` or a JS-managed focus class."),
    ("modern selector", re.compile(r":(?:is|where|has)\s*\("), "Do not use :is(), :where(), or :has() in WebHome CSS."),
]

WARNING_PATTERNS = [
    ("replaceAll API", re.compile(r"\.replaceAll\s*\("), "Use split/join or global regex replace."),
    ("Promise.allSettled API", re.compile(r"\bPromise\.allSettled\s*\("), "Use Promise.all with per-item catch."),
    ("Object.fromEntries API", re.compile(r"\bObject\.fromEntries\s*\("), "Use an explicit loop."),
    ("structuredClone API", re.compile(r"\bstructuredClone\s*\("), "Use JSON clone for plain data."),
    ("globalThis API", re.compile(r"\bglobalThis\b"), "Use window."),
    ("AbortController API", re.compile(r"\bAbortController\b"), "Guard it or use request sequence tokens."),
    ("Array.flat API", re.compile(r"\.flat\s*\("), "Only use after bootstrap polyfill; avoid on critical paths."),
    ("Array.flatMap API", re.compile(r"\.flatMap\s*\("), "Only use after bootstrap polyfill; avoid on critical paths."),
    ("flex/grid gap", re.compile(r"\bgap\s*:"), "Provide no-layout-gap margin fallback."),
    ("aspect-ratio", re.compile(r"\baspect-ratio\s*:"), "Provide no-aspect-ratio fallback."),
    ("CSS functions", re.compile(r"\b(?:min|max|clamp)\s*\("), "Provide no-css-functions fallback for critical layout."),
    ("backdrop-filter", re.compile(r"\bbackdrop-filter\s*:"), "Pair with readable solid/semi-transparent background; reduce on TV."),
    ("inset", re.compile(r"\binset\s*:"), "Write top/right/bottom/left fallback first."),
    ("dynamic viewport unit", re.compile(r"\b\d+(?:dvh|svh|lvh)\b"), "Use var(--fm-web-height, 100vh) fallback."),
    ("safe-area env", re.compile(r"env\s*\(\s*safe-area-inset-", re.I), "Do not add env() to --fm-safe-*; use max() with fallback."),
    ("content-visibility", re.compile(r"\bcontent-visibility\s*:"), "Avoid unless tested on target TV WebView."),
]

BOOTSTRAP_MARKERS = [
    "no-layout-gap",
    "no-css-functions",
    "no-aspect-ratio",
    "replaceChildren",
    "Element.prototype.closest",
]


def line_col(text: str, index: int) -> tuple[int, int]:
    line = text.count("\n", 0, index) + 1
    last_newline = text.rfind("\n", 0, index)
    col = index + 1 if last_newline < 0 else index - last_newline
    return line, col


def first_script_body(text: str) -> str:
    match = re.search(r"<script\b[^>]*>(.*?)</script>", text, re.I | re.S)
    return match.group(1) if match else ""


def scan_patterns(text: str, patterns):
    findings = []
    for name, pattern, advice in patterns:
      for match in pattern.finditer(text):
          line, col = line_col(text, match.start())
          snippet = text[match.start():match.end()].replace("\n", "\\n")
          findings.append((line, col, name, snippet, advice))
    findings.sort(key=lambda item: (item[0], item[1], item[2]))
    return findings


def scan_file(path: Path, max_findings: int, strict_warnings: bool) -> int:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        text = path.read_text(encoding="utf-8", errors="replace")
    errors = scan_patterns(text, ERROR_PATTERNS)
    warnings = scan_patterns(text, WARNING_PATTERNS)

    bootstrap_warnings = []
    missing = [marker for marker in BOOTSTRAP_MARKERS if marker not in text]
    if missing:
        bootstrap_warnings.append(
            (1, 1, "missing bootstrap markers", ", ".join(missing), "Use the ES5 compatibility bootstrap from assets/demo/nostr.html.")
        )
    first = first_script_body(text)
    if first and re.search(r"(\blet\b|\bconst\b|=>|`)", first):
        bootstrap_warnings.append(
            (1, 1, "first script may not be ES5", "<script>", "The first bootstrap script should use ES5 syntax only.")
        )
    warnings = bootstrap_warnings + warnings

    print(f"{path}: {len(errors)} error(s), {len(warnings)} warning(s)")
    if errors:
        print("\nErrors:")
        for item in errors[:max_findings]:
            line, col, name, snippet, advice = item
            print(f"  {line}:{col}: {name}: {snippet}")
            print(f"    {advice}")
        if len(errors) > max_findings:
            print(f"  ... {len(errors) - max_findings} more error(s)")
    if warnings:
        print("\nWarnings:")
        for item in warnings[:max_findings]:
            line, col, name, snippet, advice = item
            print(f"  {line}:{col}: {name}: {snippet}")
            print(f"    {advice}")
        if len(warnings) > max_findings:
            print(f"  ... {len(warnings) - max_findings} more warning(s)")

    if errors:
        return 1
    if strict_warnings and warnings:
        return 1
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Check WebHome HTML for old WebView compatibility risks.")
    parser.add_argument("files", nargs="+", help="HTML/CSS/JS files to scan")
    parser.add_argument("--max-findings", type=int, default=80, help="Maximum findings to print per severity")
    parser.add_argument("--strict-warnings", action="store_true", help="Exit non-zero when warnings are present")
    args = parser.parse_args(argv)

    status = 0
    for name in args.files:
        path = Path(name)
        if not path.exists():
            print(f"{path}: not found", file=sys.stderr)
            status = 1
            continue
        status = max(status, scan_file(path, args.max_findings, args.strict_warnings))
    return status


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
