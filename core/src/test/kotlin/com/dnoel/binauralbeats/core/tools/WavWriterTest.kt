package com.dnoel.binauralbeats.core.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * T015: the render tool exists so every measurement task (M001 to M006) can be judged by
 * listening rather than by argument. It is a test-source utility, not shipped code.
 */
class WavWriterTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes a header a player can read`() {
        val file = File(tempDir.toFile(), "tone.wav")
        WavWriter(file, sampleRate = 44_100, channels = 2).use { writer ->
            writer.write(stereoTone(frames = 44_100), frames = 44_100)
        }

        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("fmt ", String(bytes, 12, 4))
        assertEquals("data", String(bytes, 36, 4))

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, header.getShort(22).toInt(), "channel count")
        assertEquals(44_100, header.getInt(24), "sample rate")
        assertEquals(16, header.getShort(34).toInt(), "bits per sample")

        // 1 second, stereo, 16-bit: 44100 * 2 * 2 bytes of audio.
        assertEquals(44_100 * 2 * 2, header.getInt(40), "data chunk size")
        assertEquals(44 + 44_100 * 2 * 2, bytes.size, "total file size")
    }

    @Test
    fun `sizes in the header match what was actually written across many blocks`() {
        val file = File(tempDir.toFile(), "blocks.wav")
        WavWriter(file, sampleRate = 8_000, channels = 1).use { writer ->
            repeat(10) { writer.write(FloatArray(800), frames = 800) }
        }

        val header = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(8_000 * 2, header.getInt(40), "data chunk size after 10 blocks")
    }

    @Test
    fun `clamps rather than wrapping when a sample exceeds full scale`() {
        val file = File(tempDir.toFile(), "clip.wav")
        WavWriter(file, sampleRate = 8_000, channels = 1).use { writer ->
            writer.write(floatArrayOf(2.0f, -2.0f), frames = 2)
        }

        val audio = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Short.MAX_VALUE, audio.getShort(44))
        assertEquals((-Short.MAX_VALUE).toShort(), audio.getShort(46))
    }

    @Test
    fun `reports how much audio it wrote`() {
        val file = File(tempDir.toFile(), "duration.wav")
        val writer = WavWriter(file, sampleRate = 44_100, channels = 2)
        writer.use { it.write(stereoTone(frames = 22_050), frames = 22_050) }
        assertEquals(22_050L, writer.framesWritten)
        assertTrue(file.length() > 0)
    }

    private fun stereoTone(frames: Int): FloatArray = FloatArray(frames * 2) { i ->
        val frame = i / 2
        (0.3 * sin(2 * PI * 200.0 * frame / 44_100)).toFloat()
    }
}
