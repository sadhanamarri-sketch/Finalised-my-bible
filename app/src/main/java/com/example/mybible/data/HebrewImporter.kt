package com.example.mybible.data

import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.HebrewWordEntity
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads STEPBible's TAHOT (Translators Amalgamated Hebrew OT — CC BY
 * 4.0, Tyndale House Cambridge / STEPBible.org) and imports real interlinear
 * Hebrew/transliteration/gloss/Strong's data for every OT verse. Mirrors
 * [GreekImporter] exactly — same repo, same folder, same per-word row
 * format (`Book.Chapter.Verse#WordIndex=SourceType`) — just the other
 * testament and the other of the two STEPBible "Translators Amalgamated"
 * datasets.
 *
 * TAHOT ships as 4 files (too large for a single GitHub file), split by
 * book range: Gen-Deu, Jos-Est, Job-Sng, Isa-Mal. Source URLs, the
 * book-abbreviation table, chapter-block extraction, and the per-word row
 * parser were checked directly against those 4 files (word counts and
 * sample rows for Genesis 1 verified during development), the same way
 * GreekImporter's logic was proven against the real TAGNT files.
 *
 * Unlike Greek's dStrongs column (a single tag per word), Hebrew's dStrongs
 * cell can chain a prefix/root/suffix, e.g. "H9003/{H7225G}" for "in the
 * beginning" — prefix H9003 ("in") plus root H7225G ("beginning"), with the
 * root always wrapped in {braces}. [strongsRootRegex] pulls out just that
 * root for the word's primary Strong's number (used for the lexicon lookup
 * and the sheet's "STRONG'S ..." header), matching what a reader taps the
 * chip expecting to look up.
 */
object HebrewImporter {

    private const val BASE_URL =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Translators%20Amalgamated%20OT%2BNT/"

    private const val TAHOT_URL_GEN_DEU =
        BASE_URL + "TAHOT%20Gen-Deu%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt"
    private const val TAHOT_URL_JOS_EST =
        BASE_URL + "TAHOT%20Jos-Est%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt"
    private const val TAHOT_URL_JOB_SNG =
        BASE_URL + "TAHOT%20Job-Sng%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt"
    private const val TAHOT_URL_ISA_MAL =
        BASE_URL + "TAHOT%20Isa-Mal%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt"

    // TAHOT's own book-abbreviation scheme, indexed to line up with
    // BIBLE_BOOKS[0..38] (Genesis..Malachi — the 39 OT books). Pulled
    // directly from the actual data files rather than guessed — note Joel
    // is "Jol" and Nahum is "Nam" (not the more obvious "Joe"/"Nah"),
    // which is how STEPBible avoids collisions elsewhere in their scheme.
    private val TAHOT_ABBR = listOf(
        "Gen", "Exo", "Lev", "Num", "Deu", "Jos", "Jdg", "Rut",
        "1Sa", "2Sa", "1Ki", "2Ki", "1Ch", "2Ch", "Ezr", "Neh",
        "Est", "Job", "Psa", "Pro", "Ecc", "Sng", "Isa", "Jer",
        "Lam", "Ezk", "Dan", "Hos", "Jol", "Amo", "Oba", "Jon",
        "Mic", "Nam", "Hab", "Zep", "Hag", "Zec", "Mal"
    )
    private val PART_FOR_ABBR: Map<String, String> = mapOf(
        "Gen" to "genDeu", "Exo" to "genDeu", "Lev" to "genDeu", "Num" to "genDeu", "Deu" to "genDeu",
        "Jos" to "josEst", "Jdg" to "josEst", "Rut" to "josEst", "1Sa" to "josEst", "2Sa" to "josEst",
        "1Ki" to "josEst", "2Ki" to "josEst", "1Ch" to "josEst", "2Ch" to "josEst", "Ezr" to "josEst", "Neh" to "josEst", "Est" to "josEst",
        "Job" to "jobSng", "Psa" to "jobSng", "Pro" to "jobSng", "Ecc" to "jobSng", "Sng" to "jobSng",
        "Isa" to "isaMal", "Jer" to "isaMal", "Lam" to "isaMal", "Ezk" to "isaMal", "Dan" to "isaMal",
        "Hos" to "isaMal", "Jol" to "isaMal", "Amo" to "isaMal", "Oba" to "isaMal", "Jon" to "isaMal",
        "Mic" to "isaMal", "Nam" to "isaMal", "Hab" to "isaMal", "Zep" to "isaMal", "Hag" to "isaMal",
        "Zec" to "isaMal", "Mal" to "isaMal"
    )

    private fun urlForPart(part: String) = when (part) {
        "genDeu" -> TAHOT_URL_GEN_DEU
        "josEst" -> TAHOT_URL_JOS_EST
        "jobSng" -> TAHOT_URL_JOB_SNG
        else -> TAHOT_URL_ISA_MAL
    }

    private fun abbrFor(bookName: String): String? {
        val idx = BIBLE_BOOKS.indexOf(bookName)
        return TAHOT_ABBR.getOrNull(idx)
    }

    private val REF_REGEX = Regex("^[0-9A-Za-z]+\\.(\\d+)\\.(\\d+)#(\\d+)=(\\S*)$")
    private val HEADER_REGEX = Regex("\\n# ([0-9A-Za-z]+)\\.(\\d+)\\.")
    private val strongsRootRegex = Regex("\\{([^}]+)\\}")

    suspend fun importInto(
        dao: BibleDao,
        onBookImported: (bookName: String, booksDone: Int, booksTotal: Int) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val otBooks = BIBLE_BOOKS.take(39) // Genesis..Malachi
        val booksTotal = otBooks.size
        var booksDone = 0
        var anySucceeded = false

        for (part in listOf("genDeu", "josEst", "jobSng", "isaMal")) {
            val url = urlForPart(part)
            val booksInPart = otBooks.filter { name -> abbrFor(name)?.let { PART_FOR_ABBR[it] == part } == true }

            val fullText = fetchTextOrNull(url)
            if (fullText == null) {
                // This file failed — skip its books but keep going with the other files.
                booksDone += booksInPart.size
                booksInPart.forEach { onBookImported(it, booksDone, booksTotal) }
                continue
            }

            for (bookName in booksInPart) {
                val abbr = abbrFor(bookName) ?: continue
                val chapterCount = BOOK_CHAPTER_COUNTS[bookName] ?: 0
                val words = mutableListOf<HebrewWordEntity>()
                for (chapter in 1..chapterCount) {
                    val block = extractChapterBlock(fullText, abbr, chapter) ?: continue
                    words.addAll(parseTahotChapter(block, bookName, chapter))
                }
                if (words.isNotEmpty()) {
                    dao.insertHebrewWords(words)
                    anySucceeded = true
                }
                booksDone++
                onBookImported(bookName, booksDone, booksTotal)
            }
        }
        anySucceeded
    }

    // Slices out just the lines belonging to one chapter (from the
    // "# Abbr.C.1" verse-1 interlinear header up to, but not including, the
    // next chapter/book's header) — identical approach to GreekImporter,
    // and TAHOT carries the same "# Book.C.V" header line per verse that
    // TAGNT does, so the same bounding logic applies unchanged.
    private fun extractChapterBlock(fullText: String, abbr: String, chapter: Int): String? {
        val marker = "# $abbr.$chapter."
        val start = fullText.indexOf(marker)
        if (start == -1) return null
        var end = fullText.length
        for (m in HEADER_REGEX.findAll(fullText, start)) {
            val mAbbr = m.groupValues[1]
            val mChapter = m.groupValues[2].toIntOrNull()
            if (mAbbr != abbr || mChapter != chapter) {
                end = m.range.first
                break
            }
        }
        return fullText.substring(start, end)
    }

    // Parses per-word data rows like:
    // "Gen.1.1#01=L\tבְּ/רֵאשִׁ֖ית\tbe./re.Shit\tin/ beginning\tH9003/{H7225G}\tHR/Ncfsa\t...\tH7225G\t..."
    // Columns: [0] Ref  [1] Hebrew  [2] Transliteration  [3] English gloss
    // [4] dStrongs  [5] Grammar  [6] Meaning variant  [7] (blank)
    // [8] sStrong+Instance  [9] Alt Strongs  [10] Conjoin  [11] Expanded tags
    private fun parseTahotChapter(blockText: String, bookName: String, chapter: Int): List<HebrewWordEntity> {
        val result = mutableListOf<HebrewWordEntity>()
        val orderCounters = mutableMapOf<Int, Int>()
        for (line in blockText.split("\n")) {
            if (line.isEmpty() || line[0] == '#') continue
            val cols = line.split("\t")
            if (cols.size < 5) continue
            val m = REF_REGEX.find(cols[0].trim()) ?: continue
            val verse = m.groupValues[2].toIntOrNull() ?: continue

            val hebrew = cols.getOrElse(1) { "" }.trim()
            if (hebrew.isEmpty()) continue
            val translit = cols.getOrElse(2) { "" }.trim()
            val gloss = cols.getOrElse(3) { "" }.trim()

            val dStrongCell = cols.getOrElse(4) { "" }
            val strongsRoot = strongsRootRegex.find(dStrongCell)?.groupValues?.get(1)
                ?: dStrongCell.trim().trimStart('{').trimEnd('}')
            val morphology = cols.getOrElse(5) { "" }.trim()

            val orderIndex = orderCounters.getOrDefault(verse, 0)
            orderCounters[verse] = orderIndex + 1
            result.add(
                HebrewWordEntity(
                    book = bookName,
                    chapter = chapter,
                    verse = verse,
                    orderIndex = orderIndex,
                    hebrew = hebrew,
                    transliteration = translit,
                    gloss = gloss,
                    strongs = strongsRoot,
                    morphology = morphology
                )
            )
        }
        return result
    }
}
