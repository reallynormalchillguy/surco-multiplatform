package dev.surco.ios

import dev.surco.data.AppFiles
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** Carpetas de la app en iOS. */
@OptIn(ExperimentalForeignApi::class)
object IosDirs {
    private fun dir(kind: NSSearchPathDirectory, sub: String? = null): String {
        val url = NSFileManager.defaultManager.URLForDirectory(kind, NSUserDomainMask, null, true, null)
        val base = requireNotNull(url?.path) { "Sin carpeta del sistema" }
        val path = if (sub == null) base else "$base/$sub"
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
        return path
    }

    /** Documentos: visible en la app Archivos («En mi iPhone › Surco»). */
    val documents get() = dir(NSDocumentDirectory)
    val support get() = dir(NSApplicationSupportDirectory, "Surco")
    val art get() = dir(NSApplicationSupportDirectory, "Surco/art")
    val artists get() = dir(NSApplicationSupportDirectory, "Surco/artists")
    val cache get() = dir(NSCachesDirectory, "Surco")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFiles : AppFiles {
    override fun readText(name: String): String? =
        NSString.stringWithContentsOfFile("${IosDirs.support}/$name", NSUTF8StringEncoding, null)

    override fun writeText(name: String, text: String) {
        NSString.create(string = text).writeToFile("${IosDirs.support}/$name", true, NSUTF8StringEncoding, null)
    }
}
