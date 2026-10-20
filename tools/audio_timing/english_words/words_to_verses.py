"""Verse timings from the words chain_align.py placed: each verse starts just before its first word and
ends just after its last (never after the next one starts). A chapter starts where its spoken heading
does ("Chapter two", a psalm's title); a book's first, at the file's start, with the book's title. One
json per book, like the Telugu results, for export.py.

A verse is unsure where some of its words weren't placed, one was placed as impossibly long (fitted to
speech not its own), its chapter was taken so (chain_align.py), or it takes far longer or shorter than
its text would at the chapter's pace.
usage: words_to_verses.py <state.json> <kjv.tsv> <members.json> <mp4_lengths.json> <out dir>
"""
import sys, os, json, math, statistics
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load

LEAD, TAIL = 0.05, 0.10      # a verse starts just before its first word and ends just after its last
ODD = 2.5                    # a verse taking this many times longer or shorter than its text would: unsure
LONG_WORD = 2.5              # as chain_align.py
SLOW = 0.12                  # seconds a letter, said slowly


def chapter_verses(ws, texts, begin, end, stretched=False):
    """[[start, end]] for each verse and the unsure ones (from 1), from the chapter's placed words
    [[start, end, word, verse index]]; None when a verse has none of its words placed."""
    first, last, placed, long = {}, {}, {}, set()
    for s, e, _, k in ws:
        first.setdefault(k, s)
        last[k] = e
        placed[k] = placed.get(k, 0) + 1
        if e - s > LONG_WORD: long.add(k)
    n = len(texts)
    if len(first) < n: return None
    # Music after the heading taken by one of verse 1's first words, or after a verse by its last word: the
    # verse starts and ends where those words would, said at a slow pace (a little early, a little late).
    v1 = [w for w in ws if w[3] == 0][:3]
    for j, (s, e, word, _) in enumerate(v1):
        if e - s > LONG_WORD: first[0] = max(begin, e - SLOW * sum(len(w[2]) for w in v1[:j + 1]))
    ends_of = {}
    for s, e, word, k in ws: ends_of[k] = (s, e, word)
    for k, (s, e, word) in ends_of.items():
        if e - s > LONG_WORD: last[k] = s + SLOW * len(word) + 0.3
    starts = [round(max(begin, first[k] - LEAD), 2) for k in range(n)]
    ends = [round(min(last[k] + TAIL, starts[k + 1] - 0.02) if k + 1 < n else min(last[k] + TAIL, end), 2) for k in range(n)]
    # Verses placed past the chapter's end (the aligner went wrong near it, and the next chapter's heading
    # was heard before them): spread over what's left of the chapter by their text's length, unsure.
    lost = next((k for k in range(n) if starts[k] >= end - 0.3 * (n - k)), None)
    if lost is not None:
        a = min(ends[lost - 1], end - 0.5 * (n - lost)) if lost else begin
        if lost: ends[lost - 1] = round(a, 2)
        sizes = [len(t) for t in texts[lost:]]
        at = a
        for k, size in enumerate(sizes, lost):
            step = (end - a) * size / sum(sizes)
            starts[k], ends[k] = round(at + 0.02, 2), round(at + step, 2)
            at += step
    # seconds per letter, for the chapter
    pace = statistics.median((last[k] - first[k]) / max(1, len(texts[k])) for k in range(n))
    unsure = []
    for k in range(n):
        expected = len(texts[k]) * pace
        took = last[k] - first[k]
        if stretched or k in long or (lost is not None and k >= lost - 1) or placed[k] < len(words(texts[k])) or \
                not expected / ODD <= took <= expected * ODD:
            unsure.append(k + 1)
    return [[s, e] for s, e in zip(starts, ends)], unsure


def spread(texts, begin, end):
    """A chapter the aligner couldn't place (chain_align.estimate): its verses over its span (its heading
    first) by their text's length, all unsure."""
    a = begin + min(3.0, 0.1 * (end - begin))
    total = sum(len(t) for t in texts)
    verses, at = [], a
    for t in texts:
        step = (end - a) * len(t) / total
        verses.append([round(at + 0.02, 2), round(at + step, 2)])
        at += step
    return verses, list(range(1, len(texts) + 1))


def main(state_path, kjv_tsv, members_path, lengths_path, out):
    state = json.load(open(state_path, encoding='utf-8'))
    bible = load(kjv_tsv)
    members = json.load(open(members_path, encoding='utf-8'))
    lengths = json.load(open(lengths_path, encoding='utf-8'))
    os.makedirs(out, exist_ok=True)
    for book, s in state.items():
        file = os.path.basename(members[str(s['n'])][1])
        duration = lengths[file]
        chapters = bible[book]
        if not s['done'] or len(s['chapters']) < len(chapters):
            print(f'{book}: not all aligned ({len(s["chapters"])} of {len(chapters)} chapters)')
            continue
        starts = {c: 0.0 if c == 1 else s['chapters'][str(c)]['start'] for c in chapters}
        result = {'book': book, 'file': file, 'duration': duration, 'chapters': {}}
        bad, unsure = [], 0
        for c, texts in chapters.items():
            # The last chapter ends with the file (to the hundredth below its length: never past it).
            end = starts[c + 1] if c + 1 in starts else math.floor(duration * 100) / 100
            ch = s['chapters'][str(c)]
            got = spread(texts, starts[c], end) if ch.get('estimated') else chapter_verses(ch['words'], texts, starts[c], end, ch.get('stretched', False))
            if got is None:
                bad.append(c)
                continue
            verses, odd = got
            result['chapters'][str(c)] = {'start': round(starts[c], 2), 'end': round(end, 2), 'verses': verses, 'metrics': {'unsure': odd}}
            unsure += len(odd)
        if bad:
            print(f'{book}: verses with no words placed in chapters {bad}')
            continue
        json.dump(result, open(os.path.join(out, f'{s["n"]:02d}.json'), 'w', encoding='utf-8'), separators=(',', ':'))
        print(f'{book}: {len(chapters)} chapters, {sum(map(len, chapters.values()))} verses, {unsure} unsure')


if __name__ == '__main__':
    main(*sys.argv[1:6])
