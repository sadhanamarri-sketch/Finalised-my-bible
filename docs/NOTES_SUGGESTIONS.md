# Notes: suggested changes (saved for later)

Written 8 Oct 2026, after going through the Notes feature: the list, editor, writing page,
note page, tags, and notes in the Reader. Nothing here is built yet. Pick by number.

Items 1–4 matter most: today a note being written can be lost without any warning.

## Losing what you write

1. **Back shouldn't throw away a note.** Back in the full-screen writing page discards
   everything typed there, and back or × in the editor closes it without saving or asking.
   Make back keep the text, and ask "Discard changes?" only when something changed.
   *Where:* `NoteTextFullScreenEditor` (`BackHandler(onBack = onCancel)`) in
   `ui/screens/NoteEditorScreen.kt`; the editor's back is `BackHandler(enabled = showNoteEditor)`
   in `MainActivity.kt`, and its × is `onCancel`.

2. **Keep a half-written note when the phone is turned.** Checked in a test: a title typed
   into a new note was gone after the activity was recreated (rotation, dark mode switching,
   or Android closing the app in the background). The editor's fields are plain `remember`
   state, and `MainViewModel.noteToEdit` only holds the note as it was when opened.

3. **Move notes into the database, like studied verses.** All notes are one JSON list in
   preferences (`saved_notes_json`), rewritten whole on every save. The daily Drive sync
   (`DriveSyncWorker`) makes its own `BibleRepository` with its own copy of that list, so if
   it runs while the app is open it can undo a save, or bring back a note just deleted (its
   copy of the deletion records is older too). Same fix as studied verses: a `notes` table in
   `UserDatabase`, one row per note, watched live by every copy.

4. **Undo after deleting a note**, instead of "This can't be undone" — like the Studied
   page's Undo.

## Writing

5. **Fewer steps.** A new note today: + → Reader → pick verses → Done → editor → tap Note →
   writing page → Done → Save. Instead, + would open the note with the cursor ready, and one
   Save. Verses can still be added from inside the note ("Add verse" is already there).

6. **Allow saving a note with only a title or only verses.** Save stays greyed out until the
   note has body text (`enabled = text.isNotBlank()`), so a "Verses to memorise" list can't
   be saved.

7. **Show verse ranges as one chip:** "John 15:1–8" instead of eight chips, in the editor and
   on the note page. (Each verse can stay stored on its own, so the Reader still marks each.)

8. **In the writing page:** an "Insert verse" button that quotes the verse text, plus
   Undo/Redo.

## Finding notes

9. **Search finds verses and tags too.** Typing "John 15" or "Prayer" would find notes on
   that passage or with that tag. Today search only looks at the title and text.

10. **A quiet line on each card, like "John 15:1–8 · 7 Oct".** Also show an untitled note's
    first words instead of the heading "UNTITLED NOTE". (The cards were trimmed down to title
    and text on purpose earlier, so this one is optional.)

11. **Sort options:** newest, recently edited, or Bible order (Genesis to Revelation). Today
    it's by date created only, so editing a note doesn't bring it to the top.

12. **Browse by book**, like the Studied page: "John · 3 notes".

13. **Better filters:** a date range (this week, this month, from–to) instead of typing one
    exact day, and an option to require all chosen tags (today any one of them matches).

14. **Keep the search and filters** when you leave Notes and come back. Today they reset, for
    example after opening a verse from a note and returning (they're `remember` state in
    `NotesScreen`, which is disposed with the tab).

15. **Pin** important notes to the top.

## Notes while reading

16. **A clearer note mark you can tap.** The dot after a verse number looks like punctuation
    and does nothing when tapped. Use a small note icon that opens the note.

17. **"Notes in this chapter"** from the Reader menu.

18. **Show the missing details:** the note's title in the verse sheet (it shows the first line
    of the text now, see `notePreviewLine`), and the date and tags on the note page. Tags
    never show there, and the date only shows on notes without verses.

## Sharing and tags

19. **Share or copy a note** as text with its verses, and export all notes as a readable
    document, for example for a study group. The Drive backup can't be read as a document.

20. **Tags page:** a count per tag ("Prayer · 4 notes"), A–Z order, and no "No description"
    lines.

## Tidy-up

21. **Remove unused note code**, such as an old note pop-up that nothing opens anymore
    (`ui/components/NoteReaderDialog.kt`), `MainViewModel.parseNoteReference`,
    `MainViewModel.saveNote(text, tags)` and the older `BibleRepository.saveNote` overload.
    Nothing visible changes.

## Formatting tools in the writing page

Added 22 Oct 2026, after going through the toolbar over the keyboard (`FormattingToolbar` in
`ui/richtext/RichTextEditor.kt`). Today it has, in a row that scrolls sideways: B, I, U, the
text size (− 19 +), H (heading), ⇥ (indent), Ctr (centre), “ (quote), highlight, • (bullets)
and 1. (numbers). The note page shows each of them as the editor does.

22. **Bold, italic, underline, highlight and size work only on selected text.** With just the
    cursor they're greyed out, so you can't tap B and then type in bold: you type, select, then
    tap. And with the cursor in bold text, B doesn't light up. Make them, with nothing
    selected, apply to what's typed next, and light up for the style at the cursor.

23. **Selecting stops at the end of a paragraph.** Each paragraph is its own text box, so two
    paragraphs can't be made bold or highlighted together, or copied or cut in one go. One text
    box for the whole note would fix it (a bigger change to the editor; saved notes keep their
    format).

24. **Half the buttons are off the screen.** The row is about 555dp wide on a 392dp phone, so
    quote, highlight, bullets and numbers sit past the edge with nothing to say there's more.
    Put the most used first (B, I, highlight, bullets, numbers, heading), use icons instead of
    "Ctr", "⇥" and "“", and fit one row, with the rest behind a "more" button.

25. **Headings by guesswork.** H goes round big → medium → small → normal text, a tap at a time,
    without showing the choices; the "small" one is smaller than the text, in grey. A tap on H
    could open a short list instead: Title, Heading, Subheading, Normal text.

26. **Indent only goes in.** The only way back out is Backspace at the start of the line. Add an
    outdent button. (Ctr only centres; the notes can also hold right-aligned lines, but nothing
    makes them.)

27. **Text size overlaps headings.** − and + change the size of selected words in six steps,
    beside headings that already make text bigger, and the number shown is wrong in a heading
    (19 where the heading is 24). Drop it, or offer Small, Normal and Large in one place.

28. **No "Clear formatting"** to take every style off selected text at once.

29. **The cursor starts at the end of the first paragraph** when a note opens, not at the end of
    the note, where you'd go on writing.

30. **A checklist** (a list with tick boxes) for things like verses to memorise or prayers.

(Undo and Redo in the writing page are in 8.)
