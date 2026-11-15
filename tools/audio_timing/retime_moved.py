"""Times again the Telugu verses whose boundaries the app's text moved (backlog 56). BSI's own text
(6 Nov 2026) numbers some verses otherwise than the text the timings were made with, and starts or ends
others a few words earlier or later; the recording reads BSI's Old Version, so it's the new text that's
heard.

A boundary that falls where one of the old text's did keeps that one's times, which were checked by
ear, whatever the verses' numbers now. The others are placed between the nearest boundaries that didn't
move: espeak reads the new text of the verses between them at the narrator's pace, and a little slower
and faster; each reading is matched to that stretch of the recording by its sound (timing.py); each
verse's start goes onto the nearest pause, and the readings vote. Then the narrator's own voice checks
each start (as voice_check.py does): where the verse's first word, as he says it at the starts of other
verses in the same file, clearly matches better a little earlier or later, the start goes there. A
verse ends where the pause before the next one begins. Starts the readings disagreed on by more than
0.3 s, or that the voice check moved by as much, are listed as unsure.

usage: retime_moved.py <telugu.json> <old text: git rev> <telugu text dir> <recordings dir> <workers>
       [--test=N] [--results=path]
       retime_moved.py <telugu.json> --apply=<results path>
  The first works out the new timings and keeps them in the results file (default:
  results/retime.json), with --test=N also placing N boundaries that didn't move, as if they had
  (between the ones either side), and saying how close they came to the times checked by ear. The
  second writes them into <telugu.json>, and lists every verse it changed (results/retime_changed.json).
"""
import sys, os, re, json, subprocess, difflib, random, collections
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches, snap_near, mfcc, synth, match_diag, SR, STEP
from voice_check import cost, first_word, WORD

LETTER = re.compile(r'[ఀ-౿]')
TOL = 4                                   # letters either way that still count as the same place
SPREAD = (0.88, 1.0, 1.12)                # espeak's three paces, around the narrator's
VOICE_OFFSETS = np.arange(-0.8, 0.8001, 0.04)
VOICE_GROUP, VOICE_OTHERS, VOICE_MARGIN, VOICE_MIN_MOVE = 8, 24, 0.25, 0.12
UNSURE = 0.3


def letters(text):
    return ''.join(LETTER.findall(text))


def spoken(text):
    return ' '.join(text.split())


def load_text(text_dir, book, rev=None):
    """{chapter: [(verse, text)]} from the app's Telugu text, now or at a git revision."""
    path = os.path.join(text_dir, book + '.json')
    if rev:
        top = subprocess.run(['git', '-C', text_dir, 'rev-parse', '--show-toplevel'], capture_output=True, text=True, check=True).stdout.strip()
        rel = os.path.relpath(os.path.abspath(path), top)
        raw = subprocess.run(['git', '-C', top, 'show', f'{rev}:{rel}'], capture_output=True, text=True, check=True).stdout
    else:
        raw = open(path, encoding='utf-8').read()
    return {int(c['chapter']): [(int(v['verse']), v['text']) for v in c['verses']] for c in json.loads(raw)['chapters']}


def boundaries(old, new):
    """For each boundary between new verses i and i+1: the index j of the old verse it ends, when it
    falls where the old text's boundary between j and j+1 did, else None (to be placed)."""
    o = [letters(t) for _, t in old]; n = [letters(t) for _, t in new]
    O, N = ''.join(o), ''.join(n)
    o_end = list(np.cumsum([len(x) for x in o]))
    blocks = difflib.SequenceMatcher(None, N, O, autojunk=False).get_matching_blocks()

    def to_old(q):
        """Where q (in the new text) can be in the old: a point within a matched stretch, or the gap
        between two (words added, dropped or changed there)."""
        prev = 0
        for a, b, size in blocks:
            if a < q < a + size: return b + (q - a), b + (q - a)
            if q <= a: return prev, b
            prev = b + size
        return prev, prev

    out, p = [], 0
    for i in range(len(new) - 1):
        p += len(n[i])
        lo, hi = to_old(p)
        at = [j for j in range(len(old) - 1) if lo - TOL <= o_end[j] <= hi + TOL]
        out.append(at[0] if at else None)
    return out


