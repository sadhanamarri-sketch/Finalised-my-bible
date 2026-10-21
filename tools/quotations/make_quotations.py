"""Writes the app's list of the Old Testament's quotations in the New, from quotations.txt.

    python3 make_quotations.py KJV_OSIS_XML CROSS_REFERENCES quotations.txt APP_ASSETS_DIR

quotations.txt is the reviewed list (see find_quotations.py), a quotation a line:

    Matt 2:15 = Hos 11:1
    Matt 27:46 = Ps 22:1
    Acts 2:34 = Ps 110:1        # anything after "#" is a note

the New Testament verse (or verses, "Rom 3:10-12"), then the Old Testament verses it quotes, more than
one separated by ";". Every reference is checked against the KJV, and each pair against the cross
references, as the app labels links among them (a pair none of them link is reported and left out).

APP_ASSETS_DIR/quotations.tsv, one pair a line: the New Testament verses, a tab, the Old Testament
ones, as the app names its books ("Matthew 2:15", "Psalms 22:1-2").
"""
import os
import re
import sys

osis_path, xref_path, list_path, out_dir = sys.argv[1:5]

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
# The app's names for the books (ui/components BIBLE_BOOKS), in the same order.
APP = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
       "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
       "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
       "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
       "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
       "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
       "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians",
       "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter",
       "1 John", "2 John", "3 John", "Jude", "Revelation"]
NT = 39

# Which verses the KJV has.
verses = set()
for m in re.finditer(r'<verse osisID="([^"]+)" sID=', open(osis_path, encoding="utf-8").read()):
    for osis_id in m.group(1).split():
        book, chapter, verse = osis_id.split(".")
        if book in OSIS:
            verses.add((OSIS.index(book), int(chapter), int(verse)))

REF = re.compile(r"^(\S+) (\d+):(\d+)(?:-(\d+))?$")


def ref(text, line_no):
    m = REF.match(text.strip())
    if not m or m.group(1) not in OSIS:
        sys.exit(f"line {line_no}: can't read {text!r}")
    book, chapter, first = OSIS.index(m.group(1)), int(m.group(2)), int(m.group(3))
    last = int(m.group(4) or first)
    for v in range(first, last + 1):
        if (book, chapter, v) not in verses:
            sys.exit(f"line {line_no}: the KJV has no {text.strip()}")
    return book, chapter, first, last


# The cross references between the Testaments, the New Testament's side first.
links = []
for line in open(xref_path, encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) < 3 or not parts[2].lstrip("-").isdigit():
        continue
    a = parts[0].split(".")
    b0, _, b1 = parts[1].partition("-")
    b = b0.split(".")
    if a[0] not in OSIS or b[0] not in OSIS:
        continue
    ra = (OSIS.index(a[0]), int(a[1]), int(a[2]), int(a[2]))
    same_chapter = not b1 or b1.split(".")[1] == b[1]
    rb = (OSIS.index(b[0]), int(b[1]), int(b[2]), int(b1.split(".")[2]) if b1 and same_chapter else int(b[2]))
    if (ra[0] >= NT) != (rb[0] >= NT):
        links.append((ra, rb) if ra[0] >= NT else (rb, ra))


def meets(a, b):
    return a[0] == b[0] and a[1] == b[1] and a[2] <= b[3] and b[2] <= a[3]


def name(r):
    book, chapter, first, last = r
    return f"{APP[book]} {chapter}:{first}" + (f"-{last}" if last != first else "")


pairs, unlinked = [], []
for no, line in enumerate(open(list_path, encoding="utf-8"), 1):
    line = line.split("#", 1)[0].strip()
    if not line:
        continue
    nt_text, _, ot_text = line.partition("=")
    nt = ref(nt_text, no)
    if nt[0] < NT:
        sys.exit(f"line {no}: {nt_text.strip()} isn't in the New Testament")
    for ot_part in ot_text.split(";"):
        ot = ref(ot_part, no)
        if ot[0] >= NT:
            sys.exit(f"line {no}: {ot_part.strip()} isn't in the Old Testament")
        if any(meets(nt, n) and meets(ot, o) for n, o in links):
            pairs.append((nt, ot))
        else:
            unlinked.append(f"{nt_text.strip()} = {ot_part.strip()}")

pairs = sorted(set(pairs))
with open(os.path.join(out_dir, "quotations.tsv"), "w", encoding="utf-8") as out:
    out.write("# The Old Testament's quotations in the New: the New Testament verses, then those they quote.\n")
    out.write("# Made by tools/quotations/make_quotations.py from the reviewed list there.\n")
    for nt, ot in pairs:
        out.write(f"{name(nt)}\t{name(ot)}\n")
print(f"{len(pairs)} quotations ({len({p[0] for p in pairs})} New Testament verses, "
      f"{len({p[1] for p in pairs})} Old Testament passages)")
if unlinked:
    print(f"left out, as no cross reference links them ({len(unlinked)}):")
    for u in unlinked:
        print("  " + u)
