package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MediaCheckTest {

    private class Bytes(val b: ByteArray) : ByteSource {
        override val size: Long get() = b.size.toLong()
        override fun read(at: Long, count: Int): ByteArray {
            if (at < 0 || at >= b.size || count <= 0) return ByteArray(0)
            return b.copyOfRange(at.toInt(), minOf(b.size.toLong(), at + count).toInt())
        }
    }

    private fun examine(b: ByteArray) = MediaCheck.examine(Bytes(b))

    // An MPEG-1 layer III frame at 128 kbps and 48 kHz: 384 bytes, 24 ms. [bitrateIndex] 9 is 128 kbps.
    private fun frame(bitrateIndex: Int = 9, tag: String? = null, frames: Int = 0): ByteArray {
        val size = 144 * KBPS[bitrateIndex] * 1000 / 48000
        val b = ByteArray(size)
        b[0] = 0xFF.toByte()
        b[1] = 0xFB.toByte()
        b[2] = ((bitrateIndex shl 4) or (1 shl 2)).toByte()
        b[3] = 0
        if (tag != null) {
            tag.toByteArray(Charsets.ISO_8859_1).copyInto(b, 36)
            b[43] = 1
            for (i in 0 until 4) b[44 + i] = (frames ushr (24 - 8 * i)).toByte()
        }
        return b
    }

    private fun id3(length: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray())
        out.write(byteArrayOf(3, 0, 0, (length ushr 21 and 0x7f).toByte(), (length ushr 14 and 0x7f).toByte(), (length ushr 7 and 0x7f).toByte(), (length and 0x7f).toByte()))
        out.write(ByteArray(length) { 0x41 })
        return out.toByteArray()
    }

    private fun mp3(count: Int, head: ByteArray = ByteArray(0), first: ByteArray? = null, bitrates: (Int) -> Int = { 9 }): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(head)
        first?.let { out.write(it) }
        repeat(count) { out.write(frame(bitrates(it))) }
        return out.toByteArray()
    }

    @Test
    fun anMp3AtOneBitrateIsTimedByItsSize() {
        val facts = examine(mp3(2500, head = id3(80_000)))
        assertEquals(MediaKind.MP3, facts.kind)
        assertEquals(60.0, facts.seconds!!, 1e-6)
        assertTrue(facts.whole)
        // An ID3v1 tag at the end isn't sound.
        val tagged = mp3(2500) + "TAG".toByteArray() + ByteArray(125)
        assertEquals(60.0, examine(tagged).seconds!!, 1e-6)
    }

    @Test
    fun anMp3WhoseBitrateVariesIsTimedByItsXingHeader() {
        val vbr = mp3(2500, first = frame(tag = "Xing", frames = 2500)) { if (it % 3 == 0) 11 else 9 }
        val facts = examine(vbr)
        assertEquals(MediaKind.MP3_VARIABLE, facts.kind)
        assertEquals(60.0, facts.seconds!!, 1e-6)
        assertTrue(facts.whole)
        // Varying with nothing to say so: no length to be had from its size.
        val untold = examine(mp3(2500) { if (it > 1200) 11 else 9 })
        assertEquals(MediaKind.MP3_VARIABLE, untold.kind)
        assertNull(untold.seconds)
        // An Info header says one bitrate: found in by arithmetic, as without one.
        val cbr = examine(mp3(2500, first = frame(tag = "Info", frames = 2500)))
        assertEquals(MediaKind.MP3, cbr.kind)
        assertEquals(60.0, cbr.seconds!!, 1e-6)
    }

    @Test
    fun anMp3ThatsLostItsSoundIsntWhole() {
        val good = mp3(2500, head = id3(1000))
        val zeroed = good.copyOf().also { b -> for (i in b.size / 3 until b.size * 2 / 3) b[i] = 0 }
        assertFalse(examine(zeroed).whole)
        // Cut short, it's whole, but shorter: not the length timed.
        val cut = examine(good.copyOf(good.size / 2))
        assertTrue(cut.whole)
        assertEquals(30.0, cut.seconds!!, 0.05)
    }

    private fun box(type: String, body: ByteArray): ByteArray {
        val size = body.size + 8
        return byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) +
            type.toByteArray(Charsets.ISO_8859_1) + body
    }

    private fun int32(v: Long) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    // A DASH M4A: its index, then each few seconds a fragment, at 44.1 kHz.
    private fun dash(durations: List<Long>, fragment: Int = 3000): ByteArray {
        val refs = ByteArrayOutputStream()
        for (d in durations) {
            refs.write(int32(fragment.toLong()))
            refs.write(int32(d))
            refs.write(int32(0x90000000))
        }
        val sidx = box(
            "sidx",
            int32(0) + int32(1) + int32(44100) + int32(0) + int32(0) +
                byteArrayOf(0, 0, (durations.size ushr 8).toByte(), durations.size.toByte()) + refs.toByteArray()
        )
        val fragments = durations.map { box("moof", ByteArray(fragment - 8)) }.reduce(ByteArray::plus)
        return box("ftyp", "dash".toByteArray() + int32(0) + "iso6".toByteArray()) + box("moov", ByteArray(32)) + sidx + fragments
    }

    @Test
    fun anM4aIsTimedByItsIndex() {
        val file = dash(List(40) { 441_000L })
        val facts = examine(file)
        assertEquals(MediaKind.MP4, facts.kind)
        assertEquals(400.0, facts.seconds!!, 1e-9)
        assertTrue(facts.whole)
        // Cut short, or a fragment gone to zeros: not whole.
        assertFalse(examine(file.copyOf(file.size - 5000)).whole)
        val zeroed = file.copyOf().also { b -> for (i in b.size / 2 until b.size / 2 + 6000) b[i] = 0 }
        assertFalse(examine(zeroed).whole)
    }

    @Test
    fun aPlainMp4IsTimedByItsMovieHeader() {
        val mvhd = box("mvhd", byteArrayOf(0, 0, 0, 0) + int32(0) + int32(0) + int32(1000) + int32(754_500) + ByteArray(80))
        val file = box("ftyp", "M4A ".toByteArray() + int32(0)) + box("moov", mvhd) + box("mdat", ByteArray(4000))
        val facts = examine(file)
        assertEquals(MediaKind.MP4, facts.kind)
        assertEquals(754.5, facts.seconds!!, 1e-9)
    }

    @Test
    fun otherKindsAreLeftToThePhone() {
        val flac = "fLaC".toByteArray() + ByteArray(4000)
        assertEquals(MediaFacts(MediaKind.OTHER, null, whole = true), examine(flac))
        assertEquals(MediaKind.OTHER, examine(ByteArray(4000) { 7 }).kind)
    }

    private companion object {
        val KBPS = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    }
}
