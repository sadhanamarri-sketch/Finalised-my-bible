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

    // ---- Greek interlinear (TAGNT) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGreekWords(words: List<GreekWordEntity>)

    @Query("SELECT * FROM greek_words WHERE book = :book AND chapter = :chapter ORDER BY verse, orderIndex")
    suspend fun getGreekWordsForChapter(book: String, chapter: Int): List<GreekWordEntity>

    @Query("SELECT COUNT(*) FROM greek_words")
    suspend fun countGreekWords(): Int

    // ---- Hebrew interlinear (TAHOT) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHebrewWords(words: List<HebrewWordEntity>)

    @Query("SELECT * FROM hebrew_words WHERE book = :book AND chapter = :chapter ORDER BY verse, orderIndex")
    suspend fun getHebrewWordsForChapter(book: String, chapter: Int): List<HebrewWordEntity>

    @Query("SELECT COUNT(*) FROM hebrew_words")
    suspend fun countHebrewWords(): Int

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

    @Query("SELECT * FROM lexicon_entries WHERE strongs = :key LIMIT 1")
    suspend fun getLexiconEntry(key: String): LexiconEntity?

    @Query("SELECT COUNT(*) FROM lexicon_entries")
    suspend fun countLexiconEntries(): Int

    // ---- Webster's 1828 Dictionary (English word lookup) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebsterEntries(entries: List<WebsterEntity>)

    @Query("SELECT * FROM webster_entries WHERE word = :word LIMIT 1")
    suspend fun getWebsterEntry(word: String): WebsterEntity?

    @Query("SELECT COUNT(*) FROM webster_entries")
    suspend fun countWebsterEntries(): Int

}
