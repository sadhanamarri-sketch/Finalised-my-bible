package com.example.mybible.data

import android.util.Xml
import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.VerseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the same public-domain OSIS KJV XML the Capacitor app uses
 * (~10MB, one-time) and parses every verse out of it in a single streaming
 * pass, then bulk-inserts into Room grouped by book.
 *
 * The file uses "milestone" style verses: `<verse sID="Gen.1.1" .../>`
 * opens a verse, its text follows as normal XML content (which may include
 * `<note>` and `<q who="Jesus">` elements), and `<verse eID="Gen.1.1"/>`
 * closes it. This parsing approach — and the URL itself — is ported from
 * the Capacitor app's `parseOsisBible`, which is already proven against
 * this exact file.
 */
object KjvImporter {

    const val OSIS_KJV_URL =
        "https://raw.githubusercontent.com/seven1m/open-bibles/master/eng-kjv.osis.xml"

    private data class OpenVerse(
        val ids: List<String>,
        val text: StringBuilder = StringBuilder(),
        var hasJesusWords: Boolean = false
    )

    /**
     * Downloads + parses the OSIS file and writes it into Room, one book at
     * a time so [onBookImported] can drive a progress indicator.
     * Returns the total number of verses imported.
     */
    suspend fun importInto(
        dao: BibleDao,
        onBookImported: (bookName: String, booksDone: Int, booksTotal: Int) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        // book name -> verses, preserving first-seen order for progress reporting
        val byBook = LinkedHashMap<String, MutableList<VerseEntity>>()

        val connection = (URL(OSIS_KJV_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
        }

        connection.inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(InputStreamReader(stream, Charsets.UTF_8))

            var open: OpenVerse? = null
            var noteDepth = 0
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "verse" -> {
                                val sId = parser.getAttributeValue(null, "sID")
                                val eId = parser.getAttributeValue(null, "eID")
                                val osisId = parser.getAttributeValue(null, "osisID")
                                if (sId != null) {
                                    open = OpenVerse(ids = (osisId ?: sId).split(" "))
                                    noteDepth = 0
                                } else if (eId != null && open != null) {
                                    finalizeVerse(open!!, byBook)
                                    open = null
                                }
                            }
                            "note" -> if (open != null) noteDepth++
                            "q" -> if (open != null && noteDepth == 0) {
                                if (parser.getAttributeValue(null, "who") == "Jesus") {
                                    open!!.hasJesusWords = true
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "note" && open != null && noteDepth > 0) noteDepth--
                    }
                    XmlPullParser.TEXT -> {
                        if (open != null && noteDepth == 0) {
                            open!!.text.append(parser.text)
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        val booksTotal = byBook.size
        var booksDone = 0
        var total = 0
        for ((bookName, verses) in byBook) {
            dao.insertVerses(verses)
            total += verses.size
            booksDone++
            onBookImported(bookName, booksDone, booksTotal)
        }
        total
    }

    private fun finalizeVerse(open: OpenVerse, byBook: MutableMap<String, MutableList<VerseEntity>>) {
        val cleanText = open.text.toString().replace(Regex("\\s+"), " ").trim()
        if (cleanText.isEmpty()) return
        for (id in open.ids) {
            val parts = id.split(".")
            if (parts.size != 3) continue
            val bookName = OSIS_ID_TO_BOOK[parts[0]] ?: continue
            val chapterNum = parts[1].toIntOrNull() ?: continue
            val verseNum = parts[2].toIntOrNull() ?: continue
            byBook.getOrPut(bookName) { mutableListOf() }.add(
                VerseEntity(
                    book = bookName,
                    chapter = chapterNum,
                    number = verseNum,
                    text = cleanText,
                    isRedLetter = open.hasJesusWords
                )
            )
        }
    }
}
