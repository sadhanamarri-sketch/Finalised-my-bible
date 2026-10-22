"""Makes the picture prompts: the app's list of scenes and a document of them, from the scenes written here.

    python3 make_prompts.py KJV_TSV APP_ASSETS_DIR DOCS_DIR

Each book's scenes are in <book>.tsv here ("genesis.tsv"), a scene a line:

    chapter<TAB>first verse<TAB>last verse<TAB>title<TAB>cast<TAB>god<TAB>description

the cast being the keys of cast.tsv it shows (comma-separated), god "y" where God appears (shown only as
light). A scene's prompt is its description, the looks of its cast, and the same style and framing for
every picture (the look of the first picture made in ChatGPT, warm and cinematic), so every prompt asks
for the same kind of picture.

Every verse of a book's chapters has to be in exactly one scene, in order (checked against KJV_TSV, the
app's KJV: book, chapter, verse, text). Writes APP_ASSETS_DIR/pictures/scenes.tsv (book, chapter, first,
last, title, prompt) and DOCS_DIR/<Book>.md.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
kjv_path, assets_dir, docs_dir = sys.argv[1:4]

STYLE = ("Style: cinematic and photorealistic with a warm, painterly glow, like a still from a modern Bible "
         "film: rich warm colours, soft haze and gentle backlight, highly detailed faces and textured "
         "fabrics; golden-hour light unless the scene says otherwise. Historically accurate ancient Near "
         "Eastern clothing, tents and buildings.")
FRAMING = ("Vertical 9:16. A wide shot, the camera pulled back so the whole scene and its setting show. "
           "Everyone's heads in the lower half of the picture; the top half open (sky, hills, sea or "
           "buildings). Main action centred. No text, letters or numbers anywhere.")
GOD = "God is never shown as a person, only as radiant light."

# The books, as the app names them, in order.
BOOKS = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
         "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
         "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
         "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum",
         "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
         "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians",
         "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
         "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"]

cast = {}
for line in open(os.path.join(HERE, "cast.tsv"), encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    key, looks = line.rstrip("\n").split("\t")
    cast[key] = looks

verses = {}  # (book, chapter) -> how many verses
for line in open(kjv_path, encoding="utf-8"):
    book, chapter, verse = line.split("\t")[:3]
    key = (book, int(chapter))
    verses[key] = max(verses.get(key, 0), int(verse))


def name(book, chapter, first, last):
    """The picture's name, as the app saves it and looks for it: "Genesis 1.3-5", "Genesis 2.4"."""
    return f"{book} {chapter}.{first}" + (f"-{last}" if last != first else "")


def plain(key):
    """A cast key as it's said: "Isaac (boy)" is Isaac (the cast list tells how old)."""
    return key.split(" (")[0]


def prompt(scene):
    people = [k for k in scene["cast"] if k in cast]
    description = scene["description"].strip()
    for k in people:
        description = description.replace(k, plain(k))
    parts = [description]
    if people:
        parts.append("People: " + "; ".join(f"{plain(k)}: {cast[k]}" for k in people) + ".")
    if scene["god"]:
        parts.append(GOD)
    parts += [STYLE, FRAMING]
    return " ".join(parts)


def read_scenes(book, path):
    scenes, problems = [], []
    for no, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        fields = line.rstrip("\n").split("\t")
        if len(fields) != 7:
            problems.append(f"{path}:{no}: {len(fields)} fields")
            continue
        chapter, first, last, title, people, god, description = fields
        keys = [k.strip() for k in people.split(",") if k.strip()]
        for k in keys:
            if k not in cast:
                problems.append(f"{path}:{no}: no one called {k!r} in cast.tsv")
        scenes.append(dict(book=book, chapter=int(chapter), first=int(first), last=int(last), title=title.strip(),
                           cast=keys, god=god.strip() == "y", description=description))
    # Every verse of every chapter there, once, in order.
    by_chapter = {}
    for s in scenes:
        by_chapter.setdefault(s["chapter"], []).append(s)
    for chapter, these in sorted(by_chapter.items()):
        expected = 1
        for s in these:
            if s["first"] != expected or s["last"] < s["first"]:
                problems.append(f"{book} {chapter}: scene {s['first']}-{s['last']} where {expected} was due")
            expected = s["last"] + 1
        if expected - 1 != verses.get((book, chapter)):
            problems.append(f"{book} {chapter}: scenes end at {expected - 1}, the chapter at {verses.get((book, chapter))}")
    return scenes, problems


everything, problems = [], []
for book in BOOKS:
    path = os.path.join(HERE, book.lower().replace(" ", "_") + ".tsv")
    if not os.path.exists(path):
        continue
    scenes, found = read_scenes(book, path)
    problems += found
    everything += scenes

if problems:
    sys.exit("\n".join(problems))

os.makedirs(os.path.join(assets_dir, "pictures"), exist_ok=True)
with open(os.path.join(assets_dir, "pictures", "scenes.tsv"), "w", encoding="utf-8") as out:
    out.write("# A picture for every scene: book, chapter, first and last verse, title, the prompt to make it with.\n")
    out.write("# Made by tools/pictures/make_prompts.py.\n")
    for s in everything:
        out.write("\t".join([s["book"], str(s["chapter"]), str(s["first"]), str(s["last"]), s["title"], prompt(s)]) + "\n")

os.makedirs(docs_dir, exist_ok=True)
for book in dict.fromkeys(s["book"] for s in everything):
    these = [s for s in everything if s["book"] == book]
    with open(os.path.join(docs_dir, f"{book}.md"), "w", encoding="utf-8") as out:
        out.write(f"# {book}: picture prompts\n\n")
        out.write(f"{len(these)} pictures, one for each scene, covering every verse of {book}. For each: the name to save\n")
        out.write("the picture as (or use Add picture on the app's Pictures page, which names it for you), its verses,\n")
        out.write("and the prompt to paste into ChatGPT. Every prompt ends with the same style and framing, so the\n")
        out.write("pictures look alike: warm and cinematic, tall (9:16), wide, with the people in the lower half.\n")
        chapter = None
        for s in these:
            if s["chapter"] != chapter:
                chapter = s["chapter"]
                out.write(f"\n## Chapter {chapter}\n")
            verses_label = f"{s['chapter']}:{s['first']}" + (f"–{s['last']}" if s["last"] != s["first"] else "")
            out.write(f"\n### {verses_label} · {s['title']}\n\n")
            out.write(f"Save as: `{name(book, s['chapter'], s['first'], s['last'])}`\n\n")
            out.write(f"> {prompt(s)}\n")

books = dict.fromkeys(s["book"] for s in everything)
print(f"{len(everything)} scenes in {len(books)} book(s): " + ", ".join(
    f"{b} {sum(1 for s in everything if s['book'] == b)}" for b in books))
