package dev.surco

import dev.surco.slsk.DownloadSink
import dev.surco.slsk.Reader
import dev.surco.slsk.SlskState
import dev.surco.slsk.SoulseekCore
import dev.surco.slsk.Transfer
import dev.surco.slsk.Writer
import dev.surco.slsk.readFrame
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.posix.RLIMIT_NOFILE
import platform.posix.getrlimit
import platform.posix.rlimit
import platform.posix.setrlimit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Una búsqueda real hace que cientos de usuarios se conecten a la vez para mandar resultados.
 * En iOS el límite de archivos abiertos es bajo: el cliente no debe cerrar la app por ello.
 */
@OptIn(ExperimentalForeignApi::class)
class SoulseekStressTest {

    private fun fdLimit(): Long = memScoped {
        val r = alloc<rlimit>()
        getrlimit(RLIMIT_NOFILE, r.ptr)
        r.rlim_cur.toLong()
    }

    private fun setFdLimit(n: Long) = memScoped {
        val r = alloc<rlimit>()
        getrlimit(RLIMIT_NOFILE, r.ptr)
        r.rlim_cur = n.toULong()
        setrlimit(RLIMIT_NOFILE, r.ptr)
    }

    @Test
    fun manyPeersAnsweringASearch() = runTest(timeout = 120.seconds) {
        withContext(Dispatchers.Default) {
            setFdLimit(4096)
            println("Límite de archivos abiertos (RLIMIT_NOFILE): ${fdLimit()}")
            val selector = SelectorManager(Dispatchers.Default)
            val serverSocket = aSocket(selector).tcp().bind("127.0.0.1", 0)
            val serverPort = (serverSocket.localAddress as InetSocketAddress).port
            val waitPort = CompletableDeferred<Int>()
            val searchToken = CompletableDeferred<Long>()
            launch {
                val s = serverSocket.accept()
                val input = s.openReadChannel()
                val out = s.openWriteChannel(autoFlush = true)
                Reader(input.readFrame())
                out.writeFully(Writer.message(1) { bool(true); str("hola"); u32(0x7F000001L); str("x"); bool(false) })
                while (true) {
                    val r = Reader(runCatching { input.readFrame() }.getOrNull() ?: break)
                    when (r.u32().toInt()) {
                        2 -> waitPort.complete(r.u32().toInt())
                        26 -> searchToken.complete(r.u32())
                    }
                }
            }
            val sink = object : DownloadSink {
                override val tempDir = "/tmp"
                override suspend fun save(t: Transfer, tempPath: String) = t.name
            }
            val client = SoulseekCore(sink, {}, serverHost = "127.0.0.1", serverPort = serverPort, listenPorts = IntRange.EMPTY)
            client.connect("surco_test", "x")
            withTimeout(15_000) { client.state.first { it is SlskState.Online } }
            val port = waitPort.await()
            client.search("algo")
            val token = searchToken.await()

            val peers = ArrayList<Socket>()
            var opened = 0
            var refused = 0
            repeat(400) { i ->
                val s = runCatching { aSocket(selector).tcp().connect("127.0.0.1", port) }.getOrNull()
                if (s == null) { refused++; return@repeat }
                peers += s
                opened++
                runCatching {
                    val out = s.openWriteChannel(autoFlush = true)
                    out.writeFully(Writer.initMessage(1) { str("user$i"); str("P"); u32(0) })
                    val payload = Writer().str("user$i").u32(token).u32(1)
                        .u8(1).str("@@m\\Disco $i\\01.flac").u64(30_000_000).str("flac").u32(0)
                        .bool(true).u32(100_000).u32(0).toByteArray()
                    out.writeFully(Writer.message(9) { raw(Writer.zlib(payload)) })
                }
            }
            println("Conexiones abiertas: $opened, rechazadas: $refused")
            delay(3_000)
            val n = client.results.value.size
            println("Resultados recibidos: $n; estado: ${client.state.value}")
            assertTrue(n > 0, "Debe haber resultados")
            println("Sockets abiertos en el cliente: ${client.socketCount}")
            assertTrue(client.socketCount <= SoulseekCore.MAX_SOCKETS + 1, "El cliente respeta el tope de sockets")
            // El proceso sigue vivo y el cliente sigue en línea.
            assertTrue(client.state.value is SlskState.Online, "Sigue en línea")
            peers.forEach { runCatching { it.close() } }
            client.disconnect()
            serverSocket.close()
        }
    }

    /**
     * Reproduce el cierre de iOS: el selector de Ktor en Kotlin/Native usa select() y lanza una excepción
     * (sin capturar → la app se cierra) si un socket recibe un descriptor ≥ FD_SETSIZE (1024).
     * Ocupamos ~1030 descriptores y luego el cliente intenta conectarse.
     */
    @Test
    fun socketWithHighDescriptorDoesNotKillTheApp() = runTest(timeout = 120.seconds) {
        withContext(Dispatchers.Default) {
            setFdLimit(4096)
            // Servidor que acepta la conexión a nivel TCP (cola del kernel) sin llamar nunca a accept().
            val listener = aSocket(SelectorManager(Dispatchers.Default)).tcp().bind("127.0.0.1", 0)
            val port = (listener.localAddress as InetSocketAddress).port
            val dummies = (0 until 1030).map { platform.posix.open("/dev/null", platform.posix.O_RDONLY) }
            println("Descriptor más alto ocupado: ${dummies.max()}")
            try {
                val sink = object : DownloadSink {
                    override val tempDir = "/tmp"
                    override suspend fun save(t: Transfer, tempPath: String) = t.name
                }
                val client = SoulseekCore(sink, {}, serverHost = "127.0.0.1", serverPort = port, listenPorts = IntRange.EMPTY)
                client.connect("surco_test", "x")
                // En los simuladores de CI, lentos, el tiempo de espera del inicio de sesión puede pasar de 20 s.
                val st = withTimeout(60_000) { client.state.first { it is SlskState.Failed } }
                println("Estado tras el fallo: $st")
                client.disconnect()
            } finally {
                dummies.forEach { platform.posix.close(it) }
                listener.close()
            }
        }
    }
}
