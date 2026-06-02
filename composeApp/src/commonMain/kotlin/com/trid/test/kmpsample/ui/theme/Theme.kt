package com.trid.test.kmpsample.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Dark-only Material 3 color scheme for the app (Egyptian / gold theme).
 */
private val DarkColors: ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    outline = Outline,
    outlineVariant = OutlineVariant,

    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,

    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

/**
 * Extra accent colors not covered by the Material color scheme, intended for
 * charts, chips and stat indicators.
 */
object AppAccent {
    val Gold: Color = AccentGold
    val Sand: Color = AccentSand
    val Bronze: Color = AccentBronze
    val Turquoise: Color = AccentTurquoise
    val Papyrus: Color = AccentPapyrus
}

/**
 * Reusable gradient brushes. These are direction-agnostic linear gradients
 * (default start = top-left, end = bottom-right via the gradient's own
 * coordinate handling), safe to hold as plain vals.
 */
object AppGradients {
    val Primary: Brush = Brush.linearGradient(
        colors = listOf(GradientPrimaryStart, GradientPrimaryEnd)
    )

    val Card: Brush = Brush.linearGradient(
        colors = listOf(GradientCardStart, GradientCardEnd)
    )

    val PremiumBanner: Brush = Brush.linearGradient(
        colors = listOf(GradientPremiumStart, GradientPremiumMid, GradientPremiumEnd)
    )
}

/**
 * Applies the app's dark Material 3 color scheme and typography. Wraps content
 * in a [Surface] that consumes the safe-drawing insets so children can lay out
 * within the safe area.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = appTypography(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .consumeWindowInsets(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
