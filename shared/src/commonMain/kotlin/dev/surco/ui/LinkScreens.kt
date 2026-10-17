package dev.surco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.surco.data.AlbumMatcher
import dev.surco.data.AlbumRow
import dev.surco.data.Track
import dev.surco.data.UNKNOWN_ARTIST
import dev.surco.i18n.S
import dev.surco.net.LinkKind
import dev.surco.net.LinkedMusic
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

// ================================================================ Abrir un enlace de Spotify

/** Lo que se encontró en la biblioteca para un enlace. */
private sealed interface LocalMatch {
    data class Album(val key: String) : LocalMatch
    data class Song(val track: Track, val albumTracks: List<Track>) : LocalMatch
    data class Artist(val name: String) : LocalMatch
}

/**
 * Abre un enlace de Spotify (compartido con Surco o pegado en el buscador): si el álbum, la canción o el
 * artista está en tu biblioteca, va directo; si no, enseña qué es, con su ficha y «Buscar en Soulseek».
 */
@Composable
fun SpotifyLinkScreen(url: String) {
    val actions = LocalActions.current
    var info by remember(url) { mutableStateOf<LinkedMusic?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var deezerAlbum by remember(url) { mutableStateOf<Long?>(null) }
    var albumOfSong by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        val m = actions.container.spotify.resolve(url)
        if (m == null) { failed = true; return@LaunchedEffect }
        info = m
        when (val local = findInLibrary(actions, m)) {
            is LocalMatch.Album -> {
                actions.nav.popBackStack()
                actions.openAlbum(local.key)
                actions.toast(S.inYourLibrary)
                return@LaunchedEffect
            }
            is LocalMatch.Song -> {
                // La canción concreta: suena ya, dentro de su álbum, y se abre el reproductor.
                actions.nav.popBackStack()
                actions.openAlbum(local.track.albumKey)
                val i = local.albumTracks.indexOfFirst { it.uri == local.track.uri }.coerceAtLeast(0)
                actions.play(local.albumTracks, i, "álbum|${local.track.album}")
                actions.openPlayer()
                return@LaunchedEffect
            }
            is LocalMatch.Artist -> {
                actions.nav.popBackStack()
                actions.openArtist(local.name)
                return@LaunchedEffect
            }
            null -> {}
        }
        // No lo tienes: ficha del álbum en Deezer (con la canción marcada si era una canción).
        val deezer = actions.container.deezer
        when (m.kind) {
            LinkKind.ALBUM -> deezerAlbum = runCatching { deezer.findAlbum(m.artist, m.title) }.getOrNull()?.id
            LinkKind.TRACK -> runCatching { deezer.findTrack(m.artist, m.title) }.getOrNull()?.let {
                deezerAlbum = it.first; albumOfSong = it.third
            }
            LinkKind.ARTIST -> {}
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Text(S.spotifyLink, style = MaterialTheme.typography.titleLarge)
        }
        val m = info
        when {
            failed -> Text(S.couldNotReadLink, color = SurcoColors.TextSecondary, modifier = Modifier.padding(24.dp))
            m == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SurcoColors.Accent)
                    Spacer(Modifier.height(12.dp))
                    Text(S.readingLink, color = SurcoColors.TextSecondary)
                }
            }
            else -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp).testTag("link_result"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    m.coverUrl, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(SurcoColors.SurfaceHigh),
                )
                Spacer(Modifier.height(18.dp))
                Text(m.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (m.artist.isNotBlank() && m.kind != LinkKind.ARTIST) {
                    Text(m.artist, style = MaterialTheme.typography.titleMedium, color = SurcoColors.TextSecondary, textAlign = TextAlign.Center)
                }
                Text(
                    listOfNotNull(S.linkKind(m.kind.name), albumOfSong?.let { S.fromAlbum(it) }).joinToString(" · ") + " · " + S.notInLibrary,
                    style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
                val query = when (m.kind) {
                    LinkKind.ALBUM -> listOf(m.artist, m.title)
                    LinkKind.TRACK -> listOf(m.artist, albumOfSong ?: m.title)
                    LinkKind.ARTIST -> listOf(m.title)
                }.filter { it.isNotBlank() }.joinToString(" ")
                Button(
                    onClick = { actions.openSoulseek(query) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(S.searchOnSoulseekFor(query), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                deezerAlbum?.let { id ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { actions.openDiscover(id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(S.seeAlbumDetails)
                    }
                }
            }
        }
    }
}

/** ¿Está en la biblioteca? Se compara por nombre (sin acentos, mayúsculas ni «(Remastered)»). */
private suspend fun findInLibrary(actions: AppActions, m: LinkedMusic): LocalMatch? {
    val dao = actions.repo.dao
    fun sameArtist(a: String) = m.artist.isBlank() || AlbumMatcher.similarity(a, m.artist) >= 0.8f
    // «OK Computer OKNOTOK 1997 2017 - Remastered» → «OK Computer OKNOTOK 1997 2017»: así lo encuentra la búsqueda.
    val core = m.title.substringBefore(" - ").replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifBlank { m.title }
    return when (m.kind) {
        LinkKind.ALBUM -> {
            val found = actions.repo.search(core).albums
            val best = found.filter { sameArtist(it.artist) }
                .maxByOrNull { AlbumMatcher.similarity(it.album, m.title) }
                ?.takeIf { AlbumMatcher.similarity(it.album, m.title) >= 0.85f }
            best?.let { LocalMatch.Album(it.albumKey) }
        }
        LinkKind.TRACK -> {
            val t = actions.repo.search(core).tracks
                .filter { sameArtist(it.artist) || sameArtist(it.groupArtist) }
                .maxByOrNull { AlbumMatcher.similarity(it.title, m.title) }
                ?.takeIf { AlbumMatcher.similarity(it.title, m.title) >= 0.85f }
            t?.let { LocalMatch.Song(it, dao.albumTracksOnce(it.albumKey)) }
        }
        LinkKind.ARTIST -> actions.repo.search(m.title).artists
            .firstOrNull { AlbumMatcher.norm(it.name) == AlbumMatcher.norm(m.title) && it.name != UNKNOWN_ARTIST }
            ?.let { LocalMatch.Artist(it.name) }
    }
}

// ================================================================ Compartir

/**
 * Hoja de «Compartir»: el enlace de Spotify (exacto si MusicBrainz lo conoce; si no, una búsqueda que
 * en Spotify abre el disco como primer resultado) o, para canciones, el archivo de audio tal cual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(track: Track?, album: AlbumRow?, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val sharer = rememberSharer()
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SurcoColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                S.share + " · " + (track?.title ?: album?.album ?: ""),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 6.dp))
            if (busy) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(S.findingSpotifyLink, color = SurcoColors.TextSecondary)
                }
            } else {
                SheetItem(Icons.Filled.Link, S.shareSpotifyLink, "share_spotify") {
                    busy = true
                    actions.scope.launch {
                        val spotify = actions.container.spotify
                        val link = when {
                            album != null -> spotify.albumLink(album.artist, album.album)
                            track != null -> spotify.trackLink(track.artist, track.title)
                            else -> null
                        }
                        busy = false
                        onDismiss()
                        if (link != null) {
                            val what = track?.let { "${it.title} · ${it.artist}" } ?: album?.let { "${it.album} · ${it.artist}" }.orEmpty()
                            sharer.shareText("$what\n${link.url}", S.share)
                            if (!link.exact) actions.toast(S.sharedSpotifySearch)
                        }
                    }
                }
                if (track != null) {
                    SheetItem(Icons.Filled.AudioFile, S.shareAudioFile(track.fileName.substringAfterLast('.', "").uppercase()), "share_file") {
                        onDismiss()
                        sharer.shareFile(track)
                    }
                }
            }
        }
    }
}
