package com.trid.test.kmpsample.navigation

/**
 * Multiplatform-safe Parcelize support for Circuit [Screen] keys.
 *
 * On Android, Circuit's `Screen` actual extends `Parcelable`, so screen keys
 * must be parcelable for backstack saving and deep-link restoration. On iOS the
 * `Screen` actual is a bare interface with no such requirement.
 *
 * To satisfy both from commonMain without leaking `kotlinx.parcelize` into
 * iOS code, we use the standard KMP pattern:
 *
 *  - [CommonParcelize] is an `expect` annotation that maps to
 *    `@kotlinx.parcelize.Parcelize` on Android and to a no-op on iOS.
 *
 * Usage:
 * ```kotlin
 * @CommonParcelize
 * data object HomeScreen : Screen
 * ```
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class CommonParcelize()
