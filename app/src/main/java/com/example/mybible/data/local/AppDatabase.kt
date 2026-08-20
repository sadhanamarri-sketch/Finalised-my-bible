package com.example.mybible.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_bible.db"
                )
                    // No release has shipped with a real schema-migration path
                    // yet, so a version bump just re-imports from scratch
                    // (cheap: Telugu is a local asset read, KJV/Greek/Hebrew/xrefs
                    // re-download once) rather than needing a Migration.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
