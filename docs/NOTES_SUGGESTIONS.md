# Notes: suggested changes (saved for later)

Written 8 Oct 2026, after going through the Notes feature: the list, editor, writing page,
note page, tags, and notes in the Reader. Pick by number.

Done 22 Oct (each marked, and BACKLOG.md item 35): 1–4, Undo/Redo from 8, and 22–44. Still to
do: 5–7, Insert verse (8) and 9–21.

## Losing what you write

1. **Done 22 Oct.** **Back shouldn't throw away a note.** Back in the full-screen writing page discards
   everything typed there, and back or × in the editor closes it without saving or asking.
   Make back keep the text, and ask "Discard changes?" only when something changed.
   *Where:* `NoteTextFullScreenEditor` (`BackHandler(onBack = onCancel)`) in
   `ui/screens/NoteEditorScreen.kt`; the editor's back is `BackHandler(enabled = showNoteEditor)`
   in `MainActivity.kt`, and its × is `onCancel`.

2. **Done 22 Oct.** **Keep a half-written note when the phone is turned.** Checked in a test: a title typed
   into a new note was gone after the activity was recreated (rotation, dark mode switching,
   or Android closing the app in the background). The editor's fields are plain `remember`
   state, and `MainViewModel.noteToEdit` only holds the note as it was when opened.

3. **Done 22 Oct.** **Move notes into the database, like studied verses.** All notes are one JSON list in
   preferences (`saved_notes_json`), rewritten whole on every save. The daily Drive sync
   (`DriveSyncWorker`) makes its own `BibleRepository` with its own copy of that list, so if
   it runs while the app is open it can undo a save, or bring back a note just deleted (its
   copy of the deletion records is older too). Same fix as studied verses: a `notes` table in
   `UserDatabase`, one row per note, watched live by every copy.

4. **Done 22 Oct.** **Undo after deleting a note**, instead of "This can't be undone" — like the Studied
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

8. **Undo/Redo done 22 Oct; Insert verse still to do.** **In the writing page:** an "Insert verse" button that quotes the verse text, plus
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
and 1. (numbers). The note page shows each of them as the editor does. (That editor is gone
now: see `ui/richtext/NoteBodyEditor.kt` and `NoteDisplay.kt`.)

22. **Done 22 Oct.** **Bold, italic, underline, highlight and size work only on selected text.** With just the
    cursor they're greyed out, so you can't tap B and then type in bold: you type, select, then
    tap. And with the cursor in bold text, B doesn't light up. Make them, with nothing
    selected, apply to what's typed next, and light up for the style at the cursor.

23. **Done 22 Oct.** **Selecting stops at the end of a paragraph.** Each paragraph is its own text box, so two
    paragraphs can't be made bold or highlighted together, or copied or cut in one go. One text
    box for the whole note would fix it (a bigger change to the editor; saved notes keep their
    format).

24. **Done 22 Oct.** **Half the buttons are off the screen.** The row is about 555dp wide on a 392dp phone, so
    quote, highlight, bullets and numbers sit past the edge with nothing to say there's more.
    Put the most used first (B, I, highlight, bullets, numbers, heading), use icons instead of
    "Ctr", "⇥" and "“", and fit one row, with the rest behind a "more" button.

25. **Done 22 Oct.** **Headings by guesswork.** H goes round big → medium → small → normal text, a tap at a time,
    without showing the choices; the "small" one is smaller than the text, in grey. A tap on H
    could open a short list instead: Title, Heading, Subheading, Normal text.

26. **Settled 22 Oct** by the Tab and Shift-Tab buttons (31). **Indent only goes in.** The only way back out is Backspace at the start of the line. Tab
    and Shift-Tab (31) would fix it. (Ctr only centres; the notes can also hold right-aligned
    lines, but nothing makes them.)

27. **Settled 22 Oct:** size is out of the toolbar; sizes in older notes still show, and Clear formatting takes them off. **Text size overlaps headings.** − and + change the size of selected words in six steps,
    beside headings that already make text bigger, and the number shown is wrong in a heading
    (19 where the heading is 24). Drop it, or offer Small, Normal and Large in one place.

28. **Done 22 Oct.** **No "Clear formatting"** to take every style off selected text at once.

