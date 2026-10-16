package dev.surco.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Paleta de colores de la interfaz. El usuario elige (o sortea) cuatro colores base;
 * los demás tonos se derivan de ellos para que todo siga siendo legible.
 */
data class Palette(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val surfaceHigh: Color,
    val surfaceHigher: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val onAccent: Color,
) {
    val isLight: Boolean get() = background.luminance() > 0.45f

    /** Colores base, en el orden en que se guardan y se muestran. */
    val base: List<Color> get() = listOf(accent, background, surface, text)

    fun with(role: Role, c: Color): Palette = when (role) {
        Role.ACCENT -> of(c, background, surface, text)
        Role.BACKGROUND -> of(accent, c, surface, text)
        Role.SURFACE -> of(accent, background, c, text)
        Role.TEXT -> of(accent, background, surface, c)
    }

    fun get(role: Role): Color = when (role) {
        Role.ACCENT -> accent
        Role.BACKGROUND -> background
        Role.SURFACE -> surface
        Role.TEXT -> text
    }

    /** "rrggbb,rrggbb,rrggbb,rrggbb" (acento, fondo, superficie, texto). */
    fun encode(): String = base.joinToString(",") { hex(it) }

    enum class Role { ACCENT, BACKGROUND, SURFACE, TEXT }

    companion object {
        /** La paleta original de Surco (verde sobre negro), con sus tonos exactos. */
        val Default = Palette(
            accent = Color(0xFF1ED760),
            background = Color(0xFF101010),
            surface = Color(0xFF1A1A1A),
            text = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFF262626),
            surfaceHigher = Color(0xFF333333),
            textSecondary = Color(0xFFB3B3B3),
            textTertiary = Color(0xFF7A7A7A),
            onAccent = Color(0xFF000000),
        )

        /** Construye una paleta completa a partir de los cuatro colores base. */
        fun of(accent: Color, background: Color, surface: Color, text: Color): Palette {
            val p = Palette(
                accent = accent.opaque(),
                background = background.opaque(),
                surface = surface.opaque(),
                text = text.opaque(),
                surfaceHigh = lerp(surface, text, 0.06f).opaque(),
                surfaceHigher = lerp(surface, text, 0.12f).opaque(),
                textSecondary = lerp(text, background, 0.30f).opaque(),
                textTertiary = lerp(text, background, 0.52f).opaque(),
                onAccent = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White,
            )
            return if (p.base == Default.base) Default else p
        }

        fun decode(s: String?): Palette {
            if (s.isNullOrBlank()) return Default
            val c = s.split(',').mapNotNull { parseHex(it) }
            return if (c.size == 4) of(c[0], c[1], c[2], c[3]) else Default
        }

        /**
         * Paleta aleatoria armoniosa: fondo oscuro con un matiz, acento en un color relacionado
         * (análogo, complementario, triádico o complementario dividido) y texto casi blanco.
         * Siempre cumple contraste suficiente (texto ≥ 7:1, acento ≥ 4,5:1 sobre el fondo).
         */
        fun random(rng: Random = Random.Default): Palette {
            val baseHue = rng.nextFloat() * 360f
            val accentHue = when (rng.nextInt(4)) {
                0 -> baseHue + rng.nextInt(-40, 41)          // análogo
                1 -> baseHue + 180f                          // complementario
                2 -> baseHue + if (rng.nextBoolean()) 120f else 240f // triádico
                else -> baseHue + 150f + rng.nextInt(0, 61)  // complementario dividido
            }.mod(360f)
            val bgSat = 0.12f + rng.nextFloat() * 0.33f
            val bgVal = 0.05f + rng.nextFloat() * 0.06f
            val background = hsv(baseHue, bgSat, bgVal)
            val surface = hsv(baseHue, bgSat * 0.9f, bgVal + 0.045f + rng.nextFloat() * 0.03f)
            val text = hsv(baseHue, 0.02f + rng.nextFloat() * 0.06f, 0.97f)
            var sat = 0.55f + rng.nextFloat() * 0.35f
            var v = 0.80f + rng.nextFloat() * 0.20f
            var accent = hsv(accentHue, sat, v)
            // Si el acento se pierde sobre el fondo, se aclara hasta que se lea bien.
            var guard = 0
            while (contrast(accent, background) < 4.5f && guard++ < 20) {
                v = min(1f, v + 0.05f); sat = max(0.35f, sat - 0.04f)
                accent = hsv(accentHue, sat, v)
            }
            return of(accent, background, surface, text)
        }

        // ------------------------------------------------------------------ Utilidades de color

        fun hex(c: Color): String = (c.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

        fun parseHex(s: String): Color? {
            val t = s.trim().removePrefix("#")
            if (t.length != 6 || t.any { it !in "0123456789abcdefABCDEF" }) return null
            return Color(0xFF000000 or t.toLong(16))
        }

        /** Relación de contraste WCAG entre dos colores (1 a 21). */
        fun contrast(a: Color, b: Color): Float {
            val la = a.luminance() + 0.05f
            val lb = b.luminance() + 0.05f
            return if (la > lb) la / lb else lb / la
        }

        fun hsv(h: Float, s: Float, v: Float): Color = Color.hsv(h.mod(360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

        /** Color → (matiz 0–360, saturación 0–1, brillo 0–1). */
        fun toHsv(c: Color): Triple<Float, Float, Float> {
            val r = c.red; val g = c.green; val b = c.blue
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val d = mx - mn
            val h = when {
                d == 0f -> 0f
                mx == r -> 60f * (((g - b) / d).mod(6f))
                mx == g -> 60f * (((b - r) / d) + 2f)
                else -> 60f * (((r - g) / d) + 4f)
            }
            val s = if (mx == 0f) 0f else d / mx
            return Triple(h.mod(360f), s, mx)
        }

        private fun Color.opaque() = copy(alpha = 1f)
    }
}
