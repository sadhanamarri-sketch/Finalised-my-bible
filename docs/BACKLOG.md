# To build later

Changes and bugs noted for a later build. Nothing here is built yet. Each item keeps the
original note in quotes, then what it most likely means and what still needs asking.

Also saved: 21 suggested changes to Notes, in [NOTES_SUGGESTIONS.md](NOTES_SUGGESTIONS.md)
(8 Oct 2026).

## Noted 9 Oct 2026

1. **More than one colour on a highlighted verse.** "Multi highlighted verses", clarified as
   "multi colors to one highlighted verse": one verse can carry, say, both Promise and Prayer.
   Today a highlight is one colour per verse (`HighlightItem`, keyed by book, chapter and
   verse), so the stored highlights, Drive backups, the Highlighted Verses page and its colour
   filter, and Verse Scroll's colour box all need to allow several.
   *To ask:* how should two or three colours show on the verse in the Reader (split
   background, stripes, small colour marks)? Is there a limit on how many?

2. **Bug — Verse Scroll: part of the next photo shows at the bottom of the current one.**
   "Verse scroll bottom (next picture)", clarified as "a part of next image is seen at the
   bottom of current image in verse scroll." The card's own photo should fill it, with nothing
   of the next card's photo showing.
   *Where to start:* how the photos of the cards on either side are loaded and placed ahead of
   a swipe (`ui/versescroll/VerseScrollScreen.kt`, `SceneLayer.kt`).
   *To check:* on every card or only some, and all the time or only after a swipe?

3. **Verse Scroll modes: Full, Focused, or one chapter or book.** "Verse scroll for
   particular chapter or book (to stay on context instead of wandering)", and added 11 Oct:
   "scroll verse should have modes. like 29k will be full, 2.6k you earlier created will be
   focused."
   - *Full:* every verse that cross references point to — 29,227 verses, today's Discover
     pool (`assets/verse_scroll/discover.tsv`).
   - *Focused:* only the best-known verses. Today that's the pool's "familiar" tier of 2,000
     verses, dealt two cards in three (the first Verse Scroll used 1,600).
   - *One chapter or book:* pick John or Romans 8 and every card comes from there.
   Items 4 and 9 are more modes of the same kind.
   *To ask:* Focused at 2,000, or about 2,600 as remembered? For a chapter or book, cards in
   reading order or shuffled, and may Discover's linked verses still lead outside it?

4. **Verse Scroll of highlighted or studied verses**, "to revisit instead of discovering by
   wandering." Another mode (see 3).
   A feed made only of your highlighted verses, or only your studied ones.
   *To ask:* by colour too (only "Promise")? Newest first, Bible order, or shuffled?

5. **Keep the focused verse when Telugu or Greek is turned on.** "Focused verse ... stay even
   if Telugu/Greek enabled."
   The verse in focus should stay in focus and on screen when Telugu or Greek/Hebrew is
   switched on.
   *To ask:* what happens now — does the page move away from the verse, does the focus
   (the other verses dimmed) go away, or both? Hebrew too?

6. **Link Greek and Hebrew words myself.** "Ability to link words by myself (Greek and
   Hebrew)."
   Add your own link between a Greek word and a Hebrew word, alongside the Septuagint links
   already built in, so the word pages and "Find every verse with this word" use it.
   *To ask:* Greek ↔ Hebrew only, or also an English word in a verse ↔ a Greek or Hebrew word?
   Should your links be backed up to Drive with notes and highlights?

## Noted 11 Oct 2026

