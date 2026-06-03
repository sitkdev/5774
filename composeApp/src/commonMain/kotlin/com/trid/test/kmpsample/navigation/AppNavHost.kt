package com.trid.test.kmpsample.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.trid.test.kmpsample.ui.theme.AppGradients
import com.trid.test.kmpsample.ui.wrappers.RootScaffold

/**
 * The app's navigation host.
 *
 * Wiring:
 *  - Builds the [com.slack.circuit.foundation.Circuit] registry once and
 *    remembers it.
 *  - Roots a [com.slack.circuit.backstack.SaveableBackStack] at [LoadingScreen]
 *    (the mandatory entry point).
 *  - Creates the underlying Circuit [com.slack.circuit.runtime.Navigator] via
 *    [rememberCircuitNavigator]; its `onRootPop` deliberately does nothing so a
 *    back press / spam back-press at the root never closes the app — it settles
 *    on the initial screen instead.
 *  - Wraps that navigator in a [GuardedNavigator] (debounce + duplicate-top
 *    guard) and exposes it both to Circuit and to the UI tree via
 *    [LocalGuardedNavigator].
 *
 * `Circuit` is provided here via `remember` rather than Koin. Koin is not yet
 * initialized in this project (no `startKoin`), so wiring DI would require
 * touching both platform entry points and risks the build; providing the
 * single `Circuit` instance through `remember` is the lower-risk, fully
 * iOS-safe path. See the report for how to migrate to Koin later.
 */
@Composable
fun AppNavHost() {
    val circuit = remember { buildAppCircuit() }
    val backStack = rememberSaveableBackStack(root = LoadingScreen)

    // Underlying Circuit navigator. onRootPop is a no-op: pressing back at the
    // root must NOT exit the app (anti spam-close), it simply stays put.
    val circuitNavigator = rememberCircuitNavigator(backStack) { /* swallow root pop */ }

    // Single safety integration point. Stable across recomposition.
    val guardedNavigator = remember(circuitNavigator) { GuardedNavigator(circuitNavigator) }

    CircuitCompositionLocals(circuit) {
        CompositionLocalProvider(LocalGuardedNavigator provides guardedNavigator) {
            RootScaffold(){ paddingValues ->
                NavigableCircuitContent(
                    modifier = Modifier.padding(paddingValues),
                    navigator = guardedNavigator,
                    backStack = backStack,
                )
            }
        }
    }
}
