package dev.surco.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.surco.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Canción anterior y siguiente de la cola (respeta aleatorio y repetición), para el gesto de deslizar. */
@Composable
fun rememberNeighbourTracks(): Pair<Track?, Track?> {
    val state = LocalPlayer.current
    val dao = LocalActions.current.repo.dao
    val prevUri = state.queue.getOrNull(state.prevIndex)?.takeIf { state.prevIndex != state.queueIndex }
    val nextUri = state.queue.getOrNull(state.nextIndex)?.takeIf { state.nextIndex != state.queueIndex }
    val prev by produceState<Track?>(null, prevUri) { value = prevUri?.let { dao.track(it) } }
    val next by produceState<Track?>(null, nextUri) { value = nextUri?.let { dao.track(it) } }
    return prev to next
}

/**
 * Carrusel de canciones como el de Spotify: se desliza a la izquierda para pasar a la siguiente y a la
 * derecha para volver a la anterior, viendo cómo entra la otra portada. Cuando la canción cambia por
 * otro motivo (botones, fin de la canción, cola…), también se anima: la nueva entra por el lado que toca.
 *
 * [content] dibuja una canción (la portada, o la fila del mini reproductor); se llama para la actual y,
 * mientras se desliza, para la vecina que entra.
 */
@Composable
fun SwipeTracks(
    current: Track?,
    previous: Track?,
    next: Track?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 24.dp,
    content: @Composable (Track?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val step = width + with(LocalDensity.current) { gap.toPx() }

    // Lo que se está enseñando (puede ir un paso por detrás de la cola mientras dura la animación).
    var shownPrev by remember { mutableStateOf(previous) }
    var shownCur by remember { mutableStateOf(current) }
    var shownNext by remember { mutableStateOf(next) }
    /** -1: se deslizó hacia la siguiente y se espera al reproductor; 1: hacia la anterior; 0: nada. */
    var waiting by remember { mutableIntStateOf(0) }

    LaunchedEffect(current?.uri) {
        if (current?.uri == shownCur?.uri) {
            shownCur = current; shownPrev = previous; shownNext = next
            return@LaunchedEffect
        }
        if (shownCur == null || current == null || step <= 0f) {
            shownCur = current; shownPrev = previous; shownNext = next; waiting = 0
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        // ¿Hacia dónde? Si era la que se veía como anterior (o se deslizó a la derecha), entra por la izquierda.
        val back = waiting == 1 || (waiting == 0 && current.uri == shownPrev?.uri)
        if (back) {
            shownPrev = current
            offset.animateTo(step, tween(260))
        } else {
            shownNext = current
            offset.animateTo(-step, tween(260))
        }
        shownCur = current; shownPrev = previous; shownNext = next; waiting = 0
        offset.snapTo(0f)
    }
    // Las vecinas cambian (se carga la siguiente, se reordena la cola…): al día si no hay animación en curso.
    LaunchedEffect(previous?.uri, next?.uri) {
        if (waiting == 0 && !offset.isRunning && current?.uri == shownCur?.uri) {
            shownPrev = previous; shownNext = next
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .testTag("track_swipe")
            .pointerInput(previous?.uri, next?.uri) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) } },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val o = offset.value
                        val far = size.width * 0.3f
                        scope.launch {
                            when {
                                next != null && o < 0 && (o < -far || v < -1000f) -> {
                                    waiting = -1
                                    offset.animateTo(-step, tween(180))
                                    onNext()
                                    // Si el reproductor no cambia de canción, la portada vuelve a su sitio.
                                    delay(1500)
                                    if (waiting == -1) { waiting = 0; offset.animateTo(0f) }
                                }
                                previous != null && o > 0 && (o > far || v > 1000f) -> {
                                    waiting = 1
                                    offset.animateTo(step, tween(180))
                                    onPrevious()
                                    delay(1500)
                                    if (waiting == 1) { waiting = 0; offset.animateTo(0f) }
                                }
                                else -> offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                ) { change, dx ->
                    if (waiting != 0) return@detectHorizontalDragGestures
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Sin vecina en ese lado, el gesto se resiste (como al final de una lista).
                    val o = offset.value
                    val blocked = (o + dx < 0 && next == null) || (o + dx > 0 && previous == null)
                    scope.launch { offset.snapTo(o + if (blocked) dx * 0.25f else dx) }
                }
            },
    ) {
        val o = offset.value
        val center = if (shownCur?.uri == current?.uri) current else shownCur
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content(center) }
        if (o > 0.5f && shownPrev != null) {
            Box(Modifier.matchParentSize().graphicsLayer { translationX = offset.value - step }) { content(shownPrev) }
        }
        if (o < -0.5f && shownNext != null) {
            Box(Modifier.matchParentSize().graphicsLayer { translationX = offset.value + step }) { content(shownNext) }
        }
    }
}
