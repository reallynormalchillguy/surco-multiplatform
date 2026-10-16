package dev.surco.data

import dev.surco.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Escaneo automático (común). Cada plataforma avisa con [onChange] cuando ve música nueva
 * (Android: MediaStore) y con [onForeground] al volver a la app; en iOS, además, se revisa la carpeta
 * cada cierto tiempo mientras la app está abierta ([startPolling]). Los avisos se agrupan y se hace
 * un escaneo incremental (sólo se leen los archivos nuevos o cambiados). Desactivable en Ajustes.
 */
class AutoScan(
    private val prefs: Prefs,
    private val repository: Repository,
    private val scope: CoroutineScope,
) {
    private var pending: Job? = null
    private var poller: Job? = null

    val enabled get() = prefs.current.autoScan && prefs.current.treeUri != null

    /** Algo cambió en el almacenamiento: se espera a que termine la copia y se escanea una vez. */
    fun onChange() = schedule(SETTLE_MS)

    /** La app vuelve a primer plano: si hace rato del último escaneo, uno rápido por si hay música nueva. */
    fun onForeground() {
        if (!enabled) return
        if (nowMillis() - repository.lastRescanAt < FOREGROUND_MIN_GAP_MS) return
        schedule(500)
    }

    /** Revisión periódica mientras la app está abierta (para plataformas sin avisos del sistema). */
    fun startPolling(intervalMs: Long = POLL_MS) {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (enabled && nowMillis() - repository.lastRescanAt >= intervalMs) {
                    repository.rescan(auto = true)?.join()
                }
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }

    private fun schedule(delayMs: Long) {
        if (!enabled) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            repository.rescan(auto = true)?.join()
        }
    }

    companion object {
        private const val SETTLE_MS = 4_000L
        private const val FOREGROUND_MIN_GAP_MS = 30_000L
        private const val POLL_MS = 45_000L
    }
}
