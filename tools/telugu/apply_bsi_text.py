"""Puts the Bible Society of India's own Telugu text (the Old Version) into the app (backlog 56).

    python3 -I tools/telugu/apply_bsi_text.py --check BSI_DIR REPORT_DIR
    python3 -I tools/telugu/apply_bsi_text.py BSI_DIR KJV REPORT_DIR [ASSETS_DIR]

BSI_DIR holds BSI's files, one per book ({"book_name", "chapters": {"1": [{"verse", "text"}]}}), which BSI
gave for the app; they aren't kept in the repository. KJV is the King James text the app downloads
(eng-kjv.osis.xml, from github.com/seven1m/open-bibles), used for its verses' lengths only. ASSETS_DIR is
the app's assets folder (app/src/main/assets by default, or a copy of it to try this out first).

--check: whether BSI's files are whole, each chapter's Telugu letters against the app's. The copy of
4 Nov 2026 wasn't (6,734 verses had only their first printed line); the copy of 6 Nov is (100.02% of the
app's letters, no chapter under 95%). Writes REPORT_DIR/short.tsv and changes nothing.

The words are BSI's ("why not use bsi directly"):
- Its line breaks inside a verse (where its page starts a new line: poetry, songs, a quotation, a list)
  become spaces; where a line broke inside a word (ఆత్మ|ను), the word is joined, as BSI writes it
  elsewhere (and where BSI never writes it either way, as the app's text before had it).
- A space inside a word where an older printing's line broke (వెలు గిచ్చుటకును) is taken out where one
  half is no word, neither is a word BSI often uses on its own, and the app had the word whole.
Its verses go under the English verses by BSI's own numbers. BSI prints some verses together, as one
entry numbered by the last (Genesis 1:17-18 as "18"): those are kept whole in telugu_together.json
({"Genesis": {"1": [[17, 18, "text"]]}}), for the app to show them together, and cut at the English
verses' starts (found by matching letters with the app's text before) for showing one under each.
Where BSI's numbers and the app's text before disagree on where a verse's Telugu goes (BSI's numbering
slips in places, 1 Chronicles 1:27-30; the app's text was a verse off in others, Acts 8:37, 1 John
5:9-19), the stretch is settled by the English verses' lengths: by BSI's numbers, by the app's verse
breaks, or shown together, whichever fits, BSI's numbers unless another clearly fits better. In the
chapters where BSI's numbers don't line up with the English verses at all, its text is placed by matching
letters with the app's text before.

Written to ASSETS_DIR: telugu/<Book>.json and telugu_together.json. To REPORT_DIR, to look over:
placed.tsv (each stretch settled, and how), together.tsv (each group, whole and cut), changed.tsv (verses
whose Telugu differs much from the app's before), breaks.tsv (line breaks inside words, joined),
joins.tsv (spaces taken out of words), unsure.tsv (line breaks left as spaces though a half isn't a word).
"""
import collections
import difflib
import glob
import json
import math
import os
import re
import sys

CHECK = len(sys.argv) > 1 and sys.argv[1] == "--check"
args = sys.argv[2:] if CHECK else sys.argv[1:]
if CHECK:
    BSI_DIR, REPORT = args[0], args[1]
    KJV = None
    rest = args[2:]
else:
    BSI_DIR, KJV, REPORT = args[0], args[1], args[2]
    rest = args[3:]
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ASSETS = rest[0] if rest else os.path.join(REPO, "app", "src", "main", "assets")
os.makedirs(REPORT, exist_ok=True)

# A word: Telugu letters and signs, with the zero-width joiners some words have.
WORD = "[ఀ-౿‌‍]+"
TOKEN = re.compile(WORD)
# A line break between two words, or inside one.
BREAK = re.compile("(" + WORD + ")[ \t]*\n[ \t]*(" + WORD + ")")
# Pairs of words with a space between, each counted (the lookbehind keeps them whole words).
PAIRS = re.compile("(?<![ఀ-౿‌‍])(?=(" + WORD + ") (" + WORD + "))")

