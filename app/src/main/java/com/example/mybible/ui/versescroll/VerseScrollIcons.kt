package com.example.mybible.ui.versescroll

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Verse Scroll's line icons, drawn on a 24-unit grid with round ends — the same drawings as the design
 * preview, so the bottom bar and color box read the way they were reviewed.
 */
internal object VerseScrollIcons {
    private fun lineIcon(name: String, strokeWidth: Float, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    // A circle as two arcs, since path data has no circle command.
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Highlight = lineIcon(
        "Highlight", 1.8f,
        "M9.5 15.5L6 19H3.5v-2.5L7 13", "M14.5 4.5l5 5-8 8-5-5z", "M13 20.5h8"
    )
    val Note = lineIcon(
        "Note", 1.8f,
        "M6 3.5h12A1.5 1.5 0 0 1 19.5 5v10.5l-5 5H6A1.5 1.5 0 0 1 4.5 19V5A1.5 1.5 0 0 1 6 3.5z",
        "M14.5 20.5V17a1 1 0 0 1 1-1h4", "M8 8.5h8M8 12h5"
    )
    val Read = lineIcon(
        "Read", 1.8f,
        "M12 6.5C10 5 7 4.5 3.5 5v13c3.5-.5 6.5 0 8.5 1.5 2-1.5 5-2 8.5-1.5V5C17 4.5 14 5 12 6.5z", "M12 6.5v13"
    )
    val Links = lineIcon(
        "Links", 1.8f,
        circle(6f, 6.5f, 2.3f), circle(18f, 12f, 2.3f), circle(6f, 17.5f, 2.3f), "M8.2 7.5l7.6 3.4M8.2 16.5l7.6-3.4"
    )
    val Spark = lineIcon(
        "Spark", 1.8f,
        "M12 4v4M12 16v4M4 12h4M16 12h4M6.5 6.5l2.5 2.5M15 15l2.5 2.5M17.5 6.5L15 9M9 15l-2.5 2.5"
    )
    val RemoveHighlight = lineIcon(
        "RemoveHighlight", 1.8f,
        "M8.6 7.4C7.3 9.2 6.5 11 6.5 12.5a5.5 5.5 0 0 0 9.9 3.3",
        "M17.4 13.4c.1-.3.1-.6.1-.9 0-3.4-5.5-9-5.5-9s-1.1 1.1-2.3 2.6", "M4 4l16 16"
    )
    val Back = lineIcon("Back", 2f, "M15 5l-7 7 7 7")
    val Settings = lineIcon(
        "Settings", 2f,
        "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f)
    )
    val Return = lineIcon("Return", 2f, "M9 14l-5-5 5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Close = lineIcon("Close", 2f, "M6 6l12 12M18 6L6 18")
}
