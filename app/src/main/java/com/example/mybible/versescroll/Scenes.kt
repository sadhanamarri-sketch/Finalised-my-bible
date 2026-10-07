package com.example.mybible.versescroll

import kotlinx.serialization.Serializable

/**
 * One of Verse Scroll's background photos (assets/verse_scroll/scenes/<id>.webp, free photos from
 * Unsplash listed in tools/verse_scroll/photos.tsv), as listed in assets/verse_scroll/scenes.json.
 * Each one's dimming was measured: [kd] is how much it's darkened under the text in the dark themes,
 * [kl] how much it's lightened in the light ones, so the verse always stands out by at least 4.5:1
 * and the reference by 3:1. [kind] is what it shows (sea, night, wheat…), for matching to a verse.
 */
@Serializable
data class SceneSpec(
    val id: String,
    val kd: Float,
    val kl: Float,
    val kind: String = ""
) {
    /** The kind words are matched against, and that never shows twice in a row. */
    val type: String get() = id.substringBefore('-')
}

@Serializable
data class SceneCatalog(val scenes: List<SceneSpec>)

/** 32-bit FNV-1a over the string's UTF-16 units, the same hash the design preview used. */
fun fnv1a(s: String): Int {
    var h = 2166136261L.toInt()
    for (ch in s) {
        h = h xor ch.code
        h *= 16777619
    }
    return h
}

/** Small seeded random number generator (mulberry32), so a verse's scene always looks the same. */
class SeededRandom(seed: Int) {
    private var a = seed

    fun next(): Float {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toFloat() / 4294967296f
    }
}

object SceneMatcher {
    // A verse's own words pick the kind of scene when they can; the first match wins.
    private val WORDS: List<Pair<Set<String>, Regex>> = listOf(
        setOf("night") to Regex("\\b(night|stars?|moon|darkness)\\b"),
        setOf("snow") to Regex("\\b(snow|wool|winter|frost|hail|cold)\\b"),
        setOf("wheat") to Regex("\\b(wheat|corn|grain|harvest|reap\\w*|sheaves|sow|sowed|soweth|seed|bread)\\b"),
        setOf("lake") to Regex("\\b(still|quiet|quietness|rest|peace|calm)\\b"),
        setOf("sea") to Regex("\\b(sea|seas|waters?|waves|floods?|rivers?|ships?)\\b"),
        setOf("hills") to Regex("\\b(shepherd|sheep|flocks?|pastures?|fields?|grass|lambs?|vines?)\\b"),
        setOf("desert") to Regex("\\b(wilderness|desert|dry|thirst|thirsty|sand|dust)\\b"),
        setOf("forest") to Regex("\\b(trees?|forest|cedars?|branch(es)?|roots?|leaf|leaves|fruit)\\b"),
        setOf("ridges") to Regex("\\b(mountains?|hills|rock|fortress|refuge|strength)\\b"),
        setOf("light") to Regex("\\b(light|sun|morning|shine|shineth|bright)\\b")
    )

    /**
     * The scene for a verse: one matching its words when they name something a scene shows, otherwise
     * any, never the same kind as the card before. [key] makes the choice stable for the same verse.
     */
    fun pick(scenes: List<SceneSpec>, text: String, key: String, previousType: String?): SceneSpec {
        val lower = text.lowercase()
        var pool = scenes
        for ((types, re) in WORDS) {
            if (re.containsMatchIn(lower)) {
                pool = scenes.filter { it.type in types }
                break
            }
        }
        pool = pool.filter { it.type != previousType }
        if (pool.isEmpty()) pool = scenes.filter { it.type != previousType }
        if (pool.isEmpty()) pool = scenes
        val h = fnv1a(key).toLong() and 0xffffffffL
        return pool[(h % pool.size).toInt()]
    }
}

/**
 * The slow drift and zoom of a scene ("breathing"): from 102% to 112% and back while drifting by
 * ([dx], [dy]) of the card, about the point ([originX], [originY]). Different for every verse.
 */
data class Breathe(
    val dx: Float,
    val dy: Float,
    val originX: Float,
    val originY: Float,
    val period: Float,
    val delay: Float
) {
    companion object {
        fun forKey(key: String): Breathe {
            val r = SeededRandom(fnv1a(key) xor 0x9e3779b9L.toInt())
            return Breathe(
                dx = (r.next() - 0.5f) * 0.048f,
                dy = (r.next() - 0.5f) * 0.04f,
                originX = 0.40f + r.next() * 0.2f,
                originY = 0.45f + r.next() * 0.2f,
                period = 26f + r.next() * 8f,
                delay = -r.next() * 20f
            )
        }
    }
}
