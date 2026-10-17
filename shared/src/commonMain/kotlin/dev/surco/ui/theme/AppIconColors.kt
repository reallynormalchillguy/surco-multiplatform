package dev.surco.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colores en los que existe el icono de la app (un icono por color; el sistema no deja pintarlo al vuelo).
 * Se elige el más parecido al color de acento.
 */
object AppIconColors {
    val variants: List<Pair<String, Color>> = listOf(
        "Green" to Color(0xFF1ED760), "Lime" to Color(0xFFC8E62E), "Yellow" to Color(0xFFFFD23F),
        "Orange" to Color(0xFFFF8A3D), "Red" to Color(0xFFFF4F4F), "Pink" to Color(0xFFFF5FA8),
        "Magenta" to Color(0xFFE05CFF), "Purple" to Color(0xFF9F6BFF), "Indigo" to Color(0xFF6C7BFF),
        "Blue" to Color(0xFF3D9BFF), "Cyan" to Color(0xFF2EC8E6), "Mint" to Color(0xFF3DDCA4),
        "White" to Color(0xFFE8E8E8),
    )

    /** El icono más parecido a [accent]: por tono; los colores casi grises van al blanco. */
    fun nearest(accent: Color): String {
        val (h, s, v) = hsv(accent)
        if (s < 0.22f || v < 0.25f) return "White"
        return variants.filter { it.first != "White" }.minBy { (_, c) ->
            val (h2, s2, v2) = hsv(c)
            val dh = abs(h - h2).let { min(it, 360f - it) }
            dh + abs(s - s2) * 30f + abs(v - v2) * 30f
        }.first
    }

    private fun hsv(c: Color): Triple<Float, Float, Float> {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0) it + 360f else it }
        return Triple(h, if (mx == 0f) 0f else d / mx, mx)
    }
}
