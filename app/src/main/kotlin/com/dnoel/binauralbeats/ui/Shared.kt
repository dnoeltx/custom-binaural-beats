package com.dnoel.binauralbeats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnoel.binauralbeats.R

/** One offered pitch, with the plain-language name shown beside it. */
data class PresetChoice(val hz: Double, val labelResId: Int)

@Composable
fun PresetPicker(
    presets: List<PresetChoice>,
    selected: Double,
    enabled: Boolean,
    onSelect: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringRes(R.string.pick_a_pitch),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        for (preset in presets) {
            FilterChip(
                selected = selected == preset.hz,
                onClick = { onSelect(preset.hz) },
                enabled = enabled,
                label = { Text("${stringRes(preset.labelResId)}  ${preset.hz.toInt()} Hz") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text = stringRes(R.string.action_start), fontSize = 20.sp)
    }
}

/**
 * T041: headphones are required for a binaural beat to exist at all, so their absence is
 * worth saying plainly. It is a warning, not a block: the listener may be about to put
 * them in, and an app that refuses to start is more annoying than one that mentions it.
 */
@Composable
fun OutputHint(warning: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (warning) {
            stringRes(R.string.headphones_missing)
        } else {
            stringRes(R.string.headphones_required)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
