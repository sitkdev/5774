package com.trid.test.kmpsample.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

// ---------------------------------------------------------------------------
// Gallery picker — Android Photo Picker (no storage permission required)
// ---------------------------------------------------------------------------

@Composable
actual fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): MediaPicker {
    val onResultState = rememberUpdatedState(onResult)
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        onResultState.value(uri?.readBytes(context))
    }

    return MediaPicker {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

// ---------------------------------------------------------------------------
// Camera picker — TakePicture via FileProvider + runtime CAMERA permission
// ---------------------------------------------------------------------------

@Composable
actual fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): MediaPicker {
    val onResultState = rememberUpdatedState(onResult)
    val context = LocalContext.current

    // Holds the URI for the current in-flight capture.  A fresh file is created on
    // every launch() call so stale files are never re-used after cleanup.
    val pendingUri = remember { mutableStateOf<Uri?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        val uri = pendingUri.value
        pendingUri.value = null
        if (success && uri != null) {
            val bytes = uri.readBytes(context)
            // Delete the temp file after reading.
            runCatching {
                File(context.cacheDir, CAMERA_PHOTOS_DIR).listFiles()?.forEach { it.delete() }
            }
            onResultState.value(bytes)
        } else {
            onResultState.value(null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            val uri = createTempCameraUri(context)
            pendingUri.value = uri
            uri?.let { captureLauncher.launch(it) } ?: onResultState.value(null)
        } else {
            onResultState.value(null)
        }
    }

    return MediaPicker {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED -> {
                val uri = createTempCameraUri(context)
                pendingUri.value = uri
                uri?.let { captureLauncher.launch(it) } ?: onResultState.value(null)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private const val CAMERA_PHOTOS_DIR = "camera_photos"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

/**
 * Creates a new temp file under the app cache dir and returns its [Uri] via the
 * FileProvider.  Returns `null` if file creation fails.
 */
private fun createTempCameraUri(context: Context): Uri? =
    runCatching {
        val dir = File(context.cacheDir, CAMERA_PHOTOS_DIR).also { it.mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        FileProvider.getUriForFile(context, authority, file)
    }.getOrNull()

/** Reads all bytes from a [Uri] via the [ContentResolver], or returns `null` on failure. */
private fun Uri.readBytes(context: Context): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(this)?.use { it.readBytes() }
    }.getOrNull()
