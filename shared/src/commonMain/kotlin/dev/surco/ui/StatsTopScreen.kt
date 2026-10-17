package dev.surco.ui

import dev.surco.i18n.S

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.surco.data.AlbumMatcher
import dev.surco.data.StatsMath
import dev.surco.data.StatsPeriod
import dev.surco.data.StatsRepository
import dev.surco.data.TopItem
import dev.surco.ui.theme.SurcoColors

/** Periodos de las listas top completas. */
private val TOP_PERIODS = listOf(StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.HALF, StatsPeriod.YEAR, StatsPeriod.ALL)

/**
 * Lista top completa (artistas, álbumes o canciones) de 7, 30, 180 o 365 días o desde siempre.
 * Se abre con el periodo predeterminado de esa lista, que se puede cambiar aquí mismo.
 */
@Composable
fun StatsTopScreen(kindName: String) {
    val actions = LocalActions.current
    val stats = actions.container.stats
    val prefs = actions.repo.prefs
    val settings by prefs.settings.collectAsState()
    val viewing by stats.viewing.collectAsState()
    val kind = runCatching { StatsRepository.TopKind.valueOf(kindName.uppercase()) }.getOrDefault(StatsRepository.TopKind.ARTISTS)
    val stored = when (kind) {
        StatsRepository.TopKind.ARTISTS -> settings.statsTopArtists
        StatsRepository.TopKind.ALBUMS -> settings.statsTopAlbums
        StatsRepository.TopKind.TRACKS -> settings.statsTopTracks
    }
    val defaultPeriod = TOP_PERIODS.firstOrNull { it.name == stored } ?: StatsPeriod.MONTH
    var period by remember(kind) { mutableStateOf(defaultPeriod) }
    val items = remember(kind, period, viewing) { mutableStateListOf<TopItem>() }
    var page by remember(kind, period, viewing) { mutableIntStateOf(0) }
    var more by remember(kind, period, viewing) { mutableStateOf(true) }
    var loading by remember(kind, period, viewing) { mutableStateOf(false) }
    var failed by remember(kind, period, viewing) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 6 } ?: true } }

    // Página a página (50 cada vez) según se baja.
    LaunchedEffect(kind, period, viewing, nearEnd, page) {
        if (!nearEnd || !more) return@LaunchedEffect
        loading = true
        try {
            val list = stats.topPage(kind, period, page + 1)
            items.addAll(list.filter { n -> items.none { it.name == n.name && it.artist == n.artist } })
            more = list.size >= StatsRepository.TOP_PAGE
            page += 1
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true; more = false
        } finally {
            loading = false
        }
    }

    fun setDefault(p: StatsPeriod) = prefs.update {
        when (kind) {
            StatsRepository.TopKind.ARTISTS -> it.copy(statsTopArtists = p.name)
            StatsRepository.TopKind.ALBUMS -> it.copy(statsTopAlbums = p.name)
            StatsRepository.TopKind.TRACKS -> it.copy(statsTopTracks = p.name)
        }
    }

    val title = when (kind) {
        StatsRepository.TopKind.ARTISTS -> S.topArtistsTitle
        StatsRepository.TopKind.ALBUMS -> S.topAlbumsTitle
        StatsRepository.TopKind.TRACKS -> S.topTracksTitle
    }
    val lib = rememberStatsLibraryIndex()
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize().testTag("stats_top")) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@" + stats.currentUser(), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(TOP_PERIODS) { _, p ->
                dev.surco.ui.components.SurcoChip(
                    S.topPeriodChip(p.name), selected = period == p, onClick = { period = p },
                    modifier = Modifier.testTag("top_period_${p.name}"),
                )
            }
        }
        // Predeterminado: discreto, debajo de los periodos.
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (period == defaultPeriod) {
                Icon(Icons.Filled.Star, null, tint = SurcoColors.Accent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(S.defaultPeriod, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                TextButton(onClick = { setDefault(period) }, modifier = Modifier.testTag("top_make_default")) {
                    Icon(Icons.Outlined.StarOutline, null, tint = SurcoColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S.makeDefaultPeriod(S.topPeriodChip(period.name)), style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary)
                }
            }
        }
        val max = items.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
            itemsIndexed(items, key = { i, t -> "$i|${t.name}|${t.artist}" }) { i, t ->
                TopRow(i + 1, t, kind, max, lib, onClick = {
                    when (kind) {
                        StatsRepository.TopKind.ARTISTS -> {
                            val local = lib.artists[AlbumMatcher.norm(t.name).replace(" ", "")]
                            when {
                                local != null -> actions.openArtist(local)
                                t.deezerId > 0 -> actions.openOnlineArtist(t.deezerId)
                                t.url.isNotBlank() -> runCatching { uri.openUri(t.url) }
                            }
                        }
                        StatsRepository.TopKind.ALBUMS -> {
                            val key = lib.albums[StatsMath.key(t.artist, t.name)]
                            if (key != null) actions.openAlbum(key) else if (t.url.isNotBlank()) runCatching { uri.openUri(t.url) }
                        }
                        StatsRepository.TopKind.TRACKS -> {
                            val local = lib.tracks[StatsMath.key(t.artist, t.name)]
                            if (local != null) actions.play(listOf(local), 0, "estadísticas|${S.stats}")
                            else if (t.url.isNotBlank()) runCatching { uri.openUri(t.url) }
                        }
                    }
                })
            }
            item {
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
                    }
                    failed && items.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(S.statsLoadFailed, color = SurcoColors.TextSecondary, textAlign = TextAlign.Center)
                        TextButton(onClick = { failed = false; more = true; page = 0 }) { Text(S.retry, color = SurcoColors.Accent) }
                    }
                    items.isEmpty() && !more -> Text(
                        S.nothingInPeriod, color = SurcoColors.TextSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopRow(rank: Int, t: TopItem, kind: StatsRepository.TopKind, max: Long, lib: StatsLibraryIndex, onClick: () -> Unit) {
    val stats = LocalActions.current.container.stats
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.titleSmall, color = SurcoColors.TextSecondary, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        when (kind) {
            StatsRepository.TopKind.ARTISTS -> {
                // La foto se busca (en Deezer) sólo cuando la fila aparece en pantalla.
                val pic by produceState(t.imageUrl, t.name) { if (value == null) value = stats.artistPicture(t.name).first }
                Box(Modifier.size(48.dp).clip(CircleShape).background(SurcoColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    if (pic != null) AsyncImage(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.Person, null, tint = SurcoColors.TextTertiary)
                }
            }
            StatsRepository.TopKind.ALBUMS -> dev.surco.ui.components.ArtImage(t.imageUrl, Modifier.size(48.dp), RoundedCornerShape(4.dp))
            StatsRepository.TopKind.TRACKS -> dev.surco.ui.components.ArtImage(lib.tracks[StatsMath.key(t.artist, t.name)]?.artPath, Modifier.size(48.dp), RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(fmtPlays(t.plays), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            }
            if (kind != StatsRepository.TopKind.ARTISTS) {
                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurcoColors.SurfaceHigher)) {
                Box(Modifier.fillMaxWidth((t.plays.toFloat() / max).coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurcoColors.Accent))
            }
        }
    }
}
