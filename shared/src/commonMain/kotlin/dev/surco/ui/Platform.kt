package dev.surco.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import coil3.PlatformContext

/**
 * Lo poco de la interfaz que depende de la plataforma: selectores del sistema, tipografía
 * y el color dominante de una portada.
 */

/** Abre el selector de carpetas del sistema. [onPicked] recibe la carpeta (con permiso persistente). */
@Composable
expect fun rememberDirectoryPicker(onPicked: (String) -> Unit): () -> Unit

/** Suelta el permiso de una carpeta que ya no se usa (Android); en iOS no hace nada. */
@Composable
expect fun rememberFolderReleaser(): (String) -> Unit

/** Abre el selector de fotos. [onPicked] recibe los bytes de la imagen, o null si falla. */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit

/** La tipografía de la app (Figtree). */
@Composable
expect fun figtreeFamily(): FontFamily

/** Color dominante de una portada (archivo local o URL), o null si no se puede leer. */
expect suspend fun extractArtColor(context: PlatformContext, path: String): Color?

/** Nombre legible de la carpeta elegida, para mostrarlo en Ajustes. */
expect fun folderLabel(root: String): String

/** Compartir con otras apps (hoja del sistema). */
interface Sharer {
    fun shareText(text: String, title: String)
    /** Comparte el archivo de audio tal cual (MP3, FLAC…). */
    fun shareFile(track: dev.surco.data.Track)
}

@Composable
expect fun rememberSharer(): Sharer

/** Esconde las barras del sistema mientras se muestra (pantalla completa de verdad). */
@androidx.compose.runtime.Composable
expect fun HideSystemBars()

/** El gesto o botón «atrás» del sistema (en iOS no hay botón: no hace nada). */
@androidx.compose.runtime.Composable
expect fun PlatformBack(enabled: Boolean, onBack: () -> Unit)

/** En iPhone se vuelve atrás deslizando desde el borde izquierdo (lo hace Surco, no el sistema). */
expect val EDGE_SWIPE_BACK: Boolean
