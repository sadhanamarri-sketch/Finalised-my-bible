"""Where a chapter's heading ("chapter seven", "psalm twelve") is said in a stretch of a recording, as
pocketsphinx's keyword spotting hears it: the time it's heard best, or nothing. For where the aligner
went wrong at a chapter's end (chain_align.py). Run with a Python that has pocketsphinx.
usage: find_heading.py <audio> <from s> <to s> <phrase> [<phrase> ...]"""
import sys, subprocess
from pocketsphinx import Decoder

path, a, b = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
raw = subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-ss', f'{a:.2f}', '-i', path, '-t', f'{b - a:.2f}',
                      '-ac', '1', '-ar', '16000', '-f', 's16le', 'pipe:1'], capture_output=True, check=True).stdout
best = None
for phrase in sys.argv[4:]:
    decoder = Decoder(samprate=16000, keyphrase=phrase, kws_threshold=1e-30)
    decoder.start_utt()
    decoder.process_raw(raw, full_utt=True)
    decoder.end_utt()
    for seg in decoder.seg():
        if best is None or seg.prob > best[1]: best = (a + seg.start_frame / 100, seg.prob)
print(f'{best[0]:.2f}' if best else '')
