"""Puts the Bible Society of India's own Telugu text (the Old Version) into the app (backlog 56).

    python3 -I tools/telugu/apply_bsi_text.py BSI_DIR REPORT_DIR [ASSETS_DIR]
    python3 -I tools/telugu/apply_bsi_text.py --check BSI_DIR REPORT_DIR

--check first: whether BSI's files are whole. The copy of 4 Nov 2026 wasn't: 6,734 of its verses had only
their first printed line (nearly all of Psalms, Proverbs, Job, Isaiah; songs, oracles, quotations and
lists in the other books), so nothing was put in. It writes REPORT_DIR/incomplete.tsv and changes nothing.

BSI_DIR holds BSI's files, one per book ({"book_name", "chapters": {"1": [{"verse", "text"}]}}), which
BSI gave for the app; they aren't kept in the repository. ASSETS_DIR is the app's assets folder
(app/src/main/assets by default, or a copy of it to try this out first). Written there:

- telugu/<Book>.json, the Telugu the app shows under each English verse: BSI's text, cut where the app's
  verses start. Where BSI prints verses together (Genesis 1:17-18 as one) or numbers them otherwise
  (Psalm 47, Numbers 7), its chapter is cut there by matching the letters of the two.
- telugu_together.json, the verses BSI prints together, each group whole, for the app to show that way
  (the setting "Verses printed together"): {"Genesis": {"1": [[17, 18, "text"], ...]}}.

BSI's text has a space inside a word here and there, where a printed line once broke (వెలు గిచ్చుటకును,
చీక టిని). Such a space is taken out where the app had the word whole and one half is a word nowhere
else in BSI's text.

REPORT_DIR gets what to look over: together.tsv (each group, whole and cut), joins.tsv (every space
taken out), unlike.tsv (verses whose letters differ much from the app's before, or that couldn't be cut).
"""
import collections
import difflib
import glob
import json
import os
import re
import sys

CHECK = sys.argv[1] == "--check"
args = sys.argv[2:] if CHECK else sys.argv[1:]
BSI_DIR, REPORT = args[0], args[1]
ASSETS = args[2] if len(args) > 2 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets")
os.makedirs(REPORT, exist_ok=True)

# A word: Telugu letters and signs, with the zero-width joiners some words have.
WORD = "[ఀ-౿‌‍]+"
TOKEN = re.compile(WORD)
PAIR = re.compile("(" + WORD + ") (" + WORD + ")")


def is_letter(ch):
    return "ఀ" <= ch <= "ౣ" and not ("౦" <= ch <= "౯")


def letters_of(text):
    """The text's Telugu letters, and where each is in the text."""
    at = [i for i, ch in enumerate(text) if is_letter(ch)]
    return "".join(text[i] for i in at), at


bsi = {}
for path in glob.glob(os.path.join(BSI_DIR, "*.json")):
    book = json.load(open(path, encoding="utf-8"))
    bsi[book["book_name"]] = book

# Words as BSI writes them, all through: what a space inside a word is checked against.
words = collections.Counter(
    w for b in bsi.values() for vs in b["chapters"].values() for v in vs for w in TOKEN.findall(v["text"]))

files = sorted(glob.glob(os.path.join(ASSETS, "telugu", "*.json")))

if CHECK:
    # A verse BSI has on its own but with under three quarters of the app's letters: cut short.
    rows, per_book = [], collections.Counter()
    for path in files:
        app = json.load(open(path, encoding="utf-8"))
        name = app["book"]
        for chapter in app["chapters"]:
            c = int(chapter["chapter"])
            theirs = {v["verse"]: v["text"] for v in bsi[name]["chapters"].get(str(c), [])}
            for v in chapter["verses"]:
                n = int(v["verse"])
                if n not in theirs:  # printed together with another verse
                    continue
                mine, got = letters_of(v["text"])[0], letters_of(theirs[n])[0]
                if mine and len(got) < 0.75 * len(mine):
                    rows.append((name, c, n, f"{len(got) / len(mine):.0%}", theirs[n].strip()))
                    per_book[name] += 1
    with open(os.path.join(REPORT, "incomplete.tsv"), "w", encoding="utf-8") as f:
        for row in rows:
            f.write("\t".join(str(x) for x in row) + "\n")
    print(f"verses cut short: {len(rows)}" + "".join(f"\n  {b}: {n}" for b, n in per_book.most_common()))
    sys.exit(1 if rows else 0)
# The files end as they did (with a newline or not).
ENDING = "\n" if open(files[0], encoding="utf-8").read().endswith("\n") else ""

