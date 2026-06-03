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
 * - **Seeding:** on first launch (when the encrypted `seeded` flag is false and
 *   nothing is stored) it generates a deterministic demo catalog and persists
 *   it, then flips the flag so seeding never repeats.
 *
 * Determinism: ids and varied fields are derived from a fixed-seed
 * [Random] and a constant [baseEpochMillis] base time offset by index, so a
 * clean install always produces the same catalog. The media/wiring agent can
 * replace [baseEpochMillis] with a real clock later.
 *
 * Registered as a Koin `single` via `dataModule` in `di/Koin.kt`.
 */
class CollectionsRepository(
    private val storage: StorageHelper,
    /** Base timestamp for seeded `dateAddedMillis`; kept constant for determinism. */
    private val baseEpochMillis: Long = DEFAULT_BASE_EPOCH_MILLIS,
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

        if (!seeded && storedCollections.isEmpty() && storedArtifacts.isEmpty()) {
            val (seedCollections, seedArtifacts) = buildSeedData()
            _collections.value = seedCollections
            _artifacts.value = seedArtifacts
            persistCollections()
            persistArtifacts()
            storage.putBoolean(KEY_SEEDED, value = true, encrypted = true)
        } else {
            _collections.value = storedCollections
            _artifacts.value = storedArtifacts
        }
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

    /**
     * Generates a fresh id. The wiring agent may swap this for a real uuid; the
     * seeded ids are stable while runtime-created ids use a fixed-seed [Random]
     * plus a monotonic counter so they never collide within a session.
     */
    fun newId(prefix: String = "art"): String =
        "$prefix-${idCounter++}-${idRandom.nextInt(100_000, 999_999)}"

    // endregion

    // region Seed data (deterministic)

    private fun buildSeedData(): Pair<List<Collection>, List<Artifact>> {
        val rng = Random(SEED)

        val collections = listOf(
            Collection("col-coins", "Coins", "ic_coins"),
            Collection("col-minerals", "Minerals", "ic_capsule"),
            Collection("col-books", "Books", "ic_deco_emblem"),
            Collection("col-figurines", "Figurines", "ic_vase"),
            Collection("col-cards", "Cards", "ic_card"),
        )

        // Per-collection seed parameters: display category, icon used for items,
        // a pool of evocative names, and tag pools.
        val specs = listOf(
            SeedSpec(
                collection = collections[0],
                category = "Coins",
                icon = "ic_coins",
                names = listOf(
                    "Roman Denarius", "Byzantine Solidus", "Spanish Doubloon",
                    "Greek Drachma", "Persian Daric", "Florentine Florin",
                    "Saxon Penny", "Ottoman Akce", "Venetian Ducat",
                ),
                tagPool = listOf("ancient", "silver", "gold", "mint", "rare", "trade"),
                valueRange = 20.0 to 4800.0,
            ),
            SeedSpec(
                collection = collections[1],
                category = "Minerals",
                icon = "ic_capsule",
                names = listOf(
                    "Amethyst Geode", "Raw Pyrite", "Blue Azurite",
                    "Smoky Quartz", "Malachite Cluster", "Labradorite Slab",
                    "Rose Selenite", "Tourmaline Shard",
                ),
                tagPool = listOf("crystal", "raw", "polished", "fluorescent", "specimen"),
                valueRange = 8.0 to 1200.0,
            ),
            SeedSpec(
                collection = collections[2],
                category = "Books",
                icon = "ic_deco_emblem",
                names = listOf(
                    "First Folio Reprint", "Illuminated Psalter", "Pocket Almanac",
                    "Cartographer's Atlas", "Leather Codex", "Naturalist Journal",
                    "Vellum Manuscript",
                ),
                tagPool = listOf("antique", "leather", "signed", "first-edition", "vellum"),
                valueRange = 15.0 to 3200.0,
            ),
            SeedSpec(
                collection = collections[3],
                category = "Figurines",
                icon = "ic_vase",
                names = listOf(
                    "Jade Dragon", "Porcelain Crane", "Bronze Sphinx",
                    "Marble Bust", "Carved Netsuke", "Terracotta Soldier",
                    "Alabaster Idol", "Ivory Elephant",
                ),
                tagPool = listOf("hand-carved", "glazed", "limited", "antique", "display"),
                valueRange = 12.0 to 2600.0,
            ),
            SeedSpec(
                collection = collections[4],
                category = "Cards",
                icon = "ic_card",
                names = listOf(
                    "Holographic Charizard", "Vintage Tarot", "Baseball Rookie",
                    "Foil Black Lotus", "Cigarette Card", "Silver Age Hero",
                ),
                tagPool = listOf("foil", "graded", "vintage", "mint", "trading"),
                valueRange = 5.0 to 9500.0,
            ),
        )

        val rarities = Rarity.entries
        val conditions = listOf(35, 48, 60, 72, 80, 88, 94, 99)
        val locations = listOf(
            "Display Cabinet A", "Safe Box 1", "Drawer 3", "Wall Frame",
            "Archive Shelf", "Velvet Tray", "Climate Vault",
        )

        val artifacts = mutableListOf<Artifact>()
        var globalIndex = 0

        for (spec in specs) {
            val count = 6 + rng.nextInt(5) // 6..10
            for (i in 0 until count) {
                val name = spec.names[i % spec.names.size]
                val rarity = rarities[rng.nextInt(rarities.size)]
                val condition = conditions[rng.nextInt(conditions.size)]
                val (lo, hi) = spec.valueRange
                val value = roundCents(lo + rng.nextDouble() * (hi - lo))
                val tagCount = 1 + rng.nextInt(3)
                val tags = spec.tagPool.shuffled(rng).take(tagCount)
                val location = locations[rng.nextInt(locations.size)]
                // ~20% favorites, deterministically spread.
                val favorite = (globalIndex % 5 == 0)
                // Spread dates: each item one day earlier than the previous.
                val dateMillis = baseEpochMillis - globalIndex.toLong() * DAY_MILLIS

                artifacts += Artifact(
                    id = "seed-${spec.collection.id}-$i",
                    collectionId = spec.collection.id,
                    name = name,
                    description = "A ${rarity.name.lowercase()} ${spec.category.dropLast(1).lowercase()} " +
                        "in well-kept condition. Part of the ${spec.collection.name} collection.",
                    category = spec.category,
                    rarity = rarity,
                    condition = condition,
                    value = value,
                    storageLocation = location,
                    tags = tags,
                    images = listOf(ArtifactImage.Resource(spec.icon)),
                    favorite = favorite,
                    dateAddedMillis = dateMillis,
                )
                globalIndex++
            }
        }

        return collections to artifacts
    }

    private data class SeedSpec(
        val collection: Collection,
        val category: String,
        val icon: String,
        val names: List<String>,
        val tagPool: List<String>,
        val valueRange: Pair<Double, Double>,
    )

    private fun roundCents(v: Double): Double = (v * 100).toLong() / 100.0

    // endregion

    companion object {
        private const val KEY_COLLECTIONS = "collections.v1"
        private const val KEY_ARTIFACTS = "artifacts.v1"
        private const val KEY_SEEDED = "collections.seeded.v1"

        /** Fixed RNG seed → deterministic seed catalog across installs. */
        private const val SEED = 0x5773C0DE

        /** Constant base epoch for seeded timestamps (2024-01-01T00:00:00Z, ms). */
        private const val DEFAULT_BASE_EPOCH_MILLIS = 1_704_067_200_000L

        private const val DAY_MILLIS = 86_400_000L

        // Runtime id generation (session-scoped, collision-free).
        private var idCounter = 0
        private val idRandom = Random(SEED xor 0x1234)
    }
}
