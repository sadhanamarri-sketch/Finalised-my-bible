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

3. **Verse Scroll for one chapter or book**, "to stay on context instead of wandering."
   Pick a book or chapter (John, Romans 8) and every card comes from there.
   *To ask:* cards in reading order or shuffled? And may Discover's linked verses still lead
   outside it, or should everything stay inside?

4. **Verse Scroll of highlighted or studied verses**, "to revisit instead of discovering by
   wandering."
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
