"""The dramatized KJV's timings (export.py's) with the verses checked by ear folded in
(checked_by_ear.json: each verse the aligner was unsure of, played, then passed or given its start).

A checked verse starts
- where it was moved to, a quarter second at a time from the aligner's start;
- where the aligner had it, if a tap came within a tap's reach of that (taps come up to half a
  second early, and the aligner's start is on the first word);
- just after the tap (TAP_LATE), where the aligner had it wrong;
- where the aligner had it, if passed.
(Taps along a run of verses land a whole number of quarter seconds from the aligner's now and then by
chance: a run of three or more is taken as tapped along unless most of it is in quarter seconds.)
A checked verse ends just after its last word where it and the next verse kept the aligner's starts,
else where the next one starts, as it was played on the page. A chapter in chapter_starts starts
there, and the one before ends there. Checked verses are no longer unsure.
usage: fold_checked.py <timings.json> <checked_by_ear.json> <out.json>
"""
import sys, json

NEAR = (-0.2, 0.65)   # a tap up to this much (seconds) before the aligner's start agrees with it
TAP_LATE = 0.1        # a start tapped where the aligner was wrong is taken this much after the tap
GAP = 0.02            # a verse ends this long before the next one starts
HOW = ' Verses it was unsure of were checked by ear, and given their starts by hand where it was wrong.'


def nudged(start, aligned):
    """Moved from the aligner's start a quarter second at a time."""
    q = (aligned - start) / 0.25
    return round(q) != 0 and abs(q - round(q)) < 0.03


def moved_by_hand(aligned, taps):
    """The verses given starts a quarter second at a time, among those given starts."""
    out, run = set(), []
    for v in sorted(taps) + [None]:
        if run and v != run[-1] + 1:
            quarters = {u for u in run if nudged(taps[u], aligned[u - 1][0])}
            if len(run) < 3 or 2 * len(quarters) >= len(run): out |= quarters
            run = []
        if v is not None: run.append(v)
    return out


def fold(ch, taps):
    """The chapter's verses with its checks in: [[start, end]]."""
    aligned = ch['verses']
    n = len(aligned)
    by_hand = moved_by_hand(aligned, taps)
    starts, moved, wrong = [], set(), set()
    for v in range(1, n + 1):
        a, t = aligned[v - 1][0], taps.get(v)
        if t is None or (v not in by_hand and NEAR[0] <= a - t <= NEAR[1]):
            starts.append(a)
        elif v in by_hand:
            starts.append(t)
            moved.add(v)
        else:
            starts.append(round(t + TAP_LATE, 2))
            moved.add(v)
            wrong.add(v)
    verses = []
    for v in range(1, n + 1):
        until = starts[v] - GAP if v < n else ch['end']
        own = aligned[v - 1][1]
        end = until if v in wrong or (v + 1) in moved else min(own, until)
        verses.append([round(starts[v - 1], 2), round(end, 2)])
    return verses


def main(timings, checked, out):
    d = json.load(open(timings, encoding='utf-8'))
    c = json.load(open(checked, encoding='utf-8'))
    names = list(d['chapters'])
    for name, at in c['chapter_starts'].items():
        ch = d['chapters'][name]
        ch['start'] = at
        i = names.index(name)
        before = d['chapters'][names[i - 1]] if i else None
        if before and before['file'] == ch['file']:
            before['end'] = at
            before['verses'][-1][1] = min(before['verses'][-1][1], at)
    for name, fix in c['chapters'].items():
        ch = d['chapters'][name]
        taps = {int(v): t for v, t in fix['starts'].items()}
        ch['verses'] = fold(ch, taps)
        left = sorted(set(ch.get('unsure', [])) - set(fix['ok']) - set(taps))
        if left: ch['unsure'] = left
        else: ch.pop('unsure', None)
    for name, ch in d['chapters'].items():
        last = ch['start']
        for v, (s, e) in enumerate(ch['verses'], 1):
            if s < last - 1e-6 or e <= s: raise ValueError(f'{name}:{v} [{s}, {e}] overlaps or ends before it starts')
            last = e
        if last > ch['end'] + 1e-6: raise ValueError(f'{name} ends after its chapter')
    if HOW not in d['how']: d['how'] += HOW
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
    unsure = sum(len(ch.get('unsure', [])) for ch in d['chapters'].values())
    print(f'{sum(len(f["ok"]) + len(f["starts"]) for f in c["chapters"].values())} checks in {len(c["chapters"])} chapters; {unsure} verses unsure')


if __name__ == '__main__':
    main(*sys.argv[1:4])
