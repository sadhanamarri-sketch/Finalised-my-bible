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
 * eStrong key — every one of them is now kept (see LexiconEntity's class
 * doc for why discarding the non-primary ones used to lose real, distinct
 * definitions, not just harmless duplicates).
 */
object TbesgImporter {

    private const val TBESG_URL =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Lexicons/TBESG%20-%20Translators%20Brief%20lexicon%20of%20Extended%20Strongs%20for%20Greek%20-%20STEPBible.org%20CC%20BY.txt"

    private val STRONGS_KEY_RE = Regex("^[HG]\\d+[A-Za-z]*")
    private val BR_TAG_RE = Regex("<BR\\s*/?>", RegexOption.IGNORE_CASE)
    // Every Scripture citation in the source is already wrapped in a
    // machine-parseable tag, e.g. <ref='Rom.5.8'>Rom.5:8;</ref> — the key
    // attribute is the unambiguous "book.chapter.verse" (occasionally a
    // semicolon/comma-chained list of them), the inner text is what a
    // reader sees. The old cleanup stripped this tag exactly like every
    // other one, throwing the structured half away and leaving only prose
    // a regex would later have to guess back out of. Converted here to a
    // "⟦key|display⟧" marker instead, preserved through the rest of
    // cleanTbesgText and consumed by LexiconDefinitionFormatter.parse() —
    // for now that just unwraps it back to the display text (no visible
    // change yet), until the reference is wired up to actually navigate.
    // A handful of source rows nest one <ref> inside another (a data
    // quirk, ~6 of ~41,500 tags) — the non-greedy match below resolves the
    // inner one and leaves the outer tag's leftover fragment to be swept
    // up by ANY_TAG_RE like any unrecognized tag, rather than corrupting
    // surrounding text.
    private val REF_TAG_RE = Regex("<ref='([^']*)'>(.*?)</ref>")
    private val ANY_TAG_RE = Regex("<[^>]+>")
    private val UNDERSCORES_RE = Regex("_+")
    private val WHITESPACE_RE = Regex("[ \\t]+")
    // TBESG appends a bare source-attribution code to the very end of the
    // Meaning column with no separator from the prose before it — e.g.
    // "...MM, VGT, see word) (AS)" or "...(New Testament) (ML)" — so
    // cleanTbesgText previously left an unlabeled "(AS)"/"(ML)" fragment
    // glued onto whatever sentence happened to be last. Stripped here and
    // re-appended as its own clearly-labeled line instead. A trailing "†"
    // (lexicographic "not elsewhere in NT" mark) sometimes precedes it —
    // absorbed along with the code rather than left dangling alone.
    private val TRAILING_ATTRIBUTION_RE = Regex("[\\s†]*\\((AS|ML)\\)\\s*$")

    private fun attributionLabel(code: String) = when (code) {
        "AS" -> "Abbott-Smith's Manual Greek Lexicon of the New Testament"
        "ML" -> "Liddell & Scott's Abridged (Middle) Greek Lexicon"
        else -> code
    }

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

    /** TBESG markup is display formatting, not lexical data (except the
     *  Scripture-reference and attribution structure preserved above). */
    private fun cleanTbesgText(raw: String): String {
        if (raw.isBlank()) return ""
        val attributionMatch = TRAILING_ATTRIBUTION_RE.find(raw)
        val withoutAttribution = if (attributionMatch != null) raw.substring(0, attributionMatch.range.first) else raw

        val body = withoutAttribution
            .replace(REF_TAG_RE) { m -> "⟦${m.groupValues[1]}|${m.groupValues[2]}⟧" }
            .replace(BR_TAG_RE, "\n")
            .replace(ANY_TAG_RE, "")
            .replace(UNDERSCORES_RE, "")
            .split("\n")
            .map { it.replace(WHITESPACE_RE, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()

        if (attributionMatch == null) return body
        val attributionLine = "— ${attributionLabel(attributionMatch.groupValues[1])}"
        return if (body.isEmpty()) attributionLine else "$body\n$attributionLine"
    }

    private fun normalizeKey(raw: String): String? =
        STRONGS_KEY_RE.find(raw.trim())?.value?.uppercase()

    private fun parseEntry(cols: List<String>): LexiconEntity? {
        if (cols.size < 7) return null

        val strongs = normalizeKey(cols[0]) ?: return null
        if (!strongs.startsWith("G")) return null
        // dStrong/uStrong columns often carry trailing descriptive text
        // after their own "=" (e.g. "G4613O = a Name of", not just
        // "G4613O ="), which a plain removeSuffix("=") doesn't clean —
        // extracting the leading Strong's-shaped token is the correct cut
        // regardless of what follows it.
        val strongsDisambiguated = normalizeKey(cols.getOrNull(1).orEmpty()) ?: strongs
        val strongsUnified = normalizeKey(cols.getOrNull(2).orEmpty()).orEmpty()

        return LexiconEntity(
            strongsDisambiguated = strongsDisambiguated,
            strongs = strongs,
            strongsUnified = strongsUnified,
            lemma = cols.getOrNull(3).orEmpty().trim(),
            transliteration = cols.getOrNull(4).orEmpty().trim(),
            morphology = cols.getOrNull(5).orEmpty().trim(),
            gloss = cleanTbesgText(cols.getOrNull(6).orEmpty()),
            definition = cleanTbesgText(cols.getOrNull(7).orEmpty())
        )
    }

    private fun parse(fullText: String): List<LexiconEntity> {
        // Keyed by strongsDisambiguated, which is unique per source row
        // (verified against the live file — no two rows ever share a
        // dStrong code), so this is just an accumulator, not a
        // one-per-eStrong dedup like it used to be.
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
            map[entry.strongsDisambiguated] = entry
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
