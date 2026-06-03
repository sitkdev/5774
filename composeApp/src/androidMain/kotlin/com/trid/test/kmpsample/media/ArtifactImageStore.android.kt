package com.trid.test.kmpsample.media

import android.content.Context
import com.trid.test.kmpsample.data.ArtifactImage
import com.trid.test.kmpsample.data.currentTimeMillis
import java.io.File
import kotlin.random.Random

class AndroidArtifactImageStore(
    private val context: Context,
) : ArtifactImageStore {
    override fun save(bytes: ByteArray): ArtifactImage.Stored {
        val id = newId()
        directory().resolve(fileName(id)).writeBytes(bytes)
        return ArtifactImage.Stored(id)
    }

    override fun load(id: String): ByteArray? {
        val file = safeFile(id) ?: return null
        return runCatching { if (file.exists()) file.readBytes() else null }.getOrNull()
    }

    override fun delete(id: String) {
        val file = safeFile(id) ?: return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun directory(): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun safeFile(id: String): File? =
        id.takeIf { it.matches(ID_REGEX) }?.let { directory().resolve(fileName(it)) }

    private fun newId(): String =
        "img-${currentTimeMillis()}-${Random.Default.nextInt(100_000, 999_999)}"

    private fun fileName(id: String): String = "$id.bin"

    private companion object {
        const val DIRECTORY = "artifact_images"
        val ID_REGEX = Regex("[A-Za-z0-9_-]+")
    }
}
