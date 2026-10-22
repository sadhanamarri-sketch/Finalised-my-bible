"""Makes the picture prompts: the app's list of scenes and a document of them, from the scenes written here.

    python3 make_prompts.py KJV_TSV APP_ASSETS_DIR DOCS_DIR

Each book's scenes are in <book>.tsv here ("genesis.tsv"), a scene a line:

    chapter<TAB>first verse<TAB>last verse<TAB>title<TAB>cast<TAB>god<TAB>people<TAB>limits<TAB>description

the cast being the keys of the people (cast.tsv) and things (things.tsv) the picture shows, comma-separated;
god "y" where God is present (shown only as light); people "y" when anyone is in the picture, "n" when no
one is; limits the firm limits ChatGPT is to keep to (who and how many, what isn't there). A scene's prompt
is its description, its limits, the looks of its people and things, and an ending: the same style and
framing for every picture with people in it (the look of the first picture made in ChatGPT), and one
without any mention of people for a picture with none.

Every verse of a book's chapters has to be in exactly one scene, in order (checked against KJV_TSV, the
app's KJV: book, chapter, verse, text). Writes APP_ASSETS_DIR/pictures/scenes.tsv (book, chapter, first,
last, title, prompt) and DOCS_DIR/<Book>.md, with each scene's verses.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# How every picture with people in it ends, and every picture with no one in it.
ENDING_PEOPLE = (
    "Style: cinematic and photorealistic with a warm, painterly glow, like a still from a modern Bible film: "
    "rich warm colours, soft haze and gentle backlight, finely detailed faces, skin and textures. Light and "
    "time of day as the scene says; golden-hour light when it doesn't say. Everything true to the ancient "
    "Near East. Vertical 9:16. A wide shot, the camera pulled back so the whole scene and its setting show. "
    "Everyone's heads in the lower half of the picture; the top half open: sky, or the scene's own "
    "landscape. Main action centred. No text, letters or numbers anywhere."
)
ENDING_EMPTY = (
    "Style: cinematic and photorealistic with a painterly glow, like a still from a modern Bible film: rich "
    "colours, soft haze, gentle light. Light as the scene says. Vertical 9:16. A wide shot. The main subject "
    "in the lower half of the picture, the top half open. No people anywhere. No text, letters or numbers "
    "anywhere."
)
GOD = "God is shown only as radiant light."

# The books, as the app names them, in order.
BOOKS = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
         "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
         "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
         "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum",
         "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
         "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians",
         "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
         "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"]


def read_keys(name):
    keys = {}
    for line in open(os.path.join(HERE, name), encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        key, looks = line.rstrip("\n").split("\t")
        keys[key] = looks
    return keys


CAST = read_keys("cast.tsv")
THINGS = read_keys("things.tsv")


def read_kjv(path):
    """(book, chapter, verse) -> text."""
    text = {}
    for line in open(path, encoding="utf-8"):
        book, chapter, verse, words = line.rstrip("\n").split("\t")[:4]
        text[(book, int(chapter), int(verse))] = words
    return text


def name(book, chapter, first, last):
    """The picture's name, as the app saves it and looks for it: "Genesis 1.3-5", "Genesis 2.4"."""
    return f"{book} {chapter}.{first}" + (f"-{last}" if last != first else "")


def plain(key):
    """A cast key as it's said: "Isaac (lad)" is Isaac (the cast list tells how old)."""
    return key.split(" (")[0]


def prompt(scene):
    people = [k for k in scene["cast"] if k in CAST]
    things = [k for k in scene["cast"] if k in THINGS]
    description = scene["description"].strip()
    # Longest keys first, so "Noah's sons' wives" goes before "Noah's sons" and "Noah".
    for k in sorted(people, key=len, reverse=True):
        description = description.replace(k, plain(k))
    parts = [description]
    if scene["limits"]:
        parts.append(scene["limits"].strip())
    if people:
        parts.append("People: " + "; ".join(f"{plain(k)}: {CAST[k]}" for k in people) + ".")
    for k in things:
        parts.append(f"{k[0].upper()}{k[1:]}: {THINGS[k]}.")
    if scene["god"]:
        parts.append(GOD)
    parts.append(ENDING_PEOPLE if scene["people"] else ENDING_EMPTY)
    return " ".join(parts)


def read_scenes(book, path, verses):
    scenes, problems = [], []
    for no, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        fields = line.rstrip("\n").split("\t")
        if len(fields) != 9:
            problems.append(f"{path}:{no}: {len(fields)} fields")
            continue
        chapter, first, last, title, cast, god, people, limits, description = fields
        keys = [k.strip() for k in cast.split(",") if k.strip()]
        for k in keys:
            if k not in CAST and k not in THINGS:
                problems.append(f"{path}:{no}: no one or nothing called {k!r} in cast.tsv or things.tsv")
        if god not in ("y", "n") or people not in ("y", "n"):
            problems.append(f"{path}:{no}: god and people are y or n")
        if people == "n" and any(k in CAST for k in keys):
            problems.append(f"{path}:{no}: no people, but people in the cast")
        scenes.append(dict(book=book, chapter=int(chapter), first=int(first), last=int(last), title=title.strip(),
                           cast=keys, god=god == "y", people=people == "y", limits=limits.strip(),
                           description=description))
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
        last = max((v for (b, c, v) in verses if b == book and c == chapter), default=None)
        if expected - 1 != last:
            problems.append(f"{book} {chapter}: scenes end at {expected - 1}, the chapter at {last}")
    return scenes, problems


def verses_of(s, verses):
    """The scene's verses, numbered: "10 And he stayed ... 11 And the dove ..."."""
    return " ".join(f"{v} {verses[(s['book'], s['chapter'], v)]}" for v in range(s["first"], s["last"] + 1))


def main(kjv_path, assets_dir, docs_dir):
    verses = read_kjv(kjv_path)
    everything, problems = [], []
    for book in BOOKS:
        path = os.path.join(HERE, book.lower().replace(" ", "_") + ".tsv")
        if not os.path.exists(path):
            continue
        scenes, found = read_scenes(book, path, verses)
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
            out.write(f"{len(these)} pictures, one for each scene, covering every verse of {book}. For each: its verses\n")
            out.write("(King James Version), the name to save the picture as (or use Add on the app's Pictures page,\n")
            out.write("which names it for you), and the prompt to paste into ChatGPT. The prompts keep to what the\n")
            out.write("verses say, and end with the same style and framing, so the pictures look alike: warm and\n")
            out.write("cinematic, tall (9:16), wide, with the people in the lower half.\n")
            chapter = None
            for s in these:
                if s["chapter"] != chapter:
                    chapter = s["chapter"]
                    out.write(f"\n## Chapter {chapter}\n")
                label = f"{s['chapter']}:{s['first']}" + (f"–{s['last']}" if s["last"] != s["first"] else "")
                out.write(f"\n### {label} · {s['title']}\n\n")
                out.write(f"> {verses_of(s, verses)}\n\n")
                out.write(f"Save as `{name(book, s['chapter'], s['first'], s['last'])}`. Prompt:\n\n")
                out.write(f"{prompt(s)}\n")

    books = dict.fromkeys(s["book"] for s in everything)
    print(f"{len(everything)} scenes in {len(books)} book(s): " + ", ".join(
        f"{b} {sum(1 for s in everything if s['book'] == b)}" for b in books))


if __name__ == "__main__":
    main(*sys.argv[1:4])
