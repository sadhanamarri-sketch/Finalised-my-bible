# To build later

Changes and bugs noted for a later build. Items marked **Done** are built; the rest aren't yet.
Each item keeps the original note in quotes, then what it most likely means and what still
needs asking.

Also saved: 21 suggested changes to Notes, in [NOTES_SUGGESTIONS.md](NOTES_SUGGESTIONS.md)
(8 Oct 2026).

**On the phone:** build #108 (14 Oct 2026, commit 6b9fb64), and beside it **My Bible (Dev)**, a debug
build of 23 Oct (commit 4f803a1). The notes of 29 Oct were made on the Dev build (they speak of the round
reading button, Verse Scroll's progress bar and the new notes page, which only it has). A bug seen on the
phone is looked for first in the code of the build it was seen on.

**Sent 30 Oct:** a newer My Bible (Dev) at commit 61c9b8b, with everything marked Done up to 53 (the
Share button). It installs over the 23 Oct Dev build and keeps its data (it's signed with the same
checked-in debug key). Once it's installed, it's the Dev build on the phone. (An earlier one sent the same
day, at ebba364, hadn't 53.)

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
    **Changed 12 Oct — "voxsherpa is too slow to load... let's get rid of it... the speed and
    pitch should be separate for english and telugu."**
    *Built:* the speech engine choice is gone: reading is by the phone's usual engine again,
    in the deepest voice unless one is picked (a voice picked from the AI voice app is
    cleared). English and Telugu each have their own panel in the read-aloud settings, with
    their own speed and pitch sliders and their own voice; a panel shows for each language
    being read. The one speed and pitch kept before start both languages' settings. A change
    applies at once to the language being read, and to the other when it comes up; with
    nothing being read, a few words let you hear it. Greek and Hebrew words are said at
    English's pitch.

12. **Done 12 Oct.** **Verse Scroll: no highlighter behind the verse.** "There's no need to
    highlight the verse in verse scroll as we already show the tags below."
    *Built:* the verse's words stay plain on the card; the colour tags beside the reference
    show every colour on it. On a passage, a colour on only some of its verses names them
    ("SUNSHINE · 4"), so a verse highlighted in the Reader still shows when it comes up in
    a passage; tags that don't fit go on to a second line. A double-tap still pops the ring
    under your finger and opens the colour box.

13. **Done 12 Oct.** **Verse Scroll's settings, like the rest of the app's.** "Can we revamp the
    settings of verse scroll in sync with rest of the app?"
    *Built:* Verse Scroll's settings are laid out like the app's Settings page, from the same
    parts: gold section headings (Background, Double-tap color, Telugu, Reading aloud), a
    switch for each on/off setting with a line about it, dividers between sections, the
    same text-size row, and an outline button on to reading aloud's settings. "Gentle
    motion" shows only with nature photos on, and can't be turned on when the phone is set
    to reduce motion. Reading aloud's settings, which open from there and from the Reader,
    match too: "Read in" is a segmented choice like Settings' reminder frequency, each
    language a section, and "Get more voices", "Speech settings" and "Get a Telugu voice"
    outline buttons. Both sheets use the page's own colour, so their controls show in the
    dark themes as they do on the Settings page.

14. **Done 12 Oct.** **Verse Scroll: bigger Telugu.** "Increase the font of telugu in verse
    scroll a bit because it's too small to read."
    *Built:* the Telugu under each verse is 18 (it was 15), the Reader's own Telugu size, and
    can be set from 14 to 24 in Verse Scroll's settings, under the Telugu switch. It runs to
    six lines before "…" (it was four), so most verses show it whole.

15. **Done 12 Oct.** **Earbuds' gestures for reading aloud.** "Can the scroll verse continue reading
    sync earbuds gestures?"
    *Built:* reading aloud answers a headset's or earbuds' media controls, as a music app does.
    A tap pauses or plays. "Next" (a double tap on most earbuds; a wired
    headset's button pressed twice) goes on to the next card in Verse Scroll, with the screen
    moving to it, or the next verse in the Reader. "Previous" (usually a triple tap) starts the
    card or verse again, or, in its first three seconds, goes back to the one before. Continue
    reading carries on from wherever a skip lands; paused, a skip waits there. The notification
    and the lock screen have Previous and Next buttons too, and Stop shows on newer Android's
    lock-screen controls. Which tap does what is set by the earbuds themselves (or their app).
    **Found 12 Oct, testing the build: no gesture reached the app, not even a tap.** Android hands
    a headset's buttons to the app whose sound played last, and the reading's sound is the speech
    engine's — another app's — so they went elsewhere. *Fixed:* while it reads, the app now plays
    silence in its own name (`Silence.kt`), so the buttons come to it; it stops when paused, and
    the buttons still come here to play again. It also keeps a Bluetooth headset's sound open
    between verses, so a verse's first word isn't clipped.

16. **Done 12 Oct.** **The Reader's own controls for reading aloud.** "I will test and then we can
    bring it to reader as well with addition control and touch and hold to go to start of the
    chapter." (After testing the earbuds: "it worked.")
    *Built:* the Reader's reading-aloud bar has Previous and Next beside play and pause. Previous
    starts the verse again, or in its first three seconds goes back a verse, as the earbuds'
    does; touching and holding it reads the chapter from its first verse, wherever the reading
    began (with a buzz to say it took). Paused, these move there and wait. Your earbuds' own
    touch-and-hold is noise cancellation, set in their app, and never reaches other apps, so the
    hold is on the screen's button; their tap, double tap and triple tap are play and pause, next
    and previous, which the Reader and Verse Scroll answer.

17. **Done 12 Oct.** **Verse Scroll from a search: what found each verse, and a filter by it.** "I
    should be able to filter to choose which category in scroll verse and it should also display
    the category just it does for highlighted verses." (The category: the word that matched.)
    *Built:* each card from a search's Scroll these says what found its verse, in outlined gold
    chips by the reference, beside its highlight colours: the form of the word it has ("LOVE",
    "LOVETH"), the King James wording for it ("CHARITY"), or a Greek or Hebrew word with its
    meaning ("GREEK AGAPAŌ", "HEBREW AHAV"). A search typed in Greek or Hebrew says the word's
    meaning there when it has several ("FORGIVE", "PERMIT"), or the word itself, and the other
    Testament's word ("HEBREW AHAVAH"). A search of several words says the words as typed, or the
    wording for them ("HOLY GHOST"). Tapping the search's name at the top lists those words under
    "Searched for", "King James wording" and "Greek & Hebrew", each with how many verses it found
    ("love 281", "charity 24"). Pick any of them to scroll only their verses; the button says how
    many ("Show 31 verses") and Clear picks none, which is all of them. The choice is put to use
    as the sheet closes, however it's closed, so reading aloud doesn't start over at every tap.
    A search with only one such word (a case-sensitive "LORD") or a topic's verses has no chips
    and nothing to pick.

18. **Done 12 Oct.** **Search's chips, open until picked.** "In search results too ... instead of
    auto selecting the cards automatically... leave them open."
    *Built:* the chips under the search box are now the same words as Verse Scroll's choice of a
    search's verses: the word in each form it was found in ("love 281", "loved 89"), the King
    James wording ("KJV: charity 24") and the Greek and Hebrew words ("Greek agapē 26"), each
    with how many verses it found, in the place the filter button chose. None is picked at first,
    which is all of the verses; picking some shows only theirs ("26 of 480 verses"), and Show
    all, next to that count above the list, picks none again. They used to start all switched on, each to be
    switched off. A verse said in other words now counts for everything that says it there:
    1 Corinthians 13:4's "charity" is the King James wording for love and agapē in the Greek, so
    either chip finds it. Scroll these takes all the verses with the picked words still picked,
    so Verse Scroll starts with the same verses and its choice of verses can widen them. On a
    Greek or Hebrew word's page, a meaning ("forgive 38") opens Search with that meaning picked,
    and the other Testament's word with it, the other meanings there to pick too. A search in
    Telugu has its words as typed and "Inside longer words".

19. **Done 13 Oct.** **Each language's speech engine, back.** "When I press get more voices, it
    takes to voxsherpa tts and I can't select anything... can we provision that I can pick from
    there another tts other than default one? last time i tried kokoro which is slow... now i
    want to try piper." — "yeah bring back per language engine choice."
    *Why "Get more voices" went to VoxSherpa:* the button asked Android for "a speech engine's
    page for voices" without naming one, and Android handed it to VoxSherpa, whose page for that
    has nothing to choose. Its voices are chosen in the VoxSherpa app itself.
    *Built:* in the read-aloud settings, English and Telugu each have a "Speech engine" choice
    when the phone has more than one: the phone's usual one (Google's) or an AI voice app such as
    VoxSherpa TTS, whose Piper voices are lighter and quicker than Kokoro's. The voice list is
    then that engine's, and a voice picked before (the other engine's) goes. With an AI voice app
    and no voice picked, it reads in the app's own voice; only the usual engine's voices are
    measured for depth. An AI voice app can take a moment to start and may not change the pitch;
    the settings say so. "Get more voices", now under each language, opens that language's
    engine itself: the VoxSherpa app, or Google's page for voices, never left for Android to
    pick. An engine that has no Telugu voice says so under Telugu. An engine chosen and since
    uninstalled goes back to the phone's usual one. Greek and Hebrew words were still said by the
    phone's usual engine (until 20). An engine no longer chosen for either language is let go, so
    an AI voice app's model doesn't stay in memory.

20. **Done 13 Oct.** **A Greek or Hebrew word said as its "Sounds like" shows.** "hebrew and greek
    pronunciation doesn't match with the word"
    *Why:* the speaker on a word's page used the phone's Greek or Hebrew voice, which says a word
    as Greek and Hebrew are spoken today (ἀγάπη as "a-YA-pee"), while "Sounds like" spells it as
    Biblical Greek and Hebrew are usually taught ("ah-GAH-pay"). And a phone whose usual speech
    engine is an AI voice app could say it in an English voice reading Greek letters.
    *Built:* the speaker says what "Sounds like" shows, in the English reading voice and pitch, a
    little slower. No Greek or Hebrew voice is needed, and any English engine will do, an AI voice
    app's too. An English voice reads the spelling as English, so it's given the same sounds
    spelled as English spells them: lowercase (capitals get read as letters), "eigh" for a lone ay
    (else it's "eye"), "eye" for a lone ai (else it's "A I"), "gh" before e as in "get", an eh
    closed by the sound after it ("tet-tah": English has no eh ending a syllable, and reads one as
    "ay"), "ss" for a final s, and a cluster English can't begin a word with said with the
    syllable before (ek-seh), or, beginning the word, without its first sound, as English says
    pneumonia. The stressed syllable starts a part of its own, where an English voice puts the
    stress: ah-GAH-pay is said "ah gah-pay", beh-reh-SHEET "bare-resh sheet". Checked on every
    word of the Greek New Testament and the Hebrew Old Testament against the pronouncing rules
    Piper's voices use: all but 0.2% of the syllables come out as spelled, and those few nearly
    ("heh" with its h breathed). "No voice on this phone to say it with yet" shows only when there's
    no English voice at all. A Hebrew word ending in oy now shows it as oy (goy, not gohy).

## Noted 14 Oct 2026

21. **Done 15 Oct.** **Your own recordings, timed once and played verse by verse.** "If I have
    bible audio of telugu and kjv without timed.. can we generate timed? and the audio stays in the
    files storage and we just play like a music player with timed file." — "timing is just one
    time thing for each chapter right? ... a separate app can do it and we store the json in the
    app" — "is it possible to include the speed as well? will it work with verse scroll?" —
    "let's not implement everything right away... keep it in the list."
    *The plan:* the recordings stay in the phone's storage, and the app is pointed at the KJV
    folder and the Telugu folder once. Each recording is timed once, off the phone: espeak
    reads each verse of the app's text, that is matched to the narration by its sound, each
    verse's start is moved onto the narrator's pause, and three runs at different speeds vote.
    Only the timing file goes in the app: every verse's start and end, about 0.6 MB a language
    for the whole Bible. The app checks each file's length against the one timed, so a
    different recording is never followed wrongly. Then it plays like a music player — screen
    off, lock screen, earbuds — with each verse lit up as it's read and a tap on a verse going
    there. The English and Telugu speeds apply, the narrator's pitch kept. Verse Scroll plays
    each card's own verse, from its start to its end; English then Telugu plays each verse from
    both recordings; a verse with no recording is read by the phone's voice. The recordings
    never go in the repository, which is public.
    *Tried 14 Oct on Ruth* (Telugu, "Telugu Audio Bible YGFM", 18 minutes, the whole book in
    one file): four chapters joined end to end, each opening with about 3 s of a spoken
    heading, a low sound (music or hiss) under the narration throughout, and the narrator
    pausing as much inside verses as between them, so pauses alone couldn't place the verses.
    All 85 verse starts came out on a pause: 79 the same at all three speeds, and 5 settled by
    the vote (2:7, 4:5, 4:15, 4:16, 4:18). Each verse's length followed its text's (0.98).
    Checked by ear on a private page, at 0.75× to 2× and a verse at a time: "this is
    excellent." Ruth's timing file is 1.6 KB, about a minute's work a book.
    *Timed 14 Oct, the whole Bible in both* (`data/audio_timing/`, made with
    `tools/audio_timing/`): the KJV from Faith Comes By Hearing (one reader, one file per
    chapter, 79.9 hours) and the whole Telugu recording (YGFM, one file per book, 96.7 hours),
    both downloaded from Google Drive. Every chapter of both: 31,102 and 31,101 verses, in
    timing files of 0.64 MB and 0.76 MB. The three runs agreed on 96.2% (KJV) and 93.3%
    (Telugu) of verse starts. A last step chooses each start among the nearby pauses by how
    long each verse should take, and keeps verse 1 out of the chapter's spoken heading. Tested
    on good starts knocked onto a wrong pause, it put back 90–96% of them. 300 KJV chapters had
    been lost in the first download (runs of zeros) and 4 cut off. They were timed from
    replacements, checked against the originals by size and checksum. In the Telugu, 56
    chapters ran on from the one before with no break in the file and were found by their
    spoken headings. In Deuteronomy 23 every pause had been silenced in editing, which at first
    was taken for breaks between chapters. Six chapters of each are on a private page to check
    by ear.
    *Checked by ear 15 Oct* (a verse at a time, Bluetooth earbuds, in the Claude app): the
    English was fine. In the Telugu "almost all of them include first or two words of next
    verse.. and some verses where timing is marked with ≈ first word of that particular verse
    misses." A Bluetooth delay (stopping a verse early) made it "not consistent": "same verses
    cut off early and some overflow to first word of next and some are fine... if bluetooth
    earbuds is an issue why english works perfectly?"
    *What it was, and what was done:* about one Telugu verse in thirty was a word off, early or
    late: espeak's Telugu voice is far from this narrator's, and he pauses inside verses as much
    as between them. Every Telugu word is now placed by a forced aligner trained on the
    narrator's own voice, which put those right. And the Telugu narrator leaves only 0.37 s
    between one verse's last word and the next one's first (the English about 0.9 s), so a verse
    stopped by a clock ran on into the next one: on a phone the sound runs a moment past the
    stop, by more or less each time. In English that moment falls in silence. The check page now
    plays a verse on its own as just its own stretch of the recording, so nothing after it can
    be heard.
    *For the player:* a verse played on its own (Verse Scroll's cards) must play just that
    stretch of the file (Media3's clipping), never play on and stop at its end time.
    *Built 15 Oct:* "Can we implement just the timed recordings now leave rest of backlog alone
    which can be implemented later." In the reading-aloud settings (the reading bar's, Verse
    Scroll's, or Settings › Reading aloud), English and Telugu each choose Recording or Phone's
    voice. Recording asks once for the folder its files are in (one folder can hold both); they
    stay there just as they came, read through Android's folder access, never copied. Each file the
    timings name is found by its name (a lost double space or another Unicode form aside) and
    looked over: MP3 or M4A (32 of the Telugu books are M4As named .mp3), whole (a stretch of zeros
    or a file cut short isn't), and the length that was timed, to within a second. The settings say
    how many chapters are ready and name the rest ("Not in the folder, so read by the phone's
    voice: 1 Samuel 16–31, ..."). Media3's player plays each verse from 0.04 s before it starts to
    its end and no further. In one language the Reader's chapter plays on through its verses as one
    stretch of the file, its pauses and any music as recorded, the verse marked moving on as each
    begins; read from verse 1, it starts with the chapter's spoken heading. Both: each verse from
    the KJV recording, then the Telugu. Verse Scroll: each card's own verses. A pause picks up
    where it was; next, previous, the earbuds, the notification and the lock screen work as for the
    phone's voice; each language's speed applies, the narrator's pitch kept. A verse the recording
    hasn't (its file missing, damaged or moved) is read by the phone's voice.
    *Checked on the recordings themselves:* every file of both looked over as on a phone, all as
    timed (to 0.03 s); the 304 damaged in the first KJV Old Testament zip are its replacements'
    (`missed_chapters.zip`, `four.zip`) to put in the folder. Media3 seeking to every verse start in
    ten files (KJV and Telugu; MP3, M4A and Ruth's MP3 whose bitrate varies) lands on the same
    frame, at the same time, as reading the file from its start, as the timings were made.
    *Not built:* a tap on a verse going there (with the rest of the Reader's reading aloud, 23).
    *The KJV Dramatized Audio, 19–20 Oct:* "I don't like the english version ao i downloaded a new
    version from internet archive. org ... you can find that in drive." 66 MP4 videos, one a book
    (76 hours, 4.6 GB), read by a cast with music. Timed by its words: every word of the KJV placed
    by a forced aligner, with what's said besides the verses (each chapter's "Chapter seven", the
    books' openings, and the psalms' titles, which the app's text hasn't). Where the cast's voices,
    music or effects threw it (a crowd in Revelation 6, the serpent in Genesis 3, Sinai in Exodus),
    the chapters after could come out late one after another; speech recognition found those runs
    and they were timed again chapter by chapter from their spoken headings. All 1,189 chapters
    (20 Oct: of the six chapters first spread by their text's length, five were placed by a wider
    search, and Jeremiah 51 once Jeremiah 49 to 52 were timed from their headings). The 911 verses
    (2.9%) it was still unsure of were each checked by ear on a page made for it ("go ahead ...
    give everything I will try to do as many as I can"), all of them in a day: 667 passed, 244
    given their starts by tapping along or moving them a quarter second at a time (144 where the
    aligner was wrong, by up to 38 s). That also found 1 Samuel 25's first verses half a minute
    late, now timed again. No verse is unsure. The
    app takes an English folder for whichever recording it holds more of (this or Faith Comes By
    Hearing's), says which it found, and plays only the videos' sound. *To use it:* the 66 MP4s
    unzipped into one folder, named as they came (their sound alone, as .m4a or .mp3, works too),
    chosen under Reading aloud › English › Recording.
    *Fixed 20 Oct, Both stopping after a verse's English:* "a bug that stops the playing abruptly
    ... like john 9 ... if I play both english and telugu ... english plays and it doesn't move to
    telugu ... if we play english or telugu individually ... there's no problem." Only below 1x
    (English at 0.85x, Telugu at 0.9x; "I tried with speed x1 for both and it didn't cause the
    issue"). Media3 1.5.1's speed changer, at the end of a stretch, could owe less than nothing and
    never finish, so the player never said the stretch had ended and the reading waited on "English
    recording" for good (fixed in Media3 1.6.0: "Fix underflow in Sonic#getOutputSize() that could
    cause DefaultAudioSink to stall"). Its own code run over real verses at those speeds, a verse's
    clip at a time as the player cuts them, left 7 of 132 English (0.85x) and 28 of Hebrews' 303
    Telugu (0.9x) unfinished, among them Hebrews 8:2 ("stops after telugu in reader"); 1.6's, none.
    Both ends a stretch after every verse's English and its Telugu, and Verse Scroll after every
    card, so it showed there; one language in the Reader plays the chapter as one stretch. Now Media3
    1.6.1, and a stretch that sits still within a second of its end for three seconds while playing
    is taken as ended (`EndWatch` in RecordingPlayer), so nothing in the player can stop a reading
    that way again.

22. **The Gospels as films, verse by verse.** "Is it possible to stream videos in the same
    way... i found new videos of gospel verse wise no deviation."
    *Possible the same way:* a film's sound is timed like a recording, and the film plays
    above the verses, each lit up as it's said. Where the films are decides how: files that
    can be downloaded work like the recordings (a few GB a Gospel); YouTube's play only in
    YouTube's own player inside the app, which can't keep playing with the screen off, and
    can't be downloaded to be timed; Faith Comes By Hearing's Gospel films (on Bible.is) could
    be streamed through Bible Brain once its key arrives. A film in another translation lines
    up verse by verse, but its words won't be the app's.
    *Needs:* a link to the films.

## Noted 15 Oct 2026

23. **Done 21 Oct.** **Reading aloud in the Reader: any verse, any time.** "right now I dont have the provision to
    go back to verse I wanted (meaning if it's reading from verse 1 and it's on verse 14 and I
    want to hear verse 4 now), that doesn't work. And also assess how jump to verse and clicking
    read aloud. And in blur mode. Let's access every possibility and resolve it in reader tab for
    read aloud." — "disable highlighted verse pop up while reading and enable only when paused.
    Reasoning is we may not concentrate/listen/miss the current recording anyway if it's playing
    and we are trying to highlight something." — "can telugu and english selection be in sync
    with pill buttons current selection? Like If I select both for read aloud and అ is not
    selected in pill, it will be automatically select. Like wise if only telugu is selected."
    *Today:* a reading is one chapter, from the verse it starts at to the chapter's end, and
    nothing else on screen moves it. Going back to verse 4 means its sheet's "Read aloud from
    here", which starts a new reading, or Previous, a verse at a time. A tap on a verse opens its
    sheet (highlights, notes), and the reading bar goes while it's open. The menu's Read aloud
    starts from a verse half hidden at the top of the screen (after a jump, a few verses above the
    one jumped to), and while it reads, it starts again from there. A jump or a swipe to another
    chapter leaves it reading the old one. In Blur Mode the centre verse and the one being read
    are both sharp, and a tap on a blurred verse turns Blur Mode off. The reading bar hides with
    the pill as the page scrolls.
    *Agreed 15 Oct* (not built yet):
    - *Playing, the page is for listening:* a tap anywhere on a verse (its words, its Telugu, its
      Greek or Hebrew words, its cross-reference mark) reads from that verse at once, in the same
      language and speed. Nothing else opens: no sheet, dictionary, word page or cross
      references, and a long press (marking studied) does nothing. A tap on the verse being read
      starts it again. After a tap moves the reading, the bar offers Undo (back to the verse it
      was on) for a few seconds, for a stray tap or for hearing one verse again. With the pill
      hidden, a tap still reads the verse. Picking verses (for a note, or marking studied) still
      picks.
    - *Paused, everything works as now:* a tap opens the verse's sheet, and the reading bar stays
      on screen with it. The sheet's Read aloud from here (the headphones) plays from that verse,
      as does Play and then a tap. Play on the bar, or the earbuds, goes on where it stopped and
      closes the sheet. With the pill hidden, the first tap brings it back, as now.
    - *Read aloud in the menu:* after a jump (a search, a cross reference, highlights, the verse
      picker), from the verse jumped to; otherwise from the first verse wholly on screen; in Blur
      Mode, from the sharp one. While it reads, the menu says Stop reading aloud instead.
    - *Another chapter while it reads:* it keeps reading where it was, so a cross reference is a
      quick look. The bar says what it's reading ("Reading John 3:16"), with Back (go there) and
      Read here (this chapter, from the verse at the top). A tap on a verse there reads from it.
    - *Scrolled away from the verse being read:* a Follow button on the bar brings it back and
      keeps it in view.
    - *Blur Mode:* while it reads, only the verse being read is sharp, kept at the centre as the
      reading moves on, and a tap on any verse reads it. Paused or not reading, a tap brings a
      blurred verse into focus and a second opens its sheet. Blur Mode turns off only from its
      own button.
    - *The reading bar* stays on screen while it reads, no longer hiding with the pill.
    - *The chapter's end:* it stops, as now, and the bar offers Next chapter for a few seconds.
    - *The notification* opens the Reader at the verse being read. The earbuds' Next and Previous
      stay as they are.
    - *Telling people:* the first reading after this shows one line above the bar, once: "Tap a
      verse to hear it · Pause for highlights and notes".
    - *The pill's అ follows the reading's language:* a reading started in Telugu or Both, or
      switched to either while it reads, turns అ on if it's off. That happens at every start, as
      the app starts with అ off each time while the reading's language is remembered. English
      leaves అ alone. Turned off by hand during a reading, it stays off until the next reading is
      started (a tap that moves the reading isn't one), and it stays on when the reading stops.
      The pill never changes what's read. Turning Telugu on keeps the verse being read where it
      is on screen. Verse Scroll's own Telugu switch does the same. The line being said is lit:
      with Both, the English and then the Telugu; with Telugu alone, the Telugu.
    *To build it:* the read-aloud engine needs a "move to this verse" that keeps the reading
    going (today it can only start a new one). The taps, Blur Mode's focus, where a reading starts
    and the bar's states all build on that.
    అ itself isn't remembered: it starts off at every launch, as now (decided 15 Oct).
    *Built 21 Oct* ("start with the list one by one"), as agreed. The reader-aloud's moveTo goes
    to a verse of the reading at once and reads on from it, from a recording or the phone's voice
    alike (paused, it plays). What the bar offers sits over it in small pills: Undo ("From 3:16")
    for five seconds after a tap moved the reading; Follow while the verse being read is out of
    view; "Reading John 3:16" with Back and Read here while another chapter is on screen; "End of
    John 3" with Next chapter for eight seconds once a chapter's been read through (it opens the
    next chapter and reads it from its heading). The sheet's "Read aloud from here", paused in the
    same chapter, moves the reading too, and the paused bar sits at the top of the sheet. With
    the pill hidden the bar stays, down where the pill was. The line being said is lit rather
    than the whole verse (its Telugu when that's shown). In Blur Mode, outside a reading, a tap on
    a blurred verse brings it to the middle as the sharp one (a drag hands focus back to the
    middle). A reading in Telugu or both turns అ on keeping the verse it reads from where it is
    on screen; Verse Scroll's Telugu comes on the same way.

## Noted 20 Oct 2026

24. **Done 21 Oct.** **Reading aloud stops when the app is swiped away.** "Player continued in background even
    after killing the app."
    *Today:* the reading's service isn't told when the app is swiped away from Recents (nothing
    asks Android to stop it with the app, and it doesn't watch for it), and the reader-aloud lasts
    as long as the app's process does, so a Reader reading plays on. Only Verse Scroll's stops,
    with its feed.
    *Agreed 20 Oct* (not built yet): swiping the app away from Recents stops any reading, in the
    Reader or Verse Scroll, by the phone's voice or a recording, and its notification goes.
    Turning the screen off or going to another app keeps it playing, as now.
    *To build it:* ReadAloudService's onTaskRemoved stops the reader-aloud and takes the
    notification down itself (a stopped reading's own teardown runs in a collector that
    onDestroy cancels, and a paused reading's notification is detached), then stops the service.
    *Built 21 Oct,* just so: swiped away playing or paused, the reading stops, its notification
    goes, and the service stops.

25. **Done 21 Oct.** **Verse Scroll: a pause between cards, and a gentler slide.** "Provide a setting to set
    delay(pause for few secs) the scrolling during continue reading(auto-scroll). Also snoother
    transition instead of quick swap." — "let's add pause duration to settings and i choose
    gentler, smoother slide(as there is a pause already planned."
    *Today:* with Continue reading on, the next card is read the moment the last one ends, while
    the slide to it is still moving, and the slide is Compose's quick default spring.
    *Agreed 20 Oct* (not built yet):
    - *Pause between cards*, in Verse Scroll's settings under Reading aloud: Off, 2, 3, 5 or 10
      seconds (3 to start with, proposed).
    - *A gentler slide* to the next card: slower and eased, under a second. A slide, not a fade,
      as the pause already gives a breath.
    - The next card is read once it has settled. The earbuds' Next during the pause goes on at
      once; pausing or stopping during it works as now.
    *To build it:* the wait goes in CardReading.next(), before it moves to the next card, which
    runs within the reader-aloud's search for the next verse; the slide is the pager's
    animateScrollToPage with an eased tween; the reading starts when the scroll has finished.
    Next needs to cut the wait short: today skips are ignored while the next verse is being found.
    *Built 21 Oct:* *Pause between cards* shows under Continue reading while it's on, in Verse
    Scroll's settings and in the reading-aloud sheet opened from Verse Scroll: Off, 2 s, 3 s (to
    start with), 5 s or 10 s. The slide to the next card takes 0.7 s, eased in and out, and the
    card is read once it has settled (waiting 2 s at most; with the screen off, not at all).
    During the pause the earbuds' or the notification's Next goes on at once, and Previous reads
    the card's verse again. Paused during it, the reading stays on its card until it's played
    again (then it slides on and reads), rather than moving on while paused; stopping stops.
    Fixed on the way: as more cards were dealt during a slide the reading had asked for, the
    card it was leaving could be taken for the one shown and read again; and a slide a swipe
    interrupted was never let go of.

26. **Done 21 Oct.** **Reading on past the chapter, and a sleep timer.** "Provide setting to continue infinitely in
    reader mode in read along with sleep timer." — "timer doesn't pause as it's really a sleep
    timer(provide a notification to disable it or anything that is better than a notification yo
    cancel the timer), keep reading across books as an option in the settings (end of chapter,
    end of book, continuous)"
    *Today:* a Reader reading stops at the chapter's end, and there's no timer.
    *Agreed 20 Oct* (not built yet):
    - *Read until*, in the reading-aloud settings: End of chapter (as now), End of book, or
      Continuous, on into the next book (after Revelation it stops).
    - Going on, the next chapter starts with its spoken heading when it's played from a
      recording; the Reader turns to it while you're following along; the reading bar and the
      notification name the chapter being read.
    - *Sleep timer:* 15, 30, 45, 60 or 90 minutes, or the end of this chapter (proposed), set from
      the reading bar or the reading settings. When it runs out the reading stops (a sleep timer,
      not a pause), fading out over its last seconds (proposed). It works for Verse Scroll's
      readings too (proposed).
    - *Cancelling it,* rather than a notification of its own: the time left shows on the reading
      bar, and a tap there cancels or changes it; and the reading's own notification, which shows
      on the lock screen, gives the time left and a Cancel timer button.
    *To build it:* a reading that goes past its chapter (the next-chapter order is in
    MainViewModel.nextChapter) and whose lookups compare book and chapter, not just the verse
    number as ChapterReading's do; the reading's title, fixed when it starts today, follows the
    chapter; the Reader's follow-along changes chapter (today it only scrolls within the one on
    screen). This replaces 23's "the bar offers Next chapter" when Read until isn't End of
    chapter. The timer lives in the reader-aloud (a job in its scope, the time left in its state
    for the bar and the notification); the notification's Cancel timer goes through the service.
    *Built 21 Oct:* *Read until* comes second in the reading-aloud settings (from the Reader or
    the app's Settings), after Read in: End of chapter (to start with), End of book, Continuous.
    Going on, the reading's name follows the chapter (the bar, the notification, the lock
    screen), a recording starts each chapter from its spoken heading, previous at a chapter's
    first verse goes back into the chapter it came from, and held, it goes to the first verse of
    the chapter it's in now. Followed along (the verse being read last seen on screen), the
    Reader turns to the next chapter as the reading comes to it (with the screen off meanwhile,
    as it comes back on); looking at another chapter, the bar offers Back and Read here instead.
    Next chapter is offered only with End of chapter.
    *The sleep timer:* a moon on the reading bar opens 15, 30 or 45 minutes, 1 hour, 1½ hours,
    or End of this chapter; once set, the moon shows the time left ("23 min", "40 s", "Chapter")
    and its menu has Cancel timer too. The reading settings have it while something is read
    (Off to 90 minutes side by side, End of this chapter a switch). In Verse Scroll, set from its
    reading settings, a chip under the top bar shows the time left and opens the same menu. It
    runs on while paused; over its last 8 seconds the reading fades out (a recording smoothly,
    the phone's voice from verse to verse), and then it stops. A reading started before it runs
    out keeps it; one that stops takes it with it. The notification says "sleep timer: 23 min
    left" and has Cancel timer, as do the lock screen's controls. End of this chapter stops
    there even with Read until set further; Next pressed at the chapter's last verse still goes
    on.

27. **Done 21 Oct.** **Highlighting in Verse Scroll: just the colour box.** "The default color is not necessary as I
    have unclick the default one always. Exclude that functionality. It just pop up the color
    selection that's all." — "I'm okay with your suggestion"
    *Today:* in Verse Scroll a double-tap, or the bottom bar's Highlight, puts the double-tap
    colour (Sunshine, unless changed) on at once and then opens the colour box, and a colour
    picked there is added to it, so Sunshine has to be unticked. The Reader already only opens
    the colours.
    *Agreed 20 Oct* (not built yet): a double-tap and Highlight only open the colour box, and
    nothing goes on until a colour is tapped, which is then the verse's colour. The box stays
    open until a colour is picked or you tap elsewhere (today it hides after 6 seconds). The
    "Double-tap color" setting goes, the first-time hint says a double-tap chooses a colour, and
    the little ring burst comes when the colour is picked.
    *To build it:* doubleTap() and the Highlight button only call showPicker (the setColor before
    it goes), and the first pick puts the colour on through toggleColor; the doubleTapColor
    preference and its settings row go; a quick note in Verse Scroll's verse sheet goes with a
    highlight, as in the Reader (today it falls back to the double-tap colour).
    *Built 21 Oct,* just so: a double-tap or Highlight opens the colour box and puts nothing on;
    the box stays until a colour is picked (then it closes a moment later) or the verse is
    touched, swiped or Back pressed; picked after a double-tap, the ring pops in that colour
    where the finger was. The hint reads "Double-tap a verse to choose its color", and Verse
    Scroll's settings no longer have Double-tap color.

28. **Done 21 Oct.** **Verse Scroll: the English dictionary.** "Dictionary in verse scroll." — "Only English in
    scroll verse." — "no seperate button required right? can't we just tap the word like in
    reader?"
    *Today:* the Reader looks English words up in Webster's 1828 (about 61,800 words, with Save):
    a tap on a word opens its sheet. Verse Scroll has no way to it; a card's text is one tap area
    (a double-tap highlights, a long press opens the verse's sheet).
    *Agreed 20 Oct* (not built yet): a tap on an English word on the card opens its Webster's
    1828 sheet over Verse Scroll, as in the Reader; closing it comes back to the card. No button.
    English only, no Greek or Hebrew. As a double-tap still highlights, the sheet opens about a
    third of a second after the tap (once it's clear it isn't a double-tap). A tap on the space
    around the words isn't a look-up (it plays or pauses the reading, 30). Reading aloud pauses
    while a word's sheet is open and plays on by itself when it's closed ("i like pausing when
    looking up ... it resumes automatically when word window is closed").
    *To build it:* the card's text becomes tappable word by word, as the Reader's verses are
    (VerseComponents), within the card's own tap handling (double-tap, long press); the Reader's
    openEnglishWordLookup already opens the sheet over any tab.
    *Built 21 Oct,* just so: each English word on a card is marked as the Reader's are, and a
    single tap on one (on the words themselves, not the space around them, a verse number or
    "…") opens its Webster's 1828 sheet about a third of a second later. A double-tap on a word
    still opens the colour box, with no look-up; the Telugu under a verse isn't looked up.
    Reading aloud, whether a card's or the Reader's, pauses while the sheet is open and plays on
    when it closes.

29. **Done 21 Oct.** **Old Testament quotations in the New, in purple.** "Quotes/references in new testament from
    old testament." — "We already have cross references right? why can't we use them? the real
    quotes get new color and cross in reader will also get a different color which has quotes."
    — "real quotes only. let's go with purple"
    *Today:* the cross references (openbible.info's, downloaded) don't say which links are
    quotations. Checked 20 Oct: they hold 68,945 links between the Testaments; Nave's list of the
    New Testament's quotations ("Quotations and allusions") pairs 243, and 242 of them are among
    the cross references already. Matching the KJV's words finds only about half of those (114
    share five words or more in a row: the KJV often words a quote differently in the New
    Testament, translated from the Greek, as Matthew 2:15 and Hosea 11:1), and it finds clear
    quotations Nave's lacks (Mark 12:26 and Exodus 3:6, Mark 14:27 and Zechariah 13:7, Hebrews
    4:7 and Psalm 95:7, Acts 2:34 and Psalm 110:1, about 30), with the odd false one (Luke 7:40
    and 1 Kings 2:14 share a phrase).
    *Agreed 20 Oct* (not built yet):
    - Real quotations only, no allusions or echoes: Nave's pairs that are quotes, and the word
      matches, checked by hand, about 250 to 300, shipped as a small list that labels links among
      the cross references (which stay as they are).
    - *The Reader:* a verse's cross-reference mark (†, gold today) is purple when the verse quotes
      the Old Testament or is quoted in the New.
    - *Cross References:* the quotations come first, in purple, with a small "Quote" tag, and
      always show, however few votes they have (today only the top 20 by votes do).
    - *Verse Scroll's links sheet:* the same.
    *To build it:* the list first (tools/, from the cross references, Nave's topics and the KJV,
    reviewed), then the mark's colour, the Cross References order and tag, and the links sheet.
    *Built 21 Oct,* just so: 461 quotations over 379 New Testament verses (tools/quotations). The
    672 New Testament verses and passages the cross references, Nave's and the KJV's words put
    forward were each read and judged, and clear quotations they missed were added by hand (Matthew 19:5 and Genesis 2:24
    share no five words in a row). A verse counts when it reproduces a passage's words as a
    citation, introduced as one or close to word for word; each verse holding quoted words is
    marked, on both sides (Matthew 2:15 and Hosea 11:1 alike). Revelation's images, titles and
    scenes count as allusions, so only 8 of its verses are in (14:8, "Babylon is fallen, is
    fallen", from Isaiah 21:9). The Reader's † is purple on those verses. Cross References and the
    links sheet list them first, tagged "Quote", and when a link names a longer passage they show
    the verse holding the quotation (Psalm 110:1's link to Matthew 22:42-46 shows 22:44). 13
    quotations the cross references don't link at all, such as Mark 15:28 and Isaiah 53:12, can't
    be labelled and are left out.

30. **Done 21 Oct.** **Verse Scroll: a tap to play or pause, and the reading kept through a visit to the Reader.**
    "Pause/play in scroll verse by tapping the screen and change the stopping of speech after
    return from reader to scroll verse." — "wait for my tap."
    *Today:* a single tap on a card only closes the colour box, and reading is started and
    stopped with the speaker button at the top. Going to the Reader ("Read") stops the reading
    and forgets where it was; back in Verse Scroll the speaker is off, and the card starts over.
    *Agreed 20 Oct* (not built yet):
    - A tap on the card, anywhere but its words (a word is a look-up, 28), plays or pauses the
      reading, and starts reading the card when nothing is being read. As a double-tap still
      highlights, it acts about a third of a second after the tap.
    - Going to the Reader pauses the reading where it is, rather than stopping it.
    - Coming back, it waits, paused, for your tap, and then plays on from where it paused.
    *To build it:* the switch to the Reader's tab pauses instead of stopping Verse Scroll's
    reading, and the controller's own resume-where-it-stopped path plays it on; its speaker-on
    state becomes the reading's (today a pause turns it off); the tap is the card's onTap, which
    waits out the double-tap. A Reader reading started meanwhile still ends Verse Scroll's, and
    then a tap starts the card over.
    *Built 21 Oct,* just so: a tap on a card anywhere but a word (the space around the words, a
    verse number, the photo) plays or pauses the reading about a third of a second later, or starts
    reading the card when nothing is being read (or what's paused is another card's). A tap that
    closes the colour box does only that. "Read" pauses the reading instead of stopping it; back in
    Verse Scroll it waits, paused, for a tap and plays on from where it paused, and the Reader's
    reading bar doesn't show it meanwhile. A reading started in the Reader ends it, and a tap then
    starts the card over. Going to any other tab still stops it. The speaker button at the top
    works as before (a pause still turns it off) until it becomes the language button (31). With
    nothing to read with (Telugu alone, and no Telugu voice), a tap opens the reading settings,
    which say why.

31. **Done 21 Oct.** **A one-tap language button: English, Telugu, Both.** "Easy switch for eng, tel and both for
    scroll verse and reader read aloud." — "replace speaker button (now that we have pause/play
    feature) with one tap button that cycles."
    *Today:* the language is chosen only in the reading settings, and with the phone's voice a
    change waits for the next verse.
    *Agreed 20 Oct* (not built yet):
    - In Verse Scroll, the speaker button at the top becomes a language button: a tap cycles
      English, Telugu, Both (playing and pausing are a tap on the card, 30).
    - The Reader's reading bar gets the same button.
    - Changed while reading, the verse being read starts again in the new language at once,
      from a recording or the phone's voice alike.
    - Without a Telugu voice or recording, Telugu and Both are left out of the cycle (proposed).
    *To build it:* the button shows the language and calls updateLanguage, then restartVerse()
    whenever a reading is going (today only for recordings); the bar needs the settings passed
    in; restartVerse does nothing while the next verse is being found, so a change then applies
    from that verse.
    *Built 21 Oct,* just so: a small key shows the language, A, అ, or Aఅ for both, and a tap moves
    it on. In Verse Scroll it takes the speaker's place at the top, gold while the cards are read,
    and says "Reading in Telugu" as it changes. On the Reader's reading bar it sits after the verse
    being read, whose line under it now just says what's heard ("English", "Telugu recording"), to
    leave the verse its room. Changed while a verse is read, the verse starts again in the new
    language at once; paused, it waits, and plays on in the new language. The reading settings'
    "Read in" does the same now. Without a Telugu voice or recording, a tap opens the reading
    settings, which say why. Verse Scroll's settings and first hint now say a tap on a card reads
    it aloud.

32. **Done 22 Oct.** **Verse Scroll: a progress bar on the card, with a dot where the Telugu starts.** "why not a
    progress bar that can be scrolled just like in the doomscrolling apps? With a dot indicating
    the switch between english and telugu." — "Verse Scroll only for the progress bar(only if
    it's a small change)."
    *Today:* nothing shows how far a card's reading has got, and the only moves are a verse on or
    back (the earbuds, the notification).
    *Agreed 20 Oct, kept small* (not built yet):
    - A thin bar along the bottom of the card fills as the card is read, and a dot marks each
      switch between English and Telugu (with Both).
    - A drag or a tap on it moves the reading to that point of the verse being read, from a
      recording or the phone's voice (the voice from the nearest word); a tap on a dot goes to
      that language's start. On a card of several verses, a drag into another verse starts that
      verse from its beginning.
    - It thickens while held, as a reel's does, in its own strip at the card's foot, apart from
      the card's taps (28, 30) and the swipes between cards. Verse Scroll only.
    *To build it:* the reader-aloud shares how far it has got (a recording's position against the
    verse's timings, the voice's word as it reports it) and takes "go to this point of this part":
    for a recording, the part again from that second, as a paused one resumes; for the voice,
    from that character, as a pause picks up at a word. The bar's lengths come from the timings,
    or the text's length for the voice.
    *Built 22 Oct,* just so: while a card is read, a thin bar above "N verses today" fills as it
    goes, with a dot where its English gives way to its Telugu (Both). A tap moves the reading
    there, the voice from the word there, a recording from that moment; a tap by the dot goes to
    the Telugu's start. Held, the bar thickens, a thumb follows the finger, and the reading goes
    where it's let go. Paused, a move waits there, and a tap on the card plays on from it. On a
    card of a few verses each has its stretch, and a tap in another reads it from its start. The
    bar takes its own touches, apart from the card's taps and the swipes. A recording's lengths are
    its own; the voice's are guessed from the text (about 15 characters a second in English, 13 in
    Telugu, at the speed set), so there the dot's place is near enough rather than exact. A voice
    that doesn't say which word it's on fills the bar at that pace.

    *Set aside 20 Oct:* AI-made pictures or videos for the cards, for surprise ("let's scrap AI
    generated content... I don't have the budget right now"): they'd need a paid image service.
    (Taken up again 22 Oct, made by hand in ChatGPT Go instead: 33.)

## Noted 22 Oct 2026

33. **Done 22 Oct.** **Verse Scroll: your own pictures, one for every scene, each shown first.** "i
    think chatgpt go is better... Let's discuss to get prompts for prominent verses." — "when this
    picture is picked, I want it display first without verse and verse fades in later." — "picture
    cards only, 3 seconds. photo real, full people, and I want zoomed out version like capturing the
    essence of the situation like peter walking on water - the sea should be shown too. Not just 60
    verses... I want to do this for every verse possible. Let's start with book wise." — "go with
    sample picture style exactly and other features we talked about. Can you also provide document
    as well for prompts separately?"
    *Agreed 22 Oct:*
    - Pictures made by hand in ChatGPT from prompts written here, a book at a time from Genesis,
      one to a scene (a run of verses showing one moment), so every verse has one. In the look of the
      first one made: warm and cinematic, photo-real, with the people in it, wide enough to show the
      whole situation; tall (9:16), everyone's heads in the lower half (the verse sits over the
      middle), no words. God only ever as light. The same people described the same way in every
      prompt, to keep their looks.
    - Kept in a folder on the phone, chosen once. A picture is found by its name ("Genesis 1.3-5": a
      dot for the colon), so ones named by hand work too, as do a chapter's ("Psalms 23"), several
      chapters' ("Genesis 6-9"), a book's ("Psalms"), more for one place ("(2)", taking turns) and
      ones named for no place (now and then on any card).
    - A Pictures page from Verse Scroll's settings, book by book: each scene with its verses and
      title, Copy prompt, Add (then Change and Remove), a look at it, and how many are done; "Add
      several in order" fills the empty scenes with pictures picked one after another, then shows
      where each went. A picture added there is shrunk to a card's size, named and put in the folder.
      No adding from a card.
    - A card with its own picture shows it alone for 3 seconds, then the verse fades in: once a
      scene; a tap brings the verse at once; a reading waits for it, the wait counting toward the
      pause between cards; none with the screen off; a plain fade with less motion; a setting to
      turn it off.
    - The prompts in a page of their own too, with copy buttons, and as a document.
    *Built 22 Oct,* just so. Genesis is 370 scenes covering its 1,533 verses, with a cast of 37
    (tools/pictures has the scenes, the cast and the scripts; docs/pictures/Genesis.md has every
    prompt, with the name to save it under). Verse Scroll's settings have "Your pictures": Show your
    pictures, Picture first, and the Pictures page; the nature photos can be off while your pictures
    still show. On the Pictures page a folder is chosen first (a new empty one is best), then each
    scene has Copy prompt and Add (Change once it has one); its menu has Copy name, Add several from
    here and Remove; a tap on a picture shows it whole, with Change and Remove. "Add several in
    order" starts at the first empty scene after the last one done and fills the empty scenes after
    it, in the order the pictures were picked, then lists where each went, with Undo for any that
    landed wrong. A picture added is made at most 1080 by 1920 and saved as WebP (150 to 250 KB),
    the new one written before the old one is taken away. How much each picture is dimmed under the
    verse is measured once, the way the photos' was (fit_veils.py, now in the app), and kept. A
    card shows the picture that fits its verse best (its scene's, then a chapter's, then a book's,
    a book's not on two cards running); several for one place take turns visit by visit. Of pictures
    named for no place, only ones named "Any…" ("Any sunrise.jpg") go on any card, about one in six
    without its own: the folder may be Download, where ChatGPT's pictures land, and the phone's other
    pictures there are left alone. The
    picture shows alone for 3 seconds once it has come to rest on screen, then the verse and the
    dimming come in over most of a second, the verse rising a little into place (not with less
    motion). The next card of the same scene shows its verse at once; one swiped past before its
    verse came counts as shown. A tap on the picture brings the verse and nothing else; a reading
    of the card starts once the verse is there, and going on by itself, its pause between cards is
    3 seconds shorter for a card shown first (none with the screen off, where the verse is read at
    once). Pictures put in the folder show up the next time Verse Scroll opens; one added on the
    page shows at once (on the card on screen, without being shown first). The prompts page:
    https://claude.ai/artifact/3BmZ5PyWLcdHkrcpVdsPQU (private until shared).
    Not tried on a phone: choosing the folder, saving into it and the phone's photo picker (the
    checks here can't open Android's folder access), so the first pictures added are the real test.
    *Dropped 22 Oct:* the prompts ("drop the idea of prompts... you are way too much usage"), after
    the owner found them wrong in places ("only a pair of animals when first created, Noah's family
    are only 8 in total, the olive leaf is when water is not completely gone"; "genesis 1:3-5...
    there are no people yet"). A redo of Genesis true to the text was begun and stopped:
    tools/pictures/redo has what was written (chapters 1-11, 23-30 and 40-50). The app's Copy prompt
    buttons, the document and the page still have the first prompts.

34. **Done 22 Oct.** **Reader: reading aloud, the page and the bar.** "the read aloud pill is
    occupying... ideas on how to hide it like we hide the pill in reader" — "let's analyse how it
    behaves and what's the best way to handle... I am thinking what's the point of scrolling during
    read aloud when playing... and how it behaves in blur mode. .when a verse is highlighted from
    jump to" — "i like these as it is along with the pause/play button... what's the transition of
    button when hide and unhide?" — "got it.. built these".
    *Found 22 Oct:* scrolled away while it read, the page stopped following until Follow; in Blur
    Mode everything on screen was then blurred (the sharp verse being the one read, off screen), and
    paused, the sharp verse went to the one in the middle; read aloud from a verse jumped to, the
    verses after it were read blurred, the jumped-to one sharp, until the page moved.
    *Agreed 22 Oct:*
    - Scrolling while it plays is for looking around: like a song's words in a music player, 5
      seconds after the page is let go it goes back to the verse being read (Follow still there).
    - Blur Mode, looked around: the verse in the middle is the sharp one; back at the reading, the
      one being read. Paused: the one being read stays sharp until the page is dragged or another
      verse tapped.
    - A reading playing ends a jump's spotlight, as scrolling away does.
    - While it plays, the bar is drawn in to a round pause button at the right, its ring filling,
      gold, as the chapter's read; paused, the full bar. It draws in from the left (the controls
      fading first, the sign and ring fading in as it lands) and back out (play shown for a moment
      first), as quickly as the pill slides; it goes down into the corner with the pill and up
      again; a reading starts with the full bar for 3 seconds; stopped, it fades and drops away;
      what the bar offers (Follow, Undo, Back and Read here) sits over the button, at its right.
    *Built 22 Oct,* just so. The 5 seconds count from the finger leaving the page, or a fling coming
    to rest; while looking around the page isn't moved by the reading, and a reading that goes on into
    the next chapter meanwhile turns the page to it when it goes back. A tap on a verse while it
    plays (or Read aloud on a paused verse's sheet) is followed from that verse. Paused, the page
    stays where it's put until the reading plays again. The button is 56dp, 16dp from the right,
    just over the pill; the ring is the verse's place in its chapter (verse 16 of 36, a little under
    half). With a verse's sheet open the bar is the full bar as before.

35. **Done 22 Oct.** **Notes: the writing page, to the best standard.** "bring up all suggestions
    for notes" — "can you review the formatting tools in note editor as well?" — "Can we add tab
    and hide keyboard as well? can you review thoroughly tue notes editor and the formatting tools?
    I want the best standard" — "build all 5 steps you mentioned as best standards".
    *Agreed 22 Oct* (numbers from `docs/NOTES_SUGGESTIONS.md`), in five steps: never lose a note
    (1–4, 35); typing that works (33, 34); a better way to write (36, 22, 23, Undo/Redo from 8, 39,
    30, 31, 32); a toolbar that fits a phone (24, 25, 40, 28); finishing touches (29, 37, 43, 44,
    42, 41, and the bug 38).
    *Built 22 Oct:*
    - Notes are in the app's database, a row each (moved once from the old list). Delete happens at
      once, with Undo, as on the Studied page.
    - A note is saved as it's written (0.8 seconds after a change) and kept through turning the
      phone or going to the Reader for a verse, so Back just closes it. A note left empty is deleted,
      with Undo. The ⋮ menu has Discard changes (back to the note as it was opened; grey when nothing
      has changed) and Delete note.
    - One page: the title, a strip of chips (the verses, each with ×, and + Verse; the date, from a
      date picker; the tags and + Tag), then the text. Undo and Redo at the top.
    - The text is one box, so a selection runs across paragraphs, and the keyboard isn't restarted
      at every letter (swipe typing and word suggestions keep working). Sentences start with a
      capital.
    - The toolbar over the keyboard: Aa · B · I · highlight · • · ☐ · Shift-Tab · Tab | hide
      keyboard, each lit for the style at the cursor and named for TalkBack. Aa opens Title,
      Heading, Subheading and Text, then underline, numbered list, quote, left/centre/right and
      Clear formatting. Text size is out of the toolbar; sizes in older notes still show.
    - B, I, U and highlight with nothing selected apply to what's typed next. New text takes the
      style of the letter before it, so typing just before bold text isn't bold.
    - "- " or "* " starts a bullet list, "1. " a numbered one, "[] " a checklist, "> " a quote and
      "# " a heading. Enter on an empty item ends the list (a sub-item moves out a level first);
      Backspace at an item's start takes off its mark, then its indent, then joins it to the line
      above. Tab and Shift-Tab nest an item up to 4 levels (• ◦ ▪; 1. a. i.). On a keyboard: Tab,
      Shift-Tab, Ctrl+B, I, U, Z and Y.
    - Checklist boxes tick and untick with a tap, in the editor and on the note page; a ticked item
      is greyed and struck through.
    - The note page shows a note as the editor does. Its text can be selected and copied, and web
      addresses open the browser as verse references open the verse.
    - An empty page says "Start writing…"; a note opens with the cursor at its end.
    *Not yet tried on a phone:* Gboard (its underlined word, swipe typing), the date picker, and
    taps past a line's end. On a 392dp-wide screen the toolbar's 9 buttons are 43dp wide each, to
    fit, not 48dp. Still to do from the list: 5–7, Insert verse (8) and 9–21.

36. **Done 22 Oct.** **Notes: sizes that sit with the rest of the app, and Aa in the keyboard's
    place.** "how 3 lines of formatting displays handle against the keyboard on the screen? And
    text sizes of notes list and note editor, note reader seems off" — "i picked the sizes but they
    look a bit odd compared to rest of the app . They look very big which is not acceptable in UI.
    what's your recommendation for that?" — "yes build both, no apk".
    *Found 22 Oct:* Aa stacked two rows over the toolbar while the keyboard was up, about 107dp,
    and stayed open while typing: about 8 lines left to write in instead of 12, and none with the
    phone on its side. Notes are in Noto Serif, whose small letters are 13% taller than Source
    Serif 4's (the verse cards on Search and Highlights, 17sp), so 19sp note text looked about a
    quarter bigger than a verse card, the list's 18sp a fifth bigger, with lines 1.8× apart (cards
    1.71×); most of the app's text is 12–17sp. The new editor had also made Title and Heading lines
    bigger (26 and 22sp, a Title line bigger than the note's own title) and Subheading near text
    size, and given the writing page's title its own look (24sp dark serif).
    *Built 22 Oct:*
    - Text 16sp with lines 1.7× apart on the writing page and the note page; Title 21sp, Heading
      18sp, Subheading back to a small grey label (13sp); list marks, tick boxes and indents
      sized to match. The notes list's preview 15sp, 1.7× (it looks the size of a verse card), its
      title 14sp. Verse, date and tag chips 13sp.
    - The writing page's title in the note page's look: gold capitals, 17sp, centred under the top
      bar (kept as typed; shown in capitals). The top bar is just Back, Undo, Redo and ⋮.
    - On the note page the marks and tick boxes sit where the editor draws them, and a tick box
      ticks from anywhere in its paragraph's room at the left.
    - Aa swaps the keyboard for the text styles, as tall as the keyboard was (at most half the
      screen), so the page doesn't move: they show as the keyboard goes down, and a tap on the text
      or on Aa brings the keyboard back over them. In them, each a full-size button with its name:
      Title, Heading, Subheading, Text; Bullets, Numbers, Checklist, Quote; Left, Centre, Right;
      Underline, Clear formatting. While they're open the right-hand button closes them (and
      Back does); writing in the title or a tag closes them too.
    *Not yet tried on a phone:* the swap with the real keyboard (Robolectric has none): that the
    page holds still as one goes and the other comes, and that a tap on the text brings the
    keyboard back.

37. **Done 22 Oct.** **Notes: the rest of the suggestions (5–21 in NOTES_SUGGESTIONS.md).** "now
    the rest of items in backlog... do them". (Of the backlog itself only 7's step 2 and 22 are
    left, both waiting: Bible Brain's key, and a link to the films.) 16 and 17 dropped (see below).
    *Built 22 Oct:*
    - + opens a new note, dated today, ready to write in; verses are added from inside it. A note
      with only a title or only verses is kept.
    - Verses side by side show as one passage, "John 15:1–8", in Bible order, in the writing page,
      on the note page and on the cards (each verse still kept on its own, so the Reader marks
      each). Tapping one shows its words (each verse numbered); from the writing page, Insert in
      note puts them in as a quote line with the reference after them, and a line to go on in.
    - The notes list: search finds verses ("John 15", "Jn 15:5") and tags as well as words; each
      card has a quiet line of its passages and date, and an untitled note shows its first line as
      its title; Sort and filter (the Filter icon) orders by Newest, Recently edited or Bible order
      (a heading over each book's notes, "JOHN · 3 notes"), filters by tags (any, or all of them)
      and dates (this week, this month, from–to); pinned notes stay at the top; the search,
      filters and order are kept when Notes is left and come back to (the order between visits
      too). Each card's ⋮: Edit, Pin to top, Share, Copy, Delete.
    - The Reader: the verse's sheet shows each note's title (its first line when it has none).
      *Taken back out 22 Oct* ("no, revert 1 and 3", after seeing them): a note mark under the verse
      number that opened its note, and Notes in this chapter in the menu; the dot after the number
      is as it was.
    - The note page shows the note's date and tags, and has Share. Notes' ⋮ has Save all notes as a
      document: a text file of the notes shown, saved wherever it's put.
    - The Tags page: A to Z, each tag with its count, no "No description" lines. Old note code
      nothing used is gone. Mt, Mk, Lk and Jn are known as Gospel names (in search and in verse
      references written in notes).
    *Not yet tried on a phone:* the share sheet, saving the document, and the date range picker.

38. **Fixed 21 Oct, found 23 Oct; on the phone in My Bible (Dev), 23 Oct.** **Bug — Verse Scroll:
    verses repeated with the screen locked.** "i selected entire psalms in verse scroll and locked the
    screen and it repeats the verses" — "I am still on old version remember that too".
    *Found 23 Oct:* in the build on the phone (#108, 14 Oct), each time Verse Scroll dealt more
    cards (every few verses as it read on), the screen said the card it showed had come up again;
    locked, that was still the card it was locked on, so the reading went back to it and read on
    from there, over and over. Fixed on 21 Oct with the pause between cards (item 25, 4b21d07): while
    the reading goes on to a card, the one being left isn't counted as come up again. Here, all of
    Psalms read with the screen locked (a stand-in voice, 149 verses, then unlocked) had no verse
    twice, and the Psalms timings of all three recordings have no overlaps.
    *Also closed 23 Oct,* three more ways a verse could be read again: a voice saying a verse is done
    twice, while the reading waited to go on to the next card, had it go on twice (the next card read
    twice) — it now goes on once; a moment's interruption (a notification's sound, which pauses the
    reading) in a voice that doesn't say which word it's on read the whole verse again — it now picks
    up about where it had got to (a second and a half before); and one coming just as a stretch of a
    recording ended played the stretch again — no longer.

## Noted 29 Oct 2026

Seen on My Bible (Dev), the build of 23 Oct. Noted only; nothing built yet.

39. **Done 29 Oct.** **Reader: Follow takes two taps while reading aloud.** "the follow button has to be pressed
    twice in reading tab when read aloud is active(paused or playing).I think because of main pill
    pop up."
    *Found 29 Oct, paused:* with the pill hidden (the page scrolled down), the Reader takes the first
    tap anywhere to bring the pill back, a tap on the reading bar and what it offers included (Follow,
    Play, Undo), so only the second tap reaches Follow. Playing, taps are let through (a tap reads the
    verse tapped), so there the cause is still to find on the phone.
    *Built 29 Oct:* only a tap on the page brings the pill back now; the reading bar and what it offers
    take a tap at once, pill hidden or not. Tried here with the pill hidden: paused, one tap on Follow
    goes back to the verse being read, and one on the bar's play button plays (each took two before);
    playing, Follow took one tap before and still does. A tap on the page still only brings the pill
    back when paused, and still reads the verse tapped while it plays. If Follow still takes two taps
    while it plays, on the phone, what was on screen will tell where to look.

40. **Done 29 Oct.** **Reader: the pill out of the way while reading aloud plays.** "Is there a need of main pill if
    once read aloud is being played? doesn't it make sense to hide as soon as it goes to next verse to
    make space? or may after certain time like 3sec."
    *Today:* the pill hides only when the page is scrolled down, and comes back on a scroll up or a tap;
    hidden, the reading bar goes down to where it was (34).
    *Suggested:* while it plays, the pill drops away 3 seconds after the reading starts or plays on (with
    the bar's own 3 seconds, 34), and the bar takes its place; paused or stopped, it comes back. A scroll
    up still brings it back meanwhile, to change chapter, and it goes again 3 seconds after the page is
    let go. 3 seconds rather than the next verse, as a long verse takes half a minute.
    *Decided 29 Oct:* 3 seconds, as suggested.
    *Built 29 Oct,* just so; the page reaching the chapter's top or end as it's read doesn't bring the pill
    back while it plays. Not reading, the pill hides and comes back as before. Tried here: the pill was
    still there 2.4 seconds into a reading and away by 3.6; brought back by a scroll up, it was still there
    2.6 seconds after the page was let go and away by 3.8; back on pause, and staying; away again 3 seconds
    after playing on; still away while the chapter's last verses were read; back on stop.

41. **Done 29 Oct.** **Reader: the reading bar's controls without pausing.** "I don't like that it has to be paised to
    see controls. How about we just minimize it with button and maximize when pressed. or any better
    way you can come up with."
    *Today (34):* while it plays, the bar draws in to the round button by itself, and a tap on the button
    pauses, which brings the full bar back; so the full bar's controls (previous, next, the verse being
    read, settings, stop) are only there paused.
    *Suggested, as asked:* the bar no longer draws in by itself. The full bar gets a draw-in button (a
    small chevron at its left end), and a tap on the round button opens the full bar again instead of
    pausing (so the button shows a small "reading" mark in its gold ring, not a pause sign). The bar is
    kept the way it's left, for the next reading too. Pausing is then on the full bar, the earbuds or the
    notification.
    *The other way,* as a video player's controls: a tap on the round button shows the full bar for about
    4 seconds (longer while it's being used), then it draws in again; no extra button, but it goes away
    by itself.
    *Decided 29 Oct:* the video player's way. A tap on the round button shows the full bar for about 4
    seconds (longer while it's being used), then it draws in again; the button no longer pauses, so it
    shows no pause sign, and pausing is on the full bar.
    *Built 29 Oct,* just so. The round button shows a sound-wave sign in its gold ring; a tap on it brings
    the full bar, the reading going on, and it draws in again 4 seconds after it was last touched. Kept
    meanwhile: while its settings are open or the sleep timer's choices. Paused and played on from the bar,
    it stays 4 seconds too; from the earbuds or the notification it's the button again at once, as before.
    Tried here, each as described: opened and drawn in again (still there at 3.4 seconds, gone at 4.8);
    Next tapped 3 seconds in kept it 4 seconds more; the sleep timer's choices open 6 seconds, still there.

42. **Discarded 30 Oct.** **Reader, Blur Mode: a verse tapped while it plays isn't brought into focus.** "When in blur mode,
    while in play mode, when pressed another verse to play, it doesn't change focus automatically but it
    changes only when there's an automatic swift(like moving from english to telugu verse during play)"
    *Today:* a tap on a verse while it plays reads from it (34), and in Blur Mode the sharp verse is meant
    to be the one being read, brought to the middle at once. On the phone it stays on the old verse until
    the reading's next part starts by itself (the verse's English giving way to its Telugu).
    *To do:* find what holds it back (tried with Both and Blur Mode), so the verse tapped is sharp and
    in the middle as soon as it's tapped.
    *Tried 29 Oct, not reproduced:* Blur Mode, reading in both languages, by the phone's voice and from the
    recordings. A tap on another verse while its English or its Telugu was read (on its words, on its Telugu
    line, straight after scrolling the page, with the Telugu lines off), and the bar's Next: each time the
    reading went on from that verse, and it was sharp and in the middle within a third of a second; nothing
    waited for the reading's next part. Nothing in the code holds it back either, so nothing's changed yet.
    *To ask:* whether the voice went to the verse tapped at once (only the page lagged), or went on with
    the old verse until its part was done. Left for now ("leave this and move on", 29 Oct).

43. **Done 29 Oct.** **Verse Scroll: the progress bar, quieter, or a replay button instead.** "progress bar is
    distracting. make it greyish just like youtube shorts and no round thing when playing. It only
    appears when trying to adjust. stuttering of progress and gap at the end. let's analyse if we need
    it or not.or it can be replaced with a simple replay button."
    *Today (32):* a gold fill on a dark track, with a dot where the English gives way to the Telugu,
    there all the while a card is read; held, it thickens and a thumb follows the finger.
    *Most likely:* the stutter is the fill moving a word at a time, as the voice says which word it's on
    (or at a guessed pace when it doesn't); the gap is the voice's stretch, guessed from the text's
    length, ending before the fill gets to its end, just before the card moves on.
    *What it's for:* how far the card has got, going to the Telugu's start, and hearing a part again from
    a point. A replay button would do only the last, from the start.
    *Suggested:* keep it, quieter: a hairline grey track with a lighter grey fill, as YouTube Shorts'
    (no gold), and no dot while it plays; touched, it thickens and the dot and the thumb show. The fill
    glides between words at the voice's pace, and runs to the end of each part as it ends.
    *Decided 29 Oct:* the quieter bar, as suggested.
    *Built 29 Oct,* just so: a 2dp grey line (the ink's colour, faint, filled a shade darker), no gold, and
    no dot or thumb while it plays; held, it thickens to 6dp and shows the thumb and the dot. The fill
    glides to each new place (over 0.3 seconds, about a word's time) instead of jumping. The gap: the
    voice says where each word starts, so through the last word the fill stood at its start until the card
    moved on; now it goes on through the word being said at the voice's pace, as far as the next word (to
    the end, after the last). A verse read to its end, the next being found or the pause before the next
    card, shows its stretch full. Tried here: the fill rose a little every 48 ms through the words; just
    before the English was done it was at 0.425 of the bar, and its Telugu started at 0.426, no jump.

44. **Done 29 Oct.** **Notes: "Start writing…" goes as soon as anything is put on the page.** ""start writing" text in
    notes editor should disappear even when checkbox, tab or anything in toolbar is pressed not just
    first typing of letters."
    *Today:* it shows while the note has no letters at all; a checklist box, a bullet, a heading, a Tab
    or centring on the empty first line doesn't count, so the hint sits beside the box.
    *To do:* it goes once the first line has a look of its own or a style is set to type with (Bold and
    the like), and comes back only when that's all taken off again.
    *Built 29 Oct,* just so. Tried here on an empty note, each on and then off again: the checklist,
    bullets, Tab, Bold, a heading, a quote and centring; the hint went with each and came back after.

45. **Done 29 Oct, changed 30 Oct.** **Cross references: every quotation listed, both ways.** "check if cross references have quotes
    matching. If not add them explicitly."
    *Checked 29 Oct:* the 474 quotations reviewed on 21 Oct (29), against the cross references as the app
    reads them (a verse's Cross References list only the links from it). 411 are linked both ways; 31
    only from the New Testament verse, so the Old Testament verse's list lacks the verse quoting it
    (Isaiah 6:10 lacks John 12:40); 18 only from the Old Testament verse (Acts 2:27 lacks Psalm 16:10);
    and 14 not at all (Mark 15:28 and Isaiah 53:12, Mark 1:2 and Exodus 23:20, Hebrews 3:9 and Psalm
    95:9 among them; 13 of these were left out of the app's list on 21 Oct for that reason). The Reader's
    † is purple on both verses of a quotation, so on those verses it promises a quotation the list
    doesn't show.
    *To do:* the quotations become cross references of their own where they're missing, both ways (77
    links), listed first in purple with the "Quote" tag like the rest; the 13 left out go back in the
    app's list (tools/quotations).
    *Built 29 Oct,* just so. The app's list has all 474 quotations again (the 13 no cross reference
    links, reported by tools/quotations). Where a verse's cross references don't reach one of its
    quotations, that quotation is a link of its own, from each verse holding it: 126 links from 102
    verses (77 counted by quotation). Cross References and Verse Scroll's links sheet list them first,
    purple and tagged "Quote", with the rest; Verse Scroll's "links" counts them; and a verse quoting or
    quoted has its † though it has no other cross reference (Mark 15:28 now lists Isaiah 53:12). They
    carry no votes, so a rabbit hole doesn't wander into one by itself. Checked here with the real cross
    references: Isaiah 6:10 lists John 12:40, Acts 2:27 Psalm 16:10, Isaiah 53:12 Mark 15:28, and
    Matthew 2:15 still lists Hosea 11:1 once.
    *Agreed 29 Oct, built 30 Oct,* asked "quotes should be from verse which is referenced but not other
    way around?", then "both ways but different wording and diff colors": each side its own. On the verse
    that quotes (the New Testament's), the verse it quotes is tagged "Quotes", in purple, and its † is
    purple; on the verse quoted (the Old Testament's), the verse quoting it is tagged "Quoted in", in
    teal, and its † is teal. In Cross References and Verse Scroll's links sheet alike. Checked here:
    Matthew 2:15 lists Hosea 11:1 "Quotes" in purple, Hosea 11:1 lists Matthew 2:15 "Quoted in" in teal,
    and Psalm 110:1's eight New Testament quotations are "Quoted in" in Cross References and the links sheet.

46. **Done 30 Oct.** **Read aloud: English and Telugu each at its own volume.** "absolute volume/adjust the volume of
    telugu and english separately."
    *Today:* both play at the phone's media volume, so one can sound louder than the other (a recording
    against the phone's voice, or one narrator against the other).
    *Suggested:* a Volume slider for each language in Read aloud's settings, by its speed and pitch (76):
    below 100% it's turned down, above it boosted up to about 150% (Android's loudness enhancer), for
    the phone's voice and the recordings alike.
    *Decided 29 Oct:* the app evens the two out by itself, with nothing to set (rather than sliders).
    To work out while building: each recording's loudness measured once (as its folder is added) and
    each phone voice's once (a sample spoken to a file), then each played at the level that brings it
    to the same loudness, the quieter ones raised with Android's loudness enhancer.
    *Built 30 Oct,* just so, with nothing to set. Loudness is measured as broadcasters measure it (how
    loud it sounds to the ear, the silences between verses left out). A recording is measured once,
    in the background, as its folder is looked over (one already chosen, the first time the app starts):
    20 seconds of verses from each of a dozen chapters spread through it, decoded on the phone, nothing
    played, and kept with the folder. A phone voice is measured once by having it say a few words into a
    file, as the language is read (its speech engine, and the voice chosen or the usual one): when a
    reading starts, when one ends, or when the reading settings open, and kept; a new voice the same.
    Each verse then plays at the level both languages meet at: the quieter raised to the louder, by up
    to 6 dB; further apart, the louder lowered for the rest. Within a decibel, both as they are; before
    both are known, as they are. In Both each verse's English and Telugu, and in either alone, so
    switching language doesn't jump. A recording is raised in a sound session of its own player, the
    phone's voice in one the speech engine is asked to play it in. The sleep timer's fade works on top.
    A voice heard on its own (trying one in the settings, a Greek or Hebrew word's speaker) is as it is.
    Checked here: the measure against the standard's own test tones, the dozen places chosen, voices
    measured once and kept, the levels in Both with two recordings and with a recording and a voice,
    and the decoding loop run through Robolectric's stand-ins for the phone's decoder.
    *To listen for on the phone:* Both, with a recording and the phone's voice, then two recordings:
    the two at one loudness from a reading's second verse (its first may come before its voice is
    measured, the first time). Not yet heard: real MP3s and videos decoded on the phone, and the phone's
    voice raised (that needs its speech engine to play in the session it's given; if it doesn't, the
    voice stays quieter, and the fallback is to lower the other language to it instead).

47. **Done 29 Oct.** **Notes: holding Backspace stops at a checklist.** "back button long press in the keyboard is
    stopping in the middle after clearing checkmarks."
    *Found 29 Oct:* Backspace at the start of a checklist item (or a list item or quote) first only takes
    its mark off (35), putting back the line break the keyboard took away; the text then isn't what the
    keyboard made it, so the editor restarts the keyboard's input, which ends the held key's repeat.
    *To do:* holding Backspace goes on through checklist items as through any text; a single press still
    takes the mark off first. That needs the mark taken off without putting text back behind the
    keyboard's back (say, the mark's place kept as an invisible character the keyboard deletes like any
    other).
    *Built 29 Oct,* more simply: a deletion coming within a quarter of a second of the one before is the
    key held down (a keyboard repeats every 50 milliseconds or so), and then a line's start is gone
    through as the keyboard did it, the lines joined and the mark going with them, so nothing's put back
    and the keyboard isn't restarted. A single press still takes the mark off first. Held from the very
    start of a checklist item, the first press is a single one (it takes the mark off, and the keyboard
    restarts); held again, it goes on. Tested in the editor's rules; the keyboard's repeat itself only on
    the phone.

48. **Done 30 Oct.** **Notes list: special notes in purple.** "special notes in purple in notes list."
    *Decided 29 Oct,* as suggested: a mark of its own, Special, put on or taken off from the note's menu
    as Pin is; a special note's card has a thin purple edge down its left and its title in purple, and
    the list's filter has Special.
    *Built 30 Oct,* just so: "Mark as special" (a star) under Pin in a card's menu, "Not special" to take
    it off; a special card's edge and title in the app's purple; the filter sheet's Show: All notes or
    Special, and "Special" in the line under the search while it's on (Clear takes it off with the
    rest). Marking doesn't count as an edit (the note keeps its edited time), and editing a note keeps
    its mark. Checked here on the list, the menu, the sheet and Special alone.

49. **Done 30 Oct.** **Notes: verses named in a note count as its verses, and a whole chapter can be
    added.** "bible verses mentioned in the note should automatically count into add verse. Add provision
    to add entire chapter too."
    *Today:* a note's verses are the ones added with + Verse; a verse named in its text (John 3:16, linked
    as it's written) isn't one of them, so the note isn't at that verse in the Reader, under its book in
    the list, or found by that reference.
    *To do:* verses named in the text are the note's verses too, in its details strip as soon as they're
    written, and gone again when taken out of the text (unless also added with + Verse). + Verse can pick
    a whole chapter ("John 3"), kept as one passage.
    *Decided 29 Oct:* a note on a whole chapter has its dot at the chapter's first verse in the Reader.
    *Built 30 Oct,* just so: a verse written in the note's title or text ("John 3:16", "Rom 8:28-30" for
    all three) shows in the details strip as it's typed, without an ×, and goes when it's taken out of
    the text; one also added with + Verse keeps its × and stays. They count everywhere a note's verses
    do: the Reader's dot and the verse's notes, Verse Scroll, the list's line and Bible order, search by
    reference, and Share (each with its words). Verses that aren't there ("John 3:99") are left out.
    + Verse's banner has Whole chapter, for the chapter on the page: "John 3", a passage of its own (its
    dot at verse 1; a tap shows the whole chapter, which scrolls; Open in Reader goes to its top); the
    chapter holds its verses, so "John 3:16" written in the same note adds nothing more. Notes written
    before have their named verses added once the Bible is in (and notes brought in by a restore too),
    without counting as an edit. Checked here with the real Bible text: saving, the strip as it's typed,
    Whole chapter on and off, the chapter's preview, and an older note given its verses.

50. **Done 30 Oct.** **Reader: the page turns with the reading into the next chapter.** "turn chapter
    along with read loud as the read aloud move to next chapter."
    *Today (34):* followed along, the page turns with the reading (the reading set to go on past the
    chapter, 26); looked around, it turns when it goes back to the reading; otherwise the bar offers
    Back. On the phone, some case misses it.
    *Answered 29 Oct:* it happened with the screen off, or after another page (Notes, Search…).
    *Most likely:* the Reader keeps the chapter the reading was in only while its page is showing; back
    on it from another page, or after Android rebuilt the page while the screen was off a while, it has
    forgotten, so it doesn't see that the reading moved on and stays on the old chapter.
    *To do:* the app turns the Reader's chapter itself, seen or not: when the reading goes on by itself
    from the chapter the Reader is on, and the Reader was following it, the Reader goes with it, so it's
    on the chapter being read when it's next seen. Looked around, it stays put as now.
    *Built 30 Oct,* just so. Confirmed first, here in the real app with a silent voice: with Notes open as
    the reading went from John 3 into John 4, the Reader was still on John 3 when it came back, offering
    Back; the same when Android rebuilt the page with the screen off. The page only noticed the reading
    moving on while it was showing, and forgot what it had seen when rebuilt. Now the app watches the
    reading itself, and the page only tells it whether it's being followed (the verse being read in view,
    not looked around), so the Reader turns with the screen off, with another page open, and after a
    rebuild; shown again, it's at the verse being read. A page still being built doesn't count as looked
    away. Looked around, it stays put, the bar offering Back and Read here, and let go it goes on to the
    reading as before. The app opened again while the Reader's reading goes on opens on the verse being
    read. Checked here: Notes open, rebuilt with the screen off, screen off, looked around then Notes,
    and looked around on the page.

51. **Saved for later 30 Oct.** **A Telugu-only lite app.** "telugu only lite version - reader,cross reference, highlight,verse
    scroll,read aloud... entire app should be in telugu."
    *Meant, most likely:* a smaller app for Telugu readers: the Telugu Bible only, with the Reader, Cross
    References, highlights, Verse Scroll and read aloud, and every word of the app (menus, buttons,
    settings, messages, book names) in Telugu.
    *Today:* the app's words are written into its code in English, several hundred of them (only 3 are in
    strings.xml).
    *To build it:* first the app's words moved into strings, with a Telugu set (a "Telugu only" switch in
    this app would need that too); then a Telugu version built from the same code, with its own name and
    icon and installed beside My Bible, leaving out the English text and the parts not listed (Greek and
    Hebrew, Notes, Search, Studied, …) and their data, so it's much smaller. A big job.
    *Decided 29 Oct:* a separate app, named నా బైబిల్ ("My Bible"), installed beside My Bible. Its parts:
    the Reader, Cross References, highlights, Verse Scroll, read aloud and the home screen widgets; no
    Search, Notes or Studied. No English at all: the Telugu Bible only, and every word of the app in
    Telugu.
    *Designed 30 Oct* (not built yet), for people who aren't at home with phones: a clickable mock-up
    of 13 screens and the tab bar, https://claude.ai/artifact/2BhtmUiGYZEigFng9Y9oZt (private to the
    owner).
    - Four tabs always at the bottom, each a word under its picture: హోమ్, బైబిల్ (the Reader), ధ్యానం
      (Verse Scroll) and గుర్తులు (highlights). Settings (అమరికలు) open from Home.
    - Big letters from the start: the first screen asks how big (మామూలు, పెద్దవి, చాలా పెద్దవి), and
      the Reader's అ button and Settings change it (five sizes, 18 to 30 px; పెద్దవి, 23 px, is the
      usual). The Bible in Tiro Telugu, the app's words in Baloo Tammudu 2.
    - Every button says what it does, in plain Telugu, and nothing is done only by a swipe or a long
      press. A verse is touched for its choices: ఇక్కడ నుండి వినండి, గుర్తు పెట్టండి (four colours, with
      రద్దు చేయండి to undo), సంబంధిత వచనాలు (the Cross References) and పంపండి. A tip says so the first
      time. Verse Scroll has a తరువాతి వచనం button. Every page but the tabs has a వెనుకకు button.
    - Home: where you stopped (చదవడం కొనసాగించండి), today's verse (వినండి, పంపండి), and the books.
    - Books: all 66 on one page, two columns, with jumps to పాత నిబంధన and క్రొత్త నిబంధన; then the
      chapters as big numbers, the one you stopped at filled.
    - Read aloud: one big ఆపండి / వినండి button between the previous and next verse, the verse being
      read tinted, and speed and a stop timer as buttons that show their setting.
    - Widgets: today's verse (చదవండి, వినండి) and continue reading (4×1).
    - Maroon (#8B2232) on warm paper, so it's told apart from My Bible on the phone.

## Noted 30 Oct 2026

52. **Done 30 Oct.** **Read aloud: a verse's first word not clearly heard.** "The sentences mostly cut off the first word
    (i meant it's not clearly heard)... is it because of bad cutting or there's a fade in while starting
    the sentences? can you check?"
    *Checked 30 Oct (nothing changed yet):*
    - No fade-in: the app fades nothing in as a verse starts (its only fade is the sleep timer's, out).
    - The cutting is right. Measured on the recordings kept here (ten chapters of the narrated KJV, and
      Genesis, Ruth, Mark, Hebrews and Jude in Telugu, about 2,800 verses): a verse's timed start is
      usually about 0.05 s before its first sound, and it's played from 0.04 s before that. The verses
      that looked cut had a breath (or, in the KJV, the background) before them, and the word itself
      starting right at the cut.
    - But the margin is thin: half the verses start playing less than 0.1 s before their first sound,
      one in six less than 0.06 s. A soft first sound ("th", "h", "f", "s") can lose its start there,
      and so can anything that takes a moment to come on as sound starts.
    - Earbuds: between verses (and between a verse's English and Telugu) the app plays exact silence, to
      keep Bluetooth earbuds connected and their buttons coming to it. Many earbuds switch their sound off
      during exact silence and fade it back in when sound comes again, over about a tenth of a second or
      two: just the time it takes to reach the first word. "Mostly" fits that. A quick check: the same
      verses on the phone's own speaker; clear there, it's the earbuds.
    *Proposed:* each verse played from 0.25 s before its timed start, not 0.04 s (never back into the
    verse before), so a soft first sound is kept and earbuds come on over the pause before the word; and
    the silence between verses made an inaudible hiss, far below anything heard, so earbuds that switch
    off on exact silence stay on (for the phone's voice too, whose words start almost at once).
    *Built 30 Oct,* just so. A recording's verse is played from a quarter of a second before its timed
    start, in the pause before it, stopping at the end of the verse before; a chapter's first verse
    without its spoken heading (Verse Scroll's) from just before it, as before, the heading's last word
    being close. Checked on the same recordings first: the verses started less than 0.06 s before their
    first sound went from about one in four to one in twenty, and what the earlier start takes in is a
    breath or the background (and once, Genesis 9:29 in Telugu, the first word of a verse timed a
    quarter of a second late, which it had been cutting), not the verse before. The app's silence while
    it reads is now a hiss about 72 dB under full sound: with the phone as loud as it goes, about as
    loud as a very quiet room, and nothing under a voice.
    *To listen for on the phone,* with the earbuds: in Both, and with the phone's voice, each verse's
    first word clear. Each verse now begins a little more after the one before (up to a fifth of a
    second more).


53. **Done 30 Oct.** **Verse Scroll: a Share button that shares the verse as a picture.** "is
    it possible to replace quick links with a share button in scroll verse that can share the verse along
    with the image in 9:16 and more resolutions as possible? how big a build is this?" Then: "can you
    include telugu reference as well along english when telugu is enabled? build 53"
    *Meant:* the bottom bar's "N links" button replaced by Share. Share turns the card into a picture, its
    photo with the verse on it, in a choice of shapes, and hands it to WhatsApp, Instagram and the rest.
    *Before:* nothing in the app shared a picture (notes share as text). The scene photos are all 900×1950
    pixels.
    *Losing the Links button:* its sheet (the strongest links; tap one to read it next) goes. Following a
    verse's links stays, in the Rabbit hole tab at the top, and so does Cross References, through Read.
    *Proposed:* Share opens a sheet with a preview, a choice of shape, and Share and Save to phone
    (Pictures/My Bible). Each shape is 1080 pixels on its short side, which is what those apps keep; bigger
    would only sharpen the words, as the photos are 900 wide.
    - Status and Stories, 9:16 (1080×1920)
    - The phone's own screen, for a lock-screen wallpaper (for example 1080×2400)
    - Post, 4:5 (1080×1350)
    - Square, 1:1 (1080×1080)
    - Wide, 16:9 (1920×1080): the photo as a tall panel at one side, the verse beside it
    The picture has the card's photo (or your own picture for that scene), and the verse in the card's
    language (English, Telugu or Both) and type, with its reference, over the card's soft dark fade. The
    words are sized to fit, smaller for a long verse.
    *Size:* medium, about 700 to 900 lines in 5 or 6 files (the picture maker, the sheet, handing the file
    to other apps, the button, tests): two to three times item 49, smaller than 46.
    *Built 30 Oct,* with the defaults proposed (no "My Bible" mark, the picture alone, no larger size):
    - The bottom bar's "N links" button is now Share. Its sheet shows the picture as it will be, the five
      shapes in a row (9:16 Status, Phone Wallpaper, 4:5 Post, 1:1 Square, 16:9 Wide), Share (the phone's
      share list: WhatsApp, Instagram and the rest) and Save to phone (in Pictures › My Bible, from
      Android 10). The shape chosen is kept for next time. The Links sheet is gone, and its code.
    - The picture: the card's photo, or your own picture for that scene (read big enough for it), dimmed
      as the card dims it, and the words in the card's type and colors (light or dark, as the app is).
      They're as large as fits: up to 1.6 times the card's size for a short verse, down to half for a
      long one, and a verse too long even then is cut short with "…". A long verse over a photo gets the
      card's soft panel behind it. A wide picture has the photo as a 9:16 panel at its left, and the
      words beside it over the same photo blurred. With photos off, the plain page.
    - With the Telugu on: the Telugu under the verse, and the reference in Telugu beside the English one
      ("JOHN 3:16  యోహాను 3:16"), on the picture and on the card itself. All 66 books' Telugu names are
      the Bible Society's.
    - Checked here on pictures drawn from the real photos, type and verses (every shape; English and
      Telugu; a long verse and a passage; light and dark; no photo), and on the real sheet. Each picture
      takes well under half a second to draw.
    *To check on the phone:* sharing to WhatsApp (in a chat, and as Status) and to Instagram; Save to
    phone, then the picture in the gallery; a lock-screen wallpaper from the Phone shape.

54. **Saved for later 30 Oct.** **Search: "Paul intercession" finds nothing.** "i just tried seraching
    Paul intercession and it recommended it as well and no search results? us there a way to fix it? how
    big a build is this?"
    *Checked 30 Oct, on the code the Dev build has:* no verse has both words (Paul's own prayers for the
    churches never name him), and a two-word search falls back to nothing, so the page only says "No
    verses found". Nave's has what's wanted, under Intercession: "Paul for Onesimus", "Paul, for God's
    people", "By Paul, of the congregations" and "For Paul, by the congregations". But a topic is found
    only by its name and its main headings, and these are a level below. ("Jesus intercession" works:
    "Intercession of" is a main heading of Jesus, the Christ.) And "Paul intercessory prayer" offers "Did
    you mean paul intercessor prayer", which finds nothing either.
    *Proposed:*
    - A topic's entries at every level count, not only its main headings. "Paul intercession" then gives
      Intercession › By Paul, of the congregations; Paul for Onesimus; Paul, for God's people; and For
      Paul, by the congregations, each opening the topic there. So do "Moses intercession", "Abraham
      intercession" and the like.
    - A two-word search with no verse having both: "No verse has both words", and a button for each word
      on its own, with how many verses it finds.
    - "Did you mean" only when what it offers finds something; and "intercessory" (not a King James word)
      read as intercession.
    *Size:* small to medium, about 200 lines in 4 files with tests: about item 49's size.
    *Not covered:* Paul's prayers in his letters (Romans 1:9, Ephesians 1:16 and 3:14, Philippians 1:4,
    Colossians 1:9, 1 Thessalonians 1:2, 2 Thessalonians 1:11, Philemon 4) aren't in Nave's list, so they
    still wouldn't come up. A list of them could be added by hand.

55. **Saved for later 30 Oct.** **Search: subjects no word or topic names.** "is there a way to cover
    those kind of topics?" (after 54: Paul's prayers in his letters, which neither say "Paul" nor are in
    Nave's list).
    *Three ways, smallest first:*
    - **A person and a subject searched in that person's own words.** When no verse has both, the name
      stands for what the person wrote or said: Paul, his 13 letters (Romans to Philemon); John, his
      Gospel, letters and Revelation; Peter, James and Jude, their letters; Solomon, Proverbs,
      Ecclesiastes and the Song; David, the psalms titled his; Jesus, his own words (the red letters the
      app already marks). The subject is read widely: intercession as prayer for others (pray, prayer,
      supplication, intreat, "making mention of you"). In Paul's letters that finds 46 verses, his prayers
      for the churches among them (Romans 1:9 and 10:1, Ephesians 1:16, Philippians 1:4 and 1:9,
      Colossians 1:3 and 1:9, 1 Thessalonians 1:2 and 3:10, 2 Thessalonians 1:11, 2 Timothy 1:3,
      Philemon 4), those praying "for you" first; it misses one that names no prayer (Ephesians 3:14,
      "I bow my knees"). Medium: about 300 to 400 lines; it fits with 54.
    - **A second topic book: Torrey's Topical Textbook** (1897, public domain): about 600 topics broken
      into thousands of headings, many of them lists of examples (intercessory prayer, by whom), shown
      with Nave's in Search. Medium: a tool to read it in, about 300 lines in the app, and a data file of
      a megabyte or two. It needs a clean copy to read in, and what it covers checked first.
    - **Search by meaning, on the phone:** a small language model finds verses that mean what's typed,
      whatever the words ("praying for others", "worried about money"). English only (one for Telugu too
      is far bigger); the app grows by 30 to 40 MB; a search takes a moment longer on older phones.
      Large: the biggest of the three by far.

56. **Proposed 31 Oct (not built).** **The Telugu text: stray spaces in words, missing dashes and
    semicolons.** "is it possible for you to proof read and compare againt bible.com online version? I
    allowed bible.com in your environment. It is having unexpected space in the telugu words and missing
    long Em-dash and semi-colons. it's bsi ov version."
    *Checked 31 Oct, on the text in the app (unchanged since the first build, from aruljohn/Bible-telugu,
    a community copy of the BSI Old Version):*
    - No em dashes at all. 6,245 semicolons, so some are there; which are missing needs a copy to
      compare with.
    - Words split by a space: at least 1,000 (the joined spelling is used elsewhere in the same Bible,
      and the second half isn't a word on its own: జెబూ లూను, పరా క్రమశాలులు, సెల వియ్యగా). There are
      more where both halves are words too (చని పోయినను), which only a comparison finds.
    - 6 spaces before a vowel sign (always wrong: ఐగుపు ్త), 5,500 double spaces, and 127 stray "¸"
      marks, nearly all before యౌవన… (¸యౌవనుడు).
    - bible.com itself couldn't be reached: bible.com is allowed, but it sends every page on to
      www.bible.com, which isn't.
    *Proposed:*
    - Fix what's surely wrong from the text itself, with a list of every change to look over: the vowel
      sign splits, double spaces, the "¸" marks, and the split words whose joined spelling the Bible
      uses elsewhere.
    - For the dashes, semicolons and the other splits, a copy that may be copied: wordproject.org's,
      which the repo already has a tool to read in (tools/scrape-telugu.js, never run; its terms allow
      copying the text for non-commercial use). Compare it with the app's, verse by verse.
    - Check samples of both against bible.com a chapter at a time. Not a download of all of bible.com:
      YouVersion's terms don't allow copying it by program (to confirm when the site opens), and its
      copy is BSI's, by permission.
    *Needed:* www.bible.com, wordproject.org and www.wordproject.org under Allowed domains.
