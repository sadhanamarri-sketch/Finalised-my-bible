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
