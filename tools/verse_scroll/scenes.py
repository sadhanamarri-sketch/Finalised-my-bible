"""Painted-style background scenes for Verse Scroll, drawn entirely with code (no photos).

Every scene is calm and low in detail where the verse sits, darkest toward the bottom where the
bottom bar is, and finished with a little film grain so the gradients don't band.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

F = np.float32


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=F)


def mix(a, b, t):
    return a * (1 - t) + b * t


def smooth(t):
    return t * t * (3 - 2 * t)


PALETTES = {
    'dawn': dict(sky=[(0, '#1b2350'), (0.33, '#553d7a'), (0.58, '#bd6c88'), (0.76, '#f0a687'), (0.92, '#fbd6ac')],
                 sun='#fff0cf', haze='#e8b6a4', far='#b48aa4', near='#211830'),
    'golden': dict(sky=[(0, '#283a6a'), (0.38, '#7a5e86'), (0.62, '#e29972'), (0.84, '#f8cd8a'), (1, '#fff1cd')],
                   sun='#fff6dc', haze='#f1c597', far='#cc997e', near='#2f2127',
                   sea_h='#e2a47d', sea_d='#221f36'),
    'dusk': dict(sky=[(0, '#0b1230'), (0.34, '#2b2358'), (0.6, '#7a396e'), (0.8, '#cf606a'), (0.94, '#f29870')],
                 sun='#ffd2a5', haze='#b65f78', far='#84486a', near='#100b1d',
                 sea_h='#a3566f', sea_d='#120d22'),
    'morning': dict(sky=[(0, '#6a9dc7'), (0.4, '#9cc0db'), (0.7, '#d2e3e9'), (0.9, '#eff1e9'), (1, '#f7efdf')],
                    sun='#ffffff', haze='#dde8e9', far='#a5bfc9', near='#3c616d',
                    sea_h='#b7cfd8', sea_d='#284f64'),
    'mist': dict(sky=[(0, '#2f4e5b'), (0.4, '#547788'), (0.7, '#8caaae'), (0.9, '#c1d2ce'), (1, '#dde6df')],
                 sun='#f3f6f0', haze='#c9d8d4', far='#92abae', near='#16272d'),
    'night': dict(sky=[(0, '#03050c'), (0.45, '#091230'), (0.74, '#171f4b'), (0.9, '#2c2a5a'), (1, '#443768')],
                  sun='#eef0ff', haze='#3a3565', far='#19193a', near='#05050c'),
    'nightteal': dict(sky=[(0, '#020a0c'), (0.45, '#06212a'), (0.74, '#0e3b45'), (0.9, '#1d4f57'), (1, '#2e5f62')],
                      sun='#e8fff8', haze='#1d4a50', far='#0f2a30', near='#04090b'),
    'nightplum': dict(sky=[(0, '#06030c'), (0.45, '#160b2c'), (0.74, '#2f1a4a'), (0.9, '#4a2a5c'), (1, '#6a3a62')],
                      sun='#fff0f8', haze='#3a2350', far='#22142e', near='#07040b'),
    'desert': dict(sky=[(0, '#4c70a6'), (0.34, '#a497b4'), (0.6, '#eebd96'), (0.84, '#f9dab0'), (1, '#ffefd4')],
                   sun='#fff8e8', haze='#f5d1ab', far='#e6b288', near='#8f4f2c'),
    'pasture': dict(sky=[(0, '#76abd2'), (0.45, '#a6cadf'), (0.75, '#d6e7e9'), (1, '#f2f0e0')],
                    sun='#fffdf0', haze='#e4ecdb', far='#a7c599', near='#335d3a'),
    'rose': dict(sky=[(0, '#383a6c'), (0.38, '#8c6b9c'), (0.64, '#e2a2b3'), (0.84, '#f5cec5'), (1, '#fde8de')],
                 sun='#fff3ee', haze='#efc8ce', far='#c499ad', near='#3a2940', sea_h='#d69eae', sea_d='#29203f'),
    'teal': dict(sky=[(0, '#0c262f'), (0.4, '#23545e'), (0.66, '#6ea29f'), (0.85, '#e7c69d'), (1, '#f6e1be')],
                 sun='#fff4dc', haze='#a8c8be', far='#5e8b87', near='#0e292d', sea_h='#7eafa9', sea_d='#0a2028'),
    'storm': dict(sky=[(0, '#1b222b'), (0.4, '#3d4957'), (0.7, '#7c8895'), (0.9, '#b6bdc3'), (1, '#d5d8d5')],
                  sun='#f5f2e8', haze='#a8b1b7', far='#6d7985', near='#191f25', sea_h='#8c97a0', sea_d='#1a2229'),
    'lavender': dict(sky=[(0, '#2b2959'), (0.4, '#6b5e9b'), (0.7, '#c2a5d1'), (0.9, '#eed5e1'), (1, '#f8ebee')],
                     sun='#fff6fb', haze='#dbc5e1', far='#a695c3', near='#392f57', sea_h='#b8a4cf', sea_d='#221c3e'),
    'harvest': dict(sky=[(0, '#3c5989'), (0.4, '#998ea7'), (0.66, '#efb379'), (0.86, '#f7d399'), (1, '#ffefce')],
                    sun='#fff3d6', haze='#f2c894', far='#d89f65', near='#6d3e21', sea_h='#e3a874', sea_d='#2a2030'),
    'sage': dict(sky=[(0, '#5e7e85'), (0.45, '#90adab'), (0.75, '#cad8ce'), (1, '#edf0e5')],
                 sun='#fbfcf4', haze='#dae4d8', far='#a2b79c', near='#3e5943', sea_h='#a9c0b8', sea_d='#24403f'),
    'winter': dict(sky=[(0, '#7e97b2'), (0.4, '#a8bbcc'), (0.75, '#d8e1e9'), (1, '#f0f3f5')],
                   sun='#ffffff', haze='#e2e9ef', far='#a8bbcc', near='#5c7184', snow='#f6f8fb'),
    'winterdusk': dict(sky=[(0, '#2a2e54'), (0.4, '#695985'), (0.7, '#d3999b'), (1, '#f5d1b7')],
                       sun='#fff0dc', haze='#e1bbbf', far='#9c8eaf', near='#3a395b', snow='#f2e8ed'),
    'wintergold': dict(sky=[(0, '#394977'), (0.4, '#8985a7'), (0.68, '#efc299'), (0.86, '#fae0be'), (1, '#fff3e1')],
                       sun='#fff6e6', haze='#f2dbc7', far='#c6b8c3', near='#595977', snow='#fff7ee'),
    'winterblue': dict(sky=[(0, '#2e69a7'), (0.45, '#6d9ecf'), (0.8, '#bbd5eb'), (1, '#e8f1f7')],
                       sun='#ffffff', haze='#d5e5f1', far='#9eb9d5', near='#496587', snow='#f7fbff'),
    'winternight': dict(sky=[(0, '#050a17'), (0.45, '#0e1b37'), (0.8, '#233659'), (1, '#394e6f')],
                        sun='#e9efff', haze='#3b506f', far='#4a5e7f', near='#1a2438', snow='#c7d3e5', moon=True),
    'ember': dict(base='#120d1d', blobs=['#c0607a', '#f29a70', '#3b2a6a', '#2c4c7c', '#f3c47c']),
    'plum': dict(base='#190e1e', blobs=['#7a3b6e', '#c46b8a', '#3a2a6a', '#e2a07a']),
    'ocean': dict(base='#071722', blobs=['#1f5d7a', '#3fa1a6', '#14365c', '#9fd3c7']),
    'grove': dict(base='#0c1911', blobs=['#2f6b4a', '#8fbf7a', '#1c3d3a', '#d6c27a']),
    'gold': dict(base='#1b130a', blobs=['#c98a3a', '#f2c46b', '#6b3a2a', '#3a2a4a']),
    'blush': dict(base='#1c0f17', blobs=['#c46b7e', '#f2b0a0', '#5a2a4a', '#8a6aa8']),
    'daybreak': dict(base='#131525', blobs=['#5a4a8a', '#d98a8a', '#f2c48a', '#3a5a8a']),
    'lagoon': dict(base='#0c1a22', blobs=['#2f7d86', '#7fc4b5', '#24456e', '#c9a86a', '#5b4a8c']),
}


# ---------- building blocks ----------
def vgrad(H, stops, top=0.0, bottom=1.0):
    """A column of colors for rows 0..H-1, with the stops spread between `top` and `bottom` (0..1)."""
    ys = np.linspace(0, 1, H, dtype=F)
    pos = np.array([top + p * (bottom - top) for p, _ in stops], F)
    cols = np.stack([rgb(c) for _, c in stops])
    idx = np.clip(np.searchsorted(pos, ys, side='right') - 1, 0, len(pos) - 2)
    t = np.clip((ys - pos[idx]) / np.maximum(pos[idx + 1] - pos[idx], 1e-6), 0, 1)
    t = smooth(t)[:, None]
    out = cols[idx] * (1 - t) + cols[idx + 1] * t
    out[ys <= pos[0]] = cols[0]
    out[ys >= pos[-1]] = cols[-1]
    return out


def sky(H, W, stops, bottom=1.0):
    return np.repeat(vgrad(H, stops, 0.0, bottom)[:, None, :], W, axis=1).astype(F)


def noise1d(rng, n, octaves=5, base=3.0, persistence=0.5, ridged=False):
    x = np.linspace(0, 1, n, dtype=F)
    total = np.zeros(n, F)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        k = base * 2 ** o
        kk = int(np.ceil(k)) + 2
        pts = rng.uniform(-1, 1, kk).astype(F)
        p = x * k
        i = np.floor(p).astype(int)
        s = smooth(p - i)
        v = pts[i] * (1 - s) + pts[i + 1] * s
        if ridged:
            v = (1 - np.abs(v)) * 2 - 1
        total += amp * v
        norm += amp
        amp *= persistence
    return total / norm


def noise2d(rng, H, W, octaves=5, base=3, persistence=0.55):
    total = np.zeros((H, W), F)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        gw = int(base * 2 ** o) + 2
        gh = int(gw * H / W) + 2
        grid = rng.uniform(0, 1, (gh, gw)).astype(F)
        up = np.asarray(Image.fromarray(grid, mode='F').resize((W, H), Image.BICUBIC), dtype=F)
        total += amp * up
        norm += amp
        amp *= persistence
    return np.clip(total / norm, 0, 1)


def screen(img, color, a):
    """Screen-blend `color` with per-pixel strength `a` (H, W)."""
    c = color[None, None, :] * a[..., None]
    img[:] = 1 - (1 - img) * (1 - c)


def over(img, color, a):
    """Paint `color` (3,) or (H, W, 3) over the image with per-pixel alpha `a` (H, W)."""
    a = a[..., None]
    img[:] = img * (1 - a) + color * a


def glow(img, cx, cy, r, color, strength):
    H, W, _ = img.shape
    Y, X = np.ogrid[:H, :W]
    d2 = ((X - cx) ** 2 + (Y - cy) ** 2) / (r * r)
    screen(img, color, (np.exp(-d2) * strength).astype(F))


def disc(img, cx, cy, r, color, alpha=1.0):
    H, W, _ = img.shape
    Y, X = np.ogrid[:H, :W]
    d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
    over(img, color, (np.clip(r - d + 0.5, 0, 1) * alpha).astype(F))


def fill_below(img, ridge_y, crest, base, depth, ref=None):
    """Fill everything below a ridgeline: `crest` color right under the line, easing into `base` over `depth` px.
    With `ref` (a smoothed copy of the ridgeline), the shading follows that instead of every jagged peak."""
    H, W, _ = img.shape
    Y = np.arange(H, dtype=F)[:, None]
    d = Y - ridge_y[None, :]
    a = np.clip(d + 0.5, 0, 1)
    dr = d if ref is None else Y - ref[None, :]
    t = smooth(np.clip(dr / depth, 0, 1))[..., None]
    col = crest[None, None, :] * (1 - t) + base[None, None, :] * t
    over(img, col, a)


def fill_between(img, top, bottom, color):
    """Paint `color` where top(x) <= y < bottom(x)."""
    H, W, _ = img.shape
    Y = np.arange(H, dtype=F)[:, None]
    a = np.clip(Y - top[None, :] + 0.5, 0, 1) * np.clip(bottom[None, :] - Y + 0.5, 0, 1)
    over(img, color, a.astype(F))


def pines(rng, W, H, ground, spacing, hmin, hmax, ratio=(0.17, 0.24), skip=0.0, keep=None):
    """Tree tops standing on `ground`: returns the outline (min of ground and every tree)."""
    top = ground.copy()
    x = rng.uniform(-20, 0)
    while x < W + 20:
        if rng.uniform() >= skip and (keep is None or keep(x / W)):
            h = H * rng.uniform(hmin, hmax)
            hw = h * rng.uniform(*ratio)
            xi = int(np.clip(x, 0, W - 1))
            xa, xb = int(max(x - hw, 0)), int(min(x + hw + 1, W))
            if xa < xb:
                xs = np.arange(xa, xb, dtype=F)
                prof = ground[xi] - h + np.abs(xs - x) * (h / hw)
                top[xa:xb] = np.minimum(top[xa:xb], prof)
        x += W * rng.uniform(*spacing)
    return top


def fog(img, y0, y1, color, strength):
    """A haze band that thickens from y0 down to y1 and stays."""
    H, W, _ = img.shape
    Y = np.arange(H, dtype=F)[:, None]
    a = smooth(np.clip((Y - y0) / max(y1 - y0, 1), 0, 1)) * strength
    over(img, color, np.repeat(a, W, axis=1))


def stars(img, rng, n, region=None, scale=1.0, band=None):
    H, W, _ = img.shape
    x0, y0, x1, y1 = region or (0, 0, W, H)
    pts = []
    if band is None:
        xs = rng.uniform(x0, x1, n)
        ys = rng.uniform(y0, y1, n)
    else:
        (bx, by, ang, width) = band
        u = rng.uniform(-1.6, 1.6, n) * max(W, H)
        v = rng.normal(0, width, n)
        xs = bx + u * np.cos(ang) - v * np.sin(ang)
        ys = by + u * np.sin(ang) + v * np.cos(ang)
        keep = (xs >= x0) & (xs < x1) & (ys >= y0) & (ys < y1)
        xs, ys = xs[keep], ys[keep]
    b = rng.uniform(0, 1, len(xs)) ** 3
    for x, y, br in zip(xs, ys, b):
        sigma = (0.55 + 1.3 * br) * scale
        r = int(np.ceil(sigma * 3.5))
        xi, yi = int(x), int(y)
        xa, xb = max(xi - r, 0), min(xi + r + 1, W)
        ya, yb = max(yi - r, 0), min(yi + r + 1, H)
        if xa >= xb or ya >= yb:
            continue
        gy, gx = np.mgrid[ya:yb, xa:xb]
        g = np.exp(-((gx - x) ** 2 + (gy - y) ** 2) / (2 * sigma * sigma)) * (0.35 + 0.65 * br)
        tint = np.array([1.0, 0.97, 0.9], F) if rng.uniform() < 0.3 else np.array([0.9, 0.95, 1.0], F)
        patch = img[ya:yb, xa:xb]
        patch[:] = 1 - (1 - patch) * (1 - tint[None, None, :] * g[..., None].astype(F))


def blur1d(a, sigma):
    r = int(sigma * 3)
    k = np.exp(-np.arange(-r, r + 1) ** 2 / (2 * sigma * sigma)).astype(F)
    k /= k.sum()
    return np.convolve(np.pad(a, r, mode='edge'), k, mode='valid').astype(F)


def grain(img, rng, amount=0.011):
    H, W, _ = img.shape
    img += rng.normal(0, amount, (H, W, 1)).astype(F)


# ---------- scenes ----------
META = {}  # filled by each scene: where its sun / horizon / mist are, so the page can animate them in place
def scene_ridges(rng, W, H, P):
    img = sky(H, W, P['sky'], bottom=0.62)
    horizon = H * rng.uniform(0.52, 0.58)
    sx, sy = W * rng.uniform(0.25, 0.75), horizon - H * rng.uniform(0.0, 0.04)
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, haze=P['haze'], mist=[horizon / H + 0.06, horizon / H + 0.17])
    sun = rgb(P['sun'])
    glow(img, sx, sy, W * 0.95, sun, 0.28)
    glow(img, sx, sy, W * 0.22, sun, 0.55)
    disc(img, sx, sy, W * 0.045, sun, 0.95)
    far, near, haze = rgb(P['far']), rgb(P['near']), rgb(P['haze'])
    n = 5
    for i in range(n):
        t = i / (n - 1)
        base = horizon + H * (0.015 + 0.31 * t ** 1.25)
        amp = H * (0.11 - 0.045 * t) * rng.uniform(0.8, 1.2)
        ridge = noise1d(rng, W, octaves=6, base=rng.uniform(1.6, 3.2), ridged=True)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        layer = mix(far, near, t ** 0.9)
        crest = mix(layer, haze, 0.35 * (1 - t))
        fill_below(img, ridge_y, crest, layer, H * 0.14)
        if i < n - 1:
            fog(img, base - amp * 0.35, base + H * 0.05, haze, 0.28 * (1 - t) + 0.06)
    return img


def scene_hills(rng, W, H, P):
    img = sky(H, W, P['sky'], bottom=0.66)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.2, 0.8), H * rng.uniform(0.18, 0.32)
    glow(img, sx, sy, W * 0.9, sun, 0.35)
    glow(img, sx, sy, W * 0.16, sun, 0.6)
    clouds = noise2d(rng, H, W, octaves=5, base=2)
    a = smooth(np.clip((clouds - 0.55) * 3.2, 0, 1)) * 0.45
    a *= np.clip(1 - np.arange(H, dtype=F)[:, None] / (H * 0.6), 0, 1)
    over(img, mix(sun, rgb(P['haze']), 0.3), a)
    far, near, haze = rgb(P['far']), rgb(P['near']), rgb(P['haze'])
    horizon = H * rng.uniform(0.56, 0.6)
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, haze=P['haze'], mist=[horizon / H + 0.05, horizon / H + 0.15])
    n = 4
    for i in range(n):
        t = i / (n - 1)
        base = horizon + H * (0.04 + 0.28 * t ** 1.1)
        amp = H * (0.075 + 0.05 * t)
        ridge = noise1d(rng, W, octaves=3, base=rng.uniform(0.8, 1.4), persistence=0.35)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        layer = mix(far, near, t)
        crest = mix(layer, sun, 0.34 * (1 - t * 0.5))
        fill_below(img, ridge_y, crest, layer * 0.92, H * 0.16)
        if i < n - 1:
            fog(img, base - amp * 0.2, base + H * 0.04, haze, 0.2 * (1 - t))
    return img


def scene_sea(rng, W, H, P):
    horizon = H * rng.uniform(0.56, 0.6)
    hz = horizon / H
    img = sky(H, W, P['sky'], bottom=hz)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.3, 0.7), horizon - H * rng.uniform(0.035, 0.08)
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, glint=P['sun'])
    glow(img, sx, sy, W * 1.0, sun, 0.3)
    glow(img, sx, sy, W * 0.2, sun, 0.6)
    disc(img, sx, sy, W * 0.055, sun)
    # the sea
    Y = np.arange(H, dtype=F)
    rel = np.clip((Y - horizon) / (H - horizon), 0, 1)
    sea_col = mix(rgb(P['sea_h'])[None, :], rgb(P['sea_d'])[None, :], (rel ** 0.7)[:, None])
    sea = np.repeat(sea_col[:, None, :], W, axis=1)
    waves = 1 + 0.016 * np.sin(rel * 120 * (0.4 + rel) + noise1d(rng, H, octaves=4, base=30) * 6) * (0.4 + 0.6 * rel)
    sea *= waves[:, None, None].astype(F)
    a = np.clip(Y - horizon + 0.5, 0, 1)[:, None].repeat(W, axis=1).astype(F)
    over(img, sea, a)
    # the sun's path on the water
    X = np.arange(W, dtype=F)[None, :]
    width = (W * (0.025 + 0.2 * rel))[:, None]
    streak = np.clip(noise1d(rng, H, octaves=4, base=90) * 1.6 + 0.35, 0, 1)
    inten = (streak * (1 - rel) ** 0.6 * 0.75 * (Y > horizon))[:, None]
    screen(img, sun, (np.exp(-((X - sx) / width) ** 2) * inten).astype(F))
    # a soft line where sea meets sky
    line = np.exp(-((Y - horizon) / (H * 0.004)) ** 2)[:, None].repeat(W, axis=1) * 0.25
    screen(img, rgb(P['haze']), line.astype(F))
    return img


def scene_night(rng, W, H, P, moon=False):
    img = sky(H, W, P['sky'], bottom=0.85)
    ang = np.deg2rad(rng.uniform(-62, -38))
    bx, by = W * rng.uniform(0.35, 0.65), H * rng.uniform(0.3, 0.45)
    Y, X = np.mgrid[:H, :W].astype(F)
    d = (-(X - bx) * np.sin(ang) + (Y - by) * np.cos(ang))
    band = np.exp(-(d / (W * 0.2)) ** 2)
    cl = noise2d(rng, H, W, octaves=6, base=3)
    lanes = noise2d(rng, H, W, octaves=5, base=5)
    mw = band * (0.25 + 0.75 * cl ** 2) * (1 - 0.45 * band * smooth(np.clip((lanes - 0.45) * 3, 0, 1)))
    tint = mix(rgb('#b8b9ff')[None, None, :], rgb('#ffd7c0')[None, None, :], cl[..., None])
    c = tint * (mw * 0.32)[..., None]
    img[:] = 1 - (1 - img) * (1 - c)
    s = W / 1080
    stars(img, rng, int(1100 * s * s * H / 2340), scale=s)
    stars(img, rng, int(1600 * s * s * H / 2340), scale=s * 0.8, band=(bx, by, ang, W * 0.09))
    if moon:
        mx, my, mr = W * rng.uniform(0.2, 0.8), H * rng.uniform(0.12, 0.22), W * 0.05
        META.update(moon=[mx / W, my / H, mr * 2.2 / W])
        glow(img, mx, my, W * 0.45, rgb('#c9d2ff'), 0.22)
        Yg, Xg = np.ogrid[:H, :W]
        lit = np.clip(mr - np.sqrt((Xg - mx) ** 2 + (Yg - my) ** 2) + 0.5, 0, 1)
        cut = np.clip(mr * 0.9 - np.sqrt((Xg - mx - mr * 0.45) ** 2 + (Yg - my + mr * 0.2) ** 2) + 0.5, 0, 1)
        over(img, rgb('#f4f1e4'), (lit * (1 - cut)).astype(F))
    glow(img, W * 0.5, H * 1.02, W * 0.9, rgb('#5b4a8c'), 0.35)
    far, near = rgb(P['far']), rgb(P['near'])
    for i, (base, amp) in enumerate([(0.8, 0.07), (0.88, 0.06)]):
        ridge = noise1d(rng, W, octaves=4, base=rng.uniform(1.2, 2.2))
        ridge_y = H * base - H * amp * (ridge * 0.5 + 0.5)
        col = mix(far, near, i)
        fill_below(img, ridge_y, col, col, 10)
    return img


def scene_desert(rng, W, H, P):
    img = sky(H, W, P['sky'], bottom=0.62)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.3, 0.7), H * rng.uniform(0.47, 0.52)
    META.update(sun=[sx / W, sy / H], horizon=0.6, haze=P['haze'], mist=[0.64, 0.74])
    glow(img, sx, sy, W * 1.1, sun, 0.3)
    glow(img, sx, sy, W * 0.25, sun, 0.55)
    disc(img, sx, sy, W * 0.085, sun, 0.95)
    far, near, haze = rgb(P['far']), rgb(P['near']), rgb(P['haze'])
    n = 4
    Y = np.arange(H, dtype=F)[:, None]
    for i in range(n):
        t = i / (n - 1)
        base = H * (0.6 + 0.26 * t ** 1.15)
        amp = H * (0.05 + 0.03 * t)
        ridge = noise1d(rng, W, octaves=2, base=rng.uniform(0.9, 1.6), ridged=True)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        layer = mix(far, near, t)
        lit = mix(layer, rgb('#ffe2b8'), 0.35)
        shade = layer * 0.78
        slope = blur1d(np.gradient(ridge_y), W * 0.02)
        side = np.tanh(-slope * 3.0)
        lt = np.clip(side, 0, 1)[None, :, None]
        sh = np.clip(-side, 0, 1)[None, :, None]
        col = layer[None, None, :] * (1 - lt - sh) + lit[None, None, :] * lt + shade[None, None, :] * sh
        depth = smooth(np.clip((Y - ridge_y[None, :]) / (H * 0.07), 0, 1))[..., None]
        col = mix(col, layer[None, None, :] * 0.9, depth)
        a = np.clip(Y - ridge_y[None, :] + 0.5, 0, 1)
        over(img, col.astype(F), a.astype(F))
        rim = np.exp(-((Y - ridge_y[None, :] - 2) / 2.2) ** 2) * 0.35 * (1 - t * 0.6)
        screen(img, rgb('#fff1d8'), rim.astype(F))
        if i < n - 1:
            fog(img, base - amp * 0.3, base + H * 0.04, haze, 0.22 * (1 - t))
    return img


def scene_forest(rng, W, H, P):
    img = sky(H, W, P['sky'], bottom=0.7)
    sun = rgb(P['sun'])
    gx = W * rng.uniform(0.3, 0.7)
    gy = H * rng.uniform(0.25, 0.35)
    META.update(horizon=0.57, haze=P['haze'], mist=[0.6, 0.71])
    glow(img, gx, gy, W * 0.8, sun, 0.3)
    far, near, haze = rgb(P['far']), rgb(P['near']), rgb(P['haze'])
    n = 4
    s = W / 1080
    for i in range(n):
        t = i / (n - 1)
        base = H * (0.57 + 0.27 * t ** 1.1)
        ridge = noise1d(rng, W, octaves=3, base=rng.uniform(1.0, 2.0))
        ground = base - H * 0.03 * (ridge * 0.5 + 0.5)
        top = ground.copy()
        x = rng.uniform(-20, 0)
        while x < W + 20:
            h = H * rng.uniform(0.045, 0.1) * (0.6 + 0.7 * t)
            hw = h * rng.uniform(0.17, 0.24)
            xi = int(np.clip(x, 0, W - 1))
            xa, xb = int(max(x - hw, 0)), int(min(x + hw + 1, W))
            if xa < xb:
                xs = np.arange(xa, xb, dtype=F)
                prof = ground[xi] - h + np.abs(xs - x) * (h / hw)
                top[xa:xb] = np.minimum(top[xa:xb], prof)
            x += W * rng.uniform(0.012, 0.03) * (1 + t * 0.8)
        layer = mix(far, near, t ** 0.85)
        fill_below(img, top, mix(layer, haze, 0.15 * (1 - t)), layer, H * 0.1)
        if i < n - 1:
            fog(img, base - H * 0.05, base + H * 0.05, haze, 0.38 * (1 - t) + 0.08)
    return img


def scene_light(rng, W, H, P):
    img = sky(H, W, P['sky'], bottom=1.0)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.3, 0.7), H * rng.uniform(0.14, 0.26)
    META.update(source=[sx / W, sy / H], glint=P['sun'])
    Y, X = np.mgrid[:H, :W].astype(F)
    dist = np.sqrt((X - sx) ** 2 + (Y - sy) ** 2)
    cl = noise2d(rng, H, W, octaves=6, base=2)
    ca = smooth(np.clip((cl - 0.48) * 2.6, 0, 1)) * 0.85
    lit = np.exp(-dist / (W * 0.55))
    ccol = mix(mix(rgb(P['sky'][1][1]), rgb(P['haze']), 0.5)[None, None, :], rgb('#fff4dc')[None, None, :], lit[..., None])
    over(img, ccol.astype(F), ca.astype(F))
    ang = np.arctan2(Y - sy, X - sx)
    k = rng.uniform(0, 100)
    rays = np.sin(ang * 13 + k) * 0.55 + np.sin(ang * 29 + k * 1.7) * 0.25 + np.sin(ang * 5 + k * 0.3) * 0.35
    rays = np.clip(rays, 0, None) ** 1.2
    down = smooth(np.clip((Y - sy) / (H * 0.08), 0, 1))
    fall = np.exp(-dist / (H * 0.5)) * down
    screen(img, mix(sun, rgb('#ffd89a'), 0.4), (rays * fall * 0.24).astype(F))
    glow(img, sx, sy, W * 0.6, sun, 0.55)
    glow(img, sx, sy, W * 0.12, sun, 0.8)
    # ground the bottom so the bar area stays calm
    fog(img, H * 0.72, H * 1.0, rgb(P['near']), 0.65)
    return img


def scene_abstract(rng, W, H, P):
    sw, sh = W // 8, H // 8
    META.update(blobs=list(P['blobs'][:3]))
    base = tuple(int(v * 255) for v in rgb(P['base']))
    im = Image.new('RGB', (sw, sh), base)
    for c in rng.permutation(P['blobs']):
        layer = Image.new('RGB', (sw, sh), base)
        mask = Image.new('L', (sw, sh), 0)
        d = ImageDraw.Draw(mask)
        r = sw * rng.uniform(0.35, 0.7)
        cx, cy = sw * rng.uniform(0, 1), sh * rng.uniform(0.05, 0.8)
        d.ellipse([cx - r, cy - r * rng.uniform(0.8, 1.4), cx + r, cy + r * rng.uniform(0.8, 1.4)], fill=int(255 * rng.uniform(0.55, 0.85)))
        layer.paste(tuple(int(v * 255) for v in rgb(c)), (0, 0, sw, sh))
        im = Image.composite(layer, im, mask)
    im = im.filter(ImageFilter.GaussianBlur(sw * 0.16)).resize((W, H), Image.BICUBIC)
    img = np.asarray(im, dtype=F) / 255
    fog(img, H * 0.7, H * 1.0, rgb(P['base']), 0.6)
    return img.copy()


OLIVE = {  # canopy, canopy highlight, canopy shadow, trunk, far ground, near ground
    'golden': ('#8b9970', '#bcc49b', '#5a6748', '#4a392a', '#cdb68a', '#8b7446'),
    'morning': ('#86977a', '#b6c3a6', '#56664d', '#4b3d30', '#bcc6a8', '#7d8659'),
    'dawn': ('#7f8a73', '#b1b29c', '#525a4c', '#45362e', '#c2a39c', '#6f5a4c'),
    'harvest': ('#8a955f', '#bcbf88', '#59623e', '#4d3825', '#d8ad78', '#8e5e32'),
    'sage': ('#86987d', '#b3c3a8', '#57684f', '#4a3d31', '#b6c4ab', '#6d7c58'),
    'dusk': ('#5e6656', '#878c76', '#3a4036', '#2e2425', '#8f6a7c', '#3f2e35'),
    'mist': ('#7c8f86', '#a9b9b0', '#4f5f58', '#3c3a35', '#a7b8b4', '#5c6c63'),
    'rose': ('#808a74', '#b3b49f', '#545d4c', '#463631', '#d1aeb0', '#7d6460'),
    'pasture': ('#8c9d72', '#bcc79d', '#5b6a4a', '#4b3c2c', '#bccf9c', '#6f8a4f'),
}
WHEAT = {  # far field, near field, ear tips
    'golden': ('#e8c27a', '#b5812d', '#f7da95'), 'morning': ('#e2cf8e', '#b6903b', '#f3e2a6'),
    'harvest': ('#efb35b', '#98581b', '#f9d58c'), 'dawn': ('#e2b38a', '#986839', '#f5cfa4'),
    'dusk': ('#c4896a', '#583323', '#e2a885'), 'storm': ('#c8b179', '#79632f', '#e2cf92'),
    'rose': ('#e7bea0', '#a7774a', '#f6d8bd'), 'lavender': ('#d7bfa0', '#89694a', '#ecd8bc'),
    'pasture': ('#dfcf89', '#a78f39', '#f0e3a6'),
}


def scene_snow(rng, W, H, P, variant='range'):
    img = sky(H, W, P['sky'], bottom=0.6)
    sun = rgb(P['sun'])
    if P.get('moon'):             # high in the sky, clear of the verse, and a crescent like the night scenes'
        sx, sy, mr = W * rng.uniform(0.25, 0.75), H * rng.uniform(0.1, 0.18), W * 0.045
        META.update(moon=[sx / W, sy / H, mr * 2.2 / W])
        glow(img, sx, sy, W * 0.5, rgb('#c9d6ff'), 0.25)
        Yg, Xg = np.ogrid[:H, :W]
        lit = np.clip(mr - np.sqrt((Xg - sx) ** 2 + (Yg - sy) ** 2) + 0.5, 0, 1)
        cut = np.clip(mr * 0.9 - np.sqrt((Xg - sx - mr * 0.45) ** 2 + (Yg - sy + mr * 0.2) ** 2) + 0.5, 0, 1)
        over(img, rgb('#f2f0e6'), (lit * (1 - cut)).astype(F))
    else:
        sx, sy = W * rng.uniform(0.25, 0.75), H * rng.uniform(0.18, 0.4)
        glow(img, sx, sy, W * 0.9, sun, 0.3)
        glow(img, sx, sy, W * 0.14, sun, 0.5)
    far, near, haze, snow = rgb(P['far']), rgb(P['near']), rgb(P['haze']), rgb(P['snow'])
    if variant == 'field':        # open snowfields under a big sky, a few pines
        horizon, n = H * rng.uniform(0.58, 0.63), 3
    elif variant == 'grove':      # tall pines framing an open middle
        horizon, n = H * rng.uniform(0.5, 0.54), 4
    else:                         # a mountain range with pine-covered slopes
        horizon, n = H * rng.uniform(0.5, 0.55), 5
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, haze=P['haze'], snow=True,
                mist=[horizon / H + 0.07, horizon / H + 0.18])
    for i in range(n):
        t = i / (n - 1)
        base = horizon + H * (0.02 + 0.3 * t ** 1.2)
        amp = H * (0.1 - 0.035 * t) * rng.uniform(0.8, 1.15) * (0.55 if variant == 'field' else 1)
        peaky = i < 2 and variant != 'field'
        ridge = noise1d(rng, W, octaves=5 if peaky else 3, base=rng.uniform(1.4, 2.6), ridged=peaky)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        lit = mix(snow, haze, 0.5 * (1 - t))
        shade = mix(mix(far, near, t ** 0.8), haze, 0.35 * (1 - t))
        fill_below(img, ridge_y, lit, mix(lit, shade, 0.6), H * (0.06 + 0.1 * t), ref=blur1d(ridge_y, W * 0.06) - amp * 0.25)
        tree_col = mix(mix(near, rgb('#16212b'), 0.5), haze, 0.25 * (1 - t))
        ground = ridge_y + H * 0.004
        tops = None
        if variant == 'range' and i >= 2:
            tops = pines(rng, W, H, ground, (0.012, 0.04), 0.03 * (0.6 + t), 0.07 * (0.6 + t), skip=0.35)
        elif variant == 'field' and i >= 1:
            tops = pines(rng, W, H, ground, (0.02, 0.06), 0.025 * (0.6 + t), 0.05 * (0.6 + t), skip=0.8)
        elif variant == 'grove' and i == n - 1:
            tops = pines(rng, W, H, ground, (0.03, 0.06), 0.12, 0.2, ratio=(0.16, 0.2), keep=lambda u: u < 0.28 or u > 0.72)
        elif variant == 'grove' and i == n - 2:
            tops = pines(rng, W, H, ground, (0.015, 0.035), 0.04, 0.08, skip=0.45)
        if tops is not None:
            fill_between(img, tops, ground + 1, tree_col)
        if i < n - 1:
            fog(img, base - amp * 0.3, base + H * 0.05, haze, 0.25 * (1 - t) + 0.05)
    return img


def olive_trees(img, rng, W, H, trees, colors, haze, haze_t, sx):
    """Olive trees standing on the ground: each (x, ground_y, size) gets a gnarled trunk, a crown of overlapping
    blobs and a soft shadow on the side away from the sun."""
    canopy, hi, lo, trunk, gnear = colors
    if not trees:
        return
    s2 = 2
    layer = Image.new('L', (W * s2, H * s2), 0)
    crown = Image.new('L', (W * s2, H * s2), 0)
    shade_m = Image.new('L', (W * s2, H * s2), 0)
    dl, dc, ds = ImageDraw.Draw(layer), ImageDraw.Draw(crown), ImageDraw.Draw(shade_m)
    for x, gy, size in trees:
        cx, cy = x + rng.uniform(-0.1, 0.1) * size, gy - size * rng.uniform(0.75, 0.95)
        off = (-1 if sx > W / 2 else 1) * size * 0.35
        ds.ellipse([(cx + off - size * 0.7) * s2, (gy - size * 0.1) * s2, (cx + off + size * 0.7) * s2, (gy + size * 0.12) * s2], fill=150)
        tw = size * 0.1
        lean = rng.uniform(-0.25, 0.25) * size
        dl.polygon([((x - tw) * s2, gy * s2), ((x + tw) * s2, gy * s2), ((cx + lean * 0.3 + tw * 0.6) * s2, (cy + size * 0.2) * s2), ((cx + lean * 0.3 - tw * 0.6) * s2, (cy + size * 0.2) * s2)], fill=255)
        for _ in range(int(rng.integers(8, 13))):
            bx = cx + rng.normal(0, size * 0.34)
            by = cy + rng.normal(0, size * 0.15)
            br = size * rng.uniform(0.2, 0.36)
            dc.ellipse([(bx - br) * s2, (by - br * 0.75) * s2, (bx + br) * s2, (by + br * 0.75) * s2], fill=255)
    size = float(np.median([t[2] for t in trees]))
    shadow_a = np.asarray(shade_m.resize((W, H), Image.LANCZOS), dtype=F) / 255 * 0.35
    trunk_a = np.asarray(layer.resize((W, H), Image.LANCZOS), dtype=F) / 255
    crown_im = crown.resize((W, H), Image.LANCZOS)
    crown_a = np.asarray(crown_im, dtype=F) / 255
    lit_a = np.asarray(crown_im.filter(ImageFilter.GaussianBlur(size * 0.12)), dtype=F) / 255
    shift = int(max(2, size * 0.18))
    below = np.zeros_like(crown_a)
    below[shift:] = crown_a[:-shift]
    top_light = np.clip(crown_a - below, 0, 1)
    over(img, mix(gnear * 0.55, haze, haze_t * 0.5), shadow_a)
    over(img, mix(trunk, haze, haze_t), trunk_a)
    ccol = mix(mix(lo, canopy, lit_a[..., None]), mix(canopy, hi, 0.5), (top_light * 0.45)[..., None])
    ccol = mix(ccol, haze[None, None, :], haze_t)
    over(img, ccol.astype(F), crown_a)


def scene_olives(rng, W, H, P, pal, variant='grove'):
    img = sky(H, W, P['sky'], bottom=0.62)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.2, 0.8), H * rng.uniform(0.25, 0.42)
    glow(img, sx, sy, W * 0.9, sun, 0.3)
    glow(img, sx, sy, W * 0.15, sun, 0.5)
    cl = noise2d(rng, H, W, octaves=5, base=2)
    a = smooth(np.clip((cl - 0.56) * 3.0, 0, 1)) * 0.35 * np.clip(1 - np.arange(H, dtype=F)[:, None] / (H * 0.55), 0, 1)
    over(img, mix(sun, rgb(P['haze']), 0.35), a)
    canopy, hi, lo, trunk, gfar, gnear = [rgb(c) for c in OLIVE[pal]]
    colors = (canopy, hi, lo, trunk, gnear)
    haze = rgb(P['haze'])
    horizon = H * (rng.uniform(0.48, 0.52) if variant == 'hillside' else rng.uniform(0.53, 0.57))
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, haze=P['haze'], mist=[horizon / H + 0.04, horizon / H + 0.13])
    grounds = []
    for i in range(3):
        t = i / 2
        base = horizon + H * (0.02 + 0.12 * t)
        amp = H * (0.05 + 0.02 * t)
        ridge = noise1d(rng, W, octaves=3, base=rng.uniform(0.8, 1.5), persistence=0.35)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        col = mix(mix(gfar, haze, 0.45 * (1 - t)), gnear, t * 0.85)
        fill_below(img, ridge_y, mix(col, sun, 0.15), col, H * 0.2)
        grounds.append(ridge_y)
        if i < 2:
            fog(img, base - amp * 0.3, base + H * 0.04, haze, 0.22)
    if variant == 'hillside':     # fewer, larger trees climbing the slope
        rows, size_of, y_of = 3, (lambda t: H * (0.03 + 0.085 * t ** 1.4)), (lambda t: grounds[2] + H * (0.06 + 0.3 * t ** 1.3))
    elif variant == 'lone':       # a distant grove, and one old tree close by
        rows, size_of, y_of = 2, (lambda t: H * (0.014 + 0.02 * t)), (lambda t: grounds[2] + H * (0.02 + 0.07 * t))
    else:                         # rows of trees receding up the slope
        rows, size_of, y_of = 5, (lambda t: H * (0.016 + 0.062 * t ** 1.6)), (lambda t: grounds[2] + H * (0.03 + 0.3 * t ** 1.4))
    for r in range(rows):
        t = r / max(rows - 1, 1)
        size, yb = size_of(t), y_of(t)
        trees = []
        x = rng.uniform(-size, size)
        while x < W + size:
            trees.append((x, yb[int(np.clip(x, 0, W - 1))], size))
            x += size * rng.uniform(1.6, 2.6)
        olive_trees(img, rng, W, H, trees, colors, haze, 0.45 * (1 - t) if variant != 'lone' else 0.4, sx)
    if variant == 'lone':
        x = W * (0.84 if rng.uniform() < 0.5 else 0.16)
        olive_trees(img, rng, W, H, [(x, H * 0.9, H * 0.12)], colors, haze, 0.0, sx)
    return img


def scene_lake(rng, W, H, P):
    water = H * rng.uniform(0.5, 0.62)
    img = sky(H, W, P['sky'], bottom=water / H)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.25, 0.75), water - H * rng.uniform(0.08, 0.16)
    glow(img, sx, sy, W * 0.9, sun, 0.3)
    glow(img, sx, sy, W * 0.16, sun, 0.55)
    disc(img, sx, sy, W * 0.04, sun, 0.9)
    far, near, haze = rgb(P['far']), rgb(P['near']), rgb(P['haze'])
    META.update(sun=[sx / W, sy / H], horizon=water / H, glint=P['haze'])
    for i in range(3):
        t = i / 2
        base = water - H * (0.07 - 0.035 * t)
        amp = H * (0.09 - 0.03 * t)
        peaky = i == 0
        ridge = noise1d(rng, W, octaves=5 if peaky else 3, base=rng.uniform(1.2, 2.4), ridged=peaky)
        ridge_y = base - amp * (ridge * 0.5 + 0.5)
        col = mix(far, near, t ** 0.8)
        fill_below(img, ridge_y, mix(col, haze, 0.3 * (1 - t)), col, H * 0.08)
        if i == 2:
            # a dark tree line along the shore
            tops = pines(rng, W, H, np.full(W, water, F), (0.006, 0.016), 0.012, 0.03, ratio=(0.2, 0.3), skip=0.2)
            fill_between(img, tops, np.full(W, water + 1, F), mix(near, rgb('#0a0e12'), 0.4))
        elif i < 2:
            fog(img, base - amp * 0.4, base + H * 0.02, haze, 0.25)
    # the reflection: the world above the waterline, flipped, rippled, darkened and softened
    wi = int(water)
    hgt = H - wi
    src0 = max(wi - hgt, 0)
    refl = img[src0:wi][::-1].copy()
    if refl.shape[0] < hgt:
        refl = np.concatenate([refl, np.repeat(refl[-1:], hgt - refl.shape[0], axis=0)], axis=0)
    d = np.arange(hgt, dtype=F)
    amp = 1.5 + d / hgt * 14
    phase = noise1d(rng, hgt, octaves=4, base=60) * 9 + d * 0.21
    shift = (np.sin(phase) * amp).astype(int)
    cols = (np.arange(W)[None, :] + shift[:, None]) % W
    refl = np.take_along_axis(refl, cols[..., None].repeat(3, axis=2), axis=1)
    water_col = mix(near, rgb(P['sky'][0][1]), 0.35)
    fade = (0.22 + 0.45 * (d / hgt) ** 0.8)[:, None, None]
    refl = refl * (1 - fade) + water_col[None, None, :] * fade
    im = Image.fromarray((np.clip(refl, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
    img[wi:] = np.asarray(im, dtype=F) / 255
    # faint ripple lines catching the sky
    for _ in range(int(60 * W / 1080)):
        y = wi + int(hgt * rng.uniform(0, 1) ** 1.5)
        x0 = rng.uniform(-0.1, 1) * W
        ln = W * rng.uniform(0.04, 0.25)
        xs = np.arange(int(max(x0, 0)), int(min(x0 + ln, W)))
        if len(xs) == 0 or y >= H - 1:
            continue
        wgt = np.sin(np.linspace(0, np.pi, len(xs))) * rng.uniform(0.08, 0.2)
        img[y, xs] = img[y, xs] * (1 - wgt[:, None]) + haze[None, :] * wgt[:, None]
    line = np.exp(-((np.arange(H, dtype=F) - water) / (H * 0.003)) ** 2)[:, None].repeat(W, axis=1) * 0.2
    screen(img, haze, line.astype(F))
    return img


def scene_wheat(rng, W, H, P, pal, lone=False):
    horizon = H * rng.uniform(0.56, 0.61)
    img = sky(H, W, P['sky'], bottom=horizon / H)
    sun = rgb(P['sun'])
    sx, sy = W * rng.uniform(0.25, 0.75), horizon - H * rng.uniform(0.04, 0.14)
    glow(img, sx, sy, W * 1.0, sun, 0.32)
    glow(img, sx, sy, W * 0.16, sun, 0.55)
    cl = noise2d(rng, H, W, octaves=5, base=2)
    a = smooth(np.clip((cl - 0.55) * 3.0, 0, 1)) * 0.4 * np.clip(1 - np.arange(H, dtype=F)[:, None] / horizon, 0, 1)
    over(img, mix(sun, rgb(P['haze']), 0.4), a)
    ffar, fnear, ear = [rgb(c) for c in WHEAT[pal]]
    haze = rgb(P['haze'])
    META.update(sun=[sx / W, sy / H], horizon=horizon / H, haze=P['haze'], wind=[horizon / H + 0.06, 0.93], sheen=WHEAT[pal][2])
    # distant tree line
    ridge = noise1d(rng, W, octaves=4, base=rng.uniform(3, 6))
    tl = horizon - H * (0.006 + 0.012 * (ridge * 0.5 + 0.5))
    fill_between(img, tl, np.full(W, horizon + 2, F), mix(rgb(P['far']), haze, 0.35))
    if lone:
        s2 = 2
        m = Image.new('L', (W * s2, H * s2), 0)
        dm = ImageDraw.Draw(m)
        tx, size = W * rng.uniform(0.15, 0.85), H * rng.uniform(0.035, 0.05)
        ty = horizon + H * 0.004
        dm.rectangle([(tx - size * 0.05) * s2, (ty - size * 0.5) * s2, (tx + size * 0.05) * s2, ty * s2], fill=255)
        for _ in range(9):
            bx, by, br = tx + rng.normal(0, size * 0.22), ty - size * 0.85 + rng.normal(0, size * 0.12), size * rng.uniform(0.25, 0.38)
            dm.ellipse([(bx - br) * s2, (by - br) * s2, (bx + br) * s2, (by + br) * s2], fill=255)
        ta = np.asarray(m.resize((W, H), Image.LANCZOS), dtype=F) / 255
        over(img, mix(rgb(P['far']), rgb('#2a2420'), 0.55), ta)
    # the field
    Y = np.arange(H, dtype=F)
    rel = np.clip((Y - horizon) / (H - horizon), 0, 1)
    field = mix(ffar[None, :], fnear[None, :], (rel ** 0.9)[:, None])
    field = np.repeat(field[:, None, :], W, axis=1)
    rows = noise2d(rng, H, W // 8 + 1, octaves=4, base=6)
    rows = np.asarray(Image.fromarray(rows, mode='F').resize((W, H), Image.BICUBIC), dtype=F)
    field *= (1 + (rows - 0.5) * 0.16 * (0.3 + rel[:, None]))[..., None]
    band = np.exp(-((rel - rng.uniform(0.25, 0.5)) / 0.08) ** 2)[:, None] * (0.6 + 0.4 * noise1d(rng, W, octaves=2, base=1.5)[None, :])
    field = field * (1 - 0.12 * band[..., None]) + ear[None, None, :] * 0.12 * band[..., None]
    fa = np.clip(Y - horizon + 0.5, 0, 1)[:, None].repeat(W, axis=1)
    over(img, field.astype(F), fa.astype(F))
    # stalks and ears in the foreground, drawn at twice the size for smooth edges
    y0 = int(horizon + (H - horizon) * 0.45)
    hh = H - y0
    s2 = 2
    lay = Image.new('RGBA', (W * s2, hh * s2), (0, 0, 0, 0))
    dr = ImageDraw.Draw(lay)
    n = int(1400 * W / 1080)
    for _ in range(n):
        v = rng.uniform(0, 1) ** 0.7
        base_y = v * hh
        tall = (12 + 110 * v) * W / 1080
        x = rng.uniform(-10, W + 10)
        lean = rng.normal(0.18, 0.12) * tall
        topx, topy = x + lean, base_y - tall
        shade_k = rng.uniform(0.72, 0.95)
        stalk = tuple(int(c * 255 * shade_k * (0.75 + 0.25 * v)) for c in mix(fnear, ear, 0.25)) + (255,)
        dr.line([(x * s2, base_y * s2), (topx * s2, topy * s2)], fill=stalk, width=max(1, int((0.8 + 1.8 * v) * s2 * W / 1080)))
        er = (1.6 + 4 * v) * W / 1080
        ec = tuple(int(c * 255 * shade_k) for c in mix(ear, fnear, rng.uniform(0.25, 0.55))) + (255,)
        dr.ellipse([(topx - er * 0.42) * s2, (topy - er * 2.4) * s2, (topx + er * 0.42) * s2, (topy + er * 0.5) * s2], fill=ec)
    lay = lay.resize((W, hh), Image.LANCZOS)
    arr = np.asarray(lay, dtype=F) / 255
    region = img[y0:]
    region[:] = region * (1 - arr[..., 3:4]) + arr[..., :3] * arr[..., 3:4]
    glow(img, sx, horizon, W * 0.7, sun, 0.22)
    return img


SCENES = {
    'ridges': scene_ridges, 'hills': scene_hills, 'sea': scene_sea, 'night': scene_night,
    'moon': lambda rng, W, H, P: scene_night(rng, W, H, P, moon=True),
    'desert': scene_desert, 'forest': scene_forest, 'light': scene_light, 'abstract': scene_abstract,
    'snow': scene_snow, 'lake': scene_lake,
}
NEEDS_PALETTE_NAME = {'olives': scene_olives, 'wheat': scene_wheat}


def render(kind, palette, seed, W=1080, H=2340):
    META.clear()
    META['kind'] = kind
    rng = np.random.default_rng(seed)
    P = PALETTES[palette]
    if kind == 'snow':
        img = scene_snow(rng, W, H, P, variant=('range', 'field', 'grove')[seed % 3])
    elif kind == 'olives':
        img = scene_olives(rng, W, H, P, palette, variant=('grove', 'hillside', 'lone')[seed % 3])
    elif kind == 'wheat':
        img = scene_wheat(rng, W, H, P, palette, lone=seed % 2 == 0)
    else:
        img = SCENES[kind](rng, W, H, P)
    img = img.astype(F)
    META['layout'] = {'snow': ('range', 'field', 'grove')[seed % 3], 'olives': ('grove', 'hillside', 'lone')[seed % 3]}.get(kind)
    grain(img, rng)
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB')
