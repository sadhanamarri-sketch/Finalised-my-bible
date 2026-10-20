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
    and they were timed again chapter by chapter from their spoken headings. All 1,189 chapters;
    1,038 verses (3.3%) are marked unsure, among them 6 chapters spread by their text's length. The
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

23. **Reading aloud in the Reader: any verse, any time.** "right now I dont have the provision to
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

## Noted 20 Oct 2026

24. **Reading aloud stops when the app is swiped away.** "Player continued in background even
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

25. **Verse Scroll: a pause between cards, and a gentler slide.** "Provide a setting to set
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

26. **Reading on past the chapter, and a sleep timer.** "Provide setting to continue infinitely in
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

27. **Highlighting in Verse Scroll: just the colour box.** "The default color is not necessary as I
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

28. **Verse Scroll: the English dictionary.** "Dictionary in verse scroll." — "Only English in
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

29. **Old Testament quotations in the New, in purple.** "Quotes/references in new testament from
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

30. **Verse Scroll: a tap to play or pause, and the reading kept through a visit to the Reader.**
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

31. **A one-tap language button: English, Telugu, Both.** "Easy switch for eng, tel and both for
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
