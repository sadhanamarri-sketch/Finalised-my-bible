"""Inputs for the Montreal Forced Aligner, from the current Telugu timings:
  dict.txt            every word of the app's Telugu text, with its phones
  train/<book>.TextGrid  single verses whose boundaries look solid, as utterances (to learn the voice)
  align/<book>.TextGrid  every chapter as one utterance: the heading (an unknown word) and its verses
usage: build_corpus.py <results dir> <telugu json dir> <out dir> <training verses wanted>"""
import sys, os, json, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telugu_g2p import clean, phones

res, te_dir, out, want = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
HEADING = 'zzheading'
os.makedirs(os.path.join(out, 'train'), exist_ok=True); os.makedirs(os.path.join(out, 'align'), exist_ok=True)

def words(text):
    return [w for w in (clean(t) for t in text.split()) if w and phones(w)]

def textgrid(path, end, intervals):
    """intervals: [(start, end, text)] sorted, not overlapping; the gaps between are left empty."""
    items, at = [], 0.0
    for a, b, t in intervals:
        a = max(a, at)                       # no overlaps (rounding can make two touch)
        if a > at + 1e-3: items.append((at, a, ''))
        items.append((a, b, t)); at = b
    if end > at + 1e-3: items.append((at, end, ''))
    lines = ['File type = "ooTextFile"', 'Object class = "TextGrid"', '', 'xmin = 0', f'xmax = {end:.3f}', 'tiers? <exists>', 'size = 1', 'item []:',
             '    item [1]:', '        class = "IntervalTier"', '        name = "narrator"', '        xmin = 0', f'        xmax = {end:.3f}',
             f'        intervals: size = {len(items)}']
    for i, (a, b, t) in enumerate(items, 1):
        lines += [f'        intervals [{i}]:', f'            xmin = {a:.3f}', f'            xmax = {b:.3f}', f'            text = "{t}"']
    open(path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

vocab = set()
for p in glob.glob(os.path.join(te_dir, '*.json')):
    for c in json.load(open(p, encoding='utf-8'))['chapters']:
        for v in c['verses']: vocab.update(words(v['text']))
with open(os.path.join(out, 'dict.txt'), 'w', encoding='utf-8') as f:
    for w in sorted(vocab): f.write(w + '\t' + ' '.join(phones(w)) + '\n')

files = sorted(glob.glob(os.path.join(res, '*.json')))
candidates = []
for p in files:
    r = json.load(open(p, encoding='utf-8'))
    text = {c['chapter']: [v['text'] for v in c['verses']] for c in json.load(open(os.path.join(te_dir, r['book'] + '.json'), encoding='utf-8'))['chapters']}
    chapters = []
    for c, ch in r['chapters'].items():
        vs = ch['verses']; m = ch['metrics']
        doubt = set(m['unsure']) | set(m.get('refined', [])) | set(m.get('voice_moved', []))
        chapters.append((ch['start'], ch['end'], HEADING + ' ' + ' '.join(w for t in text[c] for w in words(t))))
        for k in range(1, len(vs) - 1):
            if {k + 1, k + 2} & doubt: continue
            before, after = vs[k][0] - vs[k - 1][1], vs[k + 1][0] - vs[k][1]
            if before < 0.3 or after < 0.3 or not 2 <= vs[k][1] - vs[k][0] <= 25: continue
            ws = words(text[c][k])
            if ws: candidates.append((os.path.basename(p)[:-5], vs[k][0] - min(0.4, before / 2), vs[k][1] + min(0.4, after / 2), ' '.join(ws)))
    name = os.path.basename(p)[:-5]
    textgrid(os.path.join(out, 'align', name + '.TextGrid'), r['duration'], sorted(chapters))
step = max(1, len(candidates) // want)
train = candidates[::step][:want]
by_book = {}
for b, a, e, t in train: by_book.setdefault(b, []).append((a, e, t))
durations = {os.path.basename(p)[:-5]: json.load(open(p, encoding='utf-8'))['duration'] for p in files}
for b, ivs in by_book.items():
    textgrid(os.path.join(out, 'train', b + '.TextGrid'), durations[b], sorted(ivs))
hours = sum(e - a for _, a, e, _ in train) / 3600
print(f'{len(vocab)} words in the dictionary; {len(candidates)} solid verses, {len(train)} for training ({hours:.1f} h) in {len(by_book)} books; {len(files)} books to align')
