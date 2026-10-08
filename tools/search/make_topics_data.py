"""Writes Search's topics into the app: Nave's Topical Bible, as BibleData structured it.

    python3 make_topics_data.py KJV_OSIS_XML NAVES_CSV APP_ASSETS_DIR

KJV_OSIS_XML is the file the app downloads (see KjvImporter). NAVES_CSV is BibleData's
NavesTopicalDictionary.csv (github.com/BradyStephenson/bible-data, CC BY 4.0). APP_ASSETS_DIR is
app/src/main/assets/search.

topics.tsv, one line per item, tab-separated:

    T  name                                   a topic; the lines after it are its entries
    E  level  label  refs                     a heading (Nave's subtopic) and/or its verses
    S  level  topic  section  text            "see" another topic, or one of its sections:
                                              "See Care", or a sentence naming it

Levels are Nave's indents, 0 to 3. Topics and sections are numbered from 0 in file order; a
section is a topic's level-0 entry. refs are verse ranges, comma-separated: the first verse as
book * 65536 + chapter * 256 + verse in base 36, then "-" and the last verse's difference from
it when the range is longer than one verse.
"""
import csv
import html
import os
import re
import sys

osis_path, naves_path, out_dir = sys.argv[1:4]

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
# BibleData's book codes, in the same order.
CODES = ["GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH",
         "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SOS", "ISA", "JER", "LAM", "EZK",
         "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL",
         "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH",
         "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV"]
BOOK_OF = {code: i for i, code in enumerate(CODES)}
BOOK_OF.update({"So": BOOK_OF["SOS"], "Jude": BOOK_OF["JUD"], "1JHN": BOOK_OF["1JN"]})
CODE = "|".join(sorted(BOOK_OF, key=len, reverse=True))
# A book code, then a chapter: "So 8:6" is the Song of Solomon, "So 2KI 17:4" King So of Egypt.
BOOK_AT = re.compile(r"(?<![A-Za-z0-9])(" + CODE + r")\s+(?=\d+(?![A-Za-z0-9]))")
# References part at ";", and at ". " before another one (the quotations topic: "LUK 1:32,33. ISA 11:10").
SEPARATOR = re.compile(r";|\.\s+(?=(?:" + CODE + r")\s+\d|\d)")
SEGMENT = re.compile(r"^(?:with\s+)?(?:(" + CODE + r")\s+)?(\d+)(?::([\d,\-\s]+))?(?:-(\d+)(?::(\d+))?)?$")

# ---- the KJV: which verses exist, and how it capitalizes each word ----
raw = open(osis_path, encoding="utf-8").read()
raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
last_verse = {}
lower_count, capital_count = {}, {}
for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
    text = html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", m.group(3)))).strip()
    for osis_id in m.group(1).split():
        book, chapter, verse = osis_id.split(".")
        if book not in OSIS:
            continue  # the Apocrypha, which the app leaves out
        key = (OSIS.index(book), int(chapter))
        last_verse[key] = max(last_verse.get(key, 0), int(verse))
    # A word's usual case away from the start of a sentence: God, Moses, Jesus, but enemies.
    for w in re.finditer(r"(?<=[a-z,;] )[A-Za-z]+|(?<=[a-z,;] \()[A-Za-z]+", text):
        word = w.group(0)
        counts = lower_count if word.islower() else capital_count
        counts[word.lower()] = counts.get(word.lower(), 0) + 1


def proper(word):
    return capital_count.get(word, 0) > lower_count.get(word, 0)


ROMAN = re.compile(r"^(?=[IVXLC])(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})$")


def sentence_case(text):
    """Nave's ALL-CAPS words as in a sentence: 'SIN, FORGIVENESS OF' -> 'Sin, forgiveness of',
    'OF MAN FOR JESUS' -> 'Of man for Jesus', 'TYPIFIED, in Israel' -> 'Typified, in Israel'.
    Words already in lower or mixed case, and Roman numerals, stay as they are."""
    out, first = [], True
    for part in re.split(r"([A-Za-z]+)", text):
        if not part or not part[0].isalpha():
            out.append(part)
            continue
        if part == "S" and out and out[-1].endswith("'"):
            part = "s"  # LORD'S
        elif part.isupper() and len(part) > 1 and not ROMAN.match(part):
            word = part.lower()
            part = word.capitalize() if first or proper(word) else word
        elif first:
            part = part[0].upper() + part[1:]
        out.append(part)
        first = False
    return "".join(out)


# ---- references ----
dropped = {"ranges": 0, "segments": 0}


