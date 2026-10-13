@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.surco.slsk

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

private const val CHUNK = 16384

actual fun zlibDeflate(data: ByteArray): ByteArray = memScoped {
    val zs = alloc<z_stream>()
    val version = zlibVersion()?.toKString()
    check(deflateInit_(zs.ptr, Z_DEFAULT_COMPRESSION, version, sizeOf<z_stream>().toInt()) == Z_OK) { "deflateInit" }
    val out = ByteBuilder()
    val chunk = ByteArray(CHUNK)
    val input = if (data.isEmpty()) ByteArray(1) else data
    try {
        input.usePinned { inPin ->
            chunk.usePinned { outPin ->
                zs.next_in = inPin.addressOf(0).reinterpret<UByteVar>()
                zs.avail_in = data.size.convert()
                do {
                    zs.next_out = outPin.addressOf(0).reinterpret<UByteVar>()
                    zs.avail_out = CHUNK.convert()
                    val r = deflate(zs.ptr, Z_FINISH)
                    val have = CHUNK - zs.avail_out.toInt()
                    out.add(chunk, 0, have)
                } while (r != Z_STREAM_END)
            }
        }
    } finally {
        deflateEnd(zs.ptr)
    }
    out.toByteArray()
}

actual fun zlibInflate(data: ByteArray, max: Int): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    return memScoped {
        val zs = alloc<z_stream>()
        val version = zlibVersion()?.toKString()
        check(inflateInit_(zs.ptr, version, sizeOf<z_stream>().toInt()) == Z_OK) { "inflateInit" }
        val out = ByteBuilder()
        val chunk = ByteArray(CHUNK)
        try {
            data.usePinned { inPin ->
                chunk.usePinned { outPin ->
                    zs.next_in = inPin.addressOf(0).reinterpret<UByteVar>()
                    zs.avail_in = data.size.convert()
                    while (true) {
                        zs.next_out = outPin.addressOf(0).reinterpret<UByteVar>()
                        zs.avail_out = CHUNK.convert()
                        val r = inflate(zs.ptr, Z_NO_FLUSH)
                        if (r != Z_OK && r != Z_STREAM_END) throw IllegalStateException("Datos zlib no válidos ($r)")
                        val have = CHUNK - zs.avail_out.toInt()
                        out.add(chunk, 0, have)
                        if (out.size > max) throw IllegalStateException("Mensaje comprimido demasiado grande")
                        if (r == Z_STREAM_END) break
                        if (zs.avail_in.toInt() == 0 && have == 0) break
                    }
                }
            }
        } finally {
            inflateEnd(zs.ptr)
        }
        out.toByteArray()
    }
}
