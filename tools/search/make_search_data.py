"""Writes Search's bundled data into the app: King James word forms, the modern-to-King-James
wording list, the Greek and Hebrew words with their meanings and King James renderings, and the
forms those words take in the text.

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
import unicodedata

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
# Hebrew letters' final forms, and Greek's final sigma, as the letter itself.
FINAL_LETTERS = str.maketrans("\u05da\u05dd\u05df\u05e3\u05e5\u03c2", "\u05db\u05de\u05e0\u05e4\u05e6\u03c3")


def dstrong(raw):
    """'G0863H' / 'H157g' -> 'G0863H' / 'H0157G'; None for anything else."""
    m = DSTRONG.match(raw.strip())
    return f"{m.group(1)}{int(m.group(2)):04d}{m.group(3).upper()}" if m else None


def base_of(key):
    return key[0] + str(int(key[1:5] if key[-1].isalpha() else key[1:]))


def script_key(text):
    """A Greek or Hebrew word with no accents, breathings or vowel points, lowercase, final letters
    as the plain ones: 'ἀγάπης' -> 'αγαπησ'. Same as originalKey in the app's OriginalSearch."""
    letters = (c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c).startswith("L"))
    return "".join(letters).lower().translate(FINAL_LETTERS)


verses_of = {}
# The forms each word takes in the text, by its number without the sense letter (G0863).
forms_of_number = {}


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
            cell = cols[strongs_column]
            braced = re.search(r"\{([^}]+)\}", cell)
            key = dstrong((braced.group(1) if braced else cell).split("=")[0])
            if not key:
                continue
            forms_of_number.setdefault(key[:5], set()).add(script_key(spelled(cols[1], cell)))
            if pattern.startswith("TAGNT") and not re.search("[NnKk]", m.group(5)):
                continue  # a word only some other manuscripts have, not the KJV's
            verses_of.setdefault(key, set()).add((BOOK_OF_STEP[m.group(1)], int(m.group(2)), int(m.group(3))))


def spelled(word, strongs):
    """The word as the text spells it, without the Hebrew prefixes TAHOT marks off ('and', 'the',
    'in'...): 'ה/אָֽרֶץ' -> 'אָֽרֶץ', its suffixes kept. TAGNT's 'ἠγάπησεν (ēgapēsen)' -> 'ἠγάπησεν'."""
    if "(" in word:
        return word.split("(")[0]
    parts, keys = word.split("/"), strongs.split("/")
    main = next((i for i, k in enumerate(keys) if "{" in k), 0)
    return "".join(parts[main:]) if len(parts) == len(keys) else word


read_step_words("TAGNT*", 3)
read_step_words("TAHOT*", 4)
print(f"{len(verses_of)} Greek and Hebrew word senses with verses", file=sys.stderr)

# TBESG/TBESH: one row per sense. Column 2 is "G0863H = a Meaning of"; column 4 the word itself
# (ἀφίημι), 5 its transliteration, 6 its grammar, starting "A:" for Aramaic; column 7 the gloss,
# which for a sense is "to release: forgive" (the word's meaning, then this sense's). A sense row
# keeps the part after the colon; the word's main row keeps the part before it ("to love: lover").
# Spelling slips in STEPBible's glosses, put right so a search for the word finds them (and a
# misspelled search doesn't look like a real word).
GLOSS_FIXES = {"recieve": "receive", "govenors": "governors", "neighours": "neighbours",
               "aggitate": "agitate", "barenness": "barrenness", "peacable": "peaceable",
               "transgresor": "transgressor", "prostatrate": "prostrate", "quano": "guano",
               "ungodlinessness": "ungodliness"}
meaning, meaning_of_base = {}, {}
# Abbott-Smith's note on each Greek word of the Hebrew words the Septuagint translates with it:
# "[in LXX chiefly for חֶסֶד ;]", "[frequently in LXX, and nearly always for בְּרִית ;]" (see the
# Septuagint links below).
septuagint_note = {}
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
            language = "G" if key[0] == "G" else "A" if cols[5].startswith("A:") else "H"
            lemma = unicodedata.normalize("NFC", re.sub(r"\s*,\s*", ", ", cols[3].strip()))
            meaning[key] = (language, lemma, cols[4].strip().replace(".", ""), gloss)
            meaning_of_base.setdefault(base_of(key), meaning[key])
            note = re.search(r"\[([^\]]*\bLXX\b[^\]]*)\]", cols[7]) if key[0] == "G" and len(cols) > 7 else None
            if note:
                septuagint_note.setdefault(key[:5], re.sub(r"<[^>]+>", " ", note.group(1)))


