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
import platform.Foundation.NSSortDescriptor
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsVersionCurrent
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

// Single-photo flow: users tap Gallery again to add more photos.
private const val GALLERY_SELECTION_LIMIT = 1L

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
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { newStatus ->
                    dispatch_async(dispatch_get_main_queue()) {
                        when (newStatus) {
                            PHAuthorizationStatusAuthorized ->
                                presentGalleryPicker(rootController, delegate)

                            // Limited: iOS уже показало multi-select-шторку и юзер выбрал
                            // набор фоток — отдаём их пачкой в onResult, без второго пикера.
                            PHAuthorizationStatusLimited ->
                                loadAccessibleImageAssets(onResultState.value)

                            else -> onResultState.value(null)
                        }
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
    // Parameterless config keeps PHPicker out-of-process so the iOS Limited
    // Library multi-select overlay never appears; selectionLimit applies always.
    val config = PHPickerConfiguration()
    config.setSelectionLimit(GALLERY_SELECTION_LIMIT)
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

        // Single-photo flow: selectionLimit caps the picker UI at one item, and we
        // deliberately process only that one item even if more were ever returned.
        val single = results.firstOrNull()
        if (single == null) {
            onResult(null)
            return
        }

        single.itemProvider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image",
            completionHandler = { data: NSData?, _: NSError? ->
                onResult(data?.toByteArray())
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Limited Photo Library — load all photos the user granted access to.
// Called after requestAuthorizationForAccessLevel returns Limited; onResult is
// invoked once per loaded photo (UI accumulates them).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private fun loadAccessibleImageAssets(onResult: (ByteArray?) -> Unit) {
    val options = PHFetchOptions().apply {
        setSortDescriptors(
            listOf(
                NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = true),
            ),
        )
    }
    val fetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options)
    fetchResult.enumerateObjectsUsingBlock { asset, _, _ ->
        (asset as? PHAsset)?.let { loadImageData(it, onResult) }
    }
}

private fun loadImageData(asset: PHAsset, onResult: (ByteArray?) -> Unit) {
    val opts = PHImageRequestOptions().apply {
        setDeliveryMode(PHImageRequestOptionsDeliveryModeHighQualityFormat)
        setVersion(PHImageRequestOptionsVersionCurrent)
        setNetworkAccessAllowed(true) // iCloud photos load over network if needed.
    }
    PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
        asset = asset,
        options = opts,
    ) { data: NSData?, _, _, _ ->
        // Транскодим в JPEG через UIImage: нормализует HEIC/Live/edited под формат,
        // который рендерер гарантированно осилит, и отфильтровывает битые/пустые байты
        // (UIImage(data:) вернёт null → пробросим null дальше → UI пропустит).
        val jpeg = data?.let { UIImage.imageWithData(it) }
            ?.let { UIImageJPEGRepresentation(it, 0.9) }
        dispatch_async(dispatch_get_main_queue()) {
            onResult(jpeg?.toByteArray())
        }
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
