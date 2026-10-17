package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.surco.ui.theme.SurcoShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.surco.data.Track
import dev.surco.data.formatDuration
import dev.surco.data.qualityLabel
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.rememberArtColor
import dev.surco.ui.theme.SurcoColors

/** La canción que suena ahora mismo (o null). */
@Composable
fun rememberCurrentTrack(): Track? {
    val actions = LocalActions.current
    val id = LocalPlayer.current.mediaId ?: return null
    val flow = remember(id) { actions.container.db.dao().trackFlow(id) }
    val track by flow.collectAsState(initial = null)
    return track
}

/** Convierte "álbum|Sol.Hz" en ("Reproduciendo desde el álbum", "Sol.Hz"). */
fun sourceHeader(source: String): Pair<String, String> {
    val type = source.substringBefore('|', "")
    val name = source.substringAfter('|', "")
    val label = S.playingFrom(type)
    return label to name
}

// ======================================================================= Mini reproductor

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state = LocalPlayer.current
    val actions = LocalActions.current
    val track = rememberCurrentTrack() ?: return
    val liked = track.uri in LocalLiked.current
    val color = rememberArtColor(track.artPath)
    val bg = lerp(color, Color.Black, 0.25f)
    val progress = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .testTag("mini_player")
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Portada y título: deslizar a los lados cambia de canción, como en Spotify.
            SwipeTracks(
                current = track,
                modifier = Modifier.weight(1f).clipToBounds(),
                gap = 16.dp,
            ) { t -> if (t != null) MiniTrackInfo(t) }
            IconButton(onClick = { actions.toggleLike(track.uri, liked) }) {
                if (liked) Icon(Icons.Filled.CheckCircle, S.removeFavourite, tint = SurcoColors.Accent)
                else Icon(Icons.Outlined.AddCircleOutline, S.addFavourite, tint = Color.White)
            }
            IconButton(onClick = { actions.player.togglePlay() }) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) S.pause else S.play,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        // Barra de progreso fina en la parte inferior
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
        }
    }
}

