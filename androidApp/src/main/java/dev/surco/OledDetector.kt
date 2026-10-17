package dev.surco

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * ¿La pantalla es OLED? Android no lo dice directamente; la mejor pista es que sea HDR o de gama amplia,
 * que en móviles casi siempre son pantallas OLED. Si se equivoca, el modo «negro puro» se cambia en Ajustes.
 */
object OledDetector {
    fun isOled(context: Context): Boolean = runCatching {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val hdr = Build.VERSION.SDK_INT >= 26 && display?.isHdr == true ||
            (display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true)
        val wide = Build.VERSION.SDK_INT >= 26 && context.resources.configuration.isScreenWideColorGamut
        hdr || wide
    }.getOrDefault(false)
}
