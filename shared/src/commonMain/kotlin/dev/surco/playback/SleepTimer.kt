package dev.surco.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.surco.util.nowMillis

/**
 * Temporizador para dormir: para la música al cabo de X minutos o al acabar la canción.
 * Lo programa la interfaz y lo ejecuta el reproductor de cada plataforma (que sigue vivo con la app cerrada):
 * baja el volumen poco a poco en los últimos segundos y pausa.
 */
class SleepTimer {
    data class State(
        /** Hora (System.currentTimeMillis) a la que se para; 0 = sin tiempo. */
        val endsAt: Long = 0,
        /** Parar al acabar la canción que suena. */
        val endOfTrack: Boolean = false,
        /** Minutos elegidos (para marcar la opción en la lista). */
        val minutes: Int = 0,
    ) {
        fun active(now: Long = nowMillis()) = endOfTrack || endsAt > now
        fun remainingMs(now: Long = nowMillis()) = (endsAt - now).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(minutes: Int) { _state.value = State(endsAt = nowMillis() + minutes * 60_000L, minutes = minutes) }
    fun endOfTrack() { _state.value = State(endOfTrack = true) }
    fun cancel() { _state.value = State() }

    /** Lo llama el servicio al parar la música. */
    fun finished() { _state.value = State() }

    companion object {
        val OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)
        const val FADE_MS = 10_000L

        /** «1:05:09», «12:03», «0:45». */
        fun format(ms: Long): String {
            val total = (ms + 999) / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            fun two(n: Long) = n.toString().padStart(2, '0')
            return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
        }
    }
}
