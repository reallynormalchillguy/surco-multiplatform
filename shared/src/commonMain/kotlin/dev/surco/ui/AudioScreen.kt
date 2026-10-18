package dev.surco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.surco.audio.AudioFx
import dev.surco.audio.EqBand
import dev.surco.audio.EqSettings
import dev.surco.audio.FilterType
import dev.surco.i18n.S
import dev.surco.ui.components.SurcoChip
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** Ajustes de sonido para audiófilos: ecualizador paramétrico, normalización, crossfade y espectrograma. */
@Composable
fun AudioScreen() {
    val actions = LocalActions.current
    val prefs = actions.repo.prefs
    val settings by prefs.settings.collectAsState()

    // El ecualizador se edita aquí y suena al momento; se guarda medio segundo después del último cambio.
    var eq by remember { mutableStateOf(EqSettings.decode(settings.eq)) }
    var selected by remember { mutableIntStateOf(0) }
    var dirty by remember { mutableStateOf(false) }
    fun change(e: EqSettings) {
        eq = e
        dirty = true
        AudioFx.setEq(prefs.current.eqEnabled, e)
    }
    LaunchedEffect(eq, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(400)
        prefs.update { it.copy(eq = eq.encode()) }
        dirty = false
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Text(S.audiophile, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("audio_screen")) {
            Text(S.audiophileIntro, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))

            // ================= Ecualizador
            Header(S.parametricEq)
            Card {
                SwitchRow(S.eqEnable, S.eqEnableDesc, settings.eqEnabled) { v ->
                    prefs.update { it.copy(eqEnabled = v, eq = eq.encode()) }
                    AudioFx.setEq(v, eq)
                }
                Spacer(Modifier.height(8.dp))
                EqGraph(
                    eq = eq,
                    enabled = settings.eqEnabled,
                    selected = selected,
                    onSelect = { selected = it },
                    onMove = { i, f, g -> change(eq.copy(bands = eq.bands.mapIndexed { j, b -> if (j == i) b.copy(freq = f, gain = g).clamped() else b })) },
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                )
                Spacer(Modifier.height(10.dp))
                // Preajustes
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    eqPresets().forEach { (name, preset) ->
                        SurcoChip(name, selected = eq == preset, onClick = { change(preset); selected = 0 })
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Bandas: una ficha por banda (tocar = editarla) y «+» para añadir otra.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    eq.bands.forEachIndexed { i, b ->
                        SurcoChip("${i + 1} · ${typeShort(b.type)}", selected = i == selected, onClick = { selected = i })
                    }
                    if (eq.bands.size < EqSettings.MAX_BANDS) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(SurcoColors.SurfaceHigh)
                                .clickable {
                                    val f = nextFreeFreq(eq.bands)
                                    change(eq.copy(bands = eq.bands + EqBand(FilterType.PEAK, f, 0f, 1f)))
                                    selected = eq.bands.size - 1
                                }.testTag("eq_add_band"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, S.addBand, tint = SurcoColors.TextPrimary, modifier = Modifier.size(20.dp)) }
                    }
                }
                val i = selected.coerceIn(0, (eq.bands.size - 1).coerceAtLeast(0))
                eq.bands.getOrNull(i)?.let { b ->
                    fun set(nb: EqBand) = change(eq.copy(bands = eq.bands.mapIndexed { j, x -> if (j == i) nb.clamped() else x }))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterType.entries.forEach { t -> SurcoChip(typeName(t), selected = b.type == t, onClick = { set(b.copy(type = t)) }) }
                    }
                    ParamSlider(S.frequency, fmtHz(b.freq), logPos(b.freq, EqSettings.MIN_FREQ, EqSettings.MAX_FREQ)) {
                        set(b.copy(freq = fromLog(it, EqSettings.MIN_FREQ, EqSettings.MAX_FREQ)))
                    }
                    ParamSlider(S.gain, fmtDb(b.gain), (b.gain + EqSettings.MAX_GAIN) / (2 * EqSettings.MAX_GAIN)) {
                        set(b.copy(gain = round((it * 2 - 1) * EqSettings.MAX_GAIN * 10) / 10f))
                    }
                    ParamSlider(S.qFactor, fmt2(b.q), logPos(b.q, EqSettings.MIN_Q, EqSettings.MAX_Q)) {
                        set(b.copy(q = round(fromLog(it, EqSettings.MIN_Q, EqSettings.MAX_Q) * 100) / 100f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (b.type == FilterType.PEAK) S.peakHelp else S.shelfHelp,
                            style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, modifier = Modifier.weight(1f),
                        )
                        if (eq.bands.size > 1) {
                            TextButton(onClick = {
                                change(eq.copy(bands = eq.bands.filterIndexed { j, _ -> j != i }))
                                selected = (i - 1).coerceAtLeast(0)
                            }) {
                                Icon(Icons.Filled.Delete, null, tint = SurcoColors.TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.removeBand, color = SurcoColors.TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ParamSlider(S.preamp, fmtDb(eq.preamp), (eq.preamp + EqSettings.MAX_GAIN) / (2 * EqSettings.MAX_GAIN)) {
                    change(eq.copy(preamp = round((it * 2 - 1) * EqSettings.MAX_GAIN * 10) / 10f))
                }
                val boost = remember(eq) { eq.maxBoost() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (boost + eq.preamp > 0.05f) S.preampWarning(fmtDb(-boost)) else S.preampOk,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (boost + eq.preamp > 0.05f) SurcoColors.Accent else SurcoColors.TextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { change(eq.copy(preamp = -round(boost * 10) / 10f)) }) { Text(S.autoPreamp, color = SurcoColors.Accent) }
                }
                OutlinedButton(onClick = { change(EqSettings()); selected = 0 }) { Text(S.resetEq) }
            }

            // ================= Normalización
            Header(S.normalization)
            Card {
                SwitchRow(S.normalizeVolume, S.normalizeVolumeDesc, settings.normalize) { v -> prefs.update { it.copy(normalize = v) } }
                if (settings.normalize) {
                    Spacer(Modifier.height(8.dp))
                    Text(S.normalizeBy, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SurcoChip(S.bySong, selected = !settings.normalizeAlbum, onClick = { prefs.update { it.copy(normalizeAlbum = false) } })
                        SurcoChip(S.byAlbum, selected = settings.normalizeAlbum, onClick = { prefs.update { it.copy(normalizeAlbum = true) } })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(S.loudnessLevel, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-19 to S.levelQuiet, -14 to S.levelNormal, -11 to S.levelLoud).forEach { (v, label) ->
                            SurcoChip("$label · $v LUFS", selected = settings.normalizeTarget == v, onClick = { prefs.update { it.copy(normalizeTarget = v) } })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(S.normalizeHow, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary)
                }
            }

            // ================= Crossfade
            Header(S.crossfade)
            Card {
                SwitchRow(S.crossfadeEnable, S.crossfadeDesc, settings.crossfade) { v -> prefs.update { it.copy(crossfade = v) } }
                if (settings.crossfade) {
                    var sec by remember(settings.crossfadeSec) { mutableStateOf(settings.crossfadeSec.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.crossfadeLength, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${sec.toInt()} s", style = MaterialTheme.typography.titleSmall, color = SurcoColors.Accent)
                    }
                    Slider(
                        value = sec, onValueChange = { sec = round(it) },
                        onValueChangeFinished = { prefs.update { it.copy(crossfadeSec = sec.toInt()) } },
                        valueRange = 1f..12f, steps = 10, colors = sliderColors(),
                        modifier = Modifier.testTag("crossfade_slider"),
                    )
                    SwitchRow(S.crossfadeAlbums, S.crossfadeAlbumsDesc, settings.crossfadeAlbums) { v -> prefs.update { it.copy(crossfadeAlbums = v) } }
                }
            }

            // ================= Espectrograma
            Header(S.spectrogram)
            Card {
                SwitchRow(S.spectrogramCover, S.spectrogramCoverDesc, settings.spectrogram) { v -> prefs.update { it.copy(spectrogram = v) } }
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ----------------------------------------------------------------------- Curva del ecualizador

private const val GRAPH_DB = 15f

/**
 * La curva del ecualizador (20 Hz–20 kHz en escala logarítmica, ±15 dB) con un punto por banda que se
 * arrastra: a izquierda/derecha cambia la frecuencia y arriba/abajo la ganancia.
 */
@Composable
private fun EqGraph(
    eq: EqSettings,
    enabled: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val accent = SurcoColors.Accent
    val grid = SurcoColors.TextTertiary
    val cur by rememberUpdatedState(eq)
    val sel by rememberUpdatedState(selected)
    val curve = remember(eq) {
        val n = 160
        FloatArray(n) { i -> eq.responseDb(20f * 1000f.pow(i / (n - 1f))) - eq.preamp } // la forma de las bandas (el preamplificador va aparte)
    }
    val labelStyle = TextStyle(color = grid, fontSize = 10.sp)

    fun xOf(f: Float, w: Float) = (log10(f / 20f) / 3f) * w
    fun fOfX(x: Float, w: Float) = 20f * 1000f.pow((x / w).coerceIn(0f, 1f))
    fun yOf(db: Float, h: Float, pad: Float) = h / 2 - db / GRAPH_DB * (h / 2 - pad)
    fun dbOfY(y: Float, h: Float, pad: Float) = ((h / 2 - y) / (h / 2 - pad) * GRAPH_DB).coerceIn(-GRAPH_DB, GRAPH_DB)

    fun nearest(p: Offset, w: Float, h: Float, pad: Float, maxDist: Float): Int? {
        var best: Int? = null
        var bestD = maxDist
        cur.bands.forEachIndexed { i, b ->
            val dx = xOf(b.freq, w) - p.x
            val dy = yOf(b.gain, h, pad) - p.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurcoColors.Background.copy(alpha = 0.6f))
            .testTag("eq_graph")
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                detectTapGestures { p -> nearest(p, size.width.toFloat(), size.height.toFloat(), pad, 36.dp.toPx())?.let(onSelect) }
            }
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                var dragging = -1
                detectDragGestures(
                    onDragStart = { p ->
                        dragging = nearest(p, size.width.toFloat(), size.height.toFloat(), pad, 44.dp.toPx()) ?: sel
                        onSelect(dragging)
                    },
                    onDragEnd = { dragging = -1 },
                ) { change, _ ->
                    change.consume()
                    val i = dragging
                    if (i in cur.bands.indices) {
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val f = round(fOfX(change.position.x, w))
                        val g = round(dbOfY(change.position.y, h, pad) * 10) / 10f
                        onMove(i, f, g)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val pad = 14.dp.toPx()
        val alpha = if (enabled) 1f else 0.45f
        // Rejilla: frecuencias y dB.
        listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f).forEach { f ->
            val x = xOf(f, w)
            drawLine(grid.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        listOf(-12f, -6f, 0f, 6f, 12f).forEach { db ->
            val y = yOf(db, h, pad)
            drawLine(grid.copy(alpha = if (db == 0f) 0.45f else 0.15f), Offset(0f, y), Offset(w, y), strokeWidth = if (db == 0f) 1.5f else 1f)
            if (db != 0f) drawText(measurer, (if (db > 0) "+" else "") + db.toInt(), Offset(4.dp.toPx(), y - 13.dp.toPx()), labelStyle)
        }
        listOf(100f to "100", 1000f to "1k", 10000f to "10k").forEach { (f, l) ->
            drawText(measurer, l, Offset(xOf(f, w) + 3.dp.toPx(), h - 15.dp.toPx()), labelStyle)
        }
        // La curva: relleno suave hasta 0 dB y la línea encima.
        val zero = yOf(0f, h, pad)
        val line = Path()
        curve.forEachIndexed { i, db ->
            val x = i / (curve.size - 1f) * w
            val y = yOf(db.coerceIn(-GRAPH_DB - 3, GRAPH_DB + 3), h, pad)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply { addPath(line); lineTo(w, zero); lineTo(0f, zero); close() }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f * alpha), accent.copy(alpha = 0.04f * alpha))))
        drawPath(line, accent.copy(alpha = alpha), style = Stroke(width = 2.5.dp.toPx()))
        // Los puntos de cada banda.
        eq.bands.forEachIndexed { i, b ->
            val c = Offset(xOf(b.freq, w), yOf(b.gain, h, pad))
            if (i == selected) {
                drawCircle(accent.copy(alpha = 0.25f * alpha), radius = 16.dp.toPx(), center = c)
                drawCircle(accent.copy(alpha = alpha), radius = 8.dp.toPx(), center = c)
            } else {
                drawCircle(SurcoColors.Surface, radius = 7.dp.toPx(), center = c)
                drawCircle(accent.copy(alpha = 0.9f * alpha), radius = 7.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
            val label = measurer.measure("${i + 1}", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (i == selected) SurcoColors.OnAccent else SurcoColors.TextPrimary))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: String, position: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = SurcoColors.Accent)
        }
        Slider(value = position.coerceIn(0f, 1f), onValueChange = onChange, colors = sliderColors(), modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = SurcoColors.Accent, activeTrackColor = SurcoColors.Accent, inactiveTrackColor = SurcoColors.SurfaceHigher,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

private fun logPos(v: Float, min: Float, max: Float) = (ln(v / min) / ln(max / min)).coerceIn(0f, 1f)
private fun fromLog(p: Float, min: Float, max: Float) = min * (max / min).pow(p.coerceIn(0f, 1f))

private fun fmt1(v: Float): String {
    val r = round(abs(v) * 10).toInt()
    return "${r / 10}.${r % 10}"
}

private fun fmt2(v: Float): String {
    val r = round(v * 100).toInt()
    return "${r / 100}.${(r % 100).toString().padStart(2, '0')}"
}

internal fun fmtDb(v: Float): String = (if (v > 0.04f) "+" else if (v < -0.04f) "−" else "") + fmt1(v) + " dB"

internal fun fmtHz(f: Float): String = if (f >= 1000f) "${fmt2(f / 1000f)} kHz" else "${round(f).toInt()} Hz"

private fun typeName(t: FilterType) = when (t) {
    FilterType.PEAK -> S.filterPeak
    FilterType.LOW_SHELF -> S.filterLowShelf
    FilterType.HIGH_SHELF -> S.filterHighShelf
}

private fun typeShort(t: FilterType) = when (t) {
    FilterType.PEAK -> "Peak"
    FilterType.LOW_SHELF -> "Low"
    FilterType.HIGH_SHELF -> "High"
}

/** Frecuencia para una banda nueva: en el hueco más grande entre las que ya hay. */
private fun nextFreeFreq(bands: List<EqBand>): Float {
    val logs = (bands.map { log10(it.freq) } + log10(20f) + log10(20000f)).sorted()
    var best = 0
    for (i in 0 until logs.size - 1) if (logs[i + 1] - logs[i] > logs[best + 1] - logs[best]) best = i
    return round(10f.pow((logs[best] + logs[best + 1]) / 2))
}

/** Preajustes (nombre y ajustes). */
fun eqPresets(): List<Pair<String, EqSettings>> = listOf(
    S.presetFlat to EqSettings(),
    S.presetBass to EqSettings(listOf(EqBand(FilterType.LOW_SHELF, 100f, 6f, 0.71f), EqBand(FilterType.PEAK, 250f, 1.5f, 1f), EqBand(FilterType.PEAK, 1000f, 0f, 1f), EqBand(FilterType.PEAK, 4000f, 0f, 1f), EqBand(FilterType.HIGH_SHELF, 10000f, 0f, 0.71f)), -6.5f),
    S.presetVocal to EqSettings(listOf(EqBand(FilterType.LOW_SHELF, 120f, -2f, 0.71f), EqBand(FilterType.PEAK, 300f, -1.5f, 1f), EqBand(FilterType.PEAK, 1500f, 2.5f, 1.2f), EqBand(FilterType.PEAK, 3200f, 3f, 1f), EqBand(FilterType.HIGH_SHELF, 10000f, 1f, 0.71f)), -3.5f),
    S.presetTreble to EqSettings(listOf(EqBand(FilterType.LOW_SHELF, 80f, 0f, 0.71f), EqBand(FilterType.PEAK, 250f, 0f, 1f), EqBand(FilterType.PEAK, 1000f, 0f, 1f), EqBand(FilterType.PEAK, 4000f, 2f, 1f), EqBand(FilterType.HIGH_SHELF, 8000f, 5f, 0.71f)), -5.5f),
    S.presetLoudness to EqSettings(listOf(EqBand(FilterType.LOW_SHELF, 80f, 5f, 0.71f), EqBand(FilterType.PEAK, 250f, 0f, 1f), EqBand(FilterType.PEAK, 1000f, -1f, 0.8f), EqBand(FilterType.PEAK, 4000f, 1f, 1f), EqBand(FilterType.HIGH_SHELF, 10000f, 4f, 0.71f)), -5f),
)
