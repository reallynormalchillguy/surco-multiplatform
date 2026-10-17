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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.surco.data.Track
import dev.surco.i18n.S
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.surco.ui.components.ArtImage
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================================================================ Menú de álbum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumMenuSheet(albumKey: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val dao = actions.repo.dao
    val album by remember(albumKey) { dao.album(albumKey) }.collectAsState(initial = null)
    val tracks by remember(albumKey) { dao.albumTracks(albumKey) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val a = album

    if (confirmDelete && a != null) {
        DeleteAlbumDialogs(albumKey, a.album, a.artist, tracks, onDone = { confirmDelete = false; onDismiss() })
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(a?.artPath, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a?.album ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(a?.artist, S.songs(tracks.size)).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1,
                    )
                }
            }
            HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 6.dp))
            SheetItem(Icons.AutoMirrored.Filled.PlaylistPlay, S.playNext, "album_play_next") {
                actions.player.playNext(tracks); actions.toast(S.albumWillPlayNext); onDismiss()
            }
            SheetItem(Icons.AutoMirrored.Filled.QueueMusic, S.addAlbumToQueue, "album_enqueue") {
                actions.player.enqueue(tracks); actions.toast(S.albumAddedToQueue(tracks.size)); onDismiss()
            }
            SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, S.addToPlaylist) { onDismiss(); actions.addToPlaylist(tracks) }
            SheetItem(Icons.Filled.Edit, S.editAlbum) { onDismiss(); actions.editAlbum(albumKey) }
            SheetItem(Icons.Filled.AutoAwesome, S.fillFromInternet, "album_fill_info") { onDismiss(); actions.albumInfo(albumKey) }
            SheetItem(Icons.Filled.ImageSearch, S.findCover) { onDismiss(); actions.searchCovers(albumKey) }
            a?.let { al ->
                SheetItem(Icons.Filled.Share, S.share, "album_share") { onDismiss(); actions.shareAlbum(al) }
                SheetItem(Icons.Filled.Person, S.goToArtist) { onDismiss(); actions.openArtist(al.artist) }
            }
            HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 6.dp))
            SheetItem(Icons.Filled.DeleteForever, S.deleteAlbum, "album_delete", tint = SurcoColors.Error) {
                if (tracks.isNotEmpty()) confirmDelete = true
            }
        }
    }
}

@Composable
fun SheetItem(
    icon: ImageVector,
    text: String,
    tag: String? = null,
    tint: Color = SurcoColors.TextSecondary,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (tint == SurcoColors.Error) SurcoColors.Error else Color.Unspecified)
    }
}

// ================================================================ Borrar álbum (dos confirmaciones)

/**
 * Borrar un álbum borra sus archivos del dispositivo, para siempre. Por eso hay dos avisos
 * seguidos, y el botón del segundo no se activa hasta pasado un momento: un doble toque
 * accidental no puede atravesar los dos.
 */
