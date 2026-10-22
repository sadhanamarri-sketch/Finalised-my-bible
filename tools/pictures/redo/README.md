# Genesis, redone true to the text (in progress)

The scenes of Genesis rewritten so that nothing in a picture goes against the King James text or adds to
it (the first version had the sun before the fourth day, dry hills at the olive leaf, the ark as a ship,
and more). Same format as `../genesis.tsv` will have (9 fields, see `../README.md`).

- Written, not yet given a second reading: chapters 1-11 (`genesis_01.tsv`), 23-30 (`genesis_23.tsv`)
  and 40-50 (`genesis_40.tsv`). Each has its notes (what the first version got wrong, and the verses) and
  the people it adds to the cast.
- Not written yet: chapters 12-22 and 31-39.
- `brief.md` is what the writers followed (the rules and the owner's decisions: the animals as the text
  has them, the ark as a box, Noah's eight and no one else); `check_brief.md` is for the second reading;
  `check.py` checks a file's form and its verses (it reads the app's KJV exported as a TSV; its paths are
  those of the session that made it).

When every range is written and checked, they go into `../genesis.tsv`, the cast into `../cast.tsv`, the
notes into `../genesis_fixes.tsv`, and this folder goes.
