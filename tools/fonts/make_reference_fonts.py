"""
The Telugu font for the book's name in a Verse Scroll card's reference ("ROMANS · రోమీయులకు 8:28",
backlog 61): Chathura Bold, from Google Fonts (SIL Open Font License 1.1).

As published, Chathura sets its line far taller than its letters (1.62 times its size, for its deepest
stacked letters), and Android lays a line out at least that tall, which would push a card's words apart.
This keeps the font's Latin and Telugu letters (and all its shaping rules) and sets its line to just cover
the 66 Telugu book names as they're shaped, with a little room. The letters themselves are unchanged; the
line fits the book names, not every possible Telugu word, so the file is for the names only.

Needs fontTools and Pillow built with raqm (for Telugu shaping).
    python3 -I tools/fonts/make_reference_fonts.py SOURCE_DIR [OUT_DIR]
SOURCE_DIR holds Chathura-Bold.ttf from https://github.com/google/fonts (ofl/chathura); OUT_DIR defaults
to app/src/main/res/font.
"""
import io
import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from PIL import ImageFont

REPO = pathlib.Path(__file__).resolve().parents[2]
FONTS = {"Chathura-Bold.ttf": "chathura_bold.ttf"}
# Basic Latin, no-break space, Telugu, the joiners, and the dotted circle the shaping can ask for.
UNICODES = list(range(0x20, 0x7F)) + [0xA0] + list(range(0x0C00, 0x0C80)) + [0x200C, 0x200D, 0x25CC]
ROOM = 0.06  # of the font's size, above and below the names' letters


def book_names() -> list[str]:
    source = (REPO / "app/src/main/java/com/example/mybible/model/TeluguBooks.kt").read_text(encoding="utf-8")
    names = re.findall(r'"[^"]+" to "([^"]+)"', source)
    assert len(names) == 66, len(names)
    return names


def make(src: pathlib.Path, out: pathlib.Path, names: list[str]) -> None:
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    font = TTFont(src)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(font)
    if "FFTM" in font:
        del font["FFTM"]

    # The names' letters as shaped, in font units (drawn at one unit a pixel).
    upm = font["head"].unitsPerEm
    buffer = io.BytesIO()
    font.save(buffer)
    shaped = ImageFont.truetype(io.BytesIO(buffer.getvalue()), upm, layout_engine=ImageFont.Layout.RAQM)
    top = max(-shaped.getbbox(n, anchor="ls")[1] for n in names)
    bottom = max(shaped.getbbox(n, anchor="ls")[3] for n in names)
    ascent, descent = round(top + ROOM * upm), round(max(bottom, 0) + ROOM * upm)

    hhea, os2 = font["hhea"], font["OS/2"]
    before = (hhea.ascent - hhea.descent) / upm
    hhea.ascent, hhea.descent, hhea.lineGap = ascent, -descent, 0
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = ascent, -descent, 0
    os2.usWinAscent, os2.usWinDescent = ascent, descent
    font.save(out)
    print(f"{out.name}: line {before:.2f} -> {(ascent + descent) / upm:.2f} of its size; {out.stat().st_size // 1024} KB")


def main() -> None:
    source = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else REPO / "app/src/main/res/font"
    names = book_names()
    for name, target in FONTS.items():
        make(source / name, out / target, names)


if __name__ == "__main__":
    main()
