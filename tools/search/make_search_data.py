"""Writes Search's bundled data into the app: King James word forms, the modern-to-King-James
wording list, and the Greek and Hebrew words with their meanings and King James renderings.

    python3 make_search_data.py KJV_OSIS_XML STEP_DIR STRONGS_DIR APP_ASSETS_DIR

KJV_OSIS_XML is the file the app downloads (see KjvImporter). STEP_DIR holds STEPBible's TAGNT
(2 files), TAHOT (4 files), TBESG and TBESH, under their own names (see GreekImporter,
HebrewImporter, TbesgImporter, TbeshImporter). STRONGS_DIR holds Open Scriptures'
strongs-greek-dictionary.js and strongs-hebrew-dictionary.js. APP_ASSETS_DIR is
app/src/main/assets/search.
"""
import glob
import html
import json
import os
import re
import sys

osis_path, step_dir, strongs_dir, out_dir = sys.argv[1:5]
here = os.path.dirname(os.path.abspath(__file__))

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
# STEPBible's own book abbreviations, in the same order (see HebrewImporter/GreekImporter).
STEP = ["Gen", "Exo", "Lev", "Num", "Deu", "Jos", "Jdg", "Rut", "1Sa", "2Sa", "1Ki", "2Ki",
        "1Ch", "2Ch", "Ezr", "Neh", "Est", "Job", "Psa", "Pro", "Ecc", "Sng", "Isa", "Jer",
        "Lam", "Ezk", "Dan", "Hos", "Jol", "Amo", "Oba", "Jon", "Mic", "Nam", "Hab", "Zep",
        "Hag", "Zec", "Mal",
        "Mat", "Mrk", "Luk", "Jhn", "Act", "Rom", "1Co", "2Co", "Gal", "Eph", "Php", "Col",
        "1Th", "2Th", "1Ti", "2Ti", "Tit", "Phm", "Heb", "Jas", "1Pe", "2Pe", "1Jn", "2Jn",
        "3Jn", "Jud", "Rev"]
BOOK_OF_OSIS = {b: i for i, b in enumerate(OSIS)}
BOOK_OF_STEP = {b: i for i, b in enumerate(STEP)}
WORD = re.compile(r"[a-z]+")

# Same lists as the app's SearchWords: function words a query can do without, and pronouns. Neither
# is ever a base for word forms or a King James rendering.
OPTIONAL_WORDS = set("""a about after all also am an and any are art as at be been before being but by
can could did do does doest doeth doth dost even every for from had has hast hath have how if in into
is may might more most much must no nor not now o of on one or out own same shall shalt should so some
such than that the then there these this those to unto up upon very was wast were what when where which
who whom whose why will wilt with would yea yet""".split())
PRONOUNS = set("""me my mine we us our ours you your yours he him his she her hers it its they them
their theirs thee thou thy thine ye""".split())
STOPWORDS = OPTIONAL_WORDS | PRONOUNS


def words(text):
    return WORD.findall(text.lower())


# ---- the KJV text, as the app imports it ----
raw = open(osis_path, encoding="utf-8").read()
raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
kjv = {}
for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
    body = html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", m.group(3)))).strip()
    for osis_id in m.group(1).split():
        book, chapter, verse = osis_id.split(".")
        if book in BOOK_OF_OSIS:  # the file also has the Apocrypha, which the app leaves out
            kjv[(BOOK_OF_OSIS[book], int(chapter), int(verse))] = body
vocab = {}
for text in kjv.values():
    for w in words(text):
        vocab[w] = vocab.get(w, 0) + 1
verse_words = {key: set(words(text)) for key, text in kjv.items()}
print(f"{len(kjv)} verses, {len(vocab)} distinct words", file=sys.stderr)


