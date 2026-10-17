package dev.surco.playback

import dev.surco.data.PlayEvent
import dev.surco.data.Repository
import dev.surco.net.nowPlaying
import dev.surco.data.ScrobbleMode
import dev.surco.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.surco.util.nowMillis
import kotlin.time.TimeSource

/**
 * Cuenta el tiempo real escuchado de cada canción y aplica las reglas de Last.fm:
 * la canción debe durar más de 30 s y haberse escuchado la mitad o 4 minutos (lo que ocurra antes).
 * También registra cada escucha en el historial local.
 */
class ScrobbleTracker(private val scope: CoroutineScope, private val repo: Repository) {

    private var current: Track? = null
    private var startedAtSec = 0L
    private var playedMs = 0L
    private var resumedAt = -1L
    private var scrobbled = false
    private var nowPlayingSent = false
    private var historyRecorded = false
    private var timer: Job? = null
    private val origin = TimeSource.Monotonic.markNow()

    /** Reloj monótono (no salta si cambia la hora del sistema). */
    private fun elapsed(): Long = origin.elapsedNow().inWholeMilliseconds

    private val builtin get() = repo.prefs.current.scrobbleMode == ScrobbleMode.BUILTIN && repo.prefs.current.lastfmLoggedIn

    fun onTrackStarted(track: Track?, isPlaying: Boolean) {
        timer?.cancel()
        current = track
        startedAtSec = nowMillis() / 1000
        playedMs = 0
        resumedAt = -1
        scrobbled = false
        nowPlayingSent = false
        historyRecorded = false
        if (isPlaying) onPlayingChanged(true)
    }

    /**
     * El reproductor ya sabe cuánto dura la canción: si la biblioteca no lo sabía, se apunta (para los
     * minutos del álbum y para que se pueda scrobblear).
     */
    fun onDuration(ms: Long) {
        val t = current ?: return
        if (ms <= 0 || t.durationMs > 0) return
        val fixed = t.copy(durationMs = ms)
        current = fixed
        scope.launch { runCatching { repo.dao.fillDuration(t.uri, ms) } }
        if (resumedAt >= 0) armTimer(fixed)
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        val t = current ?: return
        if (isPlaying) {
            if (resumedAt < 0) resumedAt = elapsed()
            if (!historyRecorded) {
                historyRecorded = true
                scope.launch {
                    repo.dao.insertPlay(
                        PlayEvent(trackUri = t.uri, albumKey = t.albumKey, artist = t.groupArtist, playedAt = nowMillis())
                    )
                }
            }
            if (!nowPlayingSent && builtin) {
                nowPlayingSent = true
                scope.launch { runCatching { repo.lastFm.nowPlaying(t) } }
            }
            armTimer(t)
        } else {
            accumulate()
            timer?.cancel()
        }
    }

    fun finish() {
        timer?.cancel()
        accumulate()
        val t = current ?: return
        val threshold = threshold(t) ?: return
        if (!scrobbled && playedMs >= threshold) {
            scrobbled = true
            enqueue(t)
        }
    }

    private fun accumulate() {
        if (resumedAt >= 0) {
            playedMs += elapsed() - resumedAt
            resumedAt = -1
        }
    }

    private fun playedSoFar(): Long =
        playedMs + if (resumedAt >= 0) elapsed() - resumedAt else 0

    private fun threshold(t: Track): Long? =
        if (t.durationMs < 30_000) null else minOf(t.durationMs / 2, 240_000L)

    private fun armTimer(t: Track) {
        timer?.cancel()
        if (scrobbled) return
        val threshold = threshold(t) ?: return
        val remaining = (threshold - playedSoFar()).coerceAtLeast(0)
        timer = scope.launch {
            delay(remaining)
            if (current === t && !scrobbled) {
                scrobbled = true
                enqueue(t)
            }
        }
    }

    private fun enqueue(t: Track) {
        // En modo externo (Pano Scrobbler) o desactivado no hacemos nada: la app externa
        // lee la MediaSession por su cuenta.
        if (!builtin) return
        // Se encola en el scope de la app para que el envío no se corte si el servicio se destruye.
        repo.enqueueScrobbleAsync(t, startedAtSec)
    }
}
