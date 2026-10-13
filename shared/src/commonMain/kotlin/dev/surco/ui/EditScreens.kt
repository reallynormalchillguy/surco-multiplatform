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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.surco.data.Track
import dev.surco.net.CoverResult
import dev.surco.tags.TagEdits
import dev.surco.ui.components.ArtImage
import dev.surco.ui.theme.SurcoColors
import dev.surco.util.IoDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================================================================ Piezas comunes

@Composable
private fun EditorTopBar(title: String, saving: Boolean, canSave: Boolean, onSave: () -> Unit) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { actions.nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (saving) {
            CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(16.dp))
        } else {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(S.save, color = if (canSave) SurcoColors.Accent else SurcoColors.TextTertiary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String? = null, number: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder, color = SurcoColors.TextTertiary) }
        } else null,
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SurcoColors.Accent,
            focusedLabelColor = SurcoColors.Accent,
            cursorColor = SurcoColors.Accent,
            unfocusedBorderColor = SurcoColors.SurfaceHigher,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun ErrorsDialog(errors: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        title = { Text(S.couldNotEditSome) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.ok) } },
    )
}

// ================================================================ Editar canción

@Composable
fun TrackEditorScreen(uri: String) {
    val actions = LocalActions.current
    var track by remember { mutableStateOf<Track?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var trackNo by remember { mutableStateOf("") }
    var discNo by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val t = actions.repo.dao.track(uri) ?: return@LaunchedEffect
        track = t
        title = t.title; artist = t.artist; album = t.album; albumArtist = t.albumArtist
        year = if (t.year > 0) t.year.toString() else ""
        trackNo = if (t.trackNumber > 0) t.trackNumber.toString() else ""
        discNo = if (t.discNumber > 0) t.discNumber.toString() else ""
        genre = t.genre
    }

    val t = track
    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(S.editTags, saving, canSave = t != null) {
            val tr = t ?: return@EditorTopBar
            fun changed(new: String, old: String) = if (new.trim() != old) new.trim() else null
            val edits = TagEdits(
                title = changed(title, tr.title),
                artist = changed(artist, tr.artist),
                album = changed(album, tr.album),
                albumArtist = changed(albumArtist, tr.albumArtist),
                year = changed(year, if (tr.year > 0) tr.year.toString() else ""),
                trackNumber = changed(trackNo, if (tr.trackNumber > 0) tr.trackNumber.toString() else ""),
                discNumber = changed(discNo, if (tr.discNumber > 0) tr.discNumber.toString() else ""),
                genre = changed(genre, tr.genre),
            )
            if (edits == TagEdits()) {
                actions.nav.popBackStack(); return@EditorTopBar
            }
            saving = true
            actions.scope.launch {
                try {
                    actions.repo.editTrack(tr, edits)
                    actions.toast(S.tagsSaved)
                    actions.nav.popBackStack()
                } catch (e: Exception) {
                    actions.toast(e.message ?: S.couldNotSaveShort)
                } finally {
                    saving = false
                }
            }
        }
        if (t == null) return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                ArtImage(t.artPath, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(S.coverIsEditedFromAlbum, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                }
            }
            Field(S.title, title, { title = it })
            Field(S.artist, artist, { artist = it })
            Field(S.album, album, { album = it })
            Field(S.albumArtist, albumArtist, { albumArtist = it })
            Row {
                Box(Modifier.weight(1f)) { Field(S.year, year, { year = it }, number = true) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Field(S.track, trackNo, { trackNo = it }, number = true) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Field(S.disc, discNo, { discNo = it }, number = true) }
            }
            Field(S.genre, genre, { genre = it })
            Spacer(Modifier.height(120.dp))
        }
    }
}

// ================================================================ Editar álbum

