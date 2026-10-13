package dev.surco.tags

import dev.surco.util.Charsets1252

/** Lectura aleatoria de un archivo (NSFileHandle en iOS, un ByteArray en las pruebas…). */
interface ByteSource {
    val size: Long
    /** Lee hasta [length] bytes desde [offset] (menos si se llega al final). */
    fun read(offset: Long, length: Int): ByteArray
}

class ByteArraySource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()
    override fun read(offset: Long, length: Int): ByteArray {
        if (offset >= data.size) return ByteArray(0)
        val end = minOf(data.size.toLong(), offset + length).toInt()
        return data.copyOfRange(offset.toInt(), end)
    }
}

/** Lo que sabemos leer de un archivo de audio. Los campos vacíos significan «no hay». */
data class AudioTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val genre: String = "",
    val lyrics: String = "",
    val cover: ByteArray? = null,
    val durationMs: Long = 0,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
    /** Bits por segundo. */
    val bitrate: Int = 0,
) {
    override fun equals(other: Any?) = other is AudioTags && toString() == other.toString() && cover.contentEqualsNullable(other.cover)
    override fun hashCode() = toString().hashCode()
}

private fun ByteArray?.contentEqualsNullable(o: ByteArray?) = if (this == null) o == null else o != null && contentEquals(o)

/**
 * Lector de etiquetas en Kotlin puro (sin TagLib): ID3v2.2/2.3/2.4 + ID3v1 (MP3), FLAC (Vorbis comment
 * y PICTURE) y MP4/M4A (átomos ilst). También calcula duración, frecuencia, bits y bitrate.
 */
object TagReader {
    fun read(src: ByteSource, fileName: String): AudioTags {
        val head = src.read(0, 16)
        val id3Size = Id3.tagSize(head)
        val afterId3 = src.read(id3Size, 12)
        return when {
            afterId3.startsWith("fLaC") -> Flac.read(src, id3Size)
            head.size >= 8 && head.ascii(4, 4) == "ftyp" -> Mp4.read(src)
            id3Size > 0 || fileName.substringAfterLast('.', "").lowercase() in MPEG_EXT -> Mp3.read(src, id3Size)
            else -> AudioTags() // WAV, AIFF, OGG…: la plataforma calcula al menos la duración
        }
    }

    private val MPEG_EXT = setOf("mp3", "mp2", "mpga")
}

// ------------------------------------------------------------------ utilidades de bytes

internal fun ByteArray.startsWith(s: String): Boolean = size >= s.length && s.indices.all { this[it] == s[it].code.toByte() }
internal fun ByteArray.ascii(off: Int, len: Int): String =
    if (off + len > size) "" else buildString { for (i in off until off + len) append((this@ascii[i].toInt() and 0xFF).toChar()) }
internal fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
internal fun ByteArray.be16(i: Int) = (u8(i) shl 8) or u8(i + 1)
internal fun ByteArray.be24(i: Int) = (u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2)
internal fun ByteArray.be32(i: Int): Long = (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()
internal fun ByteArray.be64(i: Int): Long = (be32(i) shl 32) or be32(i + 4)
internal fun ByteArray.le32(i: Int): Long = u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)
internal fun ByteArray.synchsafe(i: Int): Int = (u8(i) and 0x7F shl 21) or (u8(i + 1) and 0x7F shl 14) or (u8(i + 2) and 0x7F shl 7) or (u8(i + 3) and 0x7F)

internal fun latin1(b: ByteArray, from: Int = 0, to: Int = b.size) = buildString { for (i in from until to) append((b[i].toInt() and 0xFF).toChar()) }

internal fun utf16(b: ByteArray, from: Int, to: Int, bigEndianDefault: Boolean = true): String {
    var i = from
    var be = bigEndianDefault
    if (to - from >= 2) {
        val b0 = b.u8(from); val b1 = b.u8(from + 1)
        if (b0 == 0xFE && b1 == 0xFF) { be = true; i += 2 } else if (b0 == 0xFF && b1 == 0xFE) { be = false; i += 2 }
    }
    val chars = StringBuilder()
    while (i + 1 < to) {
        val c = if (be) (b.u8(i) shl 8) or b.u8(i + 1) else (b.u8(i + 1) shl 8) or b.u8(i)
        chars.append(c.toChar())
        i += 2
    }
    return chars.toString()
}

