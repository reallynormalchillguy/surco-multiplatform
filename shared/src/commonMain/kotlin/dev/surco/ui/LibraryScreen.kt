package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.ui.components.AppAvatar
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.LibraryRow
import dev.surco.ui.components.LikedArt
import dev.surco.ui.components.Mosaic
import dev.surco.ui.components.SurcoChip
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

private enum class LibKind { LIKED, PLAYLIST, ALBUM, ARTIST }

private data class LibItem(
    val key: String,
    val kind: LibKind,
    val title: String,
    val subtitle: String,
    val art: String?,
    val time: Long,
    val playlistId: Long = 0,
    val ref: String = "",
)

@Composable
fun LibraryScreen() {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val settings by actions.repo.prefs.settings.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val playlists by remember { dao.playlists() }.collectAsState(initial = emptyList())
    val albums by remember { dao.albums() }.collectAsState(initial = emptyList())
    val artists by remember { dao.artists() }.collectAsState(initial = emptyList())
    val albumStats by remember { dao.albumPlayStats() }.collectAsState(initial = emptyList())
    val artistStats by remember { dao.artistPlayStats() }.collectAsState(initial = emptyList())
    val likedCount = LocalLiked.current.size
    val lastLiked by remember { dao.lastLikedAt() }.collectAsState(initial = null)

    val entries = remember(playlists, albums, artists, albumStats, artistStats, likedCount, lastLiked, filter, settings.librarySortRecent) {
        val albumTime = albumStats.associate { it.key to it.lastPlayed }
        val artistTime = artistStats.associate { it.key to it.lastPlayed }
        val list = ArrayList<LibItem>()
        if (filter == null || filter == "playlists") {
            list += playlists.map {
                LibItem("p${it.id}", LibKind.PLAYLIST, it.name, S.playlistOf(it.trackCount), null, it.updatedAt, playlistId = it.id)
            }
        }
        if (filter == null || filter == "albums") {
            list += albums.map {
                LibItem("a${it.albumKey}", LibKind.ALBUM, it.album, S.albumBy(it.artist), it.artPath, albumTime[it.albumKey] ?: it.addedAt, ref = it.albumKey)
            }
        }
        if (filter == null || filter == "artists") {
            list += artists.map {
                LibItem("r${it.name}", LibKind.ARTIST, it.name, S.artist, it.imagePath ?: it.artPath, artistTime[it.name] ?: 0L, ref = it.name)
            }
        }
        val sorted = if (settings.librarySortRecent) list.sortedByDescending { it.time } else list.sortedBy { it.title.lowercase() }
        val liked = if ((filter == null || filter == "playlists") && likedCount > 0) {
            listOf(LibItem("liked", LibKind.LIKED, S.likedSongs, S.playlistOf(likedCount), null, lastLiked ?: 0L))
        } else emptyList()
        liked + sorted
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Cabecera
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(onClick = { Overlays.drawer = true })
            Spacer(Modifier.width(14.dp))
            Text(S.yourLibrary, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.openTab("search") }) {
                Icon(Icons.Filled.Search, S.search, tint = SurcoColors.TextPrimary, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, S.createPlaylist, tint = SurcoColors.TextPrimary, modifier = Modifier.size(30.dp))
            }
        }

        // ---- Filtros
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("playlists" to S.playlists, "albums" to S.albums, "artists" to S.artists)) { (f, label) ->
                SurcoChip(label, filter == f, onClick = { filter = if (filter == f) null else f })
            }
        }

        // ---- Orden y vista
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable { actions.repo.prefs.update { it.copy(librarySortRecent = !it.librarySortRecent) } }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SwapVert, null, tint = SurcoColors.TextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (settings.librarySortRecent) S.recents else S.alphabetical, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { actions.repo.prefs.update { it.copy(libraryGrid = !it.libraryGrid) } }) {
                Icon(
                    if (settings.libraryGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    S.changeView,
                    tint = SurcoColors.TextPrimary,
                )
            }
        }

        val gridState = rememberTabGridState("library")
        val listState = rememberTabListState("library")
        if (settings.libraryGrid) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.key }) { item -> LibGridCell(item) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries, key = { it.key }) { item ->
                    LibraryRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        circle = item.kind == LibKind.ARTIST,
                        pinned = item.kind == LibKind.LIKED,
                        onClick = { open(actions, item) },
                        onLongClick = if (item.kind == LibKind.ALBUM) ({ actions.albumMenu(item.ref) }) else null,
                    ) { m -> LibArt(item, m) }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = S.newPlaylist,
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                actions.scope.launch {
                    val id = actions.repo.createPlaylist(name)
                    actions.openPlaylist(id)
                }
            },
        )
    }
}

private fun open(actions: AppActions, item: LibItem) {
    when (item.kind) {
        LibKind.LIKED -> actions.openLiked()
        LibKind.PLAYLIST -> actions.openPlaylist(item.playlistId)
        LibKind.ALBUM -> actions.openAlbum(item.ref)
        LibKind.ARTIST -> actions.openArtist(item.ref)
    }
}

@Composable
private fun LibArt(item: LibItem, modifier: Modifier) {
    when (item.kind) {
        LibKind.LIKED -> LikedArt(modifier)
        LibKind.PLAYLIST -> {
            val dao = LocalActions.current.repo.dao
            val flow = remember(item.playlistId) { dao.playlistArt(item.playlistId) }
            val art by flow.collectAsState(initial = emptyList())
            Mosaic(art, modifier)
        }
        LibKind.ARTIST -> ArtImage(item.art, modifier, CircleShape, Icons.Filled.Person)
        LibKind.ALBUM -> ArtImage(item.art, modifier)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibGridCell(item: LibItem) {
    val actions = LocalActions.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { open(actions, item) },
            onLongClick = if (item.kind == LibKind.ALBUM) ({ actions.albumMenu(item.ref) }) else null,
        )
    ) {
        LibArt(item, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