def ranges(book, chapter, verses, end_chapter=None, end_verse=None):
    """Checked against the KJV: a chapter it doesn't have is dropped, a verse past the end clipped."""
    last = last_verse.get((book, chapter))
    if last is None:
        dropped["ranges"] += 1
        return []
    if verses is None:
        return [((book, chapter, 1), (book, chapter, last))]
    out = []
    for first, final in verses:
        if first > last:
            dropped["ranges"] += 1
            continue
        if end_chapter is not None and last_verse.get((book, end_chapter)):
            out.append(((book, chapter, first), (book, end_chapter, min(end_verse, last_verse[(book, end_chapter)]))))
        else:
            out.append(((book, chapter, first), (book, chapter, min(max(final, first), last))))
    return out


def parse_refs(text):
    """'EXO 6:16-20; JOS 21:4,10; 23:13' -> verse ranges. A chapter alone is the whole chapter;
    a part without a book is the last book named; "with" joins a comparison in."""
    out, book = [], None
    for seg in SEPARATOR.split(text):
        seg = seg.strip().rstrip(".,")
        if not seg:
            continue
        m = SEGMENT.match(seg)
        if not m:
            dropped["segments"] += 1
            continue
        if m.group(1):
            book = BOOK_OF[m.group(1)]
        if book is None:
            dropped["segments"] += 1
            continue
        chapter = int(m.group(2))
        if m.group(4) and not m.group(3):  # chapters 73-83, whole
            for c in range(chapter, int(m.group(4)) + 1):
                out += ranges(book, c, None)
            continue
        if m.group(4):  # 1:1-2:3
            if not m.group(5):
                dropped["segments"] += 1
                continue
            first = int(re.split(r"[-,]", m.group(3).strip())[0])
            out += ranges(book, chapter, [(first, first)], int(m.group(4)), int(m.group(5)))
            continue
        if not m.group(3):
            out += ranges(book, chapter, None)
            continue
        verses = []
        for part in m.group(3).replace(" ", "").split(","):
            a, _, b = part.partition("-")
            if a.isdigit() and (not b or b.isdigit()):
                verses.append((int(a), int(b or a)))
            elif part:
                dropped["segments"] += 1
        out += ranges(book, chapter, verses)
    # Verses next to each other become one passage: "PRO 25:21,22" is Proverbs 25:21-22.
    merged = []
    for start, end in out:
        if merged and merged[-1][1][:2] == start[:2] and start[2] - merged[-1][1][2] in (0, 1) and start >= merged[-1][0]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], end))
        elif (start, end) not in merged:
            merged.append((start, end))
    return merged


def pack(ref):
    book, chapter, verse = ref
    return book * 65536 + chapter * 256 + verse


def base36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    s = ""
    while True:
        n, r = divmod(n, 36)
        s = digits[r] + s
        if n == 0:
            return s


def encode(refs):
    parts = []
    for start, end in refs:
        delta = pack(end) - pack(start)
        parts.append(base36(pack(start)) + ("-" + base36(delta) if delta else ""))
    return ",".join(parts)


# ---- topics ----
INLINE_LINK = re.compile(r"\[\d+\]([A-Z][A-Z0-9'\- ,]*[A-Z0-9])")
SEE = re.compile(r"^(?:see\s+also|also\s+see|see)\b[\s,:]*(.*)$", re.I)

rows = list(csv.DictReader(open(naves_path, encoding="utf-8-sig")))
names = [r["subject"].strip() for r in rows]
topic_of = {}
for i, name in enumerate(names):
    topic_of.setdefault(name.upper(), i)


def parse_entries(entry):
    """A topic's lines as (level, kind, ...): ("E", label, refs) or ("S", target name, text)."""
    out = []
    for line in entry.split("\n"):
        if not line.strip():
            continue
        level = min(3, round((len(line) - len(line.lstrip(" "))) / 5))
        body = line.strip().lstrip("-").strip().lstrip(";").strip()
        body = re.sub(r"^0F\b", "OF", body)
        see = SEE.match(body)
        if see:
            rest = re.sub(r"\[\d+\]", "", see.group(1))
            found = BOOK_AT.search(rest)
            for target in (rest[:found.start()] if found else rest).split(";"):
                target = target.strip(" .,")
                if target and not target[0].isdigit():
                    out.append((level, "S", target, None))
            if found:
                refs = parse_refs(rest[found.start():])
                if refs:
                    out.append((level, "E", "See also", refs))
            continue
        links = [m.group(1) for m in INLINE_LINK.finditer(body)]
        body = INLINE_LINK.sub(lambda m: sentence_case(m.group(1)), body)
        found = BOOK_AT.search(body)
        label = (body[:found.start()] if found else body).strip(" ,;:")
        refs = parse_refs(body[found.start():]) if found else []
        if links:
            # "Plural form of [394]ASHTORETH, which see": the line is a link to that topic.
            out.append((level, "S", links[0], label))
            if refs:
                out.append((level + 1, "E", "", refs))
            continue
        if len(label) <= 1 and not refs:
            continue  # a stray letter left by the conversion
        out.append((level, "E", sentence_case(label), refs))
    return out