def choose(est, spread, lengths, pace, near, w0, w1, sure_sd=0.35, unsure_sd=2.0, reach=3.0, pause_weight=2.0):
    """Each start (after the first, fixed at w0) chosen among the pauses near where the matching put it
    (as refine.py does): held tightly where its readings agreed, a longer pause liked better (a verse
    usually follows one), and each verse taking about as long as its letters at the narrator's [pace]
    in this chapter, the last one ending at w1."""
    n = len(lengths)
    mu = [pace * l for l in lengths]
    onsets = np.array([b - 0.08 for a, b in near], float)
    plen = np.array([b - a for a, b in near], float)
    cands, scores = [np.array([w0])], [np.array([0.0])]
    for k in range(1, n):
        doubt = spread[k] > UNSURE
        sd, w = (unsure_sd, reach) if doubt else (sure_sd, 1.2)
        pick = np.where((np.abs(onsets - est[k]) <= w) & (onsets > w0 + 0.4) & (onsets < w1 - 0.4))[0]
        c = np.array([est[k]] + [onsets[i] for i in pick]); pl = np.array([0.0] + [plen[i] for i in pick])
        cands.append(c)
        scores.append(-(c - est[k]) ** 2 / (2 * sd ** 2) + pause_weight * np.log1p(pl))
    best, back = [scores[0]], []
    for k in range(1, n):
        d = cands[k][:, None] - cands[k - 1][None, :]
        sd = 0.2 * mu[k - 1] + 0.6
        trans = -(d - mu[k - 1]) ** 2 / (2 * sd ** 2)
        trans[d < 0.4] = -1e9
        tot = best[-1][None, :] + trans
        j = np.argmax(tot, axis=1)
        best.append(scores[k] + tot[np.arange(len(j)), j])
        back.append(j)
    last = w1 - cands[-1]
    final = best[-1] - (last - mu[-1]) ** 2 / (2 * (0.2 * mu[-1] + 0.6) ** 2) + np.where(last < 0.4, -1e9, 0.0)
    i = int(np.argmax(final))
    out = [0.0] * n
    for k in range(n - 1, -1, -1):
        out[k] = float(cands[k][i])
        if k > 0: i = int(back[k - 1][i])
    return out


def place(x, quiet, F, templates, texts, w0, w1, pace, voice=True):
    """Starts for texts[1:] and ends for texts[:-1] (file seconds), the verses [texts] being read one
    after another from w0 to w1; and for each start how sure (spread of the readings, voice move)."""
    real = x[int(round(w0 * SR)):int(round(w1 * SR))]
    A = mfcc(real)
    near = [(a, b) for a, b in quiet if b > w0 - 1 and a < w1 + 1]
    syn, _, ssr = synth(texts, 'te', 160)
    pace = min(max(160 * (len(syn) / ssr) / (len(real) / SR), 80 / SPREAD[0]), 400)
    votes, raws = [], []
    for k in SPREAD:
        syn, offs, ssr = synth(texts, 'te', int(round(pace * k)))
        first, _ = match_diag(A, mfcc(syn, ssr))
        raw = [w0 + first[min(int(s / STEP), len(first) - 1)] * STEP for s in offs]
        raws.append(raw)
        votes.append([snap_near(t, near, within=0.6)[0] for t in raw])
    votes = np.array(votes)
    spread = list(votes.max(0) - votes.min(0))
    starts = choose(list(np.median(votes, axis=0)), spread, [len(letters(t)) for t in texts], pace, near, w0, w1)
    moved = [0.0] * len(texts)
    if voice:
        q_ends = np.array([b for a, b in near]) if near else np.zeros(0)
        n = len(F); w = int(round(WORD / STEP))
        seg = lambda t: np.ascontiguousarray(F[min(n - w, max(0, int(round(t / STEP)))):][:w], np.float32)
        for k in range(1, len(texts)):
            others = [t for t in templates.get(first_word(texts[k]), []) if abs(t - starts[k]) > 2][:VOICE_OTHERS]
            if len(others) < VOICE_GROUP: continue
            refs = [seg(t) for t in others]
            scores = np.array([np.median([cost(seg(starts[k] + o), r) for r in refs]) for o in VOICE_OFFSETS])
            b = int(np.argmin(scores)); z = int(np.argmin(np.abs(VOICE_OFFSETS)))
            if abs(VOICE_OFFSETS[b]) < VOICE_MIN_MOVE or scores[b] > scores[z] - VOICE_MARGIN: continue
            t = starts[k] + VOICE_OFFSETS[b]
            close = np.where((q_ends - 0.08 >= t - 0.2) & (q_ends - 0.08 <= t + 0.25))[0]
            if len(close): t = float(q_ends[close[np.argmin(np.abs(q_ends[close] - 0.08 - t))]] - 0.08)
            lo = starts[k - 1] + 0.5 if k > 1 else w0 + 0.5
            hi = starts[k + 1] - 0.5 if k + 1 < len(texts) else w1 - 0.3
            if lo < t < hi:
                moved[k] = t - starts[k]
                starts[k] = t
    starts = [round(float(s), 2) for s in starts]
    ends = []
    for k in range(len(texts) - 1):
        nxt = starts[k + 1]
        before = [a for a, b in near if b - 0.1 <= nxt + 0.001 and b >= nxt - 0.1]
        ends.append(round(before[0] + 0.05, 2) if before and before[0] + 0.05 > starts[k] else round(nxt - 0.02, 2))
    sure = [None] + [{'spread': round(float(spread[k]), 2), 'voice': round(float(moved[k]), 2)} for k in range(1, len(texts))]
    return starts[1:], ends, sure[1:]


