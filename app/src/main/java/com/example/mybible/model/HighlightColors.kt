package com.example.mybible.model

/**
 * Capacitor lets people pick *any* color via the browser's native
 * `<input type=color>`. There's no equivalent control in Compose, and
 * building a full HSV picker was more than this feature needs, so instead
 * people choose from this curated palette when adding or recoloring a
 * highlight — a deliberate, discussed tradeoff (see conversation), not an
 * oversight. Two shades of each core hue plus a neutral, covering warm and
 * cool ranges without becoming an overwhelming grid.
 */
val PRESET_HIGHLIGHT_PALETTE: List<String> = listOf(
    "#FFF1A8", // Soft Yellow
    "#F2C94C", // Gold
    "#C8E6C9", // Soft Green
    "#7DBE7D", // Green
    "#BBDEFB", // Soft Blue
    "#7FB2E0", // Blue
    "#F8BBD0", // Soft Pink
    "#E38FA8", // Rose
    "#E1BEE7", // Lavender
    "#CE93D8", // Purple
    "#FFCC80", // Orange
    "#FFAB91", // Coral
    "#80CBC4", // Teal
    "#B0BEC5"  // Gray
)

/**
 * Seeded on first launch (and merged in for anyone upgrading from the old
 * fixed 5-swatch picker). Covers two sources so nothing already-highlighted
 * loses its color or ends up unlabeled after upgrading:
 *  - Capacitor's own 4 defaults (same hexes, same labels — "Key Verse"
 *    etc.), for parity with the app you're used to.
 *  - The 5 hexes the old hardcoded Kotlin swatch picker used, given plain
 *    color-name labels, since verses already highlighted with those exact
 *    hexes need a matching def to show a label instead of "Uncategorized".
 */
val DEFAULT_HIGHLIGHT_COLOR_DEFS: List<HighlightColorDef> = listOf(
    HighlightColorDef("Key Verse", "#F2C94C"),
    HighlightColorDef("Promise", "#7DBE7D"),
    HighlightColorDef("Prayer", "#7FB2E0"),
    HighlightColorDef("Important", "#E38FA8"),
    HighlightColorDef("Yellow", "#FFF1A8"),
    HighlightColorDef("Green", "#C8E6C9"),
    HighlightColorDef("Blue", "#BBDEFB"),
    HighlightColorDef("Pink", "#F8BBD0"),
    HighlightColorDef("Purple", "#E1BEE7")
)
