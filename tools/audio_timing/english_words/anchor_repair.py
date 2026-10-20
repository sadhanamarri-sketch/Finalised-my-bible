"""Chapters chain_align.py placed wrongly, one after another (a mistake carried on: their sampled verses'
words aren't heard where they're placed), timed again each on its own: each chapter's heading found by
keyword spotting near where its share of the span puts it (find_heading.py), then each chapter aligned
alone from its heading to the next one's, so a chapter the aligner gets wrong can't put the next one out.
A chapter still placed wrongly (a word stretched, words not placed) has its verses estimated.

usage: anchor_repair.py <state.json> <kjv.tsv> <flac dir> <dictionary> <acoustic model> <work dir>
                        [--beam=20,100] <Book:first-last> [<Book:first-last> ...]
The state is updated in place. Needs $KWS_PYTHON as chain_align.py does. --beam sets the aligner's
beam and retry beam: wider (100,400) is slower but places chapters the usual one gives up on.
"""
import sys, os, json, difflib, subprocess, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load, heading, before_verse
from chain_align import aligner, find_heading, LONG_WORD

WINDOW = 0.25   # a heading is looked for this share of a chapter's length either side of where it's expected


def entries_by_time(path):
    """The aligner's words [(start, end, word)], possessives rejoined (as chain_align.entries)."""
    tiers = json.load(open(path, encoding='utf-8'))['tiers']
    t = tiers.get('words') or next(v for k, v in tiers.items() if 'word' in k.lower())
    out = []
    for a, b, w in t['entries']:
        if w in ('', '<eps>', 'sil', 'sp'): continue
        if w.startswith("'") and out: out[-1] = (out[-1][0], b, out[-1][2] + w)
        else: out.append((a, b, w))
    return out


def anchors(book, s, chapters, first, last, t0, t1, flac_dir):
    """Where each chapter first..last's heading is, by keyword spotting near its share of [t0, t1]; one
    not heard (or heard out of order) is put by its share between those that are."""
    sizes = {c: sum(len(v) for v in chapters[c]) for c in range(first, last + 1)}
    total = sum(sizes.values())
    expected, at = {}, t0
    for c in range(first, last + 1):
        expected[c] = at
        at += (t1 - t0) * sizes[c] / total
    found = {}
    prev = t0 - 1.0
    for c in range(first, last + 1):
        width = max(30.0, WINDOW * (t1 - t0) * sizes[c] / total)
        a, b = max(prev + 5.0, expected[c] - width), min(t1 - 5.0, expected[c] + width)
        if c == first: a, b = t0 - 1.0, min(t1 - 5.0, t0 + 60.0)
        t = find_heading(book, s, c, a, b, flac_dir) if b > a else None
        if t is not None and t > prev + 5.0:
            found[c] = t
            prev = t
    # The rest by their share between the ones found (the span's start and end standing in at its ends).
    points = sorted({first: t0, last + 1: t1, **found}.items())
    out = {}
    for c in range(first, last + 1):
        (k0, a), (k1, b) = max(p for p in points if p[0] <= c), min(p for p in points if p[0] > c)
        out[c] = a + (b - a) * sum(sizes[k] for k in range(k0, c)) / max(1, sum(sizes[k] for k in range(k0, k1)))
    return out, found


