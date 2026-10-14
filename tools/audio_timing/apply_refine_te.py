"""Applies the refinement to every Telugu chapter's timing (in place; the matching's own starts are
kept as 'matched', and a second run refines those again). usage: apply_refine_te.py <results dir>
<telugu json dir> <workers> <zip>..."""
import sys, os, json, glob, zipfile
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches
from refine import refine, letters

res, te_dir, workers, zips = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4:]
MEMBERS = {}
for z in zips:
    for i in zipfile.ZipFile(z).infolist(): MEMBERS.setdefault(os.path.basename(i.filename), (z, i.filename))


def ends(starts, quiet, end):
    out = []
    for k, s in enumerate(starts):
        if k + 1 == len(starts): out.append(end); continue
        nxt = starts[k + 1]
        before = [a for a, b in quiet if b - 0.1 <= nxt + 0.001 and b >= nxt - 0.1]
        out.append(before[0] + 0.05 if before and before[0] + 0.05 > s else nxt)
    return out


def heading_gap(gaps, fallback):
    """How long this narrator's chapter headings usually run before verse 1: the median of the
    chapters where the matching put verse 1 a second or more after the heading."""
    ok = [g for g in gaps if g >= 1.0]
    return float(np.median(ok)) if len(ok) >= 3 else fallback


def job(p):
    r = json.load(open(p, encoding='utf-8'))
    z, m = MEMBERS[r['file']]
    quiet = quiet_stretches(envelope(decode(zipfile.ZipFile(z).read(m))))
    text = {c['chapter']: [v['text'] for v in c['verses']] for c in json.load(open(os.path.join(te_dir, r['book'] + '.json'), encoding='utf-8'))['chapters']}
    matched = {c: ch.get('matched', ch['verses']) for c, ch in r['chapters'].items()}
    gap = heading_gap([matched[c][0][0] - ch['metrics']['heading'] for c, ch in r['chapters'].items()], 3.4)
    moved = total = 0
    for c, ch in r['chapters'].items():
        starts = [v[0] for v in matched[c]]
        unsure = set(k - 1 for k in ch['metrics']['unsure'])
        new = refine(starts, unsure, [letters(t) for t in text[c]], quiet, ch['end'], head=(ch['metrics']['heading'], gap))
        changed = [k + 1 for k, (a, b) in enumerate(zip(new, starts)) if abs(a - b) > 0.15]
        ch['matched'] = matched[c]
        ch['verses'] = [[round(s, 2), round(e, 2)] for s, e in zip(new, ends(new, quiet, ch['end']))]
        ch['metrics']['refined'] = changed
        moved += len(changed); total += len(starts)
    r['refined'] = True
    r['heading_gap'] = round(gap, 2)
    with open(p + '.tmp', 'w', encoding='utf-8') as f: json.dump(r, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(p + '.tmp', p)
    return r['book'], moved, total


if __name__ == '__main__':
    files = sorted(glob.glob(os.path.join(res, '*.json')))
    moved = total = 0
    with Pool(workers) as pool:
        for book, c, n in pool.imap_unordered(job, files):
            moved += c; total += n
            print(book, c, 'of', n, 'moved', flush=True)
    print(f'done: {moved} of {total} verse starts moved by the refinement', flush=True)
