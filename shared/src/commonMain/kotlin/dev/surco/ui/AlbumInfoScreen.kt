package dev.surco.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.surco.data.AlbumCandidate
import dev.surco.data.AlbumInfo
import dev.surco.data.AlbumInfoChoice
import dev.surco.data.AlbumMatcher
import dev.surco.data.Track
import dev.surco.data.TrackPlan
import dev.surco.data.UNKNOWN_ALBUM
import dev.surco.data.UNKNOWN_ARTIST
import dev.surco.i18n.S
import io.ktor.http.decodeURLPart
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Rellenar los datos de un álbum desde internet: se busca por álbum y artista, se elige la edición
 * correcta y se ve qué cambiaría en cada canción antes de escribir nada en los archivos.
 */
@Composable
fun AlbumInfoScreen(albumKey: String) {
    val actions = LocalActions.current
    val repo = actions.repo
    val search = actions.container.metadata
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var hasCover by remember { mutableStateOf(false) }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AlbumCandidate>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loadingDetail by remember { mutableStateOf<AlbumCandidate?>(null) }
    var info by remember { mutableStateOf<AlbumInfo?>(null) }

    fun runSearch() {
        searching = true
        actions.scope.launch {
            results = search.search(artist.trim(), album.trim())
            searching = false
        }
    }

    LaunchedEffect(albumKey) {
        val a = repo.dao.album(albumKey).first()
        tracks = repo.dao.albumTracksOnce(albumKey)
        hasCover = a?.artPath != null
        artist = a?.artist?.takeIf { it != UNKNOWN_ARTIST }.orEmpty()
        album = a?.album?.takeIf { it != UNKNOWN_ALBUM }.orEmpty()
        // Sin nombre de álbum, el de la carpeta es la mejor pista.
        if (album.isBlank()) album = tracks.firstOrNull()?.let { folderGuess(it) }.orEmpty()
        if (album.isNotBlank()) runSearch() else results = emptyList()
    }

    val current = info
    if (current != null) {
        AlbumInfoPreview(albumKey, tracks, current, hasCover, onBack = { info = null })
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(S.fillFromInternet) { actions.nav.popBackStack() }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(S.fillFromInternetHelp, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            InfoField(S.album, album, { album = it }, Modifier.testTag("info_album"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { InfoField(S.artist, artist, { artist = it }, onDone = { runSearch() }) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { runSearch() },
                    enabled = album.isNotBlank() && !searching,
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                    modifier = Modifier.testTag("info_search"),
                ) { Icon(Icons.Filled.Search, S.search) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val list = results
        when {
            searching || list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SurcoColors.Accent)
            }
            list.isEmpty() -> Text(
                S.noAlbumsFound, color = SurcoColors.TextSecondary,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(
                        S.pickTheEdition(tracks.size), style = MaterialTheme.typography.bodySmall,
                        color = SurcoColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(list, key = { it.source + it.id }) { c ->
                    CandidateRow(c, localCount = tracks.size, loading = loadingDetail == c) {
                        if (loadingDetail != null) return@CandidateRow
                        loadingDetail = c
                        actions.scope.launch {
                            val d = search.details(c)
                            loadingDetail = null
                            if (d == null || d.tracks.isEmpty()) actions.toast(S.couldNotLoadAlbumInfo) else info = d
                        }
                    }
                }
            }
        }
    }
}

/** Nombre de la carpeta del álbum (sin «Artista - » ni el año entre paréntesis) como sugerencia. */
private fun folderGuess(t: Track): String {
    val folder = t.folderId.decodeURLPart().trimEnd('/').substringAfterLast('/').substringAfterLast(':')
    return folder.substringAfter(" - ", folder).replace(Regex("""\s*[(\[]\d{4}[)\]]\s*"""), " ").trim()
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary) }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Search else ImeAction.Next),
        keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SurcoColors.Accent, focusedLabelColor = SurcoColors.Accent,
            cursorColor = SurcoColors.Accent, unfocusedBorderColor = SurcoColors.SurfaceHigher,
        ),
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun RemoteArt(url: String?, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(SurcoColors.SurfaceHigh),
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Album, null, tint = SurcoColors.TextTertiary) } },
    )
}

