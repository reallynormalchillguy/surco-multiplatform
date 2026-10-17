package dev.surco.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.surco.ui.figtreeFamily

/**
 * Colores de la interfaz. Salen de la paleta activa (estado de Compose): al cambiarla en Ajustes,
 * toda la app se redibuja al momento con los colores nuevos.
 */
object SurcoColors {
    var palette by mutableStateOf(Palette.Default)

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val SurfaceHigher: Color get() = palette.surfaceHigher
    val TextPrimary: Color get() = palette.text
    val TextSecondary: Color get() = palette.textSecondary
    val TextTertiary: Color get() = palette.textTertiary
    val Accent: Color get() = palette.accent
    val OnAccent: Color get() = palette.onAccent
    val Error = Color(0xFFF15E6C)
}

/** Radios y márgenes compartidos, para que todas las pantallas hablen el mismo idioma. */
object SurcoShapes {
    val Art = 6.dp        // portadas en listas y tarjetas
    val Card = 12.dp      // tarjetas grandes (letra, ajustes)
    val Cover = 10.dp     // portada del reproductor
    val Gutter = 16.dp    // margen lateral de todas las pantallas
}

private fun schemeFor(p: Palette) = (if (p.isLight) lightColorScheme() else darkColorScheme()).copy(
    primary = p.accent,
    onPrimary = p.onAccent,
    secondary = p.accent,
    onSecondary = p.onAccent,
    background = p.background,
    onBackground = p.text,
    surface = p.background,
    onSurface = p.text,
    surfaceVariant = p.surfaceHigh,
    onSurfaceVariant = p.textSecondary,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surfaceHigh,
    surfaceContainerHighest = p.surfaceHigher,
    surfaceContainerLow = p.surface,
    surfaceContainerLowest = p.background,
    outline = p.textTertiary,
    outlineVariant = p.surfaceHigher,
    error = SurcoColors.Error,
)

private fun style(family: FontFamily, size: Int, weight: FontWeight, spacing: Double = 0.0, line: Int? = null) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = (line ?: (size * 1.3).toInt()).sp,
)

private fun typography(f: FontFamily) = Typography(
    displaySmall = style(f, 34, FontWeight.ExtraBold, -1.0),
    headlineLarge = style(f, 30, FontWeight.ExtraBold, -0.8),
    headlineMedium = style(f, 26, FontWeight.Bold, -0.6),
    headlineSmall = style(f, 22, FontWeight.Bold, -0.4),
    titleLarge = style(f, 20, FontWeight.Bold, -0.2),
    titleMedium = style(f, 16, FontWeight.Bold),
    titleSmall = style(f, 14, FontWeight.Bold),
    bodyLarge = style(f, 16, FontWeight.Medium),
    bodyMedium = style(f, 14, FontWeight.Medium),
    bodySmall = style(f, 13, FontWeight.Medium),
    labelLarge = style(f, 14, FontWeight.SemiBold),
    labelMedium = style(f, 12, FontWeight.SemiBold),
    labelSmall = style(f, 11, FontWeight.SemiBold, 0.2),
)

@Composable
fun SurcoTheme(content: @Composable () -> Unit) {
    val family = figtreeFamily()
    val typography = remember(family) { typography(family) }
    val palette = SurcoColors.palette
    val scheme = remember(palette) { schemeFor(palette) }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Color de texto por defecto: blanco. Sin esto, lo que queda fuera de un Surface/Scaffold
        // (p. ej. el reproductor a pantalla completa) salía en negro.
        CompositionLocalProvider(LocalContentColor provides SurcoColors.TextPrimary, content = content)
    }
}
