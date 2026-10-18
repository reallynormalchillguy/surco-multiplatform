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
}
