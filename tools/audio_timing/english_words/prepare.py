"""Gets the KJV Dramatized Audio ready for the aligner, from its zips as they came (one MP4 per book,
named "The Holy Bible - Book 01 - Genesis - KJV Dramatized Audio.mp4"):

  prepare.py books <zips dir> <work dir>
      <work>/members.json: each book's number, zip, file and name; <work>/mp4_lengths.json: each file's
      length from its movie header, as the app reads it; <work>/flac/NN.flac: its sound, 16 kHz mono.
  prepare.py words <kjv.tsv> <dictionary> <out oov.txt>
      the words said (the text's, the books' titles, the psalms' titles) that the aligner's dictionary
      hasn't, for its G2P model to spell out.
  prepare.py dictionary <dictionary> <oov.dict from G2P> <out dictionary>
      the two in one, in the aligner's dictionary's form, and "hundredand" (kjv_text.number).
"""
import sys, os, re, json, struct, zipfile, subprocess, tempfile
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kjv_text import words, load, said

BOOK = re.compile(r'Book (\d+) - (.+?) - KJV')


def movie_length(head):
    """Seconds from the movie header (mvhd), given an MP4's first bytes (its moov first, as these are)."""
    p = 0
    while p + 8 <= len(head):
        size, kind = struct.unpack('>I4s', head[p:p + 8])
        if size == 1: size = struct.unpack('>Q', head[p + 8:p + 16])[0]
        if kind == b'moov':
            q = head.find(b'mvhd', p) - 4
            scale, duration = struct.unpack('>IQ', head[q + 28:q + 40]) if head[q + 8] else struct.unpack('>II', head[q + 20:q + 28])
            return round(duration / scale, 3)
        p += size
    raise ValueError('no movie header near the start')


def to_flac(job):
    zp, member, dst = job
    if os.path.exists(dst): return
    # Out of its zip to a file first: an MP4's index can be at its end, which a pipe can't reach.
    with tempfile.NamedTemporaryFile(suffix='.mp4', dir=os.path.dirname(dst)) as tmp:
        tmp.write(zipfile.ZipFile(zp).read(member))
        tmp.flush()
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-i', tmp.name, '-vn', '-ac', '1',
                        '-ar', '16000', '-sample_fmt', 's16', dst + '.tmp.flac'], check=True)
    os.replace(dst + '.tmp.flac', dst)


def books(zips, work):
    members, lengths = {}, {}
    for zp in sorted(os.listdir(zips)):
        if not zp.lower().endswith('.zip'): continue
        z = zipfile.ZipFile(os.path.join(zips, zp))
        for i in z.infolist():
            m = BOOK.search(i.filename)
            if not m: continue
            members[int(m.group(1))] = (zp, i.filename, m.group(2))
            with z.open(i) as f:
                lengths[os.path.basename(i.filename)] = movie_length(f.read(3_000_000))
    if sorted(members) != list(range(1, 67)): raise ValueError(f'not 66 books: {sorted(members)}')
    os.makedirs(os.path.join(work, 'flac'), exist_ok=True)
    json.dump({n: members[n] for n in sorted(members)}, open(os.path.join(work, 'members.json'), 'w'), indent=0)
    json.dump(lengths, open(os.path.join(work, 'mp4_lengths.json'), 'w'), indent=0)
    jobs = [(os.path.join(zips, zp), member, os.path.join(work, 'flac', f'{n:02d}.flac')) for n, (zp, member, _) in sorted(members.items())]
    with Pool(4) as pool:
        for _ in pool.imap_unordered(to_flac, jobs): pass
    print(f'66 books, {sum(lengths.values()) / 3600:.1f} hours')


def missing_words(kjv_tsv, dictionary, out):
    known = set(line.split('\t', 1)[0] for line in open(dictionary, encoding='utf-8')) | {'hundredand'}
    oov = set()
    bible = load(kjv_tsv)
    every = [w for book, chapters in bible.items() for w in said(book, chapters)]
    every += [w for chapters in bible.values() for verses in chapters.values() for v in verses for w in words(v)]
    for w in every:
        if w in known: continue
        # A possessive whose word is known is said as that word and 's.
        base = w[:-2] if w.endswith("'s") else w.rstrip("'")
        if base != w and base in known: continue
        oov.add(base or w)
    open(out, 'w', encoding='utf-8').write('\n'.join(sorted(oov)) + '\n')
    print(f'{len(oov)} words to spell out')


def merge(dictionary, oov_dict, out):
    base = open(dictionary, encoding='utf-8').read().rstrip('\n') + '\n'
    sounds = {}
    for line in base.splitlines():
        w, _, rest = line.partition('\t')
        if w in ('hundred', 'and'): sounds.setdefault(w, []).append(rest.rsplit('\t', 1)[-1])
    with open(out, 'w', encoding='utf-8') as f:
        f.write(base)
        # The dictionary's form: the word, its odds (plain ones here), then its sounds.
        for line in open(oov_dict, encoding='utf-8'):
            w, _, phones = line.rstrip('\n').partition('\t')
            if w and phones: f.write(f'{w}\t1.0\t0.15\t1.0\t1.0\t{phones}\n')
        # "One hundred and nineteen", or "one hundred nineteen".
        for h in sounds['hundred']:
            for a in [''] + sounds['and']:
                f.write(f'hundredand\t1.0\t0.15\t1.0\t1.0\t{h} {a}'.rstrip() + '\n')


if __name__ == '__main__':
    {'books': books, 'words': missing_words, 'dictionary': merge}[sys.argv[1]](*sys.argv[2:])
