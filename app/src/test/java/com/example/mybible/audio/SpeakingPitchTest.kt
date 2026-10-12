package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SpeakingPitchTest {

    // Something like a voice at [hz]: a tone with its first twelve overtones, its pitch wavering a
    // little, in syllables four a second with a short pause after each.
    private fun voice(hz: Double, sampleRate: Int = 24000, seconds: Double = 2.0): FloatArray {
        val out = FloatArray((sampleRate * seconds).toInt())
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            phase += 2 * PI * hz * (1 + 0.02 * sin(2 * PI * 3 * t)) / sampleRate
            val syllable = (t * 4) % 1.0
            val loudness = if (syllable < 0.7) sin(PI * syllable / 0.7) else 0.0
            var sound = 0.0
            for (k in 1..12) sound += sin(k * phase + k) / k
            out[i] = (0.25 * loudness * sound).toFloat()
        }
        return out
    }

    private fun pitchOf(hz: Double, sampleRate: Int = 24000) = SpeakingPitch.of(SpeakingPitch.Pcm(voice(hz, sampleRate), sampleRate))!!

    private fun assertNear(expected: Double, actual: Float) =
        assertTrue("expected about $expected, was $actual", kotlin.math.abs(actual - expected) / expected < 0.04)

    @Test
    fun aMansVoiceAndAWomans() {
        assertNear(110.0, pitchOf(110.0))
        assertNear(220.0, pitchOf(220.0))
        assertNear(130.0, pitchOf(130.0, sampleRate = 16000))
        assertNear(200.0, pitchOf(200.0, sampleRate = 44100))
    }

    @Test
    fun deeperVoicesComeOutLower() {
        val pitches = listOf(220.0, 110.0, 165.0).map { pitchOf(it) }
        assertTrue(pitches[1] < pitches[2] && pitches[2] < pitches[0])
    }

    @Test
    fun noiseOrSilenceHasNoPitch() {
        val random = Random(7)
        assertNull(SpeakingPitch.of(SpeakingPitch.Pcm(FloatArray(48000) { random.nextFloat() - 0.5f }, 24000)))
        assertNull(SpeakingPitch.of(SpeakingPitch.Pcm(FloatArray(48000), 24000)))
        assertNull(SpeakingPitch.of(SpeakingPitch.Pcm(FloatArray(100), 24000)))
    }

    // A WAV file of [samples] in [channels] channels (each the same), 16-bit; its sound's length left
    // out, as a file written while it's spoken can have it.
    private fun wav(samples: FloatArray, sampleRate: Int, channels: Int = 1, sizeKnown: Boolean = true): ByteArray {
        val data = samples.size * channels * 2
        val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate)
            .putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        buffer.put("data".toByteArray()).putInt(if (sizeKnown) data else 0)
        for (s in samples) repeat(channels) { buffer.putShort((s * 32767).toInt().toShort()) }
        return buffer.array()
    }

    @Test
    fun aWavFileIsRead() {
        val sound = voice(120.0, 22050)
        val pcm = SpeakingPitch.readWav(wav(sound, 22050))!!
        assertEquals(22050, pcm.sampleRate)
        assertEquals(sound.size, pcm.samples.size)
        assertEquals(sound[1000], pcm.samples[1000], 0.001f)
        // Two channels mixed into one, and a length the header doesn't give.
        val stereo = SpeakingPitch.readWav(wav(sound, 22050, channels = 2, sizeKnown = false))!!
        assertEquals(sound.size, stereo.samples.size)
        assertEquals(sound[1000], stereo.samples[1000], 0.001f)

        val file = File.createTempFile("voice", ".wav").apply { deleteOnExit() }
        file.writeBytes(wav(sound, 22050))
        assertNear(120.0, SpeakingPitch.of(file)!!)
        assertNull(SpeakingPitch.readWav("not a wav file at all".toByteArray()))
        assertNull(SpeakingPitch.of(File("/no/such/file.wav")))
    }
}
