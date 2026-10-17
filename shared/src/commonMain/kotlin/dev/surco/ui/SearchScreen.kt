package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.surco.data.SearchRecent
import dev.surco.data.SearchResults
import dev.surco.data.Track
import dev.surco.ui.components.AppAvatar
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.rememberArtColor
import dev.surco.ui.components.LibraryRow
import dev.surco.ui.components.SectionTitle
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen() {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = SearchResults()
        } else {
            delay(180)
            results = actions.repo.search(query)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Cabecera: como en «Tu biblioteca», con el título y el campo debajo
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(onClick = { Overlays.drawer = true })
            Spacer(Modifier.width(14.dp))
            Text(S.search, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.openSoulseek() }) {
                Icon(Icons.Filled.CloudDownload, S.downloadWithSoulseek, tint = SurcoColors.TextPrimary, modifier = Modifier.size(26.dp))
            }
        }
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { runCatching { focus.requestFocus() }; keyboard?.show() }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color(0xFF121212), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(S.whatToListen, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF535353), maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF121212)),
                    cursorBrush = SolidColor(Color(0xFF121212)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, S.clear, tint = Color(0xFF121212)) }
            }
        }

        // Un enlace de Spotify pegado en el buscador abre ese álbum o esa canción.
        val link = remember(query) { dev.surco.net.SpotifyPage.findLink(query) }
        LaunchedEffect(link) {
            if (link != null) {
                query = ""
                actions.openLink(link)
            }
        }
        if (query.isBlank() || link != null) SearchHome() else SearchResultsList(query, results)
    }
}

