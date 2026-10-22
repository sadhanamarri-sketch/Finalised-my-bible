package com.example.mybible.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A note, one row each: saving one writes that row alone (the old store rewrote every note on
 * each save), and the app and the Drive sync see the same notes at once, so neither can undo the
 * other's save or bring back a note just deleted. The note is kept whole as JSON ([data], a
 * NoteItem), as it always was; [createdAt] and [updatedAt] are copies of its own, for order.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val data: String
)

@Dao
interface NoteDao {
    /** Every note, newest first, again each time one is saved or deleted. */
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun find(id: Long): NoteEntity?

    /** Saves the notes, a note of the same id replaced. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<NoteEntity>)

    /** Adds the notes not here yet; those that are stay as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<NoteEntity>): List<Long>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}
