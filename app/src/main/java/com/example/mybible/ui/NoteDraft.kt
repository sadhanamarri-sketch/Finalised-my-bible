package com.example.mybible.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.mybible.model.NoteDocument
import com.example.mybible.model.NoteItem
import com.example.mybible.model.NoteText
import com.example.mybible.ui.richtext.NoteEditorState

/**
 * A note being written: its title, verses, date, tags and text (with its undo), kept by the view
 * model rather than the page, so turning the phone or going to the Reader to add a verse keeps
 * it all; saved as it's written (see MainViewModel.saveDraft). [original] is the note as it was
 * opened, for Discard changes; [id] is 0 until a new note is first saved.
 */
@Stable
class NoteDraft(val original: NoteItem) {
    var id by mutableLongStateOf(original.id)
    var title by mutableStateOf(original.title)
    var refs by mutableStateOf(original.refs)
    var tags by mutableStateOf(original.tags)
    var date by mutableStateOf(original.noteDate)
    val body = NoteEditorState(bodyOf(original))

    val isNew: Boolean get() = original.id == 0L

    /** What it holds now, to tell a change from the last save. */
    fun key(): List<Any> = listOf(body.version, title, refs, tags, date)

    /** As last saved (or opened). */
    internal var savedKey: List<Any> = key()

    /** As it was opened, to tell whether there's anything to discard. */
    val openedKey: List<Any> = savedKey

    /** Nothing written: no title and no text. */
    fun isEmpty(): Boolean = title.isBlank() && body.note.isEmpty()

    companion object {
        /** A note's text as the editor holds it: its formatting, or for a note written before that, its lines. */
        fun bodyOf(note: NoteItem): NoteText =
            NoteDocument.fromJsonOrNull(note.richText)?.let(NoteText::from) ?: NoteText.plain(note.text)
    }
}
