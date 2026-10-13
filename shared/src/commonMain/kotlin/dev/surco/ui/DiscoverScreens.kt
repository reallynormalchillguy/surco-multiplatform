package dev.surco.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.AlbumRow
import dev.surco.data.DiscoverAlbum
import dev.surco.data.DiscoverSection
import dev.surco.data.Discovery
import dev.surco.data.formatDuration
import dev.surco.i18n.S
import dev.surco.net.Deezer
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.HorizontalSpacing
import dev.surco.ui.components.rememberArtColor
import dev.surco.ui.theme.SurcoColors

// ======================================================================= Secciones de Inicio

/** «Porque escuchas X»: álbumes que no tienes, de artistas parecidos. */
@Composable
fun DiscoverSectionView(section: DiscoverSection) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(section.seedImage, Modifier.size(48.dp), CircleShape, Icons.Filled.Person)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(S.becauseYouListen, style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary)
            Text(section.seed, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
        items(section.albums, key = { it.id }) { a -> DiscoverCard(a) { actions.openDiscover(a.id) } }
    }
}

@Composable
private fun DiscoverCard(a: DiscoverAlbum, onClick: () -> Unit) {
    Column(Modifier.width(156.dp).clickable(onClick = onClick).testTag("discover_${a.id}")) {
        Box {
            ArtImage(a.cover, Modifier.size(156.dp), RoundedCornerShape(6.dp))
            // Marca discreta: no lo tienes, se puede descargar.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(S.newForYou, style = MaterialTheme.typography.bodySmall, color = SurcoColors.Accent, maxLines = 1)
        Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ======================================================================= Página de álbum recomendado

/** Fichas ya cargadas (también la usan las capturas automáticas). */
val discoverDetailCache = HashMap<Long, Deezer.AlbumDetail>()

@Composable
fun DiscoverAlbumScreen(id: Long) {
    val actions = LocalActions.current
    var detail by remember(id) { mutableStateOf(discoverDetailCache[id]) }
    var failed by remember(id) { mutableStateOf(false) }
    var owned by remember(id) { mutableStateOf<AlbumRow?>(null) }

    LaunchedEffect(id) {
        if (detail == null) {
            val d = actions.container.deezer.album(id)
            if (d == null) failed = true else { discoverDetailCache[id] = d; detail = d }
        }
        detail?.let { d -> owned = findOwned(actions, d.album.artist, d.album.title) }
    }

    val d = detail
    val color = rememberArtColor(d?.album?.cover, SurcoColors.SurfaceHigher)

    if (d == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (failed) S.couldNotLoadAlbum else S.loading, color = SurcoColors.TextSecondary, modifier = Modifier.padding(32.dp))
            }
        }
        return
    }
    val album = d.album
    val query = Discovery.soulseekQuery(album.artist, album.title)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(color, SurcoColors.Background)))
                    .statusBarsPadding(),
            ) {
                IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ArtImage(
                        album.cover,
                        Modifier.padding(horizontal = 40.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally),
                        RoundedCornerShape(6.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(album.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.titleSmall,
                        // Toca el nombre para ver el perfil del artista.
                        modifier = Modifier.clickable(enabled = d.artistId > 0) { actions.openOnlineArtist(d.artistId) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            S.album,
                            album.year.takeIf { it > 0 }?.toString(),
                            S.songs(d.tracks.size),
                            S.duration(d.durationSec / 60L).takeIf { d.durationSec > 0 },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SurcoColors.TextSecondary,
                    )
                    val extra = (d.genres + listOfNotNull(d.label)).joinToString(" · ")
                    if (extra.isNotBlank()) {
                        Text(extra, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(18.dp))

                    val inLibrary = owned
                    if (inLibrary != null) {
                        Button(
                            onClick = { actions.openAlbum(inLibrary.albumKey) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(S.openInLibrary, style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        Text(S.notInLibrary, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { actions.openSoulseek(query) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("discover_soulseek"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(S.searchOnSoulseek, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { actions.openSoulseek(album.artist) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(S.searchArtistOnSoulseek, color = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(S.tapSongToSearch, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        itemsIndexed(d.tracks) { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.openSoulseek(Discovery.soulseekQuery(t.artist, t.title)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!t.artist.equals(album.artist, ignoreCase = true)) {
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
                    }
                }
                Text((t.durationSec * 1000L).formatDuration(), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            }
        }
    }
}

/** ¿Ya tienes este álbum? Compara título (sin «(Deluxe)», etc.) y artista. */
private suspend fun findOwned(actions: AppActions, artist: String, title: String): AlbumRow? {
    val clean = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifBlank { title }
    val candidates = actions.repo.dao.searchAlbums("%$clean%")
    val a = artist.lowercase()
    return candidates.firstOrNull { it.artist.lowercase() == a || it.artist.lowercase().contains(a) || a.contains(it.artist.lowercase()) }
}
