package dev.surco.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.surco.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Puente entre la interfaz y el servicio de reproducción (vía MediaController). */
class PlayerConnection(private val context: Context) : PlayerController {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    /** Sólo para las capturas de pantalla automáticas (CI). */
    override fun debugSetState(s: PlayerUiState) { _state.value = s }

    /** Órdenes que llegaron mientras el controlador no estaba conectado; se ejecutan al conectar. */
    private val pending = ArrayList<(MediaController) -> Unit>()

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            block(c)
        } else {
            pending += block
            connect()
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                pending.clear()
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh()
            startTicker()
            val queued = pending.toList()
            pending.clear()
            queued.forEach { runCatching { it(c) } }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        pending.clear()
        _state.update { it.copy(connected = false) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _state.update { it.copy(position = c.currentPosition, duration = c.duration.validDuration()) }
                }
                delay(250)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val upNext = ArrayList<Int>()
        val tl = c.currentTimeline
        if (!tl.isEmpty && c.currentMediaItemIndex != C.INDEX_UNSET) {
            var i = tl.getNextWindowIndex(c.currentMediaItemIndex, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && upNext.size < 500) {
                upNext += i
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        var prevIdx = C.INDEX_UNSET
        var nextIdx = C.INDEX_UNSET
        if (!tl.isEmpty && c.currentMediaItemIndex != C.INDEX_UNSET) {
            val rep = if (c.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            nextIdx = tl.getNextWindowIndex(c.currentMediaItemIndex, rep, c.shuffleModeEnabled)
            prevIdx = tl.getPreviousWindowIndex(c.currentMediaItemIndex, rep, c.shuffleModeEnabled)
        }
        _state.value = PlayerUiState(
            connected = true,
            mediaId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            position = c.currentPosition,
            duration = c.duration.validDuration(),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            source = c.currentMediaItem?.source() ?: "",
            queue = queue,
            queueIndex = c.currentMediaItemIndex,
            upNext = upNext,
            prevIndex = prevIdx,
            nextIndex = nextIdx,
        )
    }

    private fun Long.validDuration() = if (this == C.TIME_UNSET || this < 0) 0L else this

    // ---------- Acciones ----------

    override fun play(tracks: List<Track>, startIndex: Int, source: String, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem(source) }
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) tracks.indices.random() else startIndex.coerceIn(0, tracks.size - 1)
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    override fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    override fun next() = withController { it.seekToNext() }

    /** Como en todos los reproductores: si han pasado más de 3 s, vuelve al principio de la canción. */
    override fun previous() = withController { c ->
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    /** Siempre a la canción anterior (sin el «volver al principio» del botón): para el gesto de deslizar. */
    override fun previousTrack() = withController { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
    }

    override fun seekTo(ms: Long) {
        _state.update { it.copy(position = ms) }
        withController { it.seekTo(ms) }
    }

    override fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    override fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(track.toMediaItem("cola|Cola")))
            c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem(c.currentMediaItem?.source() ?: "cola|Cola"))
        }
    }

    /** Pone varias canciones (p. ej. un álbum entero) justo después de la que suena, en su orden. */
    override fun playNext(tracks: List<Track>) = withController { c ->
        if (tracks.isEmpty()) return@withController
        if (c.mediaItemCount == 0) {
            c.setMediaItems(tracks.map { it.toMediaItem("cola|Cola") })
            c.prepare(); c.play()
        } else {
            val src = c.currentMediaItem?.source() ?: "cola|Cola"
            c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem(src) })
        }
    }

    /** Quita de la cola todas las apariciones de estas canciones (p. ej. tras borrarlas del dispositivo). */
    override fun removeFromQueue(uris: Collection<String>) = withController { c ->
        val gone = uris.toHashSet()
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in gone) c.removeMediaItem(i)
        }
    }

    override fun enqueue(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(tracks.map { it.toMediaItem("cola|Cola") })
            c.prepare(); c.play()
        } else {
            c.addMediaItems(tracks.map { it.toMediaItem(c.currentMediaItem?.source() ?: "cola|Cola") })
        }
    }

    override fun skipToQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    override fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    override fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }
}
