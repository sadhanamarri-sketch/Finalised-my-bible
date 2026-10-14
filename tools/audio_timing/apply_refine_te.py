"""Applies the refinement to every Telugu chapter's timing (in place; the matching's own starts are
kept as 'matched', and a second run refines those again). usage: apply_refine_te.py <results dir>
<telugu json dir> <workers> <zip>..."""
import sys, os, json, glob, zipfile, collections
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches
from refine import refine, letters, heading_factor
from books import telugu_file, telugu_heading, spoken_length

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


def job(args):
    p, heading_takes = args
    r = json.load(open(p, encoding='utf-8'))
    z, m = MEMBERS[r['file']]
    quiet = quiet_stretches(envelope(decode(zipfile.ZipFile(z).read(m))))
    text = {c['chapter']: [v['text'] for v in c['verses']] for c in json.load(open(os.path.join(te_dir, r['book'] + '.json'), encoding='utf-8'))['chapters']}
    moved = total = 0
    for c, ch in r['chapters'].items():
        matched = ch.get('matched', ch['verses'])
        starts = [v[0] for v in matched]
        unsure = set(k - 1 for k in ch['metrics']['unsure'])
        new = refine(starts, unsure, [letters(t) for t in text[c]], quiet, ch['end'], head=(ch['metrics']['heading'], heading_takes[c]))
        changed = [k + 1 for k, (a, b) in enumerate(zip(new, starts)) if abs(a - b) > 0.15]
        ch['matched'] = matched
        ch['verses'] = [[round(s, 2), round(e, 2)] for s, e in zip(new, ends(new, quiet, ch['end']))]
        ch['metrics']['refined'] = changed
        ch['metrics']['heading_takes'] = round(heading_takes[c], 2)
        moved += len(changed); total += len(starts)
    r['refined'] = True
    r.pop('heading_gap', None)
    with open(p + '.tmp', 'w', encoding='utf-8') as f: json.dump(r, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(p + '.tmp', p)
    return r['book'], moved, total


if __name__ == '__main__':
    files = sorted(glob.glob(os.path.join(res, '*.json')))
    books = [f[:-5] for f in os.listdir(te_dir) if f.endswith('.json')]
    # each chapter's heading, how long espeak takes over it, and where the matching put verse 1
    rows = []
    for p in files:
        r = json.load(open(p, encoding='utf-8'))
        telugu = telugu_file(r['file'], books)[3]
        for c, ch in r['chapters'].items():
            rows.append((p, r['book'], c, telugu_heading(telugu, int(c)), ch.get('matched', ch['verses'])[0][0] - ch['metrics']['heading']))
    with Pool(workers) as pool:
        lengths = pool.starmap(spoken_length, [(t, 'te') for _, _, _, t, _ in rows])
    # how much longer the narrator takes over each book's headings; chapter 1's may follow a title
    by_book = collections.defaultdict(lambda: ([], []))
    for (p, b, c, t, g), n in zip(rows, lengths):
        if c != '1': by_book[b][0].append(g); by_book[b][1].append(n)
    overall = heading_factor([g for gs, _ in by_book.values() for g in gs], [n for _, ns in by_book.values() for n in ns])
    factor = {b: heading_factor(gs, ns, overall) for b, (gs, ns) in by_book.items()}
    takes = collections.defaultdict(dict)
    for (p, b, c, t, g), n in zip(rows, lengths):
        takes[p][c] = factor.get(b, overall) * n
    moved = total = 0
    with Pool(workers) as pool:
        for book, c, n in pool.imap_unordered(job, [(p, takes[p]) for p in files]):
            moved += c; total += n
            print(book, c, 'of', n, 'moved', flush=True)
    print(f'done: {moved} of {total} verse starts moved by the refinement', flush=True)
