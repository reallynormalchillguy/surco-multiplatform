package dev.surco.ios

import dev.surco.tags.ByteSource
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToEndOfFile
import platform.Foundation.seekToFileOffset

/** Lectura aleatoria de un archivo con NSFileHandle. Hay que cerrarlo con [close]. */
@OptIn(ExperimentalForeignApi::class)
class IosFileSource private constructor(private val h: NSFileHandle) : ByteSource {
    override val size: Long = h.seekToEndOfFile().toLong()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset >= size || length <= 0) return ByteArray(0)
        h.seekToFileOffset(offset.toULong())
        return h.readDataOfLength(length.toULong()).toByteArray()
    }

    fun close() = h.closeFile()

    companion object {
        fun open(url: NSURL): IosFileSource? =
            NSFileHandle.fileHandleForReadingFromURL(url, null)?.let { IosFileSource(it) }
    }
}

inline fun <T> NSURL.withSource(block: (IosFileSource) -> T): T? {
    val s = IosFileSource.open(this) ?: return null
    return try { block(s) } finally { s.close() }
}
