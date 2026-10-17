package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.surco.data.ActivityBucket
import dev.surco.data.AlbumMatcher
import dev.surco.data.StatsMath
import dev.surco.data.StatsPeriod
import dev.surco.data.StatsReport
import dev.surco.data.TopItem
import dev.surco.data.Track
import dev.surco.i18n.I18n
import dev.surco.i18n.Lang
import dev.surco.ui.theme.SurcoColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ======================================================================= Pantalla

/**
 * «Estadísticas»: tu escucha según Last.fm. Reloj de escucha, tu semana, actividad, tops, ADN musical
 * y datos curiosos. Sin cuenta conectada, invita a conectarla o a crear una.
 */
@Composable
fun StatsScreen() {
    val actions = LocalActions.current
    val prefs = actions.repo.prefs
    val settings by prefs.settings.collectAsState()
    val stats = actions.container.stats
    val state by stats.state.collectAsState()
    val viewing by stats.viewing.collectAsState()
    var askUser by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().testTag("stats_screen")) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatarOrLastFm(settings.lastfmLoggedIn && viewing == null, state.report?.profile?.imageUrl) { Overlays.drawer = true }
            Spacer(Modifier.width(14.dp))
            Text(S.stats, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (settings.lastfmReady) {
                // Discreto: ver las estadísticas de otra persona.
                IconButton(onClick = { askUser = true }, modifier = Modifier.testTag("stats_other_user")) {
                    Icon(Icons.Outlined.PersonSearch, S.viewOtherUser, tint = SurcoColors.TextSecondary)
                }
            }
            if (settings.lastfmLoggedIn || viewing != null) {
                IconButton(onClick = { stats.load(force = true) }, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, S.retry, tint = SurcoColors.TextSecondary)
                }
            }
        }
        viewing?.let { u ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(SurcoColors.Accent.copy(alpha = 0.14f)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PersonSearch, null, tint = SurcoColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(S.viewingUser(u), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { stats.view(null) }) { Text(S.backToMyStats, color = SurcoColors.Accent) }
            }
        }
        if (!settings.lastfmLoggedIn && viewing == null) {
            ConnectLastFm(onOtherUser = { askUser = true })
        } else {
            LaunchedEffect(settings.lastfmUser, viewing) { stats.load() }
            StatsContent(state, onPeriod = { stats.select(it) }, onRetry = { stats.load(force = true) })
        }
    }
    if (askUser) OtherUserDialog(onDismiss = { askUser = false }, onView = { askUser = false; stats.view(it) })
}

@Composable
private fun OtherUserDialog(onDismiss: () -> Unit, onView: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        title = { Text(S.viewOtherUser) },
        text = {
            Column {
                Text(S.viewOtherUserHint, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text(S.lastfmUsername) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (name.isNotBlank()) onView(name) }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SurcoColors.Accent, focusedLabelColor = SurcoColors.Accent, cursorColor = SurcoColors.Accent,
                        unfocusedBorderColor = SurcoColors.SurfaceHigher,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("other_user_field"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onView(name) }, enabled = name.isNotBlank()) { Text(S.view, color = SurcoColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = SurcoColors.TextSecondary) } },
    )
}

@Composable
private fun AppAvatarOrLastFm(connected: Boolean, image: String?, onClick: () -> Unit) {
    if (connected && image != null) {
        AsyncImage(
            image, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(CircleShape).background(SurcoColors.SurfaceHigh).clickable(onClick = onClick),
        )
    } else {
        dev.surco.ui.components.AppAvatar(onClick = onClick)
    }
}

// ======================================================================= Sin cuenta

