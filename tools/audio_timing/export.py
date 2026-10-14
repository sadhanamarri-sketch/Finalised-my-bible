"""Writes the timings for the app: one file per recording, every chapter's file, where the chapter
starts and ends in it, and each verse's [start, end] in seconds; verses where the matching's runs
disagreed are listed as unsure.

usage: export.py kjv <results dir> <kjv.tsv> <out.json>
       export.py te <results dir> <kjv.tsv> <out.json>
"""
import sys, os, json, glob

kind, res, kjv, out = sys.argv[1:5]
order = []
for line in open(kjv, encoding='utf-8'):
    b = line.split('\t', 1)[0]
    if b not in order: order.append(b)

chapters = {}
if kind == 'kjv':
    for p in glob.glob(os.path.join(res, '*.json')):
        r = json.load(open(p, encoding='utf-8'))
        entry = {'file': r['file'], 'start': 0.0, 'end': r['duration'], 'verses': r['verses']}
        if r['metrics']['unsure']: entry['unsure'] = r['metrics']['unsure']
        chapters[(r['book'], r['chapter'])] = entry
    recording = ("Faith Comes By Hearing's King James Version, narrated (ENGKJVO1DA, ENGKJVN1DA): "
                 "one file per chapter.")
else:
    for p in glob.glob(os.path.join(res, '*.json')):
        r = json.load(open(p, encoding='utf-8'))
        for c, ch in r['chapters'].items():
            entry = {'file': r['file'], 'start': ch['start'], 'end': ch['end'], 'verses': ch['verses']}
            if ch['metrics']['unsure']: entry['unsure'] = ch['metrics']['unsure']
            chapters[(r['book'], int(c))] = entry
    recording = ('Telugu Audio Bible, YGFM (Apostle Ratnaker), Bible Society of India Old Version: one file '
                 'per book (Psalms in two), chapters read one after another.')

data = {
    'recording': recording,
    'how': ("espeak's reading of the app's text matched to the narration by its sound (MFCC, dynamic "
            "time warping) at the narrator's pace and a little slower and faster; each verse's start "
            "moved onto the narrator's pause, the three voting, then chosen among nearby pauses by verse "
            "length. Seconds from the start of the file."),
    'chapters': {f'{b} {c}': chapters[(b, c)] for b, c in sorted(chapters, key=lambda k: (order.index(k[0]), k[1]))},
}
with open(out, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
print(f'{len(chapters)} chapters, {sum(len(e["verses"]) for e in chapters.values())} verses, {os.path.getsize(out) / 1e6:.2f} MB')
