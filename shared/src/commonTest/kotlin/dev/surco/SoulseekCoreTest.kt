package dev.surco

import dev.surco.slsk.DownloadSink
import dev.surco.slsk.Reader
import dev.surco.slsk.SlskFile
import dev.surco.slsk.SlskState
import dev.surco.slsk.SoulseekCore
import dev.surco.slsk.Transfer
import dev.surco.slsk.TransferState
import dev.surco.slsk.Writer
import dev.surco.slsk.readFrame
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * El cliente común de Soulseek contra un servidor y un usuario falsos en localhost:
 * inicio de sesión, búsqueda con respuesta comprimida y descarga completa por una conexión «F».
 */
class SoulseekCoreTest {
    private fun le32(b: ByteArray) = Reader(b).u32()

    @Test
    fun loginSearchAndDownload() = runTest(timeout = 90.seconds) {
        withContext(Dispatchers.Default) {
            val selector = SelectorManager(Dispatchers.Default)
            val serverSocket = aSocket(selector).tcp().bind("127.0.0.1", 0)
            val serverPort = (serverSocket.localAddress as InetSocketAddress).port
            val waitPort = CompletableDeferred<Int>()
            val searchToken = CompletableDeferred<Long>()
            val saved = CompletableDeferred<ByteArray>()
            val fileBytes = ByteArray(300_000) { (it % 251).toByte() }

            // ---- Servidor falso
            launch {
                val s = serverSocket.accept()
                val input = s.openReadChannel()
                val out = s.openWriteChannel(autoFlush = true)
                val login = Reader(input.readFrame())
                assertEquals(1L, login.u32())
                assertEquals("surco_test", login.str())
                out.writeFully(Writer.message(1) { bool(true); str("Bienvenido"); u32(0x7F000001L); str("hash"); bool(false) })
                while (true) {
                    val r = Reader(runCatching { input.readFrame() }.getOrNull() ?: break)
                    when (r.u32().toInt()) {
                        2 -> waitPort.complete(r.u32().toInt())
                        26 -> searchToken.complete(r.u32())
                    }
                }
            }

            val sink = object : DownloadSink {
                override val tempDir = SystemTemporaryDirectory.toString()
                override suspend fun save(t: Transfer, tempPath: String): String {
                    saved.complete(SystemFileSystem.source(Path(tempPath)).buffered().use { it.readByteArray() })
                    return "Album/${t.name}"
                }
            }
            val client = SoulseekCore(sink, onDownloadsFinished = {}, serverHost = "127.0.0.1", serverPort = serverPort, listenPorts = IntRange.EMPTY)
            client.connect("surco_test", "secreto")
            withTimeout(15_000) { client.state.first { it is SlskState.Online } }
            val port = withTimeout(10_000) { waitPort.await() }

            // ---- Búsqueda: «bob» se conecta (PeerInit «P») y responde con resultados comprimidos.
            client.search("radiohead kid a")
            val token = withTimeout(10_000) { searchToken.await() }
            val peer = aSocket(selector).tcp().connect("127.0.0.1", port)
            val pin = peer.openReadChannel()
            val pout = peer.openWriteChannel(autoFlush = true)
            pout.writeFully(Writer.initMessage(1) { str("bob"); str("P"); u32(0) })
            val path = "@@music\\Radiohead\\Kid A\\01 Everything.flac"
            val payload = Writer().str("bob").u32(token).u32(1)
                .u8(1).str(path).u64(fileBytes.size.toLong()).str("flac").u32(2).u32(4).u32(44100).u32(5).u32(16)
                .bool(true).u32(1_000_000).u32(0).toByteArray()
            pout.writeFully(Writer.message(9) { raw(Writer.zlib(payload)) })
            val results = withTimeout(10_000) { client.results.first { it.isNotEmpty() } }
            val f = results.single().files.single()
            assertEquals("01 Everything.flac", f.name)
            assertEquals(44100, f.sampleRate)
            assertEquals(16, f.bitDepth)

            // ---- Descarga: QueueUpload → TransferRequest → conexión «F» → FileOffset → datos.
            client.download(listOf(SlskFile("bob", path, fileBytes.size.toLong())))
            val q = Reader(withTimeout(10_000) { pin.readFrame() })
            assertEquals(43L, q.u32())
            assertEquals(path, q.str())
            pout.writeFully(Writer.message(40) { u32(1); u32(777); str(path); u64(fileBytes.size.toLong()) })
            // Puede llegar antes PlaceInQueueRequest (51): lo saltamos hasta ver la respuesta (41).
            var reply: Reader
            do { reply = Reader(withTimeout(10_000) { pin.readFrame() }) } while (reply.u32() != 41L)
            assertEquals(777L, reply.u32())
            assertEquals(true, reply.bool())

            val fsock = aSocket(selector).tcp().connect("127.0.0.1", port)
            val fin = fsock.openReadChannel()
            val fout = fsock.openWriteChannel(autoFlush = true)
            fout.writeFully(Writer.initMessage(1) { str("bob"); str("F"); u32(0) })
            fout.writeFully(Writer().u32(777).toByteArray())
            val offset = ByteArray(8)
            withTimeout(10_000) { fin.readFully(offset, 0, 8) }
            assertEquals(0L, le32(offset))
            fout.writeFully(fileBytes)
            fsock.close()

            assertContentEquals(fileBytes, withTimeout(20_000) { saved.await() })
            val done = withTimeout(10_000) { client.transfers.first { l -> l.any { it.state == TransferState.DONE } } }
            assertEquals("Album/01 Everything.flac", done.single().savedAs)

            client.disconnect()
            peer.close()
            serverSocket.close()
            selector.close()
        }
    }
}
