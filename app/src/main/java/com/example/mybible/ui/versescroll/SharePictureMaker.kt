package com.example.mybible.ui.versescroll

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.mybible.R
import com.example.mybible.versescroll.LONG_VERSE_WORDS
import com.example.mybible.versescroll.PictureSize
import com.example.mybible.versescroll.PixelRect
import com.example.mybible.versescroll.ShareShape
import com.example.mybible.versescroll.SharePictureLayout
import com.example.mybible.versescroll.VerseLine
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a shared picture says: a verse card's words, as the card shows them. */
internal data class SharedVerse(
    val lines: List<VerseLine>,
    /** The part of the Bible it's from, over the verse: "The Gospels". */
    val eyebrow: String,
    /** "John 3:16". */
    val label: String,
    /** The Telugu under the verse, when the card shows it; else empty. */
    val telugu: String,
    /** "యోహాను 3:16", beside [label] when the Telugu shows. */
    val teluguLabel: String?,
    /** The Telugu's size on the card, in sp. */
    val teluguSize: Int
) {
    val long: Boolean get() = lines.sumOf { line -> line.text.split(' ').count { it.isNotBlank() } } > LONG_VERSE_WORDS
}

/** The card's colors as Android colors: its words over a photo, and the page when there's no photo. */
internal data class SharePalette(val dark: Boolean, val ink: Int, val soft: Int, val gold: Int, val page: Int)

/**
 * Draws a verse card as a picture to share (backlog 53): the card's photo filling the picture, dimmed as
 * the card dims it, and its words in the card's type and colors, as large as the card's (smaller, for a
 * verse that wouldn't fit). A wide picture has the photo as a panel at its left, the words beside it over
 * the same photo blurred.
 */
internal class SharePictureMaker(context: Context) {
    private val serif = ResourcesCompat.getFont(context, R.font.gelasio_regular) ?: Typeface.SERIF
    private val sans = ResourcesCompat.getFont(context, R.font.work_sans_variable) ?: Typeface.SANS_SERIF

    /**
     * [verse] as a [shape] picture of [size]: over [photo], with the scene's own dimming ([veil], 0 to 1),
     * or on the plain page when there's no photo.
     */
    fun draw(verse: SharedVerse, shape: ShareShape, size: PictureSize, photo: Bitmap?, veil: Float, palette: SharePalette): Bitmap {
        val picture = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        val onPhoto = photo != null
        if (photo == null) canvas.drawColor(palette.page) else drawPhoto(canvas, shape, size, photo, veil, palette.dark, verse.long)

        val frame = SharePictureLayout.textFrame(shape, size, onPhoto)
        // The picture is a card on a phone this many dp wide.
        val unit = min(size.width, size.height) / CARD_WIDTH_DP
        val words = Words(verse, palette, unit, frame.width, onPhoto)
        val block = SharePictureLayout.fit(MAX_SCALE, MIN_SCALE, SCALE_STEP) { words.at(it).height <= frame.height }
            ?.let(words::at)
            ?: words.cut(frame.height)
        val top = frame.top + (frame.height - block.height) / 2f
        if (onPhoto && verse.long) {
            drawPanel(canvas, RectF(frame.left.toFloat(), top, frame.left + block.width, top + block.height), unit, palette.dark)
        }
        canvas.save()
        canvas.translate(frame.left.toFloat(), top)
        block.draw(canvas)
        canvas.restore()
        return picture
    }

