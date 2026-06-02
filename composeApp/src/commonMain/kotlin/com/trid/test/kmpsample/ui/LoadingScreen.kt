package com.trid.test.kmpsample.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.draw.clip
import com.trid.test.kmpsample.ui.theme.AppAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_diamond
import org.jetbrains.compose.resources.painterResource

/**
 * Self-contained "Hero with shimmer" loading screen for the collectibles app.
 *
 * Layers (bottom -> top):
 *  1. Obsidian vertical gradient background.
 *  2. Animated Canvas decoration: pulsing central radial glow, slowly rotating
 *     "godray" beams, and drifting gold bokeh particles.
 *  3. Hero artifact ([Res.drawable.ic_diamond]) with a gentle breathing pulse,
 *     a diagonal gold shimmer sweep masked to the image, and twinkling sparkle
 *     particles orbiting around it.
 *  4. Faux progress bar (0 -> 100) with a two-phase easing (fast start, slow
 *     finish) that holds at 100%, gold gradient fill with a moving highlight.
 *  5. Thematic caption (Cinzel).
 *
 * Purely visual: no parameters, no navigation. The Circuit presenter owns
 * navigation; this composable only animates and then rests at 100%.
 */
@Composable
fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "loading")

    // --- Shared infinite drivers (cheap floats reused across layers) ---

    // 0..1 ramp for the central glow breathing (ping-pong).
    val glowPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    // 0..1 phase for the godray rotation and particle drift (wraps each loop).
    val orbitPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitPhase",
    )

    // 0..1 sweep position for the hero shimmer and the progress-bar highlight.
    val shimmerPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )

    // 0..1 driver for sparkle twinkle (faster ping-pong, offset per particle).
    val sparklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparklePhase",
    )

    // --- Hero breathing pulse (subtle scale) ---
    val heroPulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroPulse",
    )

    // --- Faux two-phase progress: fast to 90%, slow crawl to 100%, then holds.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Phase 1: 0 -> 0.90 quickly (~1.8s), feels responsive.
        progress.animateTo(
            targetValue = 0.90f,
            animationSpec = tween(durationMillis = 18000, easing = LinearEasing),
        )
        // Phase 2: 0.90 -> 1.0 slow crawl (~2.6s), builds anticipation.
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 160000, easing = LinearEasing),
        )
        // Hold at 100% (Animatable simply stays at its last value).
    }

    // Pre-computed bokeh particle seeds (stable across recompositions).
    val bokeh = remember { buildBokeh() }
    val sparkles = remember { buildSparkles() }

    val gold = AppAccent.Gold
    val sand = AppAccent.Sand
    val bronze = AppAccent.Bronze
    val papyrus = AppAccent.Papyrus
    val turquoise = AppAccent.Turquoise

    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface

    // Stable vertical obsidian backdrop brush.
    val backdrop = remember(background, surface) {
        Brush.verticalGradient(listOf(surface, background, background))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 2: animated decorative canvas (glow + godrays + bokeh).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.42f)
            val maxR = size.maxDimension

            // Pulsing central radial glow.
            val glowRadius = maxR * (0.34f + 0.06f * glowPulse)
            val glowAlpha = 0.18f + 0.12f * glowPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        gold.copy(alpha = glowAlpha),
                        gold.copy(alpha = glowAlpha * 0.4f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )

            // Slowly rotating "godray" beams emanating from center.
            val beamCount = 12
            val baseAngle = orbitPhase * 2f * PI.toFloat()
            val beamLen = maxR * 0.9f
            for (i in 0 until beamCount) {
                val a = baseAngle + (i.toFloat() / beamCount) * 2f * PI.toFloat()
                val end = Offset(
                    center.x + cos(a) * beamLen,
                    center.y + sin(a) * beamLen,
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            gold.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        start = center,
                        end = end,
                    ),
                    start = center,
                    end = end,
                    strokeWidth = (2f + (i % 3)) * 1.5f,
                )
            }

            // Drifting gold bokeh particles (rise and wrap vertically).
            for (p in bokeh) {
                val travel = (orbitPhase + p.offset) % 1f
                val y = size.height * (1f - travel) // rise from bottom to top
                val x = size.width * p.x + sin((travel + p.offset) * 2f * PI.toFloat()) * size.width * 0.04f
                val twinkle = 0.5f + 0.5f * sin((travel + p.offset) * 2f * PI.toFloat())
                val r = p.radius * size.minDimension
                drawCircle(
                    color = (if (p.warm) sand else turquoise).copy(alpha = 0.10f + 0.18f * twinkle * p.alpha),
                    radius = r,
                    center = Offset(x, y),
                )
            }
        }

        // Layers 3-5 in a vertical column.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(WindowInsets.systemBars.asPaddingValues()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Layer 3: hero artifact with shimmer + sparkles.
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Soft gold halo behind the hero.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension * (0.55f + 0.05f * glowPulse)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                gold.copy(alpha = 0.30f),
                                Color.Transparent,
                            ),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                }

                // The hero diamond with a gold shimmer sweep masked to its pixels.
                Image(
                    painter = painterResource(Res.drawable.ic_diamond),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(150.dp)
                        .scale(heroPulse)
                        // Mask the shimmer to the drawable's alpha via SrcAtop.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            // Diagonal transparent -> gold -> transparent band that
                            // translates across the bounds with shimmerPhase.
                            val w = size.width
                            val h = size.height
                            val span = w + h
                            val travel = shimmerPhase * 2f * span - span
                            val start = Offset(travel, 0f)
                            val end = Offset(travel + span * 0.5f, h)
                            val brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    papyrus.copy(alpha = 0.85f),
                                    Color.Transparent,
                                ),
                                start = start,
                                end = end,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                            }
                        },
                )

                // Layer 3b: twinkling sparkles orbiting the hero.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val base = size.minDimension * 0.5f
                    for (s in sparkles) {
                        val a = s.angle + orbitPhase * 2f * PI.toFloat()
                        val orbit = base * s.distance
                        val pos = Offset(c.x + cos(a) * orbit, c.y + sin(a) * orbit)
                        // Twinkle: fade and scale in/out, phase-shifted per sparkle.
                        val tw = 0.5f + 0.5f * sin((sparklePhase + s.phase) * 2f * PI.toFloat())
                        val rad = s.size * size.minDimension * (0.4f + 0.6f * tw)
                        drawSparkle(
                            center = pos,
                            radius = rad,
                            color = (if (s.warm) sand else papyrus).copy(alpha = 0.25f + 0.6f * tw),
                        )
                    }
                }
            }

            // Layer 4: faux progress bar.
            val pct = progress.value
            Box(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.linearGradient(listOf(bronze, gold, sand)),
                        )
                        // Moving highlight gliding along the filled region.
                        .drawWithCache {
                            val w = size.width
                            val travel = shimmerPhase * (w * 1.6f) - w * 0.3f
                            val band = w * 0.25f
                            val brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    papyrus.copy(alpha = 0.55f),
                                    Color.Transparent,
                                ),
                                startX = travel,
                                endX = travel + band,
                            )
                            onDrawBehind {
                                drawRect(brush = brush, style = Fill)
                            }
                        },
                )
            }

            // Percentage readout (dd.d%).
            Text(
                text = formatPercent(pct),
                style = MaterialTheme.typography.labelLarge,
                color = AppAccent.Sand,
                modifier = Modifier.padding(top = 10.dp),
            )

            // Layer 5: thematic caption (Cinzel via titleMedium).
            Text(
                text = "Polishing the treasures...",
                style = MaterialTheme.typography.titleMedium,
                color = AppAccent.Papyrus,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Formats a 0..1 progress value as a padded `dd.d%` string without printf. */
private fun formatPercent(progress: Float): String {
    val tenths = (progress * 1000f).toInt().coerceIn(0, 1000) // 0..1000 = 0.0..100.0
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac%"
}

/** Draws a four-point twinkle (two crossed soft lines) at [center]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
) {
    if (radius <= 0f) return
    drawCircle(color = color, radius = radius * 0.35f, center = center)
    drawLine(
        color = color,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = radius * 0.18f,
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = radius * 0.18f,
    )
}

/** Static seed for a drifting bokeh particle. */
private data class Bokeh(
    val x: Float,        // horizontal position (0..1)
    val radius: Float,   // fraction of min dimension
    val offset: Float,   // phase offset (0..1)
    val alpha: Float,    // peak alpha multiplier
    val warm: Boolean,   // warm gold vs cool turquoise tint
)

private fun buildBokeh(): List<Bokeh> {
    val seeds = listOf(
        Bokeh(0.08f, 0.012f, 0.00f, 0.9f, true),
        Bokeh(0.20f, 0.020f, 0.35f, 0.7f, true),
        Bokeh(0.33f, 0.010f, 0.70f, 1.0f, false),
        Bokeh(0.47f, 0.016f, 0.15f, 0.8f, true),
        Bokeh(0.58f, 0.009f, 0.55f, 0.6f, false),
        Bokeh(0.69f, 0.022f, 0.85f, 0.9f, true),
        Bokeh(0.80f, 0.013f, 0.25f, 0.7f, true),
        Bokeh(0.91f, 0.018f, 0.60f, 1.0f, false),
        Bokeh(0.15f, 0.008f, 0.45f, 0.5f, true),
        Bokeh(0.74f, 0.011f, 0.05f, 0.8f, false),
    )
    return seeds
}

/** Static seed for a sparkle orbiting the hero. */
private data class Sparkle(
    val angle: Float,    // base angle (radians)
    val distance: Float, // orbit radius as fraction of half-size
    val size: Float,     // size as fraction of min dimension
    val phase: Float,    // twinkle phase offset (0..1)
    val warm: Boolean,
)

private fun buildSparkles(): List<Sparkle> {
    val twoPi = 2f * PI.toFloat()
    return listOf(
        Sparkle(twoPi * 0.05f, 0.92f, 0.030f, 0.0f, true),
        Sparkle(twoPi * 0.18f, 0.78f, 0.022f, 0.3f, false),
        Sparkle(twoPi * 0.31f, 1.00f, 0.026f, 0.6f, true),
        Sparkle(twoPi * 0.44f, 0.85f, 0.018f, 0.15f, true),
        Sparkle(twoPi * 0.57f, 0.95f, 0.028f, 0.45f, false),
        Sparkle(twoPi * 0.70f, 0.80f, 0.020f, 0.75f, true),
        Sparkle(twoPi * 0.83f, 1.02f, 0.024f, 0.2f, false),
        Sparkle(twoPi * 0.96f, 0.88f, 0.016f, 0.55f, true),
    )
}