# How far a verse's Telugu length strays from its English verse's, on the log of the ratio (measured on
# the chapters BSI numbers plainly), and what's added to both lengths (short verses vary more).
SIGMA, ROOM = 0.19, 12
# Most verses stand alone: a group hides how far its verses stray, so each verse in one costs this.
GROUP = 1.5
# Another placement than BSI's numbers must fit better by this much.
MARGIN = 1.0


def is_letter(ch):
    return "ఀ" <= ch <= "ౣ"


def letters_of(text):
    """The text's Telugu letters, and where each is in the text."""
    at = [i for i, ch in enumerate(text) if is_letter(ch)]
    return "".join(text[i] for i in at), at


def write_rows(name, rows):
    with open(os.path.join(REPORT, name), "w", encoding="utf-8") as f:
        for row in rows:
            f.write("\t".join(str(x) for x in row) + "\n")


bsi = {}
for path in glob.glob(os.path.join(BSI_DIR, "*.json")):
    with open(path, encoding="utf-8") as f:
        book = json.load(f)
    bsi[book["book_name"]] = book

files = sorted(glob.glob(os.path.join(ASSETS, "telugu", "*.json")))


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def entries_of(name, chapter):
    """BSI's verses of a chapter, as it numbers them, in its order: [(number, text)]."""
    return [(int(e["verse"]), e["text"]) for e in bsi[name]["chapters"].get(str(chapter), [])]


if CHECK:
    rows = []
    for path in files:
        app = load(path)
        for chapter in app["chapters"]:
            c = int(chapter["chapter"])
            mine = sum(len(letters_of(v["text"])[0]) for v in chapter["verses"])
            theirs = sum(len(letters_of(t)[0]) for _, t in entries_of(app["book"], c))
            if mine and theirs < 0.95 * mine:
                rows.append((app["book"], c, f"{theirs / mine:.0%}"))
    write_rows("short.tsv", rows)
    print(f"chapters cut short: {len(rows)}" + "".join(f"\n  {b} {c}: {r}" for b, c, r in rows[:40]))
    sys.exit(1 if rows else 0)

# The English verses' lengths (letters), from the OSIS file's verse markers.
OSIS_BOOKS = ("Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov "
              "Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal Matt Mark "
              "Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm Heb Jas 1Pet "
              "2Pet 1John 2John 3John Jude Rev").split()
with open(os.path.join(REPO, "app/src/main/java/com/example/mybible/model/TeluguBooks.kt"), encoding="utf-8") as f:
    BOOKS = re.findall(r'"([^"]+)" to "[^"]+"', f.read())
assert len(BOOKS) == len(OSIS_BOOKS) == 66
book_of = dict(zip(OSIS_BOOKS, BOOKS))
with open(KJV, encoding="utf-8") as f:
    osis = re.sub(r"<note\b.*?</note>", "", f.read(), flags=re.S)
english = collections.defaultdict(dict)
for m in re.finditer(r'<verse osisID="([^".]+)\.(\d+)\.(\d+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\4"\s*/>', osis, re.S):
    if m.group(1) in book_of:
        english[(book_of[m.group(1)], int(m.group(2)))][int(m.group(3))] = sum(
            ch.isalpha() for ch in re.sub(r"<[^>]+>", "", m.group(5)))
del osis

# How BSI writes words, all through its running text (a word just before or after a line break may be
# half of one): each word, and each pair of words with a space between.
words, pairs = collections.Counter(), collections.Counter()
for b in bsi.values():
    for vs in b["chapters"].values():
        for v in vs:
            lines = v["text"].split("\n")
            for k, line in enumerate(lines):
                tokens = TOKEN.findall(line)
                words.update(tokens[1 if k > 0 else 0:len(tokens) - 1 if k < len(lines) - 1 else len(tokens)])
                pairs.update((m.group(1), m.group(2)) for m in PAIRS.finditer(line))
# The app's words before, all through: what tells a word from half of one where BSI never writes it.
app_vocabulary = collections.Counter(
    w for path in files for ch in load(path)["chapters"] for v in ch["verses"] for w in TOKEN.findall(v["text"]))


