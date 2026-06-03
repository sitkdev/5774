package com.trid.test.kmpsample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.trid.test.kmpsample.data.Artifact
import com.trid.test.kmpsample.data.ArtifactImage
import com.trid.test.kmpsample.data.Collection
import com.trid.test.kmpsample.data.CollectionsRepository
import com.trid.test.kmpsample.data.DashboardStats
import com.trid.test.kmpsample.data.Rarity
import com.trid.test.kmpsample.data.currentTimeMillis
import com.trid.test.kmpsample.media.ArtifactImageStore
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform

/**
 * KMP-safe Koin accessor for presenters. Circuit constructs presenters outside
 * any composable, so we pull singletons from the global Koin instance rather
 * than injecting through the constructor (keeps `AppCircuit` registration
 * trivial and iOS-safe).
 */
private fun repo(): CollectionsRepository =
    KoinPlatform.getKoin().get<CollectionsRepository>()

private fun imageStore(): ArtifactImageStore =
    KoinPlatform.getKoin().get<ArtifactImageStore>()

private fun Navigator.popOrDashboard() {
    if (peekBackStack().size > 1) pop() else resetRoot(DashboardScreen)
}

/** How long the splash holds before advancing into the app. */
private const val LOADING_DELAY_MS = 2500L

/**
 * Presenters for the navigation skeleton.
 *
 * Each presenter is intentionally minimal: it exposes an `eventSink` on its
 * state so the (empty) UI can request transitions, and routes those events
 * through the [Navigator] it was given by Circuit. All real screen logic is
 * deferred — these only wire the navigation graph.
 *
 * The [Navigator] handed to each presenter by `NavigableCircuitContent` is the
 * app's [GuardedNavigator], so every transition fired from here is debounced
 * and duplicate-guarded automatically.
 */

// region Loading

data class LoadingUiState(
    val eventSink: (LoadingUiEvent) -> Unit,
) : CircuitUiState

sealed interface LoadingUiEvent : CircuitUiEvent {
    /** Startup work finished successfully — proceed into the app. */
    data object Ready : LoadingUiEvent

    /** Startup work failed due to connectivity — show the error screen. */
    data object NoConnection : LoadingUiEvent
}

class LoadingPresenter(
    private val navigator: Navigator,
) : Presenter<LoadingUiState> {
    @Composable
    override fun present(): LoadingUiState {
        // Drive the one-shot advance: after a short splash, fire Ready. The real
        // startup check (connectivity, warm-up) can replace the delay later and
        // route through NoConnection instead.
        val sink: (LoadingUiEvent) -> Unit = { event ->
            when (event) {
                // resetRoot wipes Loading from the backstack so back-press can
                // never return to it — Loading is a one-shot entry point.
                LoadingUiEvent.Ready -> navigator.resetRoot(DashboardScreen)
                LoadingUiEvent.NoConnection -> navigator.goTo(NoConnectionScreen)
            }
        }

        LaunchedEffect(Unit) {
            delay(LOADING_DELAY_MS)
            sink(LoadingUiEvent.Ready)
        }

        return LoadingUiState(sink)
    }
}

// endregion

// region NoConnection

data class NoConnectionUiState(
    val eventSink: (NoConnectionUiEvent) -> Unit,
) : CircuitUiState

sealed interface NoConnectionUiEvent : CircuitUiEvent {
    /** User tapped retry — go back to Loading to re-run startup. */
    data object Retry : NoConnectionUiEvent
}

class NoConnectionPresenter(
    private val navigator: Navigator,
) : Presenter<NoConnectionUiState> {
    @Composable
    override fun present(): NoConnectionUiState {
        return NoConnectionUiState { event ->
            when (event) {
                // resetRoot back to Loading: the retry destination is always
                // Loading, which then decides where to go next.
                NoConnectionUiEvent.Retry -> navigator.resetRoot(LoadingScreen)
            }
        }
    }
}

// endregion

// region Dashboard (hub)

data class DashboardUiState(
    val stats: DashboardStats,
    val recent: List<Artifact>,
    val eventSink: (DashboardUiEvent) -> Unit,
) : CircuitUiState

sealed interface DashboardUiEvent : CircuitUiEvent {
    data object OpenCollections : DashboardUiEvent
    data object OpenShowcase : DashboardUiEvent
    data object OpenAddArtifact : DashboardUiEvent
    data class OpenArtifact(val artifactId: String) : DashboardUiEvent
}

