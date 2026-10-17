package dev.surco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.ArtistRow
import dev.surco.data.Discovery
import dev.surco.data.formatDuration
import dev.surco.i18n.S
import dev.surco.net.Deezer
import dev.surco.ui.components.ArtImage
import dev.surco.ui.components.HorizontalSpacing
import dev.surco.ui.components.SectionTitle
import dev.surco.ui.components.rememberArtColor
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import dev.surco.util.fmt

/** Todo lo que se muestra en el perfil de un artista de internet. */
data class OnlineArtist(
    val artist: Deezer.Artist,
    val top: List<Deezer.TopTrack>,
    val albums: List<Deezer.Album>,
    val related: List<Deezer.Artist>,
)

/** Cachés en memoria (también las usan las pruebas automáticas para no depender de la red). */
val onlineArtistCache = HashMap<Long, OnlineArtist>()
val onlineSearchCache = HashMap<String, List<Deezer.Artist>>()

fun formatFans(n: Int): String {
    val v = when {
        n >= 1_000_000 -> "${(n / 1_000_000.0).fmt(1)}M".replace(".0M", "M")
        n >= 1_000 -> "${(n / 1_000.0).fmt(0)}K"
        else -> n.toString()
    }
    return S.fans(v)
}

// ======================================================================= En el buscador

/** Artistas de internet para una búsqueda (con un pequeño retardo para no consultar en cada letra). */
@Composable
fun rememberOnlineArtists(query: String): List<Deezer.Artist> {
    val actions = LocalActions.current
    val q = query.trim().lowercase()
    var list by remember(q) { mutableStateOf(onlineSearchCache[q] ?: emptyList()) }
    LaunchedEffect(q) {
        if (q.length < 2 || onlineSearchCache.containsKey(q)) return@LaunchedEffect
        delay(450)
        val found = runCatching { actions.container.deezer.searchArtists(q, 6) }.getOrDefault(emptyList())
        if (found.isNotEmpty()) onlineSearchCache[q] = found
        list = found
    }
    return list
}

@Composable
fun OnlineArtistRow(a: Deezer.Artist, local: ArtistRow?) {
    val actions = LocalActions.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { actions.openOnlineArtist(a.id) }
            .testTag("online_artist_${a.id}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(a.picture, Modifier.size(56.dp), CircleShape, Icons.Filled.Person)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (local != null) "${S.artist} • ${S.inYourLibrary}" else "${S.artist} • ${formatFans(a.fans)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (local != null) SurcoColors.Accent else SurcoColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

// ======================================================================= Perfil

@Composable
fun OnlineArtistScreen(id: Long) {
    val actions = LocalActions.current
    var data by remember(id) { mutableStateOf(onlineArtistCache[id]) }
    var failed by remember(id) { mutableStateOf(false) }
    var local by remember(id) { mutableStateOf<ArtistRow?>(null) }

    LaunchedEffect(id) {
        if (data == null) {
            val deezer = actions.container.deezer
            val loaded = runCatching {
                coroutineScope {
                    val info = async { deezer.artistInfo(id) }
                    val top = async { deezer.top(id, 10) }
                    val related = async { deezer.related(id) }
                    val artist = info.await() ?: return@coroutineScope null
                    val albums = deezer.albums(id, artist.name)
                        .filter { it.type == "album" || it.type == "ep" }
                        .distinctBy { it.title.lowercase().replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "") }
                        .sortedByDescending { it.year }
                    OnlineArtist(artist, top.await(), albums, related.await().take(12))
                }
            }.getOrNull()
            if (loaded == null) failed = true else { onlineArtistCache[id] = loaded; data = loaded }
        }
        data?.let { d ->
            local = actions.repo.dao.searchArtists(d.artist.name).firstOrNull { it.name.equals(d.artist.name, ignoreCase = true) }
        }
    }

    val d = data
    val color = rememberArtColor(d?.artist?.picture, SurcoColors.SurfaceHigher)
    if (d == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (failed) S.couldNotLoadArtist else S.loading, color = SurcoColors.TextSecondary, modifier = Modifier.padding(32.dp), textAlign = TextAlign.Center)
            }
        }
        return
    }
    val a = d.artist

    LazyColumn(Modifier.fillMaxSize().testTag("online_artist_screen"), contentPadding = PaddingValues(bottom = 32.dp)) {
        // ---- Cabecera
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(color, SurcoColors.fadeTo(color))))
                    .statusBarsPadding(),
            ) {
                IconButton(onClick = { actions.nav.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage(a.picture, Modifier.size(200.dp), CircleShape, Icons.Filled.Person)
                    Spacer(Modifier.height(18.dp))
                    Text(a.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(formatFans(a.fans), a.albums.takeIf { it > 0 }?.let { S.albumsCount(it) }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SurcoColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val inLib = local
                    if (inLib != null) {
                        Button(
                            onClick = { actions.openArtist(inLib.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(S.openInLibrary, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val primary = inLib == null
                    if (primary) {
                        Button(
                            onClick = { actions.openSoulseek(Discovery.soulseekQuery(a.name, "")) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("online_artist_soulseek"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(S.searchArtistOnSoulseek, style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { actions.openSoulseek(Discovery.soulseekQuery(a.name, "")) },
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("online_artist_soulseek"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, tint = SurcoColors.TextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(S.searchArtistOnSoulseek, color = SurcoColors.TextPrimary)
                        }
                    }
                }
            }
        }

        // ---- Canciones populares (tocar → buscar en Soulseek)
        if (d.top.isNotEmpty()) {
            item { SectionTitle(S.popular) }
            itemsIndexed(d.top.take(5)) { i, t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { actions.openSoulseek(Discovery.soulseekQuery(t.artist.ifBlank { a.name }, t.title)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary, modifier = Modifier.width(24.dp))
                    ArtImage(t.cover, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.album, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text((t.durationSec * 1000L).formatDuration(), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                }
            }
            item {
                Text(
                    S.tapSongToSearch,
                    style = MaterialTheme.typography.bodySmall,
                    color = SurcoColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ---- Discografía (tocar → ficha del álbum)
        if (d.albums.isNotEmpty()) {
            item { SectionTitle(S.discography) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = HorizontalSpacing) {
                    items(d.albums, key = { it.id }) { al ->
                        Column(Modifier.width(150.dp).clickable { actions.openDiscover(al.id) }.testTag("online_album_${al.id}")) {
                            ArtImage(al.cover, Modifier.size(150.dp), RoundedCornerShape(6.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(al.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(al.year.takeIf { it > 0 }?.toString(), if (al.type == "ep") "EP" else S.album).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = SurcoColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ---- Parecidos (tocar → su perfil)
        if (d.related.isNotEmpty()) {
            item { SectionTitle(S.fansAlsoLike) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(d.related, key = { it.id }) { r ->
                        Column(
                            Modifier.width(120.dp).clickable { actions.openOnlineArtist(r.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtImage(r.picture, Modifier.size(120.dp), CircleShape, Icons.Filled.Person)
                            Spacer(Modifier.height(8.dp))
                            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
