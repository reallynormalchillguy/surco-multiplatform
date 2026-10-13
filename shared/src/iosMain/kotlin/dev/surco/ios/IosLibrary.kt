package dev.surco.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create

/**
 * Acceso a la carpeta de música elegida por el usuario.
 * iOS sólo deja leer fuera de la app con un «marcador de seguridad»: lo guardamos en los ajustes
 * (como «bm:<base64>») y lo resolvemos al arrancar, manteniendo el acceso abierto mientras viva la app.
 * «docs:» es la carpeta Documentos de la propia app (visible en la app Archivos).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosLibrary {
    private const val BOOKMARK = "bm:"
    const val APP_DOCUMENTS = "docs:"
    private val opened = HashMap<String, NSURL>()

    /** Crea el identificador persistente de una carpeta recién elegida. */
    fun rootFor(url: NSURL): String? {
        val started = url.startAccessingSecurityScopedResource()
        val data = url.bookmarkDataWithOptions(0u, null, null, null) ?: run {
            if (started) url.stopAccessingSecurityScopedResource()
            return null
        }
        val root = BOOKMARK + data.base64EncodedStringWithOptions(0u)
        opened[root] = url
        return root
    }

    /** URL de la carpeta (con acceso abierto), o null si el marcador ya no es válido. */
    fun resolve(root: String): NSURL? {
        opened[root]?.let { return it }
        val url = when {
            root == APP_DOCUMENTS -> NSURL.fileURLWithPath(IosDirs.documents)
            root.startsWith(BOOKMARK) -> {
                val data = NSData.create(base64EncodedString = root.removePrefix(BOOKMARK), options = 0u) ?: return null
                NSURL.URLByResolvingBookmarkData(data, 0u, null, null, null) ?: return null
            }
            else -> NSURL.URLWithString(root) ?: return null
        }
        url.startAccessingSecurityScopedResource()
        opened[root] = url
        return url
    }

    /** Nombre visible de la carpeta. */
    fun label(root: String): String = when (root) {
        APP_DOCUMENTS -> "Surco (En mi iPhone)"
        else -> resolve(root)?.lastPathComponent ?: "?"
    }
}