/** Portada pequeña + título · artista + calidad (lo que se desliza en el mini reproductor). */
@Composable
private fun MiniTrackInfo(track: Track) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(track.artPath, Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString { append(track.title); append(" • "); append(track.artist) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.Accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        track.qualityLabel().ifBlank { track.album },
                        style = MaterialTheme.typography.labelMedium,
                        color = SurcoColors.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
}

// ======================================================================= Pantalla completa

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val state = LocalPlayer.current
    val actions = LocalActions.current
    val track = rememberCurrentTrack()
    val color = rememberArtColor(track?.artPath, Color(0xFF2E4A50))
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showLyrics by Overlays::lyrics
    var lyricsNonce by remember(track?.uri) { mutableIntStateOf(0) }
    val lyrics = rememberLyrics(track, lyricsNonce)
    val (sourceLabel, sourceName) = sourceHeader(state.source)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Tirar hacia abajo cuando la pantalla está arriba del todo cierra el reproductor.
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeDistance = with(LocalDensity.current) { 140.dp.toPx() }
    val currentClose by rememberUpdatedState(onClose)
    val dismiss = remember(scroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && dragY > 0f) {
                    val newY = (dragY + available.y).coerceAtLeast(0f)
                    val used = newY - dragY
                    dragY = newY
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    dragY += available.y * 0.8f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f) return Velocity.Zero
                if (dragY > closeDistance || available.y > 3000f) {
                    currentClose()
                    delay(350)
                    dragY = 0f
                } else {
                    animate(dragY, 0f) { v, _ -> dragY = v }
                }
                return available
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.roundToInt()) }
            .nestedScroll(dismiss)
            .background(SurcoColors.Background)
            .background(
                Brush.verticalGradient(
                    0f to color,
                    0.45f to lerp(color, Color.Black, 0.55f),
                    1f to SurcoColors.Background,
                )
            )
            // Bloquea los toques para que no lleguen a la pantalla de debajo.
            .clickable(onClick = {}, indication = null, interactionSource = null),
    ) {
        // Como Spotify: la primera «página» ocupa la pantalla (cabecera, portada grande, título, barra,
        // controles y una fila de acciones) y la letra asoma por abajo; se sube para verla.
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val pageHeight = (maxHeight - 56.dp).coerceAtLeast(520.dp)
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(Modifier.fillMaxWidth().height(pageHeight).padding(horizontal = 24.dp)) {
                    // --- Cabecera
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, S.close, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        // «Reproduciendo desde…»: lleva a esa página (artista, álbum, lista, favoritas…).
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = track != null) {
                                val t = track ?: return@clickable
                                val type = state.source.substringBefore('|', "").lowercase()
                                val name = state.source.substringAfter('|', "")
                                onClose()
                                when (type) {
                                    "artista", "artist" -> actions.openArtist(name.ifBlank { t.groupArtist })
                                    "favoritas", "liked" -> actions.openLiked()
                                    "género", "genre" -> actions.openGenre(name)
                                    "lista", "playlist" -> actions.scope.launch {
                                        val id = actions.repo.dao.playlists().first().firstOrNull { it.name == name }?.id
                                        if (id != null) actions.openPlaylist(id) else actions.openAlbum(t.albumKey)
                                    }
                                    else -> actions.openAlbum(t.albumKey)
                                }
                            }.testTag("player_source"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                sourceLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                            )
                            Text(
                                sourceName.ifBlank { track?.album ?: "" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                        IconButton(onClick = { track?.let { actions.menu(it) } }, modifier = Modifier.offset(x = 12.dp)) {
                            Icon(Icons.Filled.MoreVert, S.more, tint = Color.White)
                        }
                    }

                    // --- Portada, centrada en el espacio libre (algo más de aire debajo, como en Spotify).
                    // Al abrir el reproductor la portada aparece creciendo y encendiéndose, como en Spotify.
                    val coverIn = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(Unit) { coverIn.animateTo(1f, androidx.compose.animation.core.tween(520, delayMillis = 60, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp, bottom = 28.dp)
                            .graphicsLayer {
                                val k = coverIn.value
                                scaleX = 0.88f + 0.12f * k; scaleY = 0.88f + 0.12f * k
                                alpha = 0.3f + 0.7f * k
                            },
                        contentAlignment = androidx.compose.ui.BiasAlignment(0f, 0.1f),
                    ) {
                        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                        // Portada: deslizar a la izquierda/derecha cambia de canción (con animación).
                        SwipeTracks(
                            current = track,
                            modifier = Modifier.size(side),
                            gap = 24.dp,
                        ) { t ->
                            ArtImage(
                                t?.artPath,
                                Modifier
                                    .size(side)
                                    .shadow(24.dp, RoundedCornerShape(SurcoShapes.Cover)),
                                RoundedCornerShape(SurcoShapes.Cover),
                            )
                        }
                    }

                    // --- Título y artista (con fundido al cambiar de canción)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = track,
                            contentKey = { it?.uri },
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) togetherWith
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
                            },
                            modifier = Modifier.weight(1f),
                            label = "titulo",
                        ) { track ->
                            Column(Modifier.fillMaxWidth()) {
                                // El título lleva al álbum de la canción.
                                Text(
                                    track?.title ?: "",
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    modifier = Modifier.clickable(enabled = track != null) {
                                        track?.let { onClose(); actions.openAlbum(it.albumKey) }
                                    }.testTag("player_title").basicMarquee(),
                                )
                                Text(
                                    track?.artist ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = track != null) {
                                        track?.let { onClose(); actions.openArtist(it.groupArtist) }
                                    },
                                )
                            }
                        }
                        if (track != null) {
                            val liked = track.uri in LocalLiked.current
                            IconButton(onClick = { actions.toggleLike(track.uri, liked) }, modifier = Modifier.offset(x = 10.dp)) {
                                if (liked) Icon(Icons.Filled.CheckCircle, S.removeFavourite, tint = SurcoColors.Accent, modifier = Modifier.size(30.dp))
                                else Icon(Icons.Outlined.AddCircleOutline, S.addFavourite, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SeekBar(state.position, state.duration, onSeek = { actions.player.seekTo(it) })
                    Row(Modifier.fillMaxWidth()) {
                        Text(state.position.formatDuration(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.weight(1f))
                        Text(state.duration.formatDuration(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }

                    // --- Controles: aleatorio · anterior · play · siguiente · temporizador
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToggleIcon(Icons.Filled.Shuffle, state.shuffle, S.shuffle) { actions.player.toggleShuffle() }
                        IconButton(onClick = { actions.player.previous() }, modifier = Modifier.size(60.dp)) {
                            Icon(Icons.Filled.SkipPrevious, S.previous, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        Box(
                            Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { actions.player.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) S.pause else S.play,
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        IconButton(onClick = { actions.player.next() }, modifier = Modifier.size(60.dp)) {
                            Icon(Icons.Filled.SkipNext, S.next, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        val timer by actions.container.sleepTimer.state.collectAsState()
                        Box(Modifier.testTag("sleep_timer")) {
                            ToggleIcon(Icons.Outlined.Timer, timer.active(), S.sleepTimer) { showTimer = true }
                        }
                    }

                    // --- Fila de abajo: calidad a la izquierda; repetir, compartir y cola a la derecha
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        val quality = track?.qualityLabel().orEmpty()
                        if (quality.isNotEmpty()) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.Accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(quality, style = MaterialTheme.typography.labelMedium, color = SurcoColors.Accent, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        ToggleIcon(
                            if (state.repeat == dev.surco.playback.Repeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            state.repeat != dev.surco.playback.Repeat.OFF,
                            S.repeat,
                            size = 24.dp,
                        ) { actions.player.cycleRepeat() }
                        IconButton(onClick = { track?.let { actions.share(it) } }) {
                            Icon(Icons.Outlined.Share, S.share, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { showQueue = true }, modifier = Modifier.offset(x = 10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, S.queue, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // --- Letra (asoma por debajo de la primera página)
                if (track != null) {
                    LyricsCard(
                        lyrics = lyrics,
                        color = lerp(color, Color.White, 0.06f),
                        position = state.position,
                        onOpen = { showLyrics = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        AnimatedVisibility(
            visible = showLyrics && track != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            if (track != null) {
                LyricsFullScreen(
                    track = track,
                    lyrics = lyrics,
                    color = color,
                    onClose = { showLyrics = false },
                    onReload = { lyricsNonce++ },
                )
            }
        }
    }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
    if (showTimer) SleepTimerSheet(onDismiss = { showTimer = false })
}

@Composable
private fun ToggleIcon(icon: ImageVector, active: Boolean, description: String, size: Dp = 26.dp, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = if (active) SurcoColors.Accent else Color.White, modifier = Modifier.size(size))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) SurcoColors.Accent else Color.Transparent)
        )
    }
}

@Composable
fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(duration)
    val fraction = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val d = currentDuration
                    if (d > 0) currentOnSeek(((off.x / size.width).coerceIn(0f, 1f) * d).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> drag = (off.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        val f = drag
                        val d = currentDuration
                        if (f != null && d > 0) currentOnSeek((f * d).toLong())
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val dragging = drag != null
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val y = size.height / 2
            val stroke = 4.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(0f, y), Offset(size.width * fraction, y), stroke, StrokeCap.Round)
            drawCircle(Color.White, radius = (if (dragging) 8.dp else 6.dp).toPx(), center = Offset(size.width * fraction, y))
        }
    }
}

// ======================================================================= Cola

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val state = LocalPlayer.current
    val actions = LocalActions.current
    var byUri by remember { mutableStateOf<Map<String, Track>>(emptyMap()) }
    LaunchedEffect(state.queue) {
        byUri = state.queue.distinct().chunked(500).flatMap { actions.container.db.dao().tracksByUris(it) }.associateBy { it.uri }
    }
    val current = state.mediaId?.let { byUri[it] }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurcoColors.Surface,
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Text(S.nowPlaying, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            }
            if (current != null) {
                item { QueueRow(current, isCurrent = true, onClick = {}, onRemove = null) }
            }
            item {
                HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    if (state.shuffle) S.upNextShuffled else S.upNext,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.upNext, key = { it }) { index ->
                val uri = state.queue.getOrNull(index)
                val t = uri?.let { byUri[it] }
                if (t != null) {
                    QueueRow(
                        t, isCurrent = false,
                        onClick = { actions.player.skipToQueueIndex(index) },
                        onRemove = { actions.player.removeFromQueue(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(t: Track, isCurrent: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(t.artPath, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                color = if (isCurrent) SurcoColors.Accent else Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(t.artist, color = SurcoColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, S.removeFromQueue, tint = SurcoColors.TextSecondary) }
        }
    }
}

// ======================================================================= Temporizador

/** Elegir cuándo se para la música: X minutos o al acabar la canción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val timer = actions.container.sleepTimer
    val t by timer.state.collectAsState()
    // Cuenta atrás en vivo.
    var now by remember { mutableStateOf(dev.surco.util.nowMillis()) }
    LaunchedEffect(t) {
        while (t.endsAt > 0) { now = dev.surco.util.nowMillis(); kotlinx.coroutines.delay(500) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).testTag("sleep_sheet")) {
            Text(S.sleepTimer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (t.active(now)) {
                Text(
                    if (t.endOfTrack) S.timerStopsAtEnd else S.timerStopsIn(dev.surco.playback.SleepTimer.format(t.remainingMs(now))),
                    style = MaterialTheme.typography.bodyMedium, color = SurcoColors.Accent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 8.dp))
            @Composable
            fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) SurcoColors.Accent else SurcoColors.TextPrimary, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Filled.CheckCircle, null, tint = SurcoColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            dev.surco.playback.SleepTimer.OPTIONS.forEach { m ->
                Option(S.minutes(m), t.active(now) && !t.endOfTrack && t.minutes == m) {
                    timer.start(m); actions.toast(S.timerSet(m)); onDismiss()
                }
            }
            Option(S.endOfTrack, t.endOfTrack) { timer.endOfTrack(); actions.toast(S.timerStopsAtEnd); onDismiss() }
            if (t.active(now)) {
                HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 4.dp))
                Option(S.turnOffTimer, false) { timer.cancel(); actions.toast(S.timerOff); onDismiss() }
            }
        }
    }
}
