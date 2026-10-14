"""Times every chapter file in Faith Comes By Hearing's KJV zips (one file per chapter, named like
B01___01_Matthew_____ENGKJVN1DA.mp3): results/kjv/<book number>_<chapter>.json, kept between runs.

usage: run_kjv.py <kjv.tsv> <results dir> <workers> <zip>...
"""
import sys, os, re, json, zipfile, time, traceback
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from timing import decode, envelope, quiet_stretches, time_chapter
from books import kjv_heading

KJV_TSV, OUT, WORKERS, ZIPS = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4:]
BOOKS, TEXT = [], {}
for line in open(KJV_TSV, encoding='utf-8'):
    b, c, v, t = line.rstrip('\n').split('\t')
    if b not in BOOKS: BOOKS.append(b)
    TEXT.setdefault((b, int(c)), []).append(' '.join(t.replace('¶', ' ').split()))

NAME = re.compile(r'([AB])(\d\d)_+(\d+)_+[A-Za-z0-9]+?_*ENGKJV')


def job(args):
    zpath, member, book, ch, out = args
    if os.path.exists(out):
        return out, None, 'kept', 0
    try:
        t0 = time.time()
        with zipfile.ZipFile(zpath) as z:
            data = z.read(member)
        x = decode(data)
        quiet = quiet_stretches(envelope(x))
        verses, m = time_chapter(x, [kjv_heading(book, ch)] + TEXT[(book, ch)], 'en-us', quiet)
        result = {'book': book, 'chapter': ch, 'file': os.path.basename(member), 'duration': round(len(x) / 16000, 2),
                  'verses': verses, 'metrics': m}
        with open(out + '.tmp', 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, separators=(',', ':'))
        os.replace(out + '.tmp', out)
        return out, m, f'{time.time() - t0:.1f}s', len(verses)
    except Exception:
        return out, None, 'error ' + traceback.format_exc(limit=3).replace('\n', ' | '), 0


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = []
    damaged = []
    taken = set()   # where two zips hold the same chapter, the first one given is used
    for zpath in ZIPS:
        with zipfile.ZipFile(zpath) as z, open(zpath, 'rb') as raw:
            for info in z.infolist():
                m = NAME.search(os.path.basename(info.filename))
                if not m:
                    continue
                raw.seek(info.header_offset)
                if raw.read(4) != b'PK\x03\x04':
                    damaged.append(os.path.basename(info.filename))
                    continue
                testament, num, ch = m.group(1), int(m.group(2)), int(m.group(3))
                index = num - 1 if testament == 'A' else 38 + num
                book = BOOKS[index]
                out = os.path.join(OUT, f'{index + 1:02d}_{ch:03d}.json')
                if out in taken:
                    continue
                taken.add(out)
                jobs.append((zpath, info.filename, book, ch, out))
    jobs.sort(key=lambda j: j[4])
    print(f'{len(jobs)} chapters; {len(damaged)} damaged in the zip, skipped: {damaged}', flush=True)
    done = 0
    with Pool(WORKERS) as pool:
        for out, m, note, n in pool.imap_unordered(job, jobs):
            done += 1
            flag = ' CHECK' if m and (m['on_pause'] < 0.8 or len(m['unsure']) > max(3, 0.2 * n)) else ''
            print(f'{done}/{len(jobs)} {os.path.basename(out)} {note}{flag}' + (f' on_pause {m["on_pause"]} unsure {len(m["unsure"])} corr {m["corr"]}' if m else ''), flush=True)
    print('all done', flush=True)


if __name__ == '__main__':
    main()
