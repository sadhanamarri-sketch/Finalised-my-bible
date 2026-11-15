"""Verse timings for a King James recording made one file a book, or a few books together: the 36 M4As
of the KJV audiobook with read-along text (New_english.zip, 15 Nov 2026). Each file opens with the
book's title, each chapter with "Chapter one" (each psalm with "Psalm one"), and its pauses are
digital silence.

Each chapter is found where its heading is said: pocketsphinx's keyword spotting listens for "chapter
seven" near where the chapter before should end, by its text's length at the narrator's pace (taken
from the chapters found so far). Then each chapter is timed as timing.py does (espeak matched to it by
sound at three paces, starts on pauses, the readings voting) and refine.py chooses among nearby pauses.
A chapter's last verse ends at the pause before the next heading, or before the next book's title, or,
for the last in a file, at the first long pause after it should have ended (what's said after the
last verse isn't the text).

usage: run_kjv_books.py <eng-kjv.osis.xml> <recordings folder> <results dir> <workers> [file name ...]
  Needs NumPy and pocketsphinx in the Python that runs it, ffmpeg and espeak-ng (as the rest of
  tools/audio_timing). Results are kept a file at a time, so a run that stops can be started again.
"""
import sys, os, re, json, html
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches, digital_silences, time_chapter, SR
from refine import refine

BOOKS = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
         "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job", "Psalms", "Proverbs",
         "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
         "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark",
         "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians",
         "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
         "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"]
OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra",
        "Neh", "Esth", "Job", "Ps", "Prov", "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos", "Obad",
        "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor",
        "2Cor", "Gal", "Eph", "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas", "1Pet", "2Pet",
        "1John", "2John", "3John", "Jude", "Rev"]
# The files with several books in them, by words in their names.
TOGETHER = [("MINOR PROPHETS", "Hosea", "Malachi"), ("EPISTLES OF PAUL", "Romans", "Hebrews"), ("JAMES - JUDE", "James", "Jude")]
MUSIC = 8.0                # seconds without the narrator's digital silence: closing music


def kjv_text(osis_path):
    """{(book, chapter): [verse text]} from the KJV the app downloads."""
    raw = open(osis_path, encoding="utf-8").read()
    raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
    text = {}
    for m in re.finditer(r'<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\2"\s*/>', raw, re.S):
        words = html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", m.group(3)))).strip()
        book, chapter, verse = m.group(1).split()[0].split(".")
        if book in OSIS:
            text.setdefault((BOOKS[OSIS.index(book)], int(chapter)), []).append(words)
    return text


def books_of(name):
    """The books a file holds, from its name: "21 - THE MINOR PROPHETS (KJV) Hosea - Malachi …" all
    twelve, "12 - 1 KINGS 📜 King Solomon's Rise And Fall …" 1 Kings."""
    upper = name.upper()
    for words, first, last in TOGETHER:
        if words in upper:
            return BOOKS[BOOKS.index(first):BOOKS.index(last) + 1]
    found = [b for b in BOOKS if re.search(r"(?<![\w])" + re.escape(b.upper()) + r"(?![\w])", upper)]
    # The longest name: "Lamentations" before "Jeremiah" (in "Jeremiah's Poetic Mourning").
    return [max(found, key=len)] if found else []


ONES = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()


