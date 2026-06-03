package com.trid.test.kmpsample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trid.test.kmpsample.data.Artifact
import com.trid.test.kmpsample.data.Rarity
import com.trid.test.kmpsample.navigation.ArtifactDetailsUiEvent
import com.trid.test.kmpsample.navigation.ArtifactDetailsUiState
import com.trid.test.kmpsample.ui.components.ArtifactThumb
import com.trid.test.kmpsample.ui.components.LabelChip
import com.trid.test.kmpsample.ui.components.RarityChip
import com.trid.test.kmpsample.ui.components.accentColor
import com.trid.test.kmpsample.ui.components.formatCurrency
import com.trid.test.kmpsample.ui.theme.AppAccent
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_badge
import mic_kmp_sample.composeapp.generated.resources.ic_seal
import org.jetbrains.compose.resources.painterResource

/**
 * Rich artifact detail: large photo with a corner seal stamp, name (Cinzel),
 * description, category/tags/rarity chips, a read-only condition slider, a
 * rarity radio indicator, value + storage rows, a favorite toggle, a delete
 * confirmation dialog and a tap-to-zoom full-screen photo dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtifactDetailsScreenUi(
    state: ArtifactDetailsUiState,
    modifier: Modifier = Modifier,
) {
    val artifact = state.artifact
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = artifact?.name ?: "Artifact",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = { state.eventSink(ArtifactDetailsUiEvent.Back) }) {
                    Text(
                        "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
            actions = {
                if (artifact != null) {
                    IconButton(onClick = { state.eventSink(ArtifactDetailsUiEvent.ToggleFavorite) }) {
                        Text(
                            text = if (artifact.favorite) "★" else "☆",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (artifact.favorite) {
                                AppAccent.Gold
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        if (artifact == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Artifact not found", style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PhotoArea(
                    artifact = artifact,
                    onClick = { showPhotoDialog = true },
                )
            }

            item {
                Text(
                    text = artifact.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RarityChip(artifact.rarity)
                    LabelChip(text = artifact.category, color = AppAccent.Turquoise)
                    artifact.tags.forEach { tag ->
                        LabelChip(text = "#$tag", color = AppAccent.Sand)
                    }
                }
            }

            item {
                Text(
                    text = artifact.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { ConditionMeter(artifact.condition) }

            item { RaritySelector(selected = artifact.rarity) }

            item {
                DetailRow(label = "Value", value = formatCurrency(artifact.value))
            }
            item {
                DetailRow(label = "Storage location", value = artifact.storageLocation)
            }

            item {
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Delete artifact")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete artifact?") },
            text = { Text("This permanently removes \"${artifact?.name}\" from your collection.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    state.eventSink(ArtifactDetailsUiEvent.Delete)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showPhotoDialog && artifact != null) {
        Dialog(onDismissRequest = { showPhotoDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { showPhotoDialog = false }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtifactThumb(
                    image = artifact.images.firstOrNull(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
private fun PhotoArea(artifact: Artifact, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ArtifactThumb(
            image = artifact.images.firstOrNull(),
            modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(1f),
        )
        // Corner seal/badge stamp accent.
        Image(
            painter = painterResource(
                if (artifact.favorite) Res.drawable.ic_seal else Res.drawable.ic_badge,
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(44.dp),
        )
    }
}

@Composable
private fun ConditionMeter(condition: Int) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Condition",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$condition / 100",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppAccent.Gold,
                )
            }
            Slider(
                value = condition.toFloat(),
                onValueChange = {},
                valueRange = 0f..100f,
                enabled = false,
            )
        }
    }
}

@Composable
private fun RaritySelector(selected: Rarity) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Rarity",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Rarity.entries.forEach { rarity ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = rarity == selected,
                        onClick = null,
                    )
                    Text(
                        text = rarity.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rarity == selected) {
                            rarity.accentColor()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
