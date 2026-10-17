package dev.surco.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

/** Datos del dispositivo que iOS no da directamente. */
@OptIn(ExperimentalForeignApi::class)
object IosDevice {
    /** «iPhone17,1», etc. En el simulador, el del modelo simulado. */
    val model: String by lazy {
        val sim = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String
        sim ?: memScoped {
            val u = alloc<utsname>()
            uname(u.ptr)
            u.machine.toKString()
        }
    }

    /**
     * ¿Pantalla OLED? Todos los iPhone desde el X la tienen, salvo XR, 11 y SE (LCD). En iPad, sólo los
     * iPad Pro con chip M4. Va por el identificador del modelo.
     */
    fun isOled(id: String = model): Boolean {
        val m = Regex("""(iPhone|iPad)(\d+),(\d+)""").find(id) ?: return false
        val (kind, a, b) = m.destructured
        val major = a.toInt()
        val minor = b.toInt()
        return if (kind == "iPad") major == 16 && minor in 3..6
        else when {
            major < 10 -> false
            major == 10 -> minor == 3 || minor == 6
            "$major,$minor" in setOf("11,8", "12,1", "12,8", "14,6") -> false
            else -> true
        }
    }
}
