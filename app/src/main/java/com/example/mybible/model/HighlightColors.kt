package com.example.mybible.model

/**
 * The full, fixed set of highlight colors — no add/rename/disable/manage
 * flow anymore (that whole "Manage Highlight Colors" feature was removed
 * along with the verse-action-toolbar revamp). Twelve pastel hues stepped
 * evenly around the wheel (30° apart) at the same saturation/lightness, so
 * every color is distinct at a glance and none reads as a near-duplicate of
 * another.
 */
val HIGHLIGHT_COLOR_DEFS: List<HighlightColorDef> = listOf(
    HighlightColorDef("Rose", "#F1B1B1"),
    HighlightColorDef("Peach", "#F1D1B1"),
    HighlightColorDef("Sunshine", "#F1F1B1"),
    HighlightColorDef("Lime", "#D1F1B1"),
    HighlightColorDef("Mint", "#B1F1B1"),
    HighlightColorDef("Seafoam", "#B1F1D1"),
    HighlightColorDef("Sky", "#B1F1F1"),
    HighlightColorDef("Azure", "#B1D1F1"),
    HighlightColorDef("Periwinkle", "#B1B1F1"),
    HighlightColorDef("Lavender", "#D1B1F1"),
    HighlightColorDef("Orchid", "#F1B1F1"),
    HighlightColorDef("Blush", "#F1B1D1")
)
