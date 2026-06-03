package com.trid.test.kmpsample.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.SharedPreferencesSettings
import com.trid.test.kmpsample.media.AndroidArtifactImageStore
import com.trid.test.kmpsample.media.ArtifactImageStore
import com.trid.test.kmpsample.storage.SettingsFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private const val PLAIN_PREFS_NAME = "app_storage"
private const val ENCRYPTED_PREFS_NAME = "app_storage_secure"

/**
 * Android storage module: the [SettingsFactory] reads the application [Context]
 * from Koin's `androidContext()` (set in `initKoin { androidContext(...) }`), so no
 * ContentProvider or Application-held context holder is needed.
 */
actual fun platformStorageModule(): Module = module {
    single<SettingsFactory> {
        val context: Context = androidContext()
        SettingsFactory { encrypted ->
            val prefs = if (encrypted) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } else {
                context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
            }
            SharedPreferencesSettings(prefs)
        }
    }
}

actual fun platformImageStoreModule(): Module = module {
    single<ArtifactImageStore> { AndroidArtifactImageStore(androidContext()) }
}
