package com.example.mybible.data

import android.content.Context
import com.example.mybible.data.local.BibleDao
import com.example.mybible.ui.components.BIBLE_BOOKS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the Telugu translation from the JSON files bundled at
 * `assets/telugu/<Book>.json` (the same files the Capacitor app bundles)
 * and writes them into existing Room verse rows.
 *
 * This never inserts new rows — only [KjvImporter] does that — so it's
 * safe to run before, after, or without a successful KJV download; any
 * verse that isn't in Room yet is simply skipped and picked up on a later
 * run once the English text exists.
 */
object TeluguImporter {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importInto(
        context: Context,
        dao: BibleDao,
        onBookImported: (bookName: String, booksDone: Int, booksTotal: Int) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val booksTotal = BIBLE_BOOKS.size
        BIBLE_BOOKS.forEachIndexed { index, bookName ->
            try {
                val jsonString = context.assets.open("telugu/$bookName.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val root = json.parseToJsonElement(jsonString).jsonObject
                val chapters = root["chapters"]?.jsonArray ?: return@forEachIndexed

                for (chapterElement in chapters) {
                    val chapterObj = chapterElement.jsonObject
                    val chapterNum = chapterObj["chapter"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: continue
                    val verses = chapterObj["verses"]?.jsonArray ?: continue
                    for (verseElement in verses) {
                        val verseObj = verseElement.jsonObject
                        val verseNum = verseObj["verse"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                        val text = verseObj["text"]?.jsonPrimitive?.content ?: continue
                        if (text.isNotEmpty()) {
                            dao.updateTelugu(bookName, chapterNum, verseNum, text)
                        }
                    }
                }
            } catch (e: Exception) {
                // Missing/malformed file for this book — skip it, don't abort the rest.
                e.printStackTrace()
            }
            onBookImported(bookName, index + 1, booksTotal)
        }
    }
}
