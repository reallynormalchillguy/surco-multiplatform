package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

/**
 * Panel lateral del perfil, como el de Spotify: al tocar tu foto entra desde la izquierda y empuja
 * la pantalla hacia la derecha (que se oscurece). Se cierra tocando fuera, deslizando o con «atrás».
 * [content] es la app entera (lo que se empuja).
 */
@Composable
fun ProfileDrawerHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val open = Overlays.drawer
    LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f, tween(if (open) 340 else 280, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(SurcoColors.Background)) {
        val width = maxWidth * 0.84f
        val widthPx = with(LocalDensity.current) { width.toPx() }
        // Arrastrar hacia la izquierda cierra (sigue al dedo); al soltar, termina de abrir o de cerrar.
        val drag = Modifier.pointerInput(widthPx) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val keep = progress.value > 0.6f
                    Overlays.drawer = keep
                    scope.launch { progress.animateTo(if (keep) 1f else 0f, tween(220, easing = FastOutSlowInEasing)) }
                },
            ) { change, dx ->
                change.consume()
                scope.launch { progress.snapTo((progress.value + dx / widthPx).coerceIn(0f, 1f)) }
            }
        }

        // La app, empujada a la derecha.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = widthPx * progress.value }) {
            content()
            if (progress.value > 0f) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f * progress.value))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Overlays.drawer = false }
                        .then(drag)
                        .testTag("drawer_scrim"),
                )
            }
        }
        // El panel, entrando desde la izquierda.
        if (progress.value > 0f) {
            Box(
                Modifier.width(width).fillMaxHeight()
                    .graphicsLayer { translationX = -widthPx * (1f - progress.value) }
                    .background(SurcoColors.Background)
                    .then(drag),
            ) {
                DrawerContent(progress.value)
            }
        }
    }
}

@Composable
private fun DrawerContent(progress: Float) {
    val actions = LocalActions.current
    val settings by actions.repo.prefs.settings.collectAsState()
    val stats by actions.container.stats.state.collectAsState()
    val image = stats.report?.profile?.imageUrl?.takeIf { settings.lastfmLoggedIn && stats.report?.profile?.name.equals(settings.lastfmUser, ignoreCase = true) }
    fun go(block: () -> Unit) { Overlays.drawer = false; block() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("profile_drawer")) {
        // Cabecera: tu perfil (de Last.fm si está conectado).
        Row(
            Modifier.fillMaxWidth().clickable { go { if (settings.showStats) actions.openTab("stats") else actions.openSettings() } }.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(SurcoColors.Accent), contentAlignment = Alignment.Center) {
                if (image != null) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(
                    (settings.lastfmUser.ifBlank { "Surco" }).take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SurcoColors.OnAccent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(settings.lastfmUser.ifBlank { "Surco" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(S.viewProfile, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            }
        }
        HorizontalDivider(color = SurcoColors.SurfaceHigh)
        Spacer(Modifier.height(8.dp))
        val items = listOfNotNull(
            if (!settings.showStats) null else DrawerEntry(Icons.Filled.BarChart, S.listeningStats, "drawer_stats") { go { actions.openTab("stats") } },
            DrawerEntry(Icons.Filled.History, S.recents, "drawer_recents") { go { actions.go("recent") } },
            DrawerEntry(Icons.Filled.Favorite, S.likedSongs, "drawer_liked") { go { actions.openLiked() } },
            DrawerEntry(Icons.Filled.CloudDownload, "Soulseek", "drawer_soulseek") { go { actions.openSoulseek() } },
            DrawerEntry(Icons.Filled.Palette, S.colorPalette, "drawer_palette") { go { actions.openPalette() } },
            DrawerEntry(Icons.Filled.Settings, S.settings, "drawer_settings") { go { actions.openSettings() } },
        )
        items.forEachIndexed { i, e ->
            // Cada fila entra un poco después que la anterior (efecto cascada al abrir).
            val t = ((progress - i * 0.05f) / (1f - i * 0.05f)).coerceIn(0f, 1f)
            DrawerItem(e.icon, e.label, e.onClick, Modifier.graphicsLayer { translationX = -40f * (1f - t); alpha = t }.testTag(e.tag))
        }
    }
}

private class DrawerEntry(val icon: ImageVector, val label: String, val tag: String, val onClick: () -> Unit)

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SurcoColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
