package dev.surco.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Color de fondo «a lo Spotify» para una portada, a partir de sus píxeles ARGB.
 *
 * 1. Se busca el tono que más superficie ocupa en la portada (no el más llamativo): los píxeles
 *    se agrupan por tono, cada grupo suma también a sus vecinos y gana el de más peso. Los casi
 *    negros y casi blancos no votan, porque casi todas las portadas tienen sombras y brillos.
 * 2. Si apenas hay color (portada en blanco y negro), se usa un gris con un toque de su tinte.
 * 3. El color resultante se lleva a un tono oscuro y apagado, con la misma luminosidad para
 *    todas las portadas, para que el texto blanco se lea siempre igual de bien.
 */
fun dominantColor(argb: List<Int>): Color? {
    if (argb.isEmpty()) return null
    val bins = 24
    val wr = DoubleArray(bins); val wg = DoubleArray(bins); val wb = DoubleArray(bins); val wn = DoubleArray(bins)
    var nr = 0.0; var ng = 0.0; var nb = 0.0; var nn = 0.0
    var all = 0
    for (p in argb) {
        if ((p ushr 24) < 128) continue
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        all++
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val v = mx / 255.0
        val s = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
        if (v < 0.09 || (v > 0.94 && s < 0.12)) continue
        if (s < 0.16 || (mx - mn) < 14) {
            nr += r; ng += g; nb += b; nn += 1.0
            continue
        }
        // Los píxeles más saturados votan un poco más, pero la superficie manda.
        val w = 0.6 + s
        val k = hue(r, g, b) * bins / 360 % bins
        wr[k] += r * w; wg[k] += g * w; wb[k] += b * w; wn[k] += w
    }
    if (all == 0) return null
    var best = -1; var bestW = 0.0
    for (k in 0 until bins) {
        val w = wn[k] + 0.5 * (wn[(k + 1) % bins] + wn[(k + bins - 1) % bins])
        if (w > bestW) { bestW = w; best = k }
    }
    // Hace falta que el color cubra algo de la portada para que tiña el fondo.
    val chromatic = best >= 0 && wn[best] + 0.5 * (wn[(best + 1) % bins] + wn[(best + bins - 1) % bins]) >= all * 0.035
    return if (chromatic) {
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0.0
        for (d in -1..1) {
            val k = (best + d + bins) % bins
            val f = if (d == 0) 1.0 else 0.5
            r += wr[k] * f; g += wg[k] * f; b += wb[k] * f; n += wn[k] * f
        }
        tone(r / n, g / n, b / n, chromatic = true)
    } else if (nn > 0) {
        tone(nr / nn, ng / nn, nb / nn, chromatic = false)
    } else {
        Color(0xFF3A3A3A)
    }
}

/** Lleva un color a un tono de fondo oscuro y apagado conservando su matiz. */
private fun tone(r: Double, g: Double, b: Double, chromatic: Boolean): Color {
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val s0 = if (mx <= 0.0) 0.0 else (mx - mn) / mx
    val h = hueF(r, g, b, mx, mn)
    val s: Double
    var v: Double
    if (chromatic) {
        s = s0.coerceIn(0.32, 0.62)
        v = 0.40
        // Amarillos y verdes se ven más claros a igual brillo: un poco más oscuros.
        if (h in 45.0..170.0) v = 0.34
    } else {
        s = min(s0, 0.10)
        v = 0.30
    }
    return hsv(h, s, v)
}

private fun hsv(h: Double, s: Double, v: Double): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60.0) % 2 - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0.0)
        h < 120 -> Triple(x, c, 0.0)
        h < 180 -> Triple(0.0, c, x)
        h < 240 -> Triple(0.0, x, c)
        h < 300 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return Color((r + m).toFloat().coerceIn(0f, 1f), (g + m).toFloat().coerceIn(0f, 1f), (b + m).toFloat().coerceIn(0f, 1f))
}

private fun hueF(r: Double, g: Double, b: Double, mx: Double, mn: Double): Double {
    if (mx == mn) return 0.0
    val d = mx - mn
    val h = when (mx) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60
    return (h + 360) % 360
}

private fun hue(r: Int, g: Int, b: Int): Int {
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    return hueF(r.toDouble(), g.toDouble(), b.toDouble(), mx.toDouble(), mn.toDouble()).toInt()
}
