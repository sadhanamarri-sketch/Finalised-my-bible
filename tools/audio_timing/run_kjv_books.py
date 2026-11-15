"""Verse timings for a King James recording made one file a book, or a few books together: the 36 M4As
of the KJV audiobook with read-along text (New_english.zip, 15 Nov 2026). Each file opens with the
book's title, each chapter with "Chapter one" (each psalm with "Psalm one"), and its pauses are
digital silence.

The file's first chapter is found where its heading is heard (pocketsphinx's keyword spotting).
From there its chapters are matched to it a block at a time, about twenty minutes of them by their
text's length and the one after that, as timing.py's time_group does: espeak reads the headings and
verses at the narrator's pace and a little slower and faster, matched to the recording by sound, the
starts on pauses, the readings voting; then refine.py chooses among nearby pauses. A block's chapters
but the last are kept, and the next block starts at that last one's heading, as the match found it.
A chapter's last verse ends at the pause before the next heading, or before the next book's title,
or, for the last in a file, before its closing music.

usage: run_kjv_books.py <eng-kjv.osis.xml> <recordings folder> <results dir> <workers> [file name ...]
  Needs NumPy and pocketsphinx in the Python that runs it, ffmpeg and espeak-ng (as the rest of
  tools/audio_timing). Results are kept a file at a time, so a run that stops can be started again.
"""
import sys, os, re, json, html
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches, digital_silences, time_group, synth, SR, KEEP_GAP
from refine import refine, heading_factor

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
BLOCK = 10 * 60            # seconds of chapters matched together, by their text's length
HEADING_PAUSES = (2.5, 1.7)  # the narrator's pauses before "Chapter two" and after it, about


def kjv_text(osis_path):
    """From the KJV the app downloads: {(book, chapter): [verse text]}; {(book, chapter): the psalm's
    title} ("A Psalm of David, when he fled from Absalom his son.", said before its first verse);
    {book: its title} ("The Book of Ruth"). Psalm 119's letters ("BETH.") go before the verse they head."""
    raw = open(osis_path, encoding="utf-8").read()
    raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)

    def plain(t):
        return html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", t))).strip()

    text, psalm_titles, titles, pending = {}, {}, {}, None
    for m in re.finditer(r'<div type="book" osisID="([^"]+)"[^>]*>\s*<title type="main"[^>]*>(.*?)</title>', raw, re.S):
        if m.group(1) in OSIS:
            titles[BOOKS[OSIS.index(m.group(1))]] = plain(m.group(2))
    for m in re.finditer(r'<title type="psalm"[^>]*>(.*?)</title>'
                         r'|<verse osisID="([^"]+)" sID="([^"]+)"[^>]*/>(.*?)<verse eID="\3"\s*/>', raw, re.S):
        if m.group(1) is not None:
            pending = plain(m.group(1))
            continue
        book, chapter, verse = m.group(2).split()[0].split(".")
        if book not in OSIS:
            pending = None
            continue
        key, words = (BOOKS[OSIS.index(book)], int(chapter)), plain(m.group(4))
        if pending:
            if verse == "1": psalm_titles[key] = pending
            else: words = pending + " " + words
            pending = None
        text.setdefault(key, []).append(words)
    return text, psalm_titles, titles


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
    """[chapters]: (book, chapter, [verse text], what's said before its first verse), in the file's order."""
    if os.path.exists(out): return out, 'kept'
    x = decode(path)
    duration = len(x) / SR
    quiet = quiet_stretches(envelope(x))
    silences = digital_silences(x, -70.0, 0.05)
    n = len(chapters)
    letters = [sum(len(re.sub(r"\s+", "", t)) for t in ch[2]) for ch in chapters]
    rate = duration / max(1, sum(letters))       # seconds a letter, until blocks matched say better
    # The first block starts near where the first chapter's heading is heard.
    first = spot(x, 0, min(duration, 420), heading(chapters[0][0], chapters[0][1]))
    t0, free = (max(0.0, first - 1.0), 2.0) if first is not None else (0.0, min(duration, 420.0))
    found, blocks, i = {}, [], 0
    while i < n:
        j = i
        while j + 1 < n and rate * sum(letters[i:j + 2]) < BLOCK: j += 1
        last = min(j + 1, n - 1)
        final = last == n - 1
        expected = rate * sum(letters[i:last + 1])
        t1 = duration if final else min(duration, t0 + free + 1.25 * expected + 45.0)
        if t1 - t0 < 5: raise ValueError(f"no recording left for {chapters[i][0]} {chapters[i][1]}")
        q = [(qa, qb) for qa, qb in quiet if qa >= t0 - 0.5 and qb <= t1 + 0.5]
        texts = [[chapters[k][3]] + chapters[k][2] for k in range(i, last + 1)]
        groups = time_group(x[int(t0 * SR):int(t1 * SR)], texts, "en-us", q, offset=t0, begin_s=free, expected=expected,
                            heading_pauses=HEADING_PAUSES)
        upto = last if final else j
        for k in range(i, upto + 1):
            ga, _, verses, m = groups[k - i]
            found[k] = (ga, [v[0] for v in verses], m, len(blocks))
        m0 = groups[0][3]
        blocks.append({"from": f"{chapters[i][0]} {chapters[i][1]}", "to": f"{chapters[upto][0]} {chapters[upto][1]}",
                       "window": [round(t0, 2), round(t1, 2)], "cost": m0["cost"], "edge": m0["edge"]})
        if final: break
        anchor = groups[j + 1 - i][3]["heading"]
        rate = 0.5 * rate + 0.5 * (anchor - m0["heading"]) / max(1, sum(letters[i:j + 1]))
        t0, free = max(t0 + 1.0, anchor - 8.0), 16.0
        i = j + 1
    # How long the narrator takes over a heading and the pause after it, against espeak, so that verse 1
    # isn't put on a pause inside one ("Chapter … three", a psalm's title): refine's guard.
    syn, offsets, ssr = synth([ch[3] for ch in chapters], "en-us", 175)
    said_len = np.diff(np.append(offsets, len(syn) / ssr)) - KEEP_GAP
    gaps = [found[k][1][0] - found[k][2]["heading"] for k in range(n)]
    plain = [k for k in range(n) if k == 0 or chapters[k][0] == chapters[k - 1][0]]   # no book's title
    factor = heading_factor([gaps[k] for k in plain], [said_len[k] for k in plain], 2.5)
    result = {"file": os.path.basename(path), "duration": round(duration, 2), "heading_factor": round(factor, 3),
              "blocks": blocks, "chapters": {}}
    for k, (book, c, verses, _) in enumerate(chapters):
        a, raw_starts, m, block = found[k]
        b = found[k + 1][0] if k + 1 < n else duration
        q = [(qa, qb) for qa, qb in quiet if qa >= a - 0.5 and qb <= b + 0.5]
        unsure = set(u - 1 for u in m["unsure"])
        # the file's last verse ends before any closing music, not where the file does
        last_end = b if k + 1 < n else narration_end(silences, raw_starts[-1], duration)
        starts = refine(raw_starts, unsure, [len(re.sub(r"\s+", "", t)) for t in verses], q, last_end,
                        head=(m["heading"], factor * said_len[k]), votes=m["votes"])
        ends = []
        for v, st in enumerate(starts):
            if v + 1 < len(starts):
                nxt = starts[v + 1]
                before = [p_ for p_, e_ in q if e_ - 0.1 <= nxt + 0.001 and e_ >= nxt - 0.1]
                ends.append(before[0] + 0.05 if before and before[0] + 0.05 > st else nxt - 0.02)
                continue
            end = b
            if k + 1 < n:
                # to the pause before the next heading (or before the next book's title, said with it)
                p = pause_before(q, b + 0.15)
                if p: end = p[0] + 0.05
            else:
                end = narration_end(silences, st, duration)
                unsure.add(v)
            ends.append(max(end, st + 0.3))
        result["chapters"][f"{book} {c}"] = {
            "start": round(a, 2), "end": round(b, 2),
            "verses": [[round(float(st), 2), round(float(e), 2)] for st, e in zip(starts, ends)],
            "unsure": sorted(int(u) + 1 for u in unsure), "corr": m["corr"], "block": block}
    with open(out + ".tmp", "w") as f: json.dump(result, f)
    os.replace(out + ".tmp", out)
    fits = [ch["corr"] for ch in result["chapters"].values() if ch["corr"] is not None]
    return out, (f"{n} chapters in {len(blocks)} blocks, length fit median {np.median(fits):.3f}, "
                 f"under 0.8: {sum(1 for f in fits if f < 0.8)}, block cost {max(bl['cost'] for bl in blocks):.2f} at most")