def unbreak(text, breaks, where):
    """BSI's line breaks inside a verse: a space between words, joined where the line broke inside one:
    as BSI writes the two elsewhere, joined or apart; where it never does, joined where one half is no
    word anywhere (in BSI's running text or the app's), the next line doesn't start with a word that
    stands on its own (ఇకను, నా, కీడును), and the two together are a word the app had."""
    def fragment(w):
        return not words[w] and not app_vocabulary[w]

    def common(w):
        return words[w] + app_vocabulary[w] >= 2

    def one(m):
        a, b = m.group(1), m.group(2)
        joined, apart = words[a + b], pairs[(a, b)]
        if joined == apart:
            joined = int((fragment(a) or fragment(b)) and not common(b) and app_vocabulary[a + b] > 0)
            apart = 1 - joined
            if not joined and (fragment(a) or fragment(b)):
                unsure.append(where + (f"{a}|{b}",))
        if joined > apart:
            breaks.append(where + (f"{a}|{b}", a + b))
            return a + b
        return a + " " + b

    while True:  # a one-word line: its word is the next break's first half too
        fixed = BREAK.sub(one, text)
        if fixed == text:
            break
        text = fixed
    return re.sub(r"[ \t]+", " ", re.sub(r"\s*\n\s*", " ", text)).strip()


def unsplit(text, app_word_list, joins, where):
    """Spaces inside a word, where an older printing's line broke: one half is no word (BSI has it
    twice at most, the app never on its own), one is short (three letters at most: a line broke inside
    a word, not between two long ones), neither is a word BSI often uses on its own (its own spacing,
    నియమమేదియు లేదు, stays), and the app had the word whole. Every space between two words is
    weighed, a word's second half being the next pair's first."""
    def half(w):
        return words[w] <= 2 and not app_vocabulary[w]

    def letters(w):  # vowels and consonants, not the signs on them
        return sum("\u0c05" <= ch <= "\u0c39" for ch in w)

    spaces = []
    for m in PAIRS.finditer(text):
        a, b = m.group(1), m.group(2)
        whole = a + b
        if ((half(a) or half(b)) and max(words[a], words[b]) < 10 and min(letters(a), letters(b)) <= 3
                and any(whole in w for w in app_word_list)):
            joins.append(where + (f"{a} {b}", whole))
            spaces.append(m.start() + len(a))
    for at in reversed(spaces):
        text = text[:at] + text[at + 1:]
    return text


def by_number(entries, n):
    """BSI's verses by its own numbers, each entry on the English verses up to its number: units (first,
    last, start, end) in the chapter's text (its entries, a newline between), or None where they don't
    line up."""
    units, prev, pos = [], 0, 0
    for number, text in entries:
        if number <= prev:
            return None
        units.append((prev + 1, number, pos, pos + len(text)))
        prev, pos = number, pos + len(text) + 1
    return units if prev == n else None


def cuts_at(text, app_texts):
    """Where in [text] each of [app_texts] (the app's text before, verse by verse) starts, by matching
    letters: at BSI's own break (a newline) when one's in the gap before the letter, else just after the
    gap's last space. None where they can't be placed."""
    bl, bpos = letters_of(text)
    al, starts = "", []
    for t in app_texts:
        starts.append(len(al))
        al += letters_of(t)[0]
    to_b = {}
    for a, b, size in difflib.SequenceMatcher(None, al, bl, autojunk=False).get_matching_blocks():
        for k in range(size):
            to_b[a + k] = b + k

    def b_letter(i):
        # BSI's letter for the app's letter i: matched, or the one after the last matched before it.
        for back in range(0, 40):
            if i - back in to_b:
                return to_b[i - back] + back
        return None

    cuts = [0]
    for k in range(1, len(starts)):
        j = b_letter(starts[k])
        if j is None or j <= 0 or j >= len(bl):
            return None
        gap_from = bpos[j - 1] + 1
        gap = text[gap_from:bpos[j]]
        if "\n" in gap:
            cuts.append(gap_from + gap.index("\n") + 1)
        else:
            space = gap.rfind(" ")
            cuts.append(gap_from + space + 1 if space >= 0 else bpos[j])
    cuts.append(len(text))
    return None if any(b <= a for a, b in zip(cuts, cuts[1:])) else cuts


