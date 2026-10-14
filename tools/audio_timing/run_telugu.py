"""Times the Telugu recordings: one file per book (Psalms in two), its chapters joined end to end with
a moment of digital silence between them. Each book is split there, then each chapter is timed:
results/te/<book number>_<first chapter>.json, kept between runs.

usage: run_telugu.py <telugu json dir> <kjv.tsv (for the book order)> <results dir> <workers> <zip>...
"""
import sys, os, re, json, zipfile, time, traceback
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from timing import decode, envelope, quiet_stretches, digital_silences, time_group, synth, SR

TE_DIR, KJV_TSV, OUT, WORKERS, ZIPS = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5:]
BOOKS = []
for line in open(KJV_TSV, encoding='utf-8'):
    b = line.split('\t', 1)[0]
    if b not in BOOKS: BOOKS.append(b)
ALIASES = {'Michah': 'Micah', 'Zecharia': 'Zechariah'}
ORDINALS = ['మొదటి', 'రెండవ', 'మూడవ', 'నాలుగవ', 'అయిదవ', 'ఆరవ', 'ఏడవ', 'ఎనిమిదవ', 'తొమ్మిదవ', 'పదియవ']


def parse(member):
    """(book, first chapter, last chapter or None, the book's Telugu name) from a file's name, like
    "Book of Genesis  - ఆదికాండము  Telugu Audio Bible   YGFM - Apostle Ratnaker.mp3" or
    "Psalms Part 2 - కీర్తనలు 73 - 150 అధ్యాయములు.mp3"."""
    base = os.path.basename(member)[:-4]
    english, _, rest = base.partition(' - ')
    english = ' '.join(english.replace('Book of', '').split())
    first, last = 1, None
    part = re.match(r'Psalms Part (\d)', english)
    if part:
        english = 'Psalms'
        nums = [int(n) for n in re.findall(r'\d+', rest)]
        first, last = nums[0], nums[1]
    english = ALIASES.get(english, english)
    if english not in BOOKS:
        raise ValueError(f'no book for {base!r}')
    telugu = rest.split('Telugu')[0]
    telugu = re.split(r'\s\d', ' ' + telugu.strip())[0].strip() if part else telugu.strip()
    return english, first, last, ' '.join(telugu.split())


def merge_joins(joins, within=3.0):
    """Joins closer than [within] s are one join, broken up by a moment of sound."""
    out = []
    for a, b in joins:
        if out and a - out[-1][1] < within: out[-1] = (out[-1][0], b)
        else: out.append((a, b))
    return out


def drop_gated(joins, near=20.0, run=3):
    """Joins that come in a quick run, [run] or more each within [near] s of the last, are pauses
    silenced in editing (a noise gate), not joins between chapters: they're dropped."""
    out, i = [], 0
    while i < len(joins):
        j = i
        while j + 1 < len(joins) and joins[j + 1][0] - joins[j][1] < near: j += 1
        if j - i + 1 < run: out += joins[i:j + 1]
        i = j + 1
    return out


def plan(durations, expected):
    """Which chapters each stretch between joins holds, fitted by length: consecutive chapters, so a
    stretch may hold several (read on with no join between them), none (a short interlude), or a
    chapter may run over a join. Returns [(first stretch, end stretch, first chapter, end chapter)]."""
    S, C = len(durations), len(expected)
    cd = np.concatenate(([0.0], np.cumsum(durations)))
    ce = np.concatenate(([0.0], np.cumsum(expected)))
    INF = float('inf')
    f = np.full((S + 1, C + 1), INF); back = {}
    f[0][0] = 0.0
    for s in range(S):
        for c in range(C + 1):
            if f[s][c] == INF: continue
            if durations[s] < 120 and f[s][c] + 0.6 < f[s + 1][c]:
                f[s + 1][c] = f[s][c] + 0.6; back[(s + 1, c)] = (s, c)
            for s2 in range(s + 1, min(S, s + 3) + 1):
                d = cd[s2] - cd[s]
                for c2 in range(c + 1, min(C, c + 8) + 1):
                    e = ce[c2] - ce[c]
                    cost = f[s][c] + 10 * np.log(d / e) ** 2 + 0.3 * (s2 - s - 1) + 0.02 * (c2 - c - 1)
                    if cost < f[s2][c2]:
                        f[s2][c2] = cost; back[(s2, c2)] = (s, c)
    if f[S][C] == INF: return None
    out, at = [], (S, C)
    while at != (0, 0):
        prev = back[at]
        out.append((prev[0], at[0], prev[1], at[1]))
        at = prev
    return list(reversed(out))