@Composable
fun AlbumEditorScreen(albumKey: String) {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val album by remember(albumKey) { dao.album(albumKey) }.collectAsState(initial = null)
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var mixed by remember { mutableStateOf(setOf<String>()) }
    var original by remember { mutableStateOf(mapOf<String, String>()) }
    /** null = sin cambios; vacío = quitar; bytes = nueva portada (JPEG) */
    var newCover by remember { mutableStateOf<ByteArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(albumKey) {
        val list = dao.albumTracksOnce(albumKey)
        tracks = list
        fun common(sel: (Track) -> String): String? = list.map(sel).distinct().singleOrNull()
        val values = mapOf(
            "album" to common { it.album },
            "albumArtist" to common { it.albumArtist },
            "year" to common { if (it.year > 0) it.year.toString() else "" },
            "genre" to common { it.genre },
        )
        mixed = values.filterValues { it == null }.keys
        original = values.mapValues { it.value ?: "" }
        name = original["album"] ?: ""
        albumArtist = original["albumArtist"] ?: ""
        year = original["year"] ?: ""
        genre = original["genre"] ?: ""
        // Si no hay artista de álbum, proponemos el del grupo para que el álbum quede bien agrupado.
        if (albumArtist.isBlank() && "albumArtist" !in mixed) albumArtist = list.firstOrNull()?.groupArtist ?: ""
    }

    val pickImage = rememberImagePicker { bytes ->
        actions.scope.launch {
            val jpeg = bytes?.let { withContext(IoDispatcher) { actions.repo.normalizeImage(it) } }
            if (jpeg != null) newCover = jpeg else actions.toast(S.couldNotReadImage)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(S.editAlbum, saving, canSave = tracks.isNotEmpty()) {
            fun changed(key: String, v: String): String? {
                if (key in mixed && v.isBlank()) return null
                return if (v.trim() != original[key]) v.trim() else null
            }
            val edits = TagEdits(
                album = changed("album", name),
                albumArtist = changed("albumArtist", albumArtist),
                year = changed("year", year),
                genre = changed("genre", genre),
                cover = newCover,
            )
            if (edits == TagEdits()) {
                actions.nav.popBackStack(); return@EditorTopBar
            }
            saving = true
            actions.scope.launch {
                try {
                    val (newKey, errs) = actions.repo.editAlbum(albumKey, edits) { done, total -> progress = S.savingProgress(done, total) }
                    if (errs.isNotEmpty()) {
                        errors = errs
                    } else {
                        actions.toast(S.albumUpdated)
                        actions.nav.popBackStack()
                        if (newKey != albumKey) {
                            actions.nav.popBackStack()
                            actions.openAlbum(newKey)
                        }
                    }
                } catch (e: Exception) {
                    actions.toast(e.message ?: S.couldNotSaveShort)
                } finally {
                    saving = false
                    progress = ""
                }
            }
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // ---- Portada
            Box(
                Modifier.padding(horizontal = 48.dp, vertical = 8.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val cover = newCover
                when {
                    cover != null && cover.isNotEmpty() -> AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    cover != null -> ArtImage(null, Modifier.fillMaxSize())
                    else -> ArtImage(album?.artPath, Modifier.fillMaxSize())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                OutlinedButton(onClick = { actions.searchCovers(albumKey) }, enabled = !saving) {
                    Icon(Icons.Filled.ImageSearch, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.internet)
                }
                OutlinedButton(
                    onClick = { pickImage() },
                    enabled = !saving,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.gallery)
                }
                IconButton(onClick = { newCover = ByteArray(0) }, enabled = !saving) {
                    Icon(Icons.Filled.Delete, S.removeCover, tint = SurcoColors.TextSecondary)
                }
            }
            if (newCover != null) {
                Text(
                    if (newCover!!.isEmpty()) S.coverWillBeRemoved
                    else S.coverWillBeEmbedded(tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = SurcoColors.Accent,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Field(S.album, name, { name = it }, placeholder = if ("album" in mixed) S.various else null)
            Field(S.albumArtist, albumArtist, { albumArtist = it }, placeholder = if ("albumArtist" in mixed) S.various else null)
            Row {
                Box(Modifier.weight(1f)) { Field(S.year, year, { year = it }, placeholder = if ("year" in mixed) S.various else null, number = true) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(2f)) { Field(S.genre, genre, { genre = it }, placeholder = if ("genre" in mixed) S.various else null) }
            }
            Text(
                if (progress.isNotEmpty()) progress else S.changesApplyTo(tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = SurcoColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(120.dp))
        }
    }

    if (errors.isNotEmpty()) {
        ErrorsDialog(errors) {
            errors = emptyList()
            actions.nav.popBackStack()
        }
    }
}

// ================================================================ Buscar portada

@Composable
fun CoverSearchScreen(albumKey: String) {
    val actions = LocalActions.current
    val repo = actions.repo
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CoverResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<CoverResult?>(null) }
    var applying by remember { mutableStateOf(false) }
    val settings by repo.prefs.settings.collectAsState()
    var embed by remember { mutableStateOf(settings.embedCovers) }

    LaunchedEffect(albumKey) {
        val a = repo.dao.album(albumKey).first() ?: return@LaunchedEffect
        val artist = a.artist.takeIf { it != dev.surco.data.UNKNOWN_ARTIST } ?: ""
        query = listOf(artist, a.album).filter { it.isNotBlank() }.joinToString(" ")
        val all = repo.covers.search(artist, a.album)
        // Primero los que casan con el álbum, luego el resto.
        val good = repo.covers.matches(all, artist, a.album)
        results = (good + (all - good.toSet())).distinctBy { it.fullUrl }
        loading = false
    }

    fun runSearch() {
        loading = true
        actions.scope.launch {
            results = repo.covers.searchFree(query).distinctBy { it.fullUrl }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = Color.White)
            }
            Text(S.findCover, style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field(S.search, query, { query = it }) }
            IconButton(onClick = { runSearch() }) { Icon(Icons.Filled.Search, S.search, tint = Color.White) }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SurcoColors.Accent)
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(S.noCoversFound, color = SurcoColors.TextSecondary)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(results, key = { it.fullUrl }) { r ->
                    Column(Modifier.clickable { selected = r }) {
                        AsyncImage(
                            r.thumbUrl, null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(SurcoColors.SurfaceHigh),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.artist} · ${r.source}", style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    selected?.let { r ->
        AlertDialog(
            onDismissRequest = { if (!applying) selected = null },
            containerColor = SurcoColors.Surface,
            title = { Text(S.useThisCover) },
            text = {
                Column {
                    AsyncImage(
                        r.thumbUrl, null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.embedInFiles, modifier = Modifier.weight(1f))
                        Switch(
                            checked = embed, onCheckedChange = { embed = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = SurcoColors.Accent),
                        )
                    }
                    if (applying) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.downloadingAndSaving, color = SurcoColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !applying,
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                    onClick = {
                        applying = true
                        actions.scope.launch {
                            val bytes = repo.covers.download(r.fullUrl) ?: repo.covers.download(r.thumbUrl)
                            if (bytes == null) {
                                actions.toast(S.couldNotDownloadImage)
                            } else {
                                val errs = repo.setAlbumCover(albumKey, bytes, embed)
                                actions.toast(if (errs.isEmpty()) S.coverSaved else S.coverSavedWithErrors(errs.size))
                                selected = null
                                actions.nav.popBackStack()
                            }
                            applying = false
                        }
                    },
                ) { Text(S.use) }
            },
            dismissButton = { TextButton(onClick = { selected = null }, enabled = !applying) { Text(S.cancel) } },
        )
    }
}
