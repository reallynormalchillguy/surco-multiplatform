package dev.surco.slsk

import dev.surco.i18n.S

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

// ============================================================================ Modelos

data class SlskFile(
    val user: String,
    val path: String,
    val size: Long,
    val bitrate: Int = 0,
    val durationSec: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val vbr: Boolean = false,
) {
    val name get() = path.substringAfterLast('\\')
    val folder get() = path.substringBeforeLast('\\', "")
    val ext get() = name.substringAfterLast('.', "").lowercase()
    val isAudio get() = ext in AUDIO_EXT
    val isImage get() = ext in IMAGE_EXT
    val lossless get() = ext in LOSSLESS_EXT

    companion object {
        val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "aif", "aiff", "ape", "wv", "wma", "dsf")
        val LOSSLESS_EXT = setOf("flac", "alac", "wav", "aif", "aiff", "ape", "wv", "dsf")
        val IMAGE_EXT = setOf("jpg", "jpeg", "png")
    }
}

data class UserResult(
    val user: String,
    val files: List<SlskFile>,
    val slotFree: Boolean,
    val avgSpeed: Long,
    val queueLength: Long,
)

enum class TransferState(val active: Boolean) {
    CONNECTING(true), QUEUED(true), STARTING(true), TRANSFERRING(true), SAVING(true),
    DONE(false), FAILED(false), CANCELLED(false);

    val label: String
        get() = when (this) {
            CONNECTING -> S.stConnecting
            QUEUED -> S.stQueued
            STARTING -> S.stStarting
            TRANSFERRING -> S.stTransferring
            SAVING -> S.stSaving
            DONE -> S.stDone
            FAILED -> S.error
            CANCELLED -> S.stCancelled
        }
}

data class Transfer(
    val id: Long,
    val user: String,
    val path: String,
    val size: Long,
    val state: TransferState = TransferState.CONNECTING,
    val bytes: Long = 0,
    val speed: Long = 0,
    val place: Long? = null,
    val error: String? = null,
    val savedAs: String? = null,
    val token: Long? = null,
) {
    val name get() = path.substringAfterLast('\\')
    val folder get() = path.substringBeforeLast('\\', "").substringAfterLast('\\')
    val progress get() = if (size > 0) (bytes.toFloat() / size).coerceIn(0f, 1f) else 0f
}

sealed interface SlskState {
    data object Offline : SlskState
    data object Connecting : SlskState
    data class Online(val user: String, val port: Int) : SlskState
    /** [retrying]: se volverá a intentar solo (corte de red); si no, hace falta volver a iniciar sesión. */
    data class Failed(val message: String, val retrying: Boolean = false) : SlskState
}

// ============================================================================ Cliente

/**
 * Cliente mínimo de la red Soulseek (la misma que usan Nicotine+ y SoulseekQt):
 * inicio de sesión, búsquedas y descargas. No comparte archivos.
 *
 * Se identifica como cliente experimental (versión 177), como pide la documentación del protocolo.
 */
