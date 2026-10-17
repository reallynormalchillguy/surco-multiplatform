package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.AlbumRow
import dev.surco.data.Track
import dev.surco.data.formatDuration
import dev.surco.data.qualityLabel
import dev.surco.ui.components.AlbumCard
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.HorizontalSpacing
import dev.surco.ui.components.LikedArt
import dev.surco.ui.components.Mosaic
import dev.surco.ui.components.SectionTitle
import dev.surco.ui.components.TrackRow
import dev.surco.ui.components.rememberArtColor
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

// ================================================================ Plantilla común

@Composable
fun CollectionScreen(
    tracks: List<Track>,
    source: String,
    color: Color,
    header: @Composable ColumnScope.() -> Unit,
    toolbar: @Composable RowScope.() -> Unit = {},
    numbered: Boolean = false,
    showArt: Boolean = true,
    playlistId: Long? = null,
    footer: LazyListScope.() -> Unit = {},
    /** Título de la barra de arriba, que aparece al bajar (como en Spotify). */
    title: String = "",
    /** Cabecera a pantalla completa con efecto parallax (página de artista); recibe cuánto se ha bajado. */
    hero: (@Composable (scrolled: () -> Float) -> Unit)? = null,
) {
    val actions = LocalActions.current
    val state = LocalPlayer.current
    val liked = LocalLiked.current
    val playingHere = state.source == source && state.mediaId != null && tracks.any { it.uri == state.mediaId }
    val multiDisc = numbered && tracks.map { it.discNumber }.distinct().size > 1

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        if (hero != null) {
            item(key = "hero") {
                hero { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100_000f }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (hero == null) Modifier.background(Brush.verticalGradient(listOf(color, SurcoColors.Background))).statusBarsPadding()
                        else Modifier
                    )
            ) {
                // Sitio para la barra de arriba (que va por encima y no se mueve).
                if (hero == null) Spacer(Modifier.height(56.dp))
                Column(Modifier.padding(horizontal = 16.dp)) { header() }
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    toolbar()
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { actions.play(tracks, 0, source, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Shuffle, S.shuffle,
                            tint = if (playingHere && state.shuffle) SurcoColors.Accent else SurcoColors.TextSecondary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurcoColors.Accent)
                            .clickable(enabled = tracks.isNotEmpty()) {
                                if (playingHere) actions.player.togglePlay() else actions.play(tracks, 0, source)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playingHere && state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            S.play,
                            tint = SurcoColors.OnAccent,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        var lastDisc = -1
        tracks.forEachIndexed { i, t ->
            if (multiDisc && t.discNumber != lastDisc) {
                lastDisc = t.discNumber
                item(key = "disc${t.discNumber}") {
                    Text(
                        "${S.disc} ${t.discNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        color = SurcoColors.TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            item(key = t.uri) {
                TrackRow(
                    t,
                    onClick = { actions.play(tracks, i, source) },
                    onMore = { actions.menu(t, playlistId) },
                    isCurrent = t.uri == state.mediaId,
                    number = if (numbered) (if (t.trackNumber > 0) t.trackNumber else i + 1) else null,
                    showArt = showArt,
                    liked = t.uri in liked,
                )
            }
        }
        footer()
    }
        CollapsingTopBar(listState, color, title, heroMode = hero != null)
    }
}

/**
 * Barra de arriba con «atrás»: transparente al principio y, según bajas, se rellena del color de la
 * pantalla y aparece el título (como en Spotify).
 */
@Composable
internal fun CollapsingTopBar(listState: androidx.compose.foundation.lazy.LazyListState, color: Color, title: String, heroMode: Boolean) {
    val actions = LocalActions.current
    val fraction by remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            when {
                first == null -> 0f
                first.index > 0 -> 1f
                else -> ((-first.offset).toFloat() / (first.size * (if (heroMode) 0.72f else 0.6f)).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }
    val bar = androidx.compose.ui.graphics.lerp(color, SurcoColors.Background, 0.45f)
    Row(
        Modifier.fillMaxWidth().background(bar.copy(alpha = fraction)).statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(start = 4.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = if (heroMode) 0.35f * (1f - fraction) else 0f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 16.dp).graphicsLayer {
                val t = ((fraction - 0.65f) / 0.35f).coerceIn(0f, 1f)
                alpha = t; translationY = (1f - t) * 12.dp.toPx()
            },
        )
    }
}

private fun summary(tracks: List<Track>): String {
    val total = tracks.sumOf { it.durationMs }
    // Redondeando (un álbum de 40 s no es «0 min») y sin minutos si aún no se sabe la duración.
    if (total <= 0) return S.songs(tracks.size)
    val mins = ((total + 30_000) / 60_000).coerceAtLeast(1)
    return "${S.songs(tracks.size)} • ${S.duration(mins)}"
}

// ================================================================ Álbum

@Composable
fun AlbumScreen(albumKey: String) {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val albumFlow = remember(albumKey) { dao.album(albumKey) }
    val tracksFlow = remember(albumKey) { dao.albumTracks(albumKey) }
    val album by albumFlow.collectAsState(initial = null)
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val a = album
    val color = rememberArtColor(a?.artPath)
    // Si el álbum desaparece estando abierto (p. ej. se acaba de borrar), volvemos atrás.
    var loaded by remember(albumKey) { mutableStateOf(false) }
    LaunchedEffect(a == null) {
        if (a != null) loaded = true
        else if (loaded) actions.nav.popBackStack()
    }

    if (a == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Text(S.loading, color = SurcoColors.TextSecondary)
        }
        return
    }
    val artistImage by remember(a.artist) { dao.artist(a.artist) }.collectAsState(initial = null)
    val source = "álbum|${a.album}"

    CollectionScreen(
        tracks = tracks,
        source = source,
        color = color,
        title = a?.album.orEmpty(),
        numbered = true,
        showArt = false,
        header = {
            ArtImage(
                a.artPath,
                Modifier.padding(horizontal = 40.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally),
                RoundedCornerShape(6.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(a.album, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clickable { actions.openArtist(a.artist) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtImage(artistImage?.imagePath ?: a.artPath, Modifier.size(24.dp), CircleShape, Icons.Filled.Person)
                Spacer(Modifier.width(8.dp))
                Text(a.artist, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(S.album, a.year.takeIf { it > 0 }?.toString(), summary(tracks)).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = SurcoColors.TextSecondary,
            )
        },
        toolbar = {
            IconButton(onClick = { actions.editAlbum(a.albumKey) }) {
                Icon(Icons.Filled.Edit, S.editAlbum, tint = SurcoColors.TextSecondary)
            }
            IconButton(
                onClick = { actions.player.enqueue(tracks); actions.toast(S.albumAddedToQueue(tracks.size)) },
                enabled = tracks.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, S.addAlbumToQueue, tint = SurcoColors.TextSecondary)
            }
            IconButton(onClick = { actions.albumMenu(a.albumKey) }, modifier = Modifier.testTag("album_more")) {
                Icon(Icons.Filled.MoreVert, S.moreOptions, tint = SurcoColors.TextSecondary)
            }
        },
        footer = {
            val genres = tracks.map { it.genre }.filter { it.isNotBlank() }.distinct()
            if (genres.isNotEmpty()) {
                item {
                    Text(
                        genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SurcoColors.TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
            }
            // El formato del archivo, sólo abajo del todo.
            tracks.firstOrNull()?.qualityLabel()?.takeIf { it.isNotBlank() }?.let { q ->
                item(key = "quality") {
                    Text(
                        q, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("album_quality"),
                    )
                }
            }
        },
    )
}

// ================================================================ Artista

@Composable
fun ArtistScreen(name: String) {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val artist by remember(name) { dao.artist(name) }.collectAsState(initial = null)
    val tracks by remember(name) { dao.artistTracks(name) }.collectAsState(initial = emptyList())
    val albums by remember(name) { dao.albumsByArtist(name) }.collectAsState(initial = emptyList())
    val top by remember { dao.topTracks(2000) }.collectAsState(initial = emptyList())
    val image = artist?.imagePath ?: artist?.artPath ?: tracks.firstOrNull { it.artPath != null }?.artPath
    val color = rememberArtColor(image)

    val popular = remember(tracks, top) {
        val byUri = tracks.associateBy { it.uri }
        val fromStats = top.mapNotNull { byUri[it.key] }
        (fromStats + tracks).distinctBy { it.uri }.take(5)
    }
    val source = "artista|$name"

    CollectionScreen(
        tracks = popular,
        source = source,
        color = color,
        title = name,
        hero = { scrolled ->
            // Foto a todo lo ancho que se queda atrás (parallax) y se apaga al bajar; el nombre, encima.
            Box(
                Modifier.fillMaxWidth().height(360.dp).clipToBounds()
                    .clickable { actions.artistPicture(name) }.testTag("artist_photo"),
            ) {
                Box(
                    Modifier.matchParentSize().graphicsLayer {
                        val s = scrolled()
                        translationY = s * 0.5f
                        alpha = (1f - s / size.height).coerceIn(0f, 1f)
                        val zoom = 1f + (1f - (s / size.height).coerceIn(0f, 1f)) * 0.04f
                        scaleX = zoom; scaleY = zoom
                    },
                ) {
                    ArtImage(image, Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape, Icons.Filled.Person)
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent, 0.75f to SurcoColors.Background.copy(alpha = 0.55f), 1f to SurcoColors.Background),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp)
                        .graphicsLayer { alpha = (1f - scrolled() / (size.height * 2.2f)).coerceIn(0f, 1f) },
                ) {
                    Text(name, style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${S.albumsCount(albums.size)} • ${S.songs(tracks.size)}",
                        style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PhotoCamera, S.changeArtistPhoto, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        },
        header = {
            Text(S.popular, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        },
        toolbar = {
            TextButton(onClick = { actions.play(tracks, 0, source) }) {
                Text(S.playAll(tracks.size), color = SurcoColors.TextPrimary)
            }
            IconButton(onClick = { actions.openSoulseek(name) }, modifier = Modifier.testTag("artist_soulseek")) {
                Icon(Icons.Filled.CloudDownload, S.searchArtistOnSoulseek, tint = SurcoColors.TextSecondary)
            }
        },
        footer = {
            if (albums.isNotEmpty()) {
                item { SectionTitle(S.discography) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
                        items(albums, key = { it.albumKey }) { a ->
                            AlbumCard(a, onClick = { actions.openAlbum(a.albumKey) }, caption = if (a.year > 0) a.year.toString() else S.album)
                        }
                    }
                }
            }
            val appearsOn = tracks.filter { it.groupArtist != name }
            if (appearsOn.isNotEmpty()) {
                item { SectionTitle(S.appearsOn) }
                itemsIndexed(appearsOn, key = { _, t -> "ap" + t.uri }) { i, t ->
                    TrackRow(t, onClick = { actions.play(appearsOn, i, source) }, onMore = { actions.menu(t) })
                }
            }
            // La biografía, al final (después de la discografía), como en Spotify.
            item(key = "bio") { ArtistBioSection(name) }
        },
    )
}

// ================================================================ Listas

@Composable
fun PlaylistScreen(id: Long) {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val playlist by remember(id) { dao.playlist(id) }.collectAsState(initial = null)
    val tracks by remember(id) { dao.playlistTracks(id) }.collectAsState(initial = emptyList())
    val art by remember(id) { dao.playlistArt(id) }.collectAsState(initial = emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val p = playlist ?: return
    val color = rememberArtColor(art.firstOrNull())

    CollectionScreen(
        tracks = tracks,
        source = "lista|${p.name}",
        color = color,
        title = p.name,
        playlistId = id,
        header = {
            Mosaic(art, Modifier.size(220.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Text(p.name, style = MaterialTheme.typography.headlineMedium)
            Text(summary(tracks), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        },
        toolbar = {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, S.rename, tint = SurcoColors.TextSecondary) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, S.deletePlaylist, tint = SurcoColors.TextSecondary) }
        },
        footer = {
            if (tracks.isEmpty()) {
                item {
                    Text(
                        S.emptyPlaylist,
                        color = SurcoColors.TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        },
    )

    if (renaming) {
        NameDialog(S.renamePlaylist, p.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            actions.scope.launch { dao.renamePlaylist(id, name.ifBlank { p.name }, dev.surco.util.nowMillis()) }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = SurcoColors.Surface,
            title = { Text(S.deleteQuestion(p.name)) },
            text = { Text(S.songsStayOnDevice) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    actions.scope.launch { actions.repo.deletePlaylist(id) }
                    actions.nav.popBackStack()
                }) { Text(S.delete, color = SurcoColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
fun LikedScreen() {
    val dao = LocalActions.current.repo.dao
    val tracks by remember { dao.likedTracks() }.collectAsState(initial = emptyList())
    CollectionScreen(
        tracks = tracks,
        source = "favoritas|${S.likedSongs}",
        color = Color(0xFF4A2FBF),
        title = S.likedSongs,
        header = {
            LikedArt(Modifier.size(200.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Text(S.likedSongs, style = MaterialTheme.typography.headlineMedium)
            Text(S.songs(tracks.size), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        },
    )
}

@Composable
fun GenreScreen(genre: String) {
    val dao = LocalActions.current.repo.dao
    val tracks by remember(genre) { dao.genreTracks(genre) }.collectAsState(initial = emptyList())
    val color = rememberArtColor(tracks.firstOrNull { it.artPath != null }?.artPath)
    CollectionScreen(
        tracks = tracks,
        source = "género|$genre",
        color = color,
        title = genre,
        header = {
            Text(genre, style = MaterialTheme.typography.headlineLarge)
            Text(summary(tracks), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        },
    )
}

/** "Mostrar todos" de Recientes: álbumes escuchados, del más reciente al más antiguo. */
@Composable
fun RecentScreen() {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val stats by remember { dao.albumPlayStats() }.collectAsState(initial = emptyList())
    val albums by remember { dao.albums() }.collectAsState(initial = emptyList())
    val list: List<AlbumRow> = remember(stats, albums) {
        val byKey = albums.associateBy { it.albumKey }
        stats.sortedByDescending { it.lastPlayed }.mapNotNull { byKey[it.key] }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Text(S.recents, style = MaterialTheme.typography.headlineSmall)
        }
        if (list.isEmpty()) {
            Text(S.nothingPlayedYet, color = SurcoColors.TextSecondary, modifier = Modifier.padding(16.dp))
        } else {
            AlbumsGrid(list)
        }
    }
}


// ================================================================ Biografía del artista

/**
 * «Acerca de»: biografía de Wikipedia o Last.fm, plegada a unas líneas (se despliega al tocarla).
 * Se puede editar, escribir desde cero, volver a buscar en internet o quitar.
 */
@Composable
private fun ArtistBioSection(name: String) {
    val actions = LocalActions.current
    val bios = actions.container.bios
    val version by bios.version.collectAsState()
    val lang = dev.surco.i18n.I18n.lang
    var loaded by remember(name) { mutableStateOf(false) }
    val bio by produceState(bios.cached(name), name, version, lang) {
        value = bios.cached(name) ?: runCatching { bios.get(name) }.getOrNull() ?: bios.cached(name)
        loaded = true
    }
    var expanded by remember(name) { mutableStateOf(false) }
    var editing by remember(name) { mutableStateOf(false) }
    var menu by remember(name) { mutableStateOf(false) }
    var searching by remember(name) { mutableStateOf(false) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val b = bio?.takeIf { it.text.isNotBlank() }

    fun searchOnline() {
        searching = true
        actions.scope.launch {
            val ok = bios.refresh(name)
            searching = false
            when {
                !ok -> actions.toast(S.noConnection)
                bios.cached(name)?.text.isNullOrBlank() -> actions.toast(S.noBioFound)
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("artist_bio")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(S.aboutArtist, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (searching) CircularProgressIndicator(Modifier.size(20.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("bio_menu")) {
                    Icon(Icons.Filled.MoreVert, S.bioOptions, tint = SurcoColors.TextSecondary)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menu, onDismissRequest = { menu = false },
                    containerColor = SurcoColors.SurfaceHigh,
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (b != null) S.editBio else S.writeBio) },
                        onClick = { menu = false; editing = true },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(S.searchBioOnline) },
                        onClick = { menu = false; searchOnline() },
                    )
                    if (b != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(S.deleteBio, color = SurcoColors.Error) },
                            onClick = { menu = false; actions.scope.launch { bios.setManual(name, "") } },
                        )
                    }
                }
            }
        }
        if (b != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurcoColors.Surface)
                    .clickable { expanded = !expanded }.padding(16.dp),
            ) {
                Text(
                    b.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (expanded) S.showLess else S.showMore,
                        style = MaterialTheme.typography.labelLarge, color = SurcoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (b.source == dev.surco.data.ArtistBios.MANUAL) S.bioWrittenByYou else S.bioSource(b.source, b.lang),
                        style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextTertiary,
                        modifier = Modifier.clickable(enabled = b.url != null) { b.url?.let { runCatching { uri.openUri(it) } } },
                    )
                }
            }
        } else if (loaded) {
            Text(S.noBioYet, style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { editing = true }) { Text(S.writeBio) }
                androidx.compose.material3.OutlinedButton(onClick = { searchOnline() }, enabled = !searching) { Text(S.searchBioOnline) }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(b?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = SurcoColors.Surface,
            title = { Text(S.bioOf(name)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text(S.bioPlaceholder, color = SurcoColors.TextTertiary) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).testTag("bio_editor"),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SurcoColors.Accent, cursorColor = SurcoColors.Accent,
                        unfocusedBorderColor = SurcoColors.SurfaceHigher,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    actions.scope.launch { bios.setManual(name, text) }
                }) { Text(S.save, color = SurcoColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(S.cancel) } },
        )
    }
}
