package com.trid.test.kmpsample.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.contains
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.serialization.json.Json

/**
 * Creates the platform-specific [Settings] backing store. Supplied by the
 * platform Koin module (see `di/Koin.kt`):
 * - Android: plain `SharedPreferences` / [androidx.security.crypto.EncryptedSharedPreferences]
 * - iOS: `NSUserDefaults` / Keychain
 *
 * @param encrypted when `true`, returns a store backed by platform encryption.
 */
fun interface SettingsFactory {
    fun create(encrypted: Boolean): Settings
}

/**
 * Type-safe, cross-platform persistence for app state.
 *
 * Strings and primitives are stored directly. Complex models annotated with
 * `@Serializable` can be stored via [putObject] / [getObject] (encoded to JSON).
 *
 * Every method takes an [encrypted] flag that routes the read/write to either a
 * plain store or a platform-encrypted store. The two stores are independent: a key
 * written with `encrypted = true` is not visible with `encrypted = false`.
 *
 * Provided as a singleton through Koin (`single { StorageHelper(get()) }`); obtain it
 * with `koinInject<StorageHelper>()` (koin-compose) or `getKoin().get<StorageHelper>()`.
 */
class StorageHelper(private val factory: SettingsFactory) {

    private val plainSettings: Settings by lazy { factory.create(encrypted = false) }
    private val encryptedSettings: Settings by lazy { factory.create(encrypted = true) }

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun settings(encrypted: Boolean): Settings =
        if (encrypted) encryptedSettings else plainSettings

    // region String

    fun putString(key: String, value: String, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getString(key: String, default: String = "", encrypted: Boolean = false): String =
        settings(encrypted).getString(key, default)

    fun getStringOrNull(key: String, encrypted: Boolean = false): String? =
        settings(encrypted).getStringOrNull(key)

    // endregion

    // region Int

    fun putInt(key: String, value: Int, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getInt(key: String, default: Int = 0, encrypted: Boolean = false): Int =
        settings(encrypted).getInt(key, default)

    fun getIntOrNull(key: String, encrypted: Boolean = false): Int? =
        settings(encrypted).getIntOrNull(key)

    // endregion

    // region Long

    fun putLong(key: String, value: Long, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getLong(key: String, default: Long = 0L, encrypted: Boolean = false): Long =
        settings(encrypted).getLong(key, default)

    fun getLongOrNull(key: String, encrypted: Boolean = false): Long? =
        settings(encrypted).getLongOrNull(key)

    // endregion

    // region Float

    fun putFloat(key: String, value: Float, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getFloat(key: String, default: Float = 0f, encrypted: Boolean = false): Float =
        settings(encrypted).getFloat(key, default)

    fun getFloatOrNull(key: String, encrypted: Boolean = false): Float? =
        settings(encrypted).getFloatOrNull(key)

    // endregion

    // region Double

    fun putDouble(key: String, value: Double, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getDouble(key: String, default: Double = 0.0, encrypted: Boolean = false): Double =
        settings(encrypted).getDouble(key, default)

    fun getDoubleOrNull(key: String, encrypted: Boolean = false): Double? =
        settings(encrypted).getDoubleOrNull(key)

    // endregion

    // region Boolean

    fun putBoolean(key: String, value: Boolean, encrypted: Boolean = false) {
        settings(encrypted)[key] = value
    }

    fun getBoolean(key: String, default: Boolean = false, encrypted: Boolean = false): Boolean =
        settings(encrypted).getBoolean(key, default)

    fun getBooleanOrNull(key: String, encrypted: Boolean = false): Boolean? =
        settings(encrypted).getBooleanOrNull(key)

    // endregion

    // region Serializable objects

    /**
     * Encodes [value] to JSON and stores it under [key] via the String API.
     */
    inline fun <reified T> putObject(key: String, value: T, encrypted: Boolean = false) {
        putString(key, jsonInstance.encodeToString(value), encrypted)
    }

    /**
     * Reads and decodes a JSON-encoded object previously written with [putObject].
     * Returns `null` when the key is absent or the stored payload fails to decode.
     */
    inline fun <reified T> getObject(key: String, encrypted: Boolean = false): T? {
        val raw = getStringOrNull(key, encrypted) ?: return null
        return runCatching { jsonInstance.decodeFromString<T>(raw) }.getOrNull()
    }

    // endregion

    // region Lifecycle

    fun remove(key: String, encrypted: Boolean = false) {
        settings(encrypted).remove(key)
    }

    fun contains(key: String, encrypted: Boolean = false): Boolean =
        settings(encrypted).contains(key)

    fun clear(encrypted: Boolean = false) {
        settings(encrypted).clear()
    }

    // endregion

    /**
     * Exposes the configured [Json] instance to the `inline` object accessors.
     * Not intended for direct use; prefer [putObject] / [getObject].
     */
    @PublishedApi
    internal val jsonInstance: Json
        get() = json
}
