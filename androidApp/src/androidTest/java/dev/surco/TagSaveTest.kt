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
