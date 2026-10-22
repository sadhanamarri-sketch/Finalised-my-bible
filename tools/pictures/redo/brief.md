# Genesis picture scenes, second version: true to the text

A Bible app shows a picture behind each verse. The pictures are made by hand in ChatGPT from prompts we
write, one picture per scene (a run of verses showing one moment), so that every verse of Genesis is in
exactly one scene. The first version of the scenes read well but had factual errors that would make
ChatGPT draw things the text doesn't say. The person who owns the app reads the pictures against the
Bible, so every picture must be true to the King James text. Your job is to rewrite the scenes for your
chapters so that each one shows only what the text says, plus the fill-ins a picture can't do without.

Accuracy comes before beauty. When in doubt, leave it out.

## Files

- The KJV, one verse a line (book, chapter, verse, text):
  `/tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad/shots_data/kjv.tsv`
  Read every verse of your chapters, and the chapters around them where the story continues.
- The first version: `/home/user/Test-bible/tools/pictures/genesis.tsv` (chapter, first, last, title, cast,
  god, description). Start from it: keep what's right, fix what's wrong, rewrite what can't be fixed.
- The cast: `/home/user/Test-bible/tools/pictures/cast.tsv` (key, looks). It was corrected for ages: note the
  keys by age (Ishmael (boy), Ishmael (youth), Ishmael, Ishmael (old); Isaac (child), Isaac (lad), Isaac,
  Isaac (old); Jacob, Jacob (old); Joseph (youth), Joseph, Joseph (governor), Joseph (old)).
- The things: `/home/user/Test-bible/tools/pictures/things.tsv` (the ark).
- Do not edit any file in /home/user/Test-bible. Write only your own output files (below).

## What you write

1. `/tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad/pictures/v2/genesis_NN.tsv`
   (NN is your first chapter, two digits): your scenes, a line each, 9 tab-separated fields:

   `chapter  first  last  title  cast  god  people  limits  description`

   - **cast**: comma-separated keys of the people (cast.tsv) and things (things.tsv) shown, exactly as
     spelled there (straight apostrophes). Only who and what is in the picture.
   - **god**: `y` where God is present in the scene (speaking, appearing, acting), shown only as light;
     else `n`.
   - **people**: `y` if any person is in the picture, `n` if no one is.
   - **limits**: one to three short, plain sentences of firm limits for ChatGPT: the exact counts and who
     is there ("Exactly eight people and no one else: Noah, his wife, their three sons and the sons' three
     wives."), and what must not appear ("No sun, moon or stars." "No people anywhere."). Spell numbers
     as words. Empty only when there is truly nothing that ChatGPT is likely to get wrong. At most 45
     words.
   - **description**: what the picture shows, 40 to 75 words, in one to three sentences, naming people by
     their cast keys exactly ("Isaac (lad)"; the prompt shows them by name alone). Describe a scene with
     no people positively ("an empty world of dark water and light"), and keep the word "people" out of
     it.

2. `.../pictures/v2/notes_NN.tsv`: for every scene whose content you changed (not just reworded), a line:
   `chapter  first  last  note`, the note at most 30 words: what was wrong and the verse that shows it
   ("Showed green hillsides; the waters had only abated, the ground was dry later (8:11, 8:13-14)."). For
   a scene range split or merged, say so. Unchanged scenes get no line.

3. Only if you need a recurring person who isn't in cast.tsv (in two or more of your scenes):
   `.../pictures/v2/cast_NN.tsv`, `key  looks`, the looks in the style of cast.tsv, true to the text and
   to their age. Someone in one scene only is described in the description instead.

Check your file with:
`python3 /tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad/pictures/v2/check.py FILE FIRST-LAST`
(FIRST-LAST your chapters). It checks every verse is in exactly one scene, the fields, the keys, the
counts of words and a few words that shouldn't appear. Fix everything it reports.

## The rules

1. **Nothing against the text.** Who is there and how many; ages; the order of events; the time of day
   when the text gives it; the state of things (how high the water is, whether the ground is dry, whether
   the sun exists yet); places; objects as the text describes them. Check each sentence of each scene
   against its verses and the rest of Genesis.
2. **Nothing added to the story.** No extra people beyond those the text names or plainly implies (a
   household, servants, the men of the city, when the text has them); no invented events, gestures that
   change the meaning, or objects that matter; no invented signs (glowing trees, halos, sparkles, a
   visible angel where the text has only a voice from heaven).
3. **Fill-ins only where a picture needs them**: light and weather, landscape, clothing, faces and
   expressions, where the camera is. Choose them to fit the text (evening where the text says evening),
   and keep them plain. Prefer leaving a detail out to inventing one.
4. **Where the text is silent and readers differ, stay neutral.** Don't show a number the text doesn't
   give, or settle a question it leaves open.
5. **Counts the text gives go in the limits, with "no one else" where it matters.** ChatGPT adds
   crowds, workers, servants and children unless it's told not to.
6. **God is shown only as radiant light**, and only where the text has God present. Never a figure, a
   face, a hand or a voice drawn as anything but light.
7. **Angels**: the text calls them men ("three men" 18:2, "the men" 19:10): use the key Angels, men in
   white without wings, only where the text has them present and seen. "The angel of the LORD called unto
   him out of heaven" (21:17, 22:11, 22:15) is a voice: show light from the sky, no figure. Cherubim
   (3:24) are the only winged beings.
8. **Ages**: never contradict the ages Genesis gives or that its numbers imply (see the fact sheet), and
   pick the cast key of the right age. Don't call someone young or old where the numbers say otherwise.
9. **Hard moments**: violent or intimate moments are shown just before or after, plainly and without
   gore, as in the first version. Nobody unclothed except Adam and Eve before 3:21, always hidden by plants
   and long hair.
10. **Framing**: each prompt ends with a wide shot, the people's heads in the lower half of the picture,
    the top half open. Write descriptions that fit that: the people low in the frame, seen from far enough
    away or from a little above; not someone leaning out high up, or a figure in the sky. The main action
    centred.
11. **Words**: no numbers as digits, no words to be written in the picture, nothing modern.
12. **Names**: the text's names for the time (Abram and Sarai before chapter 17 are fine to mention, but use
    the cast keys Abraham and Sarah); no "Israel" for Jacob before 32:28, and only as the text does after.

