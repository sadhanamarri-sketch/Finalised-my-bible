"""Finds how much each scene must be dimmed for its text to stay readable, and prints it as JSON.

    python3 fit_veils.py SCENES_DIR json > veils.json      (or "report" for a table)

The scene is scaled to a 390x844 card, the veil laid over it in sRGB (a fixed gradient under the top
and bottom bars, plus a per-scene even layer k), and the contrast between each text color and the
pixels behind it checked wherever a verse can sit, from just under the top bar to just above the
bottom bar. The worst 3% of pixels count. Targets: the verse 4.5:1, context lines and the reference
3:1, against the Dark theme's text (the dimmest of the dark themes) and Sepia's (the lightest light one).
The app adds a small margin on top while scenes move (VerseScroll's SceneLayer.drawVeil).
"""
import json
import os
import sys

import numpy as np
from PIL import Image

bg_dir = sys.argv[1]
mode = sys.argv[2] if len(sys.argv) > 2 else 'report'
W, H = 390, 844
BAND = (int(H * 0.09), int(H * 0.86))
X0, X1 = 24, W - 24


def srgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def lum(c):
    c = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return c[..., 0] * 0.2126 + c[..., 1] * 0.7152 + c[..., 2] * 0.0722


def contrast(la, lb):
    hi, lo = np.maximum(la, lb), np.minimum(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def profile(stops):
    ys = np.linspace(0, 1, H)
    return np.interp(ys, [p for p, _ in stops], [a for _, a in stops])


# Veils: a fixed gradient for the bars at the top and bottom, plus a per-scene even layer `k`.
FAMILIES = {
    'dark': dict(color=srgb('#0a0808'), base=profile([(0, .45), (.14, .08), (.8, .08), (1, .7)]),
                 # text colors in scenes mode (ink, soft at 80% of ink, gold)
                 text={'ink': ('#E8D8C8', 1.0), 'soft': ('#E8D8C8', 0.8), 'gold': ('#E8C27A', 1.0)}),  # the 'Dark' theme's ink: the dimmer of the two dark themes
    'light': dict(color=srgb('#faf7f0'), base=profile([(0, .6), (.14, .12), (.8, .12), (1, .8)]),
                  text={'ink': ('#3D312A', 1.0), 'soft': ('#3D312A', 0.8), 'gold': ('#5E4813', 1.0)}),  # Sepia's ink: the lightest of the three light themes
}
TARGET = {'ink': 4.5, 'soft': 3.0, 'gold': 3.0}


def card(path):
    im = Image.open(path).convert('RGB')
    iw, ih = im.size
    scale = max(W / iw, H / ih)
    im = im.resize((round(iw * scale), round(ih * scale)), Image.LANCZOS)
    left = (im.width - W) // 2
    top = int((im.height - H) * 0.6)
    return np.asarray(im.crop((left, top, left + W, top + H)), dtype=np.float64) / 255


def worst(img, fam, k):
    f = FAMILIES[fam]
    a = 1 - (1 - f['base']) * (1 - k)
    out = img * (1 - a[:, None, None]) + f['color'] * a[:, None, None]
    region = out[BAND[0]:BAND[1], X0:X1]
    lb = lum(region)
    res = {}
    for name, (hexc, op) in f['text'].items():
        tc = srgb(hexc) * op + region * (1 - op)  # text drawn at partial opacity mixes with what's behind it
        res[name] = float(np.percentile(contrast(lum(tc), lb), 3))
    return res


def ok(img, fam, k):
    r = worst(img, fam, k)
    return all(r[n] >= TARGET[n] for n in TARGET), r


def needed_k(img, fam):
    # More dimming only ever raises contrast, so the smallest k that passes can be found by bisection.
    good, r = ok(img, fam, 0.0)
    if good:
        return 0.0, r
    lo, hi = 0.0, 0.95
    for _ in range(8):
        mid = (lo + hi) / 2
        if ok(img, fam, mid)[0]:
            hi = mid
        else:
            lo = mid
    k = float(np.ceil(hi * 100) / 100)
    return round(k, 2), worst(img, fam, k)


names = sorted(n for n in os.listdir(bg_dir) if n.endswith('.webp'))
rows = []
for n in names:
    img = card(os.path.join(bg_dir, n))
    row = {'id': n[:-5]}
    for fam in FAMILIES:
        k, r = needed_k(img, fam)
        row[fam] = k
        row[fam + '_at_k'] = r
    rows.append(row)

if mode == 'json':
    print(json.dumps({r['id']: {'kd': r['dark'], 'kl': r['light']} for r in rows}))
else:
    print(f"{'scene':22s} {'dark k':>7s} {'light k':>8s}")
    for r in rows:
        print(f"{r['id']:22s} {r['dark']:7.2f} {r['light']:8.2f}")
