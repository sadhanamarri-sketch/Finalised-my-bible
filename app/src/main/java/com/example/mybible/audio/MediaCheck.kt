package com.example.mybible.audio

/**
 * What kind of sound file one of a recording's files is, so it's opened the right way: an MP3 at one
 * bitrate throughout (a place in it is found by arithmetic, exactly), an MP3 whose bitrate varies (found
 * by reading through it), an MP4 (M4A) with its index of where each few seconds are, or something else.
 */
enum class MediaKind(val mime: String?) {
    MP3("audio/mpeg"),
    MP3_VARIABLE("audio/mpeg"),
    MP4("audio/mp4"),
    OTHER(null)
}

/**
 * A file looked over: its kind, how long it plays in seconds (null when that can't be told from its
 * headers), and whether it's whole. A file that's lost its sound (a download that broke off, a stretch
 * of zeros) isn't, though it may be the right length.
 */
data class MediaFacts(val kind: MediaKind, val seconds: Double?, val whole: Boolean)

/** Random access to a file's bytes: [read] gives fewer than asked at the end, none past it. */
interface ByteSource {
    val size: Long

    fun read(at: Long, count: Int): ByteArray
}

/**
 * Looks a sound file over from its headers and a few places through it, without decoding it: quick
 * enough for a whole Bible's files. Only the formats the recordings come in are looked into (MP3, MP4);
 * anything else is [MediaKind.OTHER], its length unknown.
 */
object MediaCheck {

    fun examine(bytes: ByteSource): MediaFacts {
        if (bytes.size < 16) return MediaFacts(MediaKind.OTHER, null, whole = false)
        val head = bytes.read(0, 12)
        if (text(head, 4, 4) == "ftyp") return mp4(bytes)
        // Other kinds of sound file, which an MP3's frames could be glimpsed in by chance.
        if (text(head, 0, 4) in OTHER_STARTS) return MediaFacts(MediaKind.OTHER, null, whole = true)
        return mp3(bytes) ?: MediaFacts(MediaKind.OTHER, null, whole = true)
    }

    // ---- MP3 ----

    private class Frame(val size: Int, val bitrate: Int, val sampleRate: Int, val samples: Int, val mpeg1: Boolean, val mono: Boolean)

