package com.trid.test.kmpsample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trid.test.kmpsample.data.Artifact
import com.trid.test.kmpsample.data.Rarity
import com.trid.test.kmpsample.legal.LegalLinks
import com.trid.test.kmpsample.navigation.DashboardUiEvent
import com.trid.test.kmpsample.navigation.DashboardUiState
import com.trid.test.kmpsample.ui.components.ArtifactThumb
import com.trid.test.kmpsample.ui.components.EmptyState
import com.trid.test.kmpsample.ui.components.SectionHeader
import com.trid.test.kmpsample.ui.components.accentColor
import com.trid.test.kmpsample.ui.components.formatCurrency
import com.trid.test.kmpsample.ui.theme.AppAccent
import com.trid.test.kmpsample.ui.theme.AppGradients
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_diamond
import mic_kmp_sample.composeapp.generated.resources.ic_medallion
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Dashboard hub: hero crest, stat cards, recently-added carousel, embedded
 * analytics (category bars, rarity legend, top-value items), navigation cards
 * and the legal footer. Background gradient is supplied by the host scaffold.
 */
@Composable
fun DashboardScreenUi(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val stats = state.stats
    val recent = state.recent

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { HeroHeader() }

        item {
            StatGrid(
                artifactCount = stats.artifactCount,
                collectionCount = stats.collectionCount,
                favoriteCount = stats.favoriteCount,
                totalValue = stats.totalValue,
            )
        }

        item {
            NavCards(
                onCollections = { state.eventSink(DashboardUiEvent.OpenCollections) },
                onShowcase = { state.eventSink(DashboardUiEvent.OpenShowcase) },
                onAdd = { state.eventSink(DashboardUiEvent.OpenAddArtifact) },
            )
        }

        item { SectionHeader(title = "Recently added") }
        item {
            if (recent.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        title = "No artifacts yet",
                        subtitle = "Add your first item and create a collection while saving it.",
                        iconSize = 72.dp,
                    )
                    Button(onClick = { state.eventSink(DashboardUiEvent.OpenAddArtifact) }) {
                        Text("Add first item")
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(recent, key = { it.id }) { artifact ->
                        RecentArtifactCard(
                            artifact = artifact,
                            onClick = {
                                state.eventSink(DashboardUiEvent.OpenArtifact(artifact.id))
                            },
                        )
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader(title = "Analytics") }
            item { AnalyticsPanel(recent) }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            LegalLinks(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppGradients.PremiumBanner)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.ic_medallion),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "My collection",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "Your personal catalog",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun StatGrid(
    artifactCount: Int,
    collectionCount: Int,
    favoriteCount: Int,
    totalValue: Double,
) {
    // Two responsive rows of two cards; weights scale across widths.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Artifacts",
                value = artifactCount.toString(),
                accent = AppAccent.Sand,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Collections",
                value = collectionCount.toString(),
                accent = AppAccent.Turquoise,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Favorites",
                value = favoriteCount.toString(),
                accent = AppAccent.Bronze,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Value",
                value = formatCurrency(totalValue),
                accent = AppAccent.Gold,
                modifier = Modifier.weight(1f),
                icon = Res.drawable.ic_diamond,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (icon != null) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NavCards(
    onCollections: () -> Unit,
    onShowcase: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NavCard("Collections", AppAccent.Turquoise, onCollections, Modifier.weight(1f))
        NavCard("Showcase", AppAccent.Sand, onShowcase, Modifier.weight(1f))
        NavCard("Add", AppAccent.Gold, onAdd, Modifier.weight(1f))
    }
}

@Composable
private fun NavCard(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.16f),
            contentColor = accent,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = accent)
        }
    }
}

@Composable
private fun RecentArtifactCard(artifact: Artifact, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                ArtifactThumb(
                    image = artifact.images.firstOrNull(),
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = artifact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatCurrency(artifact.value),
                    style = MaterialTheme.typography.labelMedium,
                    color = artifact.rarity.accentColor(),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsPanel(items: List<Artifact>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Category distribution bars.
            val byCategory = remember(items) {
                items.groupingBy { it.category }.eachCount()
                    .entries.sortedByDescending { it.value }
            }
            val maxCat = byCategory.maxOfOrNull { it.value } ?: 1
            Text(
                "Category distribution",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            byCategory.forEach { (category, count) ->
                CategoryBar(category, count, maxCat)
            }

            // Rarity legend chips.
            val byRarity = remember(items) {
                Rarity.entries.associateWith { r -> items.count { it.rarity == r } }
            }
            Text(
                "By rarity",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                byRarity.filter { it.value > 0 }.forEach { (rarity, count) ->
                    RarityLegend(rarity, count)
                }
            }

            // Top-value items.
            val top = remember(items) { items.sortedByDescending { it.value }.take(3) }
            Text(
                "Top value",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            top.forEach { artifact ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = artifact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatCurrency(artifact.value),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppAccent.Gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBar(category: String, count: Int, max: Int) {
    val fraction = (count.toFloat() / max).coerceIn(0.05f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppAccent.Gold),
            )
        }
    }
}

@Composable
private fun RarityLegend(rarity: Rarity, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50))
                .background(rarity.accentColor()),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
