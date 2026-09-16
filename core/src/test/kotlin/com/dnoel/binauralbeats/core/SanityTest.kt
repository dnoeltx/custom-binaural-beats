package com.dnoel.binauralbeats.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * T007: proves the JVM test path runs with no device and no Android dependency.
 * Replaced by real tests as the core module grows.
 */
class SanityTest {

    @Test
    fun `core module runs tests on the JVM`() {
        assertEquals(4, 2 + 2)
    }
}
