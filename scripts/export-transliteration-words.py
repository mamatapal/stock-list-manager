#!/usr/bin/env python3
"""Copies the shop-word list of the Android Transliterate.kt into web/src/i18n/words.generated.json, so the web app
writes names by sound exactly like the Android app. Run from the repo root:  python3 scripts/export-transliteration-words.py"""
import json, re, pathlib

src = next(pathlib.Path("android").rglob("Transliterate.kt")).read_text(encoding="utf-8")
block = src[src.index("private val words"):src.index("private val reverseWords")]
pairs = re.findall(r'^\s*"([^"\\]+)"\s+to\s+"([^"\\]+)",?\s*$', block, re.M)
assert len(pairs) > 300, len(pairs)
out = pathlib.Path("web/src/i18n/words.generated.json")
out.write_text(json.dumps(pairs, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
print(len(pairs), "words")
