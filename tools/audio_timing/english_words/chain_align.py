"""Every word of a dramatized KJV recording placed in it, one file per book, by the Montreal Forced Aligner
with its pretrained English acoustic model: a book a stretch at a time, rounds of all the books at once.

Each stretch holds whole chapters, each with what's said before its verses ("Chapter two"; in Psalms the
psalm's number and title; a book's title before its first), and after the last chapter an unknown word
(zzpad) to take whatever of the next chapter the stretch reaches into. The aligner takes unknown speech
after a stretch's words, but not before them (it fits the words in from the stretch's first moment
instead), so a stretch must start where its words do: just after the last word the stretch before
placed (hence the rounds), never further on, though the aligner may call the speech that follows
silence (a minute and a half of it, once). A book's first stretch says its title in words, else its
first verse's words can be fitted to the title's ("Paul" in "The Epistle of Paul to Titus").

A stretch whose last words look squeezed in (not enough recording for them) is tried again longer. A
chapter with a word placed as impossibly long has been fitted to speech not its own, from there on, so
it isn't taken nor anything after it: it's tried again on its own from the end of the one before, and
taken as it comes then, its verses marked unsure.

Words per chapter go to <work>/state.json, with where each book has got to; rounds go on from there.
usage: chain_align.py <kjv.tsv> <flac dir: NN.flac> <dictionary> <acoustic model> <work dir> [workers]
"""
import sys, os, json, difflib, subprocess, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load, title, heading, before_verse, number

TARGET = 600.0             # seconds of chapters a stretch aims at
SLACK, EXTRA = 1.25, 15.0  # its end: the chapters' expected length times SLACK, plus EXTRA seconds
MIN_PAD = 2.0              # less unknown speech after the words than this, short of the file's end: too short
LEAD_IN = 1.0              # the next stretch starts at most this long after the last word placed
LONG_WORD = 2.5            # a word placed as longer than this has been stretched over speech not its own


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
    """The next stretch of [book] from its state [s]: (start, end, [(token, chapter, verse index)]), the
    verse index -1 for what's said before the book's chapters and -2 for what's said before a verse."""
    chapters = bible[book]
    last = max(chapters)
    chosen, est = [], 0.0
    for c in range(s['next'], last + 1):
        chosen.append(c)
        est += s['pace'] * sum(len(v) for v in chapters[c])
        # A chapter tried again on its own.
        if est >= TARGET or s.get('odd'): break
    grow = 1.5 ** s['tries']
    end = s['pos'] + est * SLACK * grow + EXTRA * grow
    if chosen[-1] == last or end > s['duration'] - 30: end = s['duration']
    tokens = []
    if s['next'] == 1:
        tokens.append(('zzintro', 1, -1))
        tokens += [(w, 1, -1) for w in title(book)]
    else:
        # The rest of the pause before the heading, music.
        tokens.append(('zzhead', s['next'], -1))
    for c in chosen:
        tokens += [(w, c, -2) for w in heading(book, c, last)]
        for k, v in enumerate(chapters[c]):
            tokens += [(w, c, -2) for w in before_verse(book, c, k + 1)]
            tokens += [(w, c, k) for w in words(v)]
    tokens.append(('zzpad', None, -1))
    return s['pos'], end, tokens


def find_heading(book, s, chapter, a, b, flac_dir):
    """Where [chapter]'s heading is heard between [a] and [b] seconds (find_heading.py, by the Python in
    $KWS_PYTHON), or None."""
    said = ' '.join(heading(book, chapter, 2)[:1] + number(chapter))
    # "One hundred and ten", or without the "and".
    phrases = [said.replace('hundredand', 'hundred and'), said.replace('hundredand', 'hundred')] if 'hundredand' in said else [said]
    out = subprocess.run([os.environ.get('KWS_PYTHON', 'python3'), os.path.join(os.path.dirname(os.path.abspath(__file__)), 'find_heading.py'),
                          os.path.join(flac_dir, f'{s["n"]:02d}.flac'), f'{a:.2f}', f'{b:.2f}', *phrases], capture_output=True, text=True)
    return float(out.stdout) if out.returncode == 0 and out.stdout.strip() else None


