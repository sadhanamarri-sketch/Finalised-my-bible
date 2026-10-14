"""Applies the refinement to every KJV chapter's timing (in place; the matching's own starts are kept
as 'matched', and a second run refines those again). usage: apply_refine_kjv.py <results dir>
<kjv.tsv> <workers> <zip>... (where two zips hold the same file, the first one given is used)"""
import sys, os, json, glob, zipfile, collections
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches
from refine import refine, letters, heading_factor
from books import kjv_heading, spoken_length
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

def job(args):
    p, heading_takes = args
    r = json.load(open(p))
    z, m = MEMBERS[r['file']]
    quiet = quiet_stretches(envelope(decode(zipfile.ZipFile(z).read(m))))
    matched = r.get('matched', r['verses'])
    starts = [v[0] for v in matched]
    unsure = set(k - 1 for k in r['metrics']['unsure'])
    new = refine(starts, unsure, [letters(t) for t in TEXT[(r['book'], r['chapter'])]], quiet, r['duration'],
                 head=(r['metrics']['heading'], heading_takes))
    changed = [k + 1 for k, (a, b) in enumerate(zip(new, starts)) if abs(a - b) > 0.15]
    r['matched'] = matched
    r['verses'] = [[round(s, 2), round(e, 2)] for s, e in zip(new, ends(new, quiet, r['duration']))]
    r['metrics']['refined'] = changed
    r['metrics']['heading_takes'] = round(heading_takes, 2)
    r.pop('heading_gap', None)
    with open(p + '.tmp', 'w', encoding='utf-8') as f: json.dump(r, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(p + '.tmp', p)
    return len(changed), len(starts)

if __name__ == '__main__':
    files = sorted(glob.glob(os.path.join(res, '*.json')))
    rows = []
    for p in files:
        r = json.load(open(p))
        rows.append((p, r['book'], r['chapter'], r.get('matched', r['verses'])[0][0] - r['metrics']['heading']))
    with Pool(workers) as pool:
        lengths = pool.starmap(spoken_length, [(kjv_heading(b, c), 'en-us') for _, b, c, _ in rows])
    # how much longer the narrator takes over each book's headings; chapter 1's follow the book's title
    by_book = collections.defaultdict(lambda: ([], []))
    for (p, b, c, g), n in zip(rows, lengths):
        if c != 1: by_book[b][0].append(g); by_book[b][1].append(n)
    overall = heading_factor([g for gs, _ in by_book.values() for g in gs], [n for _, ns in by_book.values() for n in ns])
    factor = {b: heading_factor(gs, ns, overall) for b, (gs, ns) in by_book.items()}
    jobs = [(p, factor.get(b, overall) * n) for (p, b, c, g), n in zip(rows, lengths)]
    moved = total = 0
    with Pool(workers) as pool:
        for i, (c, n) in enumerate(pool.imap_unordered(job, jobs)):
            moved += c; total += n
            if (i + 1) % 100 == 0: print(i + 1, 'chapters', flush=True)
    print(f'done: {moved} of {total} verse starts moved by the refinement', flush=True)