class DashboardPresenter(
    private val navigator: Navigator,
) : Presenter<DashboardUiState> {
    @Composable
    override fun present(): DashboardUiState {
        val repository = repo()
        // Observe reactively so added/removed/favorited items refresh the hub.
        val artifacts by repository.artifacts.collectAsState()
        val collections by repository.collections.collectAsState()

        // Recompute derived values whenever the underlying lists change.
        val stats = remember(artifacts, collections) { repository.dashboardStats() }
        val recent = remember(artifacts) { repository.recent(RECENT_COUNT) }

        return DashboardUiState(
            stats = stats,
            recent = recent,
        ) { event ->
            when (event) {
                DashboardUiEvent.OpenCollections -> navigator.goTo(CollectionsScreen)
                DashboardUiEvent.OpenShowcase -> navigator.goTo(ShowcaseScreen)
                DashboardUiEvent.OpenAddArtifact -> navigator.goTo(AddArtifactScreen())
                is DashboardUiEvent.OpenArtifact ->
                    navigator.goTo(ArtifactDetailsScreen(event.artifactId))
            }
        }
    }

    private companion object {
        const val RECENT_COUNT = 6
    }
}

// endregion

// region Collections

data class CollectionsUiState(
    val collections: List<Collection>,
    val eventSink: (CollectionsUiEvent) -> Unit,
) : CircuitUiState

sealed interface CollectionsUiEvent : CircuitUiEvent {
    data class OpenCollection(val collectionId: String) : CollectionsUiEvent
    data object OpenAddArtifact : CollectionsUiEvent
    data object Back : CollectionsUiEvent
}

class CollectionsPresenter(
    private val navigator: Navigator,
) : Presenter<CollectionsUiState> {
    @Composable
    override fun present(): CollectionsUiState {
        val collections by repo().collections.collectAsState()
        return CollectionsUiState(collections = collections) { event ->
            when (event) {
                is CollectionsUiEvent.OpenCollection ->
                    navigator.goTo(CollectionArtifactsScreen(event.collectionId))
                CollectionsUiEvent.OpenAddArtifact -> navigator.goTo(AddArtifactScreen())
                CollectionsUiEvent.Back -> navigator.popOrDashboard()
            }
        }
    }
}

// endregion

// region CollectionArtifacts

data class CollectionArtifactsUiState(
    val collection: Collection?,
    val artifacts: List<Artifact>,
    val eventSink: (CollectionArtifactsUiEvent) -> Unit,
) : CircuitUiState

sealed interface CollectionArtifactsUiEvent : CircuitUiEvent {
    data class OpenArtifact(val artifactId: String) : CollectionArtifactsUiEvent
    data class ToggleFavorite(val artifactId: String) : CollectionArtifactsUiEvent
    data object OpenAddArtifact : CollectionArtifactsUiEvent
    data object Back : CollectionArtifactsUiEvent
}

class CollectionArtifactsPresenter(
    private val collectionId: String,
    private val navigator: Navigator,
) : Presenter<CollectionArtifactsUiState> {
    @Composable
    override fun present(): CollectionArtifactsUiState {
        val repository = repo()
        val allArtifacts by repository.artifacts.collectAsState()
        val allCollections by repository.collections.collectAsState()

        val collection = remember(allCollections, collectionId) {
            repository.collection(collectionId)
        }
        val artifacts = remember(allArtifacts, collectionId) {
            repository.artifactsIn(collectionId)
        }

        return CollectionArtifactsUiState(
            collection = collection,
            artifacts = artifacts,
        ) { event ->
            when (event) {
                is CollectionArtifactsUiEvent.OpenArtifact ->
                    navigator.goTo(ArtifactDetailsScreen(event.artifactId))
                is CollectionArtifactsUiEvent.ToggleFavorite ->
                    repository.toggleFavorite(event.artifactId)
                CollectionArtifactsUiEvent.OpenAddArtifact -> navigator.goTo(
                    AddArtifactScreen(collectionId = collectionId),
                )
                CollectionArtifactsUiEvent.Back -> navigator.popOrDashboard()
            }
        }
    }
}

// endregion

// region ArtifactDetails

data class ArtifactDetailsUiState(
    val artifact: Artifact?,
    val eventSink: (ArtifactDetailsUiEvent) -> Unit,
) : CircuitUiState

sealed interface ArtifactDetailsUiEvent : CircuitUiEvent {
    data object ToggleFavorite : ArtifactDetailsUiEvent
    data object Delete : ArtifactDetailsUiEvent
    data object Back : ArtifactDetailsUiEvent
}

class ArtifactDetailsPresenter(
    private val artifactId: String,
    private val navigator: Navigator,
) : Presenter<ArtifactDetailsUiState> {
    @Composable
    override fun present(): ArtifactDetailsUiState {
        val repository = repo()
        val allArtifacts by repository.artifacts.collectAsState()
        val artifact = remember(allArtifacts, artifactId) { repository.artifact(artifactId) }

        return ArtifactDetailsUiState(artifact = artifact) { event ->
            when (event) {
                ArtifactDetailsUiEvent.ToggleFavorite ->
                    repository.toggleFavorite(artifactId)
                ArtifactDetailsUiEvent.Delete -> {
                    repository.removeArtifact(artifactId)
                    navigator.popOrDashboard()
                }
                ArtifactDetailsUiEvent.Back -> navigator.popOrDashboard()
            }
        }
    }
}