together, joins, unlike = {}, [], []
groups_report = []
changed = total = 0
for path in files:
    app = json.load(open(path, encoding="utf-8"))
    name = app["book"]
    for chapter in app["chapters"]:
        c = chapter["chapter"]
        verses = chapter["verses"]
        entries = bsi[name]["chapters"][str(int(c))]
        # BSI's chapter as one text, a newline where it starts a verse.
        text = "\n".join(e["text"].strip() for e in entries)

        # Spaces inside a word, where a printed line once broke: the app had the word whole there,
        # and one half is a word nowhere else in BSI's text.
        app_words = [w for v in verses for w in TOKEN.findall(v["text"])]

        def join(m):
            a, b = m.group(1), m.group(2)
            whole = a + b
            if (words[a] <= 1 or words[b] <= 1) and any(whole in w for w in app_words):
                joins.append((name, c, f"{a} {b}", whole))
                return whole
            return m.group(0)

        while True:  # pairs overlap: a word's second half is the next pair's first
            fixed = PAIR.sub(join, text)
            if fixed == text:
                break
            text = fixed

        bl, bpos = letters_of(text)
        # The app's chapter: its letters, and the letter each verse starts at.
        al, starts = "", []
        for v in verses:
            starts.append(len(al))
            al += letters_of(v["text"])[0]
        to_b = {}
        for a, b, n in difflib.SequenceMatcher(None, al, bl, autojunk=False).get_matching_blocks():
            for k in range(n):
                to_b[a + k] = b + k

        def b_letter(i):
            # BSI's letter for the app's letter i: matched, or the one after the last matched before it.
            for back in range(0, 40):
                if i - back in to_b:
                    return to_b[i - back] + back
            return None

        # Where each verse after the first starts in BSI's text: at BSI's own break (a newline) when it's
        # in the gap before the letter, else just after the gap's last space.
        cuts = [0]
        for k in range(1, len(starts)):
            j = b_letter(starts[k])
            if j is None or j <= 0 or j >= len(bl):
                cuts.append(None)
                continue
            gap_from = bpos[j - 1] + 1
            gap = text[gap_from:bpos[j]]
            if "\n" in gap:
                cuts.append(gap_from + gap.index("\n") + 1)
            else:
                space = gap.rfind(" ")
                cuts.append(gap_from + space + 1 if space >= 0 else bpos[j])
        cuts.append(len(text))
        if None in cuts or any(b <= a for a, b in zip(cuts, cuts[1:])):
            unlike.append((name, c, "*", "its verses couldn't be placed: left as they were", ""))
            continue

        def at_break(pos):
            return pos == 0 or pos == len(text) or text[pos - 1] == "\n"

        def clean(piece):
            return re.sub(r"\s*\n\s*", " ", piece).strip()

        pieces = [clean(text[cuts[k]:cuts[k + 1]]) for k in range(len(verses))]
        # Verses BSI prints together: runs of the app's verses whose starts fall inside one of its verses.
        k = 0
        while k < len(verses):
            m = k
            while m + 1 < len(verses) and not at_break(cuts[m + 1]):
                m += 1
            if m > k:
                whole = clean(text[cuts[k]:cuts[m + 1]])
                first, last = int(verses[k]["verse"]), int(verses[m]["verse"])
                together.setdefault(name, {}).setdefault(str(int(c)), []).append([first, last, whole])
                groups_report.append((name, c, f"{first}-{last}", whole, " | ".join(pieces[k:m + 1])))
            k = m + 1

        for k, v in enumerate(verses):
            total += 1
            old, new = v["text"], pieces[k]
            if not new:
                unlike.append((name, c, v["verse"], "empty: left as it was", old))
                continue
            ratio = difflib.SequenceMatcher(None, letters_of(old)[0], letters_of(new)[0], autojunk=False).ratio()
            if ratio < 0.85:
                unlike.append((name, c, v["verse"], f"{ratio:.2f}", f"{old} || {new}"))
            if new != old:
                changed += 1
                v["text"] = new
    with open(path, "w", encoding="utf-8") as f:
        json.dump(app, f, ensure_ascii=False)
        f.write(ENDING)

with open(os.path.join(ASSETS, "telugu_together.json"), "w", encoding="utf-8") as f:
    json.dump(together, f, ensure_ascii=False)
    f.write("\n")
for file, rows in (("together.tsv", groups_report), ("joins.tsv", joins), ("unlike.tsv", unlike)):
    with open(os.path.join(REPORT, file), "w", encoding="utf-8") as f:
        for row in rows:
            f.write("\t".join(str(x) for x in row) + "\n")
print(f"verses {total}, changed {changed}; printed together: {len(groups_report)} groups; "
      f"spaces taken out {len(joins)}; unlike before {len(unlike)}")
