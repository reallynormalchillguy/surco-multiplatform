package dev.surco.ui

import dev.surco.i18n.S
import dev.surco.util.fmt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.surco.data.formatDuration
import dev.surco.slsk.SlskFile
import dev.surco.slsk.SlskState
import dev.surco.slsk.Transfer
import dev.surco.slsk.TransferState
import dev.surco.slsk.UserResult
import dev.surco.ui.components.SurcoChip
import dev.surco.ui.theme.SurcoColors

/** Resultados agrupados por carpeta remota (normalmente, un álbum). */
private data class FolderGroup(
    val user: String,
    val folder: String,
    val files: List<SlskFile>,
    val slotFree: Boolean,
    val speed: Long,
    val queue: Long,
) {
    val key get() = "$user|$folder"
    val audio get() = files.filter { it.isAudio }
    val name get() = folder.substringAfterLast('\\').ifBlank { S.rootFolder }
    /** Carpeta superior, salvo la raíz compartida («@@music»), que no aporta nada. */
    val parent get() = folder.substringBeforeLast('\\', "").substringAfterLast('\\').takeUnless { it.startsWith("@@") }.orEmpty()
    val lossless get() = audio.isNotEmpty() && audio.all { it.lossless }
    /**
     * Calidad de una carpeta de MP3: el bitrate de su peor canción (una carpeta vale lo que su archivo más flojo).
     * Sin bitrate conocido, al final.
     */
    val mp3Quality: Int get() = audio.map { it.bitrate }.let { b -> if (b.isEmpty() || b.any { it <= 0 }) 0 else b.min() }
}

/** Filtro de formato. Se aplica a cada archivo: de una carpeta mixta sólo se ve (y se baja) lo que encaja. */
private enum class Format(val key: String) {
    ALL("all"), LOSSLESS("lossless"), FLAC("flac"), MP3("mp3");

    val label get() = when (this) { ALL -> S.formatAll; LOSSLESS -> S.formatLossless; FLAC -> "FLAC"; MP3 -> "MP3" }

    fun accepts(f: SlskFile) = when {
        !f.isAudio -> true // portadas y demás imágenes de la carpeta
        this == ALL -> true
        this == LOSSLESS -> f.lossless
        this == FLAC -> f.ext == "flac"
        else -> f.ext == "mp3"
    }

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: ALL
    }
}

private fun group(results: List<UserResult>, format: Format): List<FolderGroup> =
    results.flatMap { r ->
        r.files.filter { format.accepts(it) }.groupBy { it.folder }.map { (folder, files) ->
            FolderGroup(r.user, folder, files.sortedBy { it.name.lowercase() }, r.slotFree, r.avgSpeed, r.queueLength)
        }
    }
        // Un mismo usuario puede responder dos veces con la misma carpeta: se juntan (dos tarjetas con la
        // misma clave cerraban la app).
        .groupBy { it.key }
        .map { (_, same) -> if (same.size == 1) same[0] else same[0].copy(files = same.flatMap { it.files }.distinctBy { it.path }.sortedBy { it.name.lowercase() }) }
        .filter { it.audio.isNotEmpty() }
        .sortedWith(
            if (format == Format.MP3) {
                // MP3: primero la mejor calidad (320 → 256 → V0 → …); a igual calidad, como siempre.
                compareByDescending<FolderGroup> { it.mp3Quality }
                    .thenByDescending { it.slotFree }
                    .thenByDescending { it.audio.size >= 3 }
                    .thenByDescending { it.speed }
            } else {
                compareByDescending<FolderGroup> { it.slotFree }
                    .thenByDescending { it.audio.size >= 3 }
                    .thenByDescending { it.speed }
            }
        )

private fun qualityOf(files: List<SlskFile>): String {
    val audio = files.filter { it.isAudio }
    if (audio.isEmpty()) return ""
    val ext = audio.groupingBy { it.ext }.eachCount().maxByOrNull { it.value }!!.key.uppercase()
    val f = audio.first { it.ext.uppercase() == ext }
    return when {
        f.bitDepth > 0 && f.sampleRate > 0 -> "$ext · ${f.bitDepth}/${khz(f.sampleRate)}"
        f.lossless -> ext
        f.bitrate > 0 -> "$ext · ${audio.filter { it.bitrate > 0 }.minOf { it.bitrate }}${if (audio.any { it.vbr }) " VBR" else ""}"
        else -> ext
    }
}

private fun khz(hz: Int): String {
    val k = hz / 1000.0
    return if (k % 1.0 == 0.0) k.toInt().toString() else k.fmt(1)
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "${(b / (1L shl 30).toDouble()).fmt(1)} GB"
    b >= 1L shl 20 -> "${(b / (1L shl 20).toDouble()).fmt(0)} MB"
    b >= 1L shl 10 -> "${(b / 1024.0).fmt(0)} KB"
    else -> "$b B"
}

