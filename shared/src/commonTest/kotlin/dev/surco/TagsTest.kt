package dev.surco

import dev.surco.tags.ByteArraySource
import dev.surco.tags.TagEdits
import dev.surco.tags.TagFileWriter
import dev.surco.tags.TagReader
import dev.surco.tags.TagWriteException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lector y escritor de etiquetas en Kotlin puro, con archivos sintéticos. */
class TagsTest {
    private class B {
        val l = ArrayList<Byte>()
        fun u8(v: Int) = apply { l += v.toByte() }
        fun be16(v: Int) = apply { u8(v shr 8); u8(v) }
        fun be24(v: Int) = apply { u8(v shr 16); u8(v shr 8); u8(v) }
        fun be32(v: Long) = apply { u8((v shr 24).toInt()); u8((v shr 16).toInt()); u8((v shr 8).toInt()); u8(v.toInt()) }
        fun le32(v: Int) = apply { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun ascii(s: String) = apply { s.forEach { u8(it.code) } }
        fun raw(b: ByteArray) = apply { b.forEach { l += it } }
        fun synch(v: Int) = apply { u8((v shr 21) and 0x7F); u8((v shr 14) and 0x7F); u8((v shr 7) and 0x7F); u8(v and 0x7F) }
        fun bytes() = l.toByteArray()
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 0xFF.toByte(), 0xD9.toByte())
    private val newJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9, 9, 0xFF.toByte(), 0xD9.toByte())
    private val audio = ByteArray(5000) { (it * 7).toByte() }

    private fun apply(file: ByteArray, name: String, edits: TagEdits): ByteArray {
        val r = TagFileWriter.rewrite(ByteArraySource(file), name, edits)
        return r.header + file.copyOfRange(r.audioOffset.toInt(), file.size)
    }

    // ------------------------------------------------------------ FLAC

    private fun flacFile(): ByteArray {
        val si = B()
            .be16(4096).be16(4096).be24(0).be24(0)
            // 96000 Hz (20 bits), 2 canales -1 (3 bits), 24 bits -1 (5 bits), 36 bits de muestras
            .also { b ->
                val rate = 96000; val ch = 1; val bps = 23; val total = 96000L * 180
                b.u8(rate shr 12); b.u8(rate shr 4)
                b.u8(((rate and 0xF) shl 4) or (ch shl 1) or (bps shr 4))
                b.u8(((bps and 0xF) shl 4) or ((total shr 32).toInt() and 0xF))
                b.be32(total and 0xFFFFFFFFL)
            }
            .raw(ByteArray(16)).bytes()
        val comments = listOf("TITLE=Jóga", "ARTIST=Björk", "ALBUM=Homogenic", "TRACKNUMBER=3", "DATE=1997",
            "GENRE=Electronic", "REPLAYGAIN_TRACK_GAIN=-6.5 dB", "LYRICS=línea uno\nlínea dos")
        val vc = B().le32(6).ascii("vendor").le32(comments.size).also { b ->
            comments.forEach { c -> val e = c.encodeToByteArray(); b.le32(e.size).raw(e) }
        }.bytes()
        val pic = B().be32(3).be32(10).ascii("image/jpeg").be32(0).be32(0).be32(0).be32(24).be32(0)
            .be32(jpeg.size.toLong()).raw(jpeg).bytes()
        return B().ascii("fLaC")
            .u8(0).be24(si.size).raw(si)
            .u8(4).be24(vc.size).raw(vc)
            .u8(6).be24(pic.size).raw(pic)
            .u8(0x81).be24(100).raw(ByteArray(100))
            .raw(audio).bytes()
    }

    @Test
    fun flacRead() {
        val t = TagReader.read(ByteArraySource(flacFile()), "01.flac")
        assertEquals("Jóga", t.title)
        assertEquals("Björk", t.artist)
        assertEquals("3", t.trackNumber)
        assertEquals(96000, t.sampleRate)
        assertEquals(24, t.bitsPerSample)
        assertEquals(180_000L, t.durationMs)
        assertEquals("línea uno\nlínea dos", t.lyrics)
        assertContentEquals(jpeg, t.cover)
    }

    @Test
    fun flacRewriteKeepsAudioAndOtherFields() {
        val file = flacFile()
        val out = apply(file, "01.flac", TagEdits(title = "Joga", albumArtist = "Björk", genre = "", cover = newJpeg))
        val t = TagReader.read(ByteArraySource(out), "01.flac")
        assertEquals("Joga", t.title)
        assertEquals("Björk", t.albumArtist)
        assertEquals("", t.genre)
        assertEquals("Homogenic", t.album)
        assertEquals(96000, t.sampleRate)
        assertContentEquals(newJpeg, t.cover)
        assertContentEquals(audio, out.copyOfRange(out.size - audio.size, out.size))
        assertTrue(out.decodeToString().contains("REPLAYGAIN_TRACK_GAIN=-6.5 dB"), "ReplayGain conservado")
        // Quitar la portada.
        val noCover = apply(out, "01.flac", TagEdits(cover = ByteArray(0)))
        assertNull(TagReader.read(ByteArraySource(noCover), "01.flac").cover)
    }

    // ------------------------------------------------------------ MP3

    /** 100 tramas MPEG-1 Layer III, 128 kbps, 44,1 kHz (417 bytes cada una). */
    private val mpegFrames: ByteArray = B().also { b ->
        repeat(100) { b.u8(0xFF).u8(0xFB).u8(0x90).u8(0x00).raw(ByteArray(413)) }
    }.bytes()