// ------------------------------------------------------------------ ID3 / MP3

internal object Id3 {
    /** Tamaño total de la etiqueta ID3v2 al principio (0 si no hay). */
    fun tagSize(head: ByteArray): Long {
        if (head.size < 10 || !head.startsWith("ID3")) return 0
        val footer = if (head.u8(5) and 0x10 != 0) 10 else 0
        return 10L + head.synchsafe(6) + footer
    }

    class Frame(val id: String, val data: ByteArray, val flags: Int)

    class Tag(val version: Int, val frames: List<Frame>)

    /** Lee las tramas de la etiqueta ID3v2 (sin interpretar). */
    fun parse(src: ByteSource): Tag? {
        val head = src.read(0, 10)
        if (head.size < 10 || !head.startsWith("ID3")) return null
        val version = head.u8(3)
        val flags = head.u8(5)
        val size = head.synchsafe(6)
        var body = src.read(10, size)
        if (version <= 3 && flags and 0x80 != 0) body = unsync(body)
        var pos = 0
        if (flags and 0x40 != 0 && version >= 3) {
            pos = if (version == 3) 4 + body.be32(0).toInt() else body.synchsafe(0)
        }
        val frames = ArrayList<Frame>()
        val idLen = if (version == 2) 3 else 4
        val hdrLen = if (version == 2) 6 else 10
        while (pos + hdrLen <= body.size) {
            if (body[pos] == 0.toByte()) break // relleno
            val id = body.ascii(pos, idLen)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val len = when (version) {
                2 -> body.be24(pos + 3)
                3 -> body.be32(pos + 4).toInt()
                else -> body.synchsafe(pos + 4)
            }
            val fflags = if (version == 2) 0 else body.be16(pos + 8)
            val start = pos + hdrLen
            if (len < 0 || start + len > body.size) break
            var data = body.copyOfRange(start, start + len)
            if (version == 4 && fflags and 0x0002 != 0) data = unsync(data)
            if (version == 4 && fflags and 0x0001 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size) // longitud de datos
            frames += Frame(id, data, fflags)
            pos = start + len
        }
        return Tag(version, frames)
    }

