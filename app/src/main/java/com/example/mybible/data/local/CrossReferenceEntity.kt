package com.example.mybible.data.local

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
    val toVerseEnd: Int
)
