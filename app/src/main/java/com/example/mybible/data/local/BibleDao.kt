package com.example.mybible.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BibleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(verses: List<VerseEntity>)

    @Query("SELECT * FROM verses WHERE book = :book AND chapter = :chapter ORDER BY number")
    suspend fun getChapter(book: String, chapter: Int): List<VerseEntity>

    @Query("SELECT COUNT(*) FROM verses WHERE book = :book AND chapter = :chapter")
    suspend fun countInChapter(book: String, chapter: Int): Int

    // Telugu import only ever updates rows the KJV import already created —
    // never inserts — so a re-run, a partial download, or Telugu finishing
    // before English (unlikely, but possible if English fails) can't leave
    // Telugu-only ghost rows with no English text.
    @Query("UPDATE verses SET teluguText = :text WHERE book = :book AND chapter = :chapter AND number = :verse")
    suspend fun updateTelugu(book: String, chapter: Int, verse: Int, text: String)

    @Query("SELECT COUNT(*) FROM verses")
    suspend fun countAllVerses(): Int

    // Used for the Studied tab's Old/New Testament progress bars — total
    // verse count across a set of books (the 39 OT or 27 NT book names),
    // read from the imported data rather than a hardcoded canon-wide
    // constant so it stays correct if versification ever changes.
    @Query("SELECT COUNT(*) FROM verses WHERE book IN (:books)")
    suspend fun countVersesForBooks(books: List<String>): Int

    @Query("SELECT COUNT(*) FROM verses WHERE teluguText IS NOT NULL")
    suspend fun countTeluguVerses(): Int

    @Query(
        "SELECT * FROM verses WHERE text LIKE '%' || :query || '%' " +
        "OR teluguText LIKE '%' || :query || '%'"
    )
    suspend fun search(query: String): List<VerseEntity>

    @Query("SELECT text FROM verses WHERE book = :book AND chapter = :chapter AND number = :verse LIMIT 1")
    suspend fun getVerseText(book: String, chapter: Int, verse: Int): String?

    // Search's typo-tolerance builds an in-memory "words that actually
    // appear in the KJV" set from this once, then caches it — see
    // BibleRepository.getKjvWordSet. Only English text; typo-correction
    // doesn't extend to Telugu.
    @Query("SELECT text FROM verses")
    suspend fun getAllVerseTexts(): List<String>

    // ---- Greek interlinear (TAGNT) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGreekWords(words: List<GreekWordEntity>)

    @Query("SELECT * FROM greek_words WHERE book = :book AND chapter = :chapter ORDER BY verse, orderIndex")
    suspend fun getGreekWordsForChapter(book: String, chapter: Int): List<GreekWordEntity>

    @Query("SELECT COUNT(*) FROM greek_words")
    suspend fun countGreekWords(): Int

    // TAGNT downloads in 2 file-parts (gospels, Acts-Revelation — see
    // GreekImporter); each part is all-or-nothing (a failed fetch inserts
    // zero words for every book in that part; see BibleDataInitializer's
    // maybeImportGreek doc for why aggregate countGreekWords() alone can't
    // detect a missing part). Distinct book count catches that precisely.
    @Query("SELECT COUNT(DISTINCT book) FROM greek_words")
    suspend fun countDistinctGreekBooks(): Int

    // Used by Settings' "Re-check Greek/Hebrew data" — wipes the table so
    // maybeImportGreek's threshold check no longer sees it as "already
    // complete" and does a genuine fresh re-import against the current
    // upstream TAGNT file, rather than the REPLACE-on-insert conflict
    // strategy alone, which would leave any word STEPBible has since
    // removed/renumbered stranded as a stale leftover row.
    @Query("DELETE FROM greek_words")
    suspend fun deleteAllGreekWords()

    // ---- Hebrew interlinear (TAHOT) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHebrewWords(words: List<HebrewWordEntity>)

    @Query("SELECT * FROM hebrew_words WHERE book = :book AND chapter = :chapter ORDER BY verse, orderIndex")
    suspend fun getHebrewWordsForChapter(book: String, chapter: Int): List<HebrewWordEntity>

    @Query("SELECT COUNT(*) FROM hebrew_words")
    suspend fun countHebrewWords(): Int

    // TAHOT downloads in 4 file-parts (Gen-Deu, Jos-Est, Job-Sng, Isa-Mal —
    // see HebrewImporter); see countDistinctGreekBooks() above for why this
    // is needed alongside the aggregate count.
    @Query("SELECT COUNT(DISTINCT book) FROM hebrew_words")
    suspend fun countDistinctHebrewBooks(): Int

    // See deleteAllGreekWords's doc — same reasoning, for TAHOT.
    @Query("DELETE FROM hebrew_words")
    suspend fun deleteAllHebrewWords()

    // ---- Cross references (Treasury of Scripture Knowledge) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossReferences(refs: List<CrossReferenceEntity>)

    @Query(
        "SELECT * FROM cross_references WHERE fromBook = :book AND fromChapter = :chapter " +
        "AND fromVerse = :verse"
    )
    suspend fun getCrossReferences(book: String, chapter: Int, verse: Int): List<CrossReferenceEntity>

    // Cheap, indexed, chapter-scoped: just the verse numbers that have at
    // least one cross-reference, so the reader can mark them with a dagger
    // without a per-verse query for every verse on screen.
    @Query(
        "SELECT DISTINCT fromVerse FROM cross_references WHERE fromBook = :book AND fromChapter = :chapter"
    )
    suspend fun getCrossReferenceVerseNumbers(book: String, chapter: Int): List<Int>

    @Query("SELECT COUNT(*) FROM cross_references")
    suspend fun countCrossReferences(): Int

    // ---- Greek lexicon (TBESG) ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLexiconEntries(entries: List<LexiconEntity>)

    // Exact match on the disambiguated Strong's form (dStrong#, e.g.
    // G4613H) — the precise per-occurrence key TAGNT/TAHOT tag words with.
    // See LexiconEntity's class doc for why this must be tried before the
    // bare-number fallback below.
    @Query("SELECT * FROM lexicon_entries WHERE strongsDisambiguated = :key LIMIT 1")
    suspend fun getLexiconEntryByDisambiguated(key: String): LexiconEntity?

    // Fallback when no row has that exact disambiguated key: any row for
    // the bare eStrong, preferring the earliest-lettered one (TBESG's own
    // "primary/general meaning" convention — its disambiguation suffixes
    // consistently start at "G", so ascending order picks it first).
    @Query("SELECT * FROM lexicon_entries WHERE strongs = :key ORDER BY strongsDisambiguated ASC LIMIT 1")
    suspend fun getLexiconEntryByBareStrongs(key: String): LexiconEntity?

    @Query("SELECT COUNT(*) FROM lexicon_entries")
    suspend fun countLexiconEntries(): Int

    // Same lexicon_entries table (strongs is unique regardless of language —
    // "G..." and "H..." never collide), just counted separately so Hebrew's
    // TBESH import can be tracked/retried independently of Greek's TBESG.
    @Query("SELECT COUNT(*) FROM lexicon_entries WHERE strongs LIKE 'H%'")
    suspend fun countHebrewLexiconEntries(): Int

    // ---- Webster's 1828 Dictionary (English word lookup) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebsterEntries(entries: List<WebsterEntity>)

    @Query("SELECT * FROM webster_entries WHERE word = :word LIMIT 1")
    suspend fun getWebsterEntry(word: String): WebsterEntity?

    @Query("SELECT COUNT(*) FROM webster_entries")
    suspend fun countWebsterEntries(): Int

}