@Composable
private fun SearchHome() {
    val actions = LocalActions.current
    val recents by remember { actions.repo.dao.searchRecents() }.collectAsState(initial = emptyList())
    val genres by remember { actions.repo.dao.genres() }.collectAsState(initial = emptyList())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (recents.isNotEmpty()) {
            item { SectionTitle(S.recents) }
            items(recents, key = { it.key }) { r -> RecentRow(r) }
        }
        if (genres.isNotEmpty()) {
            item { SectionTitle(S.browseGenres) }
            items(genres.chunked(2)) { pair ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    pair.forEachIndexed { i, g ->
                        if (i == 1) Spacer(Modifier.width(12.dp))
                        GenreTile(g.genre, g.artPath, Modifier.weight(1f)) { actions.openGenre(g.genre) }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.width(12.dp))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: SearchRecent) {
    val actions = LocalActions.current
    val subtitle = when (r.type) {
        "artist" -> S.artist
        "album" -> S.albumBy(r.subtitle)
        else -> S.songBy(r.subtitle)
    }
    LibraryRow(
        title = r.title,
        subtitle = subtitle,
        circle = r.type == "artist",
        onClick = { openRecent(actions, r) },
        trailing = {
            IconButton(onClick = { actions.scope.launch { actions.repo.dao.deleteSearchRecent(r.key) } }) {
                Icon(Icons.Filled.Close, S.remove, tint = SurcoColors.TextSecondary)
            }
        },
    ) { m ->
        ArtImage(
            r.artPath, m,
            if (r.type == "artist") CircleShape else RoundedCornerShape(4.dp),
            if (r.type == "artist") Icons.Filled.Person else Icons.Filled.MusicNote,
        )
    }
}

private fun openRecent(actions: AppActions, r: SearchRecent) {
    actions.scope.launch { actions.repo.dao.upsertSearchRecent(r.copy(at = dev.surco.util.nowMillis())) }
    when (r.type) {
        "artist" -> actions.openArtist(r.ref)
        "album" -> actions.openAlbum(r.ref)
        else -> actions.scope.launch {
            actions.repo.dao.track(r.ref)?.let { playTrackInAlbum(actions, it) }
        }
    }
}

/** Reproduce una canción y sigue con el resto de su álbum. */
suspend fun playTrackInAlbum(actions: AppActions, t: Track) {
    val album = actions.repo.dao.albumTracksOnce(t.albumKey)
    val idx = album.indexOfFirst { it.uri == t.uri }
    if (idx >= 0) actions.play(album, idx, "álbum|${t.album}") else actions.play(listOf(t), 0, "búsqueda|${t.title}")
}

@Composable
private fun SearchResultsList(query: String, results: SearchResults) {
    val actions = LocalActions.current
    val liked = LocalLiked.current
    val empty = results.artists.isEmpty() && results.albums.isEmpty() && results.tracks.isEmpty()
    val shownLocal = results.artists.map { it.name.lowercase() }.toSet()
    val online = rememberOnlineArtists(query).filter { it.name.lowercase() !in shownLocal }
    val libraryArtists by remember { actions.repo.dao.artists() }.collectAsState(initial = emptyList())
    val localArtists = remember(libraryArtists) { libraryArtists.associateBy { it.name.lowercase() } }

    LazyColumn(Modifier.fillMaxSize().testTag("search_results"), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (empty) {
            item {
                Text(
                    S.nothingInLibrary(query.trim()),
                    color = SurcoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(results.artists, key = { "ar:" + it.name }) { a ->
            LibraryRow(
                title = a.name,
                subtitle = S.artist,
                circle = true,
                onClick = {
                    actions.scope.launch { actions.repo.rememberSearch("artist", a.name, a.name, "", a.imagePath ?: a.artPath) }
                    actions.openArtist(a.name)
                },
            ) { m -> ArtImage(a.imagePath ?: a.artPath, m, CircleShape, Icons.Filled.Person) }
        }
        // Artistas de internet que puedes explorar (aunque no tengas nada suyo).
        if (online.isNotEmpty()) {
            item(key = "online_title") { SectionTitle(S.artistsToExplore) }
            items(online, key = { "on:" + it.id }) { a ->
                OnlineArtistRow(a, localArtists[a.name.lowercase()])
            }
        }
        item(key = "slsk") {
            LibraryRow(
                title = S.searchOnSoulseekFor(query.trim()),
                subtitle = S.downloadToFolder,
                artSize = 52.dp,
                onClick = { actions.openSoulseek(query.trim()) },
            ) { m ->
                Box(m.background(SurcoColors.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CloudDownload, null, tint = SurcoColors.OnAccent, modifier = Modifier.size(26.dp))
                }
            }
        }
        items(results.albums, key = { "al:" + it.albumKey }) { a ->
            LibraryRow(
                title = a.album,
                subtitle = S.albumBy(a.artist),
                onClick = {
                    actions.scope.launch { actions.repo.rememberSearch("album", a.albumKey, a.album, a.artist, a.artPath) }
                    actions.openAlbum(a.albumKey)
                },
            ) { m -> ArtImage(a.artPath, m) }
        }
        items(results.tracks, key = { "tr:" + it.uri }) { t ->
            val isLiked = t.uri in liked
            LibraryRow(
                title = t.title,
                subtitle = S.songBy(t.artist),
                artSize = 52.dp,
                onClick = {
                    actions.scope.launch {
                        actions.repo.rememberSearch("track", t.uri, t.title, t.artist, t.artPath)
                        playTrackInAlbum(actions, t)
                    }
                },
                trailing = {
                    IconButton(onClick = { actions.toggleLike(t.uri, isLiked) }) {
                        if (isLiked) Icon(Icons.Filled.CheckCircle, null, tint = SurcoColors.Accent)
                        else Icon(Icons.Outlined.AddCircleOutline, null, tint = SurcoColors.TextSecondary)
                    }
                },
            ) { m -> ArtImage(t.artPath, m) }
        }
    }
}

private val genrePalette = listOf(
    Color(0xFFE13300), Color(0xFF1E3264), Color(0xFF8D67AB), Color(0xFF148A08), Color(0xFFBA5D07),
    Color(0xFF27856A), Color(0xFFE8115B), Color(0xFF503750), Color(0xFF0D73EC), Color(0xFFAF2896),
)

@Composable
private fun GenreTile(name: String, art: String?, modifier: Modifier, onClick: () -> Unit) {
    // El color sale de la portada (como el reproductor); si no hay, de una paleta fija por nombre.
    val color = rememberArtColor(art, genrePalette[(name.lowercase().hashCode() and 0x7fffffff) % genrePalette.size])
    Box(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp), maxLines = 2)
        ArtImage(
            art,
            Modifier
                .size(64.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 4.dp)
                .rotate(25f),
            RoundedCornerShape(4.dp),
        )
    }
}
