package com.trid.test.kmpsample.navigation

import com.slack.circuit.runtime.screen.Screen

/**
 * Type-safe Circuit [Screen] keys for the app's navigation tree.
 *
 * Navigation tree (Dashboard-hub model, no bottom bar):
 *
 *   LoadingScreen (root / entry point)
 *        |
 *        |-- success --> DashboardScreen  (the hub)
 *        |                    |
 *        |                    |-- CollectionsScreen --> CollectionArtifactsScreen(id) --> ArtifactDetailsScreen(id)
 *        |                    |-- ShowcaseScreen ----------------------------------------> ArtifactDetailsScreen(id)
 *        |                    |-- AddArtifactScreen (save -> pop)
 *        |                    '-- recent item -------------------------------------------> ArtifactDetailsScreen(id)
 *        |
 *        '-- no network --> NoConnectionScreen
 *                                |
 *                                '-- retry --> back to LoadingScreen
 *
 * Only lightweight ids (`collectionId`, `artifactId`) cross the navigation
 * boundary as `data class` keys; full models are fetched from
 * [com.trid.test.kmpsample.data.CollectionsRepository] inside each presenter.
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

/** Hub screen shown after loading: stats, recent items, and entry points. */
@CommonParcelize
data object DashboardScreen : Screen

/** Grid/list of all collections. */
@CommonParcelize
data object CollectionsScreen : Screen

/** Artifacts belonging to a single collection. */
@CommonParcelize
data class CollectionArtifactsScreen(val collectionId: String) : Screen

/** Full detail of one artifact (view / favorite / delete). */
@CommonParcelize
data class ArtifactDetailsScreen(val artifactId: String) : Screen

/** Form for adding a new artifact; optionally preselects a source collection. */
@CommonParcelize
data class AddArtifactScreen(val collectionId: String? = null) : Screen

/** Curated cross-collection showcase (favorites / highlights). */
@CommonParcelize
data object ShowcaseScreen : Screen
