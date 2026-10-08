package com.example.mybible.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A verse marked studied, and when. One row per verse, so marking or unmarking one writes that
 * row alone (the old store rewrote the whole list every time).
 */
@Entity(tableName = "studied_verses", primaryKeys = ["book", "chapter", "verse"])
data class StudiedVerseEntity(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val completedAt: Long
)

@Dao
interface StudiedVerseDao {
    /** Every studied verse, again each time one is marked or unmarked. */
    @Query("SELECT * FROM studied_verses")
    fun observeAll(): Flow<List<StudiedVerseEntity>>

    @Query("SELECT * FROM studied_verses")
    suspend fun getAll(): List<StudiedVerseEntity>

    @Query("SELECT * FROM studied_verses WHERE book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun find(book: String, chapter: Int, verse: Int): StudiedVerseEntity?

    @Query("SELECT * FROM studied_verses WHERE book = :book AND chapter = :chapter")
    suspend fun inChapter(book: String, chapter: Int): List<StudiedVerseEntity>

    @Query("SELECT COUNT(*) FROM studied_verses")
    suspend fun count(): Int

    /** Adds the verses, or puts back a verse's time (a restore, an undo). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<StudiedVerseEntity>)

    /** Adds the verses not studied yet; those that are keep the time they were. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<StudiedVerseEntity>): List<Long>

    @Query("DELETE FROM studied_verses WHERE book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun delete(book: String, chapter: Int, verse: Int)

    @Query("DELETE FROM studied_verses")
    suspend fun deleteAll()

    @Transaction
    suspend fun deleteEach(items: List<StudiedVerseEntity>) {
        for (item in items) delete(item.book, item.chapter, item.verse)
    }
}
