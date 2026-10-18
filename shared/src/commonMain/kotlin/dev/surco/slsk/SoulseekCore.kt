package dev.surco.slsk

import dev.surco.i18n.S
import dev.surco.util.IoDispatcher
import dev.surco.util.Md5
import dev.surco.util.nowMillis
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random

/** Dónde deja la plataforma cada descarga terminada. */
interface DownloadSink {
    /** Carpeta para los archivos temporales («.part»). */
    val tempDir: String
    /** Mueve/copia el temporal a su destino definitivo y devuelve «Carpeta/archivo.flac». */
    suspend fun save(t: Transfer, tempPath: String): String
}

/**
 * Cliente mínimo de la red Soulseek (la misma que usan Nicotine+ y SoulseekQt) en Kotlin común,
 * con sockets de Ktor: inicio de sesión, búsquedas y descargas. No comparte archivos.
 * Se identifica como cliente experimental (versión 177), como pide la documentación del protocolo.
 *
 * Es una traducción a corrutinas del cliente de Android (mismos mensajes, mismos pasos), de modo que
 * iOS se comporta igual.
 */
class SoulseekCore(
    private val sink: DownloadSink,
    private val onDownloadsFinished: () -> Unit,
    private val serverHost: String = SERVER_HOST,
    private val serverPort: Int = SERVER_PORT,
    private val listenPorts: IntRange = LISTEN_PORTS,
    /** Nombre del cliente en la respuesta a «información de usuario». */
    private val clientDescription: String = "Surco, a music player",
) : SoulseekEngine {
    private val scope = CoroutineScope(
        SupervisorJob() + IoDispatcher + CoroutineExceptionHandler { _, e -> println("Soulseek: fallo en segundo plano: $e") }
    )
    /**
     * Selector de sockets de Ktor. En iOS usa select(), que no admite descriptores ≥ 1024: si uno lo
     * supera, el bucle del selector lanza una excepción. Con este manejador ya no cierra la app; la
     * sesión cae, se crea un selector nuevo y se reconecta sola.
     */
    private var selector = newSelector()

    private fun newSelector(): SelectorManager {
        lateinit var created: SelectorManager
        created = SelectorManager(
            IoDispatcher + CoroutineExceptionHandler { _, e ->
                println("Soulseek: el selector de sockets se detuvo: $e")
                onSelectorDied(created)
            }
        )
        return created
    }

    /**
     * Con el selector detenido, las lecturas pendientes ya no terminarían nunca: rehacemos la sesión
     * entera (sockets y selector nuevos) en vez de quedarnos «en línea» sin funcionar.
     */
    private fun onSelectorDied(dead: SelectorManager) {
        scope.launch {
            if (dead !== selector || !wantOnline) return@launch
            sessionJob?.cancel()
            closeAll()
            _state.value = SlskState.Failed(S.slskReconnecting, retrying = true)
            sessionJob = scope.launch {
                delay(3_000)
                sessionLoop()
            }
        }
    }

    private val _state = MutableStateFlow<SlskState>(SlskState.Offline)
    override val state: StateFlow<SlskState> = _state.asStateFlow()
    private val _results = MutableStateFlow<List<UserResult>>(emptyList())
    override val results: StateFlow<List<UserResult>> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    override val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    override val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private var username = ""
    private var password = ""
    private var wantOnline = false
    private val searchToken = MutableStateFlow(-1L)
    private var server: Conn? = null
    private var listener: ServerSocket? = null
    private var sessionJob: Job? = null

    private val peers = MutableStateFlow<Map<String, PeerConn>>(emptyMap())
    private val peerLocks = MutableStateFlow<Map<String, Mutex>>(emptyMap())
    private val indirect = MutableStateFlow<Map<Long, CompletableDeferred<Conn>>>(emptyMap())
    private val addrWaiters = MutableStateFlow<Map<String, CompletableDeferred<Pair<String, Int>>>>(emptyMap())
    private val activeSockets = MutableStateFlow<Map<Long, Conn>>(emptyMap())
    private val tokens = MutableStateFlow(Random.nextLong(10_000, 1_000_000))
    private val ids = MutableStateFlow(0L)

    private fun nextToken() = tokens.updateAndGet { it + 1 } and 0xFFFFFFFFL
    private fun nextId() = ids.updateAndGet { it + 1 }

    /** Sockets abiertos ahora mismo (para no pasar de [MAX_SOCKETS]). */
    private val openSockets = MutableStateFlow(0)

    /** Número de sockets abiertos (pruebas). */
    val socketCount: Int get() = openSockets.value

    /** Socket con sus dos canales y un cerrojo para escribir mensajes enteros. */
    private inner class Conn(val socket: Socket) {
        val input: ByteReadChannel = socket.openReadChannel()
        val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
        private val writeLock = Mutex()
        private val closed = MutableStateFlow(false)

        init { openSockets.update { it + 1 } }

        suspend fun send(bytes: ByteArray) = writeLock.withLock { output.writeFully(bytes, 0, bytes.size); output.flush() }

        fun close() {
            if (closed.compareAndSet(false, true)) {
                openSockets.update { it - 1 }
                runCatching { socket.close() }
            }
        }
    }

    /**
     * Antes de abrir otro socket: si vamos justos, cierra las conexiones con usuarios que sólo nos
     * mandaron resultados (sin descargas pendientes), empezando por las más antiguas.
     * Devuelve false si aun así no hay sitio.
     */
    private fun makeRoom(): Boolean {
        if (openSockets.value < MAX_SOCKETS) return true
        val busy = _transfers.value.filter { it.state.active }.map { it.user }.toSet()
        val idle = peers.value.values.filter { it.user !in busy }.sortedBy { it.lastActivity }
        for (pc in idle) {
            if (openSockets.value < MAX_SOCKETS - SOCKET_SLACK) break
            pc.close()
        }
        return openSockets.value < MAX_SOCKETS
    }

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
            if (err is kotlinx.coroutines.CancellationException) throw err
            if (!wantOnline) break
            _state.value = SlskState.Failed(S.slskReconnecting, retrying = true)
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(120_000)
        }
    }

    private class LoginRejected(msg: String) : Exception(msg)

    private suspend fun runSession() {
        val sock = withTimeout(15_000) { aSocket(selector).tcp().connect(serverHost, serverPort) { keepAlive = true; noDelay = true } }
        val s = Conn(sock)
        server = s
        s.send(Writer.message(1) {
            str(username); str(password); u32(MAJOR_VERSION); str(Md5.hex(username + password)); u32(MINOR_VERSION)
        })
        val first = Reader(withTimeout(30_000) { s.input.readFrame() })
        if (first.u32() != 1L) throw IllegalStateException(S.unexpectedReply)
        if (!first.bool()) {
            val reason = first.str()
            val detail = runCatching { first.str() }.getOrNull()
            throw LoginRejected(S.loginError(reason, detail))
        }

        val port = startListener()
        s.send(Writer.message(2) { u32(port) })              // SetWaitPort
        s.send(Writer.message(28) { u32(2) })                // SetStatus: en línea
        s.send(Writer.message(35) { u32(0); u32(0) })        // SharedFoldersFiles: no compartimos
        // No participamos en la red distribuida: no aceptamos hijos ni pedimos padre.
        s.send(Writer.message(100) { bool(false) })          // AcceptChildren
        s.send(Writer.message(71) { bool(false) })           // HaveNoParent
        _state.value = SlskState.Online(username, port)

        val pinger = scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                runCatching { s.send(Writer.message(32)) }   // ServerPing
            }
        }
        try {
            while (true) handleServer(Reader(s.input.readFrame()))
        } finally {
            pinger.cancel()
        }
    }

    private suspend fun sendServer(bytes: ByteArray) {
        val s = server ?: throw IllegalStateException(S.offline)
        s.send(bytes)
    }

    private fun handleServer(r: Reader) {
        when (r.u32().toInt()) {
            3 -> { // GetPeerAddress
                val user = r.str()
                val ip = ipToString(r.u32())
                val port = r.u32().toInt()
                takeAddrWaiter(user)?.complete(ip to port)
            }
            18 -> { // ConnectToPeer
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
                server?.close()
            }
            1001 -> { // CantConnectToPeer
                val token = r.u32()
                takeIndirect(token)?.completeExceptionally(IllegalStateException(S.userUnreachable))
            }
            else -> Unit
        }
    }

    private suspend fun startListener(): Int {
        listener?.let { if (!it.isClosed) return (it.localAddress as InetSocketAddress).port }
        var bound: ServerSocket? = null
        for (p in listenPorts) {
            bound = runCatching { aSocket(selector).tcp().bind("0.0.0.0", p) }.getOrNull()
            if (bound != null) break
        }
        val ss = bound ?: aSocket(selector).tcp().bind("0.0.0.0", 0)
        listener = ss
        scope.launch {
            while (!ss.isClosed) {
                val sock = runCatching { ss.accept() }.getOrNull()
                if (sock == null) {
                    // p. ej. sin descriptores libres: esperamos un poco en vez de dejar de escuchar.
                    if (ss.isClosed) break
                    delay(500)
                    continue
                }
                val c = Conn(sock)
                if (openSockets.value > MAX_SOCKETS && !makeRoom()) { c.close(); continue }
                scope.launch { acceptIncoming(c) }
            }
        }
        return (ss.localAddress as InetSocketAddress).port
    }

    private fun closeAll() {
        server?.close()
        server = null
        runCatching { listener?.close() }
        listener = null
        peers.getAndClear().values.forEach { it.close() }
        indirect.getAndClear().values.forEach { it.cancel() }
        addrWaiters.getAndClear().values.forEach { it.cancel() }
        // Selector nuevo para la siguiente sesión (el anterior puede haberse detenido).
        val old = selector
        selector = newSelector()
        runCatching { old.close() }
    }

    private fun <K, V> MutableStateFlow<Map<K, V>>.getAndClear(): Map<K, V> {
        var old: Map<K, V> = emptyMap()
        update { old = it; emptyMap() }
        return old
    }

    private fun takeIndirect(token: Long): CompletableDeferred<Conn>? {
        var w: CompletableDeferred<Conn>? = null
        indirect.update { m -> w = m[token]; m - token }
        return w
    }

    private fun takeAddrWaiter(user: String): CompletableDeferred<Pair<String, Int>>? {
        var w: CompletableDeferred<Pair<String, Int>>? = null
        addrWaiters.update { m -> w = m[user]; m - user }
        return w
    }

    // ------------------------------------------------------------------ Conexiones con pares

    private inner class PeerConn(val user: String, val conn: Conn) {
        var closed = false
        var lastActivity = nowMillis()

        suspend fun send(bytes: ByteArray) = conn.send(bytes)

        fun close() {
            closed = true
            conn.close()
        }

        fun start() {
            scope.launch {
                try {
                    while (!closed) {
                        // Quien sólo nos mandó resultados se cierra pronto; con descargas en marcha, se espera más.
                        val waiting = _transfers.value.any { it.user == user && it.state.active }
                        val idle = if (waiting) PEER_IDLE_MS else SEARCH_PEER_IDLE_MS
                        val frame = withTimeoutOrNull(idle) { conn.input.readFrame(MAX_PEER_MESSAGE) }
                        if (frame == null) {
                            if (_transfers.value.any { it.user == user && it.state.active } && !waiting) continue
                            break
                        }
                        lastActivity = nowMillis()
                        handlePeer(this@PeerConn, Reader(frame))
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                } finally {
                    close()
                    peers.update { m -> if (m[user] === this@PeerConn) m - user else m }
                }
            }
        }
    }

    private fun attachPeer(user: String, conn: Conn): PeerConn {
        val pc = PeerConn(user, conn)
        var old: PeerConn? = null
        peers.update { m -> old = m[user]; m + (user to pc) }
        old?.takeIf { it !== pc }?.close()
        pc.start()
        return pc
    }

    /** Conexión «P» con un usuario: directa y, a la vez, indirecta a través del servidor. */
    private suspend fun peer(user: String): PeerConn {
        peers.value[user]?.takeIf { !it.closed }?.let { return it }
        val lock = peerLocks.updateAndGet { m -> if (user in m) m else m + (user to Mutex()) }.getValue(user)
        return lock.withLock {
            peers.value[user]?.takeIf { !it.closed }?.let { return@withLock it }
            val token = nextToken()
            val pierce = CompletableDeferred<Conn>()
            indirect.update { it + (token to pierce) }
            val addr = CompletableDeferred<Pair<String, Int>>()
            addrWaiters.update { it + (user to addr) }
            sendServer(Writer.message(18) { u32(token); str(user); str("P") })
            sendServer(Writer.message(3) { str(user) })

            val winner = CompletableDeferred<Conn>()
            val failures = MutableStateFlow(0)
            fun failed() {
                if (failures.updateAndGet { it + 1 } >= 2) winner.completeExceptionally(IllegalStateException(S.couldNotConnectTo(user)))
            }
            val direct = scope.launch {
                runCatching {
                    val (ip, port) = withTimeoutOrNull(12_000) { addr.await() } ?: error("sin dirección")
                    if (port == 0) error("sin puerto")
                    if (!makeRoom()) error(S.couldNotConnectTo(user))
                    val sock = withTimeout(8_000) { aSocket(selector).tcp().connect(ip, port) }
                    val c = Conn(sock)
                    c.send(Writer.initMessage(1) { str(username); str("P"); u32(0) })
                    c
                }.onSuccess { if (!winner.complete(it)) it.close() }.onFailure { failed() }
            }
            val viaServer = scope.launch {
                runCatching { pierce.await() }
                    .onSuccess { if (!winner.complete(it)) it.close() }
                    .onFailure { failed() }
            }
            try {
                val c = withTimeoutOrNull(30_000) { winner.await() } ?: error(S.couldNotConnectTo(user))
                attachPeer(user, c)
            } finally {
                indirect.update { it - token }
                addrWaiters.update { m -> if (m[user] === addr) m - user else m }
                direct.cancel()
                viaServer.cancel()
            }
        }
    }

    private suspend fun answerIndirect(user: String, type: String, ip: String, port: Int, token: Long) {
        try {
            // Para búsquedas basta con que lleguen por otras vías; las descargas siempre tienen prioridad.
            if (type != "F" && !makeRoom()) error("sin sitio para más conexiones")
            val sock = withTimeout(10_000) { aSocket(selector).tcp().connect(ip, port) }
            val c = Conn(sock)
            c.send(Writer.initMessage(0) { u32(token) })
            when (type) {
                "P" -> attachPeer(user, c)
                "F" -> fileConnection(user, c)
                else -> c.close() // "D": red distribuida, no participamos.
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            runCatching { sendServer(Writer.message(1001) { u32(token); str(user) }) }
        }
    }

    private suspend fun acceptIncoming(c: Conn) {
        try {
            // El mensaje de inicio es diminuto: cualquier cosa grande es basura.
            val r = Reader(withTimeout(30_000) { c.input.readFrame(4096) })
            when (r.u8()) {
                0 -> { // PierceFireWall: respuesta a una petición indirecta nuestra
                    val token = r.u32()
                    val waiter = takeIndirect(token)
                    if (waiter == null || !waiter.complete(c)) c.close()
                }
                1 -> { // PeerInit
                    val user = r.str()
                    when (r.str()) {
                        "P" -> attachPeer(user, c)
                        "F" -> fileConnection(user, c)
                        else -> c.close()
                    }
                }
                else -> c.close()
            }
        } catch (_: Throwable) {
            c.close()
        }
    }

    // ------------------------------------------------------------------ Mensajes de pares

    private suspend fun handlePeer(pc: PeerConn, r: Reader) {
        when (r.u32().toInt()) {
            9 -> runCatching { onSearchResponse(Reader(Reader.inflate(r.rest()))) }
            40 -> { // TransferRequest
                val direction = r.u32().toInt()
                val token = r.u32()
                val file = r.str()
                val size = if (direction == 1 && r.remaining >= 8) r.u64() else 0L
                onTransferRequest(pc, direction, token, file, size)
            }
            43, 51 -> { // QueueUpload / PlaceInQueueRequest: no compartimos nada
                val file = r.str()
                pc.send(Writer.message(50) { str(file); str("File not shared.") })
            }
            44 -> { // PlaceInQueueResponse
                val file = r.str()
                val place = r.u32()
                updateWhere(pc.user, file) { if (it.state == TransferState.QUEUED) it.copy(place = place) else it }
            }
            46 -> { // UploadFailed
                val file = r.str()
                updateWhere(pc.user, file) {
                    if (it.state.active && it.state != TransferState.SAVING) it.copy(state = TransferState.FAILED, error = S.uploaderCancelled) else it
                }
                checkFinished()
            }
            50 -> { // UploadDenied
                val file = r.str()
                val reason = r.str()
                updateWhere(pc.user, file) { it.copy(state = TransferState.FAILED, error = S.denied(reason)) }
                checkFinished()
            }
            15 -> pc.send(Writer.message(16) { // UserInfoResponse
                str(clientDescription); bool(false); u32(0); u32(0); bool(false); u32(0)
            })
            4 -> pc.send(Writer.message(5) { raw(Writer.zlib(Writer().u32(0).u32(0).u32(0).toByteArray())) })
            36 -> { // FolderContentsRequest
                val token = r.u32()
                val folder = r.str()
                pc.send(Writer.message(37) { raw(Writer.zlib(Writer().u32(token).str(folder).u32(0).toByteArray())) })
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ Búsqueda

    override fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val token = nextToken()
        searchToken.value = token
        _results.value = emptyList()
        _searching.value = true
        scope.launch {
            runCatching { sendServer(Writer.message(26) { u32(token); str(q) }) }
                .onFailure { _searching.value = false }
            delay(SEARCH_WINDOW_MS)
            if (searchToken.value == token) _searching.value = false
        }
    }

    override fun clearResults() {
        searchToken.value = -1
        _results.value = emptyList()
        _searching.value = false
    }

    private fun onSearchResponse(r: Reader) {
        val user = r.str()
        val token = r.u32()
        if (token != searchToken.value) return
        val count = r.u32().toInt()
        val files = ArrayList<SlskFile>(count.coerceIn(0, 2000))
        repeat(count) { files += readFile(user, r) }
        val slotFree = r.remaining > 0 && r.bool()
        val speed = if (r.remaining >= 4) r.u32() else 0
        val queue = if (r.remaining >= 4) r.u32() else 0
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
        r.str() // extensión
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
            Transfer(id = nextId(), user = it.user, path = it.path, size = it.size)
        }
        if (fresh.isEmpty()) return
        _transfers.update { it + fresh }
        hadActive.value = true
        fresh.groupBy { it.user }.forEach { (user, list) -> scope.launch { request(user, list) } }
    }

    fun retry(id: Long) {
        val t = _transfers.value.firstOrNull { it.id == id } ?: return
        if (t.state.active) return
        update(id) { it.copy(state = TransferState.CONNECTING, error = null, bytes = 0, speed = 0, place = null, token = null) }
        hadActive.value = true
        scope.launch { request(t.user, listOf(t)) }
    }

    override fun cancel(id: Long) {
        _transfers.value.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(state = TransferState.CANCELLED, speed = 0) }
        takeActive(id)?.close()
        forgetCancelled()
        checkFinished()
    }

    override fun cancelAll(which: (Transfer) -> Boolean): Int {
        val ids = _transfers.value.filter { it.state.active && it.state != TransferState.SAVING && which(it) }.map { it.id }.toSet()
        if (ids.isEmpty()) return 0
        _transfers.update { list -> list.map { if (it.id in ids) it.copy(state = TransferState.CANCELLED, speed = 0) else it } }
        ids.forEach { id -> takeActive(id)?.close() }
        forgetCancelled()
        checkFinished()
        return ids.size
    }

    override fun cancelQueued() = cancelAll { it.state == TransferState.QUEUED || it.state == TransferState.CONNECTING }

    override fun cancelDownloading() = cancelAll { it.state == TransferState.STARTING || it.state == TransferState.TRANSFERRING }

    fun retryFailed(): Int {
        val failed = _transfers.value.filter { it.state == TransferState.FAILED || it.state == TransferState.CANCELLED }
        failed.forEach { t ->
            update(t.id) { it.copy(state = TransferState.CONNECTING, error = null, bytes = 0, speed = 0, place = null, token = null) }
        }
        if (failed.isNotEmpty()) hadActive.value = true
        failed.groupBy { it.user }.forEach { (user, list) -> scope.launch { request(user, list) } }
        return failed.size
    }

    override fun clearFinished() {
        _transfers.update { list -> list.filter { it.state.active } }
    }

    private fun takeActive(id: Long): Conn? {
        var c: Conn? = null
        activeSockets.update { m -> c = m[id]; m - id }
        return c
    }

    /**
     * Lo cancelado desaparece de la lista de descargas; y si ya no queda nada en marcha, el historial
     * entero (terminadas y fallidas) se limpia solo. Si el otro usuario intenta enviarlo después, ya
     * no está en la lista y se le contesta «Cancelled».
     */
    private fun forgetCancelled() {
        _transfers.update { list ->
            val rest = list.filter { it.state != TransferState.CANCELLED }
            if (rest.none { it.state.active }) emptyList() else rest
        }
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            list.forEach { t ->
                update(t.id) {
                    if (it.state == TransferState.CONNECTING) it.copy(state = TransferState.FAILED, error = S.couldNotConnectTo(user))
                    else it
                }
            }
            checkFinished()
        }
    }

    private suspend fun onTransferRequest(pc: PeerConn, direction: Int, token: Long, file: String, size: Long) {
        if (direction != 1) {
            pc.send(Writer.message(41) { u32(token); bool(false); str("File not shared.") })
            return
        }
        val t = _transfers.value.firstOrNull {
            it.user == pc.user && it.path == file &&
                (it.state == TransferState.QUEUED || it.state == TransferState.CONNECTING || it.state == TransferState.STARTING)
        }
        if (t == null) {
            pc.send(Writer.message(41) { u32(token); bool(false); str("Cancelled") })
            return
        }
        update(t.id) { it.copy(state = TransferState.STARTING, token = token, size = if (size > 0) size else it.size, place = null) }
        pc.send(Writer.message(41) { u32(token); bool(true) })
    }

    private suspend fun fileConnection(user: String, c: Conn) {
        var transferId: Long? = null
        var tmp: Path? = null
        try {
            val tokenBytes = ByteArray(4)
            withTimeout(60_000) { c.input.readFully(tokenBytes, 0, 4) }      // FileTransferInit
            val token = Reader(tokenBytes).u32()
            val t = _transfers.value.firstOrNull { it.user == user && it.token == token && it.state == TransferState.STARTING }
                ?: run { c.close(); return }
            transferId = t.id
            activeSockets.update { it + (t.id to c) }
            c.send(Writer().u64(0).toByteArray())                            // FileOffset
            update(t.id) { it.copy(state = TransferState.TRANSFERRING) }

            val path = Path(sink.tempDir, "slsk_${t.id}_${nowMillis()}.part").also { tmp = it }
            val buf = ByteArray(64 * 1024)
            var got = 0L
            var lastUi = 0L
            var windowStart = nowMillis()
            var windowBytes = 0L
            var speed = 0L
            SystemFileSystem.sink(path).buffered().use { out ->
                while (got < t.size) {
                    val want = minOf(buf.size.toLong(), t.size - got).toInt()
                    val n = withTimeoutOrNull(60_000) { c.input.readAvailable(buf, 0, want) } ?: -1
                    if (n < 0) break
                    if (n == 0) continue
                    out.write(buf, 0, n)
                    got += n
                    windowBytes += n
                    val now = nowMillis()
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
            c.close()
            takeActive(t.id)
            if (_transfers.value.firstOrNull { it.id == t.id }?.state == TransferState.CANCELLED) return
            if (got < t.size) {
                update(t.id) { it.copy(state = TransferState.FAILED, bytes = got, speed = 0, error = S.connectionDropped(got * 100 / t.size.coerceAtLeast(1))) }
                return
            }
            update(t.id) { it.copy(state = TransferState.SAVING, bytes = got, speed = 0) }
            val saved = sink.save(t, path.toString())
            update(t.id) { it.copy(state = TransferState.DONE, savedAs = saved) }
        } catch (e: Throwable) {
            transferId?.let { id ->
                update(id) {
                    if (it.state == TransferState.CANCELLED) it
                    else it.copy(state = TransferState.FAILED, speed = 0, error = e.message ?: S.downloadError)
                }
            }
        } finally {
            transferId?.let { takeActive(it) }
            tmp?.let { runCatching { SystemFileSystem.delete(it, mustExist = false) } }
            c.close()
            checkFinished()
        }
    }

    private fun update(id: Long, f: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun updateWhere(user: String, path: String, f: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.user == user && it.path == path) f(it) else it } }
    }

    private val hadActive = MutableStateFlow(false)

    private fun checkFinished() {
        val active = _transfers.value.any { it.state.active }
        if (!active && hadActive.value) {
            hadActive.value = false
            onDownloadsFinished()
        }
        if (active) hadActive.value = true
    }

    override fun debugSet(state: SlskState, results: List<UserResult>, transfers: List<Transfer>) {
        _state.value = state
        _results.value = results
        _transfers.value = transfers
    }

    companion object {
        const val SERVER_HOST = "server.slsknet.org"
        const val SERVER_PORT = 2242
        private const val MAJOR_VERSION = 177
        private const val MINOR_VERSION = 1
        val LISTEN_PORTS = 2234..2240
        private const val PEER_IDLE_MS = 120_000L
        /** Usuarios que sólo nos enviaron resultados de búsqueda. */
        private const val SEARCH_PEER_IDLE_MS = 15_000L
        /** Tope de sockets a la vez: muy por debajo de los 1024 descriptores que admite select() en iOS. */
        const val MAX_SOCKETS = 250
        private const val SOCKET_SLACK = 50
        private const val SEARCH_WINDOW_MS = 20_000L
        private const val MAX_RESULT_FILES = 15_000
    }
}

/** Lee un marco «longitud uint32 LE + contenido». Rechaza longitudes absurdas (datos basura). */
suspend fun ByteReadChannel.readFrame(max: Int = MAX_MESSAGE): ByteArray {
    val b = ByteArray(4)
    readFully(b, 0, 4)
    val len = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
        ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    if (len < 0 || len > max) throw IllegalStateException("Mensaje de $len bytes")
    val body = ByteArray(len.toInt())
    if (len > 0) readFully(body, 0, body.size)
    return body
}