29. **Done 22 Oct.** **The cursor starts at the end of the first paragraph** when a note opens, not at the end of
    the note, where you'd go on writing.

30. **Done 22 Oct.** **A checklist** (a list with tick boxes) for things like verses to memorise or prayers.

(Undo and Redo in the writing page are in 8.)

## The writing page to the best standard

Added 22 Oct 2026, after a closer look at the editor, the writing page and the note page
(`NoteEditorScreen.kt`, `ui/richtext/`), measured against Google Keep, Samsung Notes, Apple
Notes and Google Docs.

31. **Done 22 Oct.** **Tab and Shift-Tab buttons.** In a list, Tab makes the item a sub-item (◦ under •; a, b, c
    under 1, 2, 3) and Shift-Tab brings it back; in plain text they indent and outdent the
    paragraph. They replace ⇥ and the hidden Backspace trick. Lists are one level only today. A
    physical keyboard's Tab and Shift+Tab would do the same.

32. **Done 22 Oct.** **A hide-keyboard button** at the right end of the toolbar, always in view. The toolbar goes
    with the keyboard; a tap on the text brings both back.

33. **Done 22 Oct.** **The keyboard is restarted on every letter (a bug).** The writing page hands the text back
    to Android without the word the keyboard is still building (its "composition"), and Compose
    then restarts the keyboard's input each time (seen in Compose 1.7.5's own code:
    `EditProcessor.reset`, then `restartInputImmediately`). That can stop suggestions,
    autocorrect and swipe typing from working properly, and make typing feel slow. The fix is
    one line: pass the composition back with the styled text (`displayValue` in `BlockRow`).

34. **Done 22 Oct.** **Sentences don't start with a capital.** The writing page, the title and the tag field don't
    ask the keyboard for capitals at the start of sentences (no `KeyboardCapitalization`), so the
    keyboard stays in small letters.

35. **Done 22 Oct.** **Save as you type.** Keep every change as it's made, and after the app is closed or the phone
    turned, so Back just closes the note; throwing changes away is a choice ("Discard"), never
    the default. (Goes further than 1 and 2.)

36. **Done 22 Oct.** **One page to write on.** Title and text together on the writing page, with the verses, date
    and tags in a strip or sheet of their own. Today the text comes last on a form, as a preview
    that opens a second page with its own Cancel and Done. (Goes with 5.)

37. **Settled 22 Oct** by 36: the form and its preview are gone. **The preview on the form shows plain text.** Headings, bold, lists and highlights don't show
    until the note is opened; a bullet list reads as plain lines. Show it formatted.

38. **Done 22 Oct.** **Typing just before bold or highlighted text makes it bold or highlighted too.** New text
    should only carry on the style of what's before the cursor (`reindexStyleRanges` counts the
    start of a range as touching).

39. **Done 22 Oct.** **Lists that start themselves:** "- " or "* " starts a bullet list, "1. " a numbered one,
    "[] " a checklist (30), "> " a quote, "# " a heading, as in Google Docs and Notion.

40. **Done 22 Oct.** **A toolbar for everyone.** The buttons don't tell TalkBack what they are or whether they're
    on (it reads "B", "Ctr"), and they're 36dp where 48dp is the least to tap reliably.

41. **Done 22 Oct.** **Shortcuts on a physical keyboard:** Ctrl+B, Ctrl+I, Ctrl+U, Ctrl+Z, Ctrl+Y.

42. **Done 22 Oct.** **Web links** tappable on the note page (verse references already are), and verse references
    marked while writing too.

43. **Done 22 Oct.** **A hint on an empty page** ("Start writing…"): a new note's writing page is blank but for
    the cursor.

44. **Done 22 Oct.** **Copy from the note page.** Its text can't be selected, so a passage of a note can't be
    copied without opening the editor.

*Suggested toolbar,* one row that fits a phone, the hide-keyboard button pinned at the right:
Aa · B · I · highlight · • · ☐ · Shift-Tab · Tab | hide keyboard. "Aa" opens a small panel with
the rest: Title, Heading, Subheading and Normal text (25), underline, numbered list, quote,
Clear formatting (28), and alignment and size if they're kept (26, 27). Buttons light up for the
style at the cursor (22). *Done 22 Oct as suggested,* with alignment in the Aa panel and size left
out.
