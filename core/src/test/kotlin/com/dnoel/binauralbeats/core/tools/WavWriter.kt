package com.dnoel.binauralbeats.core.tools

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * T015: writes rendered audio to a 16-bit PCM WAV file so it can be listened to.
 *
 * This lives in test sources on purpose. It is the instrument for the measurement tasks
 * (fade lengths, carrier spacing, drift rate), not part of the app, and it is the only
 * place in :core that touches the filesystem.
 *
 * Sizes are patched into the header on close, so audio can be streamed block by block
 * without knowing the length in advance. A full night never needs to fit in memory.
 */
class WavWriter(
    file: File,
    private val sampleRate: Int,
    private val channels: Int,
) : Closeable {

    private val out = RandomAccessFile(file, "rw")
    private var closed = false

    var framesWritten: Long = 0L
        private set

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels >= 1) { "channels must be at least 1" }
        out.setLength(0L)
        out.write(ByteArray(HEADER_BYTES)) // placeholder, patched in close()
    }

    /** Writes [frames] frames of interleaved samples, clamped to full scale. */
    fun write(buffer: FloatArray, frames: Int) {
        check(!closed) { "writer is closed" }
        require(frames * channels <= buffer.size) { "buffer too small for $frames frames" }

        val bytes = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames * channels) {
            val clamped = buffer[i].coerceIn(-1.0f, 1.0f)
            bytes.putShort((clamped * Short.MAX_VALUE).toInt().toShort())
        }
        out.write(bytes.array())
        framesWritten += frames
    }

    override fun close() {
        if (closed) return
        closed = true

        val dataBytes = (framesWritten * channels * 2).toInt()
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)                                  // PCM chunk size
        header.putShort(1)                                 // PCM, uncompressed
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)           // byte rate
        header.putShort((channels * 2).toShort())          // block align
        header.putShort(16)                                // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataBytes)

        out.seek(0L)
        out.write(header.array())
        out.close()
    }

    private companion object {
        const val HEADER_BYTES = 44
    }
}
