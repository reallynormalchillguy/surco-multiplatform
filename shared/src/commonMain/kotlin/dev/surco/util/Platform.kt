package dev.surco.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.abs
import kotlin.math.roundToLong

/** Hora actual en milisegundos UNIX. */
expect fun nowMillis(): Long

/** Diferencia con UTC (segundos) de la zona horaria del móvil en ese instante (segundos desde 1970). */
expect fun utcOffsetSeconds(epochSec: Long): Int

/** Hilo(s) para E/S bloqueante (archivos, etiquetas…). */
expect val IoDispatcher: CoroutineDispatcher

/** Formatea con [decimals] decimales y punto decimal (como String.format(Locale.US, "%.1f")). */
fun Double.fmt(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(this) * factor).roundToLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return sign + scaled.toString()
    val int = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$int.$frac"
}
