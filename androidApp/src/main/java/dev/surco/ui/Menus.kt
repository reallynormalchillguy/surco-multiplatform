package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.Track
import dev.surco.data.qualityLabel
import dev.surco.ui.components.ArtImage
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: Track, playlistId: Long?, onDismiss: () -> Unit, onNavigate: () -> Unit) {
    val actions = LocalActions.current
    val liked = track.uri in LocalLiked.current

    // Siempre abierto del todo: a media altura, las últimas opciones quedaban fuera de la pantalla.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(track.artPath, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} • ${track.album}", style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.qualityLabel(), style = MaterialTheme.typography.labelSmall, color = SurcoColors.Accent, maxLines = 1)
                }
            }
            HorizontalDivider(color = SurcoColors.SurfaceHigh, modifier = Modifier.padding(vertical = 6.dp))

            MenuItem(
                if (liked) Icons.Filled.CheckCircle else Icons.Outlined.AddCircleOutline,
                if (liked) S.removeFromLiked else S.addToLiked,
                tint = if (liked) SurcoColors.Accent else SurcoColors.TextSecondary,
            ) { actions.toggleLike(track.uri, liked); onDismiss() }
            MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, S.addToPlaylist) { onDismiss(); actions.addToPlaylist(listOf(track)) }
            MenuItem(Icons.AutoMirrored.Filled.PlaylistPlay, S.playNext) {
                actions.player.playNext(track); actions.toast(S.willPlayNext); onDismiss()
            }
            MenuItem(Icons.AutoMirrored.Filled.QueueMusic, S.addToQueue) {
                actions.player.enqueue(listOf(track)); actions.toast(S.addedToQueue); onDismiss()
            }
            if (playlistId != null) {
                MenuItem(Icons.Filled.RemoveCircleOutline, S.removeFromPlaylist) {
                    actions.scope.launch { actions.repo.dao.removeEntry(playlistId, track.uri) }
                    onDismiss()
                }
            }
            MenuItem(Icons.Filled.Album, S.goToAlbum) { onDismiss(); onNavigate(); actions.openAlbum(track.albumKey) }
            MenuItem(Icons.Filled.Person, S.goToArtist) { onDismiss(); onNavigate(); actions.openArtist(track.groupArtist) }
            MenuItem(Icons.Filled.Edit, S.editTags) { onDismiss(); onNavigate(); actions.editTrack(track.uri) }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = SurcoColors.TextSecondary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val playlists by remember { actions.repo.dao.playlists() }.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = S.newPlaylist,
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                actions.scope.launch {
                    val id = actions.repo.createPlaylist(name)
                    actions.repo.addToPlaylist(id, tracks.map { it.uri })
                    actions.toast(S.addedTo(name.ifBlank { S.newPlaylist }))
                }
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        title = { Text(S.addToPlaylist) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = SurcoColors.Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(S.newPlaylist, color = SurcoColors.Accent, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            actions.scope.launch {
                                actions.repo.addToPlaylist(p.id, tracks.map { it.uri })
                                actions.toast(S.addedTo(p.name))
                            }
                            onDismiss()
                        }.padding(vertical = 12.dp),
                    ) {
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(S.songs(p.trackCount), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurcoColors.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(S.name) })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(S.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}
