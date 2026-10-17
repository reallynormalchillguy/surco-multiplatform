package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.surco.app.SurcoCore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.surco.data.JobProgress
import dev.surco.data.ScanState
import dev.surco.data.ScrobbleMode
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val actions = LocalActions.current
    val container = actions.container
    val repo = actions.repo
    val settings by repo.prefs.settings.collectAsState()
    val scan by repo.scanner.state.collectAsState()
    val trackCount by remember { repo.dao.trackCount() }.collectAsState(initial = 0)
    val playCount by remember { repo.dao.playCount() }.collectAsState(initial = 0)
    val pending by remember { repo.dao.pendingScrobbleCount() }.collectAsState(initial = 0)
    val lfmError by repo.lastFmError.collectAsState()
    val coverJob by repo.coverJob.collectAsState()
    val artistJob by repo.artistJob.collectAsState()
    val pickFolder = rememberFolderPicker(container)
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back, tint = SurcoColors.TextPrimary)
            }
            Text(S.settings, style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // ================= Idioma
            Header(S.language)
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dev.surco.i18n.Lang.entries.forEach { l ->
                        dev.surco.ui.components.SurcoChip(
                            l.label,
                            selected = settings.language == l.code,
                            onClick = { repo.prefs.update { it.copy(language = l.code) } },
                        )
                    }
                }
            }

            // ================= Música
            Header(S.yourMusic)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = SurcoColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folderName(settings.treeUri), style = MaterialTheme.typography.titleMedium)
                        Text(S.libraryStats(trackCount, playCount), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (val s = scan) {
                    is ScanState.Running -> {
                        Text(S.scanningProgress(s.done, s.total, s.current), style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Progress(if (s.total > 0) s.done.toFloat() / s.total else null)
                    }
                    is ScanState.Done -> Text(
                        S.lastScan(s.total, s.updated, s.removed),
                        style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                    )
                    is ScanState.Failed -> Text(S.errorWith(s.message), style = MaterialTheme.typography.bodySmall, color = SurcoColors.Error)
                    ScanState.Idle -> {}
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pickFolder) { Text(S.changeFolder) }
                    AccentButton(S.scanAgain, enabled = scan !is ScanState.Running) { repo.rescan() }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow(S.autoScan, S.autoScanDesc, settings.autoScan) { v ->
                    repo.prefs.update { it.copy(autoScan = v) }
                }
            }

            // ================= Apariencia
            Header(S.appearance)
            Card {
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openPalette() }.testTag("open_palette"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(S.colorPalette, style = MaterialTheme.typography.titleSmall)
                        Text(S.colorPaletteDesc, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SurcoColors.palette.base.forEach { c ->
                            Box(Modifier.size(26.dp).clip(CircleShape).background(SurcoColors.SurfaceHigher).padding(2.dp).clip(CircleShape).background(c))
                        }
                    }
                }
                OledRow(settings) { on -> repo.prefs.update { it.copy(oled = if (on) "on" else "off") } }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(S.shufflePalette) {
                        val p = dev.surco.ui.theme.Palette.random()
                        repo.prefs.update { it.copy(palette = p.encode()) }
                    }
                    OutlinedButton(onClick = { actions.openPalette() }) { Text(S.customize) }
                }
            }

            // ================= Soulseek
            Header("Soulseek")
            Card {
                val slskState by container.soulseek.client.state.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDownload, null, tint = SurcoColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.slskUser.isBlank()) S.noAccount else settings.slskUser,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when (val st = slskState) {
                                is dev.surco.slsk.SlskState.Online -> S.connected
                                dev.surco.slsk.SlskState.Connecting -> S.connecting
                                is dev.surco.slsk.SlskState.Failed -> st.message
                                dev.surco.slsk.SlskState.Offline -> S.connectsWhenOpened
                            },
                            style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    S.downloadsIn(folderName(settings.slskDir ?: settings.treeUri), settings.slskSubfolders),
                    style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                AccentButton(S.openSoulseek) { actions.openSoulseek() }
            }

            // ================= Last.fm
            Header("Last.fm")
            Card {
                Text(S.howToScrobble, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                ModeOption(
                    S.builtIn,
                    S.builtInDesc,
                    settings.scrobbleMode == ScrobbleMode.BUILTIN,
                ) { repo.prefs.update { it.copy(scrobbleMode = ScrobbleMode.BUILTIN) } }
                ModeOption(
                    S.panoOrOther,
                    S.panoDesc,
                    settings.scrobbleMode == ScrobbleMode.EXTERNAL,
                ) { repo.prefs.update { it.copy(scrobbleMode = ScrobbleMode.EXTERNAL) } }
                ModeOption(S.off, S.nothingIsSent, settings.scrobbleMode == ScrobbleMode.OFF) {
                    repo.prefs.update { it.copy(scrobbleMode = ScrobbleMode.OFF) }
                }

                if (settings.scrobbleMode == ScrobbleMode.BUILTIN) {
                    Spacer(Modifier.height(12.dp))
                    when {
                        !settings.lastfmReady -> ApiKeysForm(onOpenLink = {
                            runCatching { uriHandler.openUri("https://www.last.fm/api/account/create") }
                        })
                        settings.lastfmLoggedIn -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(SurcoColors.Accent))
                                Spacer(Modifier.width(10.dp))
                                Text(S.connectedAs(settings.lastfmUser), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                S.pendingScrobbles(pending),
                                style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                            )
                            val oldest by remember { repo.dao.oldestPendingScrobble() }.collectAsState(initial = null)
                            val days = oldest?.let { (dev.surco.util.nowMillis() / 1000 - it) / 86_400 }
                            if (days != null && days >= 10) {
                                Text(S.scrobblesAgeWarning(days), style = MaterialTheme.typography.bodySmall, color = SurcoColors.Error)
                            }
                            lfmError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SurcoColors.Error) }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { repo.lastFm.logout() }) { Text(S.logOut) }
                                if (pending > 0) AccentButton(S.sendNow) { repo.flushScrobblesAsync() }
                            }
                        }
                        else -> LoginForm()
                    }
                }
            }

            // ================= Portadas
            Header(S.coversAndPhotos)
            Card {
                SwitchRow(
                    S.embedCovers,
                    S.embedCoversDesc,
                    settings.embedCovers,
                ) { v -> repo.prefs.update { it.copy(embedCovers = v) } }
                SwitchRow(
                    S.autoCovers,
                    S.autoCoversDesc,
                    settings.autoCoversAfterScan,
                ) { v -> repo.prefs.update { it.copy(autoCoversAfterScan = v) } }
                Spacer(Modifier.height(8.dp))
                JobRow(coverJob)
                AccentButton(S.findMissingCovers, enabled = !coverJob.running) { repo.startFillMissingCovers() }

                Spacer(Modifier.height(16.dp))
                SwitchRow(
                    S.artistPhotos,
                    S.artistPhotosDesc,
                    settings.artistImages,
                ) { v -> repo.prefs.update { it.copy(artistImages = v) } }
                JobRow(artistJob)
                OutlinedButton(onClick = { repo.startFetchArtistImages() }, enabled = !artistJob.running) {
                    Text(S.downloadPhotosNow)
                }
            }

            // ================= Recomendaciones
            Header(S.recommendations)
            Card {
                SwitchRow(S.recommendations, S.recommendationsDesc, settings.discover) { on ->
                    repo.prefs.update { it.copy(discover = on) }
                    if (on) container.discovery.refreshIfStale(force = true)
                }
            }

            // ================= Acerca de
            Header(S.about)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Surco ${container.appVersion}", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    S.aboutText,
                    style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

