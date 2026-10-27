package dev.surco

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.surco.tags.TagEdits
import dev.surco.tags.TagWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Editar un álbum tiene que sobrevivir a un reinicio: al arrancar, el escáner vuelve a leer el archivo
 * con MediaMetadataRetriever, así que lo que escribe [TagWriter] tiene que ser lo que lee Android.
 */
@RunWith(AndroidJUnit4::class)
class TagSaveTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun copy(name: String): File {
        val f = File(ctx.cacheDir, "tagsave_$name")
        assets.open("tags/$name").use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    private fun albumOf(f: File): String {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        } finally {
            r.release()
        }
    }

    /**
     * Como el MP3 real del usuario: una ID3v2.3 de más de 3 MB (casi todo relleno) con el álbum [v2Album] y,
     * al final, una ID3v1 con el álbum «Viejo».
     */
    private fun bigPaddedMp3(name: String, v2Album: String): File {
        val base = assets.open("tags/v23.mp3").use { it.readBytes() }
        val head = 10 + ((base[6].toInt() and 127) shl 21 or ((base[7].toInt() and 127) shl 14) or
            ((base[8].toInt() and 127) shl 7) or (base[9].toInt() and 127))
        var audio = base.copyOfRange(head, base.size)
        if (audio.size >= 128 && String(audio, audio.size - 128, 3, Charsets.ISO_8859_1) == "TAG") audio = audio.copyOf(audio.size - 128)
        fun frame(id: String, text: String): ByteArray {
            val body = byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1)
            val n = body.size
            return id.toByteArray() + byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte(), 0, 0) + body
        }
        val frames = frame("TIT2", "Uno") + frame("TPE1", "Grupo") + frame("TALB", v2Album) + frame("TPE2", "Grupo") + frame("TRCK", "1")
        val size = 3_200_000
        val tag = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(), ((size shr 7) and 127).toByte(), (size and 127).toByte()) +
            frames + ByteArray(size - frames.size)
        fun field(v: String, len: Int) = v.toByteArray(Charsets.ISO_8859_1).copyOf(len)
        val v1 = "TAG".toByteArray() + field("Uno", 30) + field("Grupo", 30) + field("Viejo", 30) + field("1998", 4) +
            ByteArray(28) + byteArrayOf(0, 1, 17)
        val f = File(ctx.cacheDir, "tagsave_$name")
        f.writeBytes(tag + audio + v1)
        return f
    }

    /** La causa: con una ID3v2 de más de 3 MB, Android ignora la etiqueta y lee la ID3v1 del final. */
    @Test
    fun androidSkipsHugeId3v2Tags() {
        val f = bigPaddedMp3("big_v2nuevo.mp3", "Nuevo")
        val album = albumOf(f)
        f.delete()
        println("TagSaveTest.huge: Android lee «$album» (ID3v2 dice «Nuevo», ID3v1 dice «Viejo»)")
        assertTrue("Android lee «$album»", album == "Viejo")
    }

    /** El arreglo: al editar un MP3 así, Android tiene que leer el nombre nuevo (y la ID3v1 también lo tiene). */
    @Test
    fun editingAHugeTaggedMp3SticksForAndroid() = runBlocking {
        val f = bigPaddedMp3("big.mp3", "Viejo")
        val before = albumOf(f)
        val error = runCatching { TagWriter(ctx).write(Uri.fromFile(f), "big.mp3", TagEdits(album = "Álbum nuevo")) }.exceptionOrNull()
        val after = albumOf(f)
        val bytes = f.readBytes()
        val v1Album = String(bytes, bytes.size - 128 + 63, 30, Charsets.ISO_8859_1).trimEnd('\u0000', ' ')
        val v2Size = 10 + ((bytes[6].toInt() and 127) shl 21 or ((bytes[7].toInt() and 127) shl 14) or
            ((bytes[8].toInt() and 127) shl 7) or (bytes[9].toInt() and 127))
        f.delete()
        val report = "antes=«$before» después=«$after» id3v1=«$v1Album» id3v2=$v2Size bytes" +
            (error?.let { " error=${it.javaClass.simpleName}: ${it.message} / ${it.cause}" } ?: "")
        println("TagSaveTest.hugeEdit: $report")
        assertTrue(report, error == null && after == "Álbum nuevo" && v1Album == "Álbum nuevo" && v2Size < 3 * 1024 * 1024)
    }

    @Test
    fun renamedAlbumIsWhatAndroidReadsBack() = runBlocking {
        val writer = TagWriter(ctx)
        val report = ArrayList<String>()
        var ok = true
        for (name in listOf("v23.mp3", "v24.mp3", "a.flac", "a.m4a")) {
            val f = copy(name)
            val before = albumOf(f)
            val sizeBefore = f.length()
            val error = runCatching {
                writer.write(Uri.fromFile(f), name, TagEdits(album = "Álbum nuevo", albumArtist = "Grupo"))
            }.exceptionOrNull()
            val after = albumOf(f)
            val good = error == null && after == "Álbum nuevo"
            if (!good) ok = false
            report += "$name: antes=«$before» después=«$after» tamaño=$sizeBefore→${f.length()}" +
                (error?.let { " error=${it.javaClass.simpleName}: ${it.message} / ${it.cause}" } ?: "")
            f.delete()
        }
        println("TagSaveTest\n" + report.joinToString("\n"))
        assertTrue(report.joinToString(" | "), ok)
    }
}
