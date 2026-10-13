package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.Lyrics
import dev.surco.data.Track
import dev.surco.data.formatDuration
import dev.surco.data.indexAt
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

/** Estado de la letra de [track]. Cambiar [nonce] fuerza una nueva búsqueda. */
@Composable
fun rememberLyrics(track: Track?, nonce: Int): Lyrics {
    val repo = LocalActions.current.container.lyrics
    val uri = track?.uri
    val value by produceState<Lyrics>(Lyrics.Loading, uri, nonce) {
        value = Lyrics.Loading
        value = if (track == null) Lyrics.None
        else runCatching { repo.load(track, force = nonce > 0) }.getOrDefault(Lyrics.None)
    }
    return value
}



// ======================================================================= Tarjeta bajo el reproductor

@Composable
fun LyricsCard(
    lyrics: Lyrics,
    color: Color,
    position: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onOpen)
            .testTag("lyrics_card")
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.lyrics, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.OpenInFull, S.expandLyrics, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        when (lyrics) {
            Lyrics.Loading -> CardMessage(S.searchingLyrics)
            Lyrics.None -> CardMessage(S.lyricsNotFound)
            Lyrics.Instrumental -> CardMessage(S.instrumental)
            is Lyrics.Plain -> {
                lyrics.lines.take(10).forEach { line ->
                    Text(
                        line.ifBlank { " " },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            is Lyrics.Synced -> SyncedPreview(lyrics, position, color)
        }
    }
}

@Composable
private fun CardMessage(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(end = 8.dp))
}

/** Ventana de ~7 líneas que avanza sola con la canción. */
@Composable
private fun SyncedPreview(lyrics: Lyrics.Synced, position: Long, fade: Color) {
    val current = lyrics.lines.indexAt(position)
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        val target = (current - 1).coerceAtLeast(0)
        if (target != state.firstVisibleItemIndex) state.animateScrollToItem(target)
    }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        LazyColumn(
            state = state,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(lyrics.lines) { i, line -> LyricText(line.text, i, current) }
        }
        // Difumina la última línea en vez de cortarla en seco.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fade)))
        )
    }
}

@Composable
private fun LyricText(text: String, index: Int, current: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    val target = when {
        index == current -> Color.White
        index < current -> Color.White.copy(alpha = 0.72f)
        else -> Color.White.copy(alpha = 0.38f)
    }
    val c by animateColorAsState(target, tween(250), label = "lyric")
    Text(
        text.ifBlank { "♪" },
        style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
        color = c,
        modifier = modifier.padding(end = 8.dp),
    )
}

// ======================================================================= Pantalla completa

@Composable
fun LyricsFullScreen(
    track: Track,
    lyrics: Lyrics,
    color: Color,
    onClose: () -> Unit,
    onReload: () -> Unit,
) {
    val actions = LocalActions.current
    val state = LocalPlayer.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val raw = (lyrics as? Lyrics.Synced)?.raw ?: (lyrics as? Lyrics.Plain)?.raw

    Column(
        Modifier
            .fillMaxSize()
            .background(SurcoColors.Background)
            .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.45f))))
            .clickable(enabled = true, onClick = {}, indication = null, interactionSource = null)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // --- Cabecera
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, S.close, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, S.lyricsOptions, tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(S.searchAgain) }, onClick = { menu = false; onReload() })
                    DropdownMenuItem(text = { Text(if (raw == null) S.writeLyrics else S.editLyrics) }, onClick = { menu = false; editing = true })
                    if (raw != null) {
                        DropdownMenuItem(
                            text = { Text(S.saveToFile) },
                            onClick = {
                                menu = false
                                actions.scope.launch {
                                    runCatching { actions.container.lyrics.embed(track, raw) }
                                        .onSuccess { actions.toast(S.lyricsSavedIn(track.fileName)) }
                                        .onFailure { actions.toast(S.couldNotSave(it.message)) }
                                }
                            },
                        )
                    }
                }
            }
        }

        // --- Letra
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (lyrics) {
                is Lyrics.Synced -> SyncedFull(lyrics, state.position) { actions.player.seekTo(it) }
                is Lyrics.Plain -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lyrics.lines) { _, line ->
                        Text(line.ifBlank { " " }, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                    item { SourceFooter(lyrics.source) }
                }
                Lyrics.Loading -> FullMessage(S.searchingLyrics)
                Lyrics.Instrumental -> FullMessage(S.instrumental)
                Lyrics.None -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(S.noLyrics, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        S.noLyricsAnywhere,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        TextButton(onClick = onReload) { Text(S.searchAgain, color = Color.White) }
                        TextButton(onClick = { editing = true }) { Text(S.writeThem, color = Color.White) }
                    }
                }
            }
        }

        // --- Mini controles
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            SeekBar(state.position, state.duration, onSeek = { actions.player.seekTo(it) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.position.formatDuration(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable { actions.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) S.pause else S.play,
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(state.duration.formatDuration(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }

    if (editing) {
        LyricsEditDialog(
            initial = raw.orEmpty(),
            onDismiss = { editing = false },
            onSave = { text ->
                editing = false
                actions.scope.launch {
                    actions.container.lyrics.saveManual(track, text)
                    onReload()
                }
            },
        )
    }
}

@Composable
private fun FullMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun SourceFooter(source: String) {
    val label = S.lyricsSource(source)
    if (label.isNotEmpty()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SyncedFull(lyrics: Lyrics.Synced, position: Long, onSeek: (Long) -> Unit) {
    val current = lyrics.lines.indexAt(position)
    val listState = rememberLazyListState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    var firstScroll by remember { mutableIntStateOf(0) }

    // Sigue la canción salvo que el usuario haya movido la lista hace menos de 3 s.
    LaunchedEffect(current) {
        if (dev.surco.util.nowMillis() - lastTouch < 3000) return@LaunchedEffect
        val target = (current - 2).coerceAtLeast(0)
        if (firstScroll == 0) { listState.scrollToItem(target); firstScroll = 1 } else listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    lastTouch = dev.surco.util.nowMillis()
                }
            },
    ) {
        itemsIndexed(lyrics.lines) { i, line ->
            LyricText(
                line.text, i, current, big = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { lastTouch = 0; onSeek(line.timeMs) },
            )
        }
        item { SourceFooter(lyrics.source) }
    }
}

@Composable
private fun LyricsEditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.SurfaceHigh,
        title = { Text(S.lyrics) },
        text = {
            Column {
                Text(
                    S.lyricsFormatHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = SurcoColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SurcoColors.Accent,
                        cursorColor = SurcoColors.Accent,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(S.save, color = SurcoColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Color.White) } },
    )
}
