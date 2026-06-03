package com.trid.test.kmpsample.media

import androidx.compose.runtime.Composable

/**
 * A handle returned by [rememberGalleryPicker] and [rememberCameraPicker] that
 * the caller uses to trigger the platform picker UI.
 */
fun interface MediaPicker {
    fun launch()
}

/**
 * Creates and remembers a gallery picker backed by the platform's native photo
 * selection UI.  Invokes [onResult] on the main thread with the selected image as
 * JPEG bytes, or `null` when the user cancels or denies permission.
 */
@Composable
expect fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): MediaPicker

/**
 * Creates and remembers a camera picker that captures a photo using the device
 * camera.  Invokes [onResult] on the main thread with the captured image as JPEG
 * bytes, or `null` when the user cancels or denies permission.
 */
@Composable
expect fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): MediaPicker
