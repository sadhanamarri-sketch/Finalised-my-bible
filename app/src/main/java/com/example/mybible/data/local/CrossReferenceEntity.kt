package com.example.mybible.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One cross-reference from the Treasury of Scripture Knowledge dataset,
 * imported by [com.example.mybible.data.CrossReferenceImporter]. Real data,
 * replacing the 5-hardcoded-verses + 4-generic-fallback-refs placeholder.
 */
@Entity(
    tableName = "cross_references",
    primaryKeys = ["fromBook", "fromChapter", "fromVerse", "toBook", "toChapter", "toVerse"],
    indices = [Index(value = ["fromBook", "fromChapter", "fromVerse"])]
)
data class CrossReferenceEntity(
    val fromBook: String,
    val fromChapter: Int,
    val fromVerse: Int,
    val toBook: String,
    val toChapter: Int,
    val toVerse: Int,
    val toVerseEnd: Int,
    // How many openbible.info readers voted this link helpful: higher means a
    // stronger link. Cross References lists the strongest first. Rows imported
    // before this column existed read 0 until BibleDataInitializer re-imports
    // them (see CROSS_REFERENCE_DATA_VERSION there).
    @ColumnInfo(defaultValue = "0")
    val votes: Int = 0
)