private fun folderName(tree: String?): String {
    if (tree == null) return S.noFolder
    val decoded = folderLabel(tree)
    val path = decoded.substringAfter(':', decoded)
    val volume = decoded.substringBefore(':', "")
    val vol = if (volume == "primary") S.internalStorage else volume
    return when {
        vol.isBlank() -> path
        path.isBlank() -> vol
        else -> "$vol / $path"
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurcoColors.Surface)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Progress(fraction: Float?) {
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.fillMaxWidth(),
            color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh,
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = SurcoColors.Accent, trackColor = SurcoColors.SurfaceHigh)
    }
}

@Composable
private fun JobRow(job: JobProgress) {
    if (job.running) {
        Text("${job.done}/${job.total} · ${job.message}", style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Progress(if (job.total > 0) job.done.toFloat() / job.total else null)
        Spacer(Modifier.height(8.dp))
    } else if (job.message.isNotEmpty()) {
        Text(job.message, style = MaterialTheme.typography.bodySmall, color = SurcoColors.Accent)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccentButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
    ) { Text(text) }
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = SurcoColors.Accent))
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = SurcoColors.Accent))
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SurcoColors.Accent,
    focusedLabelColor = SurcoColors.Accent,
    cursorColor = SurcoColors.Accent,
    unfocusedBorderColor = SurcoColors.SurfaceHigher,
)