private fun formatSpeed(bps: Long) = if (bps <= 0) "" else "${formatBytes(bps)}/s"

@Composable
fun SoulseekScreen(initialQuery: String) {
    val actions = LocalActions.current
    val manager = actions.container.soulseek
    val client = manager.client
    val state by client.state.collectAsState()
    val settings by actions.repo.prefs.settings.collectAsState()
    val transfers by client.transfers.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { manager.ensureConnected() }

    val pickDir = rememberDirectoryPicker { root -> actions.repo.prefs.update { it.copy(slskDir = root) } }

    Column(Modifier.fillMaxSize()) {
        // ---- Cabecera
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Soulseek", style = MaterialTheme.typography.headlineMedium)
                StatusLine(state)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, S.options, tint = SurcoColors.TextPrimary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(S.downloadsFolder) }, onClick = { menu = false; pickDir() })
                    if (settings.slskDir != null) {
                        DropdownMenuItem(
                            text = { Text(S.useMusicFolder) },
                            onClick = { menu = false; actions.repo.prefs.update { it.copy(slskDir = null) } },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (settings.slskSubfolders) "✓ ${S.folderPerAlbum}" else S.folderPerAlbum) },
                        onClick = { menu = false; actions.repo.prefs.update { it.copy(slskSubfolders = !it.slskSubfolders) } },
                    )
                    if (manager.hasAccount) {
                        DropdownMenuItem(text = { Text(S.logOut) }, onClick = { menu = false; manager.logout() })
                    }
                }
            }
        }

        val s = state
        if (!manager.hasAccount || (s is SlskState.Failed && !s.retrying)) {
            LoginPanel(state)
            return@Column
        }

        val active = transfers.count { it.state.active }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SurcoChip(S.search, tab == 0, onClick = { tab = 0 })
            SurcoChip(if (transfers.isEmpty()) S.downloads else "${S.downloads} · ${if (active > 0) active else transfers.size}", tab == 1, onClick = { tab = 1 })
        }
        if (tab == 0) SearchPanel(initialQuery) else DownloadsPanel(transfers)
    }
}

@Composable
private fun StatusLine(state: SlskState) {
    val (color, text) = when (state) {
        is SlskState.Online -> SurcoColors.Accent to S.connectedAs(state.user)
        SlskState.Connecting -> Color(0xFFF5C451) to S.connecting
        is SlskState.Failed -> SurcoColors.Error to state.message
        SlskState.Offline -> SurcoColors.TextTertiary to S.offline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ======================================================================= Inicio de sesión

@Composable
private fun LoginPanel(state: SlskState) {
    val actions = LocalActions.current
    val manager = actions.container.soulseek
    var user by rememberSaveable { mutableStateOf(actions.repo.prefs.current.slskUser) }
    var pass by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SurcoColors.Accent,
        cursorColor = SurcoColors.Accent,
        focusedLabelColor = SurcoColors.Accent,
    )
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurcoColors.Surface).padding(20.dp),
            ) {
                Text(S.connectAccount, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    S.soulseekIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SurcoColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it.take(30) }, label = { Text(S.user) },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text(S.password) },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (user.isNotBlank() && pass.isNotEmpty()) manager.login(user, pass) }),
                )
                if (state is SlskState.Failed) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, color = SurcoColors.Error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { manager.login(user, pass) },
                    enabled = user.isNotBlank() && pass.isNotEmpty() && state != SlskState.Connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(if (state == SlskState.Connecting) S.connecting else S.connect, style = MaterialTheme.typography.titleSmall) }
                Spacer(Modifier.height(14.dp))
                Text(
                    S.soulseekNoShare,
                    style = MaterialTheme.typography.bodySmall,
                    color = SurcoColors.TextTertiary,
                )
            }
        }
    }
}

// ======================================================================= Búsqueda

