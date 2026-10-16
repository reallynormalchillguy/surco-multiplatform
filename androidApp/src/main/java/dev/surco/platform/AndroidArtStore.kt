package dev.surco.platform

import dev.surco.data.ArtStore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Portadas en el almacenamiento interno de la app (Android: BitmapFactory para normalizar a JPEG).
 */
class AndroidArtStore(context: Context) : ArtStore {
    private val albumDir = File(context.filesDir, "art").apply { mkdirs() }
    private val artistDir = File(context.filesDir, "artists").apply { mkdirs() }

    /** Normaliza cualquier imagen a JPEG de como mucho [max] px de lado. Devuelve null si no es una imagen. */
    override fun toJpeg(bytes: ByteArray, max: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scaled = if (longest > max) {
            val f = max.toFloat() / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return out.toByteArray()
    }

    override fun writeAlbumArt(albumKey: String, jpeg: ByteArray): String {
        val f = File(albumDir, "${md5(albumKey)}_${System.currentTimeMillis()}.jpg")
        f.writeBytes(jpeg)
        return f.absolutePath
    }

    override fun writeArtistImage(name: String, jpeg: ByteArray): String {
        val f = File(artistDir, "${md5(name)}_${System.currentTimeMillis()}.jpg")
        f.writeBytes(jpeg)
        return f.absolutePath
    }

    override fun deleteArtistImage(path: String?) {
        if (path == null) return
        val f = File(path)
        if (f.parentFile == artistDir) f.delete()
    }

    override fun delete(path: String?) {
        if (path == null) return
        val f = File(path)
        if (f.parentFile == albumDir) f.delete()
    }

    override fun exists(path: String?) = path != null && File(path).exists()

    companion object {
        fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Lee ancho/alto de una imagen sin decodificarla entera. */
        fun size(bytes: ByteArray): Pair<Int, Int> {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            return o.outWidth to o.outHeight
        }
    }
}