class SoulseekClient(
    private val cacheDir: File,
    /** Copia el archivo temporal a su destino definitivo y devuelve «Carpeta/archivo.flac». */
    private val sink: suspend (Transfer, File) -> String,
    private val onDownloadsFinished: () -> Unit,
) {
    /** Hilos propios: cada conexión bloquea uno mientras lee, y puede haber cientos tras una búsqueda. */
    private val io = Executors.newCachedThreadPool { r -> Thread(r, "slsk").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    /**
     * Ningún fallo de red (ni siquiera un Error grave en un hilo de fondo) debe cerrar la app:
     * se registra y esa conexión se descarta.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + io + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Fallo en segundo plano", e) }
    )

    private val _state = MutableStateFlow<SlskState>(SlskState.Offline)
    val state: StateFlow<SlskState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<UserResult>>(emptyList())
    val results: StateFlow<List<UserResult>> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    @Volatile private var username = ""
    @Volatile private var password = ""
    @Volatile private var wantOnline = false
    @Volatile private var searchToken = -1L
    private var server: Socket? = null
    private var serverOut: OutputStream? = null
    private val serverLock = Any()
    private var listener: ServerSocket? = null
    private var sessionJob: Job? = null

    private val peers = ConcurrentHashMap<String, PeerConn>()
    private val peerLocks = ConcurrentHashMap<String, Mutex>()
    private class Incoming(val socket: Socket, val input: DataInputStream)
    private val indirect = ConcurrentHashMap<Long, CompletableDeferred<Incoming>>()
    private val addrWaiters = ConcurrentHashMap<String, CompletableDeferred<Pair<String, Int>>>()
    private val tokens = AtomicLong(Random.nextLong(10_000, 1_000_000))
    private val ids = AtomicLong(1)

    private fun nextToken() = tokens.incrementAndGet() and 0xFFFFFFFFL

    // ------------------------------------------------------------------ Sesión

    fun connect(user: String, pass: String) {
        username = user.trim()
        password = pass
        wantOnline = true
        sessionJob?.cancel()
        sessionJob = scope.launch { sessionLoop() }
    }

    fun disconnect() {
        wantOnline = false
        sessionJob?.cancel()
        closeAll()
        _state.value = SlskState.Offline
    }

    private suspend fun sessionLoop() {
        var backoff = 5_000L
        while (wantOnline) {
            _state.value = SlskState.Connecting
            val result = runCatching { runSession() }
            closeAll()
            val err = result.exceptionOrNull()
            if (err is LoginRejected) {
                _state.value = SlskState.Failed(err.message ?: S.loginRejected)
                wantOnline = false
                return
            }
            if (!wantOnline) break
            _state.value = SlskState.Failed(S.slskReconnecting, retrying = true)
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(120_000)
        }
    }

    private class LoginRejected(msg: String) : Exception(msg)

    private suspend fun runSession() {
        val s = Socket()
        s.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), 15_000)
        s.keepAlive = true
        s.tcpNoDelay = true
        server = s
        serverOut = s.getOutputStream()
        val input = DataInputStream(s.getInputStream().buffered())

        sendServer(Writer.message(1) {
            str(username); str(password); u32(MAJOR_VERSION); str(md5(username + password)); u32(MINOR_VERSION)
        })
        val first = Reader(input.readFrame())
        if (first.u32() != 1L) throw IllegalStateException(S.unexpectedReply)
        if (!first.bool()) {
            val reason = first.str()
            val detail = runCatching { first.str() }.getOrNull()
            throw LoginRejected(S.loginError(reason, detail))
        }

        val port = startListener()
        sendServer(Writer.message(2) { u32(port) })              // SetWaitPort
        sendServer(Writer.message(28) { u32(2) })                // SetStatus: en línea
        sendServer(Writer.message(35) { u32(0); u32(0) })        // SharedFoldersFiles: no compartimos
        // No participamos en la red distribuida (reparto de búsquedas ajenas): no aceptamos hijos
        // y le decimos al servidor que no nos mande candidatos a padre.
        sendServer(Writer.message(100) { bool(false) })          // AcceptChildren
        sendServer(Writer.message(71) { bool(false) })           // HaveNoParent
        _state.value = SlskState.Online(username, port)

        val pinger = scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                runCatching { sendServer(Writer.message(32)) }   // ServerPing
            }
        }
        try {
            while (true) handleServer(Reader(input.readFrame()))
        } finally {
            pinger.cancel()
        }
    }

    private fun sendServer(bytes: ByteArray) {
        synchronized(serverLock) {
            val out = serverOut ?: throw IllegalStateException(S.offline)
            out.write(bytes)
            out.flush()
        }
    }

    private fun handleServer(r: Reader) {
        when (r.u32().toInt()) {
            3 -> { // GetPeerAddress
                val user = r.str()
                val ip = ipToString(r.u32())
                val port = r.u32().toInt()
                addrWaiters.remove(user)?.complete(ip to port)
            }
            18 -> { // ConnectToPeer: alguien quiere hablar con nosotros y no puede llegar directamente
                val user = r.str()
                val type = r.str()
                val ip = ipToString(r.u32())
                val port = r.u32().toInt()
                val token = r.u32()
                scope.launch { answerIndirect(user, type, ip, port, token) }
            }
            41 -> { // Relogged
                wantOnline = false
                _state.value = SlskState.Failed(S.loggedInElsewhere)
                runCatching { server?.close() }
            }
            1001 -> { // CantConnectToPeer
                val token = r.u32()
                indirect.remove(token)?.completeExceptionally(IllegalStateException(S.userUnreachable))
            }
            else -> Unit // El resto (salas, recomendaciones, red distribuida…) no nos interesa.
        }
    }

    private fun startListener(): Int {
        listener?.let { if (!it.isClosed) return it.localPort }
        val ss = (LISTEN_PORTS.asSequence().mapNotNull { p -> runCatching { ServerSocket(p) }.getOrNull() }.firstOrNull())
            ?: ServerSocket(0)
        listener = ss
        scope.launch {
            while (!ss.isClosed) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: break
                scope.launch { acceptIncoming(sock) }
            }
        }
        return ss.localPort
    }

    private fun closeAll() {
        runCatching { server?.close() }
        server = null
        serverOut = null
        runCatching { listener?.close() }
        listener = null
        peers.values.forEach { it.close() }
        peers.clear()
        indirect.values.forEach { it.cancel() }
        indirect.clear()
        addrWaiters.values.forEach { it.cancel() }
        addrWaiters.clear()
    }

    // ------------------------------------------------------------------ Conexiones con pares

    private inner class PeerConn(val user: String, val socket: Socket, val input: DataInputStream) {
        private val out = socket.getOutputStream()
        @Volatile var closed = false

        fun send(bytes: ByteArray) {
            synchronized(this) { out.write(bytes); out.flush() }
        }

        fun close() {
            closed = true
            runCatching { socket.close() }
        }

        fun start() {
            scope.launch {
                try {
                    socket.soTimeout = PEER_IDLE_MS
                    while (!closed) handlePeer(this@PeerConn, Reader(input.readFrame(MAX_PEER_MESSAGE)))
                } catch (_: SocketTimeoutException) {
                } catch (e: Throwable) {
                    if (!closed) Log.d(TAG, "Conexión con $user cerrada: ${e.message}")
                } finally {
                    close()
                    peers.remove(user, this@PeerConn)
                }
            }
        }
    }

    private fun attachPeer(user: String, socket: Socket, input: DataInputStream = DataInputStream(socket.getInputStream().buffered())): PeerConn {
        val conn = PeerConn(user, socket, input)
        peers.put(user, conn)?.takeIf { it !== conn }?.close()
        conn.start()
        return conn
    }

    /** Conexión «P» con un usuario: directa y, a la vez, indirecta a través del servidor. */
    private suspend fun peer(user: String): PeerConn {
        peers[user]?.takeIf { !it.closed }?.let { return it }
        val lock = peerLocks.getOrPut(user) { Mutex() }
        return lock.withLock {
            peers[user]?.takeIf { !it.closed }?.let { return@withLock it }
            val token = nextToken()
            val pierce = CompletableDeferred<Incoming>()
            indirect[token] = pierce
            val addr = CompletableDeferred<Pair<String, Int>>()
            addrWaiters[user] = addr
            sendServer(Writer.message(18) { u32(token); str(user); str("P") })
            sendServer(Writer.message(3) { str(user) })

            val winner = CompletableDeferred<Incoming>()
            var failures = 0
            val failLock = Any()
            fun failed() = synchronized(failLock) {
                failures++
                if (failures >= 2) winner.completeExceptionally(IllegalStateException(S.couldNotConnectTo(user)))
            }
            val direct = scope.launch {
                runCatching {
                    val (ip, port) = withTimeoutOrNull(12_000) { addr.await() } ?: error("sin dirección")
                    if (port == 0) error("sin puerto")
                    val s = Socket()
                    s.connect(InetSocketAddress(ip, port), 8_000)
                    s.getOutputStream().apply {
                        write(Writer.initMessage(1) { str(username); str("P"); u32(0) })
                        flush()
                    }
                    Incoming(s, DataInputStream(s.getInputStream().buffered()))
                }.onSuccess { if (!winner.complete(it)) it.socket.close() }.onFailure { failed() }
            }
            val viaServer = scope.launch {
                runCatching { pierce.await() }
                    .onSuccess { if (!winner.complete(it)) it.socket.close() }
                    .onFailure { failed() }
            }
            try {
                val inc = withTimeoutOrNull(30_000) { winner.await() } ?: error(S.couldNotConnectTo(user))
                attachPeer(user, inc.socket, inc.input)
            } finally {
                indirect.remove(token)
                addrWaiters.remove(user, addr)
                direct.cancel()
                viaServer.cancel()
            }
        }
    }

    private suspend fun answerIndirect(user: String, type: String, ip: String, port: Int, token: Long) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(ip, port), 10_000)
            s.getOutputStream().apply { write(Writer.initMessage(0) { u32(token) }); flush() }
            when (type) {
                "P" -> attachPeer(user, s)
                "F" -> fileConnection(user, s, DataInputStream(s.getInputStream().buffered()))
                else -> s.close() // "D": red distribuida, no participamos.
            }
        } catch (e: Throwable) {
            runCatching { sendServer(Writer.message(1001) { u32(token); str(user) }) }
        }
    }

    private suspend fun acceptIncoming(sock: Socket) {
        try {
            sock.soTimeout = 30_000
            val input = DataInputStream(sock.getInputStream().buffered())
            // El mensaje de inicio es diminuto: cualquier cosa grande es basura (escáneres, otros protocolos).
            val r = Reader(input.readFrame(4096))
            when (r.u8()) {
                0 -> { // PierceFireWall: respuesta a una petición indirecta nuestra
                    val token = r.u32()
                    val waiter = indirect.remove(token)
                    if (waiter == null || !waiter.complete(Incoming(sock, input))) sock.close()
                }
                1 -> { // PeerInit
                    val user = r.str()
                    when (r.str()) {
                        "P" -> attachPeer(user, sock, input)
                        "F" -> fileConnection(user, sock, input)
                        else -> sock.close()
                    }
                }
                else -> sock.close()
            }
        } catch (_: Throwable) {
            runCatching { sock.close() }
        }
    }

    // ------------------------------------------------------------------ Mensajes de pares

    private fun handlePeer(conn: PeerConn, r: Reader) {
        when (r.u32().toInt()) {
            9 -> runCatching { onSearchResponse(Reader(Reader.inflate(r.rest()))) }
            40 -> { // TransferRequest
                val direction = r.u32().toInt()
                val token = r.u32()
                val file = r.str()
                val size = if (direction == 1 && r.remaining >= 8) r.u64() else 0L
                onTransferRequest(conn, direction, token, file, size)
            }
            43, 51 -> { // QueueUpload / PlaceInQueueRequest: no compartimos nada
                val file = r.str()
                conn.send(Writer.message(50) { str(file); str("File not shared.") })
            }
            44 -> { // PlaceInQueueResponse
                val file = r.str()
                val place = r.u32()
                updateWhere(conn.user, file) { if (it.state == TransferState.QUEUED) it.copy(place = place) else it }
            }
            46 -> { // UploadFailed
                val file = r.str()
                updateWhere(conn.user, file) {
                    if (it.state.active && it.state != TransferState.SAVING) it.copy(state = TransferState.FAILED, error = S.uploaderCancelled) else it
                }
                checkFinished()
            }
            50 -> { // UploadDenied
                val file = r.str()
                val reason = r.str()
                updateWhere(conn.user, file) { it.copy(state = TransferState.FAILED, error = S.denied(reason)) }
                checkFinished()
            }
            15 -> conn.send(Writer.message(16) { // UserInfoResponse
                str("Surco, a music player for Android"); bool(false); u32(0); u32(0); bool(false); u32(0)
            })
            4 -> conn.send(Writer.message(5) { raw(Writer.zlib(Writer().u32(0).u32(0).u32(0).toByteArray())) })
            36 -> { // FolderContentsRequest
                val token = r.u32()
                val folder = r.str()
                conn.send(Writer.message(37) { raw(Writer.zlib(Writer().u32(token).str(folder).u32(0).toByteArray())) })
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ Búsqueda

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val token = nextToken()
        searchToken = token
        _results.value = emptyList()
        _searching.value = true
        scope.launch {
            runCatching { sendServer(Writer.message(26) { u32(token); str(q) }) }
                .onFailure { _searching.value = false }
            delay(SEARCH_WINDOW_MS)
            if (searchToken == token) _searching.value = false
        }
    }

    fun clearResults() {
        searchToken = -1
        _results.value = emptyList()
        _searching.value = false
    }

    private fun onSearchResponse(r: Reader) {
        val user = r.str()
        val token = r.u32()
        if (token != searchToken) return
        val count = r.u32().toInt()
        val files = ArrayList<SlskFile>(count.coerceAtMost(2000))
        repeat(count) { files += readFile(user, r) }
        val slotFree = r.remaining > 0 && r.bool()
        val speed = if (r.remaining >= 4) r.u32() else 0
        val queue = if (r.remaining >= 4) r.u32() else 0
        // Los resultados «privados» (sólo para amigos del usuario) no se pueden descargar: se ignoran.
        val result = UserResult(user, files, slotFree, speed, queue)
        _results.update { list ->
            if (list.sumOf { it.files.size } > MAX_RESULT_FILES) list
            else list.filter { it.user != user } + result
        }
    }

    private fun readFile(user: String, r: Reader): SlskFile {
        r.u8()
        val name = r.str()
        val size = r.u64()
        r.str() // extensión (vacía en SoulseekQt)
        val n = r.u32().toInt()
        var bitrate = 0; var duration = 0; var vbr = false; var rate = 0; var depth = 0
        repeat(n) {
            val code = r.u32().toInt()
            val value = r.u32().toInt()
            when (code) {
                0 -> bitrate = value
                1 -> duration = value
                2 -> vbr = value == 1
                4 -> rate = value
                5 -> depth = value
            }
        }
        return SlskFile(user, name, size, bitrate, duration, rate, depth, vbr)
    }

    // ------------------------------------------------------------------ Descargas

    fun download(files: List<SlskFile>) {
        val existing = _transfers.value.filter { it.state != TransferState.FAILED && it.state != TransferState.CANCELLED }
            .map { it.user to it.path }.toSet()
        val fresh = files.filter { (it.user to it.path) !in existing }.map {
            Transfer(id = ids.getAndIncrement(), user = it.user, path = it.path, size = it.size)
        }
        if (fresh.isEmpty()) return
        _transfers.update { it + fresh }
        fresh.groupBy { it.user }.forEach { (user, list) -> scope.launch { request(user, list) } }
    }

    fun retry(id: Long) {
        val t = _transfers.value.firstOrNull { it.id == id } ?: return
        if (t.state.active) return
        update(id) { it.copy(state = TransferState.CONNECTING, error = null, bytes = 0, speed = 0, place = null, token = null) }
        scope.launch { request(t.user, listOf(t)) }
    }

    fun cancel(id: Long) {
        val t = _transfers.value.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(state = TransferState.CANCELLED, speed = 0) }
        activeSockets.remove(id)?.let { runCatching { it.close() } }
        // No existe un mensaje para retirar algo de la cola remota: cuando llegue su turno,
        // rechazaremos la transferencia («Cancelled») y el otro cliente la descartará.
        checkFinished()
    }

    /**
     * Cancela de golpe todas las descargas activas que cumplan [which]. Devuelve cuántas.
     * (Las que ya se están guardando en tu carpeta no se tocan: están a punto de terminar.)
     */
    fun cancelAll(which: (Transfer) -> Boolean = { true }): Int {
        val ids = _transfers.value.filter { it.state.active && it.state != TransferState.SAVING && which(it) }.map { it.id }.toSet()
        if (ids.isEmpty()) return 0
        _transfers.update { list -> list.map { if (it.id in ids) it.copy(state = TransferState.CANCELLED, speed = 0) else it } }
        ids.forEach { id -> activeSockets.remove(id)?.let { runCatching { it.close() } } }
        checkFinished()
        return ids.size
    }

    /** En cola (propia o del otro usuario) o conectando: aún no ha empezado a bajar nada. */
    fun cancelQueued() = cancelAll { it.state == TransferState.QUEUED || it.state == TransferState.CONNECTING }

    /** Las que están bajando ahora mismo. */
    fun cancelDownloading() = cancelAll { it.state == TransferState.STARTING || it.state == TransferState.TRANSFERRING }

    /** Vuelve a pedir todas las que fallaron o se cancelaron. */
    fun retryFailed(): Int {
        val failed = _transfers.value.filter { it.state == TransferState.FAILED || it.state == TransferState.CANCELLED }
        failed.forEach { t ->
            update(t.id) { it.copy(state = TransferState.CONNECTING, error = null, bytes = 0, speed = 0, place = null, token = null) }
        }
        failed.groupBy { it.user }.forEach { (user, list) -> scope.launch { request(user, list) } }
        return failed.size
    }

    fun clearFinished() {
        _transfers.update { list -> list.filter { it.state.active } }
    }

    private suspend fun request(user: String, list: List<Transfer>) {
        try {
            val conn = peer(user)
            list.forEach { t ->
                conn.send(Writer.message(43) { str(t.path) })    // QueueUpload
                update(t.id) { if (it.state == TransferState.CONNECTING) it.copy(state = TransferState.QUEUED) else it }
            }
            list.forEach { t -> conn.send(Writer.message(51) { str(t.path) }) } // PlaceInQueueRequest
        } catch (e: Throwable) {
            list.forEach { t ->
                update(t.id) {
                    if (it.state == TransferState.CONNECTING) it.copy(state = TransferState.FAILED, error = S.couldNotConnectTo(user))
                    else it
                }
            }
            checkFinished()
        }
    }

    private fun onTransferRequest(conn: PeerConn, direction: Int, token: Long, file: String, size: Long) {
        if (direction != 1) {
            conn.send(Writer.message(41) { u32(token); bool(false); str("File not shared.") })
            return
        }
        val t = _transfers.value.firstOrNull {
            it.user == conn.user && it.path == file &&
                (it.state == TransferState.QUEUED || it.state == TransferState.CONNECTING || it.state == TransferState.STARTING)
        }
        if (t == null) {
            conn.send(Writer.message(41) { u32(token); bool(false); str("Cancelled") })
            return
        }
        update(t.id) { it.copy(state = TransferState.STARTING, token = token, size = if (size > 0) size else it.size, place = null) }
        conn.send(Writer.message(41) { u32(token); bool(true) })
    }

    private val activeSockets = ConcurrentHashMap<Long, Socket>()

    private suspend fun fileConnection(user: String, sock: Socket, input: DataInputStream) {
        var transferId: Long? = null
        var tmp: File? = null
        try {
            sock.soTimeout = 60_000
            val token = Reader(input.readExactly(4)).u32()           // FileTransferInit
            val t = _transfers.value.firstOrNull { it.user == user && it.token == token && it.state == TransferState.STARTING }
                ?: run { sock.close(); return }
            transferId = t.id
            activeSockets[t.id] = sock
            sock.getOutputStream().apply { write(Writer().u64(0).toByteArray()); flush() } // FileOffset
            update(t.id) { it.copy(state = TransferState.TRANSFERRING) }

            val file = File.createTempFile("slsk_", ".part", cacheDir).also { tmp = it }
            val buf = ByteArray(64 * 1024)
            var got = 0L
            var lastUi = 0L
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L
            var speed = 0L
            file.outputStream().buffered(256 * 1024).use { out ->
                while (got < t.size) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), t.size - got).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    windowBytes += n
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= 1000) {
                        speed = windowBytes * 1000 / (now - windowStart)
                        windowStart = now
                        windowBytes = 0
                    }
                    if (now - lastUi > 300) {
                        lastUi = now
                        val g = got; val sp = speed
                        update(t.id) { if (it.state == TransferState.TRANSFERRING) it.copy(bytes = g, speed = sp) else it }
                    }
                }
            }
            runCatching { sock.close() }
            activeSockets.remove(t.id)
            if (_transfers.value.firstOrNull { it.id == t.id }?.state == TransferState.CANCELLED) return
            if (got < t.size) {
                update(t.id) { it.copy(state = TransferState.FAILED, bytes = got, speed = 0, error = S.connectionDropped(got * 100 / t.size.coerceAtLeast(1))) }
                return
            }
            update(t.id) { it.copy(state = TransferState.SAVING, bytes = got, speed = 0) }
            val saved = sink(t, file)
            update(t.id) { it.copy(state = TransferState.DONE, savedAs = saved) }
        } catch (e: Throwable) {
            transferId?.let { id ->
                update(id) {
                    if (it.state == TransferState.CANCELLED) it
                    else it.copy(state = TransferState.FAILED, speed = 0, error = e.message ?: S.downloadError)
                }
            }
        } finally {
            transferId?.let { activeSockets.remove(it) }
            tmp?.delete()
            runCatching { sock.close() }
            checkFinished()
        }
    }

    private fun update(id: Long, f: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun updateWhere(user: String, path: String, f: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.user == user && it.path == path) f(it) else it } }
    }

    @Volatile private var hadActive = false

    private fun checkFinished() {
        val active = _transfers.value.any { it.state.active }
        if (!active && hadActive) {
            hadActive = false
            onDownloadsFinished()
        }
        if (active) hadActive = true
    }

    init {
        // Marca que hay descargas activas en cuanto aparecen (para avisar al terminar).
        scope.launch {
            _transfers.collect { list -> if (list.any { it.state.active }) hadActive = true }
        }
    }

    /** Sólo para las capturas de pantalla automáticas. */
    fun debugSet(state: SlskState, results: List<UserResult>, transfers: List<Transfer>) {
        _state.value = state
        _results.value = results
        _transfers.value = transfers
    }

    companion object {
        private const val TAG = "Soulseek"
        const val SERVER_HOST = "server.slsknet.org"
        const val SERVER_PORT = 2242
        /** Versión reservada para clientes experimentales (ver documentación del protocolo). */
        private const val MAJOR_VERSION = 177
        private const val MINOR_VERSION = 1
        private val LISTEN_PORTS = 2234..2240
        private const val PEER_IDLE_MS = 120_000
        private const val SEARCH_WINDOW_MS = 20_000L
        private const val MAX_RESULT_FILES = 15_000

        fun md5(s: String): String = dev.surco.util.Md5.hex(s)
    }
}
