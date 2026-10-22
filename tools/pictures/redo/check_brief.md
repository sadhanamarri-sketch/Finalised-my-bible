# Second reading: check the Genesis picture scenes against the text

A Bible app shows a picture behind each verse, made by hand in ChatGPT from prompts we write, one per scene
(a run of verses showing one moment). The scenes were just rewritten by another writer to be true to the
King James text, following the brief in
`/tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad/pictures/v2/brief.md`.
Read that brief first: its rules, decisions, ages and counts are yours too.

You are the independent second reader. The first version of these scenes went out with errors that
nobody caught (dry green hills at the olive leaf, the sun before the fourth day, Lot looking back, the ark
as a ship), so assume there are still some, and look for them. The owner will check every picture
against the Bible.

## What to check, scene by scene

Read each scene's verses in the KJV (`.../scratchpad/shots_data/kjv.tsv`), and the chapter around them,
then go through every statement in the title, cast, god, people, limits and description:

1. **Against the text**: who is there and how many, ages (by the cast key and any words), the order of
   events, the time of day where given, the state of things (water, dry ground, sun, darkness), places,
   objects. Anything the verses or the rest of Genesis contradict.
2. **Added to the story**: people the text doesn't name or plainly imply (crowds, workers, servants,
   children, onlookers), events or actions it doesn't have, objects that matter, invented signs (glows,
   halos, sparkles on things, a visible angel or figure where the text has a voice from heaven).
3. **Missing limits**: a count the text gives that isn't in the limits; "no one else" where ChatGPT would
   add people; something ChatGPT is likely to draw wrongly that the limits should rule out.
4. **The cast**: every person in the picture is in the cast with the key of the right age, or described
   in words if they're in no other scene; no one in the cast who isn't in the picture; people y/n right.
5. **The decisions**: creation days (what doesn't exist yet, no sun before 1:14), animals as the text says
   (no herds or pairs for land animals and birds at creation), the ark as the box of 6:14-16 with no ship
   words, Noah's eight and no one else, God only as light, angels only where seen.
6. **Framing**: the people low in the frame, the top half open; nothing that forces someone high up.
7. **The notes**: each note in the notes file is true (the fix it describes is right and the verses fit).

Also fix anything the check script reports.

## What to do

- Fix what you're sure of directly in the scenes file, keeping its format (9 tab-separated fields; write
  with a Python script, utf-8), and run the check script again:
  `python3 /tmp/claude-0/-home-user-Test-bible/e1699488-058c-5976-bb26-2654ec1609ac/scratchpad/pictures/v2/check.py FILE FIRST-LAST`
- For each scene you change, add a line to the notes file (or extend the scene's line):
  `chapter  first  last  note`, the note starting "Second check:" and saying what was wrong and the verse.
- Don't change scene ranges unless a scene can't be one true picture; then say why.
- Where readers could differ, or you're not sure, don't change it: list it in your report with the
  verses, and what you'd suggest.
- Don't edit any file but your scenes file and its notes file.

## Report

Briefly: how many scenes you changed, a line for each change (verses and what), and the debatable points.

## Settled since the writers' brief (apply them in your chapters)

- **God as light only where God appears or speaks** in the scene's verses (y). Where the text has God
  acting unseen (blessing, prospering, remembering, opening a womb, seeing), god is n and there is no
  light, beam or glow for it.
- **Laban** is young in chapter 24 (key "Laban (young)") and old in chapters 29-31 (key "Laban"): about
  97 years pass between them (25:20, 25:26, Jacob about 77 at Haran).
- **Rebekah** is "Rebekah" until chapter 26 and "Rebekah (old)" in chapters 27-28.
- **Leah's wedding night (29:21-24)**: no veil (the text doesn't have one); the text has evening.
