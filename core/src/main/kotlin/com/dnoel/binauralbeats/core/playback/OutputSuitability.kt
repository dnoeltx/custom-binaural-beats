package com.dnoel.binauralbeats.core.playback

/**
 * The kinds of audio output this app cares about, named without reference to any
 * platform's constants so the rule below stays testable off-device and portable.
 */
enum class OutputKind {
    WIRED_HEADPHONES,
    WIRED_HEADSET,
    USB_HEADSET,
    BLUETOOTH_A2DP,
    BLE_HEADSET,
    BLUETOOTH_SPEAKER,
    BUILTIN_SPEAKER,
    BUILTIN_EARPIECE,

    /** Anything the platform reports that this list does not name. */
    OTHER,
}

/**
 * T041: whether a session may play through a given output.
 *
 * Headphones only. A binaural beat is a separate tone in each ear, so it does not exist
 * on a speaker, and a speaker at 3am wakes whoever else is in the room. An earpiece is
 * refused for the same reason as a speaker: one ear is half a binaural beat.
 *
 * Unrecognised outputs are refused rather than assumed safe, so a new device type has to
 * be considered deliberately rather than inherited by accident.
 */
object OutputSuitability {

    private val suitable = setOf(
        OutputKind.WIRED_HEADPHONES,
        OutputKind.WIRED_HEADSET,
        OutputKind.USB_HEADSET,
        OutputKind.BLUETOOTH_A2DP,
        OutputKind.BLE_HEADSET,
    )

    fun isSuitable(kind: OutputKind): Boolean = kind in suitable

    fun anySuitable(kinds: List<OutputKind>): Boolean = kinds.any(::isSuitable)
}
