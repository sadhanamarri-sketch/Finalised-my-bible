# Verse timings for recorded Bibles

Makes the files in `data/audio_timing/`: for a recording of the Bible, where each verse starts and
ends in its audio file, so the app can play the recording and follow along verse by verse (backlog
item 21). The recordings themselves never go in this repository; only the timings do.

Three recordings are timed: Faith Comes By Hearing's narrated KJV and YGFM's Telugu Audio Bible as
below, and the KJV Dramatized Audio by its words alone (see the end of "How it works").

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
longer one) and whether each verse then takes about as long as its text. It also keeps verse 1 from
starting inside the chapter's spoken heading, where the matching had put it in 211 Telugu chapters
and 67 KJV ones, mostly on the pause between the book's name and the chapter's number (afterwards 1
and 17, most of those psalms with no title, straight after a short heading). How long each heading
takes is espeak's length for it, times how much longer the narrator takes over that book's headings
(`books.py` has what each narrator says). Verse 1 can still come later than that: a psalm's title is
read after its heading.

Tested on starts all three readings agreed on: knocked 1.5–4 s off, it put back 96% of them (150 KJV
chapters); moved 0.4–2.5 s onto the nearest other pause, as the matching goes wrong, it put back
90–94% of them in four Telugu books, where about 20% would be right if left alone. It moved 6 of the
4,277 other starts there.

Last, `voice_check.py` checks the starts with the narrator's own voice: verses that begin with the
same word (దేవుడు, మరియు) should sound alike just after their starts. Where a verse's first half
second matches that word, at the other verses' starts, clearly better more than 0.3 s earlier, its
start had landed on a pause after the first word, and it's moved back to where the word begins. It
can check the verses whose first word begins at least 8 in the same file: about half of them.

For the Telugu, that gets most verses right, but not all to the word: espeak's Telugu voice is
far from this narrator's, and he pauses inside verses as much as between them. So every word is then
placed by a forced aligner trained on the narrator himself (`telugu_words/`, the Montreal Forced
Aligner, which uses Kaldi). Its training material is 7,000 verses (21 hours) whose boundaries came
out solid above; each word's sounds come from its letters (`telugu_g2p.py`: Telugu spelling is
nearly phonetic). It then aligns each chapter's whole text, heading first, and each verse starts
where its first word does (`words_to_verses.py`). Checked by the narrator's own voice as below, on
Genesis: within 0.15 s of where the verses' first words begin, 76% of starts after the matching, 82%
after the voice check, 89% by the words.

A recording with one file per book (the Telugu one) is split first (`run_telugu.py`). Its chapters
were joined with a moment of digital silence, but not always, and some joins are in the middle of a
chapter. In places every pause was silenced in editing (a noise gate), so a quick run of silences,
three or more each within 20 s of the last, isn't taken for joins. The chapters are then fitted to the
stretches between joins by their expected length. A stretch can hold several chapters, which are
timed together and split at their spoken headings, or none (an interlude).

The KJV Dramatized Audio (the Internet Archive: one MP4 per book, 76 hours, read by a cast) is timed
by its words alone (`english_words/`): every word placed by the Montreal Forced Aligner's pretrained
English model (`english_mfa`, with its US English dictionary; the 4,570 words said that the dictionary
hasn't are spelled out by its pretrained G2P model). Besides the verses, the recording says each book's
title (mostly as the KJV prints it; Genesis just "Genesis"), each chapter's number ("Chapter two"), in
Psalms "Psalm two" and the psalm's title, which the app's text hasn't (`psalm_titles.json`, from a
public KJV), and Psalm 119's Hebrew letters; the aligner is given all of it (`kjv_text.py`).

A book is far too long to align in one piece, so `chain_align.py` takes it about ten minutes at a
time, every book's next stretch in one run of the aligner, round after round. A stretch holds whole
chapters and ends with an unknown word to take whatever of the next chapter it reaches into. The
aligner fits a stretch's words in from its very first moment, so each stretch starts just after the
last word the one before placed (at most a second after: the aligner can call speech that follows
silence, once a minute and a half of it). A stretch whose last words were squeezed in (too little of
the recording for them) is tried again, longer.

