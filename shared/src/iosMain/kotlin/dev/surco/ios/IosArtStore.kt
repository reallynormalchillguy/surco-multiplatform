package dev.surco.ios

import dev.surco.data.ArtStore
import dev.surco.util.Md5
import dev.surco.util.nowMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** Portadas en Application Support, normalizadas a JPEG con UIKit. */
@OptIn(ExperimentalForeignApi::class)
class IosArtStore : ArtStore {
    override fun toJpeg(bytes: ByteArray, max: Int): ByteArray? {
        if (bytes.isEmpty()) return null
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
        val (w, h) = image.size.useContents { width to height }
        if (w <= 0 || h <= 0) return null
        val longest = maxOf(w, h)
        val scaled = if (longest > max) {
            val f = max / longest
            val size = CGSizeMake(maxOf(1.0, w * f), maxOf(1.0, h * f))
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
            UIGraphicsImageRenderer(size, format).imageWithActions { _ ->
                image.drawInRect(CGRectMake(0.0, 0.0, size.useContents { width }, size.useContents { height }))
            }
        } else image
        return UIImageJPEGRepresentation(scaled, 0.92)?.toByteArray()
    }

    override fun writeAlbumArt(albumKey: String, jpeg: ByteArray): String {
        val path = "${IosDirs.art}/${Md5.hex(albumKey)}_${nowMillis()}.jpg"
        jpeg.toNSData().writeToFile(path, true)
        return path
    }

    override fun writeArtistImage(name: String, jpeg: ByteArray): String {
        val path = "${IosDirs.artists}/${Md5.hex(name)}_${nowMillis()}.jpg"
        jpeg.toNSData().writeToFile(path, true)
        return path
    }

    override fun deleteArtistImage(path: String?) {
        if (path == null || !path.startsWith(IosDirs.artists)) return
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    override fun delete(path: String?) {
        if (path == null || !path.startsWith(IosDirs.art)) return
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    override fun exists(path: String?) = path != null && NSFileManager.defaultManager.fileExistsAtPath(path)
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