7. **Audio Bible, KJV and Telugu BSI.** "Is it possible for audio bible for kjv and telugu bsi?"
   Possible, in two ways:
   - *The phone's own voice (text-to-speech)* reads the exact text on screen, verse by verse,
     following along in the Reader. Free and offline, no files to download, but it sounds
     synthetic. English works on any phone; Telugu needs a Telugu voice installed (Google's
     speech engine offers one in the phone's settings).
   - *Recorded narration* sounds far better, but the recordings are copyrighted and large
     (a whole Bible is about 70 hours of audio, a gigabyte or more per language, so it would
     stream or download a book at a time).
     - KJV: LibriVox volunteers' KJV readings are public domain in the US, but they're 73
       separate projects by different readers; whether every book is covered needs checking.
     - Telugu BSI: the Bible Society of India's published terms (seen for its Tamil Bible)
       allow only short audio excerpts without written permission, so their recordings would
       need their permission. A Telugu audio Bible under an open licence (CC BY-SA) does exist,
       but for a different translation (Indian Revised Version), so its wording wouldn't match
       the app's Telugu text.
     - Faith Comes By Hearing's Bible Brain API (looked into 11 Oct, from search results;
       its own site and API are blocked from the build environment, so the licence wasn't
       read first-hand):
       - *Cost and terms:* free, with an API key they approve on request. The licence
         reportedly requires the app to be free to its users, and lets them restrict or
         withdraw any content at any time.
       - *KJV:* yes, narrated (fileset IDs like `ENGKJVO1DA`, Old Testament, MP3), and KJV
         appears to have verse timings, so the Reader could follow along verse by verse and
         start playing from any verse.
       - *Telugu:* audio exists for other translations — the Easy-to-Read Version (a
         dramatised New Testament, Bible.is `TELWTC`), the Indian Revised Version (`TELDPI`)
         and Grace Ministries (`TELGMI`) — but nothing found for the BSI text the app shows,
         so the words heard wouldn't match the words on screen. Checked on Bible.is on 11 Oct
         against the app's Telugu text in Genesis 1, Psalm 23, John 1, John 3 and Romans 8:
         not one of about 160 verses is the same in any of the three (the Indian Revised
         Version is closest, about 64% alike; the others 51% and 59%). Verse numbers do line
         up. The BSI Old Version does have audio on YouVersion (Bible.com, `TELUBSI`, there
         by BSI's permission) — that's YouVersion, not Bible Brain.
       - *Delivery:* streamed a chapter at a time (MP3 at 64 kbps, about half a megabyte a
         minute; also smaller Opus and HLS streams). Listening offline is allowed only for
         recordings the API marks as downloadable, kept inside the app.
       - *To build it:* a key requested in your name (the licence is between the app's
         maker and Faith Comes By Hearing), and the build environment allowed to reach
         `4.dbt.io` to test against.
   *To ask:* is the phone's voice good enough to start with, or only real narration? For
   Telugu, the phone's voice reading the BSI text, or a recording of a different
   translation? Play a chapter, or continue through the book?
   Sources: [LibriVox KJV group](https://librivox.org/group/360),
   [Telugu IRV audio on Bible.com](https://my.bible.com/audio-bible/1895/JHN.3.IRVTEL),
   [Bible Society of India](https://www.bsind.org/category/uncategorized/page/32/),
   [Bible Brain](https://www.faithcomesbyhearing.com/audio-bible-resources/bible-brain),
   [Bible Brain licence](https://www.faithcomesbyhearing.com/bible-brain/license),
   [Bible Brain core concepts](https://www.faithcomesbyhearing.com/bible-brain/core-concepts),
   [a review of Bible Brain's terms](https://learnofchrist.com/resources/bible-brain-api),
   [BibleBrainSharp (timestamps example)](https://github.com/MarkIvanDev/BibleBrainSharp),
   [Telugu ERV](https://www.southasiabibles.com/resource/language/telugu/Easy-to-Read-Version),
   [Telugu IRV](https://southasiabibles.com/resource/language/Telugu/Indian-Revised-Version),
   [Telugu dramatised New Testament](https://podbay.fm/p/-telugu-bible-dramatized).

8. **Pronunciation for Greek and Hebrew words.** "Can pronunciation be included for greek and
   hebrew?"
   Possible, in two ways:
   - *Written:* a sounds-like guide next to the transliteration the word pages already show
     (from STEPBible). For Hebrew it's ready: Open Scriptures' Strong's data, which the app's
     data tools already read for King James renderings, has one for 8,674 words ("awb" for
     אָב, H1). For Greek that data has only the transliteration ("agápē"), so the guide
     ("ag-ah'-pay") needs another source, or rules that turn the transliteration into one.
   - *Spoken:* a speaker button using the phone's Greek or Hebrew voice. Those voices speak
     modern Greek and modern Hebrew, which differ from how Koine Greek and Biblical Hebrew are
     usually taught.
   *To ask:* written only, or a speaker button too?

9. **Verse Scroll from a word search or a topic.** "Can scroll verse take up the word search,
   topic search?" Another mode (see 3): after searching for "love", or opening Nave's topic
   "Faith", scroll through just those verses.
   *To ask:* a button on the results ("Scroll these"), in result order or shuffled? Greek and
   Hebrew word searches too?
