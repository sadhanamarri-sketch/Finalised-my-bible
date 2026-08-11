package com.example.mybible.data

import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.LexiconEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports STEPBible's TBESG (Translators Brief lexicon of Extended Strongs
 * for Greek — CC BY 4.0, Tyndale House Cambridge / STEPBible.org).
 *
 * Important: TBESG is a structured TSV. Its columns are:
 *   0 eStrong#  1 dStrong#  2 uStrong#  3 Greek  4 Transliteration
 *   5 Morph     6 Gloss     7 Meaning
 *
 * Do not infer meaning from field length. Multiple rows can share the same
 * eStrong key; the primary dStrong meaning (suffix G) is preferred.
 */
object TbesgImporter {

    private const val TBESG_URL =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Lexicons/TBESG%20-%20Translators%20Brief%20lexicon%20of%20Extended%20Strongs%20for%20Greek%20-%20STEPBible.org%20CC%20BY.txt"

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
                    // TSV source uses quotes to protect fields. A doubled quote
                    // represents a literal quote inside a quoted field.
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

    /** TBESG markup is display formatting, not lexical data. */
    private fun cleanTbesgText(raw: String): String {
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
        if (!strongs.startsWith("G")) return null

        return LexiconEntity(
            strongs = strongs,
            strongsDisambiguated = cols.getOrNull(1).orEmpty().removeSuffix("=").trim(),
            strongsUnified = cols.getOrNull(2).orEmpty().removeSuffix("=").trim(),
            lemma = cols.getOrNull(3).orEmpty().trim(),
            transliteration = cols.getOrNull(4).orEmpty().trim(),
            morphology = cols.getOrNull(5).orEmpty().trim(),
            gloss = cleanTbesgText(cols.getOrNull(6).orEmpty()),
            definition = cleanTbesgText(cols.getOrNull(7).orEmpty())
        )
    }

    private fun parse(fullText: String): List<LexiconEntity> {
        val map = LinkedHashMap<String, LexiconEntity>()
        var dataStarted = false

        for (rawLine in fullText.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Ignore the source preamble until the TSV header is reached.
            if (!dataStarted) {
                if (trimmed.contains("eStrong") || trimmed.contains("Strong")) {
                    dataStarted = true
                }
                continue
            }

            val entry = parseEntry(parseTsvLine(line)) ?: continue
            val existing = map[entry.strongs]

            // The source can contain several disambiguated meanings under one
            // eStrong. Prefer the primary/general meaning (dStrong suffix G).
            if (existing == null ||
                (isPrimaryMeaning(entry.strongsDisambiguated) &&
                    !isPrimaryMeaning(existing.strongsDisambiguated))) {
                map[entry.strongs] = entry
            }
        }
        return map.values.toList()
    }

    /** Downloads + parses TBESG and bulk-inserts it into Room. */
    suspend fun importInto(dao: BibleDao): Int = withContext(Dispatchers.IO) {
        val text = fetchTextOrNull(TBESG_URL) ?: return@withContext -1
        val entries = parse(text)
        if (entries.isNotEmpty()) dao.insertLexiconEntries(entries)
        entries.size
    }
}
