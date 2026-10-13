package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
) {
    val actions = LocalActions.current
    val state = LocalPlayer.current
    val liked = LocalLiked.current
    val playingHere = state.source == source && state.mediaId != null && tracks.any { it.uri == state.mediaId }
    val multiDisc = numbered && tracks.map { it.discNumber }.distinct().size > 1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(color, SurcoColors.Background)))
                    .statusBarsPadding()
            ) {
                IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
                }
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
                            tint = Color.Black,
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
}

private fun summary(tracks: List<Track>): String {
    val total = tracks.sumOf { it.durationMs }
    val mins = total / 60000
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
            tracks.firstOrNull()?.qualityLabel()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = SurcoColors.Accent, modifier = Modifier.padding(top = 2.dp))
            }
        },
        toolbar = {
            IconButton(onClick = { actions.editAlbum(a.albumKey) }) {
                Icon(Icons.Filled.Edit, S.editAlbum, tint = SurcoColors.TextSecondary)
            }
            IconButton(onClick = { actions.searchCovers(a.albumKey) }) {
                Icon(Icons.Filled.ImageSearch, S.findCover, tint = SurcoColors.TextSecondary)
            }
            IconButton(onClick = { actions.addToPlaylist(tracks) }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, S.addToPlaylist, tint = SurcoColors.TextSecondary)
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
                        modifier = Modifier.padding(16.dp),
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
        header = {
            ArtImage(
                image,
                Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                CircleShape,
                Icons.Filled.Person,
            )
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "${S.albumsCount(albums.size)} • ${S.songs(tracks.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = SurcoColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(S.popular, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        },
        toolbar = {
            TextButton(onClick = { actions.play(tracks, 0, source) }) {
                Text(S.playAll(tracks.size), color = Color.White)
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
            actions.scope.launch { dao.renamePlaylist(id, name.ifBlank { p.name }, System.currentTimeMillis()) }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
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
