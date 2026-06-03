package com.trid.test.kmpsample.media

import com.trid.test.kmpsample.data.ArtifactImage
import com.trid.test.kmpsample.data.currentTimeMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
class IosArtifactImageStore : ArtifactImageStore {
    override fun save(bytes: ByteArray): ArtifactImage.Stored {
        val id = newId()
        val path = pathFor(id) ?: error("Image directory unavailable")
        val file = fopen(path, "wb") ?: error("Image file unavailable")
        try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
                }
            }
        } finally {
            fclose(file)
        }
        return ArtifactImage.Stored(id)
    }

    override fun load(id: String): ByteArray? {
        val path = pathFor(id) ?: return null
        val file = fopen(path, "rb") ?: return null
        return try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            if (size <= 0) return ByteArray(0)
            ByteArray(size).also { bytes ->
                bytes.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1.convert(), size.convert(), file)
                }
            }
        } finally {
            fclose(file)
        }
    }

    override fun delete(id: String) {
        val path = pathFor(id) ?: return
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }

    private fun pathFor(id: String): String? =
        id.takeIf { it.matches(ID_REGEX) }?.let { directoryPath()?.let { dir -> "$dir/${fileName(id)}" } }

    private fun directoryPath(): String? {
        val manager = NSFileManager.defaultManager
        val baseUrl: NSURL = manager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: return null
        val dir = baseUrl.URLByAppendingPathComponent(DIRECTORY, isDirectory = true) ?: return null
        manager.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return dir.path
    }

    private fun newId(): String =
        "img-${currentTimeMillis()}-${Random.Default.nextInt(100_000, 999_999)}"

    private fun fileName(id: String): String = "$id.bin"

    private companion object {
        const val DIRECTORY = "artifact_images"
        val ID_REGEX = Regex("[A-Za-z0-9_-]+")
    }
}