def trimmed(text, a, b):
    while a < b and text[a] in " \n":
        a += 1
    while b > a and text[b - 1] in " \n":
        b -= 1
    return a, b


def by_letters(text, app_texts):
    """BSI's text cut at the app's verse breaks; a run of verses whose breaks fall inside one of BSI's
    verses is one unit (printed together). Units as by_number's, or None."""
    cuts = cuts_at(text, app_texts)
    if cuts is None:
        return None

    def at_break(pos):
        return pos == 0 or pos == len(text) or text[pos - 1] == "\n"

    units, k = [], 0
    while k < len(app_texts):
        m = k
        while m + 1 < len(app_texts) and not at_break(cuts[m + 1]):
            m += 1
        units.append((k + 1, m + 1) + trimmed(text, cuts[k], cuts[m + 1]))
        k = m + 1
    return units


def fit(units, text, lengths, ratio):
    """How badly units' Telugu lengths fit their English verses' (lower is better)."""
    score = 0.0
    for f, l, a, b in units:
        te = len(letters_of(text[a:b])[0])
        en = sum(lengths.get(v, 0) for v in range(f, l + 1))
        z = math.log((te + ROOM) / (ratio * en + ROOM)) / SIGMA
        score += z * z / 2 + GROUP * (l - f)
    return score


def stretches(A, B):
    """Runs of units where two placements differ, each ending where both end a unit at the same verse and
    the same place in BSI's text."""
    out, i, j = [], 0, 0
    while i < len(A) and j < len(B):
        ua, ub = [A[i]], [B[j]]
        i, j = i + 1, j + 1
        while ua[-1][1] != ub[-1][1] or abs(ua[-1][3] - ub[-1][3]) > 1:
            if i < len(A) and (ua[-1][1], ua[-1][3]) < (ub[-1][1], ub[-1][3]):
                ua.append(A[i])
                i += 1
            elif j < len(B):
                ub.append(B[j])
                j += 1
            else:
                break
        same = len(ua) == len(ub) and all(
            x[:2] == y[:2] and abs(x[2] - y[2]) <= 1 and abs(x[3] - y[3]) <= 1 for x, y in zip(ua, ub))
        out.append((ua, ub, same))
    return out


def split_group(piece, app_texts, lengths, first):
    """A group's text cut one piece a verse: at the app's verse breaks, by matching letters; failing
    that, at the spaces nearest the English verses' shares of its length."""
    cuts = cuts_at(piece, app_texts)
    if cuts is None:
        total = sum(lengths.get(first + k, 1) for k in range(len(app_texts)))
        cuts, run = [0], 0
        spaces = [i + 1 for i, ch in enumerate(piece) if ch == " "]
        for k in range(len(app_texts) - 1):
            run += lengths.get(first + k, 1)
            want = len(piece) * run / total
            at = min(spaces, key=lambda s: abs(s - want)) if spaces else len(piece)
            cuts.append(max(at, cuts[-1]))
        cuts.append(len(piece))
    return [piece[a:b].strip() for a, b in zip(cuts, cuts[1:])]


# The files end as they did (with a newline or not).
ENDING = "\n" if open(files[0], encoding="utf-8").read().endswith("\n") else ""

