package dev.surco

import dev.surco.ios.IosTagEditor
import dev.surco.ios.toNSData
import dev.surco.ios.withSource
import dev.surco.tags.TagEdits
import dev.surco.tags.TagReader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Editar un álbum tiene que sobrevivir a un reinicio: al arrancar, el escáner relee el archivo con
 * [TagReader], así que lo que escribe [IosTagEditor] en el disco tiene que ser lo que se lee después.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class TagSaveTest {

    @Test
    fun renamedAlbumIsWhatTheScannerReadsBack() = runTest {
        val editor = IosTagEditor()
        val report = ArrayList<String>()
        var ok = true
        for ((name, b64) in listOf(
            "v23.mp3" to TagSaveFixtures.mp3v23,
            "v24.mp3" to TagSaveFixtures.mp3v24,
            "a.flac" to TagSaveFixtures.flac,
        )) {
            val path = NSTemporaryDirectory() + "tagsave_$name"
            val fm = NSFileManager.defaultManager
            fm.removeItemAtPath(path, null)
            fm.createFileAtPath(path, Base64.decode(b64).toNSData(), null)
            val url = NSURL.fileURLWithPath(path)
            val uri = url.absoluteString!!
            val before = url.withSource { TagReader.read(it, name).album }
            val error = runCatching { editor.write(uri, name, TagEdits(album = "Álbum nuevo", albumArtist = "Grupo")) }.exceptionOrNull()
            val after = NSURL.fileURLWithPath(path).withSource { TagReader.read(it, name).album }
            if (error != null || after != "Álbum nuevo") ok = false
            report += "$name: antes=«$before» después=«$after»" + (error?.let { " error=${it::class.simpleName}: ${it.message}" } ?: "")
            fm.removeItemAtPath(path, null)
        }
        println("TagSaveTest\n" + report.joinToString("\n"))
        assertTrue(ok, report.joinToString(" | "))
    }
}
