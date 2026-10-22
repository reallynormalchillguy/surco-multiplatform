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
    runCatching { dominantColor(samplePixels(bmp)) }.getOrNull()
}

actual fun folderLabel(root: String): String = Uri.decode(root.substringAfterLast("/"))

@Composable
actual fun rememberSharer(): Sharer {
    val context = LocalContext.current
    return remember(context) {
        object : Sharer {
            override fun shareText(text: String, title: String) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_TITLE, title)
                }
                runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            override fun shareFile(track: dev.surco.data.Track) {
                val uri = Uri.parse(track.uri)
                // El permiso de la carpeta es de Surco: se presta sólo para leer este archivo a la app elegida.
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = track.mime.ifBlank { "audio/*" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, track.fileName)
                    clipData = android.content.ClipData.newRawUri(track.fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(send, track.fileName)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
actual fun HideSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window
        fun set(hidden: Boolean) {
            if (window == null) return
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val c = window.insetsController ?: return
                if (hidden) {
                    c.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(android.view.WindowInsets.Type.systemBars())
                } else c.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (hidden) (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    ) else android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        set(true)
        onDispose { set(false) }
    }
}

/** Unos 600 píxeles repartidos por toda la portada: de sobra para saber su color. */
private fun samplePixels(bmp: android.graphics.Bitmap): List<Int> {
    val step = maxOf(1, minOf(bmp.width, bmp.height) / 24)
    val out = ArrayList<Int>()
    var y = step / 2
    while (y < bmp.height) {
        var x = step / 2
        while (x < bmp.width) { out += bmp.getPixel(x, y); x += step }
        y += step
    }
    return out
}

@Composable
actual fun PlatformBack(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

actual val EDGE_SWIPE_BACK: Boolean = false

@Composable
actual fun AllowLandscape(allowed: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, allowed) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val activity = ctx as? android.app.Activity
        // En el manifiesto la actividad es sólo vertical; aquí se libera el giro mientras dure.
        if (allowed && activity != null) {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            if (allowed && activity != null) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}
