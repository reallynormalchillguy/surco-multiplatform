package dev.surco.slsk

import dev.surco.i18n.S

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.surco.MainActivity
import dev.surco.R
import dev.surco.SurcoApp
import dev.surco.data.Prefs
import dev.surco.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Une el cliente de Soulseek con la app: credenciales, carpeta de destino, notificación y re-escaneo. */
class SoulseekManager(
    private val context: Context,
    private val prefs: Prefs,
    private val repo: Repository,
) : SoulseekService {
    private val saveLock = Mutex()

    override val client = SoulseekClient(
        cacheDir = File(context.cacheDir, "slsk").apply { mkdirs() },
        sink = { t, tmp -> save(t, tmp) },
        onDownloadsFinished = { repo.rescan() },
    )

    override val hasAccount get() = prefs.current.slskUser.isNotBlank() && prefs.current.slskPass.isNotEmpty()

    /** Conecta con la cuenta guardada si aún no hay sesión. */
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

    override fun download(files: List<SlskFile>) {
        client.download(files)
        DownloadService.start(context)
    }

    override fun retry(id: Long) {
        client.retry(id)
        DownloadService.start(context)
    }

    override fun retryFailed(): Int = client.retryFailed().also { if (it > 0) DownloadService.start(context) }

    /** Carpeta donde se guardan las descargas (la elegida o la de música). */
    override val targetTree: String? get() = prefs.current.slskDir ?: prefs.current.treeUri

    // ------------------------------------------------------------------ Guardado vía SAF

    private suspend fun save(t: Transfer, tmp: File): String = saveLock.withLock {
        withContext(Dispatchers.IO) {
            val treeStr = targetTree ?: error(S.chooseDownloadsFolder)
            val tree = Uri.parse(treeStr)
            val cr = context.contentResolver
            var dirId = DocumentsContract.getTreeDocumentId(tree)
            var label = ""
            if (prefs.current.slskSubfolders && t.folder.isNotBlank()) {
                val folderName = clean(t.folder)
                dirId = findChild(tree, dirId, folderName, wantDir = true)
                    ?: DocumentsContract.createDocument(
                        cr, DocumentsContract.buildDocumentUriUsingTree(tree, dirId),
                        DocumentsContract.Document.MIME_TYPE_DIR, folderName,
                    )?.let { DocumentsContract.getDocumentId(it) }
                    ?: error(S.couldNotCreateFolder(folderName))
                label = "$folderName/"
            }
            val name = uniqueName(tree, dirId, clean(t.name))
            val doc = DocumentsContract.createDocument(
                cr, DocumentsContract.buildDocumentUriUsingTree(tree, dirId), "application/octet-stream", name,
            ) ?: error(S.couldNotCreateFile)
            (cr.openOutputStream(doc, "w") ?: error(S.noWritePermission)).use { out ->
                tmp.inputStream().use { it.copyTo(out, 256 * 1024) }
            }
            label + name
        }
    }

    private fun findChild(tree: Uri, parentId: String, name: String, wantDir: Boolean): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(children, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir == wantDir && c.getString(1).equals(name, ignoreCase = true)) return c.getString(0)
            }
        }
        return null
    }

    private fun uniqueName(tree: Uri, dirId: String, name: String): String {
        if (findChild(tree, dirId, name, wantDir = false) == null) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        for (i in 1..99) {
            val candidate = "$base ($i)$ext"
            if (findChild(tree, dirId, candidate, wantDir = false) == null) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }

    private fun clean(s: String) = s.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim().trimEnd('.').ifBlank { "Soulseek" }
}

/** Mantiene vivas las descargas con la app en segundo plano y muestra el progreso. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build(S.preparingDownloads, 0, 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val client = (application as SurcoApp).container.soulseek.client
        job = scope.launch {
            client.transfers.collectLatest { list ->
                val active = list.filter { it.state.active }
                if (active.isEmpty()) {
                    val done = list.count { it.state == TransferState.DONE }
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (done > 0) notifyDone(done)
                    stopSelf()
                } else {
                    val total = active.sumOf { it.size }.coerceAtLeast(1)
                    val got = active.sumOf { it.bytes }
                    val running = active.count { it.state == TransferState.TRANSFERRING }
                    val text = if (running > 0) S.downloadingFiles(active.size) else S.queuedFiles(active.size)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, build(text, (got * 100 / total).toInt(), 100))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            (application as SurcoApp).container.soulseek.client.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun openIntent() = PendingIntent.getActivity(
        this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(text: String, progress: Int, max: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Soulseek")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(max, progress, max == 0)
            .setContentIntent(openIntent())
            .addAction(
                0, S.cancelAll,
                PendingIntent.getService(
                    this, 2, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun notifyDone(n: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(S.downloadsFinished)
                .setContentText(S.addedToLibrary(n))
                .setAutoCancel(true)
                .setContentIntent(openIntent())
                .build(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, S.downloads, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_CANCEL_ALL = "dev.surco.CANCEL_ALL_DOWNLOADS"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
