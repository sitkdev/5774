package com.trid.test.kmpsample.navigation

import kotlinx.parcelize.Parcelize

/**
 * On Android, [CommonParcelize] maps to `@kotlinx.parcelize.Parcelize` so
 * Circuit [Screen] keys become `Parcelable` (required by the Android `Screen`
 * actual for backstack saving / deep-link restoration).
 */
actual typealias CommonParcelize = Parcelize
