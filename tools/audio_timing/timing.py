"""Verse timings for a recorded chapter.

espeak reads the chapter's heading and verses (one call, an exact silent gap after each) at the
narrator's pace, and a little slower and faster; each reading is matched to the recording by its
sound (MFCC features every 20 ms, dynamic time warping in a band around the straight line from
start to end); each verse's start is moved onto the narrator's nearest pause, and the three
readings vote. Each verse ends where the pause before the next one begins. refine.py then chooses
among nearby pauses by verse length where the readings disagreed.
"""
import os, re, ctypes, subprocess
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 16000
STEP = 0.020          # seconds per feature frame
GAP = 1.5             # espeak's gap after each text, in seconds
KEEP_GAP = 0.25       # what's kept of it in the reading that's matched

lib = ctypes.CDLL(os.path.join(HERE, 'libdtw2.so'))
_ip = ctypes.POINTER(ctypes.c_int)
_fp = ctypes.POINTER(ctypes.c_float)
lib.dtw_path.argtypes = [_fp, ctypes.c_int, _fp, ctypes.c_int, ctypes.c_int, _ip, _ip, _ip, _ip, _ip]
lib.dtw_path.restype = ctypes.c_int


# ---- audio ----

def decode(data_or_path, start=None, dur=None):
    """A recording (a path, or an MP3's bytes) as 16 kHz mono floats."""
    cmd = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin']
    if start is not None: cmd += ['-ss', f'{start:.3f}']
    cmd += ['-i', 'pipe:0' if isinstance(data_or_path, (bytes, bytearray)) else data_or_path]
    if dur is not None: cmd += ['-t', f'{dur:.3f}']
    cmd += ['-ac', '1', '-ar', str(SR), '-f', 's16le', 'pipe:1']
    raw = subprocess.run(cmd, check=True, capture_output=True,
                         input=data_or_path if isinstance(data_or_path, (bytes, bytearray)) else None).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768


def digital_silences(x, thr=-70.0, min_len=0.05):
    """Stretches of digital silence (start s, end s), where files were joined end to end."""
    n = len(x) // 160
    db = np.empty(n, np.float32)
    for s in range(0, n, 200000):
        e = min(n, s + 200000)
        db[s:e] = 20 * np.log10(np.sqrt(np.mean(x[s * 160:e * 160].reshape(e - s, 160) ** 2, axis=1)) + 1e-9)
    q = np.concatenate(([0], (db < thr).astype(np.int8), [0]))
    d = np.diff(q)
    return [(a / 100, b / 100) for a, b in zip(np.where(d == 1)[0], np.where(d == -1)[0]) if (b - a) / 100 >= min_len]


def envelope(x, sr=SR):
    """Loudness in dB every 10 ms, smoothed over 50 ms."""
    hop = sr // 100
    n = len(x) // hop
    out = np.empty(n, np.float32)
    for s in range(0, n, 200000):
        e = min(n, s + 200000)
        seg = x[s * hop:e * hop].reshape(e - s, hop)
        out[s:e] = 20 * np.log10(np.sqrt(np.mean(seg.astype(np.float32) ** 2, axis=1)) + 1e-9)
    return np.convolve(out, np.ones(5) / 5, 'same')


def quiet_stretches(env, drop=12.0, min_len=0.12):
    """The quiet stretches (start s, end s): at least [drop] dB below the loud parts within 3 s."""
    n = len(env)
    if n == 0: return []
    loud = np.array([np.percentile(env[max(0, i - 300):i + 300], 80) for i in range(0, n, 50)])
    loud = np.repeat(loud, 50)[:n]
    q = env < loud - drop
    out, i = [], 0
    while i < n:
        if q[i]:
            j = i
            while j < n and q[j]: j += 1
            if (j - i) / 100 >= min_len: out.append((i / 100, j / 100))
            i = j
        else: i += 1
    return out


