package dev.surco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import io.ktor.http.decodeURLPart
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.Font

// Los selectores nativos (carpeta con marcador de seguridad y PHPicker) llegan con la app de iOS (fase 4).
@Composable
actual fun rememberDirectoryPicker(onPicked: (String) -> Unit): () -> Unit = IosPickers.directory(onPicked)

@Composable
actual fun rememberFolderReleaser(): (String) -> Unit = remember { { _: String -> } }

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit = IosPickers.image(onPicked)

/** Puntos de enganche para los selectores de la app de iOS. */
object IosPickers {
    var directory: @Composable ((String) -> Unit) -> (() -> Unit) = { _ -> {} }
    var image: @Composable ((ByteArray?) -> Unit) -> (() -> Unit) = { _ -> {} }
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

actual fun folderLabel(root: String): String = runCatching { root.trimEnd('/').substringAfterLast('/').decodeURLPart() }.getOrDefault(root)
