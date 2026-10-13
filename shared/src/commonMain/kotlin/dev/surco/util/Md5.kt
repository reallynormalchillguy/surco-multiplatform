package dev.surco.util

/**
 * MD5 en Kotlin puro (iOS no tiene java.security). Lo usan el inicio de sesión de Soulseek
 * y la firma de las peticiones a Last.fm. No es para seguridad: ambos protocolos lo exigen.
 */
object Md5 {
    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val K = IntArray(64) { i ->
        // floor(abs(sin(i + 1)) * 2^32)
        (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    fun digest(input: ByteArray): ByteArray {
        val bitLen = input.size.toLong() * 8
        val padLen = ((56 - (input.size + 1) % 64) + 64) % 64
        val msg = ByteArray(input.size + 1 + padLen + 8)
        input.copyInto(msg)
        msg[input.size] = 0x80.toByte()
        for (i in 0 until 8) msg[msg.size - 8 + i] = (bitLen ushr (8 * i)).toByte()

        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val m = IntArray(16)
        for (chunk in msg.indices step 64) {
            for (i in 0 until 16) {
                val j = chunk + i * 4
                m[i] = (msg[j].toInt() and 0xFF) or ((msg[j + 1].toInt() and 0xFF) shl 8) or
                    ((msg[j + 2].toInt() and 0xFF) shl 16) or ((msg[j + 3].toInt() and 0xFF) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                var f: Int
                val g: Int
                when (i / 16) {
                    0 -> { f = (b and c) or (b.inv() and d); g = i }
                    1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                f += a + K[i] + m[g]
                a = d; d = c; c = b
                b += f.rotateLeft(S[i])
            }
            a0 += a; b0 += b; c0 += c; d0 += d
        }
        val out = ByteArray(16)
        intArrayOf(a0, b0, c0, d0).forEachIndexed { w, v -> for (i in 0 until 4) out[w * 4 + i] = (v ushr (8 * i)).toByte() }
        return out
    }

    fun hex(s: String): String = digest(s.encodeToByteArray()).joinToString("") {
        val v = it.toInt() and 0xFF
        "0123456789abcdef"[v shr 4].toString() + "0123456789abcdef"[v and 0xF]
    }
}
