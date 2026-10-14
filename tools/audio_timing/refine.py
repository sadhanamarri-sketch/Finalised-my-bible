"""The last step: each verse's start chosen among the narrator's pauses near it, by what the matching
found (held tightly where its runs agreed), how long the pause is (a verse usually follows a longer
one), and whether each verse then takes about as long as its text says."""
import re
import numpy as np


def refine(starts, unsure, lengths, quiet, end, sure_sd=0.35, unsure_sd=4.0, reach=8.0, pause_weight=2.0):
    """[starts]: each verse's start (s) from the matching; [unsure]: indexes whose runs disagreed;
    [lengths]: each verse's letters; [quiet]: (start, end) quiet stretches in the same seconds;
    [end]: where the last verse ends. Returns the chosen starts."""
    n = len(starts)
    if n < 3:
        return list(starts)
    starts = np.asarray(starts, float)
    L = np.asarray(lengths, float)
    durs = np.diff(np.append(starts, end))
    # The narrator's pace in this chapter, from the verses that look right.
    ok = [k for k in range(n) if k not in unsure and durs[k] > 0.3]
    pace = float(np.median(durs[ok] / np.maximum(L[ok], 1))) if len(ok) >= 3 else float(np.median(durs / np.maximum(L, 1)))
    mu = pace * L
    # Verses too short or long for their text count as unsure, and so do their neighbours' starts.
    doubt = set(unsure)
    for k in range(n):
        if durs[k] < 0.35 * mu[k] or durs[k] > 2.8 * mu[k] + 2:
            doubt.update({k, k + 1})
    onsets = np.array([b - 0.08 for a, b in quiet], float)
    plen = np.array([b - a for a, b in quiet], float)
    cands, scores = [], []
    for k in range(n):
        sd = unsure_sd if k in doubt else sure_sd
        w = reach if k in doubt else 1.2
        pick = np.where(np.abs(onsets - starts[k]) <= w)[0]
        c = [starts[k]] + [onsets[i] for i in pick]
        p = [0.0] + [plen[i] for i in pick]
        c = np.array(c); p = np.array(p)
        cands.append(c)
        scores.append(-(c - starts[k]) ** 2 / (2 * sd ** 2) + pause_weight * np.log1p(p))
    # Best path: each start after the one before, verses as long as their text.
    best = [scores[0]]
    back = []
    for k in range(1, n):
        d = cands[k][:, None] - cands[k - 1][None, :]
        sd = 0.2 * mu[k - 1] + 0.6
        trans = -(d - mu[k - 1]) ** 2 / (2 * sd ** 2)
        trans[d < 0.4] = -1e9
        tot = best[-1][None, :] + trans
        j = np.argmax(tot, axis=1)
        best.append(scores[k] + tot[np.arange(len(j)), j])
        back.append(j)
    i = int(np.argmax(best[-1]))
    out = [0.0] * n
    for k in range(n - 1, -1, -1):
        out[k] = float(cands[k][i])
        if k > 0:
            i = int(back[k - 1][i])
    return out


def letters(text):
    return len(re.sub(r'\s+', '', text))