def kjv_renderings(definition):
    """The English words Strong's lists for a word: '(feast of) charity(-ably), dear, love' gives
    charity, charitably, feast, dear, love; '(loving-) kindness' lovingkindness and kindness;
    'right(-eous) (act, -ly, -ness)' righteous, righteously, righteousness."""
    if not definition:
        return set()
    d = re.sub(r"\[idiom\]|\[phrase\]|×|\bX\b|\+", " ", definition)
    out = set()
    for part in split_outside_brackets(d, ",;."):
        part = part.strip()
        if not part or part.lower().startswith("compare"):
            continue
        for m in re.finditer(r"\(([A-Za-z]+)-\)\s*([A-Za-z]+)", part):
            out.add((m.group(1) + m.group(2)).lower())
        # A word with its endings, then maybe more endings for it or for what they made:
        # husband(-man) (-ry) is husbandman and husbandry.
        for m in re.finditer(r"([A-Za-z]+)\(-([^)]*)\)(?:\s*\(([^)]*)\))?", part):
            stem = m.group(1).lower()
            made = {w for suffix in endings(m.group(2)) for w in attached(stem, suffix)}
            out |= made
            for suffix in endings(m.group(3) or "", dashed_only=True):
                out |= {w for base in made | {stem} for w in attached(base, suffix)}
        part = re.sub(r"\(-[^)]*\)", "", part)
        out |= set(words(re.sub(r"[()\[\]-]", " ", part)))
    # (The "s" of "man's" is a word of the text too, as the app splits it.)
    return {w for w in out if w in vocab and w not in STOPWORDS and len(w) > 1}


def split_outside_brackets(text, separators):
    """text split at each separator that isn't inside (): 'Juda(-h, -s); Jude' -> 'Juda(-h, -s)', ' Jude'."""
    parts, depth, current = [], 0, []
    for c in text:
        depth += (c == "(") - (c == ")")
        depth = max(depth, 0)
        if c in separators and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
    parts.append("".join(current))
    return parts


def endings(group, dashed_only=False):
    """'-h, -s' -> h, s; with dashed_only, '(act, -ly, -ness)' gives ly and ness, not act."""
    out = []
    for item in group.split(","):
        item = item.strip().lower()
        if item.startswith("-"):
            out.append(item.lstrip("-"))
        elif item and not dashed_only:
            out.append(item)
    return [e for e in out if e.isalpha()]


def attached(stem, ending):
    """stem with ending, both ways when stem ends in e: store + house, treasure + y -> treasury."""
    out = {stem + ending}
    if stem.endswith("e"):
        out.add(stem[:-1] + ending)
    return out


# Strong's own numbers have no senses, so every sense of a word starts from the word's renderings;
# each sense's share is then counted over its own verses, which tells the senses apart.
renderings_of = {}
# A Greek word Strong's says is "of Hebrew origin (H4899)": Messias, amēn, sabbaton, Abraam.
hebrew_origin = {}
for name in ("strongs-greek-dictionary.js", "strongs-hebrew-dictionary.js"):
    text = open(os.path.join(strongs_dir, name), encoding="utf-8").read()
    start = text.index("{", text.index("Dictionary = "))
    for key, entry in json.loads(text[start:text.rindex("}") + 1]).items():
        k = dstrong(key)
        if k:
            renderings_of[base_of(k)] = kjv_renderings(entry.get("kjv_def", ""))
        derivation = entry.get("derivation", "")
        if k and k[0] == "G" and re.match(r"of (Hebrew|Chaldee) origin", derivation):
            # Not the words it's only compared with: "of Chaldee origin (compare H06453)".
            named = re.sub(r"compare [^;)]*", "", derivation)
            hebrew_origin[k[:5]] = [dstrong(h)[:5] for h in re.findall(r"H\d+", named)]


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


# A rendering worth marking where the word is (the app's OriginalWord.marked): used in 5% of its
# verses or more, and at least 4 times likelier there than in any verse (logos: "word", not "say",
# which is everywhere).
MARK_SHARE, MARK_LIFT = 5, 4
base_share = {}


verses_with = {}
for verse, ws in verse_words.items():
    for w in ws:
        verses_with.setdefault(w, set()).add(verse)


def share_everywhere(rendering):
    if rendering not in base_share:
        found = set().union(*(verses_with.get(f, set()) for f in forms_of(rendering)))
        base_share[rendering] = 100 * len(found) / len(verse_words)
    return base_share[rendering]