    private fun id3v23(): ByteArray {
        fun frame(id: String, data: ByteArray) = B().ascii(id).be32(data.size.toLong()).be16(0).raw(data).bytes()
        fun utf16(s: String) = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + s.flatMap { listOf(it.code.toByte(), (it.code shr 8).toByte()) }.toByteArray()
        val frames = frame("TIT2", utf16("Canción")) +
            frame("TPE1", byteArrayOf(0) + "Artista".encodeToByteArray()) +
            frame("TYER", byteArrayOf(0) + "2003".encodeToByteArray()) +
            frame("TCON", byteArrayOf(0) + "(17)Rock".encodeToByteArray()) +
            frame("TXXX", byteArrayOf(0) + "REPLAYGAIN_TRACK_GAIN".encodeToByteArray() + byteArrayOf(0) + "-3 dB".encodeToByteArray()) +
            frame("APIC", byteArrayOf(0) + "image/jpeg".encodeToByteArray() + byteArrayOf(0, 3, 0) + jpeg)
        return B().ascii("ID3").u8(3).u8(0).u8(0).synch(frames.size + 50).raw(frames).raw(ByteArray(50)).bytes()
    }

    @Test
    fun mp3ReadAndDuration() {
        val file = id3v23() + mpegFrames
        val t = TagReader.read(ByteArraySource(file), "x.mp3")
        assertEquals("Canción", t.title)
        assertEquals("Artista", t.artist)
        assertEquals("2003", t.year)
        assertEquals("Rock", t.genre)
        assertEquals(44100, t.sampleRate)
        assertEquals(128_000, t.bitrate)
        assertEquals(2606L, t.durationMs)
        assertContentEquals(jpeg, t.cover)
    }

    @Test
    fun mp3RewriteToId3v24() {
        val file = id3v23() + mpegFrames
        val out = apply(file, "x.mp3", TagEdits(title = "Nueva", album = "Disco", trackNumber = "4/12", lyrics = "hola\nadiós"))
        assertEquals(4, out[3].toInt(), "ID3v2.4")
        val t = TagReader.read(ByteArraySource(out), "x.mp3")
        assertEquals("Nueva", t.title)
        assertEquals("Artista", t.artist)
        assertEquals("Disco", t.album)
        assertEquals("4/12", t.trackNumber)
        assertEquals("2003", t.year) // TYER → TDRC
        assertEquals("hola\nadiós", t.lyrics)
        assertContentEquals(jpeg, t.cover)
        assertEquals(2606L, t.durationMs)
        assertTrue(out.decodeToString().contains("REPLAYGAIN_TRACK_GAIN"), "TXXX conservado")
        assertContentEquals(mpegFrames, out.copyOfRange(out.size - mpegFrames.size, out.size))
        // Segunda edición sobre un v2.4.
        val again = apply(out, "x.mp3", TagEdits(artist = "", cover = newJpeg))
        val t2 = TagReader.read(ByteArraySource(again), "x.mp3")
        assertEquals("", t2.artist)
        assertEquals("Nueva", t2.title)
        assertContentEquals(newJpeg, t2.cover)
    }

    @Test
    fun mp3WithoutTagGetsOne() {
        val tagged = apply(mpegFrames, "y.mp3", TagEdits(title = "Sin etiqueta"))
        assertEquals("Sin etiqueta", TagReader.read(ByteArraySource(tagged), "y.mp3").title)
        val v1 = mpegFrames + B().ascii("TAG").ascii("Viejo".padEnd(30, '\u0000')).ascii("Alguien".padEnd(30, '\u0000'))
            .raw(ByteArray(30)).ascii("1999").raw(ByteArray(28)).u8(0).u8(7).u8(0).bytes()
        val t = TagReader.read(ByteArraySource(v1), "z.mp3")
        assertEquals("Viejo", t.title)
        assertEquals("7", t.trackNumber)
        assertEquals("1999", t.year)
    }

    // ------------------------------------------------------------ MP4

    private fun atom(type: String, body: ByteArray) = B().be32(8L + body.size).ascii(type).raw(body).bytes()
    private fun item(type: String, kind: Int, value: ByteArray) = atom(type, atom("data", B().be32(kind.toLong()).be32(0).raw(value).bytes()))

    @Test
    fun mp4Read() {
        val mvhd = atom("mvhd", B().u8(0).be24(0).be32(0).be32(0).be32(1000).be32(245_000).raw(ByteArray(80)).bytes())
        val ilst = atom("ilst",
            item("©nam", 1, "Título".encodeToByteArray()) +
                item("©ART", 1, "Intérprete".encodeToByteArray()) +
                item("trkn", 0, B().be16(0).be16(5).be16(10).be16(0).bytes()) +
                item("covr", 13, jpeg))
        val meta = atom("meta", B().be32(0).raw(atom("hdlr", ByteArray(25))).raw(ilst).bytes())
        val moov = atom("moov", mvhd + atom("udta", meta))
        val file = atom("ftyp", "M4A ".encodeToByteArray() + ByteArray(4)) + atom("mdat", audio) + moov
        val t = TagReader.read(ByteArraySource(file.mapIndexed { i, b -> if (i in 12..15) b else b }.toByteArray()), "a.m4a")
        assertEquals("Título", t.title)
        assertEquals("Intérprete", t.artist)
        assertEquals("5/10", t.trackNumber)
        assertEquals(245_000L, t.durationMs)
        assertContentEquals(jpeg, t.cover)
        assertFailsWith<TagWriteException> { TagFileWriter.rewrite(ByteArraySource(file), "a.m4a", TagEdits(title = "x")) }
    }
}
