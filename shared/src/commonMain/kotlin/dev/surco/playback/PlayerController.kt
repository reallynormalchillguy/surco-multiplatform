package dev.surco.playback

import dev.surco.data.Track
import kotlinx.coroutines.flow.StateFlow

/** Modos de repetición (mismos valores que Media3). */
object Repeat {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

data class PlayerUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Repeat.OFF,
    /** "tipo|nombre", p. ej. "álbum|Sol.Hz" */
    val source: String = "",
    val queue: List<String> = emptyList(),
    val queueIndex: Int = -1,
    /** Índices de la cola en el orden en que sonarán (respeta el modo aleatorio). */
    val upNext: List<Int> = emptyList(),
)

/** Lo que la interfaz puede pedirle al reproductor de la plataforma (Media3 en Android, AVFoundation en iOS). */
interface PlayerController {
    val state: StateFlow<PlayerUiState>
    fun play(tracks: List<Track>, startIndex: Int = 0, source: String, shuffle: Boolean = false)
    fun togglePlay()
    fun next()
    /** Si han pasado más de 3 s, vuelve al principio de la canción. */
    fun previous()
    fun seekTo(ms: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playNext(track: Track)
    /** Pone varias canciones (p. ej. un álbum entero) justo después de la que suena, en su orden. */
    fun playNext(tracks: List<Track>)
    fun enqueue(tracks: List<Track>)
    /** Quita de la cola todas las apariciones de estas canciones (p. ej. tras borrarlas del dispositivo). */
    fun removeFromQueue(uris: Collection<String>)
    fun skipToQueueIndex(index: Int)
    fun removeFromQueue(index: Int)
    fun moveInQueue(from: Int, to: Int)
    /** Sólo para las capturas de pantalla automáticas (CI). */
    fun debugSetState(s: PlayerUiState)
}
