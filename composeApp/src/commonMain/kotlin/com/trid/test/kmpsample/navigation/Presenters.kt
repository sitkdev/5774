package com.trid.test.kmpsample.navigation

import androidx.compose.runtime.Composable
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

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
        return LoadingUiState { event ->
            when (event) {
                // resetRoot wipes Loading from the backstack so back-press can
                // never return to it — Loading is a one-shot entry point.
                LoadingUiEvent.Ready -> navigator.resetRoot(HomeScreen)
                LoadingUiEvent.NoConnection -> navigator.goTo(NoConnectionScreen)
            }
        }
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

// region Home

data class HomeUiState(
    val eventSink: (HomeUiEvent) -> Unit,
) : CircuitUiState

sealed interface HomeUiEvent : CircuitUiEvent

class HomePresenter(
    @Suppress("unused") private val navigator: Navigator,
) : Presenter<HomeUiState> {
    @Composable
    override fun present(): HomeUiState {
        // Placeholder: no child destinations wired yet.
        return HomeUiState { }
    }
}

// endregion