The aligner goes wrong where the cast's voices are far from what it was trained on (a crowd crying "hide
us" in Revelation 6, the serpent in Genesis 3): it stretches a word over seconds of speech not its own,
and carries on placing the words after it late. So a chapter with a word placed as longer than 2.5 s
isn't taken, nor anything after it in its stretch; it's tried again on its own from the end of the one
before, and taken as it comes then, its verses marked unsure. As its end isn't to be trusted, the next
chapter starts from its heading as pocketsphinx's keyword spotting hears it ("chapter seven",
`find_heading.py`). Verses the aligner put past that are spread over what's left of the chapter by
their text's length, unsure too. Then each verse starts 0.05 s before its first word and ends 0.1 s
after its last, and each chapter starts with its heading (`words_to_verses.py`).

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
python3 voice_check.py results/te ../../app/src/main/assets/telugu 4 telugu_*.zip

# ...then word by word, with the Montreal Forced Aligner 3 (conda-forge), about 2 hours in all
conda create -p ./mfa -c conda-forge montreal-forced-aligner && export PATH=$PWD/mfa/bin:$PATH
mfa configure --disable_use_postgres -j 4
python3 telugu_words/to_flac.py results/te audio telugu_*.zip
python3 telugu_words/build_corpus.py results/te ../../app/src/main/assets/telugu corpus 7000
mfa train corpus/train corpus/dict.txt te_model.zip --audio_directory audio --single_speaker -j 4 \
    --config_path telugu_words/train.yaml
mfa align corpus/align corpus/dict.txt te_model.zip aligned --audio_directory audio --single_speaker \
    -j 4 --output_format json --beam 20 --retry_beam 100
# (15 chapters were given up on at that beam: aligned again alone with --beam 400 --retry_beam 2000,
# their words added to aligned/)
python3 telugu_words/words_to_verses.py aligned results/te ../../app/src/main/assets/telugu results/te_words
python3 export.py te results/te_words kjv.tsv ../../data/audio_timing/telugu.json

# The KJV Dramatized Audio (Kjv1-10.zip, kjv11-20.zip, kjv21-40.zip, kjv41-66.zip in zips/), with the
# aligner above and its English models, and pocketsphinx (pip) in the Python $KWS_PYTHON names
mfa model download acoustic english_mfa && mfa model download dictionary english_us_mfa
mfa model download g2p english_us_mfa
python3 english_words/prepare.py books zips drama
python3 english_words/prepare.py words kjv.tsv ~/Documents/MFA/pretrained_models/dictionary/english_us_mfa.dict drama/oov.txt
mfa g2p drama/oov.txt english_us_mfa drama/oov.dict
python3 english_words/prepare.py dictionary ~/Documents/MFA/pretrained_models/dictionary/english_us_mfa.dict \
    drama/oov.dict drama/english_kjv.dict
python3 english_words/chain_align.py kjv.tsv drama/flac drama/english_kjv.dict english_mfa drama/chain 4
python3 english_words/words_to_verses.py drama/chain/state.json kjv.tsv drama/members.json drama/mp4_lengths.json \
    results/drama
python3 export.py drama results/drama kjv.tsv ../../data/audio_timing/kjv_dramatized.json
```

`kjv.tsv` is the app's KJV text, one verse per line: book, chapter, verse, text. Results are kept
chapter by chapter (book by book for Telugu), so a run that stops can be started again to finish.

## What came out (14 Oct 2026)

- KJV: all 1,189 chapters and 31,102 verses (79.9 hours). The three readings agreed on 96.2% of
  verse starts, and the refinement moved 2.2%. 300 chapters had been lost from the first download
  of `ENGKJVO1DA.zip` (long runs of zeros in the file), and 4 more were cut off. They were timed from
  replacement files, each identical to the original by size and checksum.
- Telugu: all 1,189 chapters and 31,101 verses (96.7 hours, 67 files). The three readings agreed on
  93.3% of verse starts, and the refinement moved 3.5%. Checked by ear, some verses started a word
  late; the voice check moved 362 starts back (1.2%). Played a verse at a time, they still ran into
  the next verse or were cut short here and there, so every word was then placed by the forced
  aligner: all 1,189 chapters. Its starts agree with the earlier ones to within 0.15 s for 96% of
  verses and differ by more than 0.3 s for 2.9%. Each verse's length follows its text's
  (median correlation 0.98 a chapter). 56 chapters were read on from the one before with no join,
  and were split at their spoken headings. In Deuteronomy 23 every pause had been silenced in
  editing, which at first was taken for joins and put chapters 23 to 28 out of place.
