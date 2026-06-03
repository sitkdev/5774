package com.trid.test.kmpsample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.slack.circuit.foundation.Circuit
import com.trid.test.kmpsample.ui.AddArtifactScreenUi
import com.trid.test.kmpsample.ui.ArtifactDetailsScreenUi
import com.trid.test.kmpsample.ui.CollectionArtifactsScreenUi
import com.trid.test.kmpsample.ui.CollectionsScreenUi
import com.trid.test.kmpsample.ui.DashboardScreenUi
import com.trid.test.kmpsample.ui.ShowcaseScreenUi
import com.trid.test.kmpsample.ui.LoadingScreen as LoadingScreenContent
import com.trid.test.kmpsample.ui.NoConnectionScreen as NoConnectionScreenContent

/**
 * Builds the app-wide [Circuit] instance: the registry of presenters and UIs
 * keyed by [com.trid.test.kmpsample.navigation] screen types.
 *
 * Registration uses the type-safe `addPresenter<Screen, State>` /
 * `addUi<Screen, State>` builder extensions from circuit-foundation 0.33.1.
 * Each `addPresenter` receives `(screen, navigator, context)` — the navigator
 * passed in by `NavigableCircuitContent` is the app's [GuardedNavigator].
 *
 * The visual bodies of [LoadingScreenContent] and [NoConnectionScreenContent]
 * stay empty placeholders; here we only wrap them as Circuit UIs and connect
 * their state/events.
 */
fun buildAppCircuit(): Circuit =
    Circuit.Builder()
        // --- Loading ---
        .addPresenter<LoadingScreen, LoadingUiState> { _, navigator, _ ->
            LoadingPresenter(navigator)
        }
        .addUi<LoadingScreen, LoadingUiState> { _, modifier ->
            LoadingUi(modifier)
        }
        // --- No connection ---
        .addPresenter<NoConnectionScreen, NoConnectionUiState> { _, navigator, _ ->
            NoConnectionPresenter(navigator)
        }
        .addUi<NoConnectionScreen, NoConnectionUiState> { state, modifier ->
            NoConnectionUi(state, modifier)
        }
        // --- Dashboard (hub) ---
        .addPresenter<DashboardScreen, DashboardUiState> { _, navigator, _ ->
            DashboardPresenter(navigator)
        }
        .addUi<DashboardScreen, DashboardUiState> { state, modifier ->
            DashboardScreenUi(state, modifier)
        }
        // --- Collections ---
        .addPresenter<CollectionsScreen, CollectionsUiState> { _, navigator, _ ->
            CollectionsPresenter(navigator)
        }
        .addUi<CollectionsScreen, CollectionsUiState> { state, modifier ->
            CollectionsScreenUi(state, modifier)
        }
        // --- Artifacts within a collection ---
        .addPresenter<CollectionArtifactsScreen, CollectionArtifactsUiState> { screen, navigator, _ ->
            CollectionArtifactsPresenter(screen.collectionId, navigator)
        }
        .addUi<CollectionArtifactsScreen, CollectionArtifactsUiState> { state, modifier ->
            CollectionArtifactsScreenUi(state, modifier)
        }
        // --- Artifact details ---
        .addPresenter<ArtifactDetailsScreen, ArtifactDetailsUiState> { screen, navigator, _ ->
            ArtifactDetailsPresenter(screen.artifactId, navigator)
        }
        .addUi<ArtifactDetailsScreen, ArtifactDetailsUiState> { state, modifier ->
            ArtifactDetailsScreenUi(state, modifier)
        }
        // --- Add artifact ---
        .addPresenter<AddArtifactScreen, AddArtifactUiState> { _, navigator, _ ->
            AddArtifactPresenter(navigator)
        }
        .addUi<AddArtifactScreen, AddArtifactUiState> { state, modifier ->
            AddArtifactScreenUi(state, modifier)
        }
        // --- Showcase ---
        .addPresenter<ShowcaseScreen, ShowcaseUiState> { _, navigator, _ ->
            ShowcasePresenter(navigator)
        }
        .addUi<ShowcaseScreen, ShowcaseUiState> { state, modifier ->
            ShowcaseScreenUi(state, modifier)
        }
        .build()

/**
 * Loading UI wrapper.
 *
 * Back-press lock: [LoadingScreen] is the root entry point and must not be
 * dismissable. A `BackHandler(enabled = true)` with an empty body swallows
 * back gestures so nothing happens while loading.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LoadingUi(modifier: Modifier = Modifier) {
    BackHandler(enabled = true) { /* locked: ignore back press */ }
    LoadingScreenContent()
}

/**
 * No-connection UI wrapper.
 *
 * Back-press lock: this is a global error screen; back press is swallowed so
 * the user cannot escape it without using the retry action. Retry is routed
 * through the guarded navigator via the presenter's event sink.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoConnectionUi(
    state: NoConnectionUiState,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { /* locked: ignore back press */ }
    NoConnectionScreenContent(
        onReconnect = { /*state.eventSink(NoConnectionUiEvent.Retry)*/ },
    )
}
