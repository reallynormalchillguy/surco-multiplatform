package dev.surco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.surco.i18n.S
import dev.surco.data.Prefs
import dev.surco.ui.theme.Palette
import dev.surco.ui.theme.SurcoColors

/** Nombre visible de cada color base. */
private fun roleName(r: Palette.Role) = when (r) {
    Palette.Role.ACCENT -> S.colorAccent
    Palette.Role.BACKGROUND -> S.colorBackground
    Palette.Role.SURFACE -> S.colorSurface
    Palette.Role.TEXT -> S.colorText
}

/**
 * Generador de paletas: «Sortear» crea una combinación armoniosa al azar; cada color se puede
 * retocar a mano (matiz, saturación, brillo o código hexadecimal). Los cambios se ven al momento.
 */
@Composable
fun PaletteScreen() {
    val actions = LocalActions.current
    val prefs = actions.repo.prefs
    val settings by prefs.settings.collectAsState()
    /** Mientras se arrastra un control, la paleta aún sin guardar. */
    var draft by remember { mutableStateOf<Palette?>(null) }
    /** La paleta que se edita (la elegida por el usuario, sin el negro del modo OLED). */
    val palette = draft ?: Palette.decode(settings.palette)
    /** La que se ve en la maqueta: con negro puro si el modo OLED está activo. */
    val shown = Prefs.effectivePalette(palette, settings.copy(theme = ""))
    val history = remember { mutableStateListOf<Palette>() }
    var editing by remember { mutableStateOf<Palette.Role?>(Palette.Role.ACCENT) }
    /** Cambia cuando la paleta entera cambia desde fuera del editor (sortear, deshacer…): reinicia los controles. */
    var generation by remember { mutableIntStateOf(0) }

    /** Aplica sin guardar (mientras se arrastra un control). */
    fun preview(p: Palette) { draft = p; SurcoColors.palette = Prefs.effectivePalette(p, prefs.current.copy(theme = "")) }
    /** Aplica y guarda. */
    fun commit(p: Palette, remember: Boolean = true) {
        val before = Palette.decode(prefs.current.palette)
        if (remember && before != p) { history.add(before); if (history.size > 20) history.removeAt(0) }
        draft = null
        // Tocar los colores sale del tema con imagen: vuelve a tus colores.
        prefs.update { it.copy(palette = if (p == Palette.Default) "" else p.encode(), theme = "") }
    }
    fun replaceAll(p: Palette, remember: Boolean = true) { commit(p, remember); generation++ }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Text(S.colorPalette, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PalettePreview(shown)
            // Negro puro (OLED): el fondo se queda en negro sea cual sea la paleta.
            OledRow(settings) { on -> prefs.update { it.copy(oled = if (on) "on" else "off") } }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { replaceAll(Palette.random()) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                    modifier = Modifier.testTag("palette_shuffle"),
                ) {
                    Icon(Icons.Filled.Casino, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(S.shufflePalette)
                }
                OutlinedButton(
                    onClick = { history.removeLastOrNull()?.let { replaceAll(it, remember = false) } },
                    enabled = history.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S.undo)
                }
                IconButton(onClick = { replaceAll(Palette.Default) }, enabled = palette != Palette.Default) {
                    Icon(Icons.Filled.RestartAlt, S.resetPalette, tint = SurcoColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(S.tapColorToEdit, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Palette.Role.entries.forEach { role ->
                    Swatch(
                        color = palette.get(role),
                        label = roleName(role),
                        selected = editing == role,
                        onClick = { editing = if (editing == role) null else role },
                        modifier = Modifier.testTag("swatch_${role.name.lowercase()}"),
                    )
                }
            }

            editing?.let { role ->
                Spacer(Modifier.height(16.dp))
                ColorEditor(
                    key = "$role/$generation",
                    color = palette.get(role),
                    onPreview = { preview(palette.with(role, it)) },
                    onCommit = { commit(palette.with(role, it)) },
                )
            }

            ContrastWarnings(shown)
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) SurcoColors.Accent else SurcoColors.SurfaceHigher, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = if (Palette.contrast(Color.Black, color) > 4f) Color.Black else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text("#" + Palette.hex(color), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary)
    }
}

/** Una maqueta pequeña de la app con la paleta: así se ve el resultado sin salir de la pantalla. */
@Composable
private fun PalettePreview(p: Palette) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.background)
            .border(1.dp, p.surfaceHigher, RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Text(S.previewTitle, style = MaterialTheme.typography.titleLarge, color = p.text)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.surface).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(p.accent, p.surfaceHigher))))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.previewSong, style = MaterialTheme.typography.titleSmall, color = p.accent)
                Text(S.previewArtist, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = p.onAccent)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                S.albums, style = MaterialTheme.typography.labelLarge, color = p.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent).padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Text(
                S.artists, style = MaterialTheme.typography.labelLarge, color = p.text,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.surfaceHigh).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(S.previewCaption, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
    }
}

