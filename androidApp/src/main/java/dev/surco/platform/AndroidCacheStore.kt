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

    /** Como «Borrar almacenamiento» de los ajustes de Android: todo fuera y la app se cierra. */
    override suspend fun wipeAll() {
        // El icono vuelve al de siempre y se sueltan los permisos de las carpetas elegidas.
        runCatching { dev.surco.AppIcon.apply(context, androidx.compose.ui.graphics.Color(0xFF1ED760), follow = false) }
        runCatching {
            context.contentResolver.persistedUriPermissions.forEach { p ->
                context.contentResolver.releasePersistableUriPermission(
                    p.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        if (am?.clearApplicationUserData() != true) {
            // Plan B: borrar a mano todas las carpetas de la app y cerrarla.
            listOfNotNull(context.filesDir, context.cacheDir, context.externalCacheDir, context.getExternalFilesDir(null), java.io.File(context.applicationInfo.dataDir, "shared_prefs"), java.io.File(context.applicationInfo.dataDir, "databases"))
                .forEach { d -> d.listFiles().orEmpty().forEach { runCatching { it.deleteRecursively() } } }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
