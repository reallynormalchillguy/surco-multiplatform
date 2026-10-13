package dev.surco.ios

import dev.surco.data.Prefs
import dev.surco.data.Repository
import dev.surco.i18n.S
import dev.surco.slsk.DownloadSink
import dev.surco.slsk.SlskFile
import dev.surco.slsk.SlskState
import dev.surco.slsk.SoulseekCore
import dev.surco.slsk.SoulseekService
import dev.surco.slsk.Transfer
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/** Soulseek en iOS: el cliente común, guardando las descargas en la carpeta elegida (o en la de música). */
@OptIn(ExperimentalForeignApi::class)
class IosSoulseek(private val prefs: Prefs, private val repo: Repository, scope: CoroutineScope) : SoulseekService {
    private val saveLock = Mutex()

    override val client = SoulseekCore(
        sink = object : DownloadSink {
            override val tempDir: String get() = IosDirs.cache
            override suspend fun save(t: Transfer, tempPath: String): String = saveFile(t, tempPath)
        },
        onDownloadsFinished = { repo.rescan() },
        clientDescription = "Surco, a music player for iPhone",
    )

    override val hasAccount get() = prefs.current.slskUser.isNotBlank() && prefs.current.slskPass.isNotEmpty()

    override val targetTree: String? get() = prefs.current.slskDir ?: prefs.current.treeUri

    override fun ensureConnected() {
        if (!hasAccount) return
        when (client.state.value) {
            is SlskState.Online, SlskState.Connecting -> Unit
            else -> client.connect(prefs.current.slskUser, prefs.current.slskPass)
        }
    }

    override fun login(user: String, pass: String) {
        prefs.update { it.copy(slskUser = user.trim(), slskPass = pass) }
        client.connect(user.trim(), pass)
    }

    override fun logout() {
        client.disconnect()
        prefs.update { it.copy(slskUser = "", slskPass = "") }
    }

    override fun download(files: List<SlskFile>) = client.download(files)
    override fun retry(id: Long) = client.retry(id)
    override fun retryFailed(): Int = client.retryFailed()

    // iOS concede unos segundos extra en segundo plano mientras haya descargas activas.
    private var bgTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

    init {
        scope.launch {
            client.transfers.collectLatest { list ->
                val active = list.any { it.state.active }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val app = UIApplication.sharedApplication
                    if (active && bgTask == UIBackgroundTaskInvalid) {
                        bgTask = app.beginBackgroundTaskWithName("soulseek") {
                            app.endBackgroundTask(bgTask); bgTask = UIBackgroundTaskInvalid
                        }
                    } else if (!active && bgTask != UIBackgroundTaskInvalid) {
                        app.endBackgroundTask(bgTask); bgTask = UIBackgroundTaskInvalid
                    }
                }
            }
        }
    }

    private suspend fun saveFile(t: Transfer, tempPath: String): String = saveLock.withLock {
        withContext(IoDispatcher) {
            val root = targetTree ?: error(S.chooseDownloadsFolder)
            val base = IosLibrary.resolve(root)?.path ?: error(S.chooseDownloadsFolder)
            val fm = NSFileManager.defaultManager
            var dir = base
            var label = ""
            if (prefs.current.slskSubfolders && t.folder.isNotBlank()) {
                val folderName = clean(t.folder)
                val existing = fm.contentsOfDirectoryAtPath(base, null)?.mapNotNull { it as? String }
                    ?.firstOrNull { it.equals(folderName, ignoreCase = true) }
                dir = "$base/${existing ?: folderName}"
                if (existing == null && !fm.createDirectoryAtPath(dir, true, null, null)) error(S.couldNotCreateFolder(folderName))
                label = "${existing ?: folderName}/"
            }
            val name = uniqueName(dir, clean(t.name))
            if (!fm.moveItemAtPath(tempPath, "$dir/$name", null)) {
                if (!fm.copyItemAtPath(tempPath, "$dir/$name", null)) error(S.noWritePermission)
            }
            label + name
        }
    }

    private fun uniqueName(dir: String, name: String): String {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath("$dir/$name")) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        for (i in 1..99) {
            val candidate = "$stem ($i)$ext"
            if (!fm.fileExistsAtPath("$dir/$candidate")) return candidate
        }
        return "$stem (${nowMillis()})$ext"
    }

    private fun clean(s: String) = s.map { c -> if (c.code < 0x20 || c in "\\/:*?\"<>|") '_' else c }
        .joinToString("").trim().trimEnd('.').ifBlank { "Soulseek" }
}