@Composable
private fun ConnectLastFm(onOtherUser: () -> Unit) {
    val actions = LocalActions.current
    val settings by actions.repo.prefs.settings.collectAsState()
    val uri = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize().imePadding().testTag("stats_connect"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            // Ilustración: un reloj de escucha de ejemplo, el mismo gráfico que verás con tus datos.
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                ListeningClock(DEMO_HOURS, selected = 22, onSelect = {}, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), showValue = false)
            }
            Text(S.statsConnectTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(S.statsConnectBody, style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurcoColors.Surface).padding(16.dp)) {
                if (!settings.lastfmReady) {
                    LastFmKeysForm(help = S.statsKeysHelp, onOpenLink = { runCatching { uri.openUri("https://www.last.fm/api/account/create") } })
                } else {
                    LastFmLoginForm()
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(S.noLastfmAccountYet, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = { runCatching { uri.openUri("https://www.last.fm/join") } }, modifier = Modifier.testTag("create_lastfm")) {
                    Text(S.createLastfmAccount, color = SurcoColors.TextPrimary)
                }
            }
            if (settings.lastfmReady) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onOtherUser) { Text(S.viewOtherUser, color = SurcoColors.TextSecondary) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(S.statsReadOnlyNote, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Reparto de ejemplo para la ilustración: poca mañana, tarde media y mucha noche. */
private val DEMO_HOURS = listOf(9, 5, 3, 1, 1, 1, 2, 6, 10, 9, 8, 9, 12, 11, 10, 12, 15, 18, 21, 24, 27, 31, 34, 22)

// ======================================================================= Con datos

@Composable
private fun StatsContent(state: dev.surco.data.StatsUiState, onPeriod: (StatsPeriod) -> Unit, onRetry: () -> Unit) {
    val r = state.report
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    ScrollToTopOnTabTap("stats") { if (listState.firstVisibleItemIndex < 8) listState.animateScrollToItem(0) else listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().testTag("stats_list"), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
        if (r != null) item(key = "hero") { ProfileHero(r) }
        item(key = "recent") { RecentScrobbles() }
        item(key = "periods") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(StatsPeriod.entries) { _, p ->
                    dev.surco.ui.components.SurcoChip(
                        S.periodChip(p.name), selected = state.period == p, onClick = { onPeriod(p) },
                        modifier = Modifier.testTag("period_${p.name}"),
                    )
                }
            }
        }
        if (state.loading) {
            item(key = "progress") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0.02f, 1f) },
                        color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(S.readingScrobbles((state.progress * 100).roundToInt()), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                }
            }
        }
        state.error?.let { msg ->
            item(key = "error") {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurcoColors.Surface).padding(16.dp),
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text(S.retry, color = SurcoColors.Accent) }
                }
            }
        }
        if (r == null || r.period != state.period && !state.loading) return@LazyColumn
        // Mientras se recarga, se sigue viendo lo anterior, un poco apagado (sin saltos).
        val dim = if (state.loading) Modifier.alpha(0.45f) else Modifier
        item(key = "summary") { Box(dim) { PeriodSummary(r) } }
        if (r.sampled == 0) {
            item(key = "empty") {
                Text(S.noScrobblesInPeriod, color = SurcoColors.TextSecondary, modifier = Modifier.padding(24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
        } else {
            item(key = "clock") { Box(dim) { ClockCard(r) } }
            item(key = "week") { Box(dim) { WeekCard(r) } }
            item(key = "activity") { Box(dim) { ActivityCard(r) } }
        }
        if (r.topArtists.isNotEmpty()) item(key = "artists") { Box(dim) { TopArtistsCard(r.topArtists) } }
        if (r.topAlbums.isNotEmpty()) item(key = "albums") { Box(dim) { TopAlbumsRow(r.topAlbums) } }
        if (r.topTracks.isNotEmpty()) item(key = "tracks") { Box(dim) { TopTracksCard(r.topTracks) } }
        if (r.genres.isNotEmpty()) item(key = "genres") { Box(dim) { GenresCard(r) } }
        item(key = "facts") { Box(dim) { FactsGrid(r) } }
        item(key = "footer") { Footer(r) }
    }
}

// ======================================================================= Piezas

@Composable
private fun StatsCard(title: String, subtitle: String? = null, tag: String = "", content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(SurcoColors.Surface).padding(16.dp).testTag(tag),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ProfileHero(r: StatsReport) {
    val p = r.profile
    val uri = LocalUriHandler.current
    val accent = SurcoColors.Accent
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.34f), SurcoColors.Surface, SurcoColors.Surface)))
            .padding(20.dp).testTag("stats_hero"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(SurcoColors.SurfaceHigher), contentAlignment = Alignment.Center) {
                if (p.imageUrl != null) AsyncImage(p.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(enabled = p.url.isNotBlank()) { runCatching { uri.openUri(p.url) } }) {
                Text(p.realName.ifBlank { p.name }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val since = if (p.registeredAt > 0) StatsMath.civil(p.registeredAt / 86_400).let { (y, m, _) -> S.since(m, y) } else ""
                Text(listOf("@${p.name}", since, p.country).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(18.dp))
        // La cifra principal: una por pantalla.
        Text(fmt(p.playcount), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp), maxLines = 1)
        Text(S.scrobblesTotal, style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val actions = LocalActions.current
            MiniStat(S.artists, fmt(p.artists), Modifier.weight(1f).testTag("hero_artists")) { actions.go("stats_top/artists") }
            MiniStat(S.albums, fmt(p.albums), Modifier.weight(1f).testTag("hero_albums")) { actions.go("stats_top/albums") }
            MiniStat(S.songs, fmt(p.tracks), Modifier.weight(1f).testTag("hero_tracks")) { actions.go("stats_top/tracks") }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SurcoColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

// ----------------------------------------------------------------------- Escuchado hace poco

/** Las últimas 10 escuchas (y la que suena ahora), entre el perfil y los periodos. */
@Composable
private fun RecentScrobbles() {
    val actions = LocalActions.current
    val list by actions.container.stats.recent.collectAsState()
    if (list.isEmpty()) return
    val uri = LocalUriHandler.current
    val lib = rememberStatsLibraryIndex()
    val nowSec = remember(list) { dev.surco.util.nowMillis() / 1000 }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurcoColors.Surface).padding(vertical = 12.dp).testTag("stats_recent")) {
        Text(S.recentlyPlayed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        list.take(11).forEach { r ->
            val local = lib.tracks[StatsMath.key(r.artist, r.title)]
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (local != null) actions.play(listOf(local), 0, "estadísticas|${S.stats}")
                    else actions.openOnlineTrackAlbum(r.artist, r.title)
                }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dev.surco.ui.components.ArtImage(local?.artPath ?: r.imageUrl, Modifier.size(42.dp), RoundedCornerShape(4.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (r.nowPlaying) SurcoColors.Accent else SurcoColors.TextPrimary)
                    Text(r.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { actions.openArtistAnywhere(lib, r.artist) })
                }
                Spacer(Modifier.width(8.dp))
                if (r.nowPlaying) {
                    Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.nowLabel, style = MaterialTheme.typography.labelSmall, color = SurcoColors.Accent)
                } else {
                    Text(timeAgo(r.at, nowSec), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextTertiary)
                }
            }
        }
    }
}

