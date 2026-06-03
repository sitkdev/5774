package com.trid.test.kmpsample.data

import com.trid.test.kmpsample.storage.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Single source of truth for collections and artifacts.
 *
 * - Holds two [MutableStateFlow]s and exposes them read-only as [collections]
 *   and [artifacts]; presenters observe these reactively
 *   (`repo.artifacts.collectAsState()`).
 * - **Persistence:** all reads/writes go through [StorageHelper] with
 *   `encrypted = true` (Android EncryptedSharedPreferences / iOS Keychain) —
 *   the user requires every piece of app data to be encrypted at rest.
 * - Fresh installs start empty: users create their own collections and artifacts.
 *   Legacy seed-only data from earlier builds is removed only when it is safely
 *   identifiable, so user-created data is never wiped accidentally.
 *
 * Registered as a Koin `single` via `dataModule` in `di/Koin.kt`.
 */
class CollectionsRepository(
    private val storage: StorageHelper,
) {

    private val _collections = MutableStateFlow<List<Collection>>(emptyList())
    val collections: StateFlow<List<Collection>> = _collections.asStateFlow()

    private val _artifacts = MutableStateFlow<List<Artifact>>(emptyList())
    val artifacts: StateFlow<List<Artifact>> = _artifacts.asStateFlow()

    init {
        load()
    }

    // region Load / persist

    private fun load() {
        val storedCollections =
            storage.getObject<List<Collection>>(KEY_COLLECTIONS, encrypted = true).orEmpty()
        val storedArtifacts =
            storage.getObject<List<Artifact>>(KEY_ARTIFACTS, encrypted = true).orEmpty()
        val seeded = storage.getBoolean(KEY_SEEDED, default = false, encrypted = true)

        if (seeded && isLegacySeedOnly(storedCollections, storedArtifacts)) {
            storage.remove(KEY_COLLECTIONS, encrypted = true)
            storage.remove(KEY_ARTIFACTS, encrypted = true)
            storage.remove(KEY_SEEDED, encrypted = true)
            _collections.value = emptyList()
            _artifacts.value = emptyList()
        } else {
            _collections.value = storedCollections
            _artifacts.value = storedArtifacts
        }
    }

    private fun isLegacySeedOnly(
        collections: List<Collection>,
        artifacts: List<Artifact>,
    ): Boolean {
        if (collections.isEmpty() && artifacts.isEmpty()) return false
        return collections.all { it.id in LEGACY_SEED_COLLECTION_IDS } &&
            artifacts.all { it.id.startsWith(LEGACY_SEED_ARTIFACT_PREFIX) }
    }

    private fun persistCollections() {
        storage.putObject(KEY_COLLECTIONS, _collections.value, encrypted = true)
    }

    private fun persistArtifacts() {
        storage.putObject(KEY_ARTIFACTS, _artifacts.value, encrypted = true)
    }

    // endregion

    // region Queries

    fun artifactsIn(collectionId: String): List<Artifact> =
        _artifacts.value.filter { it.collectionId == collectionId }

    fun artifact(id: String): Artifact? =
        _artifacts.value.firstOrNull { it.id == id }

    fun collection(id: String): Collection? =
        _collections.value.firstOrNull { it.id == id }

    val favorites: List<Artifact>
        get() = _artifacts.value.filter { it.favorite }

    fun recent(n: Int): List<Artifact> =
        _artifacts.value.sortedByDescending { it.dateAddedMillis }.take(n)

    fun dashboardStats(): DashboardStats {
        val items = _artifacts.value
        return DashboardStats(
            artifactCount = items.size,
            collectionCount = _collections.value.size,
            favoriteCount = items.count { it.favorite },
            totalValue = items.sumOf { it.value },
        )
    }

    fun search(query: String): List<Artifact> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return _artifacts.value.filter { artifact ->
            artifact.name.contains(q, ignoreCase = true) ||
                artifact.description.contains(q, ignoreCase = true) ||
                artifact.category.contains(q, ignoreCase = true) ||
                artifact.tags.any { it.contains(q, ignoreCase = true) }
        }
    }

    // endregion

    // region Sort helpers

    fun sortedByRarity(items: List<Artifact>, descending: Boolean = true): List<Artifact> =
        if (descending) items.sortedByDescending { it.rarity.ordinal }
        else items.sortedBy { it.rarity.ordinal }

    fun sortedByValue(items: List<Artifact>, descending: Boolean = true): List<Artifact> =
        if (descending) items.sortedByDescending { it.value }
        else items.sortedBy { it.value }

    fun sortedByDate(items: List<Artifact>, descending: Boolean = true): List<Artifact> =
        if (descending) items.sortedByDescending { it.dateAddedMillis }
        else items.sortedBy { it.dateAddedMillis }

    // endregion

    // region Mutations (each persists)

    fun addArtifact(artifact: Artifact) {
        _artifacts.value = _artifacts.value + artifact
        persistArtifacts()
    }

    fun updateArtifact(artifact: Artifact) {
        _artifacts.value = _artifacts.value.map { if (it.id == artifact.id) artifact else it }
        persistArtifacts()
    }

    fun toggleFavorite(id: String) {
        _artifacts.value = _artifacts.value.map {
            if (it.id == id) it.copy(favorite = !it.favorite) else it
        }
        persistArtifacts()
    }

    fun removeArtifact(id: String) {
        _artifacts.value = _artifacts.value.filterNot { it.id == id }
        persistArtifacts()
    }

    fun addCollection(collection: Collection) {
        _collections.value = _collections.value + collection
        persistCollections()
    }

    fun removeCollection(id: String) {
        _collections.value = _collections.value.filterNot { it.id == id }
        _artifacts.value = _artifacts.value.filterNot { it.collectionId == id }
        persistCollections()
        persistArtifacts()
    }

    /** Generates a fresh id and checks it against already persisted model ids. */
    fun newId(prefix: String = "art"): String {
        val existingIds = buildSet {
            _collections.value.forEach { add(it.id) }
            _artifacts.value.forEach { add(it.id) }
        }
        var candidate: String
        do {
            candidate = "$prefix-${currentTimeMillis()}-${Random.Default.nextInt(100_000, 999_999)}"
        } while (candidate in existingIds)
        return candidate
    }

    // endregion

    companion object {
        private const val KEY_COLLECTIONS = "collections.v1"
        private const val KEY_ARTIFACTS = "artifacts.v1"
        private const val KEY_SEEDED = "collections.seeded.v1"

        private const val LEGACY_SEED_ARTIFACT_PREFIX = "seed-"
        private val LEGACY_SEED_COLLECTION_IDS = setOf(
            "col-coins",
            "col-minerals",
            "col-books",
            "col-figurines",
            "col-cards",
        )

    }
}