    private fun drawPhoto(canvas: Canvas, shape: ShareShape, size: PictureSize, photo: Bitmap, veil: Float, dark: Boolean, long: Boolean) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val whole = Rect(0, 0, size.width, size.height)
        val veilColor = (if (dark) VeilDark else VeilLight).toArgb()
        if (shape == ShareShape.WIDE) {
            // Behind the words, the photo blurred and dimmed; beside them, the photo itself.
            canvas.drawBitmap(blurred(photo, size), null, whole, paint)
            canvas.drawColor(withAlpha(veilColor, if (dark) WIDE_DIM_DARK else WIDE_DIM_LIGHT))
            val panel = SharePictureLayout.panelWidth(size)
            val crop = SharePictureLayout.crop(photo.width, photo.height, panel, size.height)
            canvas.drawBitmap(photo, crop.toRect(), Rect(0, 0, panel, size.height), paint)
            return
        }
        val crop = SharePictureLayout.crop(photo.width, photo.height, size.width, size.height)
        canvas.drawBitmap(photo, crop.toRect(), whole, paint)
        // Darker (or lighter) toward the top and bottom edges, then the scene's own dimming, as on the card.
        val edges = if (dark) floatArrayOf(0.30f, 0.06f, 0.06f, 0.40f) else floatArrayOf(0.40f, 0.10f, 0.10f, 0.55f)
        val gradient = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.height.toFloat(),
                edges.map { withAlpha(veilColor, it) }.toIntArray(),
                floatArrayOf(0f, 0.2f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), gradient)
        canvas.drawColor(withAlpha(veilColor, (veil + if (long) LONG_EXTRA else 0f).coerceIn(0f, 1f)))
    }

    // The part of the photo a [size] picture would show, blurred: halved in size a few times, then
    // doubled back, smoothing as it goes.
    private fun blurred(photo: Bitmap, size: PictureSize): Bitmap {
        val crop = SharePictureLayout.crop(photo.width, photo.height, size.width, size.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var width = crop.width
        var height = crop.height
        var image = Bitmap.createBitmap(photo, crop.left, crop.top, crop.width, crop.height)
        while (width > BLUR_WIDTH) {
            width = max(1, width / 2)
            height = max(1, height / 2)
            image = resized(image, width, height, paint)
        }
        while (width * 2 <= size.width) {
            width *= 2
            height *= 2
            image = resized(image, width, height, paint)
        }
        return image
    }

    private fun resized(image: Bitmap, width: Int, height: Int, paint: Paint): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(image, null, Rect(0, 0, width, height), paint)
        return out
    }

    // The soft panel the cards put behind a long verse over a photo, its edges fading out.
    private fun drawPanel(canvas: Canvas, words: RectF, unit: Float, dark: Boolean) {
        val color = (if (dark) VeilDark else VeilLight).toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(color, if (dark) 0.42f else 0.55f)
            maskFilter = BlurMaskFilter(22 * unit, BlurMaskFilter.Blur.NORMAL)
        }
        val inset = 30 * unit
        canvas.drawRoundRect(
            RectF(words.left - inset, words.top - inset, words.right + inset, words.bottom + inset),
            26 * unit, 26 * unit, paint
        )
    }

    /** The words of [verse], laid out in [width] pixels, at any scale of the card's sizes. */
    private inner class Words(
        private val verse: SharedVerse,
        private val palette: SharePalette,
        private val unit: Float,
        private val width: Int,
        private val onPhoto: Boolean
    ) {
        private val gold = palette.gold

        /** Everything at [scale] of the card's sizes, the verse and its Telugu whole. */
        fun at(scale: Float): Block = block(scale, Int.MAX_VALUE, Int.MAX_VALUE)

        /**
         * At the smallest scale, with as many of the verse's lines (and the Telugu's) as fit in [height], each
         * cut short with "…": for a verse too long for the picture even at its smallest.
         */
        fun cut(height: Int): Block {
            val whole = at(MIN_SCALE)
            val room = height - (whole.height - whole.textHeight)
            val verseLines = whole.verse.lineCount
            val teluguLines = whole.telugu?.lineCount ?: 0
            val verseShare = whole.verse.height.toFloat() / whole.textHeight
            val verseLine = whole.verse.height.toFloat() / verseLines
            val keepVerse = floor(room * verseShare / verseLine).toInt().coerceIn(min(2, verseLines), verseLines)
            val keepTelugu = whole.telugu?.let { telugu ->
                val teluguLine = telugu.height.toFloat() / teluguLines
                floor((room - keepVerse * verseLine) / teluguLine).toInt().coerceIn(min(2, teluguLines), teluguLines)
            } ?: Int.MAX_VALUE
            return block(MIN_SCALE, keepVerse, keepTelugu)
        }

        private fun block(scale: Float, verseLines: Int, teluguLines: Int): Block {
            val versePaint = paint(serif, VERSE_SP * unit * scale, palette.ink).apply {
                if (onPhoto) {
                    if (palette.dark) setShadowLayer(12 * unit, 0f, unit, withAlpha(0xFF000000.toInt(), 0.35f))
                    else setShadowLayer(10 * unit, 0f, unit, withAlpha(0xFFFFFFFF.toInt(), 0.6f))
                }
            }
            val verseLayout = layout(verseText(), versePaint, VERSE_LINE_HEIGHT, verseLines)
            val teluguLayout = verse.telugu.takeIf { it.isNotEmpty() }?.let {
                layout(it, paint(Typeface.DEFAULT, verse.teluguSize * unit * scale, palette.soft), TELUGU_LINE_HEIGHT, teluguLines)
            }
            // The small type above and below grows with the verse, but shrinks less.
            val small = unit * scale.coerceIn(MIN_LABEL_SCALE, MAX_LABEL_SCALE)
            val eyebrow = Line(verse.eyebrow.uppercase(), paint(sans, 11 * small, gold, weight = 600, spacing = 0.14f))
            val reference = Line(verse.label.uppercase(), paint(sans, 13.5f * small, gold, weight = 600, spacing = 0.12f))
            val teluguReference = verse.teluguLabel?.takeIf { teluguLayout != null }
                ?.let { Line(it, paint(Typeface.DEFAULT_BOLD, 15 * small, gold)) }
            return Block(eyebrow, verseLayout, teluguLayout, reference, teluguReference, STACK_GAP * small, width)
        }

        // The verse, or a passage's verses each on its own line, numbered small as the card numbers them.
        private fun verseText(): CharSequence {
            if (verse.lines.size == 1) return verse.lines.first().text
            val text = SpannableStringBuilder()
            verse.lines.forEachIndexed { i, line ->
                if (i > 0) text.append('\n')
                val start = text.length
                text.append("${line.number} ")
                listOf(RelativeSizeSpan(0.5f), SuperscriptSpan(), ForegroundColorSpan(palette.soft), FontSpan(sans, 600))
                    .forEach { text.setSpan(it, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                text.append(line.text)
            }
            return text
        }

        private fun layout(text: CharSequence, paint: TextPaint, lineHeight: Float, maxLines: Int): StaticLayout {
            val metrics = paint.fontMetrics
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineHeight * paint.textSize / (metrics.descent - metrics.ascent))
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(maxLines)
                .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
                .build()
        }
    }

    private fun paint(typeface: Typeface, size: Float, color: Int, weight: Int = 0, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
            letterSpacing = spacing
            if (weight > 0) fontVariationSettings = "'wght' $weight"
        }

    private companion object {
        // The width of the phone the card's sizes are taken at, in dp: a picture's short side is that wide.
        const val CARD_WIDTH_DP = 411f
        // The card's sizes, in dp (sp).
        const val VERSE_SP = 26f
        const val VERSE_LINE_HEIGHT = 1.4f
        const val TELUGU_LINE_HEIGHT = 1.65f
        const val STACK_GAP = 14f
        // How big the words get, as a share of the card's sizes: larger for a short verse with room, down to
        // half for a long one, before it's cut short. The small type above and below them, less so.
        const val MAX_SCALE = 1.6f
        const val MIN_SCALE = 0.5f
        const val SCALE_STEP = 0.02f
        const val MAX_LABEL_SCALE = 1.3f
        const val MIN_LABEL_SCALE = 0.85f
        // A long verse over a photo: dimmed a little more, besides the panel behind it.
        const val LONG_EXTRA = 0.08f
        // The blurred photo behind a wide picture's words: how small it's shrunk to, and dimmed.
        const val BLUR_WIDTH = 60
        const val WIDE_DIM_DARK = 0.55f
        const val WIDE_DIM_LIGHT = 0.62f
    }
}

