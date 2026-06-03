package com.trid.test.kmpsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.trid.test.kmpsample.data.Collection
import com.trid.test.kmpsample.data.Rarity
import com.trid.test.kmpsample.media.rememberCameraPicker
import com.trid.test.kmpsample.media.rememberGalleryPicker
import com.trid.test.kmpsample.navigation.AddArtifactUiEvent
import com.trid.test.kmpsample.navigation.AddArtifactUiState
import com.trid.test.kmpsample.ui.components.NavBackIcon
import com.trid.test.kmpsample.ui.components.RarityChip
import com.trid.test.kmpsample.ui.components.accentColor
import coil3.compose.AsyncImage

private val CATEGORIES = listOf("Coins", "Minerals", "Books", "Figurines", "Cards", "Misc")

/**
 * Add-artifact form showcasing Material3 inputs: OutlinedTextFields, two
 * ExposedDropdownMenuBox dropdowns (collection + category), a condition Slider,
 * a value Slider, a favorite Switch, a rarity RadioButton group and a
 * DatePicker dialog. Camera/Gallery buttons keep picked images in memory for
 * preview; the presenter writes them to app-private files on save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddArtifactScreenUi(
    state: AddArtifactUiState,
    modifier: Modifier = Modifier,
) {
    val collections = state.collections

    // User-captured/selected images for preview only. Do not base64-persist them.
    val images = remember { mutableStateListOf<ByteArray>() }
    val onPicked: (ByteArray?) -> Unit = { bytes ->
        if (bytes != null) {
            images.add(bytes)
        }
    }
    val cameraPicker = rememberCameraPicker(onPicked)
    val galleryPicker = rememberGalleryPicker(onPicked)

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var value by remember { mutableStateOf(0f) }
    var condition by remember { mutableStateOf(80f) }
    var favorite by remember { mutableStateOf(false) }
    var rarity by remember { mutableStateOf(Rarity.Common) }
    var selectedCollection by remember(collections, state.preselectedCollectionId) {
        mutableStateOf(
            collections.firstOrNull { it.id == state.preselectedCollectionId }
                ?: collections.firstOrNull(),
        )
    }
    var newCollectionName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first()) }

    var collectionExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val dateMillis = datePickerState.selectedDateMillis

    val canSave = name.isNotBlank() && (selectedCollection != null || newCollectionName.isNotBlank())

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Add artifact", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                NavBackIcon(
                    onClick = { state.eventSink(AddArtifactUiEvent.Cancel) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Media pickers + selected-images preview.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { cameraPicker.launch() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("📷 Camera")
                    }
                    OutlinedButton(
                        onClick = { galleryPicker.launch() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("🖼️ Gallery")
                    }
                }
            }
            item { ImagePreviewRow(images) }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                if (collections.isEmpty()) {
                    NewCollectionField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        label = "Collection name",
                        supportingText = "Create your first collection to save this artifact.",
                    )
                } else {
                    CollectionDropdown(
                        collections = collections,
                        selected = selectedCollection,
                        expanded = collectionExpanded,
                        onExpandedChange = { collectionExpanded = it },
                        onSelect = {
                            selectedCollection = it
                            collectionExpanded = false
                        },
                    )
                }
            }

            if (collections.isNotEmpty()) {
                item {
                    NewCollectionField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        label = "New collection (optional)",
                        supportingText = "Fill this to save into a new collection instead.",
                    )
                }
            }

            // Category dropdown.
            item {
                CategoryDropdown(
                    selected = category,
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    onSelect = {
                        category = it
                        categoryExpanded = false
                    },
                )
            }

            item {
                OutlinedTextField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = { Text("Storage location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Value: text field + slider for quick set.
            item {
                Column {
                    Text(
                        "Value: ${value.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..10000f,
                    )
                }
            }

            item {
                Column {
                    Text(
                        "Condition: ${condition.toInt()} / 100",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Slider(
                        value = condition,
                        onValueChange = { condition = it },
                        valueRange = 0f..100f,
                    )
                }
            }

            // Favorite switch.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Mark as favorite",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }

            // Rarity radio group.
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rarity",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        RarityChip(rarity)
                    }
                    Rarity.entries.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rarity = r },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = r == rarity, onClick = { rarity = r })
                            Text(
                                text = r.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (r == rarity) {
                                    r.accentColor()
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // Date acquired.
            item {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (dateMillis != null) "Date acquired: set" else "Pick date acquired",
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Button(
                    onClick = {
                        state.eventSink(
                            AddArtifactUiEvent.Save(
                                name = name,
                                description = description,
                                selectedCollectionId = selectedCollection?.id,
                                newCollectionName = newCollectionName,
                                category = category,
                                rarity = rarity,
                                condition = condition.toInt(),
                                value = value.toDouble(),
                                storageLocation = storage,
                                imageBytes = images.toList(),
                                favorite = favorite,
                                dateAddedMillis = dateMillis ?: 0L,
                            ),
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }
            }
            item {
                TextButton(
                    onClick = { state.eventSink(AddArtifactUiEvent.Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun NewCollectionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionDropdown(
    collections: List<Collection>,
    selected: Collection?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Collection) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Select collection",
            onValueChange = {},
            readOnly = true,
            label = { Text("Collection") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            collections.forEach { collection ->
                DropdownMenuItem(
                    text = { Text(collection.name) },
                    onClick = { onSelect(collection) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            CATEGORIES.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewRow(images: List<ByteArray>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (images.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(images.size) { index ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = images[index],
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
