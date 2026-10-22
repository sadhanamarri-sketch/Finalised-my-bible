package com.example.mybible.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * What the reader makes, as opposed to the Bible text in [AppDatabase]: the verses marked studied,
 * and the notes. Its own file, because AppDatabase is wiped and downloaded again when its version
 * moves on without a migration; this one never is. A new version here needs a real migration.
 */
@Database(entities = [StudiedVerseEntity::class, NoteEntity::class], version = 2, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun studiedVerseDao(): StudiedVerseDao

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        // Version 2: the notes, until then one JSON list in the app's preferences.
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        fun getInstance(context: Context): UserDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "my_bible_user.db")
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
