package dev.surco.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.surco.R

object SurcoColors {
    val Background = Color(0xFF101010)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceHigh = Color(0xFF262626)
    val SurfaceHigher = Color(0xFF333333)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextTertiary = Color(0xFF7A7A7A)
    val Accent = Color(0xFF1ED760)
    val OnAccent = Color(0xFF000000)
    val Error = Color(0xFFF15E6C)
}

/** Radios y márgenes compartidos, para que todas las pantallas hablen el mismo idioma. */
object SurcoShapes {
    val Art = 6.dp        // portadas en listas y tarjetas
    val Card = 12.dp      // tarjetas grandes (letra, ajustes)
    val Cover = 10.dp     // portada del reproductor
    val Gutter = 16.dp    // margen lateral de todas las pantallas
}

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

private val scheme = darkColorScheme(
    primary = SurcoColors.Accent,
    onPrimary = SurcoColors.OnAccent,
    secondary = SurcoColors.Accent,
    onSecondary = SurcoColors.OnAccent,
    background = SurcoColors.Background,
    onBackground = SurcoColors.TextPrimary,
    surface = SurcoColors.Background,
    onSurface = SurcoColors.TextPrimary,
    surfaceVariant = SurcoColors.SurfaceHigh,
    onSurfaceVariant = SurcoColors.TextSecondary,
    surfaceContainer = SurcoColors.Surface,
    surfaceContainerHigh = SurcoColors.SurfaceHigh,
    surfaceContainerHighest = SurcoColors.SurfaceHigher,
    surfaceContainerLow = SurcoColors.Surface,
    surfaceContainerLowest = SurcoColors.Background,
    outline = SurcoColors.TextTertiary,
    outlineVariant = SurcoColors.SurfaceHigher,
    error = SurcoColors.Error,
)

private fun style(size: Int, weight: FontWeight, spacing: Double = 0.0, line: Int? = null) = TextStyle(
    fontFamily = Figtree,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = (line ?: (size * 1.3).toInt()).sp,
)

private val typography = Typography(
    displaySmall = style(34, FontWeight.ExtraBold, -1.0),
    headlineLarge = style(30, FontWeight.ExtraBold, -0.8),
    headlineMedium = style(26, FontWeight.Bold, -0.6),
    headlineSmall = style(22, FontWeight.Bold, -0.4),
    titleLarge = style(20, FontWeight.Bold, -0.2),
    titleMedium = style(16, FontWeight.Bold),
    titleSmall = style(14, FontWeight.Bold),
    bodyLarge = style(16, FontWeight.Medium),
    bodyMedium = style(14, FontWeight.Medium),
    bodySmall = style(13, FontWeight.Medium),
    labelLarge = style(14, FontWeight.SemiBold),
    labelMedium = style(12, FontWeight.SemiBold),
    labelSmall = style(11, FontWeight.SemiBold, 0.2),
)

@Composable
fun SurcoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Color de texto por defecto: blanco. Sin esto, lo que queda fuera de un Surface/Scaffold
        // (p. ej. el reproductor a pantalla completa) salía en negro.
        CompositionLocalProvider(LocalContentColor provides SurcoColors.TextPrimary, content = content)
    }
}
