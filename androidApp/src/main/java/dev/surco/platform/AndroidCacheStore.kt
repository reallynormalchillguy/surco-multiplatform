package dev.surco.platform

import android.content.Context
import coil3.SingletonImageLoader
import dev.surco.data.CacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Caché de Android: la carpeta de caché de la app y las de las imágenes (Coil). */
class AndroidCacheStore(private val context: Context) : CacheStore {
    /** Descargas de Soulseek a medias: no se tocan (se perderían). */
    private fun keep(f: File) = f.name == "slsk"

    private fun dirs() = listOfNotNull(context.cacheDir, context.externalCacheDir)

    override suspend fun size(): Long = withContext(Dispatchers.IO) {
        dirs().sumOf { d -> d.listFiles().orEmpty().filterNot(::keep).sumOf { f -> f.walkBottomUp().filter { it.isFile }.sumOf { it.length() } } }
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override suspend fun clear() = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        runCatching { loader.diskCache?.clear() }
        dirs().forEach { d -> d.listFiles().orEmpty().filterNot(::keep).forEach { runCatching { it.deleteRecursively() } } }
    }
}
