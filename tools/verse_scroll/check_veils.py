"""Checks every app theme against every scene with its fitted dimming, and prints the lowest contrast.

    python3 check_veils.py SCENES_DIR veils.json
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
bg, kfile = sys.argv[1], sys.argv[2]
sys.argv = [sys.argv[0], bg, 'none']
import fit_veils as vf  # noqa: E402

K = json.load(open(kfile))
THEMES = {  # each theme's ink (onBackground); the quieter text is ink at 80%, the gold as VsColors.onScene sets it
    'classicdark': ('dark', '#EDE8DD'), 'dark': ('dark', '#E8D8C8'),
    'paper': ('light', '#2C221E'), 'sepia': ('light', '#3D312A'), 'light': ('light', '#1F1F1F'),
}
GOLD = {'dark': '#E8C27A', 'light': '#5E4813'}
worst = {t: {'ink': 99, 'soft': 99, 'gold': 99} for t in THEMES}
for n in sorted(x for x in os.listdir(bg) if x.endswith('.webp')):
    img = vf.card(os.path.join(bg, n))
    sid = n[:-5]
    for theme, (fam, ink) in THEMES.items():
        f = vf.FAMILIES[fam]
        k = K[sid]['kd' if fam == 'dark' else 'kl']
        a = 1 - (1 - f['base']) * (1 - k)
        out = img * (1 - a[:, None, None]) + f['color'] * a[:, None, None]
        region = out[vf.BAND[0]:vf.BAND[1], vf.X0:vf.X1]
        lb = vf.lum(region)
        for name, (hexc, op) in {'ink': (ink, 1.0), 'soft': (ink, 0.8), 'gold': (GOLD[fam], 1.0)}.items():
            tc = vf.srgb(hexc) * op + region * (1 - op)
            c = float(np.percentile(vf.contrast(vf.lum(tc), lb), 3))
            worst[theme][name] = min(worst[theme][name], c)
print(f'lowest contrast over all {len(K)} scenes (worst 3% of pixels behind the text):')
for theme, w in worst.items():
    print(f"  {theme:12s} verse {w['ink']:4.1f}   context lines {w['soft']:4.1f}   reference {w['gold']:4.1f}")