def job(args):
    zpath, member = args
    try:
        book, first, last, telugu = parse(member)
        index = BOOKS.index(book)
        out = os.path.join(OUT, f'{index + 1:02d}_{first:03d}.json')
        if os.path.exists(out):
            return out, 'kept'
        t0 = time.time()
        text = json.load(open(os.path.join(TE_DIR, book + '.json'), encoding='utf-8'))
        chapters = [c for c in text['chapters'] if int(c['chapter']) >= first and (last is None or int(c['chapter']) <= last)]
        with zipfile.ZipFile(zpath) as z:
            x = decode(z.read(member))
        total = len(x) / SR
        joins = drop_gated(merge_joins([(a, b) for a, b in digital_silences(x) if a > 1.0 and b < total - 1.0]))
        bounds = [0.0] + [round((a + b) / 2, 3) for a, b in joins] + [round(total, 3)]
        stretches = list(zip(bounds[:-1], bounds[1:]))
        texts = []
        for ch in chapters:
            c = int(ch['chapter'])
            heading = f'{telugu} {ORDINALS[c - 1] if c <= len(ORDINALS) else str(c) + " వ"} అధ్యాయము'
            texts.append([heading] + [' '.join(v['text'].split()) for v in ch['verses']])
        raw = [len(synth(t, 'te', 160)[0]) / 22050 for t in texts]
        pace = total / sum(raw)
        groups = plan([b - a for a, b in stretches], [pace * r for r in raw])
        if groups is None:
            return out, f'PLAN {book}: no fit for {len(chapters)} chapters in {len(stretches)} stretches'
        quiet = quiet_stretches(envelope(x))
        result = {'book': book, 'file': os.path.basename(member), 'duration': round(total, 2), 'joins': len(joins),
                  'groups': [], 'chapters': {}}
        for s1, s2, c1, c2 in groups:
            a, b = stretches[s1][0], stretches[s2 - 1][1]
            ratio = (b - a) / (pace * sum(raw[c1:c2])) if c2 > c1 else None
            result['groups'].append({'stretches': [s1, s2], 'chapters': [int(chapters[c]['chapter']) for c in range(c1, c2)],
                                     'from': a, 'to': b, 'ratio': None if ratio is None else round(ratio, 3)})
            if c2 == c1:
                continue
            timed = time_group(x[int(a * SR):int(b * SR)], texts[c1:c2], 'te', quiet, offset=a)
            for c, (start, end, verses, m) in zip(range(c1, c2), timed):
                result['chapters'][chapters[c]['chapter']] = {'start': start, 'end': end, 'verses': verses, 'metrics': m}
        with open(out + '.tmp', 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, separators=(',', ':'))
        os.replace(out + '.tmp', out)
        ms = [c['metrics'] for c in result['chapters'].values()]
        n = sum(len(c['verses']) for c in result['chapters'].values())
        unsure = sum(len(m['unsure']) for m in ms)
        weak = [k for k, c in result['chapters'].items() if c['metrics']['on_pause'] < 0.8 or len(c['metrics']['unsure']) > max(3, 0.2 * len(c['verses']))]
        grouped = sum(1 for g in result['groups'] if len(g['chapters']) > 1)
        interludes = sum(1 for g in result['groups'] if not g['chapters'])
        odd = [g['chapters'] for g in result['groups'] if g['ratio'] and not 0.75 <= g['ratio'] <= 1.33]
        return out, (f'{time.time() - t0:.0f}s {book} {first}-{chapters[-1]["chapter"]}: {n} verses, joins {len(joins)}, '
                     f'{grouped} stretches with several chapters, {interludes} interludes' + (f', ODD LENGTHS {odd}' if odd else '') + ', '
                     f'on pause {np.mean([m["on_pause"] for m in ms]):.3f}, unsure {unsure}' + (f', CHECK chapters {weak}' if weak else ''))
    except Exception:
        return member, 'error ' + traceback.format_exc(limit=3).replace('\n', ' | ')


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = []
    for zpath in ZIPS:
        with zipfile.ZipFile(zpath) as z:
            jobs += [(zpath, i.filename) for i in z.infolist() if i.filename.lower().endswith('.mp3')]
    # the longest first, so the last to finish are short
    sizes = {}
    for zpath in ZIPS:
        with zipfile.ZipFile(zpath) as z:
            sizes.update({i.filename: i.file_size for i in z.infolist()})
    jobs.sort(key=lambda j: -sizes[j[1]])
    print(f'{len(jobs)} files', flush=True)
    done = 0
    with Pool(WORKERS) as pool:
        for out, note in pool.imap_unordered(job, jobs):
            done += 1
            print(f'{done}/{len(jobs)} {os.path.basename(out)} {note}', flush=True)
    print('all done', flush=True)


if __name__ == '__main__':
    main()
