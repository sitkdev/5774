package com.trid.test.kmpsample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trid.test.kmpsample.navigation.ShowcaseUiEvent
import com.trid.test.kmpsample.navigation.ShowcaseUiState
import com.trid.test.kmpsample.ui.components.EmptyState
import com.trid.test.kmpsample.ui.components.NavBackIcon
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_eye
import org.jetbrains.compose.resources.painterResource

private enum class ShowcaseSort(val label: String) {
    Rarity("Rarity"),
    Value("Value"),
    Date("Date"),
}

/**
 * Showcase of highlight artifacts. Local search + sort (no presenter event for
 * these, so the UI keeps `remember` state and derives the displayed list). The
 * eye icon accents the hero header; an adaptive grid renders the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseScreenUi(
    state: ShowcaseUiState,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ShowcaseSort.Rarity) }

    val displayed = remember(state.highlights, query, sort) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            state.highlights
        } else {
            state.highlights.filter { artifact ->
                artifact.name.contains(q, ignoreCase = true) ||
                    artifact.category.contains(q, ignoreCase = true) ||
                    artifact.tags.any { it.contains(q, ignoreCase = true) }
            }
        }
        when (sort) {
            ShowcaseSort.Rarity -> filtered.sortedByDescending { it.rarity.ordinal }
            ShowcaseSort.Value -> filtered.sortedByDescending { it.value }
            ShowcaseSort.Date -> filtered.sortedByDescending { it.dateAddedMillis }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Hero header with the eye accent.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NavBackIcon(onClick = { state.eventSink(ShowcaseUiEvent.Back) })
            Image(
                painter = painterResource(Res.drawable.ic_eye),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    "Showcase",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Your highlighted items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search highlights") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            ShowcaseSort.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = sort == option,
                    onClick = { sort = option },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ShowcaseSort.entries.size,
                    ),
                ) {
                    Text(option.label)
                }
            }
        }

        if (displayed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        title = if (query.isBlank()) "No highlights yet" else "No matches",
                        subtitle = if (query.isBlank()) {
                            "Favorite items to feature them here, or add your first item."
                        } else {
                            "Try a different search term."
                        },
                    )
                    if (query.isBlank()) {
                        Button(onClick = { state.eventSink(ShowcaseUiEvent.OpenAddArtifact) }) {
                            Text("Add item")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(displayed, key = { it.id }) { artifact ->
                    ArtifactGridCard(
                        artifact = artifact,
                        onClick = { state.eventSink(ShowcaseUiEvent.OpenArtifact(artifact.id)) },
                    )
                }
            }
        }
    }
}