def main(state_path, kjv_tsv, flac_dir, dictionary, model, work, *runs):
    beam, retry_beam = '20', '100'
    for r in runs:
        if r.startswith('--beam='): beam, retry_beam = r[len('--beam='):].split(',')
    runs = [r for r in runs if not r.startswith('--beam=')]
    state = json.load(open(state_path, encoding='utf-8'))
    bible = load(kjv_tsv)
    os.makedirs(work, exist_ok=True)
    corpus, out = os.path.join(work, 'corpus'), os.path.join(work, 'aligned')
    shutil.rmtree(corpus, ignore_errors=True)
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(corpus)
    plans = {}
    for run in runs:
        book, span = run.split(':')
        first, last = map(int, span.split('-'))
        s, chapters = state[book], bible[book]
        prev = s['chapters'].get(str(first - 1))
        t0 = (prev['words'][-1][1] if prev and prev['words'] else prev['end'] if prev else 0.0) + 0.3
        nxt = s['chapters'].get(str(last + 1))
        t1 = nxt['start'] if nxt else s['duration']
        heads, heard = anchors(book, s, chapters, first, last, t0, t1, flac_dir)
        print(f'{book} {first}-{last}: headings heard {sorted(heard)} of {last - first + 1}', flush=True)
        items = []
        # Each chapter from just before its heading to just before the next one's (no overlap: the aligner
        # skips a file whose stretches do).
        starts = {c: max(t0 - 0.3, heads[c] - 0.5) for c in range(first, last + 1)}
        for c in range(first, last + 1):
            a = starts[c]
            b = starts[c + 1] if c < last else t1
            tokens = [('zzhead', -1)] + [(w, -2) for w in heading(book, c, max(chapters))]
            for k, v in enumerate(chapters[c]):
                tokens += [(w, -2) for w in before_verse(book, c, k + 1)] + [(w, k) for w in words(v)]
            tokens.append(('zzpad', -1))
            items.append((c, a, b, tokens))
        plans[book] = (s, items)
        write_textgrid(os.path.join(corpus, f'{s["n"]:02d}.TextGrid'), s['duration'], f'b{s["n"]:02d}', items)
    subprocess.run(aligner() + [corpus, dictionary, model, out, '--audio_directory', flac_dir, '-j', '4', '--clean',
                                '--output_format', 'json', '--beam', beam, '--retry_beam', retry_beam],
                   stdout=open(os.path.join(work, 'align.log'), 'w'), stderr=subprocess.STDOUT)
    for book, (s, items) in plans.items():
        path = os.path.join(out, f'{s["n"]:02d}.json')
        got = entries_by_time(path) if os.path.exists(path) else []
        report = []
        for c, a, b, tokens in items:
            inside = [g for g in got if a - 0.01 <= g[0] and g[1] <= b + 0.01]
            sm = difflib.SequenceMatcher(None, [t[0] for t in tokens], [g[2] for g in inside], autojunk=False)
            at = {}
            for i, j, size in sm.get_matching_blocks():
                for d in range(size): at[i + d] = inside[j + d]
            verse_words = [i for i, t in enumerate(tokens) if t[1] >= 0]
            music = set(verse_words[:3]) | {verse_words[-1]}
            first_head = next((i for i, t in enumerate(tokens) if t[1] == -2 and i in at), None)
            begin = (at[first_head][0] - 0.1) if first_head is not None else a
            entry = {'start': round(max(a, begin) if c > 1 else 0.0, 3), 'expected': len(verse_words), 'repaired': True}
            if any(i not in at for i in verse_words):
                entry.update({'words': [], 'end': round(b, 3), 'estimated': True})
                report.append(f'{c} estimated')
            else:
                entry['words'] = [[round(at[i][0], 3), round(at[i][1], 3), tokens[i][0], tokens[i][1]] for i in verse_words]
                if any(at[i][1] - at[i][0] > LONG_WORD for i in verse_words if i not in music):
                    entry['stretched'] = True
                    report.append(f'{c} stretched')
            s['chapters'][str(c)] = entry
        print(f'{book}: ' + (', '.join(report) if report else 'all placed'), flush=True)
    json.dump(state, open(state_path + '.tmp', 'w', encoding='utf-8'), separators=(',', ':'))
    os.replace(state_path + '.tmp', state_path)


def write_textgrid(path, end, tier, items):
    """A TextGrid with one interval a chapter: [(chapter, from, to, tokens)]."""
    spans, at = [], 0.0
    for _, a, b, tokens in items:
        if a - at > 1e-3: spans.append((at, a, ''))
        spans.append((a, b, ' '.join(t[0] for t in tokens)))
        at = b
    if end - at > 1e-3: spans.append((at, end, ''))
    lines = ['File type = "ooTextFile"', 'Object class = "TextGrid"', '', 'xmin = 0', f'xmax = {end:.3f}', 'tiers? <exists>', 'size = 1',
             'item []:', '    item [1]:', '        class = "IntervalTier"', f'        name = "{tier}"', '        xmin = 0',
             f'        xmax = {end:.3f}', f'        intervals: size = {len(spans)}']
    for i, (x, y, t) in enumerate(spans, 1):
        lines += [f'        intervals [{i}]:', f'            xmin = {x:.3f}', f'            xmax = {y:.3f}', f'            text = "{t}"']
    open(path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main(*sys.argv[1:])
