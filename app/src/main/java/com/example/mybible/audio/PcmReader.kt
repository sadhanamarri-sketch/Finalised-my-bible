package com.example.mybible.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A stretch of a sound file's sound, decoded by the phone — an MP3, or a video's or an M4A's sound — and
 * handed over a piece at a time, mixed into one channel (-1 to 1), with how many samples a second: to
 * measure how loud a recording is (see RecordingLoudness). Nothing is played.
 */
object PcmReader {
    /** The sound of [uri] from [from] to [to] seconds, to [onPcm]; false when it couldn't be read. */
    fun read(context: Context, uri: Uri, from: Double, to: Double, onPcm: (FloatArray, Int) -> Unit): Boolean =
        read({ extractor ->
            // The extractor keeps its own hold on the file: this one can be closed at once.
            context.contentResolver.openFileDescriptor(uri, "r")?.use { extractor.setDataSource(it.fileDescriptor) } != null
        }, from, to, onPcm)

    /** The same, the file given to the extractor by [open] (false: it couldn't be). */
    internal fun read(open: (MediaExtractor) -> Boolean, from: Double, to: Double, onPcm: (FloatArray, Int) -> Unit): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            if (!open(extractor)) return false
            val track = (0 until extractor.trackCount)
                .firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return false).also { decoder = it }
            codec.configure(format, null, null, 0)
            codec.start()
            val fromUs = (from * 1_000_000).toLong()
            val toUs = (to * 1_000_000).toLong()
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floats = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var turns = 0
            while (turns++ < MAX_TURNS) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(WAIT_US)
                    if (index >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(index) ?: return false, 0)
                        if (size < 0 || extractor.sampleTime > toUs) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, WAIT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floats = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (index >= 0) {
                    val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0 && info.presentationTimeUs in fromUs..toUs) {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            onPcm(mono(buffer.order(ByteOrder.nativeOrder()), channels, floats), rate)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (ended) break
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            decoder?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    // Never started.
                }
                it.release()
            }
            extractor.release()
        }
    }

    // A piece of decoded sound, its channels mixed into one.
    private fun mono(buffer: ByteBuffer, channels: Int, floats: Boolean): FloatArray {
        val count = channels.coerceAtLeast(1)
        return if (floats) {
            val samples = buffer.asFloatBuffer()
            FloatArray(samples.remaining() / count) { i ->
                var sum = 0f
                for (c in 0 until count) sum += samples.get(i * count + c)
                sum / count
            }
        } else {
            val samples = buffer.asShortBuffer()
            FloatArray(samples.remaining() / count) { i ->
                var sum = 0f
                for (c in 0 until count) sum += samples.get(i * count + c) / 32768f
                sum / count
            }
        }
    }

    // How long to wait for the decoder each time round, and how many times round at most (a stretch of
    // 20 seconds takes about a thousand).
    private const val WAIT_US = 10_000L
    private const val MAX_TURNS = 20_000
}
