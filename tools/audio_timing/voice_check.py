"""The last check, with the narrator's own voice: verses that begin with the same word (దేవుడు, మరియు,
"And") should sound alike just after their starts. For each verse whose first word begins at least 8
verses in the same file, the half second from its start is matched against the same word at the
others' starts, from 1.6 s earlier to 1 s later. Where it matches clearly better more than 0.3 s
earlier, the start had landed on a pause after the first word, and it's moved back to where the word
begins (the verse before then ends at the pause before it). The starts it moved are listed in each
chapter's metrics as 'voice_moved'. Run after the refinement; a second run starts again from the
refinement's starts.

usage: voice_check.py <results dir> <telugu json dir> <workers> <zip>...    (one file per book: Telugu)
"""
import sys, os, re, json, glob, zipfile, ctypes, collections
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches, mfcc, lib, STEP

lib.dtw_cost.restype = ctypes.c_double
_fp = ctypes.POINTER(ctypes.c_float)
lib.dtw_cost.argtypes = [_fp, ctypes.c_int, _fp, ctypes.c_int, ctypes.c_int]

WORD = 0.45                                  # seconds compared: about one word
OFFSETS = np.arange(-1.6, 1.0001, 0.04)
GROUP, OTHERS, LATE, MARGIN = 8, 24, -0.3, 0.25


def cost(a, b):
    return lib.dtw_cost(a.ctypes.data_as(_fp), len(a), b.ctypes.data_as(_fp), len(b), a.shape[1])


def first_word(text):
    words = text.split()
    return re.sub(r'[^\wఀ-౿]', '', words[0]).lower() if words else ''


def ends(starts, quiet, end):
    """Each verse ends where the pause before the next one begins (as the refinement does)."""
    out = []
    for k, s in enumerate(starts):
        if k + 1 == len(starts): out.append(end); continue
        nxt = starts[k + 1]
        before = [a for a, b in quiet if b - 0.1 <= nxt + 0.001 and b >= nxt - 0.1]
        out.append(before[0] + 0.05 if before and before[0] + 0.05 > s else nxt)
    return out


def check(F, quiet, chapters):
    """[chapters]: {chapter: (starts, texts, heading)}. Returns {chapter: {verse index: new start}}."""
    n = len(F)
    w = int(round(WORD / STEP))
    seg = lambda t: np.ascontiguousarray(F[min(n - w, max(0, int(round(t / STEP)))):][:w], np.float32)
    groups = collections.defaultdict(list)
    for c, (starts, texts, _) in chapters.items():
        for k, (s, t) in enumerate(zip(starts, texts)):
            groups[first_word(t)].append((c, k, s))
    q_ends = np.array([b for a, b in quiet])
    moves = collections.defaultdict(dict)
    for word, items in groups.items():
        if not word or len(items) < GROUP: continue
        for i, (c, k, s) in enumerate(items):
            others = [seg(t) for j, (_, _, t) in enumerate(items) if j != i][:OTHERS]
            scores = np.array([np.median([cost(seg(s + o), q) for q in others]) for o in OFFSETS])
            b = int(np.argmin(scores)); z = int(np.argmin(np.abs(OFFSETS)))
            if OFFSETS[b] > LATE or scores[b] > scores[z] - MARGIN: continue
            t = s + OFFSETS[b]
            # onto the end of the pause just before the word, if there's one close by
            near = np.where((q_ends - 0.08 >= t - 0.2) & (q_ends - 0.08 <= t + 0.25))[0]
            if len(near): t = float(q_ends[near[np.argmin(np.abs(q_ends[near] - 0.08 - t))]] - 0.08)
            starts, _, heading = chapters[c]
            floor = (starts[k - 1] if k else heading) + 0.5
            if floor < t < s - 0.15:
                moves[c][k] = round(t, 2)
    return moves


def job(args):
    p, te_dir, members = args
    r = json.load(open(p, encoding='utf-8'))
    z, m = members[r['file']]
    x = decode(zipfile.ZipFile(z).read(m))
    quiet = quiet_stretches(envelope(x))
    F = mfcc(x)
    text = {c['chapter']: [v['text'] for v in c['verses']] for c in json.load(open(os.path.join(te_dir, r['book'] + '.json'), encoding='utf-8'))['chapters']}
    chapters = {}
    for c, ch in r['chapters'].items():
        ch.setdefault('refined_verses', ch['verses'])
        chapters[c] = ([v[0] for v in ch['refined_verses']], text[c], ch['metrics']['heading'])
    moves = check(F, quiet, chapters)
    moved = 0
    for c, ch in r['chapters'].items():
        starts = list(chapters[c][0])
        for k, t in moves.get(c, {}).items(): starts[k] = t
        new_ends = ends(starts, quiet, ch['end'])
        ch['verses'] = [[round(s, 2), round(e, 2)] for s, e in zip(starts, new_ends)]
        ch['metrics']['voice_moved'] = sorted(k + 1 for k in moves.get(c, {}))
        moved += len(moves.get(c, {}))
    with open(p + '.tmp', 'w', encoding='utf-8') as f: json.dump(r, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(p + '.tmp', p)
    return r['book'], moved, sum(len(ch['verses']) for ch in r['chapters'].values())


if __name__ == '__main__':
    res, te_dir, workers, zips = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4:]
    members = {}
    for z in zips:
        for i in zipfile.ZipFile(z).infolist(): members.setdefault(os.path.basename(i.filename), (z, i.filename))
    files = sorted(glob.glob(os.path.join(res, '*.json')), key=os.path.getsize, reverse=True)
    moved = total = 0
    with Pool(workers) as pool:
        for book, c, n in pool.imap_unordered(job, [(p, te_dir, members) for p in files]):
            moved += c; total += n
            print(book, c, 'of', n, 'starts moved back', flush=True)
    print(f'done: {moved} of {total} verse starts moved back to their first word', flush=True)
