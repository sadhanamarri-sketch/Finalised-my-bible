"""Verse timings from the forced aligner's word timings: each chapter was aligned as one utterance,
its heading (an unknown word) and then its verses' words in order, so each verse starts just before its
first word and ends just after its last. The aligner's words are lined up with the text's as a diff
does, as it can split or repeat a word. A verse is unsure where this and the earlier timing (by pauses)
disagree by over 0.3 s. usage: words_to_verses.py <aligned json dir> <results dir> <telugu json dir>
<out dir: one json per book, like the results>"""
import sys, os, json, glob, difflib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telugu_g2p import clean, phones

def text_words(text):
    """A verse's words as the corpus has them (build_corpus.words)."""
    return [w for w in (clean(t) for t in text.split()) if w and phones(w)]

LEAD, TAIL = 0.05, 0.10      # a verse starts just before its first word and ends just after its last

def entries(d):
    """[(start, end, word)] from an MFA json output."""
    tiers = d['tiers']
    t = tiers.get('words') or next(v for k, v in tiers.items() if 'word' in k.lower())
    return [(e[0], e[1], e[2]) for e in t['entries']]

def main(aligned, res, te_dir, out):
    os.makedirs(out, exist_ok=True)
    report = []
    for p in sorted(glob.glob(os.path.join(res, '*.json'))):
        name = os.path.basename(p)[:-5]
        a = os.path.join(aligned, name + '.json')
        if not os.path.exists(a): report.append((name, 'not aligned')); continue
        r = json.load(open(p, encoding='utf-8'))
        ws = entries(json.load(open(a, encoding='utf-8')))
        text = {c['chapter']: [v['text'] for v in c['verses']] for c in json.load(open(os.path.join(te_dir, r['book'] + '.json'), encoding='utf-8'))['chapters']}
        bad = []
        for c, ch in r['chapters'].items():
            inside = [w for w in ws if ch['start'] - 0.01 <= w[0] and w[1] <= ch['end'] + 0.01 and w[2] not in ('', '<eps>', 'sil', 'sp')]
            expected = [(k, w) for k, t in enumerate(text[c]) for w in text_words(t)]
            got = [w for w in inside if not w[2].startswith('zzheading') and w[2] not in ('<unk>', 'spn')]
            # line the aligner's words up with the text's, as a diff does (it can split or repeat a word)
            sm = difflib.SequenceMatcher(None, [e[1] for e in expected], [g[2] for g in got], autojunk=False)
            first, last, matched = {}, {}, 0
            for i, j, size in sm.get_matching_blocks():
                for d in range(size):
                    k = expected[i + d][0]; s, e, _ = got[j + d]
                    first.setdefault(k, s); last[k] = e; matched += 1
            n = len(text[c])
            if matched < 0.95 * len(expected) or len(first) < n:
                bad.append(c); continue
            starts = [round(max(ch['start'], first[k] - LEAD), 2) for k in range(n)]
            ends = [round(min(last[k] + TAIL, starts[k + 1] - 0.02) if k + 1 < n else min(last[k] + TAIL, ch['end']), 2) for k in range(n)]
            ch['before_words'] = ch['verses']
            ch['verses'] = [[s, e] for s, e in zip(starts, ends)]
            ch['metrics']['words'] = True
            # unsure now means the two ways of timing (by pauses, by words) disagree by over 0.3 s
            ch['metrics']['matching_unsure'] = ch['metrics']['unsure']
            ch['metrics']['unsure'] = [k + 1 for k in range(n) if abs(starts[k] - ch['before_words'][k][0]) > 0.3]
        r['word_aligned'] = [c for c in r['chapters'] if c not in bad]
        json.dump(r, open(os.path.join(out, name + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
        report.append((name, f'{len(r["chapters"]) - len(bad)} of {len(r["chapters"])} chapters by their words' + (f'; not: {bad[:10]}' if bad else '')))
    for n, m in report: print(n, m)

if __name__ == '__main__':
    main(*sys.argv[1:5])
