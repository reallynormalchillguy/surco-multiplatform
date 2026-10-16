package dev.surco.platform

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.surco.data.AutoScan
import dev.surco.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Cuando aparece (o desaparece) música en el dispositivo, Android lo anota en MediaStore y avisa:
 * se lo pasamos al escaneo automático común, que espera a que termine la copia y escanea una vez.
 */
class MediaStoreWatcher(
    private val app: Application,
    private val prefs: Prefs,
    private val autoScan: AutoScan,
    private val scope: CoroutineScope,
) {
    private var observer: ContentObserver? = null

    fun start() {
        scope.launch(Dispatchers.Main) {
            prefs.settings.map { it.autoScan && it.treeUri != null }.distinctUntilChanged().collect { on ->
                if (on) register() else unregister()
            }
        }
    }

    private fun register() {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange(selfChange, null)
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Sólo audio: las fotos y vídeos nuevos no deben disparar escaneos de la biblioteca.
                if (uri != null && "/audio/" !in (uri.path ?: "") + "/") return
                autoScan.onChange()
            }
        }
        // Raíz de MediaStore con descendientes: cubre la memoria interna y las tarjetas SD.
        val ok = runCatching { app.contentResolver.registerContentObserver(Uri.parse("content://media"), true, o) }.isSuccess
        if (ok) observer = o
    }

    private fun unregister() {
        observer?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }
}
