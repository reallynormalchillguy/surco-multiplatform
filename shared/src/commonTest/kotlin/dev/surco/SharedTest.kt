package dev.surco

import dev.surco.data.LrcParser
import dev.surco.data.indexAt
import dev.surco.slsk.Reader
import dev.surco.slsk.Writer
import dev.surco.slsk.ipToString
import dev.surco.util.Md5
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Pruebas del código común. Se ejecutan en Android (JVM) y en el simulador de iPhone. */
class SharedTest {

    private fun hex(s: String) = s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    /** Ejemplo literal de la documentación del protocolo (Server Code 1, Login). */
    @Test
    fun loginMessageMatchesDocumentation() {
        val expected = hex(
            "48 00 00 00 01 00 00 00 08 00 00 00 75 73 65 72 6e 61 6d 65 08 00 00 00 70 61 73 73 77 6f 72 64 b1 00 00 00 20 00 " +
                "00 00 64 35 31 63 39 61 37 65 39 33 35 33 37 34 36 61 36 30 32 30 66 39 36 30 32 64 34 35 32 39 32 39 01 00 00 00"
        )
        val msg = Writer.message(1) {
            str("username"); str("password"); u32(177); str(Md5.hex("usernamepassword")); u32(1)
        }
        assertContentEquals(expected, msg)
    }

    @Test
    fun md5MatchesKnownValues() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.hex(""))
        assertEquals("d51c9a7e9353746a6020f9602d452929", Md5.hex("usernamepassword"))
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", Md5.hex("The quick brown fox jumps over the lazy dog"))
    }

    @Test
    fun peerInitAndRoundTrip() {
        val init = Writer.initMessage(1) { str("ana"); str("P"); u32(0) }
        val r = Reader(init)
        assertEquals(init.size - 4L, r.u32())
        assertEquals(1, r.u8())
        assertEquals("ana", r.str())
        assertEquals("P", r.str())
        assertEquals(0L, r.u32())

        val r2 = Reader(Writer().u64(5_000_000_000L).u32(0xFFFFFFFFL).toByteArray())
        assertEquals(5_000_000_000L, r2.u64())
        assertEquals(0xFFFFFFFFL, r2.u32())
    }

    @Test
    fun zlibRoundTripAndLatin1Fallback() {
        val payload = Writer().str("Canción").u32(7).toByteArray()
        assertContentEquals(payload, Reader.inflate(Writer.zlib(payload)))
        val big = ByteArray(200_000) { (it % 251).toByte() }
        assertContentEquals(big, Reader.inflate(Writer.zlib(big)))
        // «Canción» en Latin-1 (clientes antiguos): no es UTF-8 válido.
        val latin = byteArrayOf(4, 0, 0, 0) + byteArrayOf(0x43, 0x61, 0x6e, 0x63) // "Canc"
        assertEquals("Canc", Reader(latin).str())
        val accented = Writer().bytes(byteArrayOf(0x43, 0xF3.toByte(), 0x6E)).toByteArray() // "Cón" en Latin-1
        assertEquals("Cón", Reader(accented).str())
    }

    @Test
    fun ipIsDecodedMostSignificantByteFirst() {
        assertEquals("192.168.1.10", ipToString(Reader(hex("0a 01 a8 c0")).u32()))
    }

    @Test
    fun lrcParsing() {
        val text = """
            [ar:Alguien]
            [offset:+500]
            [00:01.00]Uno
            [00:05.5]Dos
            [00:10.123][00:20.00]<00:10.20>Tres <00:10.50>palabras
            Sin tiempo
        """.trimIndent()
        val lines = LrcParser.parse(text)
        assertEquals(listOf(500L, 5000L, 9623L, 19500L), lines.map { it.timeMs })
        assertEquals("Tres palabras", lines[2].text)
        assertEquals(-1, lines.indexAt(100))
        assertEquals(1, lines.indexAt(6000))
        assertEquals(3, lines.indexAt(60_000))
    }
}
