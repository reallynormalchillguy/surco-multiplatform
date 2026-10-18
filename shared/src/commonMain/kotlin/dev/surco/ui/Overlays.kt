package dev.surco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

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

    /** Token de Last.fm a la espera de que el usuario permita el acceso en la web. */
    var lastfmPending by mutableStateOf<String?>(null)
    /** Token que llegó por el enlace «surco://lastfm-auth?token=…» al volver de la web de Last.fm. */
    var lastfmCallback by mutableStateOf<String?>(null)

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
 * Estado de la lista de una pestaña de abajo: cada vez que se toca la pestaña (desde otra, desde una
 * página dentro de ella o estando ya en ella) la lista empieza de cero, arriba del todo, sin animación.
 * Al volver «atrás» desde un álbum, en cambio, sigue donde estaba.
 */
@Composable
fun rememberTabListState(route: String): androidx.compose.foundation.lazy.LazyListState {
    val tap = Overlays.tabTaps[route] ?: 0
    val state = rememberSaveable(tap, key = "tab_list_${route}_$tap", saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    KeepAtTopWhileLoading(state.interactionSource, { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }, { state.layoutInfo.totalItemsCount }) {
        state.scrollToItem(0)
    }
    return state
}

@Composable
fun rememberTabGridState(route: String): androidx.compose.foundation.lazy.grid.LazyGridState {
    val tap = Overlays.tabTaps[route] ?: 0
    val state = rememberSaveable(tap, key = "tab_grid_${route}_$tap", saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {
        androidx.compose.foundation.lazy.grid.LazyGridState()
    }
    KeepAtTopWhileLoading(state.interactionSource, { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }, { state.layoutInfo.totalItemsCount }) {
        state.scrollToItem(0)
    }
    return state
}

/**
 * Una lista que empieza arriba del todo sigue arriba mientras se va llenando. Si no, al llegar las
 * secciones de la base de datos (después de las recomendaciones, que ya estaban en memoria), la lista se
 * quedaba «enganchada» a la primera que vio y aparecía a media página. En cuanto el usuario la toca, se
 * le deja en paz.
 */
@Composable
private fun KeepAtTopWhileLoading(
    interactions: androidx.compose.foundation.interaction.InteractionSource,
    atTop: () -> Boolean,
    itemCount: () -> Int,
    scrollTop: suspend () -> Unit,
) {
    LaunchedEffect(interactions) {
        if (!atTop()) return@LaunchedEffect
        var touched = false
        // Sólo mientras se carga; después, la lista es del usuario.
        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            launch {
                interactions.interactions.collect { i ->
                    if (i is androidx.compose.foundation.interaction.DragInteraction.Start) touched = true
                }
            }
            var lastCount = itemCount()
            var wasTop = true
            androidx.compose.runtime.snapshotFlow { itemCount() to atTop() }.collect { (count, top) ->
                // Sólo si se ha movido porque han llegado secciones nuevas (no por un desplazamiento).
                if (count != lastCount && wasTop && !top && !touched) scrollTop() else wasTop = top
                lastCount = count
            }
        }
    }
}

/** Al volver de Last.fm por el enlace, termina la conexión (esté abierta la pantalla que esté). */
@Composable
fun LastFmCallbackHandler() {
    val actions = LocalActions.current
    val token = Overlays.lastfmCallback ?: return
    LaunchedEffect(token) {
        Overlays.lastfmCallback = null
        val t = Overlays.lastfmPending ?: token
        val ok = runCatching { actions.repo.lastFm.webAuthFinish(t) || (t != token && actions.repo.lastFm.webAuthFinish(token)) }.getOrDefault(false)
        if (ok) {
            Overlays.lastfmPending = null
            actions.toast(dev.surco.i18n.S.connectedToLastfm)
            runCatching { actions.repo.flushScrobbles() }
        }
    }
}
