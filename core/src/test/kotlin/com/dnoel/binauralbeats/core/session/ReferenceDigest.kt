package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.audio.SessionRenderer
import com.dnoel.binauralbeats.core.model.BeatArc
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import java.security.MessageDigest

/**
 * T205c: a fingerprint of the audio this engine produces today, before breathing exists.
 *
 * SC-107 requires that turning breathing off reproduces feature 001's audio exactly. That
 * claim can only be checked against a record of what 001 actually produced, and once the
 * scheduler gains breathing the original is gone. So the fingerprint is taken first and
 * stored, and the assertion comes later in T217.
 *
 * Deliberately a digest rather than the audio itself: a few minutes of stereo is megabytes,
 * and what matters is whether a single sample changed, which a hash answers exactly.
 *
 * Uses MessageDigest, which is JVM only. That is acceptable here because this file lives in
 * test sources and never ships, but it must not migrate into main sources (constitution VII).
 */
object ReferenceDigest {

    /** Kept small enough to run on every build, long enough to cover the opening descent. */
    const val SAMPLE_RATE = 8_000
    const val SECONDS = 120

    private val wideProfile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)
    private val narrowProfile = ListenerProfile(150.0, 280.0, ProfileSource.CALIBRATED, 0L)

    /**
     * One entry per arrangement worth protecting. The removed arc is absent on purpose:
     * after this feature it cannot be constructed, so a fingerprint of it could never be
     * checked again.
     */
    fun digests(tuning: SessionTuning = SessionTuning.MEASURED): Map<String, String> {
        val cases = listOf(
            "descend-then-hold/wide/seed-1" to Triple(BeatArc.DESCEND_THEN_HOLD, wideProfile, 1L),
            "descend-then-hold/narrow/seed-7" to Triple(BeatArc.DESCEND_THEN_HOLD, narrowProfile, 7L),
            "constant/wide/seed-1" to Triple(BeatArc.CONSTANT, wideProfile, 1L),
            "constant/narrow/seed-42" to Triple(BeatArc.CONSTANT, narrowProfile, 42L),
        )
        return cases.associate { (name, case) ->
            val (arc, profile, seed) = case
            name to digestOf(arc, profile, seed, tuning)
        }
    }

    private fun digestOf(
        arc: BeatArc,
        profile: ListenerProfile,
        seed: Long,
        tuning: SessionTuning,
    ): String {
        val scheduler = SessionScheduler(profile, SessionConfiguration(beatArc = arc), tuning, seed)
        val renderer = SessionRenderer(scheduler, SAMPLE_RATE)
        val digest = MessageDigest.getInstance("SHA-256")

        val blockFrames = 4_096
        val buffer = FloatArray(blockFrames * 2)
        val totalFrames = SECONDS.toLong() * SAMPLE_RATE
        var at = 0L
        while (at < totalFrames) {
            val frames = minOf(blockFrames.toLong(), totalFrames - at).toInt()
            renderer.render(buffer, frames, startFrame = at)
            // Quantize to 16 bit before hashing. Floating point arithmetic can differ in the
            // last bit between platforms and JDK versions, and a reference that fails on a
            // different machine would be worse than no reference at all.
            for (i in 0 until frames * 2) {
                val sample = (buffer[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                digest.update((sample.toInt() and 0xFF).toByte())
                digest.update(((sample.toInt() shr 8) and 0xFF).toByte())
            }
            at += frames
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