@Composable
private fun ApiKeysForm(onOpenLink: () -> Unit) {
    val repo = LocalActions.current.repo
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Text(
        S.apiKeysHelp,
        style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary,
    )
    Text(
        "last.fm/api/account/create",
        style = MaterialTheme.typography.bodySmall,
        color = SurcoColors.Accent,
        modifier = Modifier.clickable(onClick = onOpenLink).padding(vertical = 6.dp),
    )
    OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, colors = textFieldColors(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(secret, { secret = it }, label = { Text("Shared secret") }, singleLine = true, colors = textFieldColors(), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    AccentButton(S.saveKeys, enabled = key.isNotBlank() && secret.isNotBlank()) {
        repo.prefs.update { it.copy(lastfmKey = key.trim(), lastfmSecret = secret.trim()) }
    }
}

@Composable
private fun LoginForm() {
    val actions = LocalActions.current
    val repo = actions.repo
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text(S.signInLastfm, style = MaterialTheme.typography.titleSmall)
    Text(S.passwordNotStored, style = MaterialTheme.typography.bodySmall, color = SurcoColors.TextSecondary)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(user, { user = it }, label = { Text(S.user) }, singleLine = true, colors = textFieldColors(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        pass, { pass = it }, label = { Text(S.password) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = textFieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = SurcoColors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentButton(if (busy) S.connecting else S.signIn, enabled = !busy && user.isNotBlank() && pass.isNotBlank()) {
            busy = true
            error = null
            actions.scope.launch {
                try {
                    repo.lastFm.login(user.trim(), pass)
                    pass = ""
                    actions.toast(S.connectedToLastfm)
                    repo.flushScrobbles()
                } catch (e: Exception) {
                    error = e.message ?: S.couldNotSignIn
                } finally {
                    busy = false
                }
            }
        }
        OutlinedButton(onClick = { repo.prefs.update { it.copy(lastfmKey = "", lastfmSecret = "") } }) { Text(S.changeKeys) }
    }
}

// ================================================================ Bienvenida

@Composable
fun OnboardingScreen(container: SurcoCore) {
    val pick = rememberFolderPicker(container)
    Column(
        Modifier
            .fillMaxSize()
            .background(SurcoColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(SurcoColors.Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.GraphicEq, null, tint = SurcoColors.OnAccent, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Surco", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            S.onboardingText,
            style = MaterialTheme.typography.bodyLarge,
            color = SurcoColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = pick,
            colors = ButtonDefaults.buttonColors(containerColor = SurcoColors.Accent, contentColor = SurcoColors.OnAccent),
            modifier = Modifier.height(52.dp),
        ) {
            Icon(Icons.Filled.Folder, null)
            Spacer(Modifier.width(10.dp))
            Text(S.chooseMusicFolder, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            S.grantWriteAccess,
            style = MaterialTheme.typography.bodySmall,
            color = SurcoColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
