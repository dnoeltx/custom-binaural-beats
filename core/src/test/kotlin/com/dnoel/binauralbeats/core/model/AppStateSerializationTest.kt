package com.dnoel.binauralbeats.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T016: round trip, and the rules that matter more than the round trip.
 *
 * Choosing DataStore over Room (research R2) made schema evolution our own problem. The
 * rule recorded there: a read that fails or finds an unknown version returns defaults
 * and MUST NOT destroy what is stored. Losing a calibration to a bad parse would be the
 * worst bug this app could have, because recovering it costs the listener another
 * twelve-minute sitting.
 */
class AppStateSerializationTest {

    private val fullState = AppState(
        profile = ListenerProfile(
            lowHz = 150.0,
            highHz = 260.0,
            source = ProfileSource.CALIBRATED,
            createdAtEpochMillis = 1_726_000_000_000L,
        ),
        settings = SessionConfiguration(
            beatArc = BeatArc.DESCEND_THEN_VARY,
            endBehavior = EndBehavior.AtClockTime(TimeOfDay(6, 30)),
            carrierCount = 2,
            volumeWarningAcknowledged = true,
        ),
        calibrationInProgress = CalibrationSession(
            id = "cal-1",
            startedAtEpochMillis = 1_726_000_100_000L,
            beatRateHz = 3.0,
            judgments = listOf(
                ToneJudgment(180.0, Verdict.RELAXING, 1_726_000_110_000L, 4_200L),
                ToneJudgment(420.0, Verdict.NOT_RELAXING, 1_726_000_130_000L, 2_900L),
            ),
        ),
        lastSession = SessionRecord(
            startedAtEpochMillis = 1_725_900_000_000L,
            endedAtEpochMillis = 1_725_930_000_000L,
            endReason = EndReason.STOPPED_BY_LISTENER,
            configuration = SessionConfiguration(),
            profile = ListenerProfile(200.0, 260.0, ProfileSource.PRESET, 1L),
            renderSeed = 987_654_321L,
            carrierSummary = listOf(205.0, 232.0, 251.0),
        ),
    )

    @Test
    fun `a round trip preserves every field`() {
        val decoded = AppStateSerialization.decode(AppStateSerialization.encode(fullState))
        assertEquals(fullState, decoded)
    }

    @Test
    fun `a round trip preserves each end behavior variant`() {
        for (behavior in listOf(
            EndBehavior.RunUntilStopped,
            EndBehavior.AfterDuration(8 * 60 * 60 * 1000L),
            EndBehavior.AtClockTime(TimeOfDay(5, 45)),
        )) {
            val state = AppState(settings = SessionConfiguration(endBehavior = behavior))
            assertEquals(behavior, AppStateSerialization.decode(AppStateSerialization.encode(state)).settings.endBehavior)
        }
    }

    @Test
    fun `an empty payload yields defaults`() {
        val decoded = AppStateSerialization.decode("")
        assertEquals(AppState(), decoded)
        assertNull(decoded.profile)
    }

    @Test
    fun `a corrupt payload yields defaults rather than throwing`() {
        val decoded = AppStateSerialization.decode("{ this is not json")
        assertEquals(AppState(), decoded)
    }

    @Test
    fun `a payload from a future schema version yields defaults`() {
        val future = AppStateSerialization.encode(fullState)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val decoded = AppStateSerialization.decode(future)
        assertEquals(AppState.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertNull(decoded.profile, "a future version must not be interpreted as current")
    }

    @Test
    fun `decoding never reports whether the stored bytes should be discarded`() {
        // The port contract says a failed read returns defaults; it must not offer any
        // signal that invites the caller to overwrite the file it could not parse.
        val result = AppStateSerialization.decodeWithOutcome("{ broken")
        assertEquals(AppState(), result.state)
        assertTrue(result.usedFallback)
        assertNotNull(result.problem)
    }

    @Test
    fun `an unknown field in stored data is ignored rather than fatal`() {
        // A newer build that added a field, then a downgrade. Losing the profile here
        // would be a real failure for the listener.
        val withExtra = AppStateSerialization.encode(fullState)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"somethingNew\":42")
        val decoded = AppStateSerialization.decode(withExtra)
        assertEquals(fullState.profile, decoded.profile)
    }
}
