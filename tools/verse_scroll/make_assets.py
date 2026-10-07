"""Writes Verse Scroll's bundled data into the app: the Discover verse pool, and the scene catalog (each
scene's measured dimming and where its moving touches go) with the pictures themselves.

    python3 make_assets.py KJV_OSIS_XML CROSS_REFERENCES_TXT SCENES_DIR VEILS_JSON APP_ASSETS_DIR

KJV_OSIS_XML and CROSS_REFERENCES_TXT are the files the app itself downloads (see KjvImporter and
CrossReferenceImporter); SCENES_DIR comes from render_scenes.py and VEILS_JSON from fit_veils.py.
APP_ASSETS_DIR is app/src/main/assets/verse_scroll.
"""
import html
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_list import SCENES  # noqa: E402

osis_path, xref_path, scenes_dir, veils_path, out_dir = sys.argv[1:6]

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
        "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
        "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
        "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
        "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"]
APP = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
       "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
       "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
       "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
       "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
       "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
       "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians",
       "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter",
       "1 John", "2 John", "3 John", "Jude", "Revelation"]
NAME = dict(zip(OSIS, APP))

# ---- the KJV's text, for word counts ----
raw = open(osis_path, encoding='utf-8').read()
raw = re.sub(r'<note\b.*?</note>', '', raw, flags=re.S)
texts = {}
for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
    body = re.sub(r'<[^>]+>', '', m.group(3))
    texts[m.group(1)] = html.unescape(re.sub(r'\s+', ' ', body)).strip()

# ---- how often, and how strongly, other verses point at each verse ----
inbound, inbound_votes = {}, {}
ref = re.compile(r'^(\w+)\.(\d+)\.(\d+)$')
with open(xref_path, encoding='utf-8') as f:
    next(f)
    for line in f:
        parts = line.rstrip('\n').split('\t')
        if len(parts) < 3:
            continue
        src, dst = parts[0].strip(), parts[1].strip().split('-')[0]
        if not ref.match(src) or not ref.match(dst):
            continue
        try:
            votes = int(parts[2])
        except ValueError:
            votes = 0
        inbound[dst] = inbound.get(dst, 0) + 1
        if votes > 0:
            inbound_votes[dst] = inbound_votes.get(dst, 0) + votes


def words(k):
    return len(texts[k].replace('¶', '').split())


def score(k):
    return inbound_votes.get(k, 0) + 2 * inbound.get(k, 0)


# Discover: every verse other verses link to, scored by how widely it's referenced (votes pointing at it + 2 per
# link). The best known are the familiar tier, dealt two cards in three; the rest come up the other third, the
# better linked sooner. Long verses are in (the card cuts them short with "…" when they run out of room); short
# ones only when they're a whole sentence ("Jesus wept."), so lists of names and other fragments stay out.
FAMILIAR = 2000


def whole_sentence(k):
    return re.search(r'[.?!][’”)\]]*$', texts[k].replace('¶', '').strip()) is not None


cands = [k for k in inbound if k.split('.')[0] in NAME and k in texts and (words(k) >= 6 or whole_sentence(k))]
cands.sort(key=lambda k: (-score(k), k))
rows = [(k, score(k) ** 0.6, 1 if i < FAMILIAR else 2) for i, k in enumerate(cands)]
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, 'discover.tsv'), 'w', encoding='utf-8') as f:
    f.write('# book\tchapter\tverse\tweight\ttier (1 familiar, 2 the rest)  '
            '(Verse Scroll Discover pool, see tools/verse_scroll/make_assets.py)\n')
    for k, w, tier in rows:
        b, c, v = k.split('.')
        f.write(f'{NAME[b]}\t{c}\t{v}\t{w:.2f}\t{tier}\n')

# ---- scenes: the dimming each needs, where its moving touches go, and the pictures ----
veils = json.load(open(veils_path))
meta = json.load(open(os.path.join(scenes_dir, 'meta.json')))
catalog = []
os.makedirs(os.path.join(out_dir, 'scenes'), exist_ok=True)
for kind, pal, seed in SCENES:
    sid = f'{kind}-{pal}-{seed}'
    entry = {'id': sid, 'kd': veils[sid]['kd'], 'kl': veils[sid]['kl']}
    entry.update({k: v for k, v in meta[sid].items() if k != 'layout' and v is not None})
    catalog.append(entry)
    shutil.copyfile(os.path.join(scenes_dir, sid + '.webp'), os.path.join(out_dir, 'scenes', sid + '.webp'))
json.dump({'scenes': catalog}, open(os.path.join(out_dir, 'scenes.json'), 'w'), separators=(',', ':'))
print(len(rows), 'Discover verses (', FAMILIAR, 'familiar );', len(catalog), 'scenes')
