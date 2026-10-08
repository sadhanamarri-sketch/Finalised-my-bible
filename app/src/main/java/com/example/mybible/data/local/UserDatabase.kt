package com.example.mybible.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * What the reader makes, as opposed to the Bible text in [AppDatabase]: for now, the verses marked
 * studied. Its own file, because AppDatabase is wiped and downloaded again when its version moves
 * on without a migration; this one never is. A new version here needs a real migration.
 */
@Database(entities = [StudiedVerseEntity::class], version = 1, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun studiedVerseDao(): StudiedVerseDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "my_bible_user.db")
                    .build().also { INSTANCE = it }
            }
    }
}