def take(s, start, end, tokens, aligned, verses, book=None, flac_dir=None):
    """Takes a stretch's aligned words into the state [s] ([verses]: the book's, {chapter: [text]}), and
    says how it went."""
    got = entries(aligned)
    sm = difflib.SequenceMatcher(None, [t[0] for t in tokens], [g[2] for g in got], autojunk=False)
    at = {}
    for i, j, size in sm.get_matching_blocks():
        for d in range(size): at[i + d] = got[j + d]
    chapters = sorted({t[1] for t in tokens if t[1] is not None})
    taken, stretched = [], False
    for c in chapters:
        said = [i for i, t in enumerate(tokens) if t[1] == c and not t[0].startswith('zz')]
        verse_words = [i for i in said if tokens[i][2] >= 0]
        if any(i not in at for i in verse_words): break
        # Music after a chapter's heading or after its last verse is taken by the word next to it, often:
        # no matter (words_to_verses.py trims it off). Elsewhere, a word so long has been misplaced.
        music = set(verse_words[:3]) | {verse_words[-1]}
        if any(at[i][1] - at[i][0] > LONG_WORD for i in verse_words if i not in music):
            stretched = True
            break
        taken.append(c)
    as_is = False
    if not taken:
        if not stretched:
            s['tries'] += 1
            return f'AGAIN ({s["tries"]}), words not placed'
        if not s.get('odd'):
            s['odd'] = 1
            return f'{chapters[0]} again on its own'
        # Stretched again, on its own: taken as it is.
        taken, as_is = chapters[:1], True
    words_of = {c: [i for i, t in enumerate(tokens) if t[1] == c and t[2] >= 0] for c in taken}
    last_i = words_of[taken[-1]][-1]
    last_end = at[last_i][1]
    if taken == chapters:
        # Not enough recording for the words: the last ones squeezed in at the stretch's end.
        pad = at.get(len(tokens) - 1)
        pad_len = (pad[1] - pad[0]) if pad else 0.0
        tail = [at[i] for i in words_of[taken[-1]][-30:]]
        squeezed = sum(1 for a, b, _ in tail if b - a <= 0.035)
        if end < s['duration'] - 1 and (pad_len < MIN_PAD or squeezed > 6):
            s['tries'] += 1
            return f'AGAIN ({s["tries"]}), too short'
    spoken = 0.0
    for c in taken:
        idx = [i for i, t in enumerate(tokens) if t[1] == c]
        ws = [[round(at[i][0], 3), round(at[i][1], 3), tokens[i][0], tokens[i][2]] for i in words_of[c]]
        # From its heading's first word (a moment before it, not back into what's before); a book's first
        # chapter from the file's start, with the book's title.
        first = next((i for i in idx if tokens[i][2] == -2 and not tokens[i][0].startswith('zz') and i in at), None)
        if c == 1:
            begin = 0.0
        elif first is not None:
            before = [at[i][1] for i in range(first) if i in at]
            begin = max(at[first][0] - 0.1, before[-1] if before else start)
        else:
            begin = ws[0][0] - 0.1
        s['chapters'][str(c)] = {'start': round(begin, 3), 'words': ws, 'expected': len(words_of[c])}
        if as_is: s['chapters'][str(c)]['stretched'] = True
        spoken += ws[-1][1] - begin
    # On from just after the last word, in the pause before the next heading.
    after = [at[i][0] for i in range(last_i + 1, len(tokens)) if i in at]
    gap = (after[0] - last_end) if after and after[0] > last_end else 0.0
    s['pos'] = round(last_end + min(0.6 * gap, LEAD_IN), 3)
    if as_is and taken[-1] < max(verses) and flac_dir:
        # Its end isn't to be trusted, nor where the next chapter starts: its heading, heard after the
        # word stretched, if it's heard by the end of the chapter as placed and a little after.
        first_long = min(at[i][0] for i in words_of[taken[-1]] if at[i][1] - at[i][0] > LONG_WORD)
        found = find_heading(book, s, taken[-1] + 1, first_long, min(s['duration'], last_end + 30.0), flac_dir)
        if found is not None:
            s['pos'] = round(max(first_long, found - 0.5), 3)
            s['chapters'][str(taken[-1])]['heard_next'] = round(found, 2)
    s['next'] = taken[-1] + 1
    s['done'] = s['next'] > max(verses)
    s['tries'] = 0
    s['odd'] = 0
    chars = sum(len(v) for c in taken for v in verses[c])
    if spoken > 0 and chars: s['pace'] = 0.5 * s['pace'] + 0.5 * spoken / chars
    if as_is: return f'to {taken[-1]}, stretched'
    return f'to {taken[-1]}' + (f' ({taken[-1] + 1} stretched)' if stretched else '')


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
            if os.path.exists(path):
                moved.append(f'{b} ' + take(s, a, e, tokens, path, bible[b], b, flac_dir))
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