@Composable
private fun SearchPanel(initialQuery: String) {
    val actions = LocalActions.current
    val manager = actions.container.soulseek
    val client = manager.client
    val state by client.state.collectAsState()
    val results by client.results.collectAsState()
    val searching by client.searching.collectAsState()
    val transfers by client.transfers.collectAsState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val settingsNow by actions.repo.prefs.settings.collectAsState()
    val format = Format.of(settingsNow.slskFormat)
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // Los resultados se enseñan de 10 en 10: al llegar al final de la lista se cargan 10 más.
    var shown by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(PAGE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val online = state is SlskState.Online

    // Si venimos de «Buscar … en Soulseek», lanzamos la búsqueda en cuanto haya conexión.
    LaunchedEffect(online) {
        if (online && !submitted && query.isNotBlank()) {
            submitted = true
            client.search(query)
        }
    }

    val groups = remember(results, format) { group(results, format) }
    // Lo que ya está en pantalla no se mueve aunque lleguen resultados mejores: los nuevos van detrás.
    val pinned = remember { ArrayList<String>() }
    if (groups.isEmpty()) pinned.clear()
    val visible = remember(groups, shown) {
        val byKey = groups.associateBy { it.key }
        val kept = pinned.mapNotNull { byKey[it] }
        val keptKeys = kept.map { it.key }.toHashSet()
        (kept + groups.filter { it.key !in keptKeys }).take(shown)
    }
    androidx.compose.runtime.SideEffect { pinned.clear(); visible.mapTo(pinned) { it.key } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 10 más sólo cuando TÚ llegas al final de la lista (no porque lleguen resultados nuevos).
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { !listState.canScrollForward && listState.canScrollBackward }
            .collect { atEnd -> if (atEnd && listState.isScrollInProgress) shown += PAGE }
    }
    val queued = remember(transfers) {
        transfers.filter { it.state != TransferState.FAILED && it.state != TransferState.CANCELLED }.map { it.user + "|" + it.path }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color(0xFF121212), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(S.artistAlbumOrSong, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF535353))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF121212)),
                    cursorBrush = SolidColor(Color(0xFF121212)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        if (query.isNotBlank()) { submitted = true; expanded = emptySet(); shown = PAGE; client.search(query) }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = ""; client.clearResults() }) { Icon(Icons.Filled.Close, S.clear, tint = Color(0xFF121212)) }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Format.entries.toList()) { f ->
                SurcoChip(
                    f.label, format == f,
                    onClick = { actions.repo.prefs.update { it.copy(slskFormat = f.key) } },
                    modifier = Modifier.testTag("format_${f.key}"),
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val summary = when {
                !online -> ""
                searching && groups.isEmpty() -> S.searchingNetwork
                groups.isEmpty() && submitted -> S.noResultsYet
                groups.size > visible.size -> S.showingOf(visible.size, groups.size)
                groups.isNotEmpty() -> S.foldersAndUsers(groups.size, groups.map { it.user }.distinct().size)
                else -> ""
            }
            Text(summary, style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary, modifier = Modifier.weight(1f))
        }
        if (searching) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
                color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh,
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().testTag("slsk_results"),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.key }) { g ->
                FolderCard(
                    g,
                    expanded = g.key in expanded,
                    queued = queued,
                    onToggle = { expanded = if (g.key in expanded) expanded - g.key else expanded + g.key },
                    onDownload = { files ->
                        manager.download(files)
                        actions.toast(S.addedToDownloads(files.size))
                    },
                )
            }
            if (groups.size > visible.size) {
                item(key = "more") {
                    // Al llegar aquí deslizando se cargan solos; el botón por si la lista no llena la pantalla.
                    Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.TextButton(onClick = { shown += PAGE }, modifier = Modifier.testTag("slsk_more")) {
                            Text(S.showTenMore, color = SurcoColors.Accent)
                        }
                    }
                }
            }
        }
    }
}

/** Resultados de Soulseek que se enseñan cada vez. */
private const val PAGE = 10

