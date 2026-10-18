package dev.surco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.surco.ios.IosLibrary
import dev.surco.ios.toByteArray
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import dev.surco.resources.Res
import dev.surco.resources.figtree_bold
import dev.surco.resources.figtree_extrabold
import dev.surco.resources.figtree_medium
import dev.surco.resources.figtree_regular
import dev.surco.resources.figtree_semibold
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberDirectoryPicker(onPicked: (String) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { DirectoryDelegate { url -> IosLibrary.rootFor(url)?.let { callback.value(it) } } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            topViewController()?.presentViewController(picker, animated = true, completion = null)
            Unit
        }
    }
}

@Composable
actual fun rememberFolderReleaser(): (String) -> Unit = remember { { _: String -> } }

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { ImageDelegate { bytes -> callback.value(bytes) } }
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
            Unit
        }
    }
}

@Suppress("DEPRECATION")
private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private class DirectoryDelegate(val onUrl: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onUrl)
    }
}

private class ImageDelegate(val onBytes: (ByteArray?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val bytes = data?.toByteArray()
            dispatch_async(dispatch_get_main_queue()) { onBytes(bytes) }
        }
    }
}

@Composable
actual fun figtreeFamily(): FontFamily = FontFamily(
    Font(Res.font.figtree_regular, FontWeight.Normal),
    Font(Res.font.figtree_medium, FontWeight.Medium),
    Font(Res.font.figtree_semibold, FontWeight.SemiBold),
    Font(Res.font.figtree_bold, FontWeight.Bold),
    Font(Res.font.figtree_extrabold, FontWeight.ExtraBold),
)

actual suspend fun extractArtColor(context: PlatformContext, path: String): Color? = runCatching {
    val data: Any = if (path.startsWith("http")) path else path.toPath()
    val req = ImageRequest.Builder(context).data(data).size(Size(64, 64)).build()
    val bmp = (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap() ?: return@runCatching null
    val pixels = ArrayList<Int>()
    val step = maxOf(1, minOf(bmp.width, bmp.height) / 24)
    var y = 0
    while (y < bmp.height) {
        var x = 0
        while (x < bmp.width) { pixels += bmp.getColor(x, y); x += step }
        y += step
    }
    dominantColor(pixels)?.let { lerp(it, Color.Black, 0.25f) }
}.getOrNull()

actual fun folderLabel(root: String): String = runCatching { IosLibrary.label(root) }.getOrDefault(root)

@Composable
actual fun rememberSharer(): Sharer = remember {
    object : Sharer {
        override fun shareText(text: String, title: String) = presentShareSheet(listOf(text))

        override fun shareFile(track: dev.surco.data.Track) {
            val url = NSURL.URLWithString(track.uri) ?: return
            presentShareSheet(listOf(url))
        }
    }
}

/** Hoja de compartir de iOS (AirDrop, Mensajes, WhatsApp, Guardar en Archivos…). */
private fun presentShareSheet(items: List<Any>) {
    val top = topViewController() ?: return
    val vc = platform.UIKit.UIActivityViewController(activityItems = items, applicationActivities = null)
    vc.popoverPresentationController?.sourceView = top.view
    top.presentViewController(vc, animated = true, completion = null)
}

/** En el iPhone la barra de estado ya se esconde sola en horizontal. */
@androidx.compose.runtime.Composable
actual fun HideSystemBars() {}
