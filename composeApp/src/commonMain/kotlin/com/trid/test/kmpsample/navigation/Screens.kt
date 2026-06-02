package com.trid.test.kmpsample.navigation

import com.slack.circuit.runtime.screen.Screen

/**
 * Type-safe Circuit [Screen] keys for the app's navigation tree.
 *
 * Navigation tree:
 *
 *   LoadingScreen (root / entry point)
 *        |
 *        |-- success --> HomeScreen        (the menu / main container)
 *        |
 *        '-- no network --> NoConnectionScreen
 *                                |
 *                                '-- retry --> back to LoadingScreen
 *
 * In Kotlin Multiplatform the Circuit `Screen` type does not require
 * `Parcelable`/`@Parcelize` in commonMain — plain `data object`/`data class`
 * keys are saveable via Circuit's own backstack saver. This keeps the keys
 * iOS-safe (no `kotlinx.parcelize`).
 *
 * Naming convention: one screen key per destination, suffixed `Screen`,
 * grouped in this single file so the whole tree is greppable in one place.
 */

/** Root entry point. Decides where to route once startup work completes. */
@CommonParcelize
data object LoadingScreen : Screen

/** Global error screen shown when there is no network connectivity. */
@CommonParcelize
data object NoConnectionScreen : Screen

/** Main menu / home container placeholder. */
@CommonParcelize
data object HomeScreen : Screen
