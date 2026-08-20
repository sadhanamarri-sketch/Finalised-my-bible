package com.example.mybible.data

/**
 * Resolves a book abbreviation (in whatever casing/spelling scheme a source
 * file happens to use) to a canonical book name. Ported verbatim from the
 * Capacitor app's `BOOK_ALIASES` + `normKey`, which is deliberately more
 * permissive than [OSIS_ID_TO_BOOK] — the cross-reference dataset doesn't
 * necessarily use the same abbreviation scheme as the OSIS KJV file, so this
 * is tried first and falls back to the OSIS ids.
 */
private fun normKey(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")

private val BOOK_ALIASES: Map<String, String> = mapOf(
    "gen" to "Genesis", "genesis" to "Genesis",
    "ex" to "Exodus", "exo" to "Exodus", "exod" to "Exodus", "exodus" to "Exodus",
    "lev" to "Leviticus", "levit" to "Leviticus", "leviticus" to "Leviticus",
    "num" to "Numbers", "numb" to "Numbers", "numbers" to "Numbers",
    "deut" to "Deuteronomy", "deu" to "Deuteronomy", "deuteronomy" to "Deuteronomy",
    "josh" to "Joshua", "jos" to "Joshua", "joshua" to "Joshua",
    "judg" to "Judges", "jdg" to "Judges", "judges" to "Judges",
    "ruth" to "Ruth", "rut" to "Ruth",
    "1sam" to "1 Samuel", "1sa" to "1 Samuel", "1samuel" to "1 Samuel",
    "2sam" to "2 Samuel", "2sa" to "2 Samuel", "2samuel" to "2 Samuel",
    "1kgs" to "1 Kings", "1ki" to "1 Kings", "1kings" to "1 Kings",
    "2kgs" to "2 Kings", "2ki" to "2 Kings", "2kings" to "2 Kings",
    "1chr" to "1 Chronicles", "1ch" to "1 Chronicles", "1chronicles" to "1 Chronicles",
    "2chr" to "2 Chronicles", "2ch" to "2 Chronicles", "2chronicles" to "2 Chronicles",
    "ezra" to "Ezra", "ezr" to "Ezra",
    "neh" to "Nehemiah", "nehemiah" to "Nehemiah",
    "esth" to "Esther", "est" to "Esther", "esther" to "Esther",
    "job" to "Job",
    "ps" to "Psalms", "psa" to "Psalms", "psm" to "Psalms", "psalm" to "Psalms", "psalms" to "Psalms", "pss" to "Psalms",
    "prov" to "Proverbs", "pro" to "Proverbs", "proverbs" to "Proverbs",
    "eccl" to "Ecclesiastes", "ecc" to "Ecclesiastes", "ecclesiastes" to "Ecclesiastes",
    "song" to "Song of Solomon", "sos" to "Song of Solomon", "canticles" to "Song of Solomon", "sng" to "Song of Solomon",
    "isa" to "Isaiah", "isaiah" to "Isaiah",
    "jer" to "Jeremiah", "jeremiah" to "Jeremiah",
    "lam" to "Lamentations", "lamentations" to "Lamentations",
    "ezek" to "Ezekiel", "eze" to "Ezekiel", "ezk" to "Ezekiel", "ezekiel" to "Ezekiel",
    "dan" to "Daniel", "daniel" to "Daniel",
    "hos" to "Hosea", "hosea" to "Hosea",
    "joel" to "Joel", "jol" to "Joel",
    "amos" to "Amos", "amo" to "Amos",
    "obad" to "Obadiah", "oba" to "Obadiah", "obadiah" to "Obadiah",
    "jonah" to "Jonah", "jon" to "Jonah",
    "mic" to "Micah", "micah" to "Micah",
    "nah" to "Nahum", "nam" to "Nahum", "nahum" to "Nahum",
    "hab" to "Habakkuk", "habakkuk" to "Habakkuk",
    "zeph" to "Zephaniah", "zep" to "Zephaniah", "zephaniah" to "Zephaniah",
    "hag" to "Haggai", "haggai" to "Haggai",
    "zech" to "Zechariah", "zec" to "Zechariah", "zechariah" to "Zechariah",
    "mal" to "Malachi", "malachi" to "Malachi",
    "matt" to "Matthew", "mat" to "Matthew", "matthew" to "Matthew",
    "mark" to "Mark", "mar" to "Mark", "mrk" to "Mark",
    "luke" to "Luke", "luk" to "Luke",
    "john" to "John", "joh" to "John", "jhn" to "John",
    "acts" to "Acts", "act" to "Acts",
    "rom" to "Romans", "romans" to "Romans",
    "1cor" to "1 Corinthians", "1co" to "1 Corinthians", "1corinthians" to "1 Corinthians",
    "2cor" to "2 Corinthians", "2co" to "2 Corinthians", "2corinthians" to "2 Corinthians",
    "gal" to "Galatians", "galatians" to "Galatians",
    "eph" to "Ephesians", "ephesians" to "Ephesians",
    "phil" to "Philippians", "php" to "Philippians", "philippians" to "Philippians",
    "col" to "Colossians", "colossians" to "Colossians",
    "1thess" to "1 Thessalonians", "1th" to "1 Thessalonians", "1thessalonians" to "1 Thessalonians",
    "2thess" to "2 Thessalonians", "2th" to "2 Thessalonians", "2thessalonians" to "2 Thessalonians",
    "1tim" to "1 Timothy", "1ti" to "1 Timothy", "1timothy" to "1 Timothy",
    "2tim" to "2 Timothy", "2ti" to "2 Timothy", "2timothy" to "2 Timothy",
    "titus" to "Titus", "tit" to "Titus",
    "philem" to "Philemon", "phm" to "Philemon", "philemon" to "Philemon", "phlm" to "Philemon",
    "heb" to "Hebrews", "hebrews" to "Hebrews",
    "jas" to "James", "jam" to "James", "james" to "James",
    "1pet" to "1 Peter", "1pe" to "1 Peter", "1peter" to "1 Peter",
    "2pet" to "2 Peter", "2pe" to "2 Peter", "2peter" to "2 Peter",
    "1john" to "1 John", "1jo" to "1 John", "1jn" to "1 John",
    "2john" to "2 John", "2jo" to "2 John", "2jn" to "2 John",
    "3john" to "3 John", "3jo" to "3 John", "3jn" to "3 John",
    "jude" to "Jude", "jud" to "Jude",
    "rev" to "Revelation", "revelation" to "Revelation", "revelations" to "Revelation"
)

/** Book abbreviation (any casing/scheme) -> canonical book name, or null if unrecognized. */
fun resolveBookName(abbr: String): String? {
    val key = normKey(abbr)
    return BOOK_ALIASES[key] ?: OSIS_ID_TO_BOOK[abbr]
}
