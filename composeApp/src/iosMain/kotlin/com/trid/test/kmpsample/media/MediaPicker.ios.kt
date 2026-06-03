package com.trid.test.kmpsample.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

// ---------------------------------------------------------------------------
// Gallery picker — PHPickerViewController (no library permission on iOS 14+)
// ---------------------------------------------------------------------------

@Composable
actual fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): MediaPicker {
    val rootController = LocalUIViewController.current
    val onResultState = rememberUpdatedState(onResult)

    val delegate = remember {
        GalleryDelegate { bytes ->
            dispatch_async(dispatch_get_main_queue()) {
                onResultState.value(bytes)
            }
        }
    }

    return MediaPicker {
        when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
            PHAuthorizationStatusAuthorized,
            PHAuthorizationStatusLimited -> {
                presentGalleryPicker(rootController, delegate)
            }

            PHAuthorizationStatusNotDetermined -> {
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { _ ->
                    // PHPickerViewController works for both Authorized and Limited;
                    // present regardless so the system handles the selection scope.
                    dispatch_async(dispatch_get_main_queue()) {
                        presentGalleryPicker(rootController, delegate)
                    }
                }
            }

            else -> {
                // Denied or restricted — surface null; caller decides about Settings redirect.
                onResultState.value(null)
            }
        }
    }
}

private fun presentGalleryPicker(
    rootController: UIViewController,
    delegate: GalleryDelegate,
) {
    val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary())
    config.setSelectionLimit(1L)
    config.setFilter(PHPickerFilter.imagesFilter)
    val picker = PHPickerViewController(config)
    picker.setDelegate(delegate)
    rootController.presentViewController(picker, animated = true, completion = null)
}

private class GalleryDelegate(
    private val onResult: (ByteArray?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        @Suppress("UNCHECKED_CAST")
        val results = didFinishPicking as List<PHPickerResult>

        if (results.isEmpty()) {
            onResult(null)
            return
        }

        results.first().itemProvider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image",
            completionHandler = { data: NSData?, _: NSError? ->
                onResult(data?.toByteArray())
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Camera picker — UIImagePickerController + AVFoundation permission
// ---------------------------------------------------------------------------

@Composable
actual fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): MediaPicker {
    val rootController = LocalUIViewController.current
    val onResultState = rememberUpdatedState(onResult)

    val delegate = remember {
        CameraDelegate { bytes ->
            dispatch_async(dispatch_get_main_queue()) {
                onResultState.value(bytes)
            }
        }
    }

    return MediaPicker {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                presentCameraPicker(rootController, delegate)
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted: Boolean ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) {
                            presentCameraPicker(rootController, delegate)
                        } else {
                            onResultState.value(null)
                        }
                    }
                }
            }

            else -> {
                // Denied or restricted — return null; caller may redirect to Settings.
                onResultState.value(null)
            }
        }
    }
}

private fun presentCameraPicker(
    rootController: UIViewController,
    delegate: CameraDelegate,
) {
    val picker = UIImagePickerController()
    picker.setSourceType(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
    picker.setAllowsEditing(false)
    picker.setDelegate(delegate)
    rootController.presentViewController(picker, animated = true, completion = null)
}

private class CameraDelegate(
    private val onResult: (ByteArray?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        onResult(image?.let { UIImageJPEGRepresentation(it, 0.9) }?.toByteArray())
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

/** Copies [NSData] contents into a new [ByteArray]. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}
