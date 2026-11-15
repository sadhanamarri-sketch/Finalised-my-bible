"""Writes Torrey's New Topical Textbook into the app, a second topic book beside Nave's (backlog 55).

    python3 make_torrey_data.py KJV_OSIS_XML TORREY_ZIP APP_ASSETS_DIR

KJV_OSIS_XML is the file the app downloads (see KjvImporter). TORREY_ZIP is The SWORD Project's
module of it (www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/Torrey.zip: version 1.3,
"R. A. Torrey's New Topical Textbook", public domain, from the Bible Foundation's text).
APP_ASSETS_DIR is app/src/main/assets/search.

torrey.tsv has topics.tsv's format (see make_topics_data.py): T a topic, E level label refs, S level
topic section text. Torrey's lines are of two levels: "- " ones (0), and ". " ones (1) under the one
before, often a heading in capitals ("IS DESCRIBED AS"). A topic's "See PRAYER" is a link to that
topic; its number counts Torrey's topics alone, from 0 in file order (the app puts them after
Nave's). Every reference is checked against the KJV, as for Nave's.
"""
import html
import os
import re
import struct
import sys
import zipfile

osis_path, zip_path, out_dir = sys.argv[1:4]

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
# Torrey's book names, in the same order ("Jud" is Judges, "Jude" Jude; "So" the Song).
NAMES = ["Ge", "Ex", "Le", "Nu", "De", "Jos", "Jud", "Ru", "1Sa", "2Sa", "1Ki", "2Ki", "1Ch", "2Ch",
         "Ezr", "Ne", "Es", "Job", "Ps", "Pr", "Ec", "So", "Isa", "Jer", "La", "Eze", "Da", "Ho", "Joe",
         "Am", "Ob", "Jon", "Mic", "Na", "Hab", "Zep", "Hag", "Zec", "Mal", "Mt", "Mr", "Lu", "Joh",
         "Ac", "Ro", "1Co", "2Co", "Ga", "Eph", "Php", "Col", "1Th", "2Th", "1Ti", "2Ti", "Tit", "Phm",
         "Heb", "Jas", "1Pe", "2Pe", "1Jo", "2Jo", "3Jo", "Jude", "Re"]
BOOK_OF = {name: i for i, name in enumerate(NAMES)}
CODE = "|".join(sorted(BOOK_OF, key=len, reverse=True))
SEGMENT = re.compile(r"^(?:(" + CODE + r")\s+)?(\d+)(?::([\d,\-\s]+))?(?:-(\d+)(?::(\d+))?)?$")
# Three references to a third chapter of one-chapter books, each plain from its heading: "Proud"
# is Obadiah 3 ("The pride of thine heart"), deceit used "to themselves" Obadiah 3 and 7 ("have
# deceived thee"), and "Against false teachers" Philippians 3:2 ("beware of evil workers").
MISPRINTS = {"Ob 3:2": "Ob 1:3", "Ob 3:7": "Ob 1:3,7", "Phm 3:2": "Php 3:2"}
ACROSS = re.compile(r"^(" + CODE + r")\s+(\d+):(\d+)-(" + CODE + r")\s+(\d+):(\d+)$")

# ---- the KJV: which verses exist, and how it capitalizes each word (as make_topics_data.py) ----
raw = open(osis_path, encoding="utf-8").read()
raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
last_verse = {}
lower_count, capital_count = {}, {}
for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
    text = html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", m.group(3)))).strip()
    for osis_id in m.group(1).split():
        book, chapter, verse = osis_id.split(".")
        if book not in OSIS:
            continue
        key = (OSIS.index(book), int(chapter))
        last_verse[key] = max(last_verse.get(key, 0), int(verse))
    for w in re.finditer(r"(?<=[a-z,;] )[A-Za-z]+|(?<=[a-z,;] \()[A-Za-z]+", text):
        word = w.group(0)
        counts = lower_count if word.islower() else capital_count
        counts[word.lower()] = counts.get(word.lower(), 0) + 1

# Names of God the KJV's own case gets wrong taken a word at a time: "holy" and "spirit" are
# mostly lower case there, but not in "the Holy Spirit".
ALWAYS_CAPITAL = {"holy spirit": "Holy Spirit", "holy ghost": "Holy Ghost", "lord's supper": "Lord's Supper"}


def proper(word):
    return capital_count.get(word, 0) > lower_count.get(word, 0)


ROMAN = re.compile(r"^(?=[IVXLC])(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})$")


def sentence_case(text):
    """ALL-CAPS words as in a sentence: 'PRAYER, INTERCESSORY' -> 'Prayer, intercessory'. Words
    already in lower or mixed case, and Roman numerals, stay as they are."""
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
    s = "".join(out)
    for low, cap in ALWAYS_CAPITAL.items():
        s = re.sub(r"\b" + re.escape(low) + r"\b", cap, s, flags=re.I)
    return s


