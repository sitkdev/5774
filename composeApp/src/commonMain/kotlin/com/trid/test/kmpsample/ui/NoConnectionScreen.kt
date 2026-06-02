package com.trid.test.kmpsample.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trid.test.kmpsample.ui.theme.AppAccent
import com.trid.test.kmpsample.ui.theme.AppGradients
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.outline_wifi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Duration of the retry lockout window: rapid taps within this window after an
 * accepted tap are ignored so [NoConnectionScreen]'s `onReconnect` fires once.
 */
private const val RETRY_LOCKOUT_MS = 500L

/**
 * Full-screen "no connection" state for the collecting app.
 *
 * Shows an animated gold wifi-off glyph (slow pulse plus an expanding signal
 * ring that fades out), a thematic Cinzel heading with a calmer Nunito Sans
 * subtitle, and a gold retry button. The button has a press scale animation and
 * a [RETRY_LOCKOUT_MS] debounce so [onReconnect] cannot double-fire on rapid
 * taps.
 *
 * Centered with `fillMaxSize`; the parent [com.trid.test.kmpsample.ui.theme.AppTheme]
 * Surface already provides the obsidian background and safe-area insets.
 */
@Composable
fun NoConnectionScreen(onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LostSignalIcon()

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Connection lost!",
            style = MaterialTheme.typography.headlineSmall,
            color = AppAccent.Gold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Check your connection and try again",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        RetryButton(onRetry = onReconnect)
    }
}

/**
 * Animated lost-signal indicator: a soft gold ring expands outward and fades
 * behind the wifi-off glyph, while the glyph itself gently pulses in scale and
 * alpha. Purely decorative; the screen's heading carries the meaning.
 */
@Composable
private fun LostSignalIcon() {
    val transition = rememberInfiniteTransition()

    // Expanding ring: 0f -> 1f, restart, suggesting a signal ping that never lands.
    val ringProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    // Gentle breathing pulse of the glyph.
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val glyphScale = 0.94f + 0.06f * pulse
    val glyphAlpha = 0.6f + 0.4f * pulse

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            // Single expanding ring; fades as it grows.
            val radius = maxRadius * ringProgress
            val ringAlpha = (1f - ringProgress).coerceIn(0f, 1f)
            if (ringAlpha > 0f && radius > 0f) {
                drawCircle(
                    color = AppAccent.Gold.copy(alpha = ringAlpha * 0.6f),
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        val icon = painterResource(Res.drawable.outline_wifi)
        Image(
            painter = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(AppAccent.Gold),
            modifier = Modifier
                .size(120.dp)
                .scale(glyphScale)
                .alpha(glyphAlpha)
        )
    }
}

/**
 * Gold gradient retry button with a press scale-down animation and a
 * [RETRY_LOCKOUT_MS] debounce: the first tap fires [onRetry] immediately, then
 * the button is disabled until the lockout elapses, re-enabling automatically.
 */
@Composable
private fun RetryButton(onRetry: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    var locked by remember { mutableStateOf(false) }

    // While locked, wait out the window then re-enable. Scoped to this composable,
    // so removal (e.g. navigation away) cancels it without leaking.
    LaunchedEffect(locked) {
        if (locked) {
            delay(RETRY_LOCKOUT_MS)
            locked = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed && !locked) 0.95f else 1f,
        animationSpec = spring(),
    )

    Button(
        onClick = {
            if (!locked) {
                locked = true
                onRetry()
            }
        },
        enabled = !locked,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        modifier = Modifier
            .scale(scale)
            .wrapContentSize(),
    ) {
        Box(
            modifier = Modifier
                .background(brush = AppGradients.Primary)
                .alpha(if (locked) 0.5f else 1f)
                .padding(horizontal = 40.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