def snap_near(t, stretches, within=0.8):
    """The end of the quiet stretch [t] is in, else of the nearest within [within] s (where speech
    starts again), less 80 ms; else [t]. Returns (time, how far it moved or None)."""
    best = None
    for a, b in stretches:
        if b < t - within: continue
        if a > t + within: break
        d = 0.0 if a <= t <= b else min(abs(t - a), abs(t - b))
        if best is None or d < best[0]: best = (d, b)
    if best is None: return t, None
    s = max(0.0, best[1] - 0.08)
    return s, abs(s - t)


# ---- features ----

_banks = {}

def _bank(sr, nfft, nfilt=26):
    key = (sr, nfft)
    if key not in _banks:
        mel = lambda f: 2595 * np.log10(1 + f / 700)
        imel = lambda m: 700 * (10 ** (m / 2595) - 1)
        hz = imel(np.linspace(mel(60), mel(7600), nfilt + 2))
        freqs = np.arange(nfft // 2 + 1) * sr / nfft
        fb = np.zeros((nfilt, nfft // 2 + 1), np.float32)
        for k in range(1, nfilt + 1):
            l, c, r = hz[k - 1], hz[k], hz[k + 1]
            fb[k - 1] = np.clip(np.minimum((freqs - l) / (c - l), (r - freqs) / (r - c)), 0, None)
        _banks[key] = fb
    return _banks[key]

DCT = np.cos(np.pi / 26 * (np.arange(26)[None, :] + 0.5) * np.arange(13)[:, None]).astype(np.float32)

def mfcc(x, sr=SR, normalise=True):
    """12 cepstral coefficients every 20 ms (32 ms windows), normalised over the whole signal."""
    win, hop = round(0.032 * sr), round(STEP * sr)
    nfft = 1 << (win - 1).bit_length()
    fb = _bank(sr, nfft)
    x = np.append(x[:1], x[1:] - 0.97 * x[:-1]).astype(np.float32)
    n = 1 + (len(x) - win) // hop if len(x) >= win else 0
    out = np.empty((n, 12), np.float32)
    w = np.hamming(win).astype(np.float32)
    for s in range(0, n, 4096):
        e = min(n, s + 4096)
        idx = np.arange(win)[None, :] + hop * np.arange(s, e)[:, None]
        pw = np.abs(np.fft.rfft(x[idx] * w, nfft)) ** 2 / nfft
        out[s:e] = (np.log(pw @ fb.T + 1e-10) @ DCT.T)[:, 1:]
    return (out - out.mean(0)) / (out.std(0) + 1e-8) if normalise else out


# ---- espeak ----

def _escape(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

def synth(texts, voice, rate):
    """[texts] read by espeak; returns the reading (22050 Hz floats, each long gap cut down to
    KEEP_GAP with a little noise in it) and where each text starts in it, in seconds."""
    ssml = '<speak>' + ''.join(_escape(t) + f'<break time="{int(GAP * 1000)}ms"/>' for t in texts) + '</speak>'
    out = subprocess.run(['espeak-ng', '-m', '-v', voice, '-s', str(rate), '--stdout', '--stdin'],
                         input=ssml.encode('utf-8'), check=True, capture_output=True).stdout
    k = out.find(b'data')
    sr = int.from_bytes(out[24:28], 'little')
    x = np.frombuffer(out[k + 8:len(out) - ((len(out) - k - 8) % 2)], dtype=np.int16)
    # The gaps: runs of exact zeros at least 80% of GAP long.
    z = np.concatenate(([0], (x == 0).astype(np.int8), [0]))
    d = np.diff(z)
    starts, ends = np.where(d == 1)[0], np.where(d == -1)[0]
    gaps = [(s, e) for s, e in zip(starts, ends) if e - s >= 0.8 * GAP * sr]
    if len(gaps) != len(texts):
        raise RuntimeError(f'espeak gave {len(gaps)} gaps for {len(texts)} texts')
    pieces, at, offsets, prev = [], 0, [], 0
    keep = int(KEEP_GAP * sr)
    rng = np.random.default_rng(0)
    for s, e in gaps:
        seg = x[prev:s].astype(np.float32) / 32768
        offsets.append(at / sr)
        pieces.append(seg)
        pieces.append(rng.normal(0, 3e-4, keep).astype(np.float32))
        at += len(seg) + keep
        prev = e
    return np.concatenate(pieces), offsets, sr


# ---- matching ----

def _path(A, B, lo, hi):
    n, m = len(A), len(B)
    pi = np.empty(n + m, np.int32); pj = np.empty(n + m, np.int32); edge = ctypes.c_int(0)
    A = np.ascontiguousarray(A, np.float32); B = np.ascontiguousarray(B, np.float32)
    lo = np.ascontiguousarray(lo, np.int32); hi = np.ascontiguousarray(hi, np.int32)
    L = lib.dtw_path(A.ctypes.data_as(_fp), n, B.ctypes.data_as(_fp), m, A.shape[1],
                     lo.ctypes.data_as(_ip), hi.ctypes.data_as(_ip), pi.ctypes.data_as(_ip), pj.ctypes.data_as(_ip), ctypes.byref(edge))
    if L < 0: raise MemoryError('dtw')
    return pi[:L][::-1], pj[:L][::-1], edge.value

def match_diag(A, B, width_s=None):
    """For each frame of B (the reading), the first frame of A (the recording) matched to it, and how
    many cells of the path ran along its band's edge. Matched within a band around the straight line
    from start to end, [width_s] seconds either side (by default a fifth of the recording, between
    40 s and 90 s), or over the whole grid when that's small."""
    n, m = len(A), len(B)
    if n * m <= 60_000_000:
        lo, hi = np.zeros(n, np.int64), np.full(n, m - 1, np.int64)
    else:
        w = int((width_s if width_s else min(90.0, max(40.0, 0.2 * n * STEP))) / STEP)
        centre = np.arange(n) * (m - 1) / max(1, n - 1)
        lo = np.clip(np.floor(centre - w), 0, m - 1).astype(np.int64)
        hi = np.clip(np.ceil(centre + w), 0, m - 1).astype(np.int64)
        lo[0] = 0; hi[-1] = m - 1
    pi, pj, edge = _path(A, B, lo, hi)
    first = np.full(m, -1, np.int64)
    order = np.unique(pj, return_index=True)
    first[order[0]] = pi[order[1]]
    for j in range(1, m):
        if first[j] < 0: first[j] = first[j - 1]
    return first, edge


def time_chapter(real, texts, voice, quiet, offset=0.0, spread=(0.88, 1.0, 1.12), base=160):
    """Verse timings for a chapter's recording [real] (16 kHz floats), whose reading is [texts]: a
    heading, then each verse. [quiet]: the recording's quiet stretches (in seconds from [offset],
    where [real] starts in the file). espeak reads at the narrator's pace, and a little slower and
    faster. Returns ([start, end] per verse in file seconds, metrics)."""
    A = mfcc(real)
    syn, starts, ssr = synth(texts, voice, base)
    pace = min(max(base * (len(syn) / ssr) / (len(real) / SR), 80 / spread[0]), 400)
    rates = [int(round(pace * k)) for k in spread]
    votes, raws, edges = [], [], 0
    for rate in rates:
        syn, starts, ssr = synth(texts, voice, rate)
        B = mfcc(syn, ssr)
        first, edge = match_diag(A, B)
        edges += edge
        raw = [offset + first[min(int(s / STEP), len(first) - 1)] * STEP for s in starts]
        raws.append(raw)
        votes.append([snap_near(t, quiet)[0] for t in raw])
    votes, raws = np.array(votes), np.array(raws)
    starts = np.median(votes, axis=0)
    raw = np.median(raws, axis=0)
    end_of_real = offset + len(real) / SR
    verses = []
    for k in range(1, len(texts)):
        s = float(starts[k])
        if k + 1 < len(texts):
            nxt = float(starts[k + 1])
            before = [a for a, b in quiet if b - 0.1 <= nxt + 0.001 and b >= nxt - 0.1]
            e = before[0] + 0.05 if before and before[0] + 0.05 > s else nxt
        else:
            e = end_of_real
        verses.append([round(s, 2), round(e, 2)])
    # How sure: the rough-and-fine match's own verse starts landing in pauses, the readings agreeing.
    inside = sum(1 for t in raw[1:] if any(a - 0.25 <= t <= b + 0.25 for a, b in quiet if a <= t + 1 and b >= t - 1))
    spread = votes.max(0) - votes.min(0)
    unsure = [k for k in range(1, len(texts)) if spread[k] > 0.3]
    lengths = np.array([len(re.sub(r'\s+', '', t)) for t in texts[1:]], float)
    durs = np.array([e - s for s, e in verses])
    corr = float(np.corrcoef(durs[:-1], lengths[:-1])[0, 1]) if len(durs) >= 6 else None
    metrics = {'on_pause': round(inside / max(1, len(texts) - 1), 3), 'unsure': unsure, 'edge': edges, 'rates': rates,
               'corr': None if corr is None or np.isnan(corr) else round(corr, 3),
               'heading': round(float(starts[0]), 2)}
    return verses, metrics


def time_group(real, chapters, voice, quiet, offset=0.0, spread=(0.88, 1.0, 1.12), base=160):
    """Verse timings for a recording [real] of one or more chapters read one after another, with no
    join between them ([chapters]: each chapter's texts, its heading first). Matched as one reading,
    then split at the headings. Returns, per chapter, (start, end, [[start, end] per verse], metrics)
    in file seconds."""
    texts = [t for ch in chapters for t in ch]
    A = mfcc(real)
    syn, _, ssr = synth(texts, voice, base)
    pace = min(max(base * (len(syn) / ssr) / (len(real) / SR), 80 / spread[0]), 400)
    rates = [int(round(pace * k)) for k in spread]
    votes, raws, edges = [], [], 0
    for rate in rates:
        syn, starts, ssr = synth(texts, voice, rate)
        first, edge = match_diag(A, mfcc(syn, ssr))
        edges += edge
        raw = [offset + first[min(int(s / STEP), len(first) - 1)] * STEP for s in starts]
        raws.append(raw)
        votes.append([snap_near(t, quiet)[0] for t in raw])
    votes, raws = np.array(votes), np.array(raws)
    starts = np.median(votes, axis=0)
    raw = np.median(raws, axis=0)
    spread_ = votes.max(0) - votes.min(0)
    end_of_real = offset + len(real) / SR

    def end_before(k):
        """Where text k ends: where the pause before the next text begins (or the recording ends)."""
        if k + 1 >= len(texts): return end_of_real
        nxt = float(starts[k + 1])
        before = [a for a, b in quiet if b - 0.1 <= nxt + 0.001 and b >= nxt - 0.1]
        return before[0] + 0.05 if before and before[0] + 0.05 > starts[k] else nxt

    out, at = [], 0
    for c, ch in enumerate(chapters):
        h, n = at, len(ch)
        idx = range(h + 1, h + n)
        verses = [[round(float(starts[k]), 2), round(end_before(k), 2)] for k in idx]
        inside = sum(1 for k in idx if any(a - 0.25 <= raw[k] <= b + 0.25 for a, b in quiet if a <= raw[k] + 1 and b >= raw[k] - 1))
        lengths = np.array([len(re.sub(r'\s+', '', texts[k])) for k in idx], float)
        durs = np.array([e - s for s, e in verses])
        corr = float(np.corrcoef(durs[:-1], lengths[:-1])[0, 1]) if len(durs) >= 6 else None
        metrics = {'on_pause': round(inside / max(1, n - 1), 3), 'unsure': [k - h for k in idx if spread_[k] > 0.3],
                   'edge': edges, 'rates': rates, 'group': len(chapters),
                   'corr': None if corr is None or np.isnan(corr) else round(corr, 3),
                   'heading': round(float(starts[h]), 2)}
        start = offset if c == 0 else round(float(starts[h]) - 0.15, 2)
        end = round(end_before(h + n - 1), 2) if c + 1 < len(chapters) else round(end_of_real, 2)
        out.append((start, end, verses, metrics))
        at += n
    return out
