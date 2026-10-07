package com.example.mybible.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VerseEntity::class,
        GreekWordEntity::class,
        HebrewWordEntity::class,
        CrossReferenceEntity::class,
        LexiconEntity::class,
        WebsterEntity::class
    ],
    // v7: added unique indices on greek_words/hebrew_words(book, chapter,
    // verse, orderIndex) so REPLACE-on-conflict retries of Greek/Hebrew
    // interlinear imports overwrite instead of duplicating rows — see
    // those entities' indices comments.
    // v8: lexicon_entries' primary key changed from strongs (bare eStrong)
    // to strongsDisambiguated (dStrong) — see LexiconEntity's class doc.
    // Destructive migration (below) just wipes and re-downloads, same as
    // every prior version bump.
    // v9: cross_references gained `votes` (link strength). A real migration
    // (MIGRATION_8_9), so updating doesn't re-download the whole Bible; the
    // rows get their votes from a one-time cross-reference re-import (see
    // BibleDataInitializer's CROSS_REFERENCE_DATA_VERSION).
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cross_references ADD COLUMN votes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_bible.db"
                )
                    .addMigrations(MIGRATION_8_9)
                    // Versions older than 8 have no migration path, so they
                    // just re-import from scratch (cheap: Telugu is a local
                    // asset read, KJV/Greek/Hebrew/xrefs re-download once).
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
