package com.trid.test.kmpsample.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.cinzel_bold
import mic_kmp_sample.composeapp.generated.resources.nunito_sans
import org.jetbrains.compose.resources.Font

/**
 * Cinzel Bold: a decorative serif used for display, headline and title roles
 * to evoke the Egyptian / gold theme.
 *
 * The [Font] resource overload is `@Composable`, so the families must be built
 * inside composition.
 */
@Composable
fun cinzelFamily(): FontFamily = FontFamily(
    Font(resource = Res.font.cinzel_bold, weight = FontWeight.Bold)
)

/**
 * Nunito Sans: a clean sans-serif used for body and label roles.
 */
@Composable
fun nunitoSansFamily(): FontFamily = FontFamily(
    Font(resource = Res.font.nunito_sans, weight = FontWeight.Normal)
)

/**
 * Builds the app [Typography]. Must be called from composition because font
 * resources are loaded via the `@Composable` [Font] overload.
 */
@Composable
fun appTypography(): Typography {
    val display = cinzelFamily()
    val body = nunitoSansFamily()
    val base = Typography()

    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),

        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),

        titleLarge = base.titleLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),

        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),

        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}
