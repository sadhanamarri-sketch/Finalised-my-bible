"""Paints the scenes into OUT_DIR as WebP (1080x2340), with meta.json (where each scene's moving touches
go) and numbered contact sheets for review.

    python3 render_scenes.py OUT_DIR [comma,separated,ids]
"""
import json
import os
import sys
from multiprocessing import Pool

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenes  # noqa: E402
from scene_list import SCENES  # noqa: E402

OUT = sys.argv[1]
ONLY = set(sys.argv[2].split(',')) if len(sys.argv) > 2 else None


def work(spec):
    kind, pal, seed = spec
    name = f'{kind}-{pal}-{seed}'
    im = scenes.render(kind, pal, seed)
    im.save(os.path.join(OUT, name + '.webp'), 'WEBP', quality=78, method=6)
    meta = dict(scenes.META)
    for k, v in meta.items():
        if isinstance(v, float):
            meta[k] = round(v, 4)
        elif isinstance(v, list) and v and all(isinstance(x, float) for x in v):
            meta[k] = [round(x, 4) for x in v]
    return name, meta


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    todo = [s for s in SCENES if ONLY is None or f'{s[0]}-{s[1]}-{s[2]}' in ONLY]
    with Pool(4) as pool:
        results = pool.map(work, todo)
    mpath = os.path.join(OUT, 'meta.json')
    meta = json.load(open(mpath)) if os.path.exists(mpath) else {}
    meta.update(dict(results))
    order = [f'{k}-{p}-{s}' for k, p, s in SCENES]
    json.dump({n: meta[n] for n in order if n in meta}, open(mpath, 'w'), separators=(',', ':'))
    total = sum(os.path.getsize(os.path.join(OUT, n + '.webp')) for n in order if os.path.exists(os.path.join(OUT, n + '.webp')))
    print(f'{len(results)} rendered; all scenes {total / 1024 / 1024:.2f} MB')
    tw, th = 200, 433
    for sheet in range((len(order) + 24) // 25):
        names = [n for n in order[sheet * 25:(sheet + 1) * 25] if os.path.exists(os.path.join(OUT, n + '.webp'))]
        im = Image.new('RGB', (5 * (tw + 6) + 6, 5 * (th + 6) + 6), (30, 28, 26))
        d = ImageDraw.Draw(im)
        for i, n in enumerate(names):
            t = Image.open(os.path.join(OUT, n + '.webp')).convert('RGB').resize((tw, th), Image.LANCZOS)
            r, c = divmod(i, 5)
            x, y = 6 + c * (tw + 6), 6 + r * (th + 6)
            im.paste(t, (x, y))
            d.rectangle([x, y, x + 34, y + 18], fill=(0, 0, 0))
            d.text((x + 4, y + 3), str(sheet * 25 + i + 1), fill=(255, 255, 255))
        im.save(os.path.join(OUT, f'sheet_{sheet + 1}.png'))
