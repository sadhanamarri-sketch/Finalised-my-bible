package com.example.mybible.data

import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.LexiconEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports STEPBible's TBESH (Translators Brief lexicon of Extended Strongs
 * for Hebrew — abridged BDB, CC BY 4.0, Tyndale House Cambridge /
 * STEPBible.org). This is what was missing before: TAHOT (imported by
 * [HebrewImporter]) gives the interlinear word-by-word gloss, but the
 * fuller "STRONG'S H..." definition shown in HebrewWordSheet comes from
 * this lexicon — same relationship TBESG has to TAGNT for Greek.
 *
 * Same TSV shape and same repo/folder as TBESG (just Hebrew's file, in the
 * same "Lexicons" folder), so the parsing logic here is TbesgImporter's,
 * unchanged, pointed at TBESH's URL and filtering for "H" keys instead of
 * "G". Kept as its own object (rather than sharing code with TbesgImporter)
 * to match how HebrewImporter/GreekImporter are already two separate,
 * independently-retryable importers in this codebase.
 */
object TbeshImporter {

    private const val TBESH_URL =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Lexicons/TBESH%20-%20Translators%20Brief%20lexicon%20of%20Extended%20Strongs%20for%20Hebrew%20-%20STEPBible.org%20CC%20BY.txt"

    private val STRONGS_KEY_RE = Regex("^[HG]\\d+[A-Za-z]*")
    private val PRIMARY_DSTRONG_RE = Regex("^[HG]\\d+[A-Za-z]*G$")
    private val BR_TAG_RE = Regex("<BR\\s*/?>", RegexOption.IGNORE_CASE)
    private val ANY_TAG_RE = Regex("<[^>]+>")
    private val UNDERSCORES_RE = Regex("_+")
    private val WHITESPACE_RE = Regex("[ \\t]+")

    /** Parse a TSV row without breaking quoted fields containing tabs. */
    private fun parseTsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                '\t' -> if (inQuotes) current.append(c) else {
                    fields += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString().trim()
        return fields
    }

    /** TBESH markup is display formatting, not lexical data — same cleanup as TBESG. */
    private fun cleanTbeshText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(BR_TAG_RE, "\n")
            .replace(ANY_TAG_RE, "")
            .replace(UNDERSCORES_RE, "")
            .split("\n")
            .map { it.replace(WHITESPACE_RE, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun normalizeKey(raw: String): String? =
        STRONGS_KEY_RE.find(raw.trim())?.value?.uppercase()

    private fun isPrimaryMeaning(dStrong: String): Boolean =
        PRIMARY_DSTRONG_RE.matches(dStrong.trim().removeSuffix("="))

    private fun parseEntry(cols: List<String>): LexiconEntity? {
        if (cols.size < 7) return null

        val strongs = normalizeKey(cols[0]) ?: return null
        if (!strongs.startsWith("H")) return null

        return LexiconEntity(
            strongs = strongs,
            strongsDisambiguated = cols.getOrNull(1).orEmpty().removeSuffix("=").trim(),
            strongsUnified = cols.getOrNull(2).orEmpty().removeSuffix("=").trim(),
            lemma = cols.getOrNull(3).orEmpty().trim(),
            transliteration = cols.getOrNull(4).orEmpty().trim(),
            morphology = cols.getOrNull(5).orEmpty().trim(),
            gloss = cleanTbeshText(cols.getOrNull(6).orEmpty()),
            definition = cleanTbeshText(cols.getOrNull(7).orEmpty())
        )
    }

    private fun parse(fullText: String): List<LexiconEntity> {
        val map = LinkedHashMap<String, LexiconEntity>()
        var dataStarted = false

        for (rawLine in fullText.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (!dataStarted) {
                if (trimmed.contains("eStrong") || trimmed.contains("Strong")) {
                    dataStarted = true
                }
                continue
            }

            val entry = parseEntry(parseTsvLine(line)) ?: continue
            val existing = map[entry.strongs]

            if (existing == null ||
                (isPrimaryMeaning(entry.strongsDisambiguated) &&
                    !isPrimaryMeaning(existing.strongsDisambiguated))) {
                map[entry.strongs] = entry
            }
        }
        return map.values.toList()
    }

    /** Downloads + parses TBESH and bulk-inserts it into Room. */
    suspend fun importInto(dao: BibleDao): Int = withContext(Dispatchers.IO) {
        val text = fetchTextOrNull(TBESH_URL) ?: return@withContext -1
        val entries = parse(text)
        if (entries.isNotEmpty()) dao.insertLexiconEntries(entries)
        entries.size
    }
}
