package com.dnoel.binauralbeats.core.playback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T041: what counts as an output this app may play through.
 *
 * The rule, not the plumbing. A binaural beat is a separate tone in each ear, so it does
 * not exist on a speaker, and playing to one at 3am would also wake whoever else is in
 * the room. Kept in :core and tested because this is the list most likely to be edited
 * later by someone adding a device type, and a speaker sneaking into it would break both
 * the product and the person asleep next to it.
 */
class OutputSuitabilityTest {

    @Test
    fun `headphones of every wired kind are suitable`() {
        assertTrue(OutputSuitability.isSuitable(OutputKind.WIRED_HEADPHONES))
        assertTrue(OutputSuitability.isSuitable(OutputKind.WIRED_HEADSET))
        assertTrue(OutputSuitability.isSuitable(OutputKind.USB_HEADSET))
    }

    @Test
    fun `bluetooth headphones are suitable, in both the classic and low energy forms`() {
        // The Ozlo Sleepbuds arrive as one or the other depending on how the case has
        // connected, so accepting only one of them would fail on a real pair of earbuds.
        assertTrue(OutputSuitability.isSuitable(OutputKind.BLUETOOTH_A2DP))
        assertTrue(OutputSuitability.isSuitable(OutputKind.BLE_HEADSET))
    }

    @Test
    fun `a speaker is never suitable`() {
        assertFalse(OutputSuitability.isSuitable(OutputKind.BUILTIN_SPEAKER))
        assertFalse(OutputSuitability.isSuitable(OutputKind.BLUETOOTH_SPEAKER))
    }

    @Test
    fun `an earpiece is never suitable`() {
        // One ear only, which is half a binaural beat and no use at all.
        assertFalse(OutputSuitability.isSuitable(OutputKind.BUILTIN_EARPIECE))
    }

    @Test
    fun `anything unrecognised is refused rather than assumed safe`() {
        assertFalse(OutputSuitability.isSuitable(OutputKind.OTHER))
    }

    @Test
    fun `a set of outputs is suitable when any one of them is`() {
        assertTrue(
            OutputSuitability.anySuitable(listOf(OutputKind.BUILTIN_SPEAKER, OutputKind.BLE_HEADSET))
        )
        assertFalse(
            OutputSuitability.anySuitable(listOf(OutputKind.BUILTIN_SPEAKER, OutputKind.OTHER))
        )
        assertFalse(OutputSuitability.anySuitable(emptyList()))
    }
}
