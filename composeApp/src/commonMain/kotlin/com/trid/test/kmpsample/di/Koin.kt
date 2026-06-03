package com.trid.test.kmpsample.di

import com.trid.test.kmpsample.data.CollectionsRepository
import com.trid.test.kmpsample.media.ArtifactImageStore
import com.trid.test.kmpsample.storage.StorageHelper
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * Provides the platform-specific [com.trid.test.kmpsample.storage.SettingsFactory]
 * (Android: needs an `androidContext()`; iOS: NSUserDefaults / Keychain).
 */
expect fun platformStorageModule(): Module

/** Provides the platform app-private image file store. */
expect fun platformImageStoreModule(): Module

/**
 * Shared DI graph. [StorageHelper] is a process singleton built from the
 * platform-provided `SettingsFactory`.
 */
val storageModule: Module = module {
    single { StorageHelper(get()) }
}

/**
 * Data graph. [CollectionsRepository] is a process singleton built on top of
 * [StorageHelper] and [ArtifactImageStore]; it owns user-created
 * collections/artifacts state and cleans up stored photo files on deletion.
 * Presenters obtain it via `KoinPlatform.getKoin().get<CollectionsRepository>()`.
 */
val dataModule: Module = module {
    single { CollectionsRepository(get(), get()) }
}

/**
 * Starts Koin once per process. Safe to call from each platform entry point:
 * the [KoinPlatform.getKoinOrNull] guard makes repeat calls (e.g. an iOS view
 * controller being recreated) a no-op.
 *
 * @param appDeclaration platform hook — Android passes `androidContext(...)` here.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin {
        appDeclaration()
        modules(platformStorageModule(), platformImageStoreModule(), storageModule, dataModule)
    }
}
