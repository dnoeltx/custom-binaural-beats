package com.dnoel.binauralbeats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dnoel.binauralbeats.R

/**
 * T039: the first screen someone ever sees (FR-023).
 *
 * It says what the app does, that headphones are needed, and then gets out of the way.
 * Calibration is not offered yet because it does not exist: US2 adds both the feature and
 * its entry point here, rather than shipping a visible dead end in the meantime.
 *
 * Starting is never blocked on calibrating. The first night should cost nothing more than
 * picking a pitch.
 */
@Composable
fun WelcomeScreen(
    presets: List<PresetChoice>,
    selected: Double,
    onSelect: (Double) -> Unit,
    onStart: () -> Unit,
    outputWarning: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringRes(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringRes(R.string.welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        Column(modifier = Modifier.padding(top = 32.dp).fillMaxWidth()) {
            PresetPicker(presets = presets, selected = selected, enabled = true, onSelect = onSelect)
        }

        StartButton(
            onClick = onStart,
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(72.dp),
        )

        OutputHint(warning = outputWarning, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
internal fun stringRes(id: Int): String = LocalContext.current.getString(id)
