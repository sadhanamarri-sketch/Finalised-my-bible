package com.example.mybible.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Verses the Telugu Bible prints together, as one (backlog 56): Genesis 1:17-18, because Telugu puts the
 * main verb last and its verse can't end where the English one does. [first] to [last], and their Telugu
 * whole. With "Verses printed together" on (the default), the app shows such a group's English verses one
 * after another and then its Telugu once, and a group acts as one (highlight, studied, notes, reading).
 */
data class PrintedTogether(val first: Int, val last: Int, val telugu: String) {
    operator fun contains(verse: Int): Boolean = verse in first..last

    /** "17–18". */
    val label: String get() = "$first–$last"

    /** The group's verses, of [verses] (a chapter's, in order): those numbered [first] to [last]. */
    fun <T> of(verses: List<T>, number: (T) -> Int): List<T> = verses.filter { number(it) in this }
}

object PrintedTogetherData {
    /**
     * assets/telugu_together.json, made by tools/telugu/apply_bsi_text.py: {"Genesis": {"1": [[17, 18,
     * "text"]]}}. By book, then chapter, each chapter's groups in order.
     */
    fun parse(json: String): Map<String, Map<Int, List<PrintedTogether>>> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, chapters) ->
            chapters.jsonObject.entries.associate { (chapter, groups) ->
                chapter.toInt() to groups.jsonArray.map { g ->
                    val (first, last, text) = g.jsonArray
                    PrintedTogether(first.jsonPrimitive.int, last.jsonPrimitive.int, text.jsonPrimitive.content)
                }
            }
        }

    /** Each verse of [groups] (a chapter's), with its group. */
    fun byVerse(groups: List<PrintedTogether>): Map<Int, PrintedTogether> =
        buildMap { for (g in groups) for (v in g.first..g.last) put(v, g) }
}
