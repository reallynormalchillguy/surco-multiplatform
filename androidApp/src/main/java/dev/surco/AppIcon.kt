package dev.surco

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.surco.ui.theme.AppIconColors

/**
 * Cambia el icono de la app (en el lanzador) al color más parecido al de acento.
 * Cada color es un «alias» de la actividad principal; se activa el bueno y se apagan los demás.
 */
object AppIcon {
    fun apply(context: Context, accent: androidx.compose.ui.graphics.Color, follow: Boolean) {
        val target = if (follow) AppIconColors.nearest(accent) else "Green"
        val pm = context.packageManager
        fun cn(name: String) = ComponentName(context.packageName, "dev.surco.Icon$name")
        fun enabled(name: String) = when (pm.getComponentEnabledSetting(cn(name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> name == "Green" // lo que dice el manifiesto
        }
        if (enabled(target) && AppIconColors.variants.all { (n, _) -> n == target || !enabled(n) }) return
        runCatching {
            // Primero se enciende el nuevo (para que nunca se quede la app sin icono) y luego se apagan los demás.
            pm.setComponentEnabledSetting(cn(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            for ((n, _) in AppIconColors.variants) {
                if (n != target && enabled(n)) {
                    pm.setComponentEnabledSetting(cn(n), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }
}