# ---- word forms ----
def candidates(b):
    """Inflected forms of b by the regular rules, KJV endings included."""
    out = set()
    cy = len(b) > 1 and b.endswith("y") and b[-2] not in "aeiou"
    cvc = (len(b) >= 3 and b[-1] not in "aeiouwxy" and b[-2] in "aeiou" and b[-3] not in "aeiou")
    stems = [b] + ([b + b[-1]] if cvc else [])
    if cy:
        out |= {b[:-1] + "ies", b[:-1] + "ied", b[:-1] + "ieth", b[:-1] + "iest", b[:-1] + "iedst", b + "ing"}
        return out
    if b.endswith(("s", "x", "z", "ch", "sh")):
        out.add(b + "es")
    else:
        out.add(b + "s")
    if b.endswith("o"):
        out.add(b + "es")
    if b.endswith("e"):
        out |= {b + "d", b + "th", b + "st", b + "dst"}
        if b.endswith("ie"):
            out.add(b[:-2] + "ying")
        elif not b.endswith(("ee", "oe", "ye")):
            out.add(b[:-1] + "ing")
        else:
            out.add(b + "ing")
    else:
        for s in stems:
            out |= {s + "ed", s + "eth", s + "est", s + "edst", s + "ing"}
    return out


irregular = []
for line in open(os.path.join(here, "irregular_forms.txt"), encoding="utf-8"):
    line = line.split("#")[0].split()
    if len(line) >= 2:
        irregular.append(line)
irregular_words = {w for family in irregular for w in family}
blocked, no_forms = set(), set()
for line in open(os.path.join(here, "not_forms.txt"), encoding="utf-8"):
    parts = line.split("#")[0].split()
    if len(parts) == 2:
        blocked.add(tuple(parts))
    elif len(parts) == 1:
        no_forms.add(parts[0])

families = []
for b in sorted(vocab):
    if len(b) < 3 or b in STOPWORDS or b in irregular_words or b in no_forms:
        continue
    forms = candidates(b) & vocab.keys()
    # A form of an irregular verb is that verb's (madest is make's, not mad's), and when b+"e"
    # is a word too, the forms they share are its (cared is care's, not car's).
    forms -= irregular_words
    if b + "e" in vocab:
        forms -= candidates(b + "e")
    forms = {f for f in forms if (b, f) not in blocked and f != b}
    if forms:
        families.append([b] + sorted(forms))
families += irregular
family_of = {}
for family in families:
    family_of.setdefault(family[0], family)


def forms_of(w):
    """The forms a search for w also finds: its own family and every family it's a form in
    (healing: healings, and heal's), never a stopword it didn't ask for (being: not is, was).
    Same rules as SearchLexicon.formsOf in the app."""
    if w in STOPWORDS:
        return {w}
    found = {w}
    for family in families:
        if w in family:
            found |= set(family)
    return {f for f in found if f == w or f not in STOPWORDS}


os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "word_forms.tsv"), "w", encoding="utf-8") as f:
    f.write("# One family of King James word forms per line, base word first (make_search_data.py).\n")
    for family in families:
        f.write(" ".join(family) + "\n")
print(f"{len(families)} word families", file=sys.stderr)


# ---- modern wording -> King James wording ----
def has_phrase(phrase):
    ws = phrase.split()
    if len(ws) == 1:
        return ws[0] in vocab
    pattern = re.compile(r"\b" + r"\W+".join(map(re.escape, ws)) + r"\b")
    return any(pattern.search(t.lower()) for t in kjv.values())


modern_lines = []
for n, line in enumerate(open(os.path.join(here, "modern_kjv.txt"), encoding="utf-8"), 1):
    line = line.split("#")[0].strip()
    if not line:
        continue
    left, right = (part.strip() for part in line.split("=", 1))
    modern = [m.strip().lower() for m in left.split(",") if m.strip()]
    kjv_terms = []
    for term in (t.strip().lower() for t in right.split(",")):
        if not term:
            continue
        if has_phrase(term):
            kjv_terms.append(term)
        else:
            print(f"modern_kjv.txt:{n}: '{term}' isn't in the KJV text, left out", file=sys.stderr)
    if kjv_terms:
        modern_lines.append("|".join(modern) + "\t" + "|".join(kjv_terms))
