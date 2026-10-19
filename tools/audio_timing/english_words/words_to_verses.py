"""Verse timings from the words chain_align.py placed: each verse starts just before its first word and
ends just after its last (never after the next one starts). A chapter starts where its spoken heading
does ("Chapter two", a psalm's title); a book's first, at the file's start, with the book's title. One
json per book, like the Telugu results, for export.py.

A verse is unsure where some of its words weren't placed, or it takes far longer or shorter than its
text would at the chapter's pace.
usage: words_to_verses.py <state.json> <kjv.tsv> <members.json> <mp4_lengths.json> <out dir>
"""
import sys, os, json, statistics
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load

LEAD, TAIL = 0.05, 0.10      # a verse starts just before its first word and ends just after its last
ODD = 2.5                    # a verse taking this many times longer or shorter than its text would: unsure


def chapter_verses(ws, texts, begin, end):
    """[[start, end]] for each verse and the unsure ones (from 1), from the chapter's placed words
    [[start, end, word, verse index]]; None when a verse has none of its words placed."""
    first, last, placed = {}, {}, {}
    for s, e, _, k in ws:
        first.setdefault(k, s)
        last[k] = e
        placed[k] = placed.get(k, 0) + 1
    n = len(texts)
    if len(first) < n: return None
    starts = [round(max(begin, first[k] - LEAD), 2) for k in range(n)]
    ends = [round(min(last[k] + TAIL, starts[k + 1] - 0.02) if k + 1 < n else min(last[k] + TAIL, end), 2) for k in range(n)]
    # seconds per letter, for the chapter
    pace = statistics.median((last[k] - first[k]) / max(1, len(texts[k])) for k in range(n))
    unsure = []
    for k in range(n):
        expected = len(texts[k]) * pace
        took = last[k] - first[k]
        if placed[k] < len(words(texts[k])) or not expected / ODD <= took <= expected * ODD:
            unsure.append(k + 1)
    return [[s, e] for s, e in zip(starts, ends)], unsure


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
            end = starts[c + 1] if c + 1 in starts else duration
            got = chapter_verses(s['chapters'][str(c)]['words'], texts, starts[c], end)
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
