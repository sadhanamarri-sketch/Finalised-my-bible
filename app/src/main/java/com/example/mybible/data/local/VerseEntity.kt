package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * One verse of KJV text, optionally with its Telugu counterpart, persisted
 * locally so reading, search, and navigation never need the network after
 * the first import.
 *
 * Rows are created by [com.example.mybible.data.KjvImporter] (English text)
 * and then updated in place by [com.example.mybible.data.TeluguImporter]
 * (Telugu text) — Telugu import never inserts new rows, only fills in
 * `teluguText` on rows that already exist.
 */
@Entity(
    tableName = "verses",
    primaryKeys = ["book", "chapter", "number"],
    indices = [Index(value = ["book", "chapter"])]
)
data class VerseEntity(
    val book: String,
    val chapter: Int,
    val number: Int,
    val text: String,
    val teluguText: String? = null,
    val isRedLetter: Boolean = false
)
