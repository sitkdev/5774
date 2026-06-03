package com.trid.test.kmpsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trid.test.kmpsample.data.Artifact
import com.trid.test.kmpsample.data.Collection
import com.trid.test.kmpsample.data.CollectionsRepository
import com.trid.test.kmpsample.navigation.CollectionsUiEvent
import com.trid.test.kmpsample.navigation.CollectionsUiState
import com.trid.test.kmpsample.ui.components.ArtifactThumb
import com.trid.test.kmpsample.ui.components.EmptyState
import com.trid.test.kmpsample.ui.components.KeyedIcon
import org.koin.mp.KoinPlatform

/**
 * Collections grid. Each card shows the collection crest, name (Cinzel), item
 * count and a small preview strip of the first artifacts' icons. Adaptive grid
 * scales columns with available width.
 */
@Composable
fun CollectionsScreenUi(
    state: CollectionsUiState,
    modifier: Modifier = Modifier,
) {
    // Read-only access to artifact lists for counts/preview (presenter exposes
    // only collections; the repository is the single source of truth).
    val repository = remember { KoinPlatform.getKoin().get<CollectionsRepository>() }
    val artifacts by repository.artifacts.collectAsState()

    if (state.collections.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No collections",
                subtitle = "Collections you create will appear here.",
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.collections, key = { it.id }) { collection ->
            val preview = artifacts.filter { it.collectionId == collection.id }
            CollectionCard(
                collection = collection,
                count = preview.size,
                preview = preview.take(4),
                onClick = { state.eventSink(CollectionsUiEvent.OpenCollection(collection.id)) },
            )
        }
    }
}

@Composable
private fun CollectionCard(
    collection: Collection,
    count: Int,
    preview: List<Artifact>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    KeyedIcon(key = collection.iconKey, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (count == 1) "1 item" else "$count items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.forEach { artifact ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArtifactThumb(
                                image = artifact.images.firstOrNull(),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