def spoken(n):
    """119 as said: ["one hundred and nineteen", "one hundred nineteen"]."""
    def under_100(k):
        return ONES[k] if k < 20 else TENS[k // 10] + ("" if k % 10 == 0 else " " + ONES[k % 10])
    if n < 100:
        return [under_100(n)]
    rest = n % 100
    if rest == 0:
        return [ONES[n // 100] + " hundred"]
    return [f"{ONES[n // 100]} hundred and {under_100(rest)}", f"{ONES[n // 100]} hundred {under_100(rest)}"]


def heading(book, chapter):
    word = "psalm" if book == "Psalms" else "chapter"
    return [f"{word} {s}" for s in spoken(chapter)]


def spot(x, a, b, phrases):
    """Where in x (seconds a to b) one of [phrases] is heard best, by pocketsphinx's keyword spotting."""
    from pocketsphinx import Decoder
    a, b = max(0.0, a), min(len(x) / SR, b)
    if b - a < 1: return None
    pcm = (np.clip(x[int(a * SR):int(b * SR)], -1, 1) * 32767).astype(np.int16).tobytes()
    best = None
    for phrase in phrases:
        decoder = Decoder(samprate=16000, keyphrase=phrase, kws_threshold=1e-30)
        decoder.start_utt(); decoder.process_raw(pcm, full_utt=True); decoder.end_utt()
        for seg in decoder.seg() or []:
            if best is None or seg.prob > best[1]: best = (a + seg.start_frame / 100, seg.prob)
    return best[0] if best else None


def pause_before(quiet, t, within=0.6):
    """The quiet stretch that ends where speech starts at t (or nearest before it)."""
    near = [(a, b) for a, b in quiet if b <= t + within and b >= t - within]
    return near[-1] if near else None


def job(args):
    try:
        return timed(*args)
    except Exception as e:  # one file's trouble doesn't stop the others; it's run again next time
        return args[2], f"failed: {type(e).__name__}: {e}"


def timed(path, chapters, out):
    if os.path.exists(out): return out, 'kept'
    x = decode(path)
    duration = len(x) / SR
    quiet = quiet_stretches(envelope(x))
    silences = digital_silences(x, -70.0, 0.05)
    letters = [sum(len(re.sub(r"\s+", "", t)) for t in verses) for _, _, verses in chapters]
    rate = duration / max(1, sum(letters))       # seconds a letter, until chapters found say better
    found, rates, heads, how = [], [], [], []
    for k, (book, c, verses) in enumerate(chapters):
        if k == 0:
            t, window = spot(x, 0, min(duration, 420), heading(book, c)), "start"
        else:
            expected = heads[-1] + rate * letters[k - 1] + (6.0 if book != chapters[k - 1][0] else 0.0)
            spread = max(25.0, 0.2 * rate * letters[k - 1])
            t = spot(x, expected - spread, expected + spread, heading(book, c))
            window = "near"
            if t is None:
                t = spot(x, expected - 3 * spread, expected + 3 * spread, heading(book, c)); window = "wide"
            if t is None:
                t, window = expected, "guessed"
            if t <= heads[-1] + 2:   # heard too early: at least past the last heading
                t, window = expected, "guessed"
            rates.append((t - heads[-1]) / max(1, letters[k - 1]))
            rate = float(np.median(rates[-12:]))
        heads.append(t if t is not None else 0.0)
        how.append(window)
    result = {"file": os.path.basename(path), "duration": round(duration, 2), "chapters": {}}
    for k, (book, c, verses) in enumerate(chapters):
        a = max(0.0, heads[k] - 0.2)
        b = heads[k + 1] - 0.2 if k + 1 < len(chapters) else duration
        q = [(s, e) for s, e in quiet if s >= a - 0.5 and e <= b + 0.5]
        said = ("Psalm " if book == "Psalms" else "Chapter ") + spoken(c)[0] + "."
        starts_ends, m = time_chapter(x[int(a * SR):int(b * SR)], [said] + verses, "en-us", q, offset=a)
        unsure = set(i - 1 for i in m["unsure"])
        starts = refine([s for s, _ in starts_ends], unsure, [len(re.sub(r"\s+", "", t)) for t in verses], q, b)
        ends = []
        for i, s in enumerate(starts):
            if i + 1 < len(starts):
                nxt = starts[i + 1]
                before = [p for p, e in q if e - 0.1 <= nxt + 0.001 and e >= nxt - 0.1]
                ends.append(before[0] + 0.05 if before and before[0] + 0.05 > s else nxt - 0.02)
                continue
            # The last verse: to the pause before what comes next.
            end = b
            if k + 1 < len(chapters):
                p = pause_before(q, heads[k + 1])
                if p:
                    end = p[0] + 0.05
                    if chapters[k + 1][0] != book:
                        # the next book's title, said before its "Chapter one": the pause before that
                        title = [(pa, pb) for pa, pb in q if pb < p[0] - 0.05 and pb > p[0] - 6.0]
                        if title: end = title[-1][0] + 0.05
            else:
                # The file's last verse: the narrator's pauses are digital silence, and closing music
                # (some files end with about 20 s of it) has none, so the verse ends at the pause before
                # the first long stretch without one; with no music, at the silence the file ends in.
                after = [d for d in silences if d[0] > s + 1.0]
                end = after[-1][0] + 0.05 if after and after[-1][1] >= duration - 0.5 else duration
                for d1, d2 in zip(after, after[1:]):
                    if d2[0] - d1[1] >= MUSIC:
                        end = d1[0] + 0.05
                        break
                unsure.add(i)
            ends.append(max(end, s + 0.3))
        result["chapters"][f"{book} {c}"] = {
            "start": round(a, 2), "end": round(b, 2),
            "verses": [[round(float(s), 2), round(float(e), 2)] for s, e in zip(starts, ends)],
            "unsure": sorted(int(i) + 1 for i in unsure), "heading": how[k], "corr": m["corr"]}
    with open(out + ".tmp", "w") as f: json.dump(result, f)
    os.replace(out + ".tmp", out)
    return out, f"{len(chapters)} chapters, headings " + ", ".join(f"{h} {how.count(h)}" for h in ("start", "near", "wide", "guessed") if how.count(h))


def main():
    osis, folder, results, workers = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
    only = sys.argv[5:]
    os.makedirs(results, exist_ok=True)
    text = kjv_text(osis)
    jobs = []
    for name in sorted(os.listdir(folder)):
        if only and name not in only: continue
        books = books_of(name)
        if not books: print("no book in", name, flush=True); continue
        chapters = [(b, c, text[(b, c)]) for b in books for c in sorted(c for (bb, c) in text if bb == b)]
        out = os.path.join(results, re.sub(r"[^\w]+", "_", os.path.splitext(name)[0]).strip("_")[:80] + ".json")
        jobs.append((os.path.join(folder, name), chapters, out))
    jobs.sort(key=lambda j: -os.path.getsize(j[0]))
    with Pool(workers) as pool:
        for out, note in pool.imap_unordered(job, jobs):
            print(os.path.basename(out), note, flush=True)


if __name__ == "__main__":
    main()