@Composable
private fun CandidateRow(c: AlbumCandidate, localCount: Int, loading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("candidate"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteArt(c.thumbUrl, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(c.artist, c.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(c.source, S.songs(c.trackCount), c.detail).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (c.trackCount == localCount) SurcoColors.Accent else SurcoColors.TextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
    }
}

// ================================================================ Vista previa y aplicar

@Composable
private fun AlbumInfoPreview(albumKey: String, tracks: List<Track>, info: AlbumInfo, hasCover: Boolean, onBack: () -> Unit) {
    val actions = LocalActions.current
    val repo = actions.repo
    val compilation = remember(info) { info.tracks.map { AlbumMatcher.norm(it.artist) }.distinct().size > 1 }
    var choice by remember(info) {
        mutableStateOf(AlbumInfoChoice(cover = !hasCover && info.coverUrl != null, trackArtists = compilation))
    }
    val plans = remember(info, choice) { AlbumMatcher.plan(tracks, info, choice) }
    val matched = plans.count { it.remote != null }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val first = tracks.firstOrNull()
    val changes = plans.count { it.hasChanges }

    Column(Modifier.fillMaxSize()) {
        TopBar(S.reviewChanges, onBack = { if (!saving) onBack() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteArt(info.candidate.thumbUrl ?: info.coverUrl, Modifier.size(72.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(info.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(info.artist, info.year.takeIf { it > 0 }?.toString(), info.genre.ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                        )
                        Text(S.matchedOf(matched, tracks.size), style = MaterialTheme.typography.labelSmall, color = SurcoColors.Accent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { choice = choice.copy(onlyEmpty = !choice.onlyEmpty) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(S.onlyFillEmpty, style = MaterialTheme.typography.titleSmall)
                        Text(S.onlyFillEmptyDesc, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                    }
                    Switch(
                        checked = choice.onlyEmpty, onCheckedChange = { choice = choice.copy(onlyEmpty = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = SurcoColors.Accent),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Check(S.album, diff(first?.album, info.title), choice.album) { choice = choice.copy(album = it) }
                Check(S.albumArtist, diff(first?.albumArtist, info.artist), choice.albumArtist) { choice = choice.copy(albumArtist = it) }
                Check(S.year, diff(first?.year?.takeIf { it > 0 }?.toString(), info.year.takeIf { it > 0 }?.toString()), choice.year) { choice = choice.copy(year = it) }
                Check(S.genre, diff(first?.genre, info.genre), choice.genre) { choice = choice.copy(genre = it) }
                Check(S.songTitles, null, choice.titles) { choice = choice.copy(titles = it) }
                Check(S.trackAndDiscNumbers, null, choice.numbers) { choice = choice.copy(numbers = it) }
                Check(S.trackArtists, null, choice.trackArtists) { choice = choice.copy(trackArtists = it) }
                if (info.coverUrl != null) {
                    Check(S.cover, if (hasCover) S.replacesCurrentCover else S.albumHasNoCover, choice.cover) { choice = choice.copy(cover = it) }
                }
                Spacer(Modifier.height(10.dp))
                Text(S.songs(tracks.size), style = MaterialTheme.typography.titleMedium)
            }
            items(plans, key = { it.track.uri }) { p -> PlanRow(p) }
        }
        Column(Modifier.fillMaxWidth().background(SurcoColors.Surface).navigationBarsPadding().padding(16.dp)) {
            if (progress.isNotEmpty()) {
                Text(progress, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = {
                    saving = true
                    actions.scope.launch {
                        try {
                            val cover = if (choice.cover) {
                                info.coverUrl?.let { repo.covers.download(it) } ?: info.coverFallbackUrl?.let { repo.covers.download(it) }
                            } else null
                            val (newKey, errors) = repo.applyAlbumInfo(albumKey, plans, cover) { d, t -> progress = S.savingProgress(d, t) }
                            actions.toast(if (errors.isEmpty()) S.albumUpdated else S.couldNotEditSomeCount(errors.size))
                            // Fuera esta pantalla; si cambió la clave del álbum, también la del álbum antiguo.
                            actions.nav.popBackStack()
                            if (newKey != albumKey) {
                                actions.nav.popBackStack()
                                actions.openAlbum(newKey)
                            }
                        } catch (e: Exception) {
                            actions.toast(e.message ?: S.couldNotSaveShort)
                        } finally {
                            saving = false
                            progress = ""
                        }
                    }
                },
                enabled = !saving && (changes > 0 || choice.cover),
                colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("info_apply"),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = SurcoColors.OnAccent, strokeWidth = 2.dp)
                else Text(if (changes > 0) S.applyToSongs(changes) else S.applyCoverOnly)
            }
        }
    }
}

private fun diff(old: String?, new: String?): String? {
    val o = old.orEmpty().takeIf { it != UNKNOWN_ALBUM && it != UNKNOWN_ARTIST }.orEmpty()
    val n = new.orEmpty()
    return when {
        n.isBlank() -> "—"
        o == n -> n
        o.isBlank() -> "→ $n"
        else -> "$o → $n"
    }
}

@Composable
private fun Check(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = SurcoColors.Accent, checkmarkColor = SurcoColors.OnAccent))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlanRow(p: TrackPlan) {
    val r = p.remote
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.track.fileName, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r == null) {
                Text(S.noMatchKeepsTitle(p.track.title), style = MaterialTheme.typography.bodyMedium, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                val number = (p.trackNumber ?: p.track.trackNumber.takeIf { it > 0 }?.toString())?.let { "$it. " }.orEmpty()
                Text(
                    number + (p.title ?: p.track.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p.hasChanges) SurcoColors.TextPrimary else SurcoColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                p.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1) }
            }
        }
        if (p.hasChanges) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(SurcoColors.Accent))
        }
    }
}