    // An MPEG audio layer III frame's header at [at] in [b], or null when there isn't one there.
    private fun frameAt(b: ByteArray, at: Int): Frame? {
        if (at < 0 || at + 4 > b.size) return null
        val h = ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)
        if ((h ushr 21) and 0x7ff != 0x7ff) return null
        val version = (h ushr 19) and 3
        val layer = (h ushr 17) and 3
        val bitrateIndex = (h ushr 12) and 15
        val rateIndex = (h ushr 10) and 3
        if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
        val mpeg1 = version == 3
        val bitrate = (if (mpeg1) MPEG1_KBPS else MPEG2_KBPS)[bitrateIndex] * 1000
        val sampleRate = when (version) {
            3 -> intArrayOf(44100, 48000, 32000)
            2 -> intArrayOf(22050, 24000, 16000)
            else -> intArrayOf(11025, 12000, 8000)
        }[rateIndex]
        val samples = if (mpeg1) 1152 else 576
        val size = samples / 8 * bitrate / sampleRate + ((h ushr 9) and 1)
        return Frame(size, bitrate, sampleRate, samples, mpeg1, mono = (h ushr 6) and 3 == 3)
    }

    // A frame at [at] that another of the same kind follows: real, not a chance pattern in the sound.
    private fun confirmedAt(b: ByteArray, at: Int): Frame? {
        val frame = frameAt(b, at) ?: return null
        val next = frameAt(b, at + frame.size)
        if (at + frame.size + 4 > b.size) return frame
        return frame.takeIf { next != null && next.sampleRate == frame.sampleRate && next.mpeg1 == frame.mpeg1 }
    }

    private fun mp3(bytes: ByteSource): MediaFacts? {
        // Past any ID3 tags (cover pictures make them long) to the first frame.
        var start = 0L
        while (true) {
            val tag = bytes.read(start, 10)
            if (tag.size < 10 || text(tag, 0, 3) != "ID3") break
            val length = ((tag[6].toInt() and 0x7f) shl 21) or ((tag[7].toInt() and 0x7f) shl 14) or
                ((tag[8].toInt() and 0x7f) shl 7) or (tag[9].toInt() and 0x7f)
            start += 10 + length + (if (tag[5].toInt() and 0x10 != 0) 10 else 0)
        }
        val look = bytes.read(start, LOOK_FOR_FIRST)
        val offset = (0 until (look.size - 4).coerceAtLeast(0)).firstOrNull { confirmedAt(look, it) != null } ?: return null
        val firstAt = start + offset
        val firstBytes = bytes.read(firstAt, MAX_FRAME + 8)
        val first = frameAt(firstBytes, 0) ?: return null
        // The end of the sound: before an ID3v1 tag, if there's one.
        val tail = bytes.read(bytes.size - 128, 3)
        val end = bytes.size - if (text(tail, 0, 3) == "TAG") 128 else 0

        // A Xing or VBRI header (a varying bitrate) or an Info one (one bitrate), in the first frame.
        val sideInfo = if (first.mpeg1) (if (first.mono) 21 else 36) else (if (first.mono) 13 else 21)
        val tag = text(firstBytes, sideInfo, 4)
        val vbri = text(firstBytes, 36, 4) == "VBRI"
        val xingFrames = if (tag == "Xing" || tag == "Info") {
            val flags = int32(firstBytes, sideInfo + 4)
            if (flags and 1 != 0) int32(firstBytes, sideInfo + 8).toLong() and 0xffffffffL else null
        } else if (vbri) {
            int32(firstBytes, 36 + 14).toLong() and 0xffffffffL
        } else {
            null
        }
        val variable = tag == "Xing" || vbri
        val audioStart = if (tag == "Xing" || tag == "Info" || vbri) firstAt + first.size else firstAt

        // A few places through it, each with a frame in: a stretch of zeros, or a file cut short, has none.
        // At one bitrate, each frame found has it too (else the bitrate varies, though nothing says so).
        var whole = true
        var steady = !variable
        for (k in 1 until SAMPLES) {
            val at = audioStart + (end - audioStart) * k / SAMPLES
            val window = bytes.read(at, MAX_FRAME * 2 + 8)
            val found = (0 until (window.size - 4).coerceAtLeast(0)).asSequence().mapNotNull { confirmedAt(window, it) }.firstOrNull()
            if (found == null) {
                whole = false
                break
            }
            if (found.bitrate != first.bitrate) steady = false
        }
        val seconds = when {
            xingFrames != null && xingFrames > 0 -> xingFrames * first.samples.toDouble() / first.sampleRate
            steady -> (end - audioStart) * 8.0 / first.bitrate
            else -> null
        }
        return MediaFacts(if (steady) MediaKind.MP3 else MediaKind.MP3_VARIABLE, seconds, whole)
    }

    // ---- MP4 ----

    private fun mp4(bytes: ByteSource): MediaFacts {
        var at = 0L
        var sidxAt = -1L
        var sidxSize = 0
        var moovAt = -1L
        var moovSize = 0
        var fragmented = false
        while (at + 8 <= bytes.size) {
            val header = bytes.read(at, 16)
            if (header.size < 8) break
            var size = int32(header, 0).toLong() and 0xffffffffL
            val type = text(header, 4, 4)
            if (size == 1L && header.size >= 16) size = int64(header, 8)
            if (size == 0L) size = bytes.size - at
            if (size < 8) return MediaFacts(MediaKind.MP4, null, whole = false)
            when (type) {
                "moov" -> {
                    moovAt = at
                    moovSize = size.coerceAtMost(MAX_BOX).toInt()
                }
                "sidx" -> if (sidxAt < 0) {
                    sidxAt = at
                    sidxSize = size.coerceAtMost(MAX_BOX).toInt()
                }
                // The fragments themselves aren't walked: an index, if there is one, comes before them.
                "moof" -> {
                    fragmented = true
                    break
                }
            }
            if (sidxAt >= 0 && moovAt >= 0) break
            at += size
        }
        if (sidxAt >= 0) return indexed(bytes, sidxAt, sidxSize)
        if (moovAt < 0 || fragmented) return MediaFacts(MediaKind.MP4, null, whole = moovAt >= 0)
        // A plain MP4: its length from the movie header.
        val moov = bytes.read(moovAt, moovSize)
        val mvhd = find(moov, "mvhd") ?: return MediaFacts(MediaKind.MP4, null, whole = true)
        val version = moov[mvhd + 8].toInt()
        val (scale, duration) = if (version == 1) {
            int32(moov, mvhd + 28).toLong() to int64(moov, mvhd + 32)
        } else {
            int32(moov, mvhd + 20).toLong() to (int32(moov, mvhd + 24).toLong() and 0xffffffffL)
        }
        return MediaFacts(MediaKind.MP4, if (scale > 0 && duration > 0) duration.toDouble() / scale else null, whole = true)
    }

    // A fragmented MP4 (DASH's): its length is its index's, and each fragment the index names is there.
    private fun indexed(bytes: ByteSource, at: Long, size: Int): MediaFacts {
        val sidx = bytes.read(at, size)
        if (sidx.size < 32) return MediaFacts(MediaKind.MP4, null, whole = false)
        val version = sidx[8].toInt()
        val scale = int32(sidx, 16).toLong() and 0xffffffffL
        var p = 20
        val earliest: Long
        val firstOffset: Long
        if (version == 0) {
            earliest = int32(sidx, p).toLong() and 0xffffffffL
            firstOffset = int32(sidx, p + 4).toLong() and 0xffffffffL
            p += 8
        } else {
            earliest = int64(sidx, p)
            firstOffset = int64(sidx, p + 8)
            p += 16
        }
        val count = ((sidx[p + 2].toInt() and 0xff) shl 8) or (sidx[p + 3].toInt() and 0xff)
        p += 4
        if (scale <= 0 || count == 0 || p + count * 12 > sidx.size) return MediaFacts(MediaKind.MP4, null, whole = false)
        val offsets = LongArray(count)
        var offset = at + size + firstOffset
        var total = 0L
        for (i in 0 until count) {
            offsets[i] = offset
            offset += int32(sidx, p).toLong() and 0x7fffffffL
            total += int32(sidx, p + 4).toLong() and 0xffffffffL
            p += 12
        }
        // The last fragment ends within the file, and a few through it start where the index says.
        var whole = offset <= bytes.size
        if (whole) {
            for (k in 0 until SAMPLES) {
                val box = bytes.read(offsets[(count - 1) * k / (SAMPLES - 1)], 8)
                if (text(box, 4, 4) !in FRAGMENT_STARTS) {
                    whole = false
                    break
                }
            }
        }
        return MediaFacts(MediaKind.MP4, (earliest + total).toDouble() / scale, whole)
    }

    // Where a box of [type] starts within [b] (its size field), searching inside the boxes there.
    private fun find(b: ByteArray, type: String): Int? {
        val name = type.toByteArray(Charsets.ISO_8859_1)
        for (i in 4..b.size - 4) {
            if (b[i] == name[0] && b[i + 1] == name[1] && b[i + 2] == name[2] && b[i + 3] == name[3]) return i - 4
        }
        return null
    }

    // [count] bytes of [b] from [at] as text ("ftyp", "Xing"); "" past its end.
    private fun text(b: ByteArray, at: Int, count: Int): String =
        if (at < 0 || at + count > b.size) "" else String(b, at, count, Charsets.ISO_8859_1)

    private fun int32(b: ByteArray, at: Int): Int {
        if (at < 0 || at + 4 > b.size) return 0
        return ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)
    }

    private fun int64(b: ByteArray, at: Int): Long =
        ((int32(b, at).toLong() and 0xffffffffL) shl 32) or (int32(b, at + 4).toLong() and 0xffffffffL)

    private val MPEG1_KBPS = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MPEG2_KBPS = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    // The longest an MP3 frame can be (layer III, 320 kbps at 32 kHz, padded).
    private const val MAX_FRAME = 1441

    // How far past the tags the first frame is looked for.
    private const val LOOK_FOR_FIRST = 64 * 1024

    // Places through a file looked at.
    private const val SAMPLES = 16

    // The most of a box read to look into it.
    private const val MAX_BOX = 4L * 1024 * 1024

    // What a fragment the index points to may start with.
    private val FRAGMENT_STARTS = setOf("moof", "styp", "emsg", "prft", "sidx")

    // How FLAC, Ogg, WAV and AMR files start.
    private val OTHER_STARTS = setOf("fLaC", "OggS", "RIFF", "#!AM")
}
