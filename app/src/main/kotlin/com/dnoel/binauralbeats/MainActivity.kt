package com.dnoel.binauralbeats

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.playback.SessionReconciler
import com.dnoel.binauralbeats.core.session.SessionTuning
import com.dnoel.binauralbeats.core.ui.Screen
import com.dnoel.binauralbeats.core.ui.ScreenRouter
import com.dnoel.binauralbeats.playback.OutputWatcher
import com.dnoel.binauralbeats.playback.SessionService
import com.dnoel.binauralbeats.storage.DataStoreStateStore
import com.dnoel.binauralbeats.ui.HomeScreen
import com.dnoel.binauralbeats.ui.PresetChoice
import com.dnoel.binauralbeats.ui.SessionScreen
import com.dnoel.binauralbeats.ui.WelcomeScreen
import kotlinx.coroutines.launch

/**
 * Hosts the three screens. Which one appears is decided by [ScreenRouter] in :core, so the
 * rule is testable without a device.
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
                    AppScreens()
                }
            }
        }
    }
}

@Composable
private fun AppScreens() {
    val context = LocalContext.current
    val tuning = SessionTuning.MEASURED
    val presets = remember(tuning) {
        val labels = listOf(R.string.preset_low, R.string.preset_medium, R.string.preset_high)
        tuning.presetPitchesHz.mapIndexed { index, hz ->
            PresetChoice(hz, labels.getOrElse(index) { R.string.preset_medium })
        }
    }

    var state by remember { mutableStateOf(AppState()) }
    var sessionRunning by remember { mutableStateOf(SessionService.isRunning) }
    var selected by remember { mutableStateOf(tuning.defaultPresetHz) }
    var outputConnected by remember { mutableStateOf(true) }

    // Everything here can change while the screen is away: a session can end on its own
    // when the output is lost, and headphones come and go. Re-reading on resume keeps the
    // screen from describing a state that has passed (FR-025).
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { DataStoreStateStore.forContext(context.applicationContext) }
    var refreshCount by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshCount) {
        sessionRunning = SessionService.isRunning
        state = store.read()
        outputConnected = OutputWatcher(
            context = context.applicationContext,
            grace = tuning.outputLossGrace,
            onAction = { },
        ).anySuitableOutputConnected()
    }

    when (ScreenRouter.screenFor(state, sessionRunning)) {
        Screen.SESSION -> SessionScreen(
            onStop = {
                SessionService.stop(context)
                sessionRunning = false
            }
        )

        Screen.WELCOME -> WelcomeScreen(
            presets = presets,
            selected = selected,
            onSelect = { selected = it },
            onStart = {
                SessionService.start(context, selected)
                sessionRunning = true
            },
            outputWarning = !outputConnected,
        )

        Screen.HOME -> HomeScreen(
            profile = state.profile,
            presets = presets,
            selected = selected,
            onSelect = { selected = it },
            onStart = {
                SessionService.start(context, selected)
                sessionRunning = true
            },
            outputWarning = !outputConnected,
        )
    }
}
