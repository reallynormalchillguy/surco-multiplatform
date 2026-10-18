package dev.surco.ios

import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.surco.data.CacheStore
import dev.surco.util.IoDispatcher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURLCache

/** Caché del iPhone: Caches/Surco, la caché de red del sistema y las de las imágenes (Coil). */
@OptIn(ExperimentalForeignApi::class)
class IosCacheStore : CacheStore {
    private val fm get() = NSFileManager.defaultManager

    /** Archivos de la caché (sin las descargas de Soulseek a medias, «.part»). */
    private fun files(): List<String> {
        val root = IosDirs.cache
        val sub = fm.subpathsOfDirectoryAtPath(root, null)?.mapNotNull { it as? String }.orEmpty()
        return sub.filterNot { it.endsWith(".part") }.map { "$root/$it" }
    }

    override suspend fun size(): Long = withContext(IoDispatcher) {
        val local = files().sumOf { p -> ((fm.attributesOfItemAtPath(p, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L) }
        local + NSURLCache.sharedURLCache.currentDiskUsage.toLong()
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override suspend fun clear() = withContext(IoDispatcher) {
        runCatching {
            val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        NSURLCache.sharedURLCache.removeAllCachedResponses()
        val root = IosDirs.cache
        fm.contentsOfDirectoryAtPath(root, null)?.mapNotNull { it as? String }.orEmpty()
            .filterNot { it.endsWith(".part") }
            .forEach { runCatching { fm.removeItemAtPath("$root/$it", null) } }
        Unit
    }

    /**
     * En iPhone no se pueden borrar los datos con la app abierta (la base de datos está en uso): se
     * apunta, la app se cierra y al volver a abrirla se borra todo antes de arrancar (ver [IosWipe]).
     */
    override suspend fun wipeAll() {
        runCatching { IosAppIcon.apply("Green") }
        platform.Foundation.NSUserDefaults.standardUserDefaults.setBool(true, forKey = IosWipe.FLAG)
        platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
        platform.posix.exit(0)
    }
}

/** Borrado total pendiente (de «Borrar todos los datos»): se hace al arrancar, antes de abrir nada. */
@OptIn(ExperimentalForeignApi::class)
object IosWipe {
    const val FLAG = "surco.wipeAll"

    fun runIfPending() {
        val d = platform.Foundation.NSUserDefaults.standardUserDefaults
        if (!d.boolForKey(FLAG)) return
        val fm = NSFileManager.defaultManager
        // Base de datos (en Documentos, que no se toca entero: ahí puede estar tu música).
        val docs = (platform.Foundation.NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSDocumentDirectory, platform.Foundation.NSUserDomainMask, true,
        ).firstOrNull() as? String).orEmpty()
        if (docs.isNotEmpty()) {
            fm.contentsOfDirectoryAtPath(docs, null)?.mapNotNull { it as? String }.orEmpty()
                .filter { it.startsWith(dev.surco.data.SurcoDatabase.FILE_NAME) }
                .forEach { runCatching { fm.removeItemAtPath("$docs/$it", null) } }
        }
        // Portadas, fotos, letras, ajustes propios y caché.
        listOf(IosDirs.support, IosDirs.cache).forEach { runCatching { fm.removeItemAtPath(it, null) } }
        NSURLCache.sharedURLCache.removeAllCachedResponses()
        // Todos los ajustes (y las medidas de sonoridad).
        platform.Foundation.NSBundle.mainBundle.bundleIdentifier?.let { d.removePersistentDomainForName(it) }
        platform.Foundation.NSUserDefaults(suiteName = "dev.surco.loudness").let { l ->
            l.dictionaryRepresentation().keys.forEach { k -> (k as? String)?.let { l.removeObjectForKey(it) } }
        }
        d.removeObjectForKey(FLAG)
        d.synchronize()
    }
}
