package com.example.mybible.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * The recordings on the phone, one a language: the King James Version (the KJV Dramatized Audio, or
 * Faith Comes By Hearing's narrated one), and YGFM's Telugu Audio Bible. A folder is chosen for each once
 * (both can be one folder, holding the two); its files stay where they are, read through Android's
 * folder access, never copied. Which recording the folder holds is told by its files' names. Each of the
 * files that recording's timings name is found there by its name, looked over (whole, and the length
 * that was timed: anything else isn't this recording) and remembered, so reading aloud can play any
 * verse from its own stretch of its file (see [verse]).
 */
class Recordings(context: Context, private val scope: CoroutineScope) : RecordedVerses {
    private val app = context.applicationContext

    /** A recording there are timings for, made by tools/audio_timing and bundled ([asset]). */
    enum class Recording(val telugu: Boolean, val asset: String) {
        /** The KJV Dramatized Audio, from the Internet Archive: one MP4 a book. */
        KJV_DRAMATIZED(false, "audio_timing/kjv_dramatized.json"),

        /** Faith Comes By Hearing's King James Version, narrated: one MP3 a chapter. */
        KJV_NARRATED(false, "audio_timing/kjv.json"),

        /** YGFM's Telugu Audio Bible: one file a book (Psalms in two). */
        TELUGU(true, "audio_timing/telugu.json")
    }

    /** What's known of a language's recording, for the settings. */
    data class Status(
        /** The folder chosen, by its name; null before one is. */
        val folder: String? = null,
        /** Which of the language's recordings the folder holds, once it's been looked over. */
        val recording: Recording? = null,
        /** Being looked over: how many of its files so far, of how many. */
        val checking: Pair<Int, Int>? = null,
        /** Chapters there to play, of the recording's. */
        val chapters: Int = 0,
        val chaptersInAll: Int = 0,
        /** Chapters whose files weren't found in the folder, said briefly ("1 Samuel 16–31", "Ruth"). */
        val missing: List<String> = emptyList(),
        /** Chapters whose files are there but aren't as timed: another recording, or damaged. */
        val unlike: List<String> = emptyList(),
        /** Chapters whose files wouldn't play just now (moved or deleted since): looked over again, they're found. */
        val failed: List<String> = emptyList(),
        /** The folder can't be read any more (moved, or the app's access to it gone). */
        val lost: Boolean = false
    )

    private val _status = MutableStateFlow(mapOf(false to Status(), true to Status()))
    val status: StateFlow<Map<Boolean, Status>> = _status.asStateFlow()

    // A language's recording: the folder chosen, the recording it holds and the files found in it.
    private inner class Edition(val telugu: Boolean) {
        // The recordings its folder can hold: of two it holds as much of, the first.
        val recordings = Recording.entries.filter { it.telugu == telugu }
        val saved = File(app.filesDir, if (telugu) "recordings/telugu.json" else "recordings/kjv.json")

        @Volatile
        var folder: Uri? = null

        @Volatile
        var found: Found? = null

        // Files that wouldn't play (their uris), left out until looked over again.
        val failed: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

        @Volatile
        var result: Saved? = null
        var checking: Job? = null

        // Its loudness being measured (see measureLoudness).
        var measuring: Job? = null
    }

    // The recording a folder holds, its timings, and its files there: the timings' file names, each
    // with the file found for it.
    private class Found(val recording: Recording, val timings: RecordingTimings, val files: Map<String, RecordingFile>)

    private val editions = mapOf(false to Edition(false), true to Edition(true))

    // Each recording's timings, once read.
    private val loaded = java.util.concurrent.ConcurrentHashMap<Recording, RecordingTimings>()
    private val loading = Mutex()

    private fun edition(telugu: Boolean) = editions.getValue(telugu)

    init {
        // What was found before, off the main thread.
        scope.launch(Dispatchers.IO) {
            for (edition in editions.values) restore(edition)
        }
    }

    /** The folder picked for a language's recording (its access kept from now on): looked over at once. */
    fun choose(telugu: Boolean, folder: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Not offered to keep: it works until the phone restarts.
        }
        val edition = edition(telugu)
        val before = edition.folder
        if (before != null && before != folder && edition(!telugu).folder != before) letGo(before)
        check(edition, folder)
    }

    /** The folder looked over again (files added, moved or replaced since). */
    fun checkAgain(telugu: Boolean) {
        val edition = edition(telugu)
        edition.folder?.let { check(edition, it) }
    }

    override fun ready(telugu: Boolean): Boolean = edition(telugu).found?.files?.isNotEmpty() == true

    override fun loudness(telugu: Boolean): Double? = edition(telugu).takeIf { it.found != null }?.result?.loudness

    override suspend fun verse(verse: VerseToRead, telugu: Boolean, heading: Boolean): RecordedVerse? {
        val edition = edition(telugu)
        val found = edition.found ?: return null
        val span = found.timings.verse(verse.book, verse.chapter, verse.verse, heading) ?: return null
        val file = found.files[span.file]?.takeIf { it.uri !in edition.failed } ?: return null
        return RecordedVerse(file, span.from, span.start, span.end)
    }

    override fun failed(file: RecordingFile) {
        for (edition in editions.values) {
            if (edition.found?.files?.values.orEmpty().none { it.uri == file.uri }) continue
            edition.failed += file.uri
            publish(edition, statusOf(edition))
        }
    }

    // ---- looking over a folder ----

    private fun check(edition: Edition, folder: Uri) {
        edition.checking?.cancel()
        edition.measuring?.cancel()
        edition.folder = folder
        edition.checking = scope.launch(Dispatchers.IO) {
            val name = nameOf(folder)
            publish(edition, Status(folder = name, checking = 0 to 0))
            val listed = try {
                list(folder)
            } catch (e: Exception) {
                publish(edition, Status(folder = name, lost = true))
                return@launch
            }
            val byKey = listed.groupBy { RecordingIndex.key(it.name) }
            // The recording whose files these are: the one with most of its chapters here.
            val (recording, timings) = edition.recordings.mapNotNull { recording -> timings(recording)?.let { recording to it } }
                .maxByOrNull { (_, timings) -> RecordingIndex.chaptersHeld(timings, byKey.keys) }
                ?: return@launch publish(edition, Status(folder = name))
            val total = timings.files.size
            publish(edition, Status(folder = name, checking = 0 to total))
            val found = LinkedHashMap<String, SavedFile>()
            val missing = mutableListOf<String>()
            val unlike = mutableListOf<String>()
            var done = 0
            for ((file, seconds) in timings.files) {
                ensureActive()
                val candidates = byKey[RecordingIndex.key(file)].orEmpty()
                if (candidates.isEmpty()) {
                    missing += file
                } else {
                    // Of several copies (a replacement beside a damaged one), the first that's as timed.
                    val good = candidates.firstNotNullOfOrNull { copy -> look(copy.uri, seconds)?.let { SavedFile(copy.uri.toString(), it.name) } }
                    if (good != null) found[file] = good else unlike += file
                }
                if (++done % PROGRESS_EVERY == 0) publish(edition, Status(folder = name, checking = done to total))
            }
            val result = Saved(folder.toString(), name, recording.name, found, missing, unlike)
            save(edition, result)
            edition.result = result
            edition.failed.clear()
            edition.found = Found(recording, timings, found.mapValues { RecordingFile(it.value.uri, kindOf(it.value.kind)) })
            publish(edition, statusOf(edition))
            measureLoudness(edition)
        }
    }

    // How loud the folder's recording is (backlog 46): measured once, from a sample of its verses decoded
    // here (nothing played), and kept with what was found there. Until then it plays as recorded.
    private fun measureLoudness(edition: Edition) {
        val found = edition.found ?: return
        val result = edition.result ?: return
        if (result.loudness != null) return
        edition.measuring?.cancel()
        edition.measuring = scope.launch(Dispatchers.IO) {
            val lufs = RecordingLoudness.measure(RecordingLoudness.stretches(found.timings, found.files.keys)) { stretch, onPcm ->
                val file = found.files[stretch.file]
                isActive && file != null && PcmReader.read(app, Uri.parse(file.uri), stretch.from, stretch.to, onPcm)
            } ?: return@launch
            if (!isActive || edition.result !== result) return@launch
            val measured = result.copy(loudness = lufs)
            save(edition, measured)
            edition.result = measured
        }
    }

    private class Listed(val name: String, val uri: Uri)

    // Every file in the folder and the folders in it (a few deep: a zip unpacked keeps its own folder).
    private fun list(tree: Uri): List<Listed> {
        val resolver = app.contentResolver
        val out = mutableListOf<Listed>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += DocumentsContract.getTreeDocumentId(tree) to 0
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        while (queue.isNotEmpty()) {
            val (id, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val child = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (cursor.getString(2) == Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) queue += child to depth + 1
                    } else {
                        out += Listed(name, DocumentsContract.buildDocumentUriUsingTree(tree, child))
                    }
                }
            } ?: throw IllegalStateException("Can't list $tree")
        }
        return out
    }

    // The folder's own name ("KJV"), to show.
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

    // A file's kind, when it's whole and as long as the recording's file it's named for; else null.
    private fun look(uri: Uri, seconds: Double): MediaKind? {
        val facts = try {
            app.contentResolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                // Closing the channel closes the file.
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel.use { channel ->
                    MediaCheck.examine(ChannelBytes(channel, channel.size()))
                }
            }
        } catch (e: Exception) {
            null
        } ?: return null
        if (!facts.whole) return null
        val length = facts.seconds ?: lengthOf(uri) ?: return null
        return facts.kind.takeIf { abs(length - seconds) <= LENGTH_TOLERANCE }
    }

    // How long a file plays, by the phone's own reckoning: for kinds MediaCheck doesn't look into.
    private fun lengthOf(uri: Uri): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it / 1000.0 }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private class ChannelBytes(private val channel: FileChannel, override val size: Long) : ByteSource {
        override fun read(at: Long, count: Int): ByteArray {
            if (at < 0 || at >= size || count <= 0) return ByteArray(0)
            val buffer = ByteBuffer.allocate(minOf(count.toLong(), size - at).toInt())
            var position = at
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, position)
                if (read <= 0) break
                position += read
            }
            return buffer.array().copyOf(buffer.position())
        }
    }

    private fun letGo(folder: Uri) {
        try {
            app.contentResolver.releasePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Not kept anyway.
        }
    }

    // ---- what's kept between visits ----

    @Serializable
    private data class SavedFile(val uri: String, val kind: String)

    @Serializable
    private data class Saved(
        val folder: String,
        val name: String,
        // The recording found there (saved before there were two English ones: Faith Comes By Hearing's).
        val recording: String = Recording.KJV_NARRATED.name,
        val found: Map<String, SavedFile> = emptyMap(),
        val missing: List<String> = emptyList(),
        val unlike: List<String> = emptyList(),
        // How loud the recording is, in LUFS, once measured (see measureLoudness).
        val loudness: Double? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun save(edition: Edition, result: Saved) {
        try {
            edition.saved.parentFile?.mkdirs()
            val temp = File(edition.saved.path + ".tmp")
            temp.writeText(json.encodeToString(Saved.serializer(), result))
            temp.renameTo(edition.saved)
        } catch (e: Exception) {
            // Looked over again next time.
        }
    }

    private suspend fun restore(edition: Edition) {
        val result = try {
            if (!edition.saved.exists()) return
            json.decodeFromString(Saved.serializer(), edition.saved.readText())
        } catch (e: Exception) {
            return
        }
        val folder = Uri.parse(result.folder)
        edition.folder = folder
        edition.result = result
        // The app's access to the folder, still there?
        val kept = app.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isReadPermission }
        val recording = edition.recordings.firstOrNull { it.name == result.recording } ?: edition.recordings.first()
        val timings = timings(recording) ?: return
        if (!kept) {
            publish(edition, Status(folder = result.name, lost = true))
            return
        }
        edition.found = Found(recording, timings, result.found.mapValues { RecordingFile(it.value.uri, kindOf(it.value.kind)) })
        publish(edition, statusOf(edition))
        measureLoudness(edition)
    }

    private suspend fun timings(recording: Recording): RecordingTimings? {
        loaded[recording]?.let { return it }
        return loading.withLock {
            loaded[recording] ?: withContext(Dispatchers.IO) {
                try {
                    RecordingTimings.parse(app.assets.open(recording.asset).bufferedReader().use { it.readText() })
                } catch (e: Exception) {
                    null
                }
            }?.also { loaded[recording] = it }
        }
    }

    private fun statusOf(edition: Edition): Status {
        val result = edition.result ?: return Status()
        val found = edition.found ?: return Status()
        val byFile = found.timings.chaptersByFile
        val all = found.timings.chapterNames
        fun chaptersOf(files: Collection<String>) = files.flatMap { byFile[it].orEmpty() }
        val failed = found.files.filterValues { it.uri in edition.failed }.keys
        val playable = found.files.keys - failed
        return Status(
            folder = result.name,
            recording = found.recording,
            chapters = chaptersOf(playable).size,
            chaptersInAll = all.size,
            missing = RecordingIndex.summarize(chaptersOf(result.missing), all),
            unlike = RecordingIndex.summarize(chaptersOf(result.unlike), all),
            failed = RecordingIndex.summarize(chaptersOf(failed), all)
        )
    }

    private fun publish(edition: Edition, status: Status) {
        _status.update { it + (edition.telugu to status) }
    }

    private fun kindOf(name: String): MediaKind = MediaKind.entries.firstOrNull { it.name == name } ?: MediaKind.OTHER

    private companion object {
        // How far a file's length may be from the one timed, in seconds: a frame or two either way, and
        // the odd tag; a different recording of a chapter is seconds out at least.
        const val LENGTH_TOLERANCE = 1.0

        // Folders within the folder looked into.
        const val MAX_DEPTH = 4

        // The settings are told how it's going every so many files.
        const val PROGRESS_EVERY = 20
    }
}
