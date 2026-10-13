package dev.surco.tags

import dev.surco.i18n.S

/**
 * Nueva cabecera de etiquetas para un archivo: el archivo final es [header] seguido de los bytes
 * originales desde [audioOffset] hasta el final (el audio no se toca).
 */
class Rewrite(val header: ByteArray, val audioOffset: Long)

/**
 * Escritor de etiquetas en Kotlin puro para FLAC (Vorbis comment + PICTURE) y MP3 (ID3v2.4).
 * Conserva todo lo que no se edita (ReplayGain, comentarios, otras imágenes…).
 */
object TagFileWriter {
    fun rewrite(src: ByteSource, fileName: String, edits: TagEdits): Rewrite {
        val head = src.read(0, 16)
        val id3Size = Id3.tagSize(head)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            src.read(id3Size, 4).startsWith("fLaC") -> flac(src, id3Size, edits)
            head.size >= 8 && head.ascii(4, 4) == "ftyp" -> throw TagWriteException(S.formatNotSupported(ext.ifEmpty { "mp4" }))
            id3Size > 0 || ext == "mp3" || ext == "mp2" -> mp3(src, id3Size, edits)
            else -> throw TagWriteException(S.formatNotSupported(ext))
        }
    }

    // ---------------------------------------------------------------- FLAC

    private val vorbisKeys = listOf(
        "TITLE" to TagEdits::title, "ARTIST" to TagEdits::artist, "ALBUM" to TagEdits::album,
        "ALBUMARTIST" to TagEdits::albumArtist, "DATE" to TagEdits::year, "TRACKNUMBER" to TagEdits::trackNumber,
        "DISCNUMBER" to TagEdits::discNumber, "GENRE" to TagEdits::genre, "LYRICS" to TagEdits::lyrics,
    )
    /** Otras claves que significan lo mismo y deben desaparecer al editar ese campo. */
    private val aliases = mapOf(
        "ALBUMARTIST" to setOf("ALBUM ARTIST", "ALBUM_ARTIST"), "DATE" to setOf("YEAR"),
        "TRACKNUMBER" to setOf("TRACK"), "DISCNUMBER" to setOf("DISC"), "LYRICS" to setOf("UNSYNCEDLYRICS", "UNSYNCED LYRICS"),
    )

    private fun flac(src: ByteSource, start: Long, e: TagEdits): Rewrite {
        val (blocks, audioStart) = Flac.blocks(src, start)
        val oldComment = blocks.firstOrNull { it.type == 4 }?.data
        var comments = oldComment?.let { Flac.rawComments(it) } ?: emptyList()
        val vendor = oldComment?.let { Flac.vendor(it) }?.ifEmpty { null } ?: "Surco"
        for ((key, prop) in vorbisKeys) {
            val v = prop.get(e) ?: continue
            val drop = setOf(key) + aliases[key].orEmpty()
            comments = comments.filter { it.first.uppercase() !in drop }
            if (v.isNotBlank()) comments = comments + (key to v.trim())
        }
        val out = Bytes()
        out.ascii("fLaC")
        val kept = blocks.filter { b ->
            when (b.type) {
                1, 4 -> false // relleno y comentarios se rehacen
                6 -> e.cover == null // portadas: sólo se conservan si no se cambia la portada
                else -> true
            }
        }
        val newBlocks = ArrayList<Pair<Int, ByteArray>>()
        kept.forEach { newBlocks += it.type to it.data }
        newBlocks += 4 to vorbisComment(vendor, comments)
        e.cover?.takeIf { it.isNotEmpty() }?.let { newBlocks += 6 to flacPicture(it) }
        newBlocks += 1 to ByteArray(4096) // relleno para futuras ediciones
        // STREAMINFO siempre el primero.
        newBlocks.sortBy { if (it.first == 0) 0 else 1 }
        newBlocks.forEachIndexed { i, (type, data) ->
            val last = i == newBlocks.lastIndex
            out.u8((if (last) 0x80 else 0) or type)
            out.be24(data.size)
            out.raw(data)
        }
        return Rewrite(out.toByteArray(), audioStart)
    }

    private fun vorbisComment(vendor: String, comments: List<Pair<String, String>>): ByteArray {
        val b = Bytes()
        val v = vendor.encodeToByteArray()
        b.le32(v.size); b.raw(v)
        b.le32(comments.size)
        comments.forEach { (k, value) ->
            val c = "$k=$value".encodeToByteArray()
            b.le32(c.size); b.raw(c)
        }
        return b.toByteArray()
    }

    private fun flacPicture(jpeg: ByteArray): ByteArray {
        val b = Bytes()
        val mime = mimeOf(jpeg).encodeToByteArray()
        b.be32(3) // portada frontal
        b.be32(mime.size); b.raw(mime)
        b.be32(0) // sin descripción
        b.be32(0); b.be32(0); b.be32(24); b.be32(0) // ancho, alto, profundidad, colores (opcionales)
        b.be32(jpeg.size); b.raw(jpeg)
        return b.toByteArray()
    }

    private fun mimeOf(img: ByteArray) = if (img.size > 4 && img.u8(0) == 0x89 && img.ascii(1, 3) == "PNG") "image/png" else "image/jpeg"

    // ---------------------------------------------------------------- MP3 (ID3v2.4)

    private val id3Keys = listOf(
        "TIT2" to TagEdits::title, "TPE1" to TagEdits::artist, "TALB" to TagEdits::album,
        "TPE2" to TagEdits::albumArtist, "TDRC" to TagEdits::year, "TRCK" to TagEdits::trackNumber,
        "TPOS" to TagEdits::discNumber, "TCON" to TagEdits::genre,
    )
    /** Tramas de v2.2/v2.3 equivalentes a cada una de v2.4. */
    private val oldIds = mapOf(
        "TIT2" to setOf("TT2"), "TPE1" to setOf("TP1"), "TALB" to setOf("TAL"), "TPE2" to setOf("TP2"),
        "TDRC" to setOf("TYER", "TYE", "TDAT", "TIME", "TRDA"), "TRCK" to setOf("TRK"), "TPOS" to setOf("TPA"),
        "TCON" to setOf("TCO"), "USLT" to setOf("ULT"), "APIC" to setOf("PIC"),
    )

    private fun mp3(src: ByteSource, id3Size: Long, e: TagEdits): Rewrite {
        val tag = if (id3Size > 0) runCatching { Id3.parse(src) }.getOrNull() else null
        val frames = ArrayList<Pair<String, ByteArray>>()
        if (tag != null && tag.version >= 3) {
            for (f in tag.frames) {
                // Tramas comprimidas o cifradas: no sabemos reescribirlas fielmente, se descartan.
                val compressedOrEncrypted = if (tag.version == 3) f.flags and 0x00C0 != 0 else f.flags and 0x000C != 0
                if (compressedOrEncrypted) continue
                val id = if (tag.version == 3 && f.id == "TYER") "TDRC" else f.id
                if (tag.version == 3 && f.id in setOf("TDAT", "TIME", "TRDA", "TSIZ")) continue // no existen en v2.4
                frames += id to f.data
            }
        } else if (tag != null) {
            // v2.2: copiamos los campos conocidos a sus tramas de v2.4.
            val t = Mp3.fromId3(tag)
            listOf("TIT2" to t.title, "TPE1" to t.artist, "TALB" to t.album, "TPE2" to t.albumArtist, "TDRC" to t.year,
                "TRCK" to t.trackNumber, "TPOS" to t.discNumber, "TCON" to t.genre).forEach { (id, v) ->
                if (v.isNotBlank()) frames += id to textFrame(v)
            }
            if (t.lyrics.isNotBlank()) frames += "USLT" to lyricsFrame(t.lyrics)
            t.cover?.let { frames += "APIC" to pictureFrame(it) }
        }

        fun replace(id: String, value: ByteArray?) {
            val ids = setOf(id) + oldIds[id].orEmpty()
            frames.removeAll { it.first in ids }
            if (value != null) frames += id to value
        }
        for ((id, prop) in id3Keys) {
            val v = prop.get(e) ?: continue
            replace(id, v.trim().takeIf { it.isNotEmpty() }?.let { textFrame(it) })
        }
        e.lyrics?.let { replace("USLT", it.takeIf { l -> l.isNotBlank() }?.let { l -> lyricsFrame(l) }) }
        e.cover?.let { c ->
            // Sólo sustituimos la portada frontal; el resto de imágenes (contraportada, artista…) se quedan.
            frames.removeAll { (it.first == "APIC" || it.first == "PIC") && pictureType(it.second) == 3 }
            if (c.isNotEmpty()) frames += "APIC" to pictureFrame(c)
        }

        val body = Bytes()
        frames.forEach { (id, data) ->
            body.ascii(id)
            body.synchsafe(data.size)
            body.u8(0); body.u8(0)
            body.raw(data)
        }
        val padding = 2048
        val out = Bytes()
        out.ascii("ID3"); out.u8(4); out.u8(0); out.u8(0)
        out.synchsafe(body.size + padding)
        out.raw(body.toByteArray())
        out.raw(ByteArray(padding))
        return Rewrite(out.toByteArray(), id3Size)
    }

    private fun pictureType(d: ByteArray): Int {
        if (d.isEmpty()) return -1
        var i = 1
        while (i < d.size && d[i] != 0.toByte()) i++
        return if (i + 1 < d.size) d.u8(i + 1) else -1
    }

    private fun textFrame(v: String): ByteArray = byteArrayOf(3) + v.encodeToByteArray()

    private fun lyricsFrame(v: String): ByteArray = byteArrayOf(3) + "XXX".encodeToByteArray() + byteArrayOf(0) + v.encodeToByteArray()

    private fun pictureFrame(img: ByteArray): ByteArray =
        byteArrayOf(0) + mimeOf(img).encodeToByteArray() + byteArrayOf(0, 3, 0) + img
}

/** Constructor de bytes sencillo. */
internal class Bytes {
    private var buf = ByteArray(256)
    var size = 0
        private set

    private fun ensure(n: Int) {
        if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
    }

    fun u8(v: Int) { ensure(1); buf[size++] = v.toByte() }
    fun be24(v: Int) { u8(v shr 16); u8(v shr 8); u8(v) }
    fun be32(v: Int) { u8(v ushr 24); u8(v shr 16); u8(v shr 8); u8(v) }
    fun le32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v ushr 24) }
    fun synchsafe(v: Int) { u8((v shr 21) and 0x7F); u8((v shr 14) and 0x7F); u8((v shr 7) and 0x7F); u8(v and 0x7F) }
    fun ascii(s: String) = s.forEach { u8(it.code) }
    fun raw(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
    fun toByteArray() = buf.copyOf(size)
}
