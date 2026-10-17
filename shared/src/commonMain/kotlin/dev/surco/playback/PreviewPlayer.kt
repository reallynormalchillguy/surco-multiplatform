package dev.surco.playback

import kotlinx.coroutines.flow.StateFlow

/** Escucha previa en curso: qué fragmento suena y por dónde va (0–1). */
data class PreviewState(val url: String? = null, val playing: Boolean = false, val progress: Float = 0f, val failed: String? = null)

/**
 * Escuchas previas de 30 segundos (las de Deezer) de canciones que no tienes. Cada plataforma usa su propio
 * reproductor: pausa la música que estuviera sonando y la reanuda al terminar la vista previa.
 */
interface PreviewPlayer {
    val state: StateFlow<PreviewState>
    /** Toca o para la vista previa de [url]. */
    fun toggle(url: String)
    /** Para la vista previa (y reanuda la música si se había pausado por ella). */
    fun stop()
}
