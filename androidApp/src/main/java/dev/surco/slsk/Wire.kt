package dev.surco.slsk

import java.io.DataInputStream
import java.io.InputStream

// La codificación de mensajes (Writer/Reader) vive en el módulo común `shared`.
// Aquí sólo queda lo que depende de los flujos de Java (sockets de Android).

/** Lee un marco «longitud uint32 + contenido» de un flujo. Rechaza longitudes absurdas (datos basura). */
fun DataInputStream.readFrame(max: Int = MAX_MESSAGE): ByteArray {
    val b = ByteArray(4)
    readFully(b)
    val len = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
        ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    if (len < 0 || len > max) throw IllegalStateException("Mensaje de $len bytes")
    val body = ByteArray(len.toInt())
    readFully(body)
    return body
}

fun InputStream.readExactly(n: Int): ByteArray {
    val b = ByteArray(n)
    DataInputStream(this).readFully(b)
    return b
}
