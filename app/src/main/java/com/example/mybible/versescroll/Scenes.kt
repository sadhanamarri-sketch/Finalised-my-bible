package com.example.mybible.versescroll

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One of Verse Scroll's painted backgrounds (assets/verse_scroll/scenes/<id>.webp), as listed in
 * assets/verse_scroll/scenes.json. The paintings were drawn with code, and each one's dimming was
 * measured: [kd] is how much it's darkened under the text in the dark themes, [kl] how much it's
 * lightened in the light ones, so the verse always stands out by at least 4.5:1 and the context lines
 * and reference by 3:1. The other fields say where the scene's small moving touches go, as fractions
 * of the picture: the sun and horizon, mist bands, the moon, a light source, color glows, wind over wheat.
 */
@Serializable
data class SceneSpec(
    val id: String,
    val kd: Float,
    val kl: Float,
    val kind: String = "",
    val sun: List<Float>? = null,
    val horizon: Float? = null,
    val haze: String? = null,
    val mist: List<Float>? = null,
    val glint: String? = null,
    val moon: List<Float>? = null,
    val source: List<Float>? = null,
    val blobs: List<String>? = null,
    val snow: Boolean = false,
    val wind: List<Float>? = null,
    val sheen: String? = null
) {
    /** The kind words are matched against, and that never shows twice in a row ("night" and "moon" differ). */
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
        setOf("night", "moon") to Regex("\\b(night|stars?|moon|darkness)\\b"),
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
 * The small moving touches laid over a painting. Positions and sizes are fractions of the card (sizes
 * marked Dp are in dp); periods and delays are in seconds. A negative delay starts the effect partway
 * through, so a scene never opens with everything in step.
 */
sealed interface SceneFx {
    /** A star that brightens and fades. */
    data class Twinkle(val x: Float, val y: Float, val period: Float, val delay: Float) : SceneFx

    /** Light glittering on the sun's path across the sea. */
    data class Glint(val x: Float, val y: Float, val width: Float, val color: Int, val period: Float, val delay: Float) : SceneFx

    /** Rays from a light source, slowly swinging and pulsing. */
    data class Rays(val x: Float, val y: Float, val color: Int) : SceneFx

    /** A wandering glow of color. */
    data class Blob(val x: Float, val y: Float, val color: Int, val period: Float, val delay: Float) : SceneFx

    /** A falling snowflake. */
    data class Flake(val x: Float, val sizeDp: Float, val swayDp: Float, val period: Float, val delay: Float) : SceneFx

    /** A line of light on still water. */
    data class Ripple(val x: Float, val y: Float, val width: Float, val color: Int, val period: Float, val delay: Float) : SceneFx

    /** A brighter band passing over a field, like wind through wheat. */
    data class Breeze(val y: Float, val height: Float, val color: Int, val period: Float, val delay: Float) : SceneFx

    /** A band of mist drifting sideways. */
    data class Mist(
        val y: Float,
        val color: Int,
        val opacity: Float,
        val period: Float,
        val delay: Float,
        val reverse: Boolean
    ) : SceneFx
}

/** "#rrggbb" with an alpha, as a packed ARGB color. */
fun hexColor(hex: String, alpha: Float = 1f): Int {
    val rgb = hex.removePrefix("#").toLong(16).toInt() and 0xffffff
    return ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or rgb
}

/** The moving touches for one scene. [seed] keeps them the same each time the same verse is shown. */
fun sceneEffects(s: SceneSpec, seed: Int): List<SceneFx> {
    val r = SeededRandom(seed)
    val out = mutableListOf<SceneFx>()
    when {
        s.kind == "night" || s.kind == "moon" -> {
            val moon = s.moon
            repeat(26) {
                val x = r.next()
                val y = r.next() * 0.62f
                // Keeps stars off the moon; its radius is a fraction of the width, hence the aspect correction.
                if (moon != null && hypot(x - moon[0], (y - moon[1]) / 0.46f) < moon[2]) return@repeat
                out += SceneFx.Twinkle(x, y, period = 2.6f + r.next() * 3f, delay = -r.next() * 5f)
            }
        }
        s.kind == "sea" -> {
            val horizon = s.horizon ?: 0.5f
            val sunX = s.sun?.get(0) ?: 0.5f
            val color = hexColor(s.glint ?: "#ffffff", 0.85f)
            repeat(12) {
                val y = horizon + 0.008f + r.next().pow(1.6f) * (0.9f - horizon)
                val w = 0.05f + (y - horizon) * 0.55f
                val x = sunX - w / 2 + (r.next() - 0.5f) * w * 0.4f
                out += SceneFx.Glint(x, y, w, color, period = 2.2f + r.next() * 1.8f, delay = -r.next() * 4f)
            }
        }
        s.kind == "light" -> {
            val src = s.source ?: listOf(0.5f, 0.2f)
            out += SceneFx.Rays(src[0], src[1], hexColor(s.glint ?: "#ffffff", 0.2f))
        }
        s.kind == "abstract" -> {
            s.blobs.orEmpty().forEach { c ->
                out += SceneFx.Blob(
                    x = r.next() * 0.6f - 0.1f,
                    y = 0.05f + r.next() * 0.55f,
                    color = hexColor(c, 0.55f),
                    period = 22f + r.next() * 12f,
                    delay = -r.next() * 20f
                )
            }
        }
        s.snow -> repeat(34) {
            out += SceneFx.Flake(
                x = r.next(),
                sizeDp = 1.6f + r.next() * 2.6f,
                swayDp = (r.next() - 0.5f) * 40f,
                period = 9f + r.next() * 8f,
                delay = -r.next() * 17f
            )
        }
        s.kind == "lake" -> {
            val horizon = s.horizon ?: 0.5f
            val color = hexColor(s.glint ?: "#ffffff", 0.7f)
            repeat(11) {
                val y = horizon + 0.01f + r.next().pow(1.3f) * (0.93f - horizon)
                val w = 0.08f + r.next() * 0.22f
                out += SceneFx.Ripple(r.next() * (1 - w), y, w, color, period = 3.5f + r.next() * 3f, delay = -r.next() * 6f)
            }
        }
        s.wind != null -> {
            val (top, bottom) = s.wind
            val color = hexColor(s.sheen ?: "#ffffff", 0.45f)
            repeat(2) { n ->
                out += SceneFx.Breeze(
                    y = top + n * (bottom - top) * 0.45f,
                    height = (bottom - top) * 0.55f,
                    color = color,
                    period = 10f + r.next() * 6f,
                    delay = -r.next() * 14f
                )
            }
        }
        s.mist != null -> s.mist.forEachIndexed { n, y ->
            out += SceneFx.Mist(
                y = y - 0.07f,
                color = hexColor(s.haze ?: "#ffffff", 0.55f),
                opacity = if (n > 0) 0.3f else 0.4f,
                period = 38f + n * 14f + r.next() * 8f,
                delay = -r.next() * 30f,
                reverse = n > 0
            )
        }
    }
    return out
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