@Composable
private fun ColorEditor(key: String, color: Color, onPreview: (Color) -> Unit, onCommit: (Color) -> Unit) {
    // Los controles guardan su propio matiz: si se recalculara desde el color, al bajar la
    // saturación a 0 el matiz se perdería (el gris no tiene matiz).
    val start = remember(key) { Palette.toHsv(color) }
    var h by remember(key) { mutableStateOf(start.first) }
    var s by remember(key) { mutableStateOf(start.second) }
    var v by remember(key) { mutableStateOf(start.third) }
    var hex by remember(key) { mutableStateOf(Palette.hex(color)) }
    fun current() = Palette.hsv(h, s, v)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurcoColors.Surface).padding(16.dp)) {
        Label(S.hue)
        GradientSlider(
            h / 360f, { h = it * 360f; hex = Palette.hex(current()); onPreview(current()) }, { onCommit(current()) },
            Brush.horizontalGradient((0..6).map { Palette.hsv(it * 60f, 1f, 1f) }), "slider_hue",
        )
        Label(S.saturation)
        GradientSlider(
            s, { s = it; hex = Palette.hex(current()); onPreview(current()) }, { onCommit(current()) },
            Brush.horizontalGradient(listOf(Palette.hsv(h, 0f, v), Palette.hsv(h, 1f, v))), "slider_sat",
        )
        Label(S.brightness)
        GradientSlider(
            v, { v = it; hex = Palette.hex(current()); onPreview(current()) }, { onCommit(current()) },
            Brush.horizontalGradient(listOf(Color.Black, Palette.hsv(h, s, 1f))), "slider_val",
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                hex = t.removePrefix("#").take(6).uppercase()
                Palette.parseHex(hex)?.let { c ->
                    val (nh, ns, nv) = Palette.toHsv(c)
                    h = nh; s = ns; v = nv
                    onCommit(c)
                }
            },
            prefix = { Text("#", color = SurcoColors.TextSecondary) },
            label = { Text(S.hexCode) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SurcoColors.Accent, focusedLabelColor = SurcoColors.Accent,
                cursorColor = SurcoColors.Accent, unfocusedBorderColor = SurcoColors.SurfaceHigher,
            ),
            modifier = Modifier.fillMaxWidth().testTag("hex_field"),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, brush: Brush, tag: String) {
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(14.dp).clip(RoundedCornerShape(50)).background(brush))
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

@Composable
private fun ContrastWarnings(p: Palette) {
    val warnings = buildList {
        if (Palette.contrast(p.text, p.background) < 4.5f) add(S.lowContrastText)
        if (Palette.contrast(p.accent, p.background) < 3f) add(S.lowContrastAccent)
    }
    warnings.forEach {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = SurcoColors.Error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SurcoColors.Error)
        }
    }
}

@Composable
fun OledRow(settings: dev.surco.data.Settings, onChange: (Boolean) -> Unit) {
    val on = Prefs.oledActive(settings)
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(top = 14.dp).testTag("oled_switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(S.oledBlack, style = MaterialTheme.typography.titleSmall)
            Text(
                if (Prefs.deviceOled) S.oledDetected else S.oledNotDetected,
                style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = SurcoColors.Accent),
        )
    }
}