rows = []
# Each sense's verse count, renderings' shares and the renderings worth marking, for the
# Septuagint links below.
sense_info = {}
for key in sorted(verses_of):
    language, lemma, translit, gloss = meaning.get(key) or meaning_of_base.get(base_of(key)) or (None,) * 4
    if translit is None:
        continue
    vs = {v for v in verses_of[key] if v in verse_words}  # the KJV's versification only
    if not vs:
        continue
    shares = []
    info = sense_info[key] = (len(vs), {}, set())
    for r in sorted(renderings_of.get(base_of(key), ())):
        forms = forms_of(r)
        share = round(100 * sum(1 for v in vs if verse_words[v] & forms) / len(vs))
        if share >= 2:
            marked = share >= MARK_SHARE and share >= MARK_LIFT * share_everywhere(r)
            shares.append(f"{r}:{share}{'*' if marked else ''}")
            info[1][r] = share
            if marked:
                info[2].add(r)
    rows.append(f"{key}\t{language}\t{lemma}\t{translit}\t{gloss}\t{len(vs)}\t{','.join(shares)}\t{encode_refs(vs)}")
with open(os.path.join(out_dir, "original_words.tsv"), "w", encoding="utf-8") as f:
    f.write("# Sense-level Strong's number; language (G Greek, H Hebrew, A Aramaic); the word, its transliteration\n")
    f.write("# and meaning (STEPBible TBESG/TBESH); how many verses it's in (TAGNT/TAHOT); King James renderings\n")
    f.write("# (Strong's via Open Scriptures) with the % of those verses using each, * for those worth marking there\n")
    f.write("# (see MARK_LIFT); the verses (see encode_refs).\n")
    f.write("\n".join(rows) + "\n")
print(f"{len(rows)} Greek and Hebrew word senses", file=sys.stderr)

# Every other spelling of each word in the text, for a search typed in Greek or Hebrew: ηγαπησεν
# finds agapaō. Its own spelling (the lemma) is in original_words.tsv already.
lemma_keys = {}
for key, (_, lemma, _, _) in meaning.items():
    lemma_keys.setdefault(key[:5], set()).update(script_key(part) for part in lemma.split(","))
form_rows = []
for number in sorted(forms_of_number):
    forms = sorted(f for f in forms_of_number[number] - lemma_keys.get(number, set()) if f)
    if forms and number in lemma_keys:
        form_rows.append(f"{number}\t{' '.join(forms)}")
with open(os.path.join(out_dir, "original_forms.tsv"), "w", encoding="utf-8") as f:
    f.write("# Strong's number<TAB>the other spellings of the word in TAGNT/TAHOT, without accents or vowel points,\n")
    f.write("# lowercase, final letters as plain ones, Hebrew without its prefixes (make_search_data.py, script_key).\n")
    f.write("\n".join(form_rows) + "\n")
print(f"{sum(len(r.split(' ')) for r in form_rows)} other spellings of {len(form_rows)} Greek and Hebrew words", file=sys.stderr)


# ---- the Septuagint: which Hebrew word a Greek word stands for ----
# The Septuagint, the Greek Old Testament the apostles quoted, translates the Hebrew with the
# Greek words of the New: agapē for ahavah, eleos for chesed, kurios for YHWH. Abbott-Smith's
# lexicon (TBESG) notes it for each word: "[in LXX chiefly for חֶסֶד ;]", "[in LXX for נשׂא, נוח
# hi., נתן, סלח ni., עזב, etc. ;]". A search for a Greek word also shows the Old Testament verses
# with the Hebrew word it stands for, and the other way round. Kept:
# - the word the note names first, when it's the one the Greek is "chiefly" for (or the only
#   one), if the King James translates them alike or its spelling names exactly one Hebrew word
#   and that word isn't far commoner (pareimi, "be present", is in 24 verses; bo, "come", 2,350);
# - from a plain list, or from references each with its Hebrew ("Ge 27:4 (אָהַב), Ge 27:27 (נָשַׁק)"),
#   the words the King James translates alike (aphiēmi: salach "forgive", not natan "give").
# "Alike": a rendering worth marking in both, in a tenth of the Greek word's verses and a quarter
# of the Hebrew's. Which Hebrew word a spelling names: the one pointed exactly so, else among
# those spelled so, one the King James translates like the Greek word, then the closest pointing.
HEBREW_SPELLING = re.compile(r"[\u0590-\u05FF\uFB1D-\uFB4F]+")
CHIEFLY = re.compile(r"\b(chiefly|very freq|freq\.|frequently|mostly|usually|commonly|generally|always)\b")
POINTS = set(chr(c) for c in range(0x05B0, 0x05BD)) | {"\u05C1", "\u05C2", "\u05C7"}


def pointed_key(text):
    """Hebrew letters with their vowel points, without the accents: 'חֶ֫סֶד' -> 'חֶסֶד'."""
    out = []
    for c in unicodedata.normalize("NFD", text):
        if "\u05D0" <= c <= "\u05EA":
            out.append(c.translate(FINAL_LETTERS))
        elif c in POINTS:
            out.append(c)
    return "".join(out)


