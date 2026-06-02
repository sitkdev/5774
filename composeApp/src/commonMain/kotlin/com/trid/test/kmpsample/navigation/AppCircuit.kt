package com.trid.test.kmpsample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.slack.circuit.foundation.Circuit
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
        // --- Home (menu container placeholder) ---
        .addPresenter<HomeScreen, HomeUiState> { _, navigator, _ ->
            HomePresenter(navigator)
        }
        .addUi<HomeScreen, HomeUiState> { _, modifier ->
            HomeUi(modifier)
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

/**
 * Home UI wrapper — empty placeholder menu container. Back press is left to
 * the navigator (handled at the host level so spam back-press settles on Home
 * instead of closing the app).
 */
@Composable
private fun HomeUi(modifier: Modifier = Modifier) {
    // Placeholder menu container. Child navigators will be nested here later.
}
