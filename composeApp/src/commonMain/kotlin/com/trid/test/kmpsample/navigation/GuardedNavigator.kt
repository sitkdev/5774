package com.trid.test.kmpsample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A spam-click / race-condition guard around a Circuit [Navigator].
 *
 * Two complementary protections:
 *
 *  1. **Debounce window** — any two forward/back navigation actions fired
 *     closer together than [debounceMillis] are treated as an accidental
 *     double-tap and the second is dropped. This is the lowest-level guard and
 *     covers goTo / pop / forward / backward / resetRoot.
 *
 *  2. **Duplicate top guard** — [goTo] additionally refuses to push a screen
 *     that is already on top of the backstack, so rapid taps on the same
 *     button can never stack the same destination twice.
 *
 * Every mutating action is wrapped in a try/catch so a failing transition is
 * logged rather than crashing the app; the guard state is always released.
 *
 * This type itself implements [Navigator], so it can be handed directly to
 * `NavigableCircuitContent` while remaining the single integration point for
 * navigation safety. Read-only accessors ([peek], [peekBackStack],
 * [peekNavStack]) are delegated through untouched.
 */
class GuardedNavigator(
    private val delegate: Navigator,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : Navigator {

    private var lastMark: TimeMark? = null

    /** @return true when enough time has elapsed since the last accepted action. */
    private fun passesDebounce(): Boolean {
        val previous = lastMark
        if (previous != null && previous.elapsedNow().inWholeMilliseconds < debounceMillis) {
            Logger.d(TAG) { "Navigation ignored (debounced, <${debounceMillis}ms)" }
            return false
        }
        lastMark = timeSource.markNow()
        return true
    }

    override fun goTo(screen: Screen): Boolean {
        if (!passesDebounce()) return false
        // Duplicate-top guard: never push the same destination twice in a row.
        if (peek() == screen) {
            Logger.d(TAG) { "goTo($screen) ignored (already on top)" }
            return false
        }
        return runCatching { delegate.goTo(screen) }
            .onFailure { Logger.e(TAG, it) { "goTo($screen) failed" } }
            .getOrDefault(false)
    }

    override fun forward(): Boolean {
        if (!passesDebounce()) return false
        return runCatching { delegate.forward() }
            .onFailure { Logger.e(TAG, it) { "forward() failed" } }
            .getOrDefault(false)
    }

    override fun backward(): Boolean {
        if (!passesDebounce()) return false
        return runCatching { delegate.backward() }
            .onFailure { Logger.e(TAG, it) { "backward() failed" } }
            .getOrDefault(false)
    }

    override fun pop(result: PopResult?): Screen? {
        if (!passesDebounce()) return null
        return runCatching { delegate.pop(result) }
            .onFailure { Logger.e(TAG, it) { "pop() failed" } }
            .getOrNull()
    }

    override fun resetRoot(
        newRoot: Screen,
        options: Navigator.StateOptions,
    ): List<Screen> {
        if (!passesDebounce()) return emptyList()
        return runCatching { delegate.resetRoot(newRoot, options) }
            .onFailure { Logger.e(TAG, it) { "resetRoot($newRoot) failed" } }
            .getOrDefault(emptyList())
    }

    // --- Read-only pass-throughs (never guarded) ---

    override fun peek(): Screen? = delegate.peek()

    override fun peekBackStack(): List<Screen> = delegate.peekBackStack()

    override fun peekNavStack(): NavStackList<Screen>? = delegate.peekNavStack()

    companion object {
        private const val TAG = "GuardedNavigator"
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 400L
    }
}

/**
 * CompositionLocal exposing the app's guarded navigator to any composable in
 * the tree. Presenters receive a [Navigator] directly from Circuit, but UI
 * code (e.g. a deeply nested button) can read this without prop-drilling.
 *
 * Defaults to throwing so a missing provider is caught immediately in dev.
 */
val LocalGuardedNavigator = compositionLocalOf<Navigator> {
    error("No GuardedNavigator provided. Wrap your content in App()'s navigation host.")
}

/**
 * Remembers a [GuardedNavigator] wrapping [delegate] for the lifetime of the
 * composition. Stable across recomposition so the debounce mark persists.
 */
@Composable
fun rememberGuardedNavigator(delegate: Navigator): GuardedNavigator =
    remember(delegate) { GuardedNavigator(delegate) }