@Composable
fun DeleteAlbumDialogs(
    albumKey: String,
    albumName: String,
    artist: String,
    tracks: List<Track>,
    onDone: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val actions = LocalActions.current
    var step by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    when (step) {
        1 -> AlertDialog(
            onDismissRequest = onDone,
            containerColor = SurcoColors.Surface,
            title = { Text(S.deleteAlbumQuestion(albumName)) },
            text = { Text(S.deleteAlbumWarning(tracks.size)) },
            confirmButton = {
                TextButton(onClick = { step = 2 }, modifier = Modifier.testTag("delete_step1")) {
                    Text(S.delete, color = SurcoColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = onDone) { Text(S.cancel) } },
        )
        else -> {
            var wait by remember { mutableIntStateOf(3) }
            LaunchedEffect(Unit) { while (wait > 0) { delay(700); wait-- } }
            AlertDialog(
                onDismissRequest = { if (!busy) onDone() },
                containerColor = SurcoColors.Surface,
                title = { Text(S.deleteAlbumSure) },
                text = {
                    Column {
                        Text(S.deleteAlbumFinal(tracks.size, albumName, artist))
                        val folders = tracks.map { it.folderId }.distinct().size
                        if (folders > 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(S.albumSpansFolders(folders), color = SurcoColors.Error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (busy) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = SurcoColors.Error, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(S.deleting, color = SurcoColors.TextSecondary)
                            }
                        }
                    }
                },
                // Los botones cambian de lado respecto al primer aviso: otro motivo para no confirmar sin querer.
                dismissButton = {
                    TextButton(
                        enabled = !busy && wait == 0,
                        modifier = Modifier.testTag("delete_step2"),
                        onClick = {
                            busy = true
                            actions.scope.launch {
                                try {
                                    // Sin cancelación: si la pantalla se recrea a mitad, la biblioteca debe quedar al día
                                    // con lo que ya se borró del dispositivo.
                                    val r = withContext(NonCancellable) { actions.repo.deleteAlbum(albumKey) }
                                    actions.player.removeFromQueue(r.deleted)
                                    actions.toast(
                                        if (r.failed.isEmpty()) S.albumDeleted(albumName)
                                        else S.albumDeletedPartly(r.deleted.size, r.failed.size)
                                    )
                                    if (r.deleted.isNotEmpty()) onDeleted()
                                } catch (e: Exception) {
                                    actions.toast(e.message ?: S.couldNotDelete)
                                } finally {
                                    busy = false
                                    onDone()
                                }
                            }
                        },
                    ) { Text(if (wait > 0) "${S.deleteForever} ($wait)" else S.deleteForever, color = SurcoColors.Error) }
                },
                confirmButton = { TextButton(onClick = onDone, enabled = !busy) { Text(S.keepAlbum) } },
            )
        }
    }
}

// ================================================================ Foto de artista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPictureSheet(name: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val repo = actions.repo
    var choosingOnline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    LaunchedEffect(name) { custom = repo.isCustomArtistImage(repo.dao.artistImage(name)) }

    val pick = rememberImagePicker { bytes ->
        busy = true
        actions.scope.launch {
            val ok = bytes != null && repo.setArtistImage(name, bytes)
            actions.toast(if (ok) S.artistPhotoUpdated else S.couldNotReadImage)
            busy = false
            onDismiss()
        }
    }

    if (choosingOnline) {
        OnlineArtistPhotos(name, onDismiss = { choosingOnline = false; onDismiss() })
        return
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SurcoColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                S.artistPhotoOf(name), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (busy) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(S.saving, color = SurcoColors.TextSecondary)
                }
            } else {
                SheetItem(Icons.Filled.PhotoLibrary, S.chooseFromGallery, "artist_photo_gallery") {
                    pick()
                }
                SheetItem(Icons.Filled.ImageSearch, S.searchOnline, "artist_photo_online") { choosingOnline = true }
                if (custom) {
                    SheetItem(Icons.Filled.Refresh, S.useAutomaticPhoto) {
                        busy = true
                        actions.scope.launch {
                            val found = repo.resetArtistImage(name)
                            actions.toast(if (found) S.artistPhotoUpdated else S.noArtistPhotoFound)
                            busy = false
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineArtistPhotos(name: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val repo = actions.repo
    val finder = actions.container.artistPhotos
    var results by remember { mutableStateOf<List<dev.surco.net.ArtistPhoto>>(emptyList()) }
    var pending by remember { mutableStateOf(finder.sources.size) }
    var saving by remember { mutableStateOf(false) }
    // Todas las fuentes a la vez; cada una añade sus fotos en cuanto llegan.
    LaunchedEffect(name) {
        kotlinx.coroutines.coroutineScope {
            finder.sources.forEach { source ->
                launch {
                    val found = runCatching { source(name) }.getOrDefault(emptyList())
                    results = (results + found).distinctBy { it.full }
                    pending--
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = SurcoColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.chooseAPhoto, modifier = Modifier.weight(1f))
                if (pending > 0 && !saving) CircularProgressIndicator(Modifier.size(18.dp), color = SurcoColors.Accent, strokeWidth = 2.dp)
            }
        },
        text = {
            val list = results
            when {
                saving || (list.isEmpty() && pending > 0) -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SurcoColors.Accent)
                }
                list.isEmpty() -> Text(S.noArtistPhotoFound, color = SurcoColors.TextSecondary)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(92.dp),
                    modifier = Modifier.heightIn(max = 460.dp).testTag("artist_photo_grid"),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.full }) { p ->
                        Column(
                            Modifier.clickable {
                                saving = true
                                actions.scope.launch {
                                    val bytes = repo.covers.download(p.full) ?: repo.covers.download(p.thumb)
                                    val ok = bytes != null && repo.setArtistImage(name, bytes)
                                    actions.toast(if (ok) S.artistPhotoUpdated else S.couldNotDownloadImage)
                                    saving = false
                                    onDismiss()
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                p.thumb, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(SurcoColors.SurfaceHigh),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(p.source, style = MaterialTheme.typography.labelSmall, color = SurcoColors.TextTertiary, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(S.cancel) } },
    )
}
