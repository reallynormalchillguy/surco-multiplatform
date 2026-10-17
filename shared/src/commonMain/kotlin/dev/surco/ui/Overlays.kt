package dev.surco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** Panel lateral del perfil (al tocar tu foto). */
    var drawer by mutableStateOf(false)
    /** Enlace (de Spotify) que llegó de otra app con «Compartir», pendiente de abrir. */
    var pendingLink by mutableStateOf<String?>(null)

    /** Veces que se ha tocado cada pestaña de abajo (para volver arriba del todo). */
    val tabTaps = mutableStateMapOf<String, Int>()

    fun tapTab(route: String) { tabTaps[route] = (tabTaps[route] ?: 0) + 1 }

    /** Cierra la capa superior. Devuelve true si había alguna abierta. */
    fun back(): Boolean = when {
        lyrics -> { lyrics = false; true }
        player -> { player = false; true }
        drawer -> { drawer = false; true }
        else -> false
    }
}

/**
 * Al tocar la pestaña [route] de abajo (desde otra pestaña, desde una página dentro de ella o estando
 * ya en ella) su pantalla vuelve arriba del todo. Al volver «atrás» desde un álbum, en cambio, se
 * queda donde estaba.
 */
@Composable
fun ScrollToTopOnTabTap(route: String, scrollToTop: suspend () -> Unit) {
    val tap = Overlays.tabTaps[route] ?: 0
    var handled by rememberSaveable(route) { mutableIntStateOf(tap) }
    LaunchedEffect(tap) {
        if (tap != handled) {
            handled = tap
            scrollToTop()
        }
    }
}