def distance(a, b):
    previous = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        current = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            current[j] = min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (a[i - 1] != b[j - 1]))
        previous = current
    return previous[-1]


word_info = {}  # number -> verses, {rendering: share over all its senses}, renderings worth marking
for key, (count, shares, marked) in sense_info.items():
    total, weighted, marks = word_info.get(key[:5], (0, {}, set()))
    for r, share in shares.items():
        weighted[r] = weighted.get(r, 0) + share * count
    word_info[key[:5]] = (total + count, weighted, marks | marked)


def alike(greek, hebrew, strict):
    g, h = word_info[greek], word_info[hebrew]
    common = g[2] & h[2]
    if not strict:
        return bool(common)
    return any(g[1][r] >= 10 * g[0] and h[1][r] >= 25 * h[0] for r in common)


hebrew_pointed, hebrew_unpointed, pointings = {}, {}, {}
for key, (language, lemma, _, _) in meaning.items():
    if language == "G" or key[:5] not in word_info:
        continue
    for part in lemma.split(","):
        hebrew_pointed.setdefault(pointed_key(part), set()).add(key[:5])
        hebrew_unpointed.setdefault(script_key(part), set()).add(key[:5])
        pointings.setdefault(key[:5], set()).add(pointed_key(part))


def hebrew_word(spelling, greek):
    """The number of the Hebrew word spelling names in greek's note, and whether its pointing names it
    alone. The note's pointing can slip: κάμηλος is "for גָּמַל", to wean, where camel is גָּמָל."""
    pointed = pointed_key(spelling)
    exact = (hebrew_pointed.get(pointed) if any(c in POINTS for c in spelling) else None) or set()
    candidates = exact | hebrew_unpointed.get(script_key(spelling), set())
    if not candidates:
        return None, False
    best = min(candidates, key=lambda n: (not alike(greek, n, True), not alike(greek, n, False), n not in exact,
                                          min(distance(pointed, p) for p in pointings[n]), -word_info[n][0], n))
    return best, exact == {best}


septuagint = {}
for greek, note in sorted(septuagint_note.items()):
    if greek not in word_info:
        continue
    # "chiefly for X, also for Y", or references each with its Hebrew: "Ge 27:4, al. (אָהַב), Ge 27:27 (נָשַׁק)".
    found = re.search(r"\bfor\b", note)
    chiefly = found is not None and CHIEFLY.search(note[:found.end()]) is not None
    named = []
    for spelling in HEBREW_SPELLING.findall(note[found.start():] if found else note):
        number, certain = hebrew_word(spelling, greek) if script_key(spelling) else (None, False)
        if number and number not in [n for n, _ in named]:
            named.append((number, certain))
    if not named:
        continue
    if chiefly or len(named) == 1:
        number, certain = named[0]
        commoner = word_info[number][0] > 4 * word_info[greek][0] + 100
        keep = [number] if alike(greek, number, True) or (certain and not commoner) else []
    else:
        keep = [n for n, _ in named if alike(greek, n, True)]
    if keep:
        septuagint[greek] = keep
# The Hebrew word a Greek one comes from, when it's that word (amēn, Messias, Dabid), not a name
# made of several (Bartimaios: bar, "son", and tame, "unclean"), with the same care for a far
# commoner word (Melchi isn't melekh, "king").
origins = {}
for g, hs in hebrew_origin.items():
    if len(hs) == 1 and g in word_info and hs[0] in word_info:
        h = hs[0]
        if alike(g, h, False) or word_info[h][0] <= 4 * word_info[g][0] + 100:
            origins[g] = [h]
with open(os.path.join(out_dir, "greek_hebrew.tsv"), "w", encoding="utf-8") as f:
    f.write("# Greek word<TAB>the Hebrew words the Septuagint translates with it, from Abbott-Smith's notes in STEPBible's\n")
    f.write("# TBESG (\"in LXX chiefly for ...\", kept as make_search_data.py's Septuagint section says)<TAB>the Hebrew\n")
    f.write("# words it comes from, as Strong's says (\"of Hebrew origin (H4899)\").\n")
    f.write("\n".join(f"{g}\t{' '.join(septuagint.get(g, []))}\t{' '.join(origins.get(g, []))}"
                      for g in sorted(septuagint.keys() | origins.keys())) + "\n")
print(f"{len(septuagint)} Greek words with the Hebrew they stand for in the Septuagint "
      f"({len({h for hs in septuagint.values() for h in hs})} Hebrew words); {len(origins)} of Hebrew origin", file=sys.stderr)
