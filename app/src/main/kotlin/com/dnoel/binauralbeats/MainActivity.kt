package com.dnoel.binauralbeats

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.dnoel.binauralbeats.core.playback.SessionReconciler
import com.dnoel.binauralbeats.core.session.SessionTuning
import com.dnoel.binauralbeats.playback.SessionService
import com.dnoel.binauralbeats.storage.DataStoreStateStore
import kotlinx.coroutines.launch

/**
 * A deliberately minimal screen: choose a preset pitch, start, stop.
 *
 * This is NOT the interface described in the specification. The welcome flow, the
 * calibration screen and the dim in-session screen (T038 to T040) come next. This exists
 * so the engine can be run on real hardware overnight, which is the thing most likely to
 * be wrong and the thing a Compose layout cannot tell us about.
 */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // T043: if the process was killed mid-session, nothing ran the cleanup, so the
        // stored record still says a session is in progress. Close it honestly before
        // anything reads it.
        lifecycleScope.launch {
            val store = DataStoreStateStore.forContext(applicationContext)
            val stored = store.read()
            val reconciled = SessionReconciler.reconcile(
                state = stored,
                serviceRunning = SessionService.isRunning,
                nowMillis = System.currentTimeMillis(),
            )
            if (reconciled !== stored) store.write(reconciled)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SessionControls()
                }
            }
        }
    }
}

@Composable
private fun SessionControls() {
    val context = LocalContext.current
    val tuning = SessionTuning.MEASURED
    var selected by remember { mutableStateOf(tuning.defaultPresetHz) }
    var running by remember { mutableStateOf(SessionService.isRunning) }

    // T043: the session can end without the screen asking for it, when the output is lost
    // or focus is taken for good. Re-reading on resume keeps the button from claiming a
    // session is playing when none is.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) running = SessionService.isRunning
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.pick_a_pitch),
            style = MaterialTheme.typography.titleMedium,
        )

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            tuning.presetPitchesHz.forEachIndexed { index, hz ->
                val label = when (index) {
                    0 -> stringResource(R.string.preset_low)
                    tuning.presetPitchesHz.lastIndex -> stringResource(R.string.preset_high)
                    else -> stringResource(R.string.preset_medium)
                }
                FilterChip(
                    selected = selected == hz,
                    onClick = { selected = hz },
                    enabled = !running,
                    label = { Text("$label  ${hz.toInt()} Hz") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }

        Button(
            onClick = {
                if (running) {
                    SessionService.stop(context)
                    running = false
                } else {
                    SessionService.start(context, selected)
                    running = true
                }
            },
            modifier = Modifier.fillMaxWidth().size(width = 0.dp, height = 72.dp),
        ) {
            Text(
                text = if (running) stringResource(R.string.action_stop) else stringResource(R.string.action_start),
                fontSize = 20.sp,
            )
        }

        Text(
            text = stringResource(R.string.headphones_required),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun stringResource(id: Int): String = LocalContext.current.getString(id)