    fun unsync(b: ByteArray): ByteArray {
        val out = ArrayList<Byte>(b.size)
        var i = 0
        while (i < b.size) {
            out += b[i]
            if (b.u8(i) == 0xFF && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    /** Texto de una trama según su byte de codificación. Varios valores se unen con «; ». */
    fun decodeText(enc: Int, b: ByteArray, from: Int, to: Int): String {
        val raw = when (enc) {
            0 -> latin1(b, from, to)
            1 -> utf16(b, from, to)
            2 -> utf16(b, from, to, bigEndianDefault = true)
            else -> b.copyOfRange(from, to).decodeToString()
        }
        return raw.split('\u0000').map { it.removePrefix("﻿").trim() }.filter { it.isNotEmpty() }.joinToString("; ")
    }

    fun text(f: Frame): String = if (f.data.isEmpty()) "" else decodeText(f.data.u8(0), f.data, 1, f.data.size)

    /** Fin de una cadena terminada en nulo según la codificación (devuelve el índice tras el terminador). */
    fun skipString(enc: Int, b: ByteArray, from: Int): Int {
        var i = from
        if (enc == 1 || enc == 2) {
            while (i + 1 < b.size && !(b[i] == 0.toByte() && b[i + 1] == 0.toByte())) i += 2
            return minOf(b.size, i + 2)
        }
        while (i < b.size && b[i] != 0.toByte()) i++
        return minOf(b.size, i + 1)
    }

    fun picture(f: Frame, v2: Boolean): Pair<Int, ByteArray>? {
        val d = f.data
        if (d.size < 4) return null
        val enc = d.u8(0)
        var i: Int
        if (v2) {
            i = 4 // codificación + formato de 3 letras
        } else {
            i = 1
            while (i < d.size && d[i] != 0.toByte()) i++
            i++ // fin del tipo MIME
        }
        if (i >= d.size) return null
        val type = d.u8(i)
        i = skipString(enc, d, i + 1)
        if (i > d.size) return null
        return type to d.copyOfRange(i, d.size)
    }

    fun lyrics(f: Frame): String {
        val d = f.data
        if (d.size < 5) return ""
        val enc = d.u8(0)
        val start = skipString(enc, d, 4) // tras el idioma (3) y la descripción
        if (start >= d.size) return ""
        // Sin partir por nulos: la letra puede tener saltos de línea y conviene conservarla entera.
        val text = when (enc) {
            0 -> latin1(d, start, d.size)
            1, 2 -> utf16(d, start, d.size)
            else -> d.copyOfRange(start, d.size).decodeToString()
        }
        return text.trimEnd('\u0000').removePrefix("\uFEFF")
    }

    fun genre(raw: String): String {
        // «(17)Rock» → «Rock»; «(17)» o «17» → se queda tal cual (ScanRules lo limpia).
        val m = Regex("^\\((\\d+)\\)(.+)$").find(raw)
        return m?.groupValues?.get(2)?.trim() ?: raw
    }
}

internal object Mp3 {
    private val bitratesV1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val bitratesV2L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val rates = arrayOf(intArrayOf(11025, 12000, 8000), intArrayOf(0, 0, 0), intArrayOf(22050, 24000, 16000), intArrayOf(44100, 48000, 32000))

    fun read(src: ByteSource, id3Size: Long): AudioTags {
        var t = AudioTags()
        val tag = runCatching { Id3.parse(src) }.getOrNull()
        if (tag != null) t = fromId3(tag)
        else readId3v1(src)?.let { t = it }
        return t.withAudio(audioInfo(src, id3Size))
    }

    fun fromId3(tag: Id3.Tag): AudioTags {
        val v2 = tag.version == 2
        fun txt(vararg ids: String) = ids.firstNotNullOfOrNull { id -> tag.frames.firstOrNull { it.id == id }?.let { Id3.text(it) }?.takeIf { it.isNotEmpty() } } ?: ""
        val pics = tag.frames.filter { it.id == "APIC" || it.id == "PIC" }.mapNotNull { Id3.picture(it, v2 || it.id == "PIC") }
        val cover = (pics.firstOrNull { it.first == 3 } ?: pics.firstOrNull())?.second
        val lyr = tag.frames.firstOrNull { it.id == "USLT" || it.id == "ULT" }?.let { Id3.lyrics(it) } ?: ""
        return AudioTags(
            title = txt("TIT2", "TT2"),
            artist = txt("TPE1", "TP1"),
            album = txt("TALB", "TAL"),
            albumArtist = txt("TPE2", "TP2"),
            trackNumber = txt("TRCK", "TRK"),
            discNumber = txt("TPOS", "TPA"),
            year = txt("TDRC", "TYER", "TYE", "TDOR", "TORY"),
            genre = Id3.genre(txt("TCON", "TCO")),
            lyrics = lyr,
            cover = cover,
        )
    }

    private fun readId3v1(src: ByteSource): AudioTags? {
        if (src.size < 128) return null
        val b = src.read(src.size - 128, 128)
        if (!b.startsWith("TAG")) return null
        fun f(off: Int, len: Int) = Charsets1252.decode(b.copyOfRange(off, off + len)).trimEnd('\u0000', ' ')
        val track = if (b[125] == 0.toByte() && b[126] != 0.toByte()) b.u8(126).toString() else ""
        return AudioTags(title = f(3, 30), artist = f(33, 30), album = f(63, 30), year = f(93, 4), trackNumber = track)
    }

    private class Info(val durationMs: Long, val sampleRate: Int, val bitrate: Int)

    private fun AudioTags.withAudio(i: Info?) = if (i == null) this else copy(durationMs = i.durationMs, sampleRate = i.sampleRate, bitrate = i.bitrate)

    private fun audioInfo(src: ByteSource, start: Long): Info? {
        val buf = src.read(start, 64 * 1024)
        var i = 0
        while (i + 4 <= buf.size) {
            if (buf.u8(i) == 0xFF && buf.u8(i + 1) and 0xE0 == 0xE0) {
                val h = parseHeader(buf, i)
                if (h != null) {
                    // Confirmamos con la trama siguiente para no confundir basura con una cabecera.
                    val next = i + h.frameLen
                    val confirmed = next + 2 > buf.size || (buf.u8(next) == 0xFF && buf.u8(next + 1) and 0xE0 == 0xE0)
                    if (confirmed) return infoFrom(src, start + i, buf, i, h)
                }
            }
            i++
        }
        return null
    }

    private class Header(val version: Int, val layer: Int, val bitrate: Int, val sampleRate: Int, val mono: Boolean, val frameLen: Int, val samples: Int)

    private fun parseHeader(b: ByteArray, i: Int): Header? {
        val v = (b.u8(i + 1) shr 3) and 3 // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
        val layer = (b.u8(i + 1) shr 1) and 3 // 1 = Layer III
        val bi = (b.u8(i + 2) shr 4) and 0xF
        val si = (b.u8(i + 2) shr 2) and 3
        val pad = (b.u8(i + 2) shr 1) and 1
        if (v == 1 || layer != 1 || bi == 0 || bi == 15 || si == 3) return null
        val kbps = if (v == 3) bitratesV1L3[bi] else bitratesV2L3[bi]
        val rate = rates[v][si]
        if (rate == 0) return null
        val samples = if (v == 3) 1152 else 576
        val frameLen = (samples / 8 * kbps * 1000) / rate + pad
        val mono = (b.u8(i + 3) shr 6) == 3
        return Header(v, layer, kbps * 1000, rate, mono, frameLen, samples)
    }

    private fun infoFrom(src: ByteSource, abs: Long, b: ByteArray, i: Int, h: Header): Info {
        // Cabecera Xing/Info (VBR) justo después de la información lateral.
        val side = if (h.version == 3) (if (h.mono) 17 else 32) else (if (h.mono) 9 else 17)
        val x = i + 4 + side
        val frames: Long? = when {
            x + 12 <= b.size && (b.ascii(x, 4) == "Xing" || b.ascii(x, 4) == "Info") && b.u8(x + 7) and 1 != 0 -> b.be32(x + 8)
            i + 36 + 18 <= b.size && b.ascii(i + 36, 4) == "VBRI" -> b.be32(i + 36 + 14)
            else -> null
        }
        val audioBytes = (src.size - abs).coerceAtLeast(0)
        return if (frames != null && frames > 0) {
            val ms = frames * h.samples * 1000 / h.sampleRate
            Info(ms, h.sampleRate, if (ms > 0) (audioBytes * 8 * 1000 / ms).toInt() else h.bitrate)
        } else {
            Info(audioBytes * 8 * 1000 / h.bitrate, h.sampleRate, h.bitrate)
        }
    }
}

// ------------------------------------------------------------------ FLAC

internal object Flac {
    class Block(val type: Int, val data: ByteArray)

    /** Bloques de metadatos y posición donde empieza el audio. */
    fun blocks(src: ByteSource, start: Long): Pair<List<Block>, Long> {
        val out = ArrayList<Block>()
        var pos = start + 4
        while (true) {
            val h = src.read(pos, 4)
            if (h.size < 4) break
            val last = h.u8(0) and 0x80 != 0
            val type = h.u8(0) and 0x7F
            val len = h.be24(1)
            out += Block(type, if (type == 1) ByteArray(0) else src.read(pos + 4, len))
            pos += 4 + len
            if (last) break
        }
        return out to pos
    }

    fun read(src: ByteSource, start: Long): AudioTags {
        val (blocks, audioStart) = blocks(src, start)
        var t = AudioTags()
        blocks.firstOrNull { it.type == 0 && it.data.size >= 18 }?.let { si ->
            val d = si.data
            val rate = (d.u8(10) shl 12) or (d.u8(11) shl 4) or (d.u8(12) shr 4)
            val bits = (((d.u8(12) and 1) shl 4) or (d.u8(13) shr 4)) + 1
            val total = ((d.u8(13) and 0xF).toLong() shl 32) or d.be32(14)
            val ms = if (rate > 0) total * 1000 / rate else 0
            val br = if (ms > 0) ((src.size - audioStart) * 8 * 1000 / ms).toInt() else 0
            t = t.copy(sampleRate = rate, bitsPerSample = bits, durationMs = ms, bitrate = br)
        }
        val comments = blocks.firstOrNull { it.type == 4 }?.let { vorbisComments(it.data) }.orEmpty()
        fun c(vararg keys: String) = keys.firstNotNullOfOrNull { k -> comments[k]?.takeIf { it.isNotEmpty() } } ?: ""
        val pics = blocks.filter { it.type == 6 }.mapNotNull { picture(it.data) }
        val cover = (pics.firstOrNull { it.first == 3 } ?: pics.firstOrNull())?.second
        return t.copy(
            title = c("TITLE"), artist = c("ARTIST"), album = c("ALBUM"),
            albumArtist = c("ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST"),
            trackNumber = c("TRACKNUMBER", "TRACK"), discNumber = c("DISCNUMBER", "DISC"),
            year = c("DATE", "YEAR", "ORIGINALDATE"), genre = c("GENRE"),
            lyrics = c("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS"),
            cover = cover,
        )
    }

    /** Comentarios Vorbis: clave en mayúsculas → valores unidos con «; ». */
    fun vorbisComments(d: ByteArray): Map<String, String> = rawComments(d).groupBy({ it.first.uppercase() }, { it.second })
        .mapValues { (_, v) -> v.filter { it.isNotBlank() }.joinToString("; ") }

    fun rawComments(d: ByteArray): List<Pair<String, String>> {
        if (d.size < 8) return emptyList()
        var i = 0
        val vendorLen = d.le32(i).toInt(); i += 4 + vendorLen
        if (i + 4 > d.size) return emptyList()
        val n = d.le32(i).toInt(); i += 4
        val out = ArrayList<Pair<String, String>>()
        repeat(n) {
            if (i + 4 > d.size) return out
            val len = d.le32(i).toInt(); i += 4
            if (len < 0 || i + len > d.size) return out
            val s = d.copyOfRange(i, i + len).decodeToString(); i += len
            val eq = s.indexOf('=')
            if (eq > 0) out += s.substring(0, eq) to s.substring(eq + 1)
        }
        return out
    }

    fun vendor(d: ByteArray): String = if (d.size < 4) "" else d.copyOfRange(4, minOf(d.size, 4 + d.le32(0).toInt())).decodeToString()

    fun picture(d: ByteArray): Pair<Int, ByteArray>? = runCatching {
        var i = 0
        val type = d.be32(i).toInt(); i += 4
        val mimeLen = d.be32(i).toInt(); i += 4 + mimeLen
        val descLen = d.be32(i).toInt(); i += 4 + descLen
        i += 16
        val len = d.be32(i).toInt(); i += 4
        if (len <= 0 || i + len > d.size) null else type to d.copyOfRange(i, i + len)
    }.getOrNull()
}

// ------------------------------------------------------------------ MP4 / M4A

internal object Mp4 {
    private class Atom(val type: String, val start: Long, val headerLen: Int, val size: Long) {
        val dataStart get() = start + headerLen
        val end get() = start + size
    }

    private fun atomsIn(src: ByteSource, from: Long, to: Long): List<Atom> {
        val out = ArrayList<Atom>()
        var pos = from
        while (pos + 8 <= to) {
            val h = src.read(pos, 16)
            if (h.size < 8) break
            var size = h.be32(0)
            val type = h.ascii(4, 4)
            var hl = 8
            if (size == 1L && h.size >= 16) { size = h.be64(8); hl = 16 }
            if (size == 0L) size = to - pos
            if (size < hl) break
            out += Atom(type, pos, hl, size)
            pos += size
        }
        return out
    }

    private fun AudioTags.withAtom(type: String, d: ByteArray): AudioTags {
        // d: contenido del átomo «data»: 4 bytes de tipo + 4 de idioma + valor.
        if (d.size < 8) return this
        val value = d.copyOfRange(8, d.size)
        fun s() = value.decodeToString()
        fun pair() = if (value.size >= 6) value.be16(2).let { n -> val tot = value.be16(4); if (tot > 0) "$n/$tot" else "$n" } else ""
        return when (type) {
            "©nam" -> copy(title = s())
            "©ART" -> copy(artist = s())
            "©alb" -> copy(album = s())
            "aART" -> copy(albumArtist = s())
            "©day" -> copy(year = s())
            "©gen" -> copy(genre = s())
            "©lyr" -> copy(lyrics = s())
            "trkn" -> copy(trackNumber = pair())
            "disk" -> copy(discNumber = pair())
            "covr" -> if (cover == null) copy(cover = value) else this
            else -> this
        }
    }

    fun read(src: ByteSource): AudioTags {
        val top = atomsIn(src, 0, src.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return AudioTags()
        if (moov.size > 64L * 1024 * 1024) return AudioTags()
        val m = src.read(moov.dataStart, (moov.size - moov.headerLen).toInt())
        val inner = ByteArraySource(m)
        var t = AudioTags()
        val children = atomsIn(inner, 0, inner.size)
        children.firstOrNull { it.type == "mvhd" }?.let { a ->
            val d = inner.read(a.dataStart, 32)
            val v1 = d.u8(0) == 1
            val scale = if (v1) d.be32(20) else d.be32(12)
            val dur = if (v1) d.be64(24) else d.be32(16)
            if (scale > 0) t = t.copy(durationMs = dur * 1000 / scale)
        }
        // Frecuencia y bits: trak/mdia/minf/stbl/stsd → primera entrada (mp4a / alac).
        for (trak in children.filter { it.type == "trak" }) {
            val stsd = path(inner, trak, listOf("mdia", "minf", "stbl", "stsd")) ?: continue
            val d = inner.read(stsd.dataStart, 64)
            if (d.size < 8 + 36) continue
            val entry = 8 // versión/flags + nº de entradas
            val fmt = d.ascii(entry + 4, 4)
            if (fmt != "mp4a" && fmt != "alac") continue
            val e = entry + 8
            val bits = d.be16(e + 18)
            val rate = (d.be32(e + 24) shr 16).toInt()
            t = t.copy(sampleRate = rate, bitsPerSample = if (fmt == "alac") bits else 0)
            if (fmt == "alac") {
                // La caja «alac» interna tiene los valores reales (para más de 16 bits / 65 kHz).
                val full = inner.read(stsd.dataStart, (stsd.size - stsd.headerLen).toInt())
                val idx = (0 until full.size - 4).firstOrNull { full.ascii(it, 4) == "alac" && it > e }
                if (idx != null && idx + 4 + 24 <= full.size) {
                    val c = idx + 4 + 4 // tras «alac» y versión/flags
                    t = t.copy(bitsPerSample = full.u8(c + 5), sampleRate = full.be32(c + 20).toInt())
                }
            }
            break
        }
        val udta = children.firstOrNull { it.type == "udta" }
        val meta = udta?.let { atomsIn(inner, it.dataStart, it.end).firstOrNull { a -> a.type == "meta" } }
        val ilst = meta?.let { atomsIn(inner, it.dataStart + 4, it.end).firstOrNull { a -> a.type == "ilst" } }
        if (ilst != null) {
            for (item in atomsIn(inner, ilst.dataStart, ilst.end)) {
                val data = atomsIn(inner, item.dataStart, item.end).firstOrNull { it.type == "data" } ?: continue
                t = t.withAtom(item.type, inner.read(data.dataStart, (data.size - data.headerLen).toInt()))
            }
        }
        if (t.durationMs > 0) t = t.copy(bitrate = (src.size * 8 * 1000 / t.durationMs).toInt())
        return t
    }

    private fun path(src: ByteSource, from: Atom, names: List<String>): Atom? {
        var cur = from
        for (n in names) cur = atomsIn(src, cur.dataStart, cur.end).firstOrNull { it.type == n } ?: return null
        return cur
    }
}
