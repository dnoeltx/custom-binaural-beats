package com.dnoel.binauralbeats.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.AppStateSerialization
import com.dnoel.binauralbeats.core.ports.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * T019: the Android implementation of [StateStore], over DataStore (research R2).
 *
 * All encoding lives in :core, so the rules about corrupt and future-versioned payloads
 * are tested on the JVM and cannot drift between platforms. This class is deliberately
 * thin: open a file, read bytes, write bytes.
 *
 * **One instance per file, process-wide.** DataStore throws IllegalStateException if two
 * of its instances are active over the same file. The playback service and the UI both
 * need state, so the naive "construct one where you need it" would crash the second
 * caller. [forFile] hands back the same instance instead. This was found by T018 rather
 * than at runtime on the phone.
 */
class DataStoreStateStore private constructor(
    file: File,
    private val scope: CoroutineScope,
) : StateStore {

    private val dataStore: DataStore<AppState> = DataStoreFactory.create(
        serializer = AppStateJsonSerializer,
        scope = scope,
        produceFile = { file },
    )

    override suspend fun read(): AppState = dataStore.data.first()

    override suspend fun write(state: AppState) {
        dataStore.updateData { state }
    }

    private object AppStateJsonSerializer : Serializer<AppState> {

        override val defaultValue: AppState = AppState()

        override suspend fun readFrom(input: InputStream): AppState {
            // Never throws. An unreadable payload becomes defaults and the bytes on disk
            // are left exactly as they are, so nothing is destroyed by a failed read.
            val payload = input.readBytes().decodeToString()
            return AppStateSerialization.decode(payload)
        }

        override suspend fun writeTo(t: AppState, output: OutputStream) {
            output.write(AppStateSerialization.encode(t).encodeToByteArray())
        }
    }

    companion object {
        const val DEFAULT_FILE_NAME: String = "app_state.json"

        private val instances = mutableMapOf<String, DataStoreStateStore>()

        /** The one store for [file], creating it on first use. */
        @Synchronized
        fun forFile(file: File): DataStoreStateStore = instances.getOrPut(file.absolutePath) {
            DataStoreStateStore(file, CoroutineScope(Dispatchers.IO + SupervisorJob()))
        }

        fun forContext(context: Context): DataStoreStateStore =
            forFile(File(context.filesDir, DEFAULT_FILE_NAME))

        /**
         * Drops every open store, which is what a process restart does. Tests use it to
         * prove that data written by one process is read back by the next.
         */
        @Synchronized
        fun simulateProcessRestart() {
            instances.values.forEach { it.scope.cancel() }
            instances.clear()
        }
    }
}
