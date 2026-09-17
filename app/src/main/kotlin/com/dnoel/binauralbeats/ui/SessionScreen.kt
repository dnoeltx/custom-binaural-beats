package com.dnoel.binauralbeats.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dnoel.binauralbeats.R
import kotlinx.coroutines.delay

/**
 * T040: the screen while a session plays (FR-017).
 *
 * Black, and it stays black. One dim control appears on a tap and hides itself again,
 * because a lit screen in a dark room at 3am is the same failure as a sudden sound: it
 * asks for attention. No clock, no elapsed time, no progress, nothing bright.
 *
 * This screen deliberately does not keep the display awake. The phone locks normally, and
 * the notification carries the stop control for a locked phone (FR-015, FR-016).
 */
@Composable
fun SessionScreen(onStop: () -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }

    // Hide the status and navigation bars for the duration. The lock screen covers them
    // soon enough, but the first minute is exactly when the listener is settling, and a
    // clock and a row of icons are the only lit thing left on an otherwise black screen.
    // Restored on the way out, so the rest of the app behaves normally.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Hide again on its own, so a tap to check on it does not leave the room lit.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(CONTROL_VISIBLE_MILLIS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = true },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .alpha(if (controlsVisible) DIM_ALPHA else 0f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringRes(R.string.session_playing),
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            TextButton(
                onClick = onStop,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(R.string.action_stop),
                    color = Color.White,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

/** Visible, readable in the dark, and nowhere near bright enough to wake anyone. */
private const val DIM_ALPHA = 0.45f
private const val CONTROL_VISIBLE_MILLIS = 6_000L
