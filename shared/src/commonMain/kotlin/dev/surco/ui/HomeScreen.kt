package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.surco.data.AlbumRow
import dev.surco.data.ArtistRow
import dev.surco.data.PlayStat
import dev.surco.data.PlaylistRow
import dev.surco.data.ScanState
import dev.surco.data.Track
import dev.surco.ui.components.AlbumCard
import dev.surco.ui.components.AlbumCardCompact
import dev.surco.ui.components.AppAvatar
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.ArtistCircle
import dev.surco.ui.components.HorizontalSpacing
import dev.surco.ui.components.LikedArt
import dev.surco.ui.components.Mosaic
import dev.surco.ui.components.QuickTile
import dev.surco.ui.components.SectionTitle
import dev.surco.ui.components.SurcoChip
import dev.surco.ui.components.TrackRow
import dev.surco.ui.theme.SurcoColors
import kotlin.random.Random

private val similarCache = HashMap<String, List<String>>()

private enum class HomeFilter {
    ALL, ALBUMS, ARTISTS, SONGS;

    val label get() = when (this) { ALL -> S.all; ALBUMS -> S.albums; ARTISTS -> S.artists; SONGS -> S.songsTab }
}

@Composable
fun HomeScreen() {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(onClick = { Overlays.drawer = true })
            Spacer(Modifier.width(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                items(HomeFilter.entries.toList()) { f ->
                    SurcoChip(f.label, filter == f, onClick = { filter = f })
                }
            }
        }

        val trackCount by remember { dao.trackCount() }.collectAsState(initial = -1)
        val scan by actions.repo.scanner.state.collectAsState()
        if (trackCount == 0) {
            EmptyLibrary(scan)
            return@Column
        }

        when (filter) {
            HomeFilter.ALL -> HomeFeed()
            HomeFilter.ALBUMS -> {
                val albums by remember { dao.albums() }.collectAsState(initial = emptyList())
                AlbumsGrid(albums)
            }
            HomeFilter.ARTISTS -> {
                val artists by remember { dao.artists() }.collectAsState(initial = emptyList())
                ArtistsGrid(artists)
            }
            HomeFilter.SONGS -> {
                val tracks by remember { dao.tracks() }.collectAsState(initial = emptyList())
                SongsList(tracks, source = "biblioteca|${S.allSongs}")
            }
        }
    }
}

