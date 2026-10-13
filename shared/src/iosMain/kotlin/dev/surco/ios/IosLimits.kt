package dev.surco.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.RLIMIT_NOFILE
import platform.posix.getrlimit
import platform.posix.rlimit
import platform.posix.setrlimit

/**
 * Los sockets de Ktor en iOS usan select(), que sólo admite descriptores por debajo de 1024.
 * Limitamos los archivos abiertos de la app a 1000: si algún día se llegara al tope, abrir otro
 * socket simplemente fallaría (error normal y recuperable) en vez de cerrar la app.
 */
@OptIn(ExperimentalForeignApi::class)
object IosLimits {
    const val MAX_FILES = 1000uL

    fun apply() = memScoped {
        val r = alloc<rlimit>()
        if (getrlimit(RLIMIT_NOFILE, r.ptr) != 0) return@memScoped
        if (r.rlim_cur > MAX_FILES || r.rlim_cur == 0uL) {
            r.rlim_cur = minOf(MAX_FILES, r.rlim_max)
            setrlimit(RLIMIT_NOFILE, r.ptr)
        }
    }
}
