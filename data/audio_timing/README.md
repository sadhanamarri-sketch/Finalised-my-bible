# Verse timings

Where each verse starts and ends in two recordings of the Bible, for playing them in the app and
following along verse by verse (backlog item 21). The recordings stay on the phone, never here. Made
with `tools/audio_timing`.

The app carries both files as assets (`app/build.gradle.kts` adds this folder's parent, so they're
`audio_timing/kjv.json` and `audio_timing/telugu.json` there), read by `audio/RecordingTimings.kt`.
`audio/Recordings.kt` matches the files in the folder chosen on the phone to `files` by name, and
takes a file only when its length is the one given there.

Each file is JSON:

```json
{
  "recording": "which recording, and how its files are laid out",
  "how": "how the timings were made",
  "files": {"A01___01_Genesis_____ENGKJVO1DA.mp3": 293.02, ...},
  "chapters": {
    "Genesis 1": {
      "file": "A01___01_Genesis_____ENGKJVO1DA.mp3",
      "start": 0.0, "end": 293.02,
      "verses": [[6.55, 9.58], [10.43, 19.53], ...],
      "unsure": [6, 9, 29]
    }
  }
}
```

- Chapters are keyed by the app's book names and chapter numbers ("Psalms 23", "Song of Solomon 2").
- `file` is the recording's file name. `start` and `end` are the chapter's place in that file, in
  seconds. A chapter's span includes its spoken heading, which comes before verse 1.
- `verses` holds each verse's `[start, end]` in seconds from the start of the file, in verse order:
  the first is verse 1. A verse ends where the pause before the next one begins, never after the
  next one starts.
- `unsure` lists the verses whose three matching runs disagreed by more than 0.3 s. Their starts were
  then chosen by the refinement step, but they're the likeliest to be off.
- `files` gives each recording file's length in seconds, to check against the file on the phone, so
  a different recording is never followed with these timings.

## kjv.json

Faith Comes By Hearing's King James Version, narrated by one reader (ENGKJVO1DA, ENGKJVN1DA). One
file per chapter: 1,189 chapters, 31,102 verses, 79.9 hours. The three runs agreed on 96.2% of verse
starts, and the refinement moved 2.2%. A psalm's title ("A Psalm of David") is read after its
heading and isn't in the app's text, so verse 1 starts after it.

## telugu.json

The Telugu Audio Bible from YGFM (Apostle Ratnaker), which follows the app's Telugu text verse for
verse. One file per book (Psalms in two), its chapters read one after another: 1,189 chapters, 31,101
verses (the app's Telugu Exodus 7 has 24), 96.7 hours, 67 files. A chapter's `start` and `end` are
its place in its book's file.

Every word is placed: a forced aligner (the Montreal Forced Aligner) trained on 21 hours of this
narrator's own verses learnt his voice, then lined up each chapter's whole text with the recording.
A verse starts 0.05 s before its first word and ends 0.1 s after its last. These starts agree with
the earlier timing (by pauses) to within 0.15 s for 96% of verses; `unsure` lists the 911 (2.9%)
where they differ by more than 0.3 s. This narrator leaves only 0.37 s between one verse's last word
and the next one's first (at the median; the KJV's narrator about 0.9 s), so a verse played on its
own must play just its own stretch of the file: one stopped by a clock runs on into the next verse's
first word on a phone, Bluetooth earbuds especially.