@Composable
private fun FolderCard(
    g: FolderGroup,
    expanded: Boolean,
    queued: Set<String>,
    onToggle: () -> Unit,
    onDownload: (List<SlskFile>) -> Unit,
) {
    val toDownload = remember(g) { g.files.filter { it.isAudio || it.isImage } }
    val allQueued = toDownload.all { (g.user + "|" + it.path) in queued }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurcoColors.Surface)
            .clickable(onClick = onToggle)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(SurcoColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Folder, null, tint = if (g.lossless) SurcoColors.Accent else SurcoColors.TextSecondary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (g.parent.isNotBlank()) {
                    Text(g.parent, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Pill(qualityOf(g.files), highlight = g.lossless) }
            item { Pill(S.tracksCount(g.audio.size)) }
            item { Pill(formatBytes(g.files.sumOf { it.size })) }
            item { Pill(if (g.slotFree) S.free else S.queued(g.queue), highlight = g.slotFree) }
            if (g.speed > 0) item { Pill(formatSpeed(g.speed)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                g.user,
                style = MaterialTheme.typography.labelMedium,
                color = SurcoColors.TextTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = { onDownload(toDownload) },
                enabled = !allQueued,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent,
                    disabledContainerColor = SurcoColors.SurfaceHigh, disabledContentColor = SurcoColors.TextSecondary,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(if (allQueued) Icons.Filled.CheckCircle else Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (allQueued) S.inDownloads else S.download, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            g.files.forEach { f ->
                val isQueued = (g.user + "|" + f.path) in queued
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(
                            f.durationSec.takeIf { it > 0 }?.let { (it * 1000L).formatDuration() },
                            formatBytes(f.size),
                            f.bitrate.takeIf { it > 0 && !f.lossless }?.let { "$it kbps" },
                        ).joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                    }
                    IconButton(onClick = { onDownload(listOf(f)) }, enabled = !isQueued) {
                        Icon(
                            if (isQueued) Icons.Filled.CheckCircle else Icons.Filled.Download,
                            S.download, tint = if (isQueued) SurcoColors.Accent else SurcoColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, highlight: Boolean = false) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlight) SurcoColors.Accent else SurcoColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlight) SurcoColors.Accent.copy(alpha = 0.14f) else SurcoColors.SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ======================================================================= Descargas

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsPanel(transfers: List<Transfer>) {
    val actions = LocalActions.current
    val manager = actions.container.soulseek
    if (transfers.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                S.downloadsEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = SurcoColors.TextSecondary,
            )
        }
        return
    }
    val queued = transfers.count { it.state == TransferState.QUEUED || it.state == TransferState.CONNECTING }
    val downloading = transfers.count { it.state == TransferState.STARTING || it.state == TransferState.TRANSFERRING }
    val failed = transfers.count { it.state == TransferState.FAILED || it.state == TransferState.CANCELLED }
    val done = transfers.count { it.state == TransferState.DONE }
    val active = transfers.filter { it.state.active }
    val speed = transfers.sumOf { if (it.state == TransferState.TRANSFERRING) it.speed else 0L }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        // ---- Resumen + acciones en bloque
        item {
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurcoColors.Surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.downloadsSummary(done, transfers.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (speed > 0) Text(formatSpeed(speed), style = MaterialTheme.typography.labelLarge, color = SurcoColors.Accent)
                }
                if (active.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val total = active.sumOf { it.size }.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { (active.sumOf { it.bytes }.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(S.queuedAndActive(downloading, queued), style = MaterialTheme.typography.labelMedium, color = SurcoColors.TextSecondary)
                }
                if (queued > 0 || downloading > 0 || failed > 0 || done > 0) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (queued > 0) BulkButton(S.cancelQueuedN(queued), "cancel_queued") {
                            actions.toast(S.cancelledN(manager.client.cancelQueued()))
                        }
                        if (downloading > 0) BulkButton(S.cancelDownloadingN(downloading), "cancel_downloading") {
                            actions.toast(S.cancelledN(manager.client.cancelDownloading()))
                        }
                        if (failed > 0) BulkButton(S.retryFailedN(failed), "retry_failed", accent = true) {
                            actions.toast(S.retryingN(manager.retryFailed()))
                        }
                        if (transfers.any { !it.state.active }) BulkButton(S.clearFinished, "clear_finished") {
                            manager.client.clearFinished()
                        }
                    }
                }
            }
        }
        items(transfers.reversed(), key = { it.id }) { t -> TransferRow(t) }
    }
}

@Composable
private fun BulkButton(text: String, tag: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (accent) SurcoColors.OnAccent else SurcoColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) SurcoColors.Accent else SurcoColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TransferRow(t: Transfer) {
    val manager = LocalActions.current.container.soulseek
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(t.folder, t.user).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (t.state == TransferState.TRANSFERRING || t.state == TransferState.SAVING) {
                LinearProgressIndicator(
                    progress = { t.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                    color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh,
                )
                Spacer(Modifier.height(4.dp))
            }
            val status = when (t.state) {
                TransferState.TRANSFERRING -> S.progressOf((t.progress * 100).toInt(), formatBytes(t.bytes), formatBytes(t.size)) +
                    formatSpeed(t.speed).let { if (it.isEmpty()) "" else " · $it" }
                TransferState.QUEUED -> S.inQueue(t.place)
                TransferState.DONE -> S.savedIn(t.savedAs)
                TransferState.FAILED -> t.error ?: S.error
                else -> t.state.label
            }
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = when (t.state) {
                    TransferState.FAILED -> SurcoColors.Error
                    TransferState.DONE -> SurcoColors.Accent
                    else -> SurcoColors.TextSecondary
                },
                maxLines = 2,
            )
        }
        when {
            t.state.active -> IconButton(onClick = { manager.client.cancel(t.id) }) {
                Icon(Icons.Filled.Close, S.cancel, tint = SurcoColors.TextSecondary)
            }
            t.state == TransferState.FAILED || t.state == TransferState.CANCELLED -> IconButton(onClick = { manager.retry(t.id) }) {
                Icon(Icons.Filled.Refresh, S.retry, tint = SurcoColors.TextPrimary)
            }
            else -> Icon(Icons.Filled.CheckCircle, null, tint = SurcoColors.Accent, modifier = Modifier.padding(12.dp))
        }
    }
}
