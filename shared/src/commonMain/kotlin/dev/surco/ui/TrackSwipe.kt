package dev.surco.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import dev.surco.playback.Repeat
import dev.surco.data.Track
import dev.surco.playback.PlayerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** La cola en orden de reproducción, con la posición de cada canción (para saber cuál va antes y después). */
private class PlayOrder(state: PlayerUiState) {
    val queue = state.queue
    private val order = state.order.ifEmpty { state.queue.indices.toList() }
    private val pos = HashMap<Int, Int>(order.size * 2).apply { order.forEachIndexed { p, i -> put(i, p) } }
    private val wraps = state.repeat == Repeat.ALL && order.size > 1

    /** Índice de la canción [dir] pasos antes/después de [index] (-1 si no hay). */
    fun step(index: Int, dir: Int): Int {
        val p = pos[index] ?: return -1
        val q = p + dir
        return when {
            q in order.indices -> order[q]
            wraps -> order[((q % order.size) + order.size) % order.size]
            else -> -1
        }
    }

    fun position(index: Int) = pos[index] ?: -1
}

/** Un cambio de portada en curso: hacia [index] ([uri]), entrando por la derecha (dir 1) o la izquierda (-1). */
private data class Rotation(val index: Int, val uri: String, val dir: Int)

/**
 * Carrusel de canciones como el de Spotify: se desliza a la izquierda para pasar a la siguiente y a la
 * derecha para volver a la anterior, viendo cómo entra la otra portada. Cuando la canción cambia por
 * otro motivo (botones, fin de la canción, cola…), también se anima: la nueva entra por el lado que toca.
 *
 * Lo que se enseña no depende de cuándo responda el reproductor: al soltar, la portada termina de
 * entrar y se pide al reproductor esa canción exacta (por su posición en la cola). Así se puede
 * deslizar varias veces seguidas, muy rápido, sin saltos ni portadas que vuelven atrás.
 *
 * [content] dibuja una canción (la portada, o la fila del mini reproductor).
 */
