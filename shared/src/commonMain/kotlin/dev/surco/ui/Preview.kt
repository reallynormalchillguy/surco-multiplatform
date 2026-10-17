package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.surco.ui.theme.SurcoColors

/**
 * Botón de escucha previa (30 s) de una canción que no tienes: ▶ / ❚❚ con un anillo que avanza.
 * Sin [url] (Deezer no tiene fragmento de esa canción) no se dibuja nada.
 */
@Composable
fun PreviewButton(url: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    val preview = LocalActions.current.container.preview
    val st by preview.state.collectAsState()
    val active = st.url == url
    val accent = SurcoColors.Accent
    val ring = SurcoColors.TextPrimary.copy(alpha = 0.18f)
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable { preview.toggle(url) }.testTag("preview_button"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            val w = 2.dp.toPx()
            drawCircle(ring, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
            if (active) drawArc(accent, -90f, 360f * st.progress, false, style = Stroke(w * 1.2f, cap = StrokeCap.Round))
        }
        Icon(
            if (active && st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (active) S.stopPreview else S.playPreview,
            tint = if (active) accent else SurcoColors.TextPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Para la vista previa al salir de la pantalla, y avisa si un fragmento no se pudo reproducir. */
@Composable
fun PreviewLifecycle() {
    val actions = LocalActions.current
    val preview = actions.container.preview
    val st by preview.state.collectAsState()
    LaunchedEffect(st.failed) { if (st.failed != null) actions.toast(S.previewFailed) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
}
