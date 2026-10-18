package dev.surco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.surco.audio.AudioFx
import dev.surco.audio.SpectrogramAnalyzer
import dev.surco.ui.theme.SurcoColors
import kotlin.math.log10
import kotlin.math.roundToInt

private const val COLS = 240
private const val ROWS = 128
private const val MIN_HZ = 30f
private const val MAX_HZ = 16_000f

/**
 * Espectrograma en directo de lo que suena: el tiempo corre de derecha a izquierda, las frecuencias
 * van de graves (abajo) a agudos (arriba) y el color dice la intensidad, en el tono de acento de la app.
 */
@Composable
fun SpectrogramView(playing: Boolean, modifier: Modifier = Modifier) {
    val tap = AudioFx.tap
    DisposableEffect(Unit) {
        tap.enabled = true
        onDispose { tap.enabled = false }
    }
    val analyzer = remember { SpectrogramAnalyzer(rows = ROWS, minHz = MIN_HZ, maxHz = MAX_HZ) }
    val image = remember { ImageBitmap(COLS, ROWS) }
    val canvas = remember(image) { androidx.compose.ui.graphics.Canvas(image) }
    val paint = remember { Paint() }
    val accent = SurcoColors.Accent
    val lut = remember(accent) { spectrogramColors(accent) }
    var head by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    val isPlaying by rememberUpdatedState(playing)
    val measurer = rememberTextMeasurer()

    LaunchedEffect(lut) {
        paint.color = lut[0]
        canvas.drawRect(0f, 0f, COLS.toFloat(), ROWS.toFloat(), paint)
        frame++
        val col = FloatArray(ROWS)
        while (true) {
            withFrameNanos { }
            if (!isPlaying) continue
            val behind = tap.sampleRate * AudioFx.tapLatencyMs / 1000
            if (!analyzer.column(tap, behind, col)) continue
            val x = head
            for (r in 0 until ROWS) {
                paint.color = lut[(col[r] * 255).toInt().coerceIn(0, 255)]
                canvas.drawRect(x.toFloat(), (ROWS - 1 - r).toFloat(), x + 1f, (ROWS - r).toFloat(), paint)
            }
            head = (x + 1) % COLS
            frame++
        }
    }

    val label = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
    Canvas(modifier.testTag("spectrogram")) {
        if (frame < 0) return@Canvas // se lee para redibujar con cada columna nueva
        val h = head
        val w = size.width
        val hh = size.height.roundToInt()
        // Lo más antiguo (de la columna «head» en adelante) a la izquierda; lo último, a la derecha.
        val older = COLS - h
        val split = (w * older / COLS).roundToInt()
        drawImage(image, IntOffset(h, 0), IntSize(older, ROWS), IntOffset(0, 0), IntSize(split, hh), filterQuality = FilterQuality.Low)
        if (h > 0) drawImage(image, IntOffset(0, 0), IntSize(h, ROWS), IntOffset(split, 0), IntSize(w.roundToInt() - split, hh), filterQuality = FilterQuality.Low)
        // Marcas de frecuencia, discretas.
        listOf(100f to "100 Hz", 1000f to "1 kHz", 10000f to "10 kHz").forEach { (f, t) ->
            val y = size.height * (1f - log10(f / MIN_HZ) / log10(MAX_HZ / MIN_HZ))
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            drawText(measurer, t, Offset(8.dp.toPx(), y - 15.dp.toPx()), label)
        }
    }
}

/** Escala de color: casi negro → acento oscuro → acento → casi blanco. */
private fun spectrogramColors(accent: Color): Array<Color> {
    val base = Color(0xFF070707)
    val stops = listOf(0f to base, 0.32f to lerp(base, accent, 0.28f), 0.62f to accent, 0.88f to lerp(accent, Color.White, 0.35f), 1f to lerp(accent, Color.White, 0.7f))
    return Array(256) { i ->
        val t = i / 255f
        val k = stops.indexOfLast { it.first <= t }.coerceIn(0, stops.size - 2)
        val (t0, c0) = stops[k]
        val (t1, c1) = stops[k + 1]
        lerp(c0, c1, ((t - t0) / (t1 - t0)).coerceIn(0f, 1f))
    }
}