def place_or_split(x, quiet, F, templates, texts, w0, w1, pace, voice=True):
    """place(), or where espeak or the match fails, starts spread by the texts' lengths onto the nearest
    pauses (listed as unsure)."""
    try:
        return place(x, quiet, F, templates, texts, w0, w1, pace, voice)
    except Exception as e:
        near = [(a, b) for a, b in quiet if b > w0 - 1 and a < w1 + 1]
        lens = np.cumsum([len(letters(t)) for t in texts]); total = float(lens[-1])
        starts = [round(snap_near(w0 + (w1 - w0) * lens[k - 1] / total, near, within=0.6)[0], 2) for k in range(1, len(texts))]
        ends = [round(s - 0.02, 2) for s in starts]
        return starts, ends, [{'spread': 9.99, 'voice': 0.0, 'failed': str(e)[:80]} for _ in starts]


def file_job(args):
    """Every chapter of one recording file with boundaries to place (or, testing, to try)."""
    path, chapters = args
    x = decode(path)
    quiet = quiet_stretches(envelope(x))
    F = mfcc(x)
    # The narrator's verse starts in this file, by their first word, from the timings checked by ear.
    templates = collections.defaultdict(list)
    for c in chapters:
        for (_, t), (s, _) in zip(c['old'], c['timed']):
            templates[first_word(t)].append(s)
    results = []
    for c in chapters:
        timed, new = c['timed'], c['new']
        rates = [(e - st) / max(1, len(letters(t))) for (_, t), (st, e) in zip(c['old'], timed)]
        pace = float(np.median(rates))
        for i in c.get('try', []):
            texts = [spoken(new[i - 1][1]), spoken(new[i][1]), spoken(new[i + 1][1])]
            s, e, sure = place_or_split(x, quiet, F, templates, texts, timed[i - 1][0], timed[i + 1][1], pace)
            s0, _, _ = place_or_split(x, quiet, F, templates, texts, timed[i - 1][0], timed[i + 1][1], pace, voice=False)
            results.append({'test': True, 'chapter': c['name'], 'verse': i + 1, 'checked': timed[i][0], 'placed': s[0],
                            'before_voice': s0[0], 'spread': sure[0]['spread']})
        if not c['moved']: continue
        n = len(new)
        start = [None] * n; end = [None] * n
        start[0], end[n - 1] = timed[0][0], timed[-1][1]
        for i, j in enumerate(c['cuts']):
            if j is not None:
                end[i], start[i + 1] = timed[j][1], timed[j + 1][0]
        sure = {}
        i = 0
        while i < n - 1:
            if c['cuts'][i] is not None: i += 1; continue
            k = i
            while k + 1 < n - 1 and c['cuts'][k + 1] is None: k += 1
            texts = [spoken(new[v][1]) for v in range(i, k + 2)]
            s, e, how = place_or_split(x, quiet, F, templates, texts, start[i], end[k + 1], pace)
            for d in range(len(s)):
                start[i + 1 + d] = s[d]; sure[i + 1 + d] = how[d]
            for d in range(len(e)):
                end[i + d] = e[d]
            i = k + 1
        results.append({'chapter': c['name'], 'verses': [[start[v], end[v]] for v in range(n)],
                        'sure': {str(v): h for v, h in sure.items()}})
    return results


