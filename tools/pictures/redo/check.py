"""Checks a file of Genesis scenes (second version).

    python3 check.py FILE FIRST-LAST

FILE: chapter, first, last, title, cast, god, people, limits, description (tab-separated). Every verse of
chapters FIRST to LAST must be in exactly one scene, in order. Prints problems; exit status 1 if any.
"""
import os
import re
import sys

S = "/tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad"
REPO = "/home/user/Test-bible/tools/pictures"

path, span = sys.argv[1], sys.argv[2]
first_ch, last_ch = (int(x) for x in span.split("-"))

counts = {}
for line in open(f"{S}/shots_data/kjv.tsv", encoding="utf-8"):
    book, ch, v = line.split("\t")[:3]
    if book == "Genesis":
        counts[int(ch)] = max(counts.get(int(ch), 0), int(v))

def keys(file):
    out = {}
    if os.path.exists(file):
        for line in open(file, encoding="utf-8"):
            if line.startswith("#") or not line.strip():
                continue
            k, looks = line.rstrip("\n").split("\t")
            out[k] = looks
    return out

people_keys = keys(f"{REPO}/cast.tsv")
nn = os.path.basename(path).split("_")[-1].split(".")[0]
people_keys.update(keys(f"{S}/pictures/v2/cast_{nn}.tsv"))
thing_keys = keys(f"{REPO}/things.tsv")

problems, warnings = [], []
scenes = []
for no, line in enumerate(open(path, encoding="utf-8"), 1):
    if not line.strip() or line.startswith("#"):
        continue
    f = line.rstrip("\n").split("\t")
    where = f"line {no}"
    if len(f) != 9:
        problems.append(f"{where}: {len(f)} fields, not 9")
        continue
    ch, a, b, title, cast, god, people, limits, desc = f
    try:
        ch, a, b = int(ch), int(a), int(b)
    except ValueError:
        problems.append(f"{where}: chapter and verses must be numbers")
        continue
    where = f"{ch}:{a}-{b}"
    scenes.append((ch, a, b))
    if god not in ("y", "n"):
        problems.append(f"{where}: god must be y or n")
    if people not in ("y", "n"):
        problems.append(f"{where}: people must be y or n")
    names = [k.strip() for k in cast.split(",") if k.strip()]
    for k in names:
        if k not in people_keys and k not in thing_keys:
            problems.append(f"{where}: no one or nothing called {k!r} in cast.tsv, things.tsv or your cast file")
    persons = [k for k in names if k in people_keys]
    if people == "n" and persons:
        problems.append(f"{where}: people is n but the cast has {persons}")
    if people == "y" and not persons:
        warnings.append(f"{where}: people is y with no cast keys (fine if they're described in words)")
    if people == "n" and re.search(r"\bpeople\b", desc, re.I):
        problems.append(f"{where}: an empty scene's description mentions people")
    if people == "n" and "no people" not in limits.lower():
        warnings.append(f"{where}: an empty scene's limits don't say 'No people anywhere.'")
    words = len(desc.split())
    if not 40 <= words <= 75:
        problems.append(f"{where}: description has {words} words (40 to 75)")
    if len(limits.split()) > 45:
        problems.append(f"{where}: limits have {len(limits.split())} words (at most 45)")
    if not title.strip() or len(title.split()) > 7:
        problems.append(f"{where}: title should be 1 to 7 words")
    for field, text in (("description", desc), ("limits", limits), ("title", title)):
        if re.search(r"\d", text):
            problems.append(f"{where}: digits in the {field} (spell numbers out)")
    if "the ark" in names:
        for word in ("ship", "boat", "hull", "keel", "mast", "sail", "stern", "deck"):
            if re.search(rf"\b{word}s?\b", desc, re.I):
                problems.append(f"{where}: the ark described with {word!r}")
    if re.search(r"\bark\b", desc, re.I) and "the ark" not in names:
        warnings.append(f"{where}: the ark is mentioned but not in the cast")
    if ch == 1 and a <= 13:
        for word in ("sun", "sunrise", "sunset", "sunshine", "sunlight", "dawn", "golden hour", "sunbeam"):
            if re.search(rf"\b{word}\b", desc, re.I):
                problems.append(f"{where}: {word!r} before the sun is made")
    for word in ("glowing tree", "halo", "aura"):
        if word in desc.lower():
            warnings.append(f"{where}: {word!r}: an invented sign?")
    if ch < 32 or (ch == 32 and a < 28):
        if re.search(r"\bIsrael\b", desc):
            problems.append(f"{where}: 'Israel' before 32:28")
    for k in persons:
        if k not in desc:
            warnings.append(f"{where}: cast key {k!r} isn't named in the description")

# Every verse of every chapter once, in order.
by_ch = {}
for ch, a, b in scenes:
    by_ch.setdefault(ch, []).append((a, b))
for ch in range(first_ch, last_ch + 1):
    these = by_ch.get(ch)
    if not these:
        problems.append(f"chapter {ch} is missing")
        continue
    expected = 1
    for a, b in these:
        if a != expected or b < a:
            problems.append(f"chapter {ch}: a scene {a}-{b} where verse {expected} was due")
        expected = b + 1
    if expected - 1 != counts[ch]:
        problems.append(f"chapter {ch}: scenes end at {expected - 1}, the chapter at {counts[ch]}")
for ch in by_ch:
    if not first_ch <= ch <= last_ch:
        problems.append(f"chapter {ch} isn't in {span}")

for p in problems:
    print("PROBLEM", p)
for w in warnings:
    print("warning", w)
print(f"{len(scenes)} scenes, {len(problems)} problems, {len(warnings)} warnings")
sys.exit(1 if problems else 0)
