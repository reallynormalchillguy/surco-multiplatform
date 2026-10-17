package dev.surco.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.surco.ui.theme.SurcoColors

/**
 * Imagen del tema, detrás de todas las pantallas. «Floral vino»: el dibujo tal cual, anclado a la derecha
 * (donde están los ornamentos) y con un velo vino muy suave para que el texto encima se lea bien.
 */
@Composable
fun ThemeBackdrop() {
    if (SurcoColors.backdrop != "floral") return
    Box(Modifier.fillMaxSize()) {
        Image(
            floralPainter(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0.75f, 0f),
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(SurcoColors.Background.copy(alpha = 0.22f)))
    }
}

/** Miniatura de un tema para elegirlo en Ajustes. */
@Composable
fun FloralThumbnail(modifier: Modifier) {
    Image(
        floralPainter(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0.75f, 0f),
        modifier = modifier,
    )
}