# ---- references (as make_topics_data.py) ----
dropped = {"ranges": 0, "segments": []}


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
    """'Joh 14:13,14; 15:16; Ps 5:2' -> verse ranges. A part without a book is the last book named;
    a chapter alone is the whole chapter."""
    out, book = [], None
    for seg in text.split(";"):
        # "(marg)": the verse's margin reading; the verse is what's meant.
        seg = re.sub(r"\(marg\)", "", seg).strip().rstrip(".,")
        seg = MISPRINTS.get(seg, seg)
        if not seg:
            continue
        across = ACROSS.match(seg)
        if across:  # "2Sa 2:4-1Ki 2:11": David's reign, to the end of one book and on into the next
            b1, c1, v1, b2, c2, v2 = BOOK_OF[across.group(1)], *map(int, across.group(2, 3)), BOOK_OF[across.group(4)], *map(int, across.group(5, 6))
            book = b2
            end1 = max(c for b, c in last_verse if b == b1)
            out += ranges(b1, c1, [(v1, v1)], end1, last_verse[(b1, end1)])
            for b in range(b1 + 1, b2):
                last_ch = max(c for bb, c in last_verse if bb == b)
                out += ranges(b, 1, [(1, 1)], last_ch, last_verse[(b, last_ch)])
            out += ranges(b2, 1, [(1, 1)], c2, v2)
            continue
        m = SEGMENT.match(seg)
        if not m:
            dropped["segments"].append(seg)
            continue
        if m.group(1):
            book = BOOK_OF[m.group(1)]
        if book is None:
            dropped["segments"].append(seg)
            continue
        chapter = int(m.group(2))
        if m.group(4) and not m.group(3):  # chapters 73-83, whole
            for c in range(chapter, int(m.group(4)) + 1):
                out += ranges(book, c, None)
            continue
        if m.group(4):  # 1:1-2:3
            if not m.group(5):
                dropped["segments"].append(seg)
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
                dropped["segments"].append(seg)
        out += ranges(book, chapter, verses)
    # Verses next to each other become one passage: "Joh 14:13,14" is John 14:13-14.
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


# ---- Torrey's: a SWORD RawLD module (an index of 4-byte offsets and 2-byte sizes into the data,
# where each entry is its key, a line break, and its text in ThML) ----
with zipfile.ZipFile(zip_path) as z:
    idx = z.read("modules/lexdict/rawld/torrey/torrey.idx")
    dat = z.read("modules/lexdict/rawld/torrey/torrey.dat")
entries = []
for i in range(0, len(idx), 6):
    off, size = struct.unpack("<IH", idx[i:i + 6])
    key, _, body = dat[off:off + size].decode("utf-8").partition("\n")
    entries.append((key.strip(), body))


def topic_name(key):
    """'ATONEMENT, THE' -> 'Atonement'; 'PRAYER, INTERCESSORY' -> 'Prayer, intercessory'."""
    return sentence_case(re.sub(r",\s*THE$", "", key.strip()))


def label(text):
    text = html.unescape(re.sub(r"\s+", " ", text)).strip()
    letters = re.sub(r"[^A-Za-z]", "", text)
    return sentence_case(text) if letters and letters.isupper() else text


TERM = re.compile(r"^See\s+<term>(.*?)</term>\.?$", re.I)
topics = []   # (name, [entry]); an entry is ["E", level, label, refs] or ["S", level, target key, text]
for key, body in entries:
    # A reference list wrapped onto the next line ("Le<br />17:11") is one list.
    body = re.sub(r"<scripRef>(.*?)</scripRef>", lambda m: "<scripRef>" + re.sub(r"<br\s*/>", " ", m.group(1)) + "</scripRef>", body, flags=re.S)
    lines = []
    for piece in re.split(r"<br\s*/>", body):
        piece = piece.strip()
        if not piece or piece in (".", "-"):
            continue
        ref = re.fullmatch(r"<scripRef>(.*?)</scripRef>", piece, re.S)
        if ref:
            if lines and lines[-1][0] == "E":
                lines[-1][3] += parse_refs(ref.group(1))
            continue
        marker = piece[:2]
        if marker in ("- ", ". "):
            level, text = (0 if marker == "- " else 1), piece[2:].strip()
            see = TERM.match(text)
            if see:
                lines.append(["S", level, see.group(1).strip(), "See " + label(see.group(1))])
            else:
                lines.append(["E", level, text, []])
        elif lines and lines[-1][0] == "E":
            # A long heading carried onto the next line: "...in their intercourse with the" "world".
            lines[-1][2] += " " + piece
    for line in lines:
        if line[0] == "E":
            line[2] = label(line[2])
    topics.append((topic_name(key), lines))

# Links to the topic they name, by its key, in any case: "AFFLICTION, Prayer Under".
def bare(key):
    return re.sub(r",\s*THE$", "", key.strip().upper())


number = {bare(key): i for i, (key, _) in enumerate(entries)}
unresolved = []
with open(os.path.join(out_dir, "torrey.tsv"), "w", encoding="utf-8") as f:
    f.write("# R. A. Torrey's New Topical Textbook (1897, public domain): The SWORD Project's module 1.3,\n")
    f.write("# from the Bible Foundation's text. Written by tools/search/make_torrey_data.py.\n")
    for name, lines in topics:
        f.write(f"T\t{name}\n")
        for line in lines:
            if line[0] == "E":
                f.write(f"E\t{line[1]}\t{line[2]}\t{encode(line[3])}\n")
            else:
                target = number.get(bare(line[2]))
                if target is None:
                    unresolved.append(line[2])
                    continue
                f.write(f"S\t{line[1]}\t{target}\t\tSee {topics[target][0]}\n")

refs = sum(len(l[3]) for _, lines in topics for l in lines if l[0] == "E")
print(f"{len(topics)} topics, {sum(len(l) for _, l in topics)} lines, {refs} verse ranges; "
      f"dropped {dropped['ranges']} ranges and {len(dropped['segments'])} parts not read: {dropped['segments'][:12]}; "
      f"links not found: {unresolved}")
