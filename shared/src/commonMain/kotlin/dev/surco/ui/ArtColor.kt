package dev.surco.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Color dominante «vivo y oscuro» de una lista de píxeles ARGB, parecido a Palette.darkVibrant:
 * agrupa los colores por tono y se queda con el grupo más numeroso entre los saturados.
 */
fun dominantColor(argb: List<Int>): Color? {
    if (argb.isEmpty()) return null
    class Bucket { var r = 0L; var g = 0L; var b = 0L; var n = 0 }
    val buckets = HashMap<Int, Bucket>()
    var all = 0
    for (p in argb) {
        if ((p ushr 24) < 128) continue
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 510f
        val s = if (mx == mn) 0f else (mx - mn) / (255f - kotlin.math.abs(mx + mn - 255f))
        all++
        // Descarta casi negros, casi blancos y grises.
        val key = if (s < 0.25f || l < 0.08f || l > 0.85f) -1 else hue(r, g, b) / 20
        val bk = buckets.getOrPut(key) { Bucket() }
        bk.r += r; bk.g += g; bk.b += b; bk.n++
    }
    if (all == 0) return null
    val vivid = buckets.filterKeys { it >= 0 }.maxByOrNull { it.value.n }?.value
    val pick = if (vivid != null && vivid.n * 20 >= all) vivid else buckets.values.maxBy { it.n }
    return Color((pick.r / pick.n).toInt(), (pick.g / pick.n).toInt(), (pick.b / pick.n).toInt())
}

private fun hue(r: Int, g: Int, b: Int): Int {
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    if (mx == mn) return 0
    val d = (mx - mn).toFloat()
    val h = when (mx) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60
    return ((h + 360) % 360).toInt()
}
