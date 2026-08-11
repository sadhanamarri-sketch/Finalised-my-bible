package com.example.mybible.data

import android.content.Context
import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.WebsterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads Noah Webster's 1828 dictionary from the bundled
 * `assets/webster1828.json` (pre-parsed at asset-build time from the
 * public-domain source CSV — see the format note on [WebsterEntity]) and
 * bulk-inserts it into Room, replacing the old live per-word calls to
 * api.dictionaryapi.dev. Fully offline, one-time, no network involved —
 * same shape as [TeluguImporter].
 *
 * Asset schema: `{ "word": { "p": "part of speech", "d": ["sense 1", "sense 2", ...] }, ... }`
 */
object WebsterImporter {

    private val json = Json { ignoreUnknownKeys = true }

    // Batched inserts so Room isn't handed one 60k-item list in a single
    // transaction (keeps memory/latency reasonable and gives the progress
    // callback something to report incrementally).
    private const val BATCH_SIZE = 2000

    suspend fun importInto(
        context: Context,
        dao: BibleDao,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val jsonString = context.assets.open("webster1828.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = json.parseToJsonElement(jsonString).jsonObject
        val total = root.size

        val batch = ArrayList<WebsterEntity>(BATCH_SIZE)
        var done = 0
        for ((word, value) in root) {
            val entry = value.jsonObject
            val pos = entry["p"]?.jsonPrimitive?.content.orEmpty()
            val defs = entry["d"]?.jsonArray?.mapIndexedNotNull { index, el ->
                val text = el.jsonPrimitive.content
                if (text.isBlank()) null else "${index + 1}. $text"
            }.orEmpty()
            if (defs.isEmpty()) continue

            val definition = buildString {
                append(pos)
                append('\n')
                append(defs.joinToString("\n"))
            }
            batch.add(WebsterEntity(word = word, definition = definition))

            if (batch.size >= BATCH_SIZE) {
                dao.insertWebsterEntries(batch)
                batch.clear()
            }
            done++
            if (done % BATCH_SIZE == 0) onProgress(done, total)
        }
        if (batch.isNotEmpty()) dao.insertWebsterEntries(batch)
        onProgress(total, total)
    }
}
