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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dnoel.binauralbeats.R
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource

/**
 * T038: the screen for every night after the first (FR-008).
 *
 * One job: start tonight's session in as few taps as possible. A saved profile is shown
 * in plain language rather than as numbers, since "your range" means more at bedtime than
 * "150 to 400 Hz". Someone without a profile picks a preset here, exactly as on the
 * welcome screen.
 */
@Composable
fun HomeScreen(
    profile: ListenerProfile?,
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
        if (profile != null) {
            Text(
                text = stringRes(R.string.your_sound),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = profile.describe(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            PresetPicker(presets = presets, selected = selected, enabled = true, onSelect = onSelect)
        }

        StartButton(
            onClick = onStart,
            modifier = Modifier.padding(top = 32.dp).fillMaxWidth().height(72.dp),
        )

        OutputHint(warning = outputWarning, modifier = Modifier.padding(top = 20.dp))
    }
}

/** Plain language, because numbers are for the settings screen, not for bedtime. */
@Composable
private fun ListenerProfile.describe(): String {
    val centre = ((lowHz + highHz) / 2).toInt()
    return when (source) {
        ProfileSource.CALIBRATED -> stringRes(R.string.profile_calibrated).format(centre)
        ProfileSource.PRESET -> stringRes(R.string.profile_preset).format(centre)
        ProfileSource.MANUAL -> stringRes(R.string.profile_manual).format(centre)
    }
}
