"""Each Telugu book file as 16 kHz mono FLAC, named by its results file (01_001.flac ...)."""
import sys, os, json, glob, zipfile, subprocess
from multiprocessing import Pool
res, out, zips = sys.argv[1], sys.argv[2], sys.argv[3:]
members = {}
for z in zips:
    for i in zipfile.ZipFile(z).infolist(): members.setdefault(os.path.basename(i.filename), (z, i.filename))
def job(p):
    r = json.load(open(p, encoding='utf-8'))
    dst = os.path.join(out, os.path.basename(p)[:-5] + '.flac')
    if os.path.exists(dst): return dst, 'kept'
    z, m = members[r['file']]
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-i', 'pipe:0', '-ac', '1', '-ar', '16000',
                    '-sample_fmt', 's16', dst + '.tmp.flac'], input=zipfile.ZipFile(z).read(m), check=True)
    os.replace(dst + '.tmp.flac', dst)
    return dst, 'made'
if __name__ == '__main__':
    with Pool(4) as pool:
        for d, how in pool.imap_unordered(job, sorted(glob.glob(os.path.join(res, '*.json')), key=os.path.getsize, reverse=True)):
            print(os.path.basename(d), how, flush=True)
