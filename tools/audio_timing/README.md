# Verse timings for recorded Bibles

Makes the files in `data/audio_timing/`: for a recording of the Bible, where each verse starts and
ends in its audio file, so the app can play the recording and follow along verse by verse (backlog
item 21). The recordings themselves never go in this repository; only the timings do.

## How it works

For each chapter (`timing.py`):

1. espeak reads the chapter's heading and verses from the app's own text, in one call, with an exact
   silent gap after each, so where each verse starts in its reading is known exactly.
2. The reading is matched to the recording by its sound: MFCC features every 20 ms, and dynamic time
   warping (`dtw2.c`) in a band around the straight line from start to end.
3. espeak reads at the narrator's pace, worked out from the chapter's length, and then a little
   slower and a little faster: three readings, three matches.
4. Each verse's start is moved onto the narrator's nearest pause, and the three readings vote.
5. Each verse ends where the pause before the next one begins.

Then `refine.py` chooses each start among the pauses near it. It weighs what the matching found
(held tightly where its three readings agreed), how long each pause is (a verse usually follows a
longer one) and whether each verse then takes about as long as its text. Verse 1 is kept after the
chapter's spoken heading, about as long after it as that narrator's headings usually take: the
matching sometimes put verse 1 on a pause inside the heading, and the pause before a heading is
long. On 150 KJV chapters, the refinement put back 96% of known-good starts that had been knocked
1.5–4 s off.

A recording with one file per book (the Telugu one) is split first (`run_telugu.py`). Its chapters
were joined with a moment of digital silence, but not always, and some joins are in the middle of a
chapter. In places every pause was silenced in editing (a noise gate), so a quick run of silences,
three or more each within 20 s of the last, isn't taken for joins. The chapters are then fitted to the
stretches between joins by their expected length. A stretch can hold several chapters, which are
timed together and split at their spoken headings, or none (an interlude).

## Running it

Needs Python 3 with NumPy, ffmpeg, espeak-ng (with its Telugu voice for Telugu) and a C compiler.

```sh
gcc -O3 -shared -fPIC -o libdtw2.so dtw2.c -lm

# KJV, Faith Comes By Hearing's narrated recording (ENGKJVO1DA, ENGKJVN1DA): one file per chapter.
# Where two zips hold the same file, the first one given is used, so replacement files go first.
python3 run_kjv.py kjv.tsv results/kjv 4 ENGKJVN1DA.zip replacements.zip ENGKJVO1DA.zip
python3 apply_refine_kjv.py results/kjv kjv.tsv 4 ENGKJVN1DA.zip replacements.zip ENGKJVO1DA.zip
python3 export.py kjv results/kjv kjv.tsv ../../data/audio_timing/kjv.json

# Telugu, one file per book (Psalms in two)
python3 run_telugu.py ../../app/src/main/assets/telugu kjv.tsv results/te 4 telugu_*.zip
python3 apply_refine_te.py results/te ../../app/src/main/assets/telugu 4 telugu_*.zip
python3 export.py te results/te kjv.tsv ../../data/audio_timing/telugu.json
```

`kjv.tsv` is the app's KJV text, one verse per line: book, chapter, verse, text. Results are kept
chapter by chapter (book by book for Telugu), so a run that stops can be started again to finish.

## What came out (14 Oct 2026)

- KJV: all 1,189 chapters and 31,102 verses (79.9 hours). The three readings agreed on 96.2% of
  verse starts, and the refinement moved 2.1%. 300 chapters had been lost from the first download
  of `ENGKJVO1DA.zip` (long runs of zeros in the file), and 4 more were cut off. They were timed from
  replacement files, each identical to the original by size and checksum.
- Telugu: see `data/audio_timing/README.md`.