entries_of = [parse_entries(r["entry"]) for r in rows]


def sections(topic):
    return [e for e in entries_of[topic] if e[0] == 0 and e[1] == "E"]


def resolve(target, current):
    """A "see" target to (topic, section): one of this topic's own sections ("HOLINESS OF",
    "YIELDING TO, below"), then a topic by name, exact or nearly ('ZEAL' -> 'ZEAL, RELIGIOUS',
    'ENEMIES' -> 'ENEMY'), then a section of one ('SIN, FORGIVENESS OF'; 'ANGER OF GOD' is GOD's
    'ANGER OF')."""
    t = re.sub(r"\b(also|above|below|which see|sub-topic)\b", " ", target, flags=re.I)
    t = re.sub(r"\s+", " ", t).strip(" ,.:").upper()
    if not t:
        return None
    own = names[current].upper()
    if t.startswith(own + ","):
        t = t[len(own) + 1:].strip()

    def section_of(topic, label):
        for i, section in enumerate(sections(topic)):
            heading = section[2].upper().strip(" ,")
            if heading in (label, "OF " + label) or heading.startswith(label + " ") or heading == label:
                return i
        return None

    def topics(name):
        exact = []
        for candidate in (name, name + "S", name + "ES", name[:-1] if name.endswith("S") else None,
                          name[:-3] + "Y" if name.endswith("IES") else None):
            if candidate and candidate in topic_of:
                exact.append(topic_of[candidate])
        stems = [name] + ([name[:-1]] if name.endswith("S") else []) + [name + "S"]
        longer = sorted({n for n in topic_of for stem in stems
                         if n.startswith(stem) and len(n) > len(stem) and not n[len(stem)].isalpha()}, key=len)
        return exact + [topic_of[n] for n in longer]

    own_section = section_of(current, t)
    if own_section is not None and re.search(r"\b(above|below)\b", target, re.I):
        return current, own_section
    found = topics(t)
    if found:
        return found[0], None
    if own_section is not None:
        return current, own_section
    splits = []
    if "," in t:
        head, rest = t.split(",", 1)
        splits.append((head.strip(), rest.strip()))
    of = re.match(r"^(.+) OF (.+)$", t)
    if of:
        splits.append((of.group(2), of.group(1) + " OF"))
    for head, rest in splits:
        candidates = topics(head)
        for topic in candidates:
            section = section_of(topic, rest)
            if section is not None:
                return topic, section
        if candidates:
            return candidates[0], None
    return None


display = [sentence_case(n) for n in names]
lines, links_made, links_lost, ref_count = [], 0, 0, 0
for i, name in enumerate(names):
    lines.append(f"T\t{display[i]}")
    section = -1
    for entry in entries_of[i]:
        level, kind = entry[0], entry[1]
        if kind == "E":
            if level == 0:
                section += 1
            ref_count += len(entry[3])
            lines.append(f"E\t{level}\t{entry[2]}\t{encode(entry[3])}")
            continue
        found = resolve(entry[2], i)
        if found is None or found == (i, None) or found == (i, section if level > 0 else None):
            links_lost += 1
            continue
        links_made += 1
        target, target_section = found
        text = entry[3] or ("See " + display[target] + ("" if target_section is None else
                                                         ": " + sections(target)[target_section][2]))
        lines.append(f"S\t{level}\t{target}\t{'' if target_section is None else target_section}\t{text}")

os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "topics.tsv"), "w", encoding="utf-8") as f:
    f.write("# Nave's Topical Bible (public domain), as structured by BibleData (Brady Stephenson, CC BY 4.0).\n")
    f.write("# Written by tools/search/make_topics_data.py, which describes the format.\n")
    f.write("\n".join(lines) + "\n")
print(f"{len(names)} topics, {ref_count} verse ranges, {links_made} links "
      f"({links_lost} to topics the data doesn't have); dropped {dropped['ranges']} ranges outside the KJV "
      f"and {dropped['segments']} unreadable references", file=sys.stderr)