with open(os.path.join(out_dir, "modern_kjv.tsv"), "w", encoding="utf-8") as f:
    f.write("# modern|modern<TAB>kjv|kjv, from tools/search/modern_kjv.txt (make_search_data.py).\n")
    f.write("\n".join(modern_lines) + "\n")
print(f"{len(modern_lines)} modern wording entries", file=sys.stderr)


# ---- Greek and Hebrew words: where they occur, what they mean, how the KJV renders them ----
# Keyed by STEPBible's sense-level number (dStrong): G0863H is aphiemi where it means "forgive",
# G0863G where it means "leave". The app's search treats each sense as its own word.
REF = re.compile(r"^([0-9A-Za-z]+)\.(\d+)\.(\d+)(?:\([^)]*\))?#(\d+)=(\S*)$")
DSTRONG = re.compile(r"^([HG])(\d+)([A-Za-z]?)$")
# The app only uses words found in at most this many verses (more is a word like "God" or "the",
# whose verses mostly say it anyway), so only those get a verse list. Same as SearchLexicon.MAX_VERSES.
MAX_VERSES = 500


def dstrong(raw):
    """'G0863H' / 'H157g' -> 'G0863H' / 'H0157G'; None for anything else."""
    m = DSTRONG.match(raw.strip())
    return f"{m.group(1)}{int(m.group(2)):04d}{m.group(3).upper()}" if m else None


def base_of(key):
    return key[0] + str(int(key[1:5] if key[-1].isalpha() else key[1:]))


verses_of = {}


def read_step_words(pattern, strongs_column):
    for path in sorted(glob.glob(os.path.join(step_dir, pattern))):
        for line in open(path, encoding="utf-8"):
            if not line or line[0] == "#":
                continue
            cols = line.split("\t")
            if len(cols) <= strongs_column:
                continue
            m = REF.match(cols[0].strip())
            if not m or m.group(1) not in BOOK_OF_STEP:
                continue
            if pattern.startswith("TAGNT") and not re.search("[NnKk]", m.group(5)):
                continue  # a word only some other manuscripts have, not the KJV's
            cell = cols[strongs_column]
            braced = re.search(r"\{([^}]+)\}", cell)
            key = dstrong((braced.group(1) if braced else cell).split("=")[0])
            if key:
                verses_of.setdefault(key, set()).add((BOOK_OF_STEP[m.group(1)], int(m.group(2)), int(m.group(3))))


read_step_words("TAGNT*", 3)
read_step_words("TAHOT*", 4)
print(f"{len(verses_of)} Greek and Hebrew word senses with verses", file=sys.stderr)

# TBESG/TBESH: one row per sense. Column 2 is "G0863H = a Meaning of"; column 7 the gloss, which
# for a sense is "to release: forgive" (the word's meaning, then this sense's). A sense row keeps
# the part after the colon; the word's main row keeps the part before it ("to love: lover").
# Spelling slips in STEPBible's glosses, put right so a search for the word finds them (and a
# misspelled search doesn't look like a real word).
GLOSS_FIXES = {"recieve": "receive", "govenors": "governors", "neighours": "neighbours",
               "aggitate": "agitate", "barenness": "barrenness", "peacable": "peaceable",
               "transgresor": "transgressor", "prostatrate": "prostrate", "quano": "guano",
               "ungodlinessness": "ungodliness"}
meaning, meaning_of_base = {}, {}
for pattern in ("TBESG*", "TBESH*"):
    for path in glob.glob(os.path.join(step_dir, pattern)):
        for line in open(path, encoding="utf-8"):
            cols = line.split("\t")
            if len(cols) < 7 or "=" not in cols[1]:
                continue
            key = dstrong(cols[1].split("=")[0])
            if not key or key in meaning:
                continue
            relation = cols[1].split("=", 1)[1].strip()
            general, _, specific = cols[6].strip().partition(":")
            gloss = specific.strip() if relation == "a Meaning of" and specific.strip() else general.strip()
            gloss = re.sub(r"[a-z]+", lambda m: GLOSS_FIXES.get(m.group(0), m.group(0)), gloss)
            meaning[key] = (cols[4].strip().replace(".", ""), gloss)
            meaning_of_base.setdefault(base_of(key), meaning[key])