/** One line of type: its text and how it's drawn. */
private class Line(val text: String, val paint: TextPaint) {
    val width: Float get() = paint.measureText(text)
    val ascent: Float get() = -paint.fontMetrics.ascent
    val descent: Float get() = paint.fontMetrics.descent
}

/**
 * A card's words laid out, top to bottom: the eyebrow, the verse, its Telugu, and the reference, with
 * the Telugu reference beside the English one (or under it, when the two don't fit on a line).
 */
private class Block(
    private val eyebrow: Line,
    val verse: StaticLayout,
    val telugu: StaticLayout?,
    private val reference: Line,
    private val teluguReference: Line?,
    private val gap: Float,
    private val maxWidth: Int
) {
    private val referenceGap = gap * 0.8f
    private val oneRow = teluguReference == null || reference.width + referenceGap + teluguReference.width <= maxWidth
    private val referenceHeight: Float =
        if (teluguReference == null) reference.ascent + reference.descent
        else if (oneRow) max(reference.ascent, teluguReference.ascent) + max(reference.descent, teluguReference.descent)
        else reference.ascent + reference.descent + gap * 0.4f + teluguReference.ascent + teluguReference.descent

    /** The verse's and the Telugu's height together. */
    val textHeight: Int get() = verse.height + (telugu?.height ?: 0)

    val height: Int
        get() = ceil(eyebrow.ascent + eyebrow.descent + gap + textHeight + (if (telugu != null) gap else 0f) + gap + referenceHeight).toInt()

    val width: Float
        get() = listOfNotNull(
            widest(verse), telugu?.let(::widest), eyebrow.width, reference.width,
            teluguReference?.let { if (oneRow) reference.width + referenceGap + it.width else it.width }
        ).max().coerceAtMost(maxWidth.toFloat())

    fun draw(canvas: Canvas) {
        var y = eyebrow.ascent
        canvas.drawText(eyebrow.text, 0f, y, eyebrow.paint)
        y += eyebrow.descent + gap
        canvas.save()
        canvas.translate(0f, y)
        verse.draw(canvas)
        canvas.restore()
        y += verse.height
        if (telugu != null) {
            y += gap
            canvas.save()
            canvas.translate(0f, y)
            telugu.draw(canvas)
            canvas.restore()
            y += telugu.height
        }
        y += gap
        val other = teluguReference
        if (other == null || oneRow) {
            val baseline = y + max(reference.ascent, other?.ascent ?: 0f)
            canvas.drawText(reference.text, 0f, baseline, reference.paint)
            other?.let { canvas.drawText(it.text, reference.width + referenceGap, baseline, it.paint) }
        } else {
            val baseline = y + reference.ascent
            canvas.drawText(reference.text, 0f, baseline, reference.paint)
            canvas.drawText(other.text, 0f, baseline + reference.descent + gap * 0.4f + other.ascent, other.paint)
        }
    }

    private fun widest(layout: StaticLayout): Float = (0 until layout.lineCount).maxOfOrNull(layout::getLineWidth) ?: 0f
}

/** Sets a run of text in another typeface (the numbers of a passage's verses). */
private class FontSpan(private val typeface: Typeface, private val weight: Int) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = setOn(paint)
    override fun updateMeasureState(paint: TextPaint) = setOn(paint)

    private fun setOn(paint: TextPaint) {
        paint.typeface = typeface
        paint.fontVariationSettings = "'wght' $weight"
    }
}

private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)

private fun PixelRect.toRect() = Rect(left, top, right, bottom)

/** Where shared pictures are written: for other apps to be given, or kept in the phone's Pictures. */
internal object SharedPictures {
    private const val FOLDER = "shared"
    private const val QUALITY = 92

    /** [picture] written where another app can be given it, as [name]; the address to give it. */
    fun forSharing(context: Context, picture: Bitmap, name: String): Uri {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        // Only the latest is kept: the app given one has read it by the time another is shared.
        folder.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        val file = File(folder, name)
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    /** Whether a picture can be kept in the phone's Pictures: from Android 10 (before, it takes a permission). */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** [picture] kept in the phone's Pictures, in "My Bible", as [name]; false when it couldn't be. */
    fun save(context: Context, picture: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/My Bible")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            val written = resolver.openOutputStream(uri)?.use { picture.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) } == true
            if (written) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            written
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
