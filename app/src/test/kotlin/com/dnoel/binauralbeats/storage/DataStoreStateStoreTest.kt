package com.dnoel.binauralbeats.storage

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T018: the Android side of persistence.
 *
 * The encoding itself is covered on the JVM in AppStateSerializationTest. What needs a
 * platform test is the DataStore wiring: that a write survives a process restart, that
 * an unreadable file yields defaults WITHOUT the stored bytes being destroyed (research
 * R2), and that asking for the store twice does not crash.
 *
 * Robolectric is pinned to SDK 34 here: on the newest emulated SDK it fails inside
 * ApplicationSharedMemory with "Failed to interact with raw FileDescriptor internals",
 * which is a Robolectric and JDK interaction, not anything about this code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreStateStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() = runTest { DataStoreStateStore.simulateProcessRestart() }

    @Test
    fun `writes and reads back a profile`() = runTest {
        val store = DataStoreStateStore.forFile(newFile())

        val state = AppState(
            profile = ListenerProfile(150.0, 260.0, ProfileSource.CALIBRATED, 42L),
            settings = SessionConfiguration(carrierCount = 2),
        )
        store.write(state)

        assertEquals(state, store.read())
    }

    @Test
    fun `asking for the store twice returns the same instance rather than crashing`() {
        // DataStore throws if two instances are active over one file, and both the
        // playback service and the UI need state. This is the guard against that.
        val file = newFile()
        assertSame(DataStoreStateStore.forFile(file), DataStoreStateStore.forFile(file))
    }

    @Test
    fun `a value written by one process is visible to the next`() = runTest {
        val file = newFile()
        val state = AppState(profile = ListenerProfile(180.0, 240.0, ProfileSource.PRESET, 7L))

        DataStoreStateStore.forFile(file).write(state)
        DataStoreStateStore.simulateProcessRestart()

        assertEquals(state.profile, DataStoreStateStore.forFile(file).read().profile)
    }

    @Test
    fun `a simulated restart waits for the old store to actually stop`() = runTest {
        // The regression test for the 2026-09-16 CI failure. simulateProcessRestart used
        // to call cancel(), which only REQUESTS cancellation; DataStore keeps its claim
        // on the file until the work finishes. Opening the next store inside that window
        // throws "There are multiple DataStores active for the same file".
        //
        // HONEST LIMITATION, verified 2026-09-16: neither this test nor the 20 cycle one
        // below could be proven non-vacuous on a developer machine. Removing the join and
        // re-running left both of them green, because the race does not reproduce here
        // and `isCompleted` turns true quickly once the work has nothing left to do.
        // The fix is still right (cancel requests, join confirms), but the evidence for
        // it is CI staying green, not a local mutation. Do not claim otherwise.
        val file = newFile()
        val store = DataStoreStateStore.forFile(file)
        store.write(AppState(profile = ListenerProfile(150.0, 260.0, ProfileSource.CALIBRATED, 1L)))

        assertFalse("a live store must not report itself stopped", store.isStopped)

        DataStoreStateStore.simulateProcessRestart()

        assertTrue(
            "simulateProcessRestart must not return until the old store has finished",
            store.isStopped,
        )
    }

    @Test
    fun `repeated restarts over one file never collide`() = runTest {
        val file = newFile()
        repeat(20) { cycle ->
            val store = DataStoreStateStore.forFile(file)
            store.write(
                AppState(
                    profile = ListenerProfile(150.0 + cycle, 260.0, ProfileSource.CALIBRATED, cycle.toLong())
                )
            )
            assertEquals(150.0 + cycle, store.read().profile!!.lowHz, 0.0001)
            DataStoreStateStore.simulateProcessRestart()
        }
    }

    @Test
    fun `a fresh install reads defaults`() = runTest {
        assertEquals(AppState(), DataStoreStateStore.forFile(newFile()).read())
    }

    @Test
    fun `a corrupt file yields defaults and is left on disk`() = runTest {
        val file = newFile()
        DataStoreStateStore.forFile(file)
            .write(AppState(profile = ListenerProfile(150.0, 260.0, ProfileSource.CALIBRATED, 1L)))
        DataStoreStateStore.simulateProcessRestart()

        file.writeText("not the json you are looking for")
        val sizeBefore = file.length()

        val recovered = DataStoreStateStore.forFile(file).read()

        assertEquals(AppState(), recovered)
        assertTrue("the unreadable file must not be deleted", file.exists())
        assertEquals("the unreadable file must not be rewritten on read", sizeBefore, file.length())
    }

    @Test
    fun `writing after a corrupt read replaces the file cleanly`() = runTest {
        val file = newFile()
        file.writeText("garbage")

        val store = DataStoreStateStore.forFile(file)
        assertEquals(AppState(), store.read())

        val fresh = AppState(profile = ListenerProfile(200.0, 300.0, ProfileSource.MANUAL, 9L))
        store.write(fresh)
        DataStoreStateStore.simulateProcessRestart()

        assertEquals(fresh, DataStoreStateStore.forFile(file).read())
        assertNotNull(DataStoreStateStore.forFile(file).read().profile)
    }

    private fun newFile(): File = File(temporaryFolder.newFolder(), "state.json")
}
