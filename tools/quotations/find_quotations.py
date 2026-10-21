"""Lists the cross references between the Testaments that may be quotations, for review.

    python3 find_quotations.py KJV_OSIS_XML CROSS_REFERENCES TOPICS_TSV OUT_TSV

KJV_OSIS_XML and CROSS_REFERENCES are the files the app downloads (see KjvImporter and
CrossReferenceImporter); TOPICS_TSV is app/src/main/assets/search/topics.tsv, whose Nave's topic
"Quotations and allusions" pairs New Testament verses with the Old Testament ones they quote or
allude to.

A candidate is a New Testament verse and an Old Testament verse (or a few) that are cross-referenced
and either paired by Nave's or share at least five words in a row in the KJV (two of them not little
words). OUT_TSV, one candidate per line, tab-separated: the two references, where the candidate came
from (N Nave's, W the words, NW both), the longest run of shared words, whether the New Testament
verse or the one before it says it's quoting ("it is written", "spoken by the prophet": Q), the
link's openbible.info votes, and both texts. Each is then read, and the quotations written into
quotations.txt (see make_quotations.py).
"""
import html
import re
import sys

osis_path, xref_path, topics_path, out_path = sys.argv[1:5]

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
NT = 39  # the New Testament's first book

# ---- the KJV ----
raw = open(osis_path, encoding="utf-8").read()
raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
text = {}
for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
    t = html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", m.group(3)))).strip()
    for osis_id in m.group(1).split():
        book, chapter, verse = osis_id.split(".")
        if book in OSIS:
            text[(OSIS.index(book), int(chapter), int(verse))] = t


def span_text(book, chapter, first, last):
    return " ".join(text.get((book, chapter, v), "") for v in range(first, last + 1))


def name(ref):
    book, chapter, first, last = ref
    return f"{OSIS[book]} {chapter}:{first}" + (f"-{last}" if last != first else "")


# ---- the words two verses share ----
def words(t):
    out = []
    for w in re.findall(r"[a-z]+", t.lower()):
        for ending in ("eth", "est", "ed", "s"):  # a light stem: loveth, lovest, loved, loves
            if len(w) > 4 and w.endswith(ending):
                w = w[:-len(ending)]
                break
        out.append(w)
    return out


LITTLE = set("the and of to in that he it for unto a i is was be his shall with not they them him all which ye "
             "my me thou thee thy you your by but as are this have from were or so at on when then there their "
             "will what".split())


def longest_run(a, b):
    """The longest run of words a and b share, and how many of its words aren't little ones."""
    best, best_content = 0, 0
    prev = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        for j in range(1, len(b) + 1):
            if a[i - 1] == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                if cur[j] > best:
                    best = cur[j]
                    best_content = sum(1 for w in a[i - best:i] if w not in LITTLE)
        prev = cur
    return best, best_content


QUOTING = re.compile(r"\b(it is written|as it is written|was spoken|spoken by|spoken of|that it might be fulfilled|fulfilled|"
                     r"the scripture|scriptures|saith the lord|esaias|isaias|jeremy|elias|david|moses|the prophet|"
                     r"the prophets|written in|he saith|it saith|is written|have ye not read|the law)\b", re.I)


def quoting(nt):
    book, chapter, first, _ = nt
    before = text.get((book, chapter, first - 1), "")
    return bool(QUOTING.search(span_text(*nt)) or QUOTING.search(before[-120:]))


# ---- the cross references between the Testaments, the New Testament's side first ----
links = {}
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
    end = int(b1.split(".")[2]) if b1 else int(b[2])
    rb = (OSIS.index(b[0]), int(b[1]), int(b[2]), end if (not b1 or b1.split(".")[1] == b[1]) else int(b[2]))
    if (ra[0] >= NT) == (rb[0] >= NT):
        continue
    nt, ot = (ra, rb) if ra[0] >= NT else (rb, ra)
    links[(nt, ot)] = max(links.get((nt, ot), 0), int(parts[2]))


def overlaps(a, b):
    return a[0] == b[0] and a[1] == b[1] and not (a[3] < b[2] or b[3] < a[2])


# ---- Nave's pairs: each New Testament reference, then the Old Testament ones after it ----
def unpack(token):
    first, _, more = token.partition("-")
    n = int(first, 36)
    book, chapter, verse = n // 65536, (n // 256) % 256, n % 256
    return book, chapter, verse, verse + (int(more) if more else 0)


naves = []
for line in open(topics_path, encoding="utf-8"):
    if line.startswith("E\t0\tIn the new testament from, and to, the old testament\t"):
        current = None
        for token in line.rstrip("\n").split("\t")[3].split(","):
            ref = unpack(token)
            if ref[0] >= NT:
                current = ref
            elif current:
                naves.append((current, ref))
        break

# ---- the candidates ----
candidates = {}
for (nt, ot), votes in links.items():
    if nt[3] - nt[2] > 3 or ot[3] - ot[2] > 6:
        continue
    run, content = longest_run(words(span_text(*nt)), words(span_text(*ot)))
    named = any(overlaps(n, nt) and overlaps(o, ot) for n, o in naves)
    if named or (run >= 5 and content >= 2):
        candidates[(nt, ot)] = ("N" if named else "") + ("W" if run >= 5 and content >= 2 else ""), run, votes
for n, o in naves:
    if not any(overlaps(n, nt) and overlaps(o, ot) for nt, ot in candidates):
        run, _ = longest_run(words(span_text(*n)), words(span_text(*o)))
        candidates[(n, o)] = "N", run, 0  # not among the cross references

with open(out_path, "w", encoding="utf-8") as out:
    for (nt, ot), (source, run, votes) in sorted(candidates.items()):
        out.write("\t".join([name(nt), name(ot), source, str(run), "Q" if quoting(nt) else "", str(votes),
                             span_text(*nt), span_text(*ot)]) + "\n")
counts = {}
for source, _, _ in candidates.values():
    counts[source] = counts.get(source, 0) + 1
print(f"{len(links)} cross references between the Testaments; {len(naves)} Nave's pairs; "
      f"{len(candidates)} candidates: {counts}")
