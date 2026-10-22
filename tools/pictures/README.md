# Pictures for every scene

Verse Scroll can show your own pictures behind the verses: one for each scene of a book (a run of
verses showing one moment), made by hand in ChatGPT from the prompts written here, then added on the
app's Pictures page (Verse Scroll, Settings, Pictures page), which shrinks each to a card's size, names
it for its verses and keeps it in a folder you choose on the phone. A card with its own picture shows it
alone for three seconds before the verse comes in.

Every prompt asks for the same kind of picture, in the look of the first one made: cinematic and
photo-real with a warm, painterly glow, wide enough to show the whole situation, tall (9:16), the people
in the lower half so the verse can sit above them, no words. God is only ever shown as light.

The pictures have to be true to the text (the King James Version): nothing that goes against the verses
(who is there and how many, ages, the order of events, the state of things, like how high the water is),
nothing added to the story (no extra people, events or signs), fill-ins only where a picture needs them
(light, weather, landscape, clothes), and where the text is silent, nothing that settles it either way.
Each prompt says the counts outright ("Exactly eight people and no one else: ..."), since ChatGPT adds
people unless it's told not to.

## The files

- `cast.tsv`: the people who come up again and again, each with one description (`Abraham`, `Isaac
  (old)`) put in every prompt they're in, so ChatGPT keeps them looking alike. The key's bracket says
  which age, by Genesis's own numbers; the prompt calls them by their name alone.
- `things.tsv`: things that come up in more than one scene, described as the text describes them (the
  ark, a box of 300 by 50 by 30 cubits, not a ship).
- `<book>.tsv` (`genesis.tsv`): the book's scenes, a line each: chapter, first verse, last verse, title,
  cast (keys of `cast.tsv` and `things.tsv`, comma-separated), god (`y` where God is present), people (`y`
  if anyone is in the picture, `n` if no one is), limits (the firm limits: counts, who is there, what
  isn't), and what the picture shows. Every verse is in exactly one scene. Violent or intimate moments
  are pictured just before or after, so the prompt is one ChatGPT will make. A picture with no one in it
  gets an ending that doesn't mention people at all.
- `genesis_fixes.tsv`: what the accuracy check changed in each scene, with the verses, shown on the
  prompts page.
- `make_prompts.py` checks every book's scenes cover every verse of the KJV once, in order, and writes
  the app's list (`app/src/main/assets/pictures/scenes.tsv`) and a document of the prompts for each book
  (`docs/pictures/<Book>.md`):

  ```sh
  python3 make_prompts.py kjv.tsv ../../app/src/main/assets ../../docs/pictures
  ```

  `kjv.tsv` is the KJV a verse a line (book, chapter, verse, text), with the app's book names.
- `make_page.py` makes the prompts page, with Copy prompt and Copy name buttons, the scene's verses to
  check the picture against, what the check fixed, and a Made tick for each scene (kept in the phone's
  browser):

  ```sh
  python3 make_page.py ../../app/src/main/assets kjv.tsv prompts.html genesis_fixes.tsv
  ```

## Names

A picture is found by its file's name, so pictures named by hand work too: `Genesis 1.3-5.png` for
Genesis 1:3-5 (a dot for the colon, which phones don't allow in names), `Psalms 23.jpg` for a chapter,
`Genesis 6-9.jpg` for several, `Psalms.jpg` for a book, and `Psalms 23 (2).jpg` for another of the same
place (they take turns). A card shows the one that fits its verse best. A picture whose name starts
with "Any" (`Any sunrise.jpg`) shows now and then on any card; files with other names are left alone, so
the folder can be one the phone saves other things to.

## Adding a book

Write its scenes into `<book>.tsv` (and anyone new into `cast.tsv`), run `make_prompts.py`, which
says what's missing or out of order, then `make_page.py`, and publish the page again.
