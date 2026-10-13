package dev.surco.slsk

/**
 * Codificación de mensajes de Soulseek: enteros little-endian, cadenas con longitud uint32 delante.
 * Kotlin puro (Android e iOS). Ver https://nicotine-plus.org/doc/SLSKPROTOCOL.html
 */

/** Búfer de bytes que crece solo (ByteArrayOutputStream no existe en iOS). */
class ByteBuilder(initial: Int = 64) {
    private var buf = ByteArray(initial)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        var n = buf.size * 2
        while (n < size + extra) n *= 2
        buf = buf.copyOf(n)
    }

    fun add(b: Int) {
        ensure(1)
        buf[size++] = b.toByte()
    }

    fun add(b: ByteArray, from: Int = 0, len: Int = b.size - from) {
        ensure(len)
        b.copyInto(buf, size, from, from + len)
        size += len
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

class Writer {
    private val out = ByteBuilder()

    fun u8(v: Int) = apply { out.add(v and 0xFF) }
    fun bool(v: Boolean) = u8(if (v) 1 else 0)
    fun u32(v: Long) = apply { for (i in 0 until 4) out.add(((v shr (8 * i)) and 0xFF).toInt()) }
    fun u32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)
    fun u64(v: Long) = apply { for (i in 0 until 8) out.add(((v shr (8 * i)) and 0xFF).toInt()) }
    fun str(s: String) = bytes(s.encodeToByteArray())
    fun bytes(b: ByteArray) = apply { u32(b.size); out.add(b) }
    fun raw(b: ByteArray) = apply { out.add(b) }

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        /** Mensaje de servidor o de par: longitud + código uint32 + contenido. */
        fun message(code: Int, body: Writer.() -> Unit = {}): ByteArray {
            val content = Writer().u32(code).apply(body).toByteArray()
            return Writer().u32(content.size).raw(content).toByteArray()
        }

        /** Mensaje de inicio de conexión con par: longitud + código uint8 + contenido. */
        fun initMessage(code: Int, body: Writer.() -> Unit): ByteArray {
            val content = Writer().u8(code).apply(body).toByteArray()
            return Writer().u32(content.size).raw(content).toByteArray()
        }

        fun zlib(data: ByteArray): ByteArray = zlibDeflate(data)
    }
}

class WireEofException(message: String) : RuntimeException(message)

class Reader(private val data: ByteArray, offset: Int = 0) {
    private var pos = offset

    val remaining get() = data.size - pos

    private fun need(n: Int) {
        if (n < 0 || n > remaining) throw WireEofException("Faltan bytes: se piden $n, quedan $remaining")
    }

    private fun byte(): Int = data[pos++].toInt() and 0xFF

    fun u8(): Int { need(1); return byte() }
    fun bool(): Boolean = u8() != 0
    fun u32(): Long {
        need(4)
        var v = 0L
        for (i in 0 until 4) v = v or (byte().toLong() shl (8 * i))
        return v
    }
    fun i32(): Int = u32().toInt()
    fun u16(): Int { need(2); return byte() or (byte() shl 8) }
    fun u64(): Long {
        need(8)
        var v = 0L
        for (i in 0 until 8) v = v or (byte().toLong() shl (8 * i))
        return v
    }
    fun bytes(): ByteArray {
        val n = u32()
        if (n > remaining) throw WireEofException("Cadena de $n bytes, quedan $remaining")
        val b = data.copyOfRange(pos, pos + n.toInt())
        pos += n.toInt()
        return b
    }
    fun str(): String = decode(bytes())
    fun skip(n: Int) { need(n); pos += n }
    fun rest(): ByteArray { val b = data.copyOfRange(pos, data.size); pos = data.size; return b }

    companion object {
        /** UTF-8 y, si no lo es, Latin-1 (como hacen Nicotine+ y los clientes antiguos). */
        fun decode(b: ByteArray): String = try {
            b.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            latin1(b)
        } catch (_: IllegalArgumentException) {
            latin1(b)
        }

        private fun latin1(b: ByteArray): String {
            val chars = CharArray(b.size) { (b[it].toInt() and 0xFF).toChar() }
            return chars.concatToString()
        }

        fun inflate(data: ByteArray, max: Int = 16 * 1024 * 1024): ByteArray = zlibInflate(data, max)
    }
}

const val MAX_MESSAGE = 32 * 1024 * 1024

/** Tamaño máximo de un mensaje de otro usuario (los resultados de búsqueda vienen comprimidos y son pequeños). */
const val MAX_PEER_MESSAGE = 8 * 1024 * 1024

/** IP en uint32 tal como la manda el servidor (byte más significativo primero en la red). */
fun ipToString(ip: Long): String =
    "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"

/** Compresión zlib (cada plataforma usa la suya: java.util.zip en Android, libz en iOS). */
expect fun zlibDeflate(data: ByteArray): ByteArray

/** Descompresión zlib con límite de tamaño, para no reservar memoria con datos basura. */
expect fun zlibInflate(data: ByteArray, max: Int): ByteArray
