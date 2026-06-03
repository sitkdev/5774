package com.trid.test.kmpsample.media

import com.trid.test.kmpsample.data.ArtifactImage

/**
 * KMP-safe app-private file store for real user photos.
 *
 * Artifact metadata remains persisted through encrypted [StorageHelper]; only
 * the lightweight [ArtifactImage.Stored.id] is stored there. Raw image bytes are
 * written to platform app-private storage to avoid base64/encrypted-prefs OOMs.
 */
interface ArtifactImageStore {
    fun save(bytes: ByteArray): ArtifactImage.Stored
    fun load(id: String): ByteArray?
    fun delete(id: String)
}