// endregion

// region AddArtifact

data class AddArtifactUiState(
    val collections: List<Collection>,
    val preselectedCollectionId: String?,
    val eventSink: (AddArtifactUiEvent) -> Unit,
) : CircuitUiState

sealed interface AddArtifactUiEvent : CircuitUiEvent {
    data class Save(
        val name: String,
        val description: String,
        val selectedCollectionId: String?,
        val newCollectionName: String,
        val category: String,
        val rarity: Rarity,
        val condition: Int,
        val value: Double,
        val storageLocation: String,
        val imageBytes: List<ByteArray>,
        val favorite: Boolean,
        val dateAddedMillis: Long,
    ) : AddArtifactUiEvent

    data object Cancel : AddArtifactUiEvent
}

class AddArtifactPresenter(
    private val preselectedCollectionId: String?,
    private val navigator: Navigator,
) : Presenter<AddArtifactUiState> {
    @Composable
    override fun present(): AddArtifactUiState {
        val repository = repo()
        val imageStore = imageStore()
        val collections by repository.collections.collectAsState()

        return AddArtifactUiState(
            collections = collections,
            preselectedCollectionId = preselectedCollectionId,
        ) { event ->
            when (event) {
                is AddArtifactUiEvent.Save -> {
                    val targetCollection = resolveTargetCollection(repository, event)
                    if (targetCollection != null) {
                        val storedImages = event.imageBytes.mapNotNull { bytes ->
                            runCatching { imageStore.save(bytes) }.getOrNull()
                        }
                        val artifactImages = storedImages.ifEmpty {
                            listOf(ArtifactImage.Resource(targetCollection.iconKey))
                        }
                        repository.addArtifact(
                            Artifact(
                                id = repository.newId("art"),
                                collectionId = targetCollection.id,
                                name = event.name.trim(),
                                description = event.description.trim(),
                                category = event.category,
                                rarity = event.rarity,
                                condition = event.condition,
                                value = event.value,
                                storageLocation = event.storageLocation.trim().ifBlank { "Unsorted" },
                                tags = emptyList(),
                                images = artifactImages,
                                favorite = event.favorite,
                                dateAddedMillis = event.dateAddedMillis.takeIf { it > 0L }
                                    ?: currentTimeMillis(),
                            ),
                        )
                        navigator.popOrDashboard()
                    }
                }
                AddArtifactUiEvent.Cancel -> navigator.popOrDashboard()
            }
        }
    }

    private fun resolveTargetCollection(
        repository: CollectionsRepository,
        event: AddArtifactUiEvent.Save,
    ): Collection? {
        val newCollectionName = event.newCollectionName.trim()
        if (newCollectionName.isNotEmpty()) {
            val collection = Collection(
                id = repository.newId("col"),
                name = newCollectionName,
                iconKey = iconKeyForCategory(event.category),
            )
            repository.addCollection(collection)
            return collection
        }
        return event.selectedCollectionId?.let(repository::collection)
    }

    private fun iconKeyForCategory(category: String): String = when (category.lowercase()) {
        "coins" -> "ic_coins"
        "minerals" -> "ic_capsule"
        "books" -> "ic_deco_emblem"
        "figurines" -> "ic_vase"
        "cards" -> "ic_card"
        else -> "ic_ornament"
    }
}

// endregion

// region Showcase

data class ShowcaseUiState(
    val highlights: List<Artifact>,
    val eventSink: (ShowcaseUiEvent) -> Unit,
) : CircuitUiState

sealed interface ShowcaseUiEvent : CircuitUiEvent {
    data class OpenArtifact(val artifactId: String) : ShowcaseUiEvent
    data object OpenAddArtifact : ShowcaseUiEvent
    data object Back : ShowcaseUiEvent
}

class ShowcasePresenter(
    private val navigator: Navigator,
) : Presenter<ShowcaseUiState> {
    @Composable
    override fun present(): ShowcaseUiState {
        val repository = repo()
        val allArtifacts by repository.artifacts.collectAsState()
        // Highlights = favorites first, then highest-value items as a fallback.
        val highlights = remember(allArtifacts) {
            repository.favorites.ifEmpty {
                repository.sortedByValue(allArtifacts).take(SHOWCASE_COUNT)
            }
        }

        return ShowcaseUiState(highlights = highlights) { event ->
            when (event) {
                is ShowcaseUiEvent.OpenArtifact ->
                    navigator.goTo(ArtifactDetailsScreen(event.artifactId))
                ShowcaseUiEvent.OpenAddArtifact -> navigator.goTo(AddArtifactScreen())
                ShowcaseUiEvent.Back -> navigator.popOrDashboard()
            }
        }
    }

    private companion object {
        const val SHOWCASE_COUNT = 10
    }
}

// endregion