## Decisions already made

- **Animals at creation follow the text.** 1:20-21: the waters bring forth living creatures "abundantly",
  so many fish and sea creatures are right. Birds and land animals: a few of each kind, no herds, no
  number implied, not shown in pairs. In Eden (chapters 2-3) the same: a few animals, no herds.
- **The ark is the box of 6:14-16**: use the thing key `the ark` in the cast of every scene where it
  shows, call it "the ark" in the description, and never "ship", "boat", "hull", "bow", "stern", "keel",
  "mast", "sail" or "deck" (its look comes from things.tsv). No ramp unless needed for the moment (the
  text has only "the door of the ark ... in the side thereof").
- **Noah's family is eight people** (7:13, 8:16, 8:18; 1 Peter 3:20): Noah, his wife, his three sons
  (key Noah's sons) and the sons' three wives (key Noah's sons' wives). Whenever the family shows, the
  limits say exactly who, and "no one else". Before the flood, no one helps build the ark in the text: no
  workers.
- **Days of creation, what doesn't exist yet** (keep it in the limits):
  - 1:1-2: only darkness and the waters ("the deep"); God's Spirit moving on the waters, as light. No light
    of day yet, no land, no plants, no sun, moon or stars, no animals, no people.
  - 1:3-5: light, divided from darkness; still water everywhere. No sun, moon or stars; no land, plants,
    animals or people.
  - 1:6-8: the firmament between the waters above and the waters below. No sun, moon or stars; no land,
    plants, animals or people.
  - 1:9-10: the waters gathered, dry land appears, bare. No sun, moon or stars; no plants, animals or
    people.
  - 1:11-13: grass, herbs and fruit trees bearing fruit (full grown). No sun, moon or stars; no animals or
    people.
  - 1:14-19: the sun, the moon and the stars. No animals or people.
  - 1:20-23: fish and sea creatures in abundance, great whales; birds (a few of each kind). No land animals,
    no people.
  - 1:24-25: land animals, cattle, creeping things, a few of each kind. No people.
  - 1:26 on: Adam and Eve. Light in days 1-3 never comes from a sun: say so in the limits, and don't write
    dawn, sunrise, sunset, sunshine or golden hour.
- **People scenes vs empty ones**: set `people` to `n` only where no one is in the picture; then the
  prompt ends without any mention of people, faces or clothing, and your limits should say "No people
  anywhere." (and for creation also "no buildings or anything made by hand").

## Ages and counts from Genesis's own numbers

- Noah: 500 when his sons were born (5:32), 600 at the flood (7:6, 7:11). His sons are grown, married
  men at the flood.
- Abraham: 75 leaving Haran (12:4), 86 at Ishmael's birth (16:16), 99 at the covenant of circumcision
  (17:1, 17:24), 100 at Isaac's birth (21:5), died at 175 (25:7). Sarah is ten years younger (17:17): 65
  leaving Haran, 90 at Isaac's birth, died at 127 (23:1).
- Ishmael: a baby in 16:15; thirteen at his circumcision (17:25); about sixteen when he and Hagar are sent
  away (14 at Isaac's birth, then Isaac weaned: 21:8-21), so "Ishmael (youth)" there, not a small child;
  grown, an archer (21:20); about 89 at Abraham's burial (25:9: "Ishmael (old)" or "Ishmael"), died at 137
  (25:17).
- Isaac: a small child when weaned (21:8: "Isaac (child)"); "the lad" at Moriah (22:5, 22:12), his age not
  given ("Isaac (lad)"); 37 when Sarah died (23:1); 40 at his marriage (25:20); 60 when the twins were born
  (25:26); old and blind at the blessing (27:1: "Isaac (old)"); died at 180 (35:28).
- Esau and Jacob: newborn twins in 25:24-26 (described in words, no cast key); grown in 25:27; Esau marries
  at 40 (26:34). Jacob is about 77 when he flees to Haran (130 at 47:9; Joseph 30 at 41:46 plus seven
  years of plenty and two of famine, 45:6; Joseph born after Jacob's fourteenth year with Laban, 30:25,
  31:41): a man in his prime, not a young man ("Jacob"). From chapter 37 on (Jacob about 108 and older)
  use "Jacob (old)".
- Joseph: seventeen in 37:2 ("Joseph (youth)"); "Joseph" in Potiphar's house and prison; thirty before
  Pharaoh (41:46), "Joseph (governor)" from 41:41; 39 when his father comes (45:6); died at 110 (50:26:
  "Joseph (old)").
- Benjamin: born on the road near Ephrath (35:16-18); a grown man with ten sons when the family goes down
  to Egypt (46:21), though Judah calls him "the lad" (44:22).
- Counts: eight in the ark (7:13); clean beasts and birds by sevens, the unclean by two (7:2-3), "two and
  two" going in (7:9); three men at Mamre (18:2); two angels at Sodom (19:1); Lot's two daughters (19:15);
  318 trained servants (14:14); Jacob's twelve sons and Dinah (35:22-26, 30:21); ten brothers go first to
  Egypt, Benjamin stays (42:3-4); seventy souls of Jacob's house (46:27); seven good and seven lean kine,
  seven full and seven thin ears (41:2-7); three branches, three baskets (40:10, 40:16); twenty pieces of
  silver (37:28).

## Errors found in the first version (don't repeat them)

- Creation: "at dawn" (1:9-10) and "midday sunshine" (1:11-13) before the sun is made (1:14-19); herds of
  land animals (1:24-25, 2:1-3); animals filing past "in pairs ... every creature has its mate" (2:18-20:
  the text says neither); the tree of life "glowing softly" (2:9).
- The flood: the ark called "a great ship"; peaks above the water at 7:17-20 ("the mountains were
  covered", 7:20); "green hillsides" at the olive leaf (8:10-12: the waters "abated", the ground dry only
  later, 8:13-14); the animals leaving before the family (8:18-19 has Noah's family first); the sons'
  wives left out where the text names them (7:13, 8:18).
- Lot "looks back" as the angels lead him out (19:15-16): it is his wife who looks back (19:26); Lot
  "lingered".
- An angel "appears in a blaze of light" at Moriah (22:9-12): the angel called "out of heaven".
- Isaac shown as a boy of about twelve where the text gives no age; Ishmael as a small child when sent
  away at about sixteen; Jacob as a young man at about 77.

## How to work

1. Read your chapters in the KJV, verse by verse, and the first version's scenes beside them.
2. For each scene, list to yourself every fact the picture would show (who, how many, where, when,
   what they do, what objects, what's absent) and check each against the verses. Fix or remove anything
   not in the text and not a necessary fill-in.
3. Write the limits: counts, "no one else", and the things ChatGPT is likely to add or get wrong.
4. Run check.py and fix what it reports.
5. Read your whole file once more against the text, sentence by sentence, as if checking someone else's
   work, and fix what you find.
6. Report back briefly: scenes per chapter, the main kinds of fixes, any new cast keys, and anything you
   couldn't settle (with the verses).
