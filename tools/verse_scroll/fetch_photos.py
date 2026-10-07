"""Downloads Verse Scroll's background photos (photos.tsv) from Unsplash and makes them ready to ship.

    python3 fetch_photos.py OUT_DIR [CACHE_DIR]

Each photo is fetched from Unsplash's image service already cropped to a 1080x2340 phone screen around
its busiest part ("entropy" crop), kept in CACHE_DIR (if given) so it's only downloaded once, then
scaled to 900x1950, softened a touch and saved as WebP in OUT_DIR as <kind>-<first part of its id>.webp,
the name the app knows it by. Behind the veil and the verse the smaller size doesn't show, and it keeps
the 108 photos to about 12 MB.
"""
import io
import os
import sys
import urllib.request

from PIL import Image, ImageFilter

FETCH = (1080, 2340)
SHIP = (900, 1950)
QUALITY = 66
SOFTEN = 0.5

out_dir = sys.argv[1]
cache_dir = sys.argv[2] if len(sys.argv) > 2 else None
for d in (out_dir, cache_dir):
    if d:
        os.makedirs(d, exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))
rows = [line.rstrip('\n').split('\t') for line in open(os.path.join(here, 'photos.tsv'), encoding='utf-8')
        if line.strip() and not line.startswith('#')]
for kind, photo in rows:
    name = f"{kind}-{photo.split('-')[0]}"
    cached = os.path.join(cache_dir, name + '.jpg') if cache_dir else None
    if cached and os.path.exists(cached):
        data = open(cached, 'rb').read()
    else:
        url = (f'https://images.unsplash.com/photo-{photo}'
               f'?w={FETCH[0]}&h={FETCH[1]}&fit=crop&crop=entropy&q=92&fm=jpg')
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'verse-scroll-assets'})) as r:
            data = r.read()
        if cached:
            open(cached, 'wb').write(data)
    im = Image.open(io.BytesIO(data)).convert('RGB').resize(SHIP, Image.LANCZOS).filter(ImageFilter.GaussianBlur(SOFTEN))
    path = os.path.join(out_dir, name + '.webp')
    im.save(path, 'WEBP', quality=QUALITY, method=6)
print(len(rows), 'photos in', out_dir)
