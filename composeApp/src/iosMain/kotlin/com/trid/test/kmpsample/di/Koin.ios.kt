package com.trid.test.kmpsample.di

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.trid.test.kmpsample.media.ArtifactImageStore
import com.trid.test.kmpsample.media.IosArtifactImageStore
import com.trid.test.kmpsample.storage.SettingsFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

private const val FALLBACK_BUNDLE_ID = "com.trid.test.kmpsample"

/**
 * Keychain service id, namespaced under the running app's bundle id with a `.secure`
 * suffix so encrypted entries never collide with other Keychain consumers.
 */
private val keychainService: String
    get() = "${NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_BUNDLE_ID}.secure"

/**
 * iOS storage module: plain store via `NSUserDefaults`, encrypted store via Keychain.
 */
@OptIn(ExperimentalSettingsImplementation::class)
actual fun platformStorageModule(): Module = module {
    single<SettingsFactory> {
        SettingsFactory { encrypted ->
            if (encrypted) {
                KeychainSettings(service = keychainService)
            } else {
                NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
            }
        }
    }
}

actual fun platformImageStoreModule(): Module = module {
    single<ArtifactImageStore> { IosArtifactImageStore() }
}
