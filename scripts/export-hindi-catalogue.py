#!/usr/bin/env python3
"""Exports the Android app's English -> Hindi catalogue (HindiStrings.kt) to JSON for the web app, so both apps use the
same Hindi wording. Run from the repo root:  python3 scripts/export-hindi-catalogue.py
The web test suite checks that the committed JSON is up to date."""
import json, re, sys, pathlib

SRC = pathlib.Path("android/app/src/main/java/app/stocklist/app/core/i18n/HindiStrings.kt")
OUT = pathlib.Path("web/src/i18n/hi.generated.json")
ENTRY = re.compile(r'^\s*m\["((?:[^"\\]|\\.)*)"\]\s*=\s*"((?:[^"\\]|\\.)*)"\s*$')

def unescape(s: str) -> str:
    return re.sub(r'\\(["\\$nt])', lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), s)

def main() -> int:
    catalogue = {}
    for line in SRC.read_text(encoding="utf-8").splitlines():
        m = ENTRY.match(line)
        if m:
            catalogue[unescape(m.group(1))] = unescape(m.group(2))
    if len(catalogue) < 500:
        print(f"only {len(catalogue)} entries found; the catalogue format may have changed", file=sys.stderr)
        return 1
    OUT.write_text(json.dumps(dict(sorted(catalogue.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(catalogue)} entries -> {OUT}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
