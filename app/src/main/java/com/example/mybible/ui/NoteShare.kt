package com.example.mybible.ui

import android.content.Context
import android.content.Intent

/** Android's share sheet for [text] (a note, as NoteSharing writes it), with [subject] for an email's subject line. */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share note"))
}
