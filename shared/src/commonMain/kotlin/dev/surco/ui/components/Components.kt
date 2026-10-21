package dev.surco.ui.components

import dev.surco.i18n.S

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import dev.surco.ui.extractArtColor
import okio.Path.Companion.toPath
import dev.surco.data.AlbumRow
import dev.surco.data.ArtistRow
import dev.surco.data.Track
import dev.surco.ui.theme.SurcoColors

// ---------------------------------------------------------------- Imágenes

@Composable
fun ArtImage(
    path: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    placeholder: ImageVector = Icons.Filled.MusicNote,
) {
    Box(
        modifier
            .clip(shape)
            .background(SurcoColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = if (path.startsWith("http")) path else path.toPath(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(placeholder, null, tint = SurcoColors.TextTertiary, modifier = Modifier.fillMaxSize(0.42f))
        }
    }
}

/** Mosaico 2×2 como el de las listas. */
@Composable
fun Mosaic(paths: List<String>, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    if (paths.size < 4) {
        ArtImage(paths.firstOrNull(), modifier, shape)
        return
    }
    Column(modifier.clip(shape)) {
        Row(Modifier.weight(1f)) {
            ArtImage(paths[0], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
            ArtImage(paths[1], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
        }
        Row(Modifier.weight(1f)) {
            ArtImage(paths[2], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
            ArtImage(paths[3], Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp))
        }
    }
}

/** Cuadro degradado violeta de "Canciones que te gustan". */
@Composable
fun LikedArt(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF4A1CF0), Color(0xFF8E7CF6), Color(0xFFC4EFD9)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.38f))
    }
}

private val colorCache = HashMap<String, Color>()

/** Color dominante de una portada, sin animar (usa la misma caché que [rememberArtColor]). */
suspend fun artColorOf(context: coil3.PlatformContext, path: String): Color? =
    colorCache[path] ?: dev.surco.ui.extractArtColor(context, path)?.also { colorCache[path] = it }

/** Color dominante (oscuro) de una portada (archivo local o URL), para fondos y el mini reproductor. */
@Composable
fun rememberArtColor(path: String?, fallback: Color = Color(0xFF3A3A3A)): Color {
    val context = LocalPlatformContext.current
    var color by remember(path) { mutableStateOf(path?.let { colorCache[it] } ?: fallback) }
    LaunchedEffect(path) {
        if (path != null && colorCache[path] == null) {
            val c = extractArtColor(context, path)
            if (c != null) {
                colorCache[path] = c
                color = c
            }
        }
    }
    return animateColorAsState(color, tween(600), label = "artColor").value
}

// ---------------------------------------------------------------- Textos y chips

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 2)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = SurcoColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun SurcoChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SurcoColors.Accent else SurcoColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) SurcoColors.OnAccent else SurcoColors.TextPrimary,
        )
    }
}

/** Botón redondo del avatar / ajustes arriba a la izquierda. */
@Composable
fun AppAvatar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurcoColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.GraphicEq, S.settings, tint = SurcoColors.OnAccent, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- Tarjetas

@Composable
fun AlbumCard(album: AlbumRow, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 156.dp, caption: String? = S.album) {
    Column(modifier.width(size).clickable(onClick = onClick)) {
        ArtImage(album.artPath, Modifier.size(size), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
        }
        Text(album.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tarjeta de álbum compacta (título + "Álbum • artista"), como en "Recientes". */
@Composable
fun AlbumCardCompact(album: AlbumRow, onClick: () -> Unit, size: Dp = 124.dp) {
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        ArtImage(album.artPath, Modifier.size(size), RoundedCornerShape(4.dp))
        Spacer(Modifier.height(8.dp))
        Text(album.album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(S.albumBy(album.artist), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistCircle(artist: ArtistRow, onClick: () -> Unit, size: Dp = 156.dp) {
    Column(Modifier.width(size).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtImage(artist.imagePath ?: artist.artPath, Modifier.size(size), CircleShape, Icons.Filled.Person)
        Spacer(Modifier.height(10.dp))
        Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Celda de acceso rápido (cuadrícula 2×4 de la pantalla de inicio). */
@Composable
fun QuickTile(title: String, modifier: Modifier = Modifier, onClick: () -> Unit, art: @Composable (Modifier) -> Unit) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SurcoColors.SurfaceHigh)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(56.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- Filas

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    number: Int? = null,
    showArt: Boolean = true,
    liked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.Accent, modifier = Modifier.size(16.dp))
                else Text("$number", style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary)
            }
        }
        if (showArt) {
            ArtImage(track.artPath, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) SurcoColors.Accent else SurcoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = SurcoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onToggleLike != null) {
            IconButton(onClick = onToggleLike) {
                if (liked) Icon(Icons.Filled.CheckCircle, S.removeFavourite, tint = SurcoColors.Accent)
                else Icon(Icons.Outlined.AddCircleOutline, S.addFavourite, tint = SurcoColors.TextSecondary)
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, S.moreOptions, tint = SurcoColors.TextSecondary)
        }
    }
}

/** Fila genérica de biblioteca/búsqueda: imagen + título + subtítulo + acción a la derecha. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    artSize: Dp = 64.dp,
    pinned: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art(Modifier.size(artSize).clip(if (circle) CircleShape else RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    Icon(Icons.Filled.PushPin, null, tint = SurcoColors.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Ancho de las tarjetas en filas horizontales a pantalla completa. */
val HorizontalSpacing = Arrangement.spacedBy(14.dp)

@Composable
fun SquareBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.aspectRatio(1f)) { content() }
}