together, placed, groups_report, changed_report, breaks, joins, unsure = {}, [], [], [], [], [], []
total = changed = 0
how = collections.Counter()
for path in files:
    app = load(path)
    name = app["book"]
    for chapter in app["chapters"]:
        c = int(chapter["chapter"])
        verses = chapter["verses"]
        app_texts = [v["text"] for v in verses]
        app_word_list = [w for t in app_texts for w in TOKEN.findall(t)]
        lengths = english[(name, c)]
        entries = [(n, unbreak(t, breaks, (name, c, n))) for n, t in entries_of(name, c)]
        text = unsplit("\n".join(t for _, t in entries), app_word_list, joins, (name, c))
        # The entries again, after spaces were taken out (a newline still between each).
        entries = list(zip([n for n, _ in entries], text.split("\n")))
        ratio = len(letters_of(text)[0]) / max(1, sum(lengths.values()))

        A = by_number(entries, len(verses))
        B = by_letters(text, app_texts)
        if A is None and B is None:
            placed.append((name, c, "*", "couldn't be placed: left as it was", "", ""))
            how["chapters left as they were"] += 1
            continue
        if A is None:
            units = B
            how["chapters placed by letters (BSI's numbers don't line up)"] += 1
            placed.append((name, c, "*", "by the app's verse breaks: BSI's numbers don't line up", "", ""))
        elif B is None:
            units = A
            how["chapters by BSI's numbers"] += 1
        else:
            units = []
            for ua, ub, same in stretches(A, B):
                if same:
                    units += ua
                    continue
                f, l = ua[0][0], ua[-1][1]
                whole = [(f, l, ua[0][2], ua[-1][3])]
                sa, sb, sw = (fit(u, text, lengths, ratio) for u in (ua, ub, whole))
                if sw + MARGIN < min(sa, sb):
                    pick, choice = whole, "together"
                elif sb + MARGIN < sa:
                    pick, choice = ub, "by the app's verse breaks"
                else:
                    pick, choice = ua, "by BSI's numbers"
                units += pick
                how["stretches " + choice] += 1
                span = f"{f}-{l}" if l != f else str(f)
                placed.append((name, c, span, choice, f"{sa:.1f} / {sb:.1f} / {sw:.1f}",
                               " | ".join(f"{u[0]}" + (f"-{u[1]}" if u[1] != u[0] else "") + ": " + text[u[2]:u[3]]
                                          for u in pick)))
            how["chapters by BSI's numbers"] += 1

        # Each English verse's Telugu; a group's whole, for showing it together.
        pieces = [None] * len(verses)
        for f, l, a, b in units:
            piece = text[a:b].replace("\n", " ").strip()
            if f == l:
                pieces[f - 1] = piece
                continue
            parts = split_group(piece, app_texts[f - 1:l], lengths, f)
            pieces[f - 1:l] = parts
            first, last = int(verses[f - 1]["verse"]), int(verses[l - 1]["verse"])
            together.setdefault(name, {}).setdefault(str(c), []).append([first, last, piece])
            groups_report.append((name, c, f"{first}-{last}", piece, " | ".join(parts)))

        for k, v in enumerate(verses):
            total += 1
            old, new = v["text"], pieces[k]
            if not new:
                changed_report.append((name, c, v["verse"], "empty: left as it was", old))
                continue
            ratio_same = difflib.SequenceMatcher(None, letters_of(old)[0], letters_of(new)[0], autojunk=False).ratio()
            if ratio_same < 0.85:
                changed_report.append((name, c, v["verse"], f"{ratio_same:.2f}", f"{old} || {new}"))
            if new != old:
                changed += 1
                v["text"] = new
    with open(path, "w", encoding="utf-8") as f:
        json.dump(app, f, ensure_ascii=False)
        f.write(ENDING)

with open(os.path.join(ASSETS, "telugu_together.json"), "w", encoding="utf-8") as f:
    json.dump(together, f, ensure_ascii=False)
    f.write("\n")
for file, rows in (("placed.tsv", placed), ("together.tsv", groups_report), ("changed.tsv", changed_report),
                   ("breaks.tsv", breaks), ("joins.tsv", joins), ("unsure.tsv", unsure)):
    write_rows(file, rows)
print(f"verses {total}, changed {changed}; printed together: {len(groups_report)} groups; "
      f"line breaks joined inside words {len(breaks)}; spaces taken out {len(joins)}; "
      f"much changed {len(changed_report)}")
for k, n in sorted(how.items()):
    print(f"  {k}: {n}")
