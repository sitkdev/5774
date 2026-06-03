package com.trid.test.kmpsample.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.trid.test.kmpsample.data.ArtifactImage
import com.trid.test.kmpsample.data.DrawableKeys
import com.trid.test.kmpsample.data.Rarity
import com.trid.test.kmpsample.data.resolveDrawable
import com.trid.test.kmpsample.ui.theme.AppAccent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_open_box
import mic_kmp_sample.composeapp.generated.resources.ic_ornament
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Accent color for each [Rarity] tier (Common -> Legendary). */
fun Rarity.accentColor(): Color = when (this) {
    Rarity.Common -> AppAccent.Papyrus
    Rarity.Uncommon -> AppAccent.Turquoise
    Rarity.Rare -> AppAccent.Sand
    Rarity.Epic -> AppAccent.Bronze
    Rarity.Legendary -> AppAccent.Gold
}

/** Formats a currency value as `$1,234` (no decimals; KMP-safe manual grouping). */
fun formatCurrency(value: Double): String {
    val whole = value.toLong()
    val digits = whole.toString()
    val sb = StringBuilder()
    val len = digits.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) sb.append(',')
        sb.append(digits[i])
    }
    return "$$sb"
}

/**
 * Renders an [ArtifactImage]:
 *  - [ArtifactImage.Resource]: bundled drawable drawn via [painterResource].
 *  - [ArtifactImage.Bytes]: user-supplied bytes — the base64 payload is decoded
 *    to a [ByteArray] and loaded through Coil3 ([AsyncImage], which accepts a
 *    ByteArray model on both Android and iOS).
 *  - `null` / undecodable bytes: the open-box placeholder.
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ArtifactThumb(
    image: ArtifactImage?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    when (image) {
        is ArtifactImage.Bytes -> {
            val bytes: ByteArray? = remember(image.base64) {
                runCatching { Base64.decode(image.base64) }.getOrNull()
            }
            if (bytes != null) {
                AsyncImage(
                    model = bytes,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = modifier,
                )
            } else {
                PlaceholderImage(contentScale, modifier)
            }
        }

        is ArtifactImage.Resource -> Image(
            painter = painterResource(image.resolveDrawable()),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )

        null -> PlaceholderImage(contentScale, modifier)
    }
}

@Composable
private fun PlaceholderImage(contentScale: ContentScale, modifier: Modifier) {
    Image(
        painter = painterResource(Res.drawable.ic_open_box),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/** Draws a drawable resolved from a [DrawableKeys] string key. */
@Composable
fun KeyedIcon(
    key: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(DrawableKeys.resolve(key)),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/** A small pill chip used for rarity / category / tags. */
@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leading != null) {
                leading()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

/** A rarity chip tinted by [Rarity.accentColor]. */
@Composable
fun RarityChip(rarity: Rarity, modifier: Modifier = Modifier) {
    LabelChip(
        text = rarity.name,
        modifier = modifier,
        color = rarity.accentColor(),
    )
}

/** A decorative section header with the ornament accent and a Cinzel title. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_ornament),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/**
 * Centered empty-state with the open-box illustration, a title and a hint.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconSize: Dp = 96.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize + 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_open_box),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