@Composable
fun SwipeTracks(
    current: Track?,
    modifier: Modifier = Modifier,
    gap: Dp = 24.dp,
    content: @Composable (Track?) -> Unit,
) {
    val state = LocalPlayer.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val order = remember(state.queue, state.order, state.repeat) { PlayOrder(state) }
    val nav by rememberUpdatedState(order)

    val cache = remember { mutableStateMapOf<String, Track>() }
    val currentTrack by rememberUpdatedState(current)
    fun trackOf(uri: String?): Track? = when {
        uri == null -> null
        uri == currentTrack?.uri -> currentTrack
        else -> cache[uri]
    }

    // Lo que se enseña en el centro (puede ir por delante del reproductor, o por detrás mientras se anima).
    var shownIndex by remember { mutableIntStateOf(state.queueIndex) }
    var shownUri by remember { mutableStateOf(state.mediaId) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableStateOf<Rotation?>(null) }
    /** Canción pedida al reproductor tras deslizar y que aún no ha llegado (-1: ninguna). */
    var requested by remember { mutableIntStateOf(-1) }
    val anim = remember { arrayOfNulls<Job>(1) }

    var width by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val stepPx = width + gapPx

    fun shownValid() = shownUri != null && nav.queue.getOrNull(shownIndex) == shownUri
    fun neighbour(dir: Int): Int = if (shownValid()) nav.step(shownIndex, dir) else -1

    fun applyRotation(r: Rotation) {
        shownIndex = r.index; shownUri = r.uri; offset = 0f; rotation = null
    }

    /** Termina al instante la animación en curso (si se vuelve a tocar mientras entra una portada). */
    fun finishRunning() {
        anim[0]?.cancel(); anim[0] = null
        rotation?.let(::applyRotation)
    }

    fun rotate(r: Rotation, fast: Boolean) {
        finishRunning()
        rotation = r
        anim[0] = scope.launch {
            animate(offset, -r.dir * stepPx, animationSpec = tween(if (fast) 170 else 280)) { v, _ -> offset = v }
            applyRotation(r)
            anim[0] = null
        }
    }

    fun settle() {
        anim[0]?.cancel()
        rotation = null
        anim[0] = scope.launch {
            animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
            anim[0] = null
        }
    }

    /** El usuario soltó hacia la siguiente (1) o la anterior (-1). */
    fun commit(dir: Int) {
        val target = neighbour(dir)
        val uri = nav.queue.getOrNull(target)
        if (target < 0 || uri == null) { settle(); return }
        rotate(Rotation(target, uri, dir), fast = true)
        requested = target
        actions.player.seekToQueueIndex(target)
    }

    // El reproductor cambió de canción.
    LaunchedEffect(state.queueIndex, state.mediaId, requested) {
        val idx = state.queueIndex
        val uri = state.mediaId
        if (requested >= 0) {
            if (idx == requested) { requested = -1; return@LaunchedEffect }
            // Si el reproductor no llega a la canción pedida, la portada vuelve a la que suena.
            delay(2500)
            requested = -1
            return@LaunchedEffect
        }
        val heading = rotation?.let { it.index to it.uri } ?: (shownIndex to shownUri)
        if (heading.first == idx && heading.second == uri) return@LaunchedEffect
        if (uri == null || width == 0 || shownUri == null) {
            finishRunning(); shownIndex = idx; shownUri = uri; offset = 0f
            return@LaunchedEffect
        }
        finishRunning()
        if (shownUri == uri) { shownIndex = idx; return@LaunchedEffect } // misma canción, la cola se reordenó
        // ¿Por qué lado entra? Si era la anterior (o va antes en la cola), por la izquierda.
        val dir = when {
            !shownValid() -> 1
            nav.step(shownIndex, 1) == idx -> 1
            nav.step(shownIndex, -1) == idx -> -1
            nav.position(idx) in 0 until nav.position(shownIndex) -> -1
            else -> 1
        }
        rotate(Rotation(idx, uri, dir), fast = false)
    }

    // Precarga las canciones de alrededor (para que la portada vecina ya esté al empezar a deslizar).
    LaunchedEffect(shownIndex, rotation, order) {
        val around = ArrayList<String>()
        fun add(i: Int) { nav.queue.getOrNull(i)?.let { around += it } }
        rotation?.let { around += it.uri }
        add(shownIndex)
        var f = shownIndex; var b = shownIndex
        repeat(3) { f = nav.step(f, 1); add(f); b = nav.step(b, -1); add(b) }
        val missing = around.distinct().filter { it !in cache && it != currentTrack?.uri }
        if (missing.isNotEmpty()) runCatching { actions.repo.dao.tracksByUris(missing) }.getOrNull()?.forEach { cache[it.uri] = it }
        currentTrack?.let { if (cache[it.uri] == null) cache[it.uri] = it }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .testTag("track_swipe")
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { finishRunning(); tracker.resetTracking() },
                    onDragCancel = { settle() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val o = offset
                        val far = size.width * 0.28f
                        when {
                            o < 0 && (o < -far || v < -900f) && neighbour(1) >= 0 -> commit(1)
                            o > 0 && (o > far || v > 900f) && neighbour(-1) >= 0 -> commit(-1)
                            else -> settle()
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // Sin vecina en ese lado, el gesto se resiste (como al final de una lista).
                    val next = offset + dx
                    val blocked = (next < 0 && neighbour(1) < 0) || (next > 0 && neighbour(-1) < 0)
                    offset = (offset + if (blocked) dx * 0.25f else dx).coerceIn(-stepPx, stepPx)
                }
            },
    ) {
        val o = offset
        val r = rotation
        val center = trackOf(shownUri) ?: current?.takeIf { shownUri == null }
        Box(Modifier.graphicsLayer { translationX = offset }) { content(center) }
        if (o > 0.5f) {
            val left = if (r != null && r.dir < 0) trackOf(r.uri) else trackOf(nav.queue.getOrNull(neighbour(-1)))
            Box(Modifier.matchParentSize().graphicsLayer { translationX = offset - stepPx }) { content(left) }
        }
        if (o < -0.5f) {
            val right = if (r != null && r.dir > 0) trackOf(r.uri) else trackOf(nav.queue.getOrNull(neighbour(1)))
            Box(Modifier.matchParentSize().graphicsLayer { translationX = offset + stepPx }) { content(right) }
        }
    }
}
