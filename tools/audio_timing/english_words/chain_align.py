"""Every word of a dramatized KJV recording placed in it, one file per book, by the Montreal Forced Aligner
with its pretrained English acoustic model: a book a stretch at a time, rounds of all the books at once.

Each stretch starts exactly where the last one's words ended (at first, the book's start) and holds whole
chapters: before each chapter's verses an unknown word (zzhead) to take its spoken heading ("Chapter
two", a psalm's title), and after the last chapter another (zzpad) to take whatever of the next chapter
the stretch reaches into. The aligner takes unknown speech after a stretch's words, however long, but
not before them (it fits the words in from the stretch's first moment instead), so a stretch's start must
be known: hence the rounds. A book's first stretch says its title in words, else its first verse's words
can be fitted to the title's ("Paul" in "The Epistle of Paul to Titus"). A stretch whose last words look
squeezed in (not enough recording for them) is tried again longer.

Words per chapter go to <work>/state.json, with where each book has got to; rounds go on from there.
usage: chain_align.py <kjv.tsv> <flac dir: NN.flac> <dictionary> <acoustic model> <work dir> [workers]
"""
import sys, os, json, difflib, subprocess, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load, TITLES

TARGET = 600.0             # seconds of chapters a stretch aims at
SLACK, EXTRA = 1.25, 15.0  # its end: the chapters' expected length times SLACK, plus EXTRA seconds
MIN_PAD = 2.0              # less unknown speech after the words than this, short of the file's end: too short


