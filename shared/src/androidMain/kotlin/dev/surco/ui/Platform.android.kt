package dev.surco.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.palette.graphics.Palette
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.surco.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
actual fun rememberDirectoryPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, RW) }
                .onFailure {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
            onPicked(uri.toString())
        }
    }
    return { launcher.launch(null) }
}

@Composable
actual fun rememberFolderReleaser(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { old: String -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), RW) } }
    }
}

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                onPicked(bytes)
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private val figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

@Composable
actual fun figtreeFamily(): FontFamily = figtree

actual suspend fun extractArtColor(context: PlatformContext, path: String): Color? = withContext(Dispatchers.IO) {
    val bmp = if (path.startsWith("http")) {
        runCatching {
            val req = ImageRequest.Builder(context).data(path).allowHardware(false).size(160).build()
            (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    } else {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
    } ?: return@withContext null
    runCatching {
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val swatch = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch ?: return@runCatching null
        // Oscurecemos un poco para que el texto blanco siempre se lea.
        lerp(Color(swatch.rgb), Color.Black, 0.25f)
    }.getOrNull()
}

actual fun folderLabel(root: String): String = Uri.decode(root.substringAfterLast("/"))
