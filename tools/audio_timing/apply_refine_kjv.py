"""Applies the refinement to every KJV chapter's timing (in place; the matching's own starts are kept
as 'matched'). usage: apply_refine_kjv.py <results dir> <kjv.tsv> <workers> <zip>..."""
import sys, os, json, glob, zipfile
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches
from refine import refine, letters
res, kjv, workers, zips = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4:]
TEXT = {}
for line in open(kjv, encoding='utf-8'):
    b, c, v, t = line.rstrip('\n').split('\t'); TEXT.setdefault((b, int(c)), []).append(t)
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

def job(p):
    r = json.load(open(p))
    if 'matched' in r: return 0, 0
    z, m = MEMBERS[r['file']]
    quiet = quiet_stretches(envelope(decode(zipfile.ZipFile(z).read(m))))
    starts = [v[0] for v in r['verses']]
    unsure = set(k - 1 for k in r['metrics']['unsure'])
    new = refine(starts, unsure, [letters(t) for t in TEXT[(r['book'], r['chapter'])]], quiet, r['duration'])
    changed = [k + 1 for k, (a, b) in enumerate(zip(new, starts)) if abs(a - b) > 0.15]
    r['matched'] = r['verses']
    r['verses'] = [[round(s, 2), round(e, 2)] for s, e in zip(new, ends(new, quiet, r['duration']))]
    r['metrics']['refined'] = changed
    with open(p + '.tmp', 'w', encoding='utf-8') as f: json.dump(r, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(p + '.tmp', p)
    return len(changed), len(starts)

if __name__ == '__main__':
    files = sorted(glob.glob(os.path.join(res, '*.json')))
    moved = total = 0
    with Pool(workers) as pool:
        for i, (c, n) in enumerate(pool.imap_unordered(job, files)):
            moved += c; total += n
            if (i + 1) % 100 == 0: print(i + 1, 'chapters', flush=True)
    print(f'done: {moved} of {total} verse starts moved by the refinement', flush=True)