def kjv_renderings(definition):
    """The English words Strong's lists for a word: '(feast of) charity(-ably), dear, love'."""
    if not definition:
        return set()
    d = re.sub(r"\[idiom\]|\[phrase\]|×|\bX\b", " ", definition)
    out = set()
    for part in re.split(r"[,;.]", d):
        part = part.strip()
        if not part or part.lower().startswith("compare"):
            continue
        for m in re.finditer(r"([A-Za-z]+)\(-([^)]*)\)", part):
            stem = m.group(1).lower()
            for suffix in m.group(2).split(","):
                suffix = suffix.strip().lstrip("-").lower()
                if suffix:
                    out.add(stem[:-1] + suffix if stem.endswith("e") and suffix[0] in "aeiou" else stem + suffix)
        part = re.sub(r"\(-[^)]*\)", "", part)
        out |= set(words(re.sub(r"[()\[\]+]", " ", part)))
    return {w for w in out if w in vocab and w not in STOPWORDS}


# Strong's own numbers have no senses, so every sense of a word starts from the word's renderings;
# each sense's share is then counted over its own verses, which tells the senses apart.
renderings_of = {}
for name in ("strongs-greek-dictionary.js", "strongs-hebrew-dictionary.js"):
    text = open(os.path.join(strongs_dir, name), encoding="utf-8").read()
    start = text.index("{", text.index("Dictionary = "))
    for key, entry in json.loads(text[start:text.rindex("}") + 1]).items():
        k = dstrong(key)
        if k:
            renderings_of[base_of(k)] = kjv_renderings(entry.get("kjv_def", ""))


def encode_refs(refs):
    """Verses as book*65536 + chapter*256 + verse, ascending, each written as the base-36
    difference from the one before: 'Gen 1:1, Gen 1:2' -> '75,1' (257 is 75 in base 36)."""
    out, last = [], 0
    for book, chapter, verse in sorted(refs):
        packed = book * 65536 + chapter * 256 + verse
        out.append(base36(packed - last))
        last = packed
    return ",".join(out)


def base36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    s = ""
    while True:
        n, r = divmod(n, 36)
        s = digits[r] + s
        if n == 0:
            return s


rows = []
for key in sorted(verses_of):
    translit, gloss = meaning.get(key) or meaning_of_base.get(base_of(key)) or (None, None)
    if translit is None:
        continue
    vs = {v for v in verses_of[key] if v in verse_words}  # the KJV's versification only
    if not vs:
        continue
    shares = []
    for r in sorted(renderings_of.get(base_of(key), ())):
        forms = forms_of(r)
        share = round(100 * sum(1 for v in vs if verse_words[v] & forms) / len(vs))
        if share >= 2:
            shares.append(f"{r}:{share}")
    refs = encode_refs(vs) if len(vs) <= MAX_VERSES else ""
    rows.append(f"{key}\t{translit}\t{gloss}\t{len(vs)}\t{','.join(shares)}\t{refs}")
with open(os.path.join(out_dir, "original_words.tsv"), "w", encoding="utf-8") as f:
    f.write("# Sense-level Strong's number, transliteration and meaning (STEPBible TBESG/TBESH); verses it's in\n")
    f.write("# (TAGNT/TAHOT); King James renderings (Strong's via Open Scriptures) with the % of those verses\n")
    f.write(f"# using each; the verses, for words in at most {MAX_VERSES} (see encode_refs in make_search_data.py).\n")
    f.write("\n".join(rows) + "\n")
print(f"{len(rows)} Greek and Hebrew word senses", file=sys.stderr)
