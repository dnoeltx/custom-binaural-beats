package com.dnoel.binauralbeats.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * T205c now, T217 later.
 *
 * Today this records what the engine sounds like before breathing exists. Once breathing
 * lands, the same test proves SC-107: turning it off reproduces feature 001's audio
 * exactly, rather than approximately.
 *
 * To re-record deliberately, after a change that is meant to alter the audio:
 *
 *     ./gradlew :core:test -DcaptureReference=true --tests "*PreBreathingReferenceTest*"
 *
 * That flag exists so re-recording is always a decision someone made and can be seen in a
 * diff, never something that happens because a test was failing.
 */
class PreBreathingReferenceTest {

    private val fixture = File("src/test/resources/pre-breathing-reference.json")

    @Test
    fun `audio with breathing off matches the recorded reference`() {
        val current = ReferenceDigest.digests()

        if (System.getProperty("captureReference") == "true") {
            fixture.parentFile.mkdirs()
            fixture.writeText(render(current))
            println("Recorded ${current.size} reference digests to ${fixture.path}")
            return
        }

        assertTrue(
            fixture.exists(),
            "No reference fixture at ${fixture.path}. Run with -DcaptureReference=true to record one, " +
                "but only if you intend to change what the engine sounds like.",
        )

        val recorded = parse(fixture.readText())
        for ((case, digest) in recorded) {
            assertEquals(
                digest,
                current[case],
                "The audio for $case changed. If that was intended, re-record deliberately; " +
                    "if breathing is off, SC-107 says it should not have changed at all.",
            )
        }
        assertEquals(recorded.keys, current.keys, "The set of recorded cases changed")
    }

    /** Hand rolled so this test needs no serialization setup of its own. */
    private fun render(digests: Map<String, String>): String =
        digests.entries.sortedBy { it.key }.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n}\n",
        ) { "  \"${it.key}\": \"${it.value}\"" }

    private fun parse(text: String): Map<String, String> =
        Regex("\"([^\"]+)\"\\s*:\\s*\"([0-9a-f]+)\"")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
}