def narration_end(silences, after, duration):
    """Where the reading ends, after [after] seconds: the narrator's pauses are digital silence, and
    closing music (some files end with about 20 s of it) has none, so at the pause before the first
    long stretch without one; with no music, at the silence the file ends in."""
    later = [d for d in silences if d[0] > after + 1.0]
    for d1, d2 in zip(later, later[1:]):
        if d2[0] - d1[1] >= MUSIC:
            return d1[0] + 0.05
    return later[-1][0] + 0.05 if later and later[-1][1] >= duration - 0.5 else duration


def said(book, chapter, psalm_title=None, title=None):
    """What's said before a chapter's first verse: "Chapter seven.", "Psalm three. A Psalm of David,
    when he fled from Absalom his son."; with another book's title before it, in a file of several."""
    words = ("Psalm " if book == "Psalms" else "Chapter ") + spoken(chapter)[0][0].upper() + spoken(chapter)[0][1:] + "."
    if psalm_title: words += " " + psalm_title
    return (title + ". " + words) if title else words


def main():
    osis, folder, results, workers = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
    only = sys.argv[5:]
    os.makedirs(results, exist_ok=True)
    text, psalm_titles, titles = kjv_text(osis)
    jobs = []
    for name in sorted(os.listdir(folder)):
        if only and name not in only: continue
        books = books_of(name)
        if not books: print("no book in", name, flush=True); continue
        chapters = [(b, c, text[(b, c)], said(b, c, psalm_titles.get((b, c)), titles.get(b) if b != books[0] and c == 1 else None))
                    for b in books for c in sorted(c for (bb, c) in text if bb == b)]
        out = os.path.join(results, re.sub(r"[^\w]+", "_", os.path.splitext(name)[0]).strip("_")[:80] + ".json")
        jobs.append((os.path.join(folder, name), chapters, out))
    jobs.sort(key=lambda j: -os.path.getsize(j[0]))
    with Pool(workers) as pool:
        for out, note in pool.imap_unordered(job, jobs):
            print(os.path.basename(out), note, flush=True)


if __name__ == "__main__":
    main()