private fun timeAgo(at: Long, nowSec: Long): String {
    val d = (nowSec - at).coerceAtLeast(0)
    return when {
        d < 60 -> S.justNow
        d < 3600 -> S.minutesAgo((d / 60).toInt())
        d < 86_400 -> S.hoursAgo((d / 3600).toInt())
        d < 2 * 86_400 -> S.yesterday
        else -> dayLabel(at / 86_400)
    }
}

@Composable
private fun PeriodSummary(r: StatsReport) {
    val days = if (r.period.days > 0) r.period.days else ((r.createdAt / 1000 - r.profile.registeredAt) / 86_400).coerceAtLeast(1).toInt()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(S.periodTitle(r.period.name), style = MaterialTheme.typography.titleSmall, color = SurcoColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(S.scrobblesWord, fmt(r.periodPlays), Modifier.weight(1f))
            Tile(S.hoursOfMusic, "≈ " + fmt(r.minutesEstimate / 60), Modifier.weight(1f))
            Tile(S.perDay, fmt((r.periodPlays + days / 2) / days), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(SurcoColors.Surface).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ----------------------------------------------------------------------- Reloj

@Composable
private fun ClockCard(r: StatsReport) {
    val peak = r.peakHour
    var selected by remember(r) { mutableIntStateOf(peak) }
    val total = r.hours.sum().coerceAtLeast(1)
    StatsCard(S.clockTitle, S.clockSub, tag = "stats_clock") {
        ListeningClock(r.hours, selected, onSelect = { selected = it }, modifier = Modifier.fillMaxWidth())
        if (selected >= 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                hh(selected) + " · " + S.playsShare(fmt(r.hours[selected].toLong()), (r.hours[selected] * 100f / total).roundToInt()),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        if (peak >= 0) {
            val type = StatsMath.chronotype(peak).name
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(SurcoColors.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoGraph, null, tint = SurcoColors.Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(S.chronotypeName(type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(S.chronotypeText(type, hh(peak)), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Las cuatro partes del día, como medidores.
        val parts = StatsMath.dayParts(r.hours)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            parts.forEachIndexed { i, f ->
                Column(Modifier.weight(1f)) {
                    Text("${(f * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(S.dayPart(i), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Meter(f / (parts.maxOrNull()?.takeIf { it > 0 } ?: 1f))
                }
            }
        }
    }
}

@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurcoColors.SurfaceHigher)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurcoColors.Accent))
    }
}

/**
 * Escucha por horas del día: una curva suave en el color de acento (con un velo suave debajo), una línea
 * vertical cada dos horas y las horas en diagonal debajo. Al tocar, se marca la hora más cercana.
 */
@Composable
fun ListeningClock(hours: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, showValue: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val grow = remember(hours) { Animatable(0f) }
    LaunchedEffect(hours) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    val es = I18n.lang == Lang.ES
    val accent = SurcoColors.Accent
    val grid = SurcoColors.TextPrimary.copy(alpha = 0.08f)
    val labelColor = SurcoColors.TextSecondary
    val ring = SurcoColors.Surface
    val pill = SurcoColors.SurfaceHigher
    val ink = SurcoColors.TextPrimary
    // Hora bajo el dedo mientras se toca la gráfica (-1: nadie la toca; entonces no se marca nada).
    var touching by remember { mutableIntStateOf(-1) }
    Canvas(
        modifier
            .aspectRatio(1.39f)
            .pointerInput(hours) {
                fun hourAt(x: Float): Int {
                    val left = size.width * 0.145f
                    val right = size.width * 0.905f
                    return (((x - left) / (right - left)) * 23).roundToInt().coerceIn(0, 23)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touching = hourAt(down.position.x)
                    onSelect(touching)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val h = hourAt(ch.position.x)
                        if (h != touching) { touching = h; onSelect(h) }
                    }
                    touching = -1
                }
            },
    ) {
        val left = size.width * 0.145f
        val right = size.width * 0.905f
        val top = size.height * 0.097f
        val bottom = size.height * 0.845f
        val yHigh = top + (bottom - top) * 0.03f
        val yLow = bottom - (bottom - top) * 0.12f
        fun x(h: Int) = left + (right - left) * h / 23f
        fun y(v: Int) = yLow - (yLow - yHigh) * v.toFloat() / max

        // Líneas verticales cada dos horas (y la última, las 23) y sus horas en diagonal.
        val ticks = (0..20 step 2) + 23
        val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        for (h in ticks) {
            drawLine(grid, Offset(x(h), top), Offset(x(h), bottom), strokeWidth = 1.dp.toPx())
            val text = if (es) "${if (h < 10) "0$h" else "$h"}h" else when {
                h == 0 -> "12AM"; h < 12 -> "${h}AM"; h == 12 -> "12PM"; else -> "${h - 12}PM"
            }
            val layout = measurer.measure(text, labelStyle)
            val pivot = Offset(x(h) + 2.dp.toPx(), bottom + 3.dp.toPx())
            rotate(-45f, pivot) {
                drawText(layout, topLeft = Offset(pivot.x - layout.size.width, pivot.y - layout.size.height / 2f))
            }
        }

        // Curva suave (interpolación monótona: nunca se sale por encima ni por debajo de los datos).
        val pts = (0 until 24).map { Offset(x(it), y(hours.getOrElse(it) { 0 })) }
        val path = smoothPath(pts)
        clipRect(right = left + (right - left) * grow.value + 4.dp.toPx()) {
            // Velo suave bajo la curva (el mismo tono, muy transparente) y la curva encima.
            val area = Path().apply {
                addPath(path)
                lineTo(pts.last().x, bottom)
                lineTo(pts.first().x, bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f)), startY = yHigh, endY = bottom))
            drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // La hora elegida: un punto sobre la curva y una etiqueta con sus escuchas.
        if (showValue && touching in 0..23 && grow.value > 0.99f) {
            val selected = touching
            val p = pts[selected]
            drawLine(accent.copy(alpha = 0.5f), Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(ring, radius = 7.dp.toPx(), center = p)
            drawCircle(accent, radius = 5.dp.toPx(), center = p)
            val label = measurer.measure(
                "${hh(selected)} · ${hours.getOrElse(selected) { 0 }}",
                TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            val padH = 8.dp.toPx(); val padV = 4.dp.toPx()
            val w = label.size.width + padH * 2
            val hgt = label.size.height + padV * 2
            val lx = (p.x - w / 2).coerceIn(4.dp.toPx(), size.width - w - 4.dp.toPx())
            val above = p.y - 14.dp.toPx() - hgt
            val ly = if (above > 2.dp.toPx()) above else p.y + 14.dp.toPx()
            drawRoundRect(pill, Offset(lx, ly), Size(w, hgt), CornerRadius(hgt / 2))
            drawText(label, topLeft = Offset(lx + padH, ly + padV))
        }
    }
}

/** Curva suave que pasa por todos los puntos sin pasarse (cúbica monótona de Fritsch–Carlson). */
private fun smoothPath(p: List<Offset>): Path {
    val path = Path()
    if (p.isEmpty()) return path
    path.moveTo(p[0].x, p[0].y)
    if (p.size < 3) { p.drop(1).forEach { path.lineTo(it.x, it.y) }; return path }
    val n = p.size
    val d = FloatArray(n - 1) { (p[it + 1].y - p[it].y) / (p[it + 1].x - p[it].x) }
    val m = FloatArray(n)
    m[0] = d[0]; m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]; val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    for (i in 0 until n - 1) {
        val dx = (p[i + 1].x - p[i].x) / 3
        path.cubicTo(p[i].x + dx, p[i].y + m[i] * dx, p[i + 1].x - dx, p[i + 1].y - m[i + 1] * dx, p[i + 1].x, p[i + 1].y)
    }
    return path
}

// ----------------------------------------------------------------------- Semana

@Composable
private fun WeekCard(r: StatsReport) {
    var sel by remember(r) { mutableStateOf<Pair<Int, Int>?>(null) }
    val peak = r.peakSlot
    StatsCard(S.weekTitle, S.weekSub, tag = "stats_week") {
        val s = sel
        Text(
            when {
                s != null -> S.slotDetail(s.first, hh(s.second), hh((s.second + 1) % 24), fmt(r.week[s.first][s.second].toLong()))
                peak != null -> S.yourMoment(peak.first, hh(peak.second))
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        WeekHeatmap(r.week, sel ?: peak, onSelect = { sel = it })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(S.heatLess, style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary)
            Spacer(Modifier.width(6.dp))
            listOf(0.08f, 0.3f, 0.55f, 0.8f, 1f).forEach { a ->
                Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(SurcoColors.Accent.copy(alpha = a)))
            }
            Spacer(Modifier.width(6.dp))
            Text(S.heatMore, style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary)
        }
    }
}

/** Mapa de calor 7 × 24: una fila por día de la semana, una celda por hora. Un solo tono: más intenso, más escuchas. */
@Composable
private fun WeekHeatmap(week: List<List<Int>>, selected: Pair<Int, Int>?, onSelect: (Pair<Int, Int>) -> Unit) {
    val accent = SurcoColors.Accent
    val empty = SurcoColors.TextPrimary.copy(alpha = 0.05f)
    val labelColor = SurcoColors.TextSecondary
    val ring = SurcoColors.TextPrimary
    val measurer = rememberTextMeasurer()
    val max = week.maxOfOrNull { row -> row.maxOrNull() ?: 0 }?.coerceAtLeast(1) ?: 1
    val labelW = 16.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - labelW) / 24
        val height = cell * 7 + 18.dp
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(week) {
                detectTapGestures { pos ->
                    val cw = (size.width - labelW.toPx()) / 24
                    val col = ((pos.x - labelW.toPx()) / cw).toInt()
                    val row = (pos.y / cw).toInt()
                    if (col in 0..23 && row in 0..6) onSelect(row to col)
                }
            },
        ) {
            val lw = labelW.toPx()
            val cw = (size.width - lw) / 24
            val gap = 2.dp.toPx()
            val style = TextStyle(color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            for (d in 0 until 7) {
                val t = measurer.measure(S.weekdayShort(d), style)
                drawText(t, topLeft = Offset(0f, d * cw + (cw - t.size.height) / 2))
                for (h in 0 until 24) {
                    val v = week[d][h]
                    val color = if (v == 0) empty else accent.copy(alpha = 0.16f + 0.84f * (v.toFloat() / max).pow(0.75f))
                    val tl = Offset(lw + h * cw + gap / 2, d * cw + gap / 2)
                    val sz = Size(cw - gap, cw - gap)
                    drawRoundRect(color, tl, sz, CornerRadius(3.dp.toPx()))
                    if (selected == d to h) {
                        drawRoundRect(ring, tl, sz, CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    }
                }
            }
            for (h in listOf(0, 6, 12, 18)) {
                val t = measurer.measure(hh(h).take(2), style)
                drawText(t, topLeft = Offset(lw + h * cw, 7 * cw + 4.dp.toPx()))
            }
        }
    }
}

// ----------------------------------------------------------------------- Actividad

@Composable
private fun ActivityCard(r: StatsReport) {
    val buckets = r.activity
    val recordIdx = buckets.indices.maxByOrNull { buckets[it].count } ?: -1
    var sel by remember(r) { mutableIntStateOf(-1) }
    val shown = if (sel >= 0) sel else recordIdx
    StatsCard(S.activityTitle, S.activitySub(r.bucketDays), tag = "stats_activity") {
        if (shown >= 0 && shown < buckets.size) {
            Text(
                S.bucketDetail(bucketLabel(buckets[shown], r.bucketDays), fmt(buckets[shown].count.toLong())),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
        }
        ActivityChart(buckets, shown, onSelect = { sel = it })
        if (buckets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(shortDate(buckets.first().startDay, r.bucketDays), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(shortDate(buckets.last().startDay, r.bucketDays), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FactLine(Icons.Filled.LocalFireDepartment, S.daysN(r.currentStreak), S.currentStreak, Modifier.weight(1f))
            FactLine(Icons.Filled.AutoGraph, S.daysN(r.bestStreak), S.bestStreak, Modifier.weight(1f))
        }
        r.recordDay?.let { rec ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FactLine(Icons.Filled.Timer, fmt(rec.count.toLong()), S.recordDay + " · " + dayLabel(rec.day), Modifier.weight(1f))
                FactLine(Icons.Filled.LibraryMusic, fmt(r.activeDays.toLong()), S.activeDays, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FactLine(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SurcoColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Columnas finas que crecen desde la base, con el extremo de arriba redondeado. La elegida (o el récord), en acento. */
@Composable
private fun ActivityChart(buckets: List<ActivityBucket>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = SurcoColors.Accent
    val grid = SurcoColors.TextPrimary.copy(alpha = 0.08f)
    val labelColor = SurcoColors.TextTertiary
    val measurer = rememberTextMeasurer()
    val grow = remember(buckets) { Animatable(0f) }
    LaunchedEffect(buckets) { grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val max = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(132.dp).pointerInput(buckets) {
            detectTapGestures { pos ->
                if (buckets.isEmpty()) return@detectTapGestures
                val i = (pos.x / (size.width / buckets.size)).toInt().coerceIn(0, buckets.size - 1)
                onSelect(i)
            }
        },
    ) {
        if (buckets.isEmpty()) return@Canvas
        val axisW = 0f
        val h = size.height
        val style = TextStyle(color = labelColor, fontSize = 10.sp)
        // Rejilla: base y máximo (líneas finas, continuas).
        drawLine(grid, Offset(0f, h - 0.5f), Offset(size.width, h - 0.5f), strokeWidth = 1f)
        drawLine(grid, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
        val maxLabel = measurer.measure(compact(max.toLong()), style)
        drawText(maxLabel, topLeft = Offset(size.width - maxLabel.size.width, 2.dp.toPx()))
        val slot = (size.width - axisW) / buckets.size
        val barW = min(slot * 0.72f, 24.dp.toPx()).coerceAtLeast(1.dp.toPx())
        val radius = min(4.dp.toPx(), barW / 2)
        buckets.forEachIndexed { i, b ->
            if (b.count == 0) return@forEachIndexed
            val bh = (h - 18.dp.toPx()) * (b.count.toFloat() / max) * grow.value
            val x = i * slot + (slot - barW) / 2
            val top = h - bh
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x, top = top, right = x + barW, bottom = h,
                        topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                        bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                    )
                )
            }
            drawPath(path, if (i == selected) accent else accent.copy(alpha = 0.45f))
        }
    }
}

// ----------------------------------------------------------------------- Tops

@Composable
internal fun rememberStatsLibraryIndex(): StatsLibraryIndex {
    val dao = LocalActions.current.repo.dao
    val idx by produceState(StatsLibraryIndex(emptyMap(), emptyMap(), emptyMap())) {
        val tracks = runCatching { dao.allTracksOnce() }.getOrDefault(emptyList())
        value = StatsLibraryIndex(
            tracks = tracks.associateBy { StatsMath.key(it.artist, it.title) },
            albums = tracks.associate { StatsMath.key(it.albumArtist.ifBlank { it.groupArtist }, it.album) to it.albumKey },
            artists = tracks.associate { AlbumMatcher.norm(it.groupArtist).replace(" ", "") to it.groupArtist },
        )
    }
    return idx
}

internal data class StatsLibraryIndex(val tracks: Map<String, Track>, val albums: Map<String, String>, val artists: Map<String, String>)

/** Un artista de las estadísticas: su página de la biblioteca si lo tienes y, si no, la de internet (en la app). */
internal fun AppActions.openArtistAnywhere(lib: StatsLibraryIndex, name: String, deezerId: Long = 0) {
    val local = lib.artists[AlbumMatcher.norm(name).replace(" ", "")]
    if (local != null) openArtist(local) else openOnlineArtistNamed(name, deezerId)
}

@Composable
private fun TopArtistsCard(list: List<TopItem>) {
    val actions = LocalActions.current
    val uri = LocalUriHandler.current
    val lib = rememberStatsLibraryIndex()
    fun open(a: TopItem) {
        val local = lib.artists[AlbumMatcher.norm(a.name).replace(" ", "")]
        when {
            local != null -> actions.openArtist(local)
            else -> actions.openOnlineArtistNamed(a.name, a.deezerId)
        }
    }
    val max = list.first().plays.coerceAtLeast(1)
    StatsCard(S.topArtistsTitle, tag = "stats_artists") {
        // El número 1, en grande.
        val top = list.first()
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(SurcoColors.SurfaceHigh).clickable { open(top) }) {
            if (top.imageUrl != null) AsyncImage(top.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Person, null, tint = SurcoColors.TextTertiary, modifier = Modifier.size(64.dp).align(Alignment.Center))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("#1", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(SurcoColors.Accent).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.height(6.dp))
                Text(top.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(S.playsN(fmt(top.plays)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(8.dp))
        list.drop(1).forEachIndexed { i, a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(a) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 2}", style = MaterialTheme.typography.titleSmall, color = SurcoColors.TextSecondary, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(SurcoColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    if (a.imageUrl != null) AsyncImage(a.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.Person, null, tint = SurcoColors.TextTertiary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(fmt(a.plays), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(a.plays.toFloat() / max)
                }
            }
        }
    }
}

@Composable
private fun TopAlbumsRow(list: List<TopItem>) {
    val actions = LocalActions.current
    val uri = LocalUriHandler.current
    val lib = rememberStatsLibraryIndex()
    Column(Modifier.padding(vertical = 8.dp).testTag("stats_albums")) {
        Text(S.topAlbumsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(list) { i, a ->
                Column(
                    Modifier.width(140.dp).clickable {
                        val key = lib.albums[StatsMath.key(a.artist, a.name)]
                        if (key != null) actions.openAlbum(key) else actions.openOnlineAlbum(a.artist, a.name)
                    },
                ) {
                    Box {
                        dev.surco.ui.components.ArtImage(a.imageUrl, Modifier.size(140.dp), RoundedCornerShape(8.dp))
                        Text(
                            "${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black,
                            modifier = Modifier.padding(6.dp).size(26.dp).clip(CircleShape).background(SurcoColors.Accent).padding(top = 3.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(S.playsN(fmt(a.plays)), style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun TopTracksCard(list: List<TopItem>) {
    val actions = LocalActions.current
    val uri = LocalUriHandler.current
    val lib = rememberStatsLibraryIndex()
    val max = list.first().plays.coerceAtLeast(1)
    StatsCard(S.topTracksTitle, tag = "stats_tracks") {
        list.forEachIndexed { i, t ->
            val local = lib.tracks[StatsMath.key(t.artist, t.name)]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    if (local != null) actions.play(listOf(local), 0, "estadísticas|${S.stats}")
                    else actions.openOnlineTrackAlbum(t.artist, t.name)
                }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = SurcoColors.TextSecondary, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                if (local != null) {
                    dev.surco.ui.components.ArtImage(local.artPath, Modifier.size(40.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (local != null) SurcoColors.TextPrimary else SurcoColors.TextPrimary.copy(alpha = 0.85f))
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { actions.openArtistAnywhere(lib, t.artist) })
                    Spacer(Modifier.height(5.dp))
                    Meter(t.plays.toFloat() / max)
                }
                Spacer(Modifier.width(12.dp))
                Text(fmt(t.plays), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            }
        }
    }
}

// ----------------------------------------------------------------------- ADN musical

@Composable
private fun GenresCard(r: StatsReport) {
    StatsCard(S.genresTitle, S.genresSub, tag = "stats_genres") {
        val max = r.genres.maxOf { it.share }.coerceAtLeast(0.0001f)
        r.genres.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(g.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SurcoColors.SurfaceHigher)) {
                    Box(Modifier.fillMaxWidth(g.share / max).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SurcoColors.Accent))
                }
                Text("${(g.share * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

// ----------------------------------------------------------------------- Datos curiosos

@Composable
private fun FactsGrid(r: StatsReport) {
    val facts = buildList {
        if (r.top5Share > 0f) add(Fact(Icons.Filled.Stars, S.factLoyalty, "${(r.top5Share * 100).roundToInt()}%", S.factLoyaltyText))
        if (r.uniqueArtists > 0) add(Fact(Icons.Filled.TravelExplore, S.factVariety, fmt(r.uniqueArtists.toLong()), S.factVarietyText))
        r.obsession?.let { o -> add(Fact(Icons.Filled.Repeat, S.factObsession, "×${o.count}", S.factObsessionText(o.title, o.artist, dayLabel(o.day)))) }
        r.marathon?.let { m -> add(Fact(Icons.Filled.Timer, S.factMarathon, S.hoursMinutes(m.minutes), S.factMarathonText(m.tracks, dayLabel(m.day)))) }
        r.inLibraryShare?.let { add(Fact(Icons.Filled.LibraryMusic, S.factLibrary, "${(it * 100).roundToInt()}%", S.factLibraryText)) }
        if (r.lovedTracks > 0) add(Fact(Icons.Filled.Favorite, S.factLoved, fmt(r.lovedTracks.toLong()), S.factLovedText))
    }
    if (facts.isEmpty()) return
    Column(Modifier.padding(vertical = 8.dp).testTag("stats_facts")) {
        Text(S.factsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        facts.chunked(2).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f -> FactCard(f, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class Fact(val icon: ImageVector, val label: String, val value: String, val text: String)

@Composable
private fun FactCard(f: Fact, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = 150.dp).clip(RoundedCornerShape(16.dp)).background(SurcoColors.Surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(SurcoColors.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(f.icon, null, tint = SurcoColors.Accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(f.label, style = MaterialTheme.typography.labelLarge, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Text(f.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(f.text, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Footer(r: StatsReport) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!r.sampleComplete) {
            Text(S.sampleNote(fmt(r.sampled.toLong())), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        Text(S.dataFromLastfm, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary)
        if (r.profile.url.isNotBlank()) {
            TextButton(onClick = { runCatching { uri.openUri(r.profile.url) } }) { Text(S.viewLastfmProfile, color = SurcoColors.Accent) }
        }
    }
}

// ======================================================================= Formato

/** 48213 → «48.213» (español) o «48,213» (inglés). */
internal fun fmtPlays(n: Long): String = fmt(n)

private fun fmt(n: Long): String {
    val sep = if (I18n.lang == Lang.ES) '.' else ','
    val s = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    s.forEachIndexed { i, ch -> if (i > 0 && (s.length - i) % 3 == 0) out.append(sep); out.append(ch) }
    return (if (n < 0) "-" else "") + out
}

/** 1200 → «1,2 K». */
private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "${(n / 100_000) / 10.0}M".replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "${(n / 100) / 10.0}K".replace(".0K", "K")
    else -> n.toString()
}.let { if (I18n.lang == Lang.ES) it.replace('.', ',') else it }

private fun hh(h: Int) = (if (h < 10) "0$h" else "$h") + ":00"

private fun dayLabel(day: Long): String = StatsMath.civil(day).let { (_, m, d) -> S.dayMonth(d, m) }

private fun shortDate(day: Long, bucketDays: Int): String = StatsMath.civil(day).let { (y, m, d) ->
    if (bucketDays >= 30) S.monthYear(m, y) else S.dayMonth(d, m)
}

private fun bucketLabel(b: ActivityBucket, bucketDays: Int): String = when (bucketDays) {
    1 -> StatsMath.weekday(b.startDay).let { S.weekdayLong(it) } + " " + dayLabel(b.startDay)
    7 -> S.weekOf(dayLabel(b.startDay))
    else -> StatsMath.civil(b.startDay).let { (y, m, _) -> S.monthLong(m).replaceFirstChar { it.titlecase() } + " $y" }
}

