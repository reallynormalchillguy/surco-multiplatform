package dev.surco.slsk

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

actual fun zlibDeflate(data: ByteArray): ByteArray {
    val d = Deflater()
    d.setInput(data); d.finish()
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (!d.finished()) out.write(buf, 0, d.deflate(buf))
    d.end()
    return out.toByteArray()
}

actual fun zlibInflate(data: ByteArray, max: Int): ByteArray {
    val inf = Inflater()
    inf.setInput(data)
    val out = ByteArrayOutputStream()
    val tmp = ByteArray(16384)
    try {
        while (!inf.finished()) {
            val n = inf.inflate(tmp)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(tmp, 0, n)
            if (out.size() > max) throw IllegalStateException("Mensaje comprimido demasiado grande")
        }
    } finally {
        inf.end()
    }
    return out.toByteArray()
}
