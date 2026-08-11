#!/usr/bin/env python3
"""
Generates the Android notification "small icon" — an open-book silhouette —
at every density Android expects. Android notification icons must be a pure
white shape on a transparent background (the OS ignores color/tints it
itself), drawn well inside a safe-area padding so it isn't clipped when the
OS applies its own circular/square crop.

Output: one PNG per density, named ic_stat_book.png, sized per Android's
24dp baseline:
  mdpi    24x24
  hdpi    36x36
  xhdpi   48x48
  xxhdpi  72x72
  xxxhdpi 96x96
"""
from PIL import Image, ImageDraw

SCALE = 10  # supersample factor for smooth downscaled edges
BASE = 100 * SCALE  # working canvas is 1000x1000, scaled down to targets

DENSITIES = {
    "drawable-mdpi":    24,
    "drawable-hdpi":    36,
    "drawable-xhdpi":   48,
    "drawable-xxhdpi":  72,
    "drawable-xxxhdpi": 96,
}

def make_master():
    img = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def pt(x, y):
        return (x * SCALE, y * SCALE)

    # Open-book silhouette: two pages fanning out from a shared spine,
    # kept within a safe area so Android's own icon padding never clips it.
    spine_top = pt(50, 24)
    spine_bottom = pt(50, 80)
    left_top = pt(14, 17)
    left_bottom = pt(14, 73)
    right_top = pt(86, 17)
    right_bottom = pt(86, 73)

    draw.polygon([spine_top, left_top, left_bottom, spine_bottom], fill=(255, 255, 255, 255))
    draw.polygon([spine_top, right_top, right_bottom, spine_bottom], fill=(255, 255, 255, 255))

    # Thin transparent spine gap so the two pages read as a book, not a
    # solid bowtie/hourglass blob.
    gap_w = 1.6 * SCALE
    draw.rectangle(
        [50 * SCALE - gap_w / 2, 22 * SCALE, 50 * SCALE + gap_w / 2, 82 * SCALE],
        fill=(0, 0, 0, 0),
    )

    return img

def main():
    master = make_master()
    for folder, size in DENSITIES.items():
        resized = master.resize((size, size), Image.LANCZOS)
        import os
        os.makedirs(f"notif-icons-output/{folder}", exist_ok=True)
        resized.save(f"notif-icons-output/{folder}/ic_stat_book.png")
        print(f"{folder}: {size}x{size}")

    # Also save a large reference/master preview
    master.resize((512, 512), Image.LANCZOS).save("notif-icons-output/preview_512.png")
    print("preview_512.png written")

if __name__ == "__main__":
    main()