def duration(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def textgrid(path, end, tier, a, b, text):
    items = [(0.0, a, '')] if a > 1e-3 else []
    items.append((a, b, text))
    if end - b > 1e-3: items.append((b, end, ''))
    lines = ['File type = "ooTextFile"', 'Object class = "TextGrid"', '', 'xmin = 0', f'xmax = {end:.3f}', 'tiers? <exists>', 'size = 1',
             'item []:', '    item [1]:', '        class = "IntervalTier"', f'        name = "{tier}"', '        xmin = 0',
             f'        xmax = {end:.3f}', f'        intervals: size = {len(items)}']
    for i, (x, y, t) in enumerate(items, 1):
        lines += [f'        intervals [{i}]:', f'            xmin = {x:.3f}', f'            xmax = {y:.3f}', f'            text = "{t}"']
    open(path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')


def entries(path):
    tiers = json.load(open(path, encoding='utf-8'))['tiers']
    t = tiers.get('words') or next(v for k, v in tiers.items() if 'word' in k.lower())
    return [(e[0], e[1], e[2]) for e in t['entries'] if e[2] not in ('', '<eps>', 'sil', 'sp')]


def plan(book, s, bible):
    """The next stretch of [book] from its state [s]: (start, end, [(token, chapter, verse index)])."""
    chapters = bible[book]
    last = max(chapters)
    chosen, est = [], 0.0
    for c in range(s['next'], last + 1):
        chosen.append(c)
        est += s['pace'] * sum(len(v) for v in chapters[c])
        if est >= TARGET: break
    grow = 1.5 ** s['tries']
    end = s['pos'] + est * SLACK * grow + EXTRA * grow
    if chosen[-1] == last or end > s['duration'] - 30: end = s['duration']
    tokens = []
    if s['next'] == 1:
        tokens.append(('zzintro', 1, -1))
        tokens += [(w, 1, -1) for w in words(TITLES[book])]
    for c in chosen:
        tokens.append(('zzhead', c, -1))
        for k, v in enumerate(chapters[c]):
            tokens += [(w, c, k) for w in words(v)]
    tokens.append(('zzpad', None, -1))
    return s['pos'], end, tokens


def take(s, start, end, tokens, aligned, verses):
    """Takes a stretch's aligned words into the state ([verses]: the book's, {chapter: [text]}); False
    when the stretch looks too short."""
    got = entries(aligned)
    sm = difflib.SequenceMatcher(None, [t[0] for t in tokens], [g[2] for g in got], autojunk=False)
    at = {}
    for i, j, size in sm.get_matching_blocks():
        for d in range(size): at[i + d] = got[j + d]
    pad = at.get(len(tokens) - 1)
    real = [i for i, t in enumerate(tokens) if t[1] is not None and not t[0].startswith('zz')]
    last_i = real[-1]
    if last_i not in at:
        return False
    last_end = at[last_i][1]
    pad_len = (pad[1] - pad[0]) if pad else 0.0
    # Not enough recording for the words: the last ones squeezed in at the stretch's end.
    tail = [at[i] for i in real[-30:] if i in at]
    squeezed = sum(1 for a, b, _ in tail if b - a <= 0.035)
    if end < s['duration'] - 1 and (pad_len < MIN_PAD or squeezed > 6):
        return False
    chapters = sorted({t[1] for t in tokens if t[1] is not None})
    spoken = 0.0
    for c in chapters:
        idx = [i for i, t in enumerate(tokens) if t[1] == c]
        head = next((i for i in idx if tokens[i][0] == 'zzhead'), None)
        ws = [[round(at[i][0], 3), round(at[i][1], 3), tokens[i][0], tokens[i][2]] for i in idx if i in at and tokens[i][2] >= 0]
        if c == 1:
            begin = 0.0
        elif head in at:
            begin = at[head][0]
        else:
            begin = ws[0][0] - 1.0 if ws else start
        s['chapters'][str(c)] = {'start': round(begin, 3), 'words': ws, 'expected': sum(1 for i in idx if tokens[i][2] >= 0)}
        if ws: spoken += ws[-1][1] - begin
    # On from the pause after the last word: before the next chapter's heading.
    gap = (pad[0] - last_end) if pad and pad[0] > last_end else 0.0
    s['pos'] = round(last_end + 0.6 * gap, 3)
    s['next'] = chapters[-1] + 1
    s['done'] = s['next'] > max(verses)
    s['tries'] = 0
    chars = sum(len(v) for c in chapters for v in verses[c])
    if spoken > 0 and chars: s['pace'] = 0.5 * s['pace'] + 0.5 * spoken / chars
    return True


def aligner():
    """The command to run the aligner with: mfa_align.py, by the Python the aligner's own `mfa` runs with."""
    mfa = shutil.which('mfa')
    python = open(mfa, encoding='utf-8').readline()[2:].strip()
    return [python, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mfa_align.py')]


def main(kjv_tsv, flac_dir, dictionary, model, work, workers='4'):
    bible = load(kjv_tsv)
    books = list(bible)
    os.makedirs(work, exist_ok=True)
    state_path = os.path.join(work, 'state.json')
    if os.path.exists(state_path):
        state = json.load(open(state_path, encoding='utf-8'))
    else:
        state = {}
        for n, book in enumerate(books, 1):
            dur = duration(os.path.join(flac_dir, f'{n:02d}.flac'))
            chars = sum(len(v) for vs in bible[book].values() for v in vs)
            state[book] = {'n': n, 'duration': dur, 'pace': dur / chars, 'pos': 0.0, 'next': 1, 'done': False, 'tries': 0, 'chapters': {}}
    rnd = max([int(d[6:9]) for d in os.listdir(work) if d.startswith('round_') and d[6:9].isdigit()] + [0])
    # ONLY=Titus,Jonah: just those books (to try it out).
    only = [b for b in os.environ.get('ONLY', '').split(',') if b]
    while True:
        todo = [b for b in books if not state[b]['done'] and state[b]['tries'] < 5 and (not only or b in only)]
        if not todo: break
        rnd += 1
        corpus, out = os.path.join(work, f'round_{rnd:03d}'), os.path.join(work, f'aligned_{rnd:03d}')
        shutil.rmtree(corpus, ignore_errors=True)
        os.makedirs(corpus)
        stretches = {}
        for b in todo:
            s = state[b]
            a, e, tokens = plan(b, s, bible)
            stretches[b] = (a, e, tokens)
            textgrid(os.path.join(corpus, f'{s["n"]:02d}.TextGrid'), s['duration'], f'b{s["n"]:02d}', a, e, ' '.join(t[0] for t in tokens))
        for beam, retry in ((20, 100), (100, 400)):
            missing = [b for b in todo if not os.path.exists(os.path.join(out, f'{state[b]["n"]:02d}.json'))]
            if not missing: break
            # Nothing at all: the aligner stopped (its log says why), not a stretch it couldn't fit.
            if beam > 20 and len(missing) == len(todo):
                sys.exit(f'round {rnd}: the aligner gave no alignments at all; see {work}/round_{rnd:03d}_b20.log')
            run = corpus if len(missing) == len(todo) else corpus + f'_beam{beam}'
            if run != corpus:
                shutil.rmtree(run, ignore_errors=True)
                os.makedirs(run)
                for b in missing: shutil.copy(os.path.join(corpus, f'{state[b]["n"]:02d}.TextGrid'), run)
            subprocess.run(aligner() + [run, dictionary, model, out, '--audio_directory', flac_dir, '-j', workers, '--clean',
                                        '--output_format', 'json', '--beam', str(beam), '--retry_beam', str(retry)],
                           stdout=open(os.path.join(work, f'round_{rnd:03d}_b{beam}.log'), 'w'), stderr=subprocess.STDOUT)
        # The aligner's working files for the round (its features), large and done with.
        root = os.environ.get('MFA_ROOT_DIR', os.path.expanduser('~/Documents/MFA'))
        for d in (os.path.basename(corpus), os.path.basename(corpus) + '_beam100'):
            shutil.rmtree(os.path.join(root, d), ignore_errors=True)
        moved = []
        for b in todo:
            s = state[b]
            path = os.path.join(out, f'{s["n"]:02d}.json')
            a, e, tokens = stretches[b]
            if os.path.exists(path) and take(s, a, e, tokens, path, bible[b]):
                moved.append(f'{b} to {s["next"] - 1}')
            else:
                s['tries'] += 1
                moved.append(f'{b} AGAIN ({s["tries"]})')
        json.dump(state, open(state_path + '.tmp', 'w', encoding='utf-8'), separators=(',', ':'))
        os.replace(state_path + '.tmp', state_path)
        done = sum(1 for b in books if state[b]['done'])
        print(f'round {rnd}: {len(todo)} stretches; {done} of {len(books)} books done; ' + ', '.join(moved), flush=True)
    stuck = [b for b in books if not state[b]['done'] and (not only or b in only)]
    print('all done' + (f'; stuck: {stuck}' if stuck else ''), flush=True)


if __name__ == '__main__':
    main(*sys.argv[1:])
