package dev.surco.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Capas que se abren por encima de la navegación (reproductor y letra a pantalla completa).
 *
 * El botón/gesto «atrás» lo resuelve MainActivity consultando este objeto ANTES que la navegación,
 * así el orden en que Compose registre sus manejadores de «atrás» deja de importar
 * (antes la navegación podía quedarse el gesto y el reproductor no se cerraba).
 */
object Overlays {
    var player by mutableStateOf(false)
    var lyrics by mutableStateOf(false)
    /** Enlace (de Spotify) que llegó de otra app con «Compartir», pendiente de abrir. */
    var pendingLink by mutableStateOf<String?>(null)

    /** Cierra la capa superior. Devuelve true si había alguna abierta. */
    fun back(): Boolean = when {
        lyrics -> { lyrics = false; true }
        player -> { player = false; true }
        else -> false
    }
}
