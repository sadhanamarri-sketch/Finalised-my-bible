package com.example.mybible.versescroll

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The shapes a verse card is shared in as a picture (backlog 53), each [SHORT_SIDE] pixels on its short
 * side: what WhatsApp, Instagram and Facebook keep of a picture.
 */
enum class ShareShape(val label: String, val use: String) {
    STORY("9:16", "Status"),
    SCREEN("Phone", "Wallpaper"),
    POST("4:5", "Post"),
    SQUARE("1:1", "Square"),
    WIDE("16:9", "Wide");

    /** Its size; for [SCREEN], the shape of the phone's screen ([screenWidth] by [screenHeight], either way round). */
    fun size(screenWidth: Int, screenHeight: Int): PictureSize = when (this) {
        STORY -> PictureSize(SHORT_SIDE, SHORT_SIDE * 16 / 9)
        SCREEN -> {
            val short = min(screenWidth, screenHeight)
            val long = max(screenWidth, screenHeight)
            // A screen not known, or squarer than 9:16 (a folding phone open): 9:16.
            val tall = if (short <= 0 || long * 9L < short * 16L) SHORT_SIDE * 16 / 9 else (SHORT_SIDE.toLong() * long / short).toInt()
            PictureSize(SHORT_SIDE, tall - tall % 2)
        }
        POST -> PictureSize(SHORT_SIDE, SHORT_SIDE * 5 / 4)
        SQUARE -> PictureSize(SHORT_SIDE, SHORT_SIDE)
        WIDE -> PictureSize(SHORT_SIDE * 16 / 9, SHORT_SIDE)
    }

    companion object {
        const val SHORT_SIDE = 1080

        /** The shape saved as [name]; a name it doesn't know (or none), 9:16. */
        fun named(name: String?): ShareShape = entries.firstOrNull { it.name == name } ?: STORY
    }
}

data class PictureSize(val width: Int, val height: Int)

/** A rectangle of pixels, its edges as Android's Rect has them. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Where things go on a shared picture: what of the photo shows, where the words go, and their size. */
object SharePictureLayout {
    /** How far down the photo's spare height the part shown sits, as on the cards (-1 the top, 1 the bottom). */
    const val PHOTO_BIAS = 0.2f

    /**
     * The part of a [sourceWidth] by [sourceHeight] photo that fills a [width] by [height] frame without
     * stretching: as much as fits, centred across, and [bias] of the way down what's left over.
     */
    fun crop(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, bias: Float = PHOTO_BIAS): PixelRect =
        if (sourceWidth.toLong() * height > sourceHeight.toLong() * width) {
            // Wider than the frame: all its height, a slice across the middle.
            val w = (sourceHeight.toLong() * width / height).toInt()
            val left = (sourceWidth - w) / 2
            PixelRect(left, 0, left + w, sourceHeight)
        } else {
            val h = (sourceWidth.toLong() * height / width).toInt()
            val top = ((sourceHeight - h) * (1 + bias) / 2).roundToInt()
            PixelRect(0, top, sourceWidth, top + h)
        }

    /** The width of the photo beside a [WIDE][ShareShape.WIDE] picture's words: as tall as the picture, 9:16. */
    fun panelWidth(size: PictureSize): Int = (size.height * 9 / 16).let { it - it % 2 }

    /**
     * Where the words go on a [size] picture of [shape]: clear of the edges, and of what other apps put
     * over a picture: a story's name and buttons, a lock screen's clock. A wide picture's are beside its
     * photo, or across it when there's no [photo].
     */
    fun textFrame(shape: ShareShape, size: PictureSize, photo: Boolean = true): PixelRect {
        val margin = (min(size.width, size.height) * 0.08f).roundToInt()
        return when (shape) {
            ShareShape.WIDE -> PixelRect((if (photo) panelWidth(size) else 0) + margin, margin, size.width - margin, size.height - margin)
            ShareShape.STORY -> PixelRect(margin, (size.height * 0.13f).roundToInt(), size.width - margin, (size.height * 0.82f).roundToInt())
            ShareShape.SCREEN -> PixelRect(margin, (size.height * 0.30f).roundToInt(), size.width - margin, (size.height * 0.86f).roundToInt())
            ShareShape.POST, ShareShape.SQUARE -> PixelRect(margin, margin, size.width - margin, size.height - margin)
        }
    }

    /**
     * The largest size from [largest] down to [smallest], in steps of [step], at which [fits] holds; null when
     * it doesn't at [smallest] either. [fits] must hold at every size below one it holds at.
     */
    fun fit(largest: Float, smallest: Float, step: Float = 0.5f, fits: (Float) -> Boolean): Float? {
        if (!fits(smallest)) return null
        var low = 0
        var high = ((largest - smallest) / step).roundToInt()
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fits(smallest + mid * step)) low = mid else high = mid - 1
        }
        return smallest + low * step
    }

    /** A file name for [label]'s picture in [shape]: "John-3-16-9x16.jpg". */
    fun fileName(label: String, shape: ShareShape): String {
        val name = label.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        return "$name-${shape.label.replace(':', 'x')}.jpg"
    }
}
