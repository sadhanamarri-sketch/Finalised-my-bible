package com.example.mybible.data

import com.example.mybible.model.WordLink

/**
 * Links you make between a Greek word and a Hebrew one (agapē and chesed), beside the Septuagint's
 * built-in ones. Each is a [WordLink] by the two words' Strong's numbers. These are the rules for
 * adding, removing and merging them, kept apart from where they're stored so they can be tested.
 */

private val strongsNumber = Regex("([GgHh])0*(\\d{1,5})[A-Za-z]?")

/**
 * A word's Strong's number as links keep it, its letter and four digits without a sense: G26,
 * g0026 and G0026 are G0026; H7225G is H7225. Null when [strongs] isn't one.
 */
fun linkNumber(strongs: String): String? {
    val match = strongsNumber.matchEntire(strongs.trim()) ?: return null
    val digits = match.groupValues[2]
    if (digits.length > 4) return null
    return match.groupValues[1].uppercase() + digits.padStart(4, '0')
}

private fun WordLink.links(greek: String, hebrew: String) = this.greek == greek && this.hebrew == hebrew

/** [greek] (G0026) linked to [hebrew] (H2617), after the other links; unchanged when they already are. */
fun List<WordLink>.withLink(greek: String, hebrew: String, now: Long = System.currentTimeMillis()): List<WordLink> =
    if (any { it.links(greek, hebrew) }) this else this + WordLink(greek, hebrew, now)

fun List<WordLink>.withoutLink(greek: String, hebrew: String): List<WordLink> = filterNot { it.links(greek, hebrew) }

/** Each linked word's number with the numbers it's linked to, both ways: G0026 → H2617, and H2617 → G0026. */
fun List<WordLink>.byNumber(): Map<String, List<String>> {
    val out = HashMap<String, MutableList<String>>()
    for (link in this) {
        out.getOrPut(link.greek) { ArrayList(1) } += link.hebrew
        out.getOrPut(link.hebrew) { ArrayList(1) } += link.greek
    }
    return out
}

/** The record that a link was removed, for merging backups. */
fun wordLinkTombstoneKey(greek: String, hebrew: String) = "wordlink:$greek:$hebrew"

/**
 * A backup's links merged into [existing]: a link removed since it was made ([tombstones]) stays
 * removed, and one made again after it was removed comes back. Returns the merged list and how many
 * links the backup added.
 */
fun mergeWordLinks(existing: List<WordLink>, incoming: List<WordLink>, tombstones: Map<String, Long>): Pair<List<WordLink>, Int> {
    val merged = existing.toMutableList()
    var added = 0
    for (link in incoming) {
        val greek = linkNumber(link.greek)?.takeIf { it.startsWith("G") } ?: continue
        val hebrew = linkNumber(link.hebrew)?.takeIf { it.startsWith("H") } ?: continue
        val deletedAt = tombstones[wordLinkTombstoneKey(greek, hebrew)]
        if (deletedAt != null && link.createdAt <= deletedAt) continue
        if (merged.any { it.links(greek, hebrew) }) continue
        merged += link.copy(greek = greek, hebrew = hebrew)
        added++
    }
    return merged to added
}
