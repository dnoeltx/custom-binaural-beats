package com.dnoel.binauralbeats.core.audio

import com.dnoel.binauralbeats.core.session.SessionScheduler

/**
 * T033: the mixer. Sums the session's layers into interleaved stereo.
 *
 * This is the single source of audio for both playback and verification (research R5). A
 * test that rendered through a different path than the phone would prove nothing about
 * the phone.
 *
 * The renderer knows nothing about tones. It zeroes the buffer, asks each [SoundLayer] to
 * add itself, and normalizes by the layer count. Adding generated noise or recorded
 * ambience later is a new layer and nothing else (FR-014).
 *
 * Three properties, each with a test: allocation free once constructed, deterministic
 * including from an arbitrary start frame, and phase continuous.
 */
class SessionRenderer(
    private val layers: List<SoundLayer>,
    private val sampleRate: Int,
) {
    /** Convenience for the only arrangement this feature ships: one tone layer. */
    constructor(scheduler: SessionScheduler, sampleRate: Int) :
        this(listOf(BinauralToneLayer(scheduler)), sampleRate)

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(layers.isNotEmpty()) { "a session needs at least one layer" }
        layers.forEach { it.prepare(sampleRate) }
    }

    private val layerScale = 1.0 / layers.size
    private var nextFrame = -1L

    fun render(into: FloatArray, frames: Int, startFrame: Long) {
        require(frames * 2 <= into.size) { "buffer holds ${into.size / 2} frames, asked for $frames" }

        if (startFrame != nextFrame) {
            layers.forEach { it.seekTo(startFrame) }
        }

        // Kotlin's fill, not java.util.Arrays: this module must stay free of JVM-only
        // APIs so that converting it for iOS remains a build change (constitution VII).
        into.fill(0.0f, fromIndex = 0, toIndex = frames * 2)
        for (layer in layers) {
            layer.addTo(into, frames, startFrame, layerScale)
        }
        nextFrame = startFrame + frames
    }
}
