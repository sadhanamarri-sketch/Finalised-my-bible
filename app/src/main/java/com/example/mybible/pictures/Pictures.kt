package com.example.mybible.pictures

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * The pictures for Verse Scroll's cards: a folder on the phone, chosen once (its access kept), holding a
 * picture for each scene, each named for its verses ([PictureName]): added here ([add], which shrinks
 * it to a card's size and names it) or put there by hand. The folder is looked over when it's chosen
 * and each time Verse Scroll opens; what was found is kept between visits, with how much each picture
 * is dimmed under a verse ([PictureVeil]), measured once.
 */
class Pictures(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext

    data class Status(
        /** The folder chosen, by its name; null before one is. */
        val folder: String? = null,
        /** Being looked over. */
        val looking: Boolean = false,
        /** The folder can't be read any more (moved, or the app's access to it gone). */
        val lost: Boolean = false
    )

    /** Why a picture couldn't be added: said as it is to the person adding it. */
    class Trouble(message: String) : Exception(message)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _shelf = MutableStateFlow(PictureShelf.Empty)

    /** The pictures in the folder, as last looked over. */
    val shelf: StateFlow<PictureShelf> = _shelf.asStateFlow()

    private val _veils = MutableStateFlow<Map<String, PictureVeil.Veil>>(emptyMap())

    /** How much each picture (by its uri) is dimmed under a verse, once measured. */
    val veils: StateFlow<Map<String, PictureVeil.Veil>> = _veils.asStateFlow()

    @Volatile
    private var folder: Uri? = null
    private var looking: Job? = null
    private val writing = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val saved = File(app.filesDir, "pictures/folder.json")

    init {
        scope.launch(Dispatchers.IO) {
            try {
                restore()
            } finally {
                restored.complete(Unit)
            }
            folder?.let { lookOver(it) }
        }
    }

    /** Once what was found before is back (from the last visit), so the first cards get their pictures. */
    suspend fun ready() = restored.await()

    /** The folder picked for the pictures (its access kept from now on): looked over at once. */
    fun choose(tree: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Not offered to keep: it works until the phone restarts.
        }
        folder = tree
        lookOver(tree)
    }

    /** The folder looked over again (pictures put there or taken away since). */
    fun lookAgain() {
        folder?.let { lookOver(it) }
    }

    // ---- the scenes there are prompts for ----

    private var scenes: PictureScenes? = null
    private val loadingScenes = Mutex()

    /** The scenes there are prompts for, from the app's own list. */
    suspend fun scenes(): PictureScenes = scenes ?: loadingScenes.withLock {
        scenes ?: withContext(Dispatchers.IO) {
            try {
                PictureScenes.parse(app.assets.open("pictures/scenes.tsv").bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                PictureScenes.None
            }
        }.also { scenes = it }
    }

    // ---- adding and removing ----

    /**
     * Saves the picture at [from] (one picked from the phone's pictures) in the folder as [place]'s
     * ("Genesis 1.3-5"): shrunk to a card's size, as a WebP, in place of the one there was.
     */
    suspend fun add(place: String, from: Uri): Picture = writing.withLock {
        withContext(Dispatchers.IO) {
            val tree = folder ?: throw Trouble("Choose a folder for the pictures first.")
            val bitmap = decodeToFit(from, MAX_WIDTH, MAX_HEIGHT) ?: throw Trouble("That picture couldn't be opened.")
            val veil = veilOf(bitmap)
            val bytes = ByteArrayOutputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                bitmap.compress(format, QUALITY, out)
                out.toByteArray()
            }
            bitmap.recycle()
            val resolver = app.contentResolver
            val file = "$place.webp"
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            // The new one written first, then the old one taken away: a picture that won't save leaves the old.
            var made = try {
                DocumentsContract.createDocument(resolver, parent, WEBP, file)
            } catch (e: Exception) {
                null
            } ?: throw lost("The pictures folder can't be written to. Choose it again.")
            try {
                resolver.openOutputStream(made, "w")?.use { it.write(bytes) } ?: throw Trouble("The picture couldn't be saved.")
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, made) }
                throw e as? Trouble ?: Trouble("The picture couldn't be saved.")
            }
            val name = PictureName.parse(file)
            val replaced = _shelf.value.forPlace(place).filter { it.name?.variant == 1 && it.uri != made.toString() }
            for (old in replaced) runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(old.uri)) }
            // Saved beside the old one under another name ("… (1).webp"): its own name, now that's free.
            val (shownAs, modified, size) = factsOf(made) ?: Triple(file, System.currentTimeMillis(), bytes.size.toLong())
            var finalName = shownAs
            if (shownAs != file) {
                runCatching { DocumentsContract.renameDocument(resolver, made, file) }.getOrNull()?.let {
                    made = it
                    finalName = file
                }
            }
            val picture = Picture(made.toString(), finalName, PictureName.parse(finalName) ?: name, modified, size)
            val gone = replaced.mapTo(HashSet()) { it.uri }
            _shelf.update { shelf -> PictureShelf(shelf.pictures.filter { it.uri !in gone && it.uri != picture.uri } + picture) }
            _veils.update { veils -> veils - gone + (picture.uri to veil) }
            save()
            picture
        }
    }

    /** Takes [picture] out of the folder. */
    suspend fun remove(picture: Picture): Boolean = writing.withLock {
        withContext(Dispatchers.IO) {
            val removed = try {
                DocumentsContract.deleteDocument(app.contentResolver, Uri.parse(picture.uri))
            } catch (e: Exception) {
                false
            }
            if (removed) {
                _shelf.update { shelf -> PictureShelf(shelf.pictures.filter { it.uri != picture.uri }) }
                _veils.update { it - picture.uri }
                save()
            }
            removed
        }
    }

    // ---- showing them ----

    /** [uri]'s picture, big enough for a card (at least [CARD_HEIGHT] tall), in [config]. */
    suspend fun image(uri: String, config: Bitmap.Config): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampled(Uri.parse(uri), CARD_HEIGHT, config)
    }

    /** [uri]'s picture, small, for a list ([height] pixels tall at least). */
    suspend fun thumbnail(uri: String, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampled(Uri.parse(uri), height, Bitmap.Config.ARGB_8888)
    }

    // ---- looking over the folder ----

    private fun lookOver(tree: Uri) {
        looking?.cancel()
        looking = scope.launch(Dispatchers.IO) {
            restored.await()
            val name = nameOf(tree)
            _status.value = Status(folder = name, looking = true)
            val listed = try {
                list(tree)
            } catch (e: Exception) {
                _status.value = Status(folder = name, lost = true)
                return@launch
            }
            // A picture changed since it was measured is measured again.
            val before = _shelf.value
            val still = listed.filter { now -> before[now.uri]?.let { it.modified == now.modified && it.size == now.size } == true }
                .mapTo(HashSet()) { it.uri }
            _veils.update { veils -> veils.filterKeys { it in still } }
            _shelf.value = PictureShelf(listed)
            _status.value = Status(folder = name)
            save()
            measureMissing()
        }
    }

    // Each picture not yet measured, one at a time (put there by hand, or changed since).
    private suspend fun measureMissing() {
        var measured = 0
        for (picture in _shelf.value.pictures) {
            if (picture.uri in _veils.value) continue
            kotlin.coroutines.coroutineContext.ensureActive()
            val bitmap = decodeSampled(Uri.parse(picture.uri), PictureVeil.CARD_H * 2, Bitmap.Config.ARGB_8888) ?: continue
            val veil = veilOf(bitmap)
            bitmap.recycle()
            _veils.update { it + (picture.uri to veil) }
            if (++measured % SAVE_EVERY == 0) save()
        }
        if (measured > 0) save()
    }

    // Every picture in the folder and the folders in it (a couple deep).
    private fun list(tree: Uri): List<Picture> {
        val resolver = app.contentResolver
        val out = mutableListOf<Picture>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += DocumentsContract.getTreeDocumentId(tree) to 0
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE
        )
        while (queue.isNotEmpty()) {
            val (id, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val child = cursor.getString(0) ?: continue
                    val file = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) queue += child to depth + 1
                        continue
                    }
                    // Pictures only, and not the phone's hidden or trashed files; of those, the ones named for a
                    // place in the Bible, or for any card.
                    if (file.startsWith(".") || !(mime.startsWith("image/") || isImageName(file))) continue
                    val name = PictureName.parse(file)
                    if (name == null && !PictureName.forAnyCard(file)) continue
                    out += Picture(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, child).toString(),
                        file = file,
                        name = name,
                        modified = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                        size = if (cursor.isNull(4)) 0 else cursor.getLong(4)
                    )
                }
            } ?: throw IllegalStateException("Can't list $tree")
        }
        return out
    }

    // A file's name, when it was last changed, and its size.
    private fun factsOf(uri: Uri): Triple<String, Long, Long>? = try {
        app.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE), null, null, null)?.use {
            if (!it.moveToFirst()) null
            else Triple(it.getString(0) ?: return@use null, if (it.isNull(1)) 0L else it.getLong(1), if (it.isNull(2)) 0L else it.getLong(2))
        }
    } catch (e: Exception) {
        null
    }

    // The folder's own name ("Bible pictures"), to show.
    private fun nameOf(tree: Uri): String {
        val name = try {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            app.contentResolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return name ?: tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
    }

    private fun lost(message: String): Trouble {
        _status.update { it.copy(lost = true) }
        return Trouble(message)
    }

    // ---- reading pictures ----

    // The picture at [uri] made to fit within [maxWidth] by [maxHeight] (never larger than it is).
    private fun decodeToFit(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        val resolver = app.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return try {
                // Turned the right way up, as the phone's camera notes it.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val scale = minOf(1.0, maxWidth.toDouble() / info.size.width, maxHeight.toDouble() / info.size.height)
                    if (scale < 1.0) {
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) {
                null
            }
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null
            val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
            // Most of the way by a power of two as it's read, the rest smoothly.
            var sample = 1
            while (sample * 2 <= 1 / scale) sample *= 2
            val read = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val w = (width * scale).roundToInt().coerceAtLeast(1)
            val h = (height * scale).roundToInt().coerceAtLeast(1)
            if (read.width == w && read.height == h) read
            else Bitmap.createScaledBitmap(read, w, h, true).also { if (it !== read) read.recycle() }
        } catch (e: Exception) {
            null
        }
    }

    // The picture at [uri], at least [height] pixels tall where it's taller (read at a power of two of its size).
    private fun decodeSampled(uri: Uri, height: Int, config: Bitmap.Config): Bitmap? = try {
        val resolver = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= height) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = config
                })
            }
        }
    } catch (e: Exception) {
        null
    }

    // How much [bitmap] needs dimming under a verse: measured on it as a card shows it, small.
    private fun veilOf(bitmap: Bitmap): PictureVeil.Veil {
        val (left, top, width, height) = PictureVeil.cardCrop(bitmap.width, bitmap.height)
        val card = Bitmap.createBitmap(bitmap, left, top, width, height)
        val small = Bitmap.createScaledBitmap(card, PictureVeil.CARD_W, PictureVeil.CARD_H, true)
        val pixels = IntArray(PictureVeil.CARD_W * PictureVeil.CARD_H)
        small.getPixels(pixels, 0, PictureVeil.CARD_W, 0, 0, PictureVeil.CARD_W, PictureVeil.CARD_H)
        if (small !== card) small.recycle()
        if (card !== bitmap) card.recycle()
        return PictureVeil.measure(pixels)
    }

    // ---- what's kept between visits ----

    @Serializable
    private data class SavedPicture(
        val uri: String,
        val file: String,
        val modified: Long = 0,
        val size: Long = 0,
        val dark: Float? = null,
        val light: Float? = null
    )

    @Serializable
    private data class Saved(val folder: String, val pictures: List<SavedPicture> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    private fun save() {
        val tree = folder ?: return
        val veils = _veils.value
        val result = Saved(tree.toString(), _shelf.value.pictures.map { p ->
            val veil = veils[p.uri]
            SavedPicture(p.uri, p.file, p.modified, p.size, veil?.dark, veil?.light)
        })
        try {
            saved.parentFile?.mkdirs()
            val temp = File(saved.path + ".tmp")
            temp.writeText(json.encodeToString(Saved.serializer(), result))
            temp.renameTo(saved)
        } catch (e: Exception) {
            // Looked over again next time.
        }
    }

    private fun restore() {
        val result = try {
            if (!saved.exists()) return
            json.decodeFromString(Saved.serializer(), saved.readText())
        } catch (e: Exception) {
            return
        }
        val tree = Uri.parse(result.folder)
        folder = tree
        _shelf.value = PictureShelf(result.pictures.map { Picture(it.uri, it.file, PictureName.parse(it.file), it.modified, it.size) })
        _veils.value = result.pictures.mapNotNull { p ->
            if (p.dark != null && p.light != null) p.uri to PictureVeil.Veil(p.dark, p.light) else null
        }.toMap()
        // The app's access to the folder, still there?
        val kept = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        _status.value = Status(folder = nameOf(tree), lost = !kept)
    }

    companion object {
        private const val WEBP = "image/webp"

        // A picture added is kept at most this big: a phone's screen, in the 9:16 the prompts ask for.
        private const val MAX_WIDTH = 1080
        private const val MAX_HEIGHT = 1920

        // WebP quality: about 150-250 KB for a 9:16 picture, and no difference to see on a card.
        private const val QUALITY = 82

        // A picture is read for a card at least this tall (where it's taller).
        private const val CARD_HEIGHT = 1400

        // Folders within the folder looked into.
        private const val MAX_DEPTH = 2

        // What's been measured is saved every so many pictures.
        private const val SAVE_EVERY = 20

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

        private fun isImageName(file: String) = file.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }
}
