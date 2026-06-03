package com.trid.test.kmpsample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mic_kmp_sample.composeapp.generated.resources.Res
import mic_kmp_sample.composeapp.generated.resources.ic_badge
import mic_kmp_sample.composeapp.generated.resources.ic_capsule
import mic_kmp_sample.composeapp.generated.resources.ic_card
import mic_kmp_sample.composeapp.generated.resources.ic_coins
import mic_kmp_sample.composeapp.generated.resources.ic_deco_emblem
import mic_kmp_sample.composeapp.generated.resources.ic_diamond
import mic_kmp_sample.composeapp.generated.resources.ic_medallion
import mic_kmp_sample.composeapp.generated.resources.ic_open_box
import mic_kmp_sample.composeapp.generated.resources.ic_ornament
import mic_kmp_sample.composeapp.generated.resources.ic_seal
import mic_kmp_sample.composeapp.generated.resources.ic_vase
import org.jetbrains.compose.resources.DrawableResource

/**
 * Domain models for the collecting app.
 *
 * All models are `@Serializable` so they can be persisted through
 * [com.trid.test.kmpsample.storage.StorageHelper.putObject] / `getObject`
 * (JSON) — every write goes to the **encrypted** store (see
 * [com.trid.test.kmpsample.data.CollectionsRepository]).
 *
 * These are plain serializable types (NOT `@CommonParcelize`): they travel as
 * data inside Circuit state, not as navigation keys. Only the lightweight ids
 * cross the navigation boundary (see `navigation/Screens.kt`).
 */

/** Relative scarcity of an artifact, used for sorting, filtering and accent color. */
enum class Rarity {
    Common,
    Uncommon,
    Rare,
    Epic,
    Legendary,
}

/**
 * The visual for an artifact. Either:
 *  - [Resource]: a bundled drawable referenced by its generated-accessor [name]
 *    (e.g. `"ic_coins"`); resolve with [resolveDrawable].
 *  - [Bytes]: user-supplied image bytes, base64-encoded for JSON persistence
 *    (populated later by the media-wiring agent from the gallery/camera picker).
 */
@Serializable
sealed interface ArtifactImage {

    @Serializable
    @SerialName("resource")
    data class Resource(val name: String) : ArtifactImage

    @Serializable
    @SerialName("bytes")
    data class Bytes(val base64: String) : ArtifactImage
}

/**
 * A user collection grouping artifacts of one theme.
 *
 * @property iconKey generated drawable-accessor name (resolve via [DrawableKeys.resolve]).
 */
@Serializable
data class Collection(
    val id: String,
    val name: String,
    val iconKey: String,
)

/** A single collected item. `condition` is 0..100; `value` is in the app's display currency. */
@Serializable
data class Artifact(
    val id: String,
    val collectionId: String,
    val name: String,
    val description: String,
    val category: String,
    val rarity: Rarity,
    val condition: Int,
    val value: Double,
    val storageLocation: String,
    val tags: List<String>,
    val images: List<ArtifactImage>,
    val favorite: Boolean,
    val dateAddedMillis: Long,
)

/** Aggregate numbers shown on the Dashboard hub. */
data class DashboardStats(
    val artifactCount: Int,
    val collectionCount: Int,
    val favoriteCount: Int,
    val totalValue: Double,
)

/**
 * Maps the string drawable keys stored in models to the generated
 * [DrawableResource] accessors. Keeping the lookup here (rather than scattering
 * `Res.drawable.*` references through the UI) means the UI agent resolves any
 * key — bundled or seeded — through one function.
 */
object DrawableKeys {

    /** All keys the seed data may reference, plus extras the UI can offer when adding. */
    val all: Map<String, DrawableResource> = mapOf(
        "ic_coins" to Res.drawable.ic_coins,
        "ic_capsule" to Res.drawable.ic_capsule,
        "ic_deco_emblem" to Res.drawable.ic_deco_emblem,
        "ic_vase" to Res.drawable.ic_vase,
        "ic_card" to Res.drawable.ic_card,
        "ic_open_box" to Res.drawable.ic_open_box,
        "ic_badge" to Res.drawable.ic_badge,
        "ic_diamond" to Res.drawable.ic_diamond,
        "ic_medallion" to Res.drawable.ic_medallion,
        "ic_ornament" to Res.drawable.ic_ornament,
        "ic_seal" to Res.drawable.ic_seal,
    )

    /** Fallback drawable used when a key is unknown. */
    val fallback: DrawableResource = Res.drawable.ic_open_box

    /** Resolves a stored key to a drawable, falling back to [fallback]. */
    fun resolve(key: String): DrawableResource = all[key] ?: fallback
}

/**
 * Resolves an [ArtifactImage.Resource] name to a [DrawableResource].
 * For [ArtifactImage.Bytes] callers should decode the base64 separately (the
 * media agent owns that path); this helper covers only bundled resources.
 */
fun ArtifactImage.Resource.resolveDrawable(): DrawableResource =
    DrawableKeys.resolve(name)