@Composable
private fun EmptyLibrary(scan: ScanState) {
    val actions = LocalActions.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (scan) {
            is ScanState.Running -> {
                Text(S.scanningMusic, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                if (scan.total > 0) {
                    LinearProgressIndicator(
                        progress = { scan.done.toFloat() / scan.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = SurcoColors.Accent,
                        trackColor = SurcoColors.SurfaceHigh,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh)
                }
                Spacer(Modifier.height(8.dp))
                Text(scan.current, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
            }
            else -> {
                Text(S.noSongs, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (scan is ScanState.Failed) S.scanError(scan.message)
                    else S.noAudioFound,
                    textAlign = TextAlign.Center,
                    color = SurcoColors.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { actions.openSettings() },
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                ) { Text(S.openSettings) }
            }
        }
    }
}

private data class QuickItem(val key: String, val title: String, val kind: Int, val art: List<String>, val open: () -> Unit)

@Composable
private fun HomeFeed() {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val albums by remember { dao.albums() }.collectAsState(initial = emptyList())
    val albumStats by remember { dao.albumPlayStats() }.collectAsState(initial = emptyList())
    val artistStats by remember { dao.artistPlayStats() }.collectAsState(initial = emptyList())
    val artists by remember { dao.artists() }.collectAsState(initial = emptyList())
    val likedAlbums by remember { dao.albumsWithLiked() }.collectAsState(initial = emptyList())
    val playlists by remember { dao.playlists() }.collectAsState(initial = emptyList())
    val likedUris = LocalLiked.current
    val lastLiked by remember { dao.lastLikedAt() }.collectAsState(initial = null)

    val albumByKey = remember(albums) { albums.associateBy { it.albumKey } }
    val statByKey = remember(albumStats) { albumStats.associateBy { it.key } }

    val recentAlbums = remember(albumStats, albumByKey) {
        albumStats.sortedByDescending { it.lastPlayed }.mapNotNull { albumByKey[it.key] }
    }
    val diveBackIn = remember(albumStats, albumByKey) { diveBackIn(albumStats, albumByKey) }
    val recommended = remember(albums, statByKey) {
        val rnd = Random(dev.surco.util.nowMillis() / 86_400_000L)
        val unplayed = albums.filter { statByKey[it.albumKey] == null }
        (unplayed.ifEmpty { albums }).shuffled(rnd).take(10)
    }
    val recentlyAdded = remember(albums) { albums.sortedByDescending { it.addedAt }.take(12) }
    val artistByName = remember(artists) { artists.associateBy { it.name } }
    val favouriteArtists = remember(artistStats, artistByName) {
        artistStats.mapNotNull { artistByName[it.key] }.take(12)
    }
    val topArtist = favouriteArtists.firstOrNull()

    val settings by actions.repo.prefs.settings.collectAsState()
    val discover by actions.container.discovery.sections.collectAsState()
    LaunchedEffect(settings.discover, artists.size, artistStats.size) {
        if (settings.discover && artists.isNotEmpty()) {
            actions.container.discovery.refreshIfStale()
        }
    }

    val quick = remember(recentAlbums, playlists, likedUris.size, lastLiked) {
        buildQuickItems(actions, likedUris.isNotEmpty(), lastLiked ?: 0L, playlists, recentAlbums, albums)
    }

    val listState = rememberTabListState("home")
    LazyColumn(Modifier.fillMaxSize().testTag("home_list"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        // ---- Accesos rápidos (cuadrícula 2 columnas)
        if (quick.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { q ->
                                QuickTile(q.title, Modifier.weight(1f), onClick = q.open) { m ->
                                    when (q.kind) {
                                        0 -> LikedArt(m, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                        1 -> PlaylistMosaic(q.key.removePrefix("p").toLongOrNull() ?: 0L, m)
                                        else -> ArtImage(q.art.firstOrNull(), m, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (likedAlbums.isNotEmpty()) {
            item { SectionTitle(S.albumsWithLiked) }
            item { AlbumRowList(likedAlbums.take(15)) }
        }

        if (diveBackIn.isNotEmpty()) {
            item { SectionTitle(S.diveBackIn) }
            item { AlbumRowList(diveBackIn) }
        }

        if (recentAlbums.isNotEmpty()) {
            item { SectionTitle(S.recents, action = S.showAll, onAction = { actions.go("recent") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
                    items(recentAlbums.take(15), key = { it.albumKey }) { a ->
                        AlbumCardCompact(a, onClick = { actions.openAlbum(a.albumKey) })
                    }
                }
            }
        }

        // Música nueva para ti (no está en tu biblioteca): «Porque escuchas X».
        if (settings.discover) {
            discover.forEachIndexed { i, section ->
                // Sin clave: si no, la lista se «engancha» a las recomendaciones (que llegan antes que el resto)
                // y Inicio aparecía a media página.
                item(contentType = "discover") { DiscoverSectionView(section) }
            }
        }

        if (recommended.isNotEmpty()) {
            item { SectionTitle(S.pickedForToday) }
            item { AlbumRowList(recommended, caption = null) }
        }

        if (topArtist != null) {
            item { MoreLike(topArtist, artists) }
        }

        if (favouriteArtists.isNotEmpty()) {
            item { SectionTitle(S.favouriteArtists) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
                    items(favouriteArtists, key = { it.name }) { a ->
                        ArtistCircle(a, onClick = { actions.openArtist(a.name) })
                    }
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionTitle(S.recentlyAdded) }
            item { AlbumRowList(recentlyAdded) }
        }
    }
}

@Composable
private fun PlaylistMosaic(id: Long, modifier: Modifier) {
    val dao = LocalActions.current.repo.dao
    val art by remember(id) { dao.playlistArt(id) }.collectAsState(initial = emptyList())
    Mosaic(art, modifier, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
}

private fun diveBackIn(stats: List<PlayStat>, byKey: Map<String, AlbumRow>): List<AlbumRow> {
    val twoDaysAgo = dev.surco.util.nowMillis() - 2L * 24 * 3600 * 1000
    val old = stats.filter { it.lastPlayed < twoDaysAgo }.sortedByDescending { it.plays }
    val pick = old.ifEmpty { stats.filter { it.plays >= 2 }.sortedByDescending { it.plays } }
    return pick.mapNotNull { byKey[it.key] }.take(12)
}

private fun buildQuickItems(
    actions: AppActions,
    hasLiked: Boolean,
    lastLiked: Long,
    playlists: List<PlaylistRow>,
    recentAlbums: List<AlbumRow>,
    allAlbums: List<AlbumRow>,
): List<QuickItem> {
    val out = ArrayList<QuickItem>()
    data class Cand(val time: Long, val item: QuickItem)
    val cands = ArrayList<Cand>()
    if (hasLiked) cands += Cand(lastLiked, QuickItem("liked", S.likedSongs, 0, emptyList()) { actions.openLiked() })
    playlists.take(4).forEach { p ->
        cands += Cand(p.updatedAt, QuickItem("p${p.id}", p.name, 1, emptyList()) { actions.openPlaylist(p.id) })
    }
    // Los álbumes recientes ocupan el resto; usamos un tiempo decreciente para conservar su orden.
    val now = dev.surco.util.nowMillis()
    recentAlbums.take(8).forEachIndexed { i, a ->
        cands += Cand(now - 1000L * (i + 1), QuickItem("a${a.albumKey}", a.album, 2, listOfNotNull(a.artPath)) { actions.openAlbum(a.albumKey) })
    }
    cands.sortedByDescending { it.time }.forEach { if (out.size < 8) out += it.item }
    // Biblioteca recién creada: rellenamos con álbumes cualesquiera.
    if (out.size < 4) {
        allAlbums.filter { a -> out.none { it.key == "a${a.albumKey}" } }.take(8 - out.size).forEach { a ->
            out += QuickItem("a${a.albumKey}", a.album, 2, listOfNotNull(a.artPath)) { actions.openAlbum(a.albumKey) }
        }
    }
    return out
}

@Composable
fun AlbumRowList(albums: List<AlbumRow>, caption: String? = S.album) {
    val actions = LocalActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
        items(albums, key = { it.albumKey }) { a ->
            AlbumCard(a, onClick = { actions.openAlbum(a.albumKey) }, caption = caption)
        }
    }
}

/** "Más como X": artistas similares (según Last.fm) que tienes en tu biblioteca. */
@Composable
private fun MoreLike(artist: ArtistRow, libraryArtists: List<ArtistRow>) {
    val actions = LocalActions.current
    var albums by remember(artist.name) { mutableStateOf<List<AlbumRow>>(emptyList()) }
    var isSimilar by remember(artist.name) { mutableStateOf(false) }

    LaunchedEffect(artist.name, libraryArtists.size) {
        val similar = similarCache[artist.name] ?: actions.repo.lastFm.similarArtists(artist.name).also {
            if (it.isNotEmpty()) similarCache[artist.name] = it
        }
        val libByLower = libraryArtists.associateBy { it.name.lowercase() }
        val present = similar.mapNotNull { libByLower[it.lowercase()]?.name }.filter { it != artist.name }
        if (present.isNotEmpty()) {
            val found = actions.repo.dao.albumsByArtists(present.take(30))
            val order = present.withIndex().associate { it.value to it.index }
            albums = found.sortedBy { order[it.artist] ?: Int.MAX_VALUE }.take(15)
            isSimilar = true
        } else {
            isSimilar = false
            albums = emptyList()
        }
    }

    val show = if (isSimilar) albums else emptyList()
    if (show.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openArtist(artist.name) }
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(artist.imagePath ?: artist.artPath, Modifier.size(56.dp), CircleShape, Icons.Filled.Person)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(S.moreLike, style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary)
            Text(artist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
    AlbumRowList(show)
}

// ---------------------------------------------------------------- Listados completos

@Composable
fun AlbumsGrid(albums: List<AlbumRow>) {
    val actions = LocalActions.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.albumKey }) { a ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AlbumCardFill(a, onClick = { actions.openAlbum(a.albumKey) })
            }
        }
    }
}

@Composable
private fun AlbumCardFill(a: AlbumRow, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ArtImage(a.artPath, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        Text(a.album, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            if (a.year > 0) "${a.year} • ${a.artist}" else a.artist,
            style = MaterialTheme.typography.bodySmall,
            color = SurcoColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun ArtistsGrid(artists: List<ArtistRow>) {
    val actions = LocalActions.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(artists, key = { it.name }) { a ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ArtistCircle(a, onClick = { actions.openArtist(a.name) }, size = 140.dp)
            }
        }
    }
}

@Composable
fun SongsList(tracks: List<Track>, source: String) {
    val actions = LocalActions.current
    val current = LocalPlayer.current.mediaId
    val liked = LocalLiked.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.songs(tracks.size), color = SurcoColors.TextSecondary, modifier = Modifier.weight(1f))
                Button(
                    onClick = { actions.play(tracks, 0, source, shuffle = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                ) {
                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S.shuffle)
                }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.uri }) { i, t ->
            TrackRow(
                t,
                onClick = { actions.play(tracks, i, source) },
                onMore = { actions.menu(t) },
                isCurrent = t.uri == current,
                liked = t.uri in liked,
                onToggleLike = { actions.toggleLike(t.uri, t.uri in liked) },
            )
        }
    }
}
