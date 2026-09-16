package com.dnoel.binauralbeats.core.model

import kotlinx.serialization.json.Json

/**
 * T017: encoding for the single stored object.
 *
 * Choosing DataStore over Room (research R2) means schema evolution is ours to handle.
 * The rules, in order of importance:
 *
 *  1. A payload that cannot be read NEVER propagates an exception and NEVER causes the
 *     stored bytes to be discarded. The caller gets defaults and carries on.
 *  2. A payload from a newer schema version is treated as unreadable rather than
 *     guessed at, because a half-understood profile is worse than none.
 *  3. Unknown fields are ignored, so a downgrade after an upgrade keeps working.
 */
object AppStateSerialization {

    /** The decoded state plus what happened, for logging. Callers usually want [decode]. */
    data class DecodeResult(
        val state: AppState,
        val usedFallback: Boolean,
        val problem: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    fun decode(payload: String): AppState = decodeWithOutcome(payload).state

    fun decodeWithOutcome(payload: String): DecodeResult {
        if (payload.isBlank()) {
            return DecodeResult(AppState(), usedFallback = true, problem = "empty payload")
        }

        val decoded = try {
            json.decodeFromString(AppState.serializer(), payload)
        } catch (e: Exception) {
            // Deliberately broad: any failure to read must end in defaults, never in a
            // crash and never in deleting what is stored.
            return DecodeResult(
                state = AppState(),
                usedFallback = true,
                problem = "could not parse stored state: ${e.message}",
            )
        }

        if (decoded.schemaVersion != AppState.CURRENT_SCHEMA_VERSION) {
            return DecodeResult(
                state = AppState(),
                usedFallback = true,
                problem = "stored schema version ${decoded.schemaVersion} is not " +
                    "${AppState.CURRENT_SCHEMA_VERSION}; ignoring rather than guessing",
            )
        }

        return DecodeResult(decoded, usedFallback = false)
    }
}
