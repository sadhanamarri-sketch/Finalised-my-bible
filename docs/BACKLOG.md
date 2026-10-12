# To build later

Changes and bugs noted for a later build. Items marked **Done** are built; the rest aren't yet.
Each item keeps the original note in quotes, then what it most likely means and what still
needs asking.

Also saved: 21 suggested changes to Notes, in [NOTES_SUGGESTIONS.md](NOTES_SUGGESTIONS.md)
(8 Oct 2026).

## Noted 9 Oct 2026

1. **Done 12 Oct.** **More than one colour on a highlighted verse.** "Multi highlighted
   verses", clarified as "multi colors to one highlighted verse": one verse can carry, say,
   both Promise and Prayer.
   *Built:* a verse can carry up to 3 colours, kept in the order they were put on.
   - *Reader:* each colour is its own short mark at the verse's right edge, each under the one
     before with a gap of a quarter of a mark (decided 12 Oct: "give some gap and include the
     next color under the first ... quarter to half of the bar"); on a verse too short for
     them all, the marks shorten to fit.
   - *Verse sheet:* a swatch puts its colour on or takes it off, and Clear takes them all off.
     The colours show as labels at the top. A fourth isn't added; the sheet says why. A quick
     note goes with the colour put on last.
   - *Highlighted Verses:* one card per verse, with each colour's dot and name; filtering by a
     colour finds every verse that has it.
   - *Verse Scroll:* the highlighter band behind the verse splits into a band per colour,
     with a chip for each by the reference. The Highlight button is filled in the colours
     ("Rose +1"), and the colour box puts colours on or takes them off.
   - *Backups:* merged colour by colour; taking one colour off, or clearing a verse, carries
     over to the other phone. Highlights saved before this still load.
   Today a highlight is one colour per verse (`HighlightItem`, keyed by book, chapter and
   verse), so the stored highlights, Drive backups, the Highlighted Verses page and its colour
   filter, and Verse Scroll's colour box all need to allow several.
   *To ask:* how should two or three colours show on the verse in the Reader (split
   background, stripes, small colour marks)? Is there a limit on how many?

2. **Done 12 Oct.** **Bug — Verse Scroll: part of the next photo shows at the bottom of the current one.**
   *Fixed:* each card's photo is now clipped to its card (`SceneLayer.kt`); its slow zoom made it
   a little larger than the card, and the next card's photo, laid out just below the screen,
   drew up into the bottom of the card on screen.
   "Verse scroll bottom (next picture)", clarified as "a part of next image is seen at the
   bottom of current image in verse scroll." The card's own photo should fill it, with nothing
   of the next card's photo showing.
   *Where to start:* how the photos of the cards on either side are loaded and placed ahead of
   a swipe (`ui/versescroll/VerseScrollScreen.kt`, `SceneLayer.kt`).
   *To check:* on every card or only some, and all the time or only after a swipe?

3. **Done 12 Oct.** **Verse Scroll modes: Full, Focused, or one chapter or book.**
   *Built:* the first tab at the top of Verse Scroll now names the mode (Full, Focused,
   Romans 8, …); tap it to choose another. Full deals from all 29,227 verses, Focused from the
   2,000 best known alone. A book or chapter goes through in reading order, picks up where it
   was left (even after the app is closed), and ends on a card with "Start again" and "Other
   verses"; the chapter open in the Reader is a one-tap choice. The Rabbit hole works in every
   mode, and coming back from one carries on in order, with nothing skipped. The mode chosen
   is kept for next time. *Decided:* Focused at 2,000; a book or chapter in reading order.
   "Verse scroll for
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

4. **Done 12 Oct.** **Verse Scroll of highlighted or studied verses**, "to revisit instead of
   discovering by wandering." Another mode (see 3).
   *Built:* Highlighted and Studied modes, each your verses shuffled: every one once before
   any comes round again. All colours together for now; one colour alone could come later.
   A feed made only of your highlighted verses, or only your studied ones.
   *To ask:* by colour too (only "Promise")? Newest first, Bible order, or shuffled?

5. **Done 12 Oct.** **Keep the focused verse when Telugu or Greek is turned on.** "Focused
   verse ... stay even if Telugu/Greek enabled."
   *Found:* turning Telugu or Greek/Hebrew on or off re-anchored the Reader on the verse at the
   top of the screen and made *that* the focused verse — so after jumping to John 3:16, the
   focus moved to 3:14, the dimming of the other verses went, and 3:16 slid to the bottom of the
   screen; in Blur Mode the focus jumped from the centre verse to the top one.
   *Fixed:* the verse in focus (one jumped to, or Blur Mode's centre verse) stays the focused
   verse, centred, with the others still dimmed, through Telugu and Greek/Hebrew going on and
   off; with no verse in focus, the verse at the top stays at the top, as before
   (`ReaderScreen.kt`, `MainViewModel.toggleTeluguInline`/`toggleInterlinear`).
   The verse in focus should stay in focus and on screen when Telugu or Greek/Hebrew is
   switched on.
   *To ask:* what happens now — does the page move away from the verse, does the focus
   (the other verses dimmed) go away, or both? Hebrew too?

6. **Done 12 Oct.** **Link Greek and Hebrew words myself.** "Ability to link words by myself (Greek and
   Hebrew)."
   *Built (decided 12 Oct: Greek ↔ Hebrew):* each word page has a card of the other language's
   words for it — "Hebrew words for it" on a Greek word, "Greek words for it" on a Hebrew one:
   - the built-in ones, marked "Septuagint" (ahavah for agapē) or by origin (mashiach for
     Messias), and yours, marked "Your link", with a ✕ that removes it after asking;
   - "Link a Hebrew word" (or Greek) opens a search: type the word in Hebrew or Greek, how it
     sounds (chesed, hesed, ches…), its Strong's number (H2617, or just 2617) or what it means
     (kindness, mercy); the likeliest come first, and one already linked says so. Tap one to link it;
   - a link shows on both words' pages, and tapping a linked word opens its page over this
     one, with back coming back here;
   - "Find every verse with this word" finds your linked words' verses too, each a chip that
     can be switched off, with a line saying which you linked ("…and where it has חֶסֶד
     (chesed), which you linked to it");
   - your links are in the Drive and file backups with notes and highlights; one removed stays
     removed when an older backup is restored.
   Add your own link between a Greek word and a Hebrew word, alongside the Septuagint links
   already built in, so the word pages and "Find every verse with this word" use it.
   *To ask:* Greek ↔ Hebrew only, or also an English word in a verse ↔ a Greek or Hebrew word?
   Should your links be backed up to Drive with notes and highlights?

## Noted 11 Oct 2026

7. **Step 1 done 12 Oct.** **Audio Bible, KJV and Telugu BSI.**
   *Built (step 1, the phone's own voice):* reading aloud in English, Telugu or both (each verse
   in English, then in Telugu).
   - *Reader:* "Read aloud" in the menu reads the chapter from the verse at the top of the
     screen; the headphones on a verse's sheet read from that verse. The verse being read is
     marked and kept on screen, and it stops at the chapter's end. A bar over the bottom pill
     pauses, goes on, opens the settings and stops; tapping its verse goes back to it.
   - *Verse Scroll:* the speaker at the top reads each card as it comes up. "Continue reading"
     (in its settings) moves on to the next card by itself, hands-free, skipping check-ins.
   - *Screen off:* it keeps reading, with a notification (and the lock screen's and a headset's
     controls) to pause, play and stop. A call or another app's sound pauses it, as does
     unplugging headphones.
   - *Settings:* the language, the speed (0.75× to 1.5×), and a voice for English and for
     Telugu, each tried as it's picked. A phone without a Telugu voice is told so, with a button
     to get one.
   *Still to come (step 2):* Bible Brain's narrated KJV, once the API key arrives.
   "Is it possible for audio bible for kjv and telugu bsi?"
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
         **Found 11 Oct (link from you):** Bible Brain *does* have the Old Version as audio —
         Bible.is `TCWOVV`, "Old Version", recordings `TCWOVVN2DA` (MP3, 64 kbps) and
         `TCWOVVN2DA-opus16`. It's the New Testament only (27 books, 260 chapters), dramatised
         (several voices), with no text there, so it couldn't be compared word for word here;
         you listened and found it matches the app's Telugu text. The Old Testament would need
         the phone's voice. Still to check through the API: whether it has verse timings.
       - *Delivery:* streamed a chapter at a time (MP3 at 64 kbps, about half a megabyte a
         minute; also smaller Opus and HLS streams). Listening offline is allowed only for
         recordings the API marks as downloadable, kept inside the app.
       - *To build it:* a key requested in your name (the licence is between the app's
         maker and Faith Comes By Hearing), and the build environment allowed to reach
         `4.dbt.io` to test against.
   - *Other Telugu options looked at, 11 Oct:*
     - YouVersion's Old Version audio (`TELUBSI`) is there by BSI's permission, for
       YouVersion's own app; whether it covers the Old Testament wasn't confirmed, and
       there's no way for another app to use it without BSI.
     - A complete-looking Telugu MP3 set (sermon-online) names no translation, is missing 26
       chapters, and its rights are unknown — not usable.
     - A natural-sounding AI voice could record the whole Old Version ahead of time, one
       file per verse (Azure has two Telugu neural voices; AI4Bharat's open models IndicF5
       and Indic Parler-TTS also speak Telugu). The text is 3.7 million characters. But
       handing out recordings of BSI's text is an audio edition, which BSI's terms keep
       for their permission — so this needs BSI's say too, and if asking BSI, their own
       human recording is the better thing to ask for.
   - **Decided 12 Oct — the plan to build:**
     - *Build in two steps.* First, the phone's own voice for both KJV and Telugu, the same
       way. Then, once the Bible Brain key arrives (requested 12 Oct), add Bible Brain's
       narration for KJV, with the phone's voice staying as KJV's fallback.
     - *Telugu:* the phone's own voice (text-to-speech) for the whole Bible — exactly the
       app's BSI text, read a verse at a time with each verse highlighted as it's read,
       offline, free, and no recordings handed out (the phone reads the text, as a screen
       reader would). The voice and speed are the reader's to pick.
     - *KJV:* Bible Brain's human narration for the whole Bible, with the phone's voice when
       offline or when Bible Brain can't be reached. Needs an API key from Faith Comes By
       Hearing (requested 12 Oct) and `4.dbt.io` allowed in the build environment to test
       against. The key goes in the environment's settings as `BIBLE_BRAIN_API_KEY` (never
       in the chat), and for the APK as a GitHub Actions secret of the same name — the
       repository is public, so the key must never be committed.
     - Not doing: Bible Brain's dramatised Telugu Old Version New Testament (`TCWOVV`), a
       pre-recorded AI voice, or asking BSI for their recording.
     - *Playing (decided 12 Oct):* stops at the end of the chapter. Keeps playing with the
       screen off or the app in the background, with a notification to pause and play.
     - *Bible Brain's KJV (checked 12 Oct on Bible.is):* the whole Bible, in two styles —
       *narrated*, one voice (`ENGKJVO1DA` Old Testament, `ENGKJVN1DA` New Testament), and
       *dramatised*, a cast with sound effects (`ENGKJVO2DA`, `ENGKJVN2DA`); each as MP3 at
       64 kbps or Opus at 16 kbps. Better-known KJV readings — Alexander Scourby (1950s),
       Max McLean (Listener's Bible), David Cochran Heath (New Testament and Psalms), Paul
       Mims, James Earl Jones (New Testament) — are all sold commercially, with no app
       licence or verse timings on offer, so Bible Brain stays the choice.
     - *KJV style (decided 12 Oct):* the reader's choice — a setting with Narrated and
       Dramatised, starting on Narrated, the closer of the two to reading along.
     - *Added 12 Oct — Verse Scroll reads aloud too:* the verse on the card is read out, in
       the same voice and settings as the Reader.
     - *Added 12 Oct — a language setting:* read in English, Telugu, or both.
     - *Added 12 Oct — a "Continue reading" setting (automatic skip):* in Verse Scroll, once
       a card's verse has been read, move on to the next card by itself and read that,
       hands-free, until paused.
     - *Decided 12 Oct:* with both languages, each verse is read in English and then in
       Telugu, verse by verse, in the Reader and in Verse Scroll. "Continue reading" is for
       Verse Scroll only: the Reader always stops at the chapter's end.
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

8. **Done 12 Oct.** **Pronunciation for Greek and Hebrew words.** "Can pronunciation be included for greek and
   hebrew?"
   *Built (decided 12 Oct: written and a speaker):* under the transliteration, each word page
   says how the word sounds, its syllables split and the stressed one in capitals — ἀγάπη
   "ah-GAH-pay", בְּרֵאשִׁית "beh-reh-SHEET", חֶסֶד "KHEH-sed" — in the pronunciation usually
   taught for Biblical Greek and Hebrew. Greek's comes from the word's own letters and accent;
   Hebrew's from STEPBible's syllables and stress. The speaker beside it says the word in the
   phone's Greek or Hebrew voice (modern pronunciation), pausing anything being read aloud; on
   a phone without that voice, the page says so, with "Get one" to install it.
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

9. **Done 12 Oct.** **Verse Scroll from a word search or a topic.**
   *Built:* a "Scroll these" button beside Search's result count and under a Nave's topic's
   title shows those verses in Verse Scroll, shuffled; back returns to the search or topic.
   Greek and Hebrew word searches have it too ("Find every verse with this word": the top bar
   says "agapē", not "G26"). A search's or topic's verses aren't kept as the mode: opening
   Verse Scroll from the Reader goes back to the mode chosen before.
   "Can scroll verse take up the word search,
   topic search?" Another mode (see 3): after searching for "love", or opening Nave's topic
   "Faith", scroll through just those verses.
   *To ask:* a button on the results ("Scroll these"), in result order or shuffled? Greek and
   Hebrew word searches too?

## Noted 12 Oct 2026

10. **Done 12 Oct.** **Bug — going from Verse Scroll to the Reader still isn't smooth.**
    *Fixed:* every page now sits on its own background inside the tab switch (`MainActivity.kt`),
    so the Reader's page fades in over the photo with its words: checked frame by frame, the
    switch now spreads over 0.3 seconds with no jump at the end. "The transition from
    verse scroll to reader is still not smooth." On 7 Oct the switch became a 0.3-second
    dissolve, the Reader fading in over Verse Scroll (`VERSE_SCROLL_DISSOLVE_MS` in
    `MainActivity.kt`), and the old chapter's flash was removed; something still looks
    rough.
    **Found 12 Oct, from your screen recording (Hosea 6:11, dark theme, 74 frames):**
    the Reader's words, banner, "Mark Hosea 6 studied" and bar fade in *on top of* the
    Verse Scroll card for 0.3 seconds — two sets of words and two bars over the wheat photo
    (1.80–2.07 s) — and then, in a single frame (2.10 s), the photo is swapped for the
    Reader's dark page: a hard cut, the screen's brightness dropping from 60 to 34 at once.
    *Why:* the Reader paints no background of its own (its root `Box` in `ReaderScreen.kt`
    leaves that to the app's background behind every page). In the dissolve the Reader
    fades in over Verse Scroll, which stays fully drawn underneath until the end — so only
    the Reader's words fade in, and its page colour only appears when Verse Scroll is
    removed.
    *Fix:* give the page fading in its own background, so the dark (or paper) page itself
    fades in over the photo and the words with it — a true dissolve, nothing doubled and no
    cut at the end. A small change; check it frame by frame afterwards.

11. **Done 12 Oct.** **The reading voice sounds shrill, and the voices sound alike.** "The speech
    of both english and telugu seems like a girl child screaming", and "almost all the voices
    seemed same too."
    *Why:* reading aloud uses the phone's own speech voices, and on most phones the usual
    English (India) and Telugu voices are women's, fairly high. The app never set a pitch, so
    a pitch raised in the phone's own speech settings carried over. The voice list showed
    most voices twice — once to use offline, once online — which are the same voice, under
    names ("India 1", "India 2") that said nothing about how each sounds.
    *Built (decided 12 Oct: all three of the phone-voice fixes; Telugu stays the phone's voice):*
    - *Pitch:* Deep, Lower or Usual in the read-aloud settings, starting on Lower. The app
      sets it itself, for reading, a voice's sample and the Greek and Hebrew word speaker.
    - *Deepest voice first:* each of the phone's offline voices says a sentence into a file
      once, and the app measures how deep it speaks (a man's voice about 85–155 Hz, a woman's
      165–255). The voices are listed from the deepest, each marked Deep, Medium or High
      with a play button, and reading is in the deepest one unless another is picked — a
      man's voice, if the phone has one. Measured while nothing is being said (paused, or
      after a reading); the results are kept. An online voice is listed only when it has no
      offline copy.
    - *More voices:* "Get more voices" opens the speech engine's voice downloads (Google's
      has more, some of them men's), and "Phone speech settings" the phone's own.
    *Still to come:* Bible Brain's recorded KJV (a man's voice, narrated), once the API key
    arrives (item 7).
    **Added 12 Oct — "it does sound robotic... can it install new AI voice?"** Looked into:
    the Free Use Bible API (its only audio is the Berean Standard Bible, not the KJV, and no
    Telugu audio); AI voice apps that install as a speech engine (VoxSherpa TTS, SherpaTTS —
    natural English, offline, free; no Telugu); and cloud AI voices (Azure, Google, Sarvam,
    ElevenLabs — natural Telugu too, with your own key and a monthly limit). You tried
    VoxSherpa and found it good.
    *Built:* each language has its own speech engine in the read-aloud settings ("English
    speech engine": the phone's usual one or VoxSherpa TTS, or any other installed), with
    that engine's voices to pick from; Telugu and the Greek and Hebrew word speaker stay on
    the phone's usual engine unless changed. Speed and pitch are sliders, speed 0.5× to 3×
    and pitch 0.5× to 1.5×, in steps of 0.05, with Reset.