def apply(data, out, timing_path):
    report = []
    for r in sorted(out, key=lambda r: r['chapter']):
        ch = data['chapters'][r['chapter']]
        before = ch['verses']
        sure = {int(v): h for v, h in r['sure'].items()}
        ch['verses'] = r['verses']
        changed = [v for v in range(len(before)) if before[v] != r['verses'][v]]
        unsure = set(ch.get('unsure', [])) - {v + 1 for v in changed} - {v + 1 for v in sure}
        unsure |= {v + 1 for v, how in sure.items() if how['spread'] > UNSURE or abs(how['voice']) >= UNSURE}
        if unsure: ch['unsure'] = sorted(unsure)
        else: ch.pop('unsure', None)
        for v in changed:
            report.append({'chapter': r['chapter'], 'verse': v + 1, 'was': before[v], 'now': r['verses'][v],
                           'placed': v in sure, **(sure.get(v) or {})})
    if 'timed again' not in data['how']:
        data['how'] += (" Verses whose boundaries the app's text then moved (BSI's own text, November 2026) were timed again"
                        " between the boundaries that didn't move: espeak matched to the narration, his pauses, and his own"
                        " voice saying their first words (tools/audio_timing/retime_moved.py).")
    with open(timing_path + '.tmp', 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(timing_path + '.tmp', timing_path)
    return report


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    opts = dict(a[2:].split('=', 1) if '=' in a else (a[2:], '') for a in sys.argv[1:] if a.startswith('--'))
    timing_path = args[0]
    data = json.load(open(timing_path, encoding='utf-8'))
    results_path = opts.get('results') or os.path.join('results', 'retime.json')
    os.makedirs(os.path.dirname(results_path) or '.', exist_ok=True)
    if 'apply' in opts:
        out = json.load(open(opts['apply'] or results_path, encoding='utf-8'))['retimed']
        report = apply(data, out, timing_path)
        json.dump(report, open(os.path.join(os.path.dirname(results_path) or '.', 'retime_changed.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
        placed = sum(1 for r in report if r['placed'])
        print(f'{len(report)} verses timed again in {len({r["chapter"] for r in report})} chapters: {placed} starts '
              f'placed, the rest from the old text\'s boundaries', flush=True)
        return
    rev, text_dir, rec_dir, workers = args[1], args[2], args[3], int(args[4])
    test = int(opts.get('test') or 0)
    books = sorted(f[:-5] for f in os.listdir(text_dir) if f.endswith('.json'))
    by_file = collections.defaultdict(list)
    for book in books:
        old, new = load_text(text_dir, book, rev), load_text(text_dir, book)
        for ch in sorted(new):
            name = f'{book} {ch}'
            timed = data['chapters'][name]['verses']
            assert len(old[ch]) == len(timed) == len(new[ch]), name
            cuts = boundaries(old[ch], new[ch])
            same = {i for i, j in enumerate(cuts) if j == i}
            by_file[data['chapters'][name]['file']].append({
                'name': name, 'old': old[ch], 'new': new[ch], 'timed': timed, 'cuts': cuts,
                'moved': any(j != i for i, j in enumerate(cuts)),
                'try': [i for i in range(1, len(cuts)) if i - 1 in same and i in same]})
    # only the files with something to time again; the test's boundaries come from those same files
    files = {f: cs for f, cs in by_file.items() if any(c['moved'] for c in cs)}
    pool = [(f, c['name'], i) for f, cs in files.items() for c in cs for i in c['try']]
    picked = set(random.Random(0).sample(pool, min(test, len(pool)))) if test else set()
    jobs = []
    for f, cs in files.items():
        keep = []
        for c in cs:
            tr = [i for i in c['try'] if (f, c['name'], i) in picked]
            if c['moved'] or tr: keep.append(dict(c, **{'try': tr}))
        jobs.append((os.path.join(rec_dir, f), keep))
    jobs.sort(key=lambda j: -os.path.getsize(j[0]))
    out = []
    with Pool(workers) as p:
        for k, res in enumerate(p.imap_unordered(file_job, jobs)):
            out.extend(res)
            print(f'{k + 1}/{len(jobs)} files', flush=True)
    tests = [r for r in out if r.get('test')]
    retimed = [r for r in out if not r.get('test')]
    json.dump({'retimed': retimed, 'test': tests}, open(results_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    if tests:
        err = np.array([abs(r['placed'] - r['checked']) for r in tests])
        err0 = np.array([abs(r['before_voice'] - r['checked']) for r in tests])
        for label, e in (('matched and voted', err0), ('then the voice check', err)):
            print(f'test, {label}: {len(e)} starts, within 0.15 s {np.mean(e <= 0.15):.0%}, within 0.3 s '
                  f'{np.mean(e <= 0.3):.0%}, median {np.median(e):.2f} s, worst {e.max():.2f} s', flush=True)
    print(f'{len(retimed)} chapters worked out; kept in {results_path}', flush=True)


if __name__ == '__main__':
    main()
