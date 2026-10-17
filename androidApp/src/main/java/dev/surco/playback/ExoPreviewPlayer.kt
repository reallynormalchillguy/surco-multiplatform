package dev.surco.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Escucha previa en curso: qué fragmento suena y por dónde va (0–1). */


/**
 * Escuchas previas de 30 segundos (las de Deezer) de canciones que no tienes. Usa su propio reproductor:
 * pausa la música que estuviera sonando y la reanuda al terminar la vista previa.
 */
class ExoPreviewPlayer(private val context: Context, private val main: PlayerConnection) : PreviewPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exo: ExoPlayer? = null
    private var ticker: Job? = null
    /** La música principal sonaba y la pausamos nosotros: se reanuda al acabar. */
    private var resumeMain = false

    private val _state = MutableStateFlow(PreviewState())
    override val state: StateFlow<PreviewState> = _state.asStateFlow()

    init {
        // Si el usuario pone música mientras suena una vista previa, la vista previa se calla.
        scope.launch {
            var was = main.state.value.isPlaying
            main.state.collect {
                // Sólo cuenta el paso de «parada» a «sonando» (no la pausa que hacemos nosotros, que llega con retraso).
                if (it.isPlaying && !was && _state.value.url != null) { resumeMain = false; stop() }
                was = it.isPlaying
            }
        }
    }

    private fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) stop()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(playing = isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val url = _state.value.url
                    stop()
                    _state.value = PreviewState(failed = url)
                }
            })
            exo = p
        }

    /** Toca o para la vista previa de [url]. */
    override fun toggle(url: String) {
        val s = _state.value
        if (s.url == url) {
            stop()
            return
        }
        if (main.state.value.isPlaying) {
            main.togglePlay()
            resumeMain = true
        }
        val p = player()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
        _state.value = PreviewState(url = url, playing = true)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val d = p.duration.takeIf { it > 0 } ?: 30_000L
                _state.value = _state.value.copy(progress = (p.currentPosition.toFloat() / d).coerceIn(0f, 1f))
                delay(100)
            }
        }
    }

    /** Para la vista previa (y reanuda la música si la habíamos pausado). */
    override fun stop() {
        ticker?.cancel()
        exo?.let { it.stop(); it.clearMediaItems() }
        if (_state.value.url != null) _state.value = PreviewState()
        if (resumeMain) {
            resumeMain = false
            if (!main.state.value.isPlaying) main.togglePlay()
        }
    }
}
