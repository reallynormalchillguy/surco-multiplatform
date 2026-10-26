package dev.surco

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.surco.data.ScanState
import dev.surco.tags.ByteArraySource
import dev.surco.tags.TagEdits
import dev.surco.tags.TagReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * El caso real: la música está en la memoria interna y la app la ve con el selector de carpetas (SAF).
 * Se renombra un álbum como lo hace «Editar álbum» y se vuelve a escanear como al reiniciar la app.
 */
@RunWith(AndroidJUnit4::class)
class SafTagSaveTest {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<SurcoApp>()
    private val device = UiDevice.getInstance(inst)
    private val folder = "SurcoPrueba"
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Music/$folder")
    private var scenario: ActivityScenario<MainActivity>? = null

    /**
     * Ejecuta un comando como el usuario «shell» (los archivos quedan como los de cualquier otra app).
     * No pasa por un intérprete: nada de comillas ni redirecciones.
     */
    private fun shell(cmd: String, input: ByteArray? = null): ByteArray {
        val (out, inp) = inst.uiAutomation.executeShellCommandRw(cmd)
        ParcelFileDescriptor.AutoCloseOutputStream(inp).use { if (input != null) it.write(input) }
        return ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    private fun albumInFile(name: String): String =
        TagReader.read(ByteArraySource(shell("cat /sdcard/Music/$folder/$name")), name).album

    private fun albumViaSaf(uri: String): String {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(app, Uri.parse(uri))
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        } catch (e: Exception) {
            "error ${e.message}"
        } finally {
            r.release()
        }
    }

    /** Guarda una captura de lo que hay en pantalla (sale en la rama ci-device) y los textos visibles. */
    private fun snapshot(label: String): String {
        runCatching {
            val dir = java.io.File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
            device.takeScreenshot(java.io.File(dir, "saf_$label.png"))
        }
        return runCatching {
            device.findObjects(By.textContains("")).mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }.take(40).joinToString(" / ")
        }.getOrDefault("?")
    }

    private fun findButton(text: String, res: String, ms: Long) =
        device.wait(Until.findObject(By.text(Pattern.compile("(?i)$text"))), ms)
            ?: device.findObject(By.res(Pattern.compile(res)))

    /**
     * Elige la carpeta en el selector del sistema, como lo haría una persona.
     * Devuelve null si todo fue bien o el paso que falló, con lo que había en pantalla.
     */
    private fun grantFolder(): String? {
        val c = app.container
        c.prefs.update { it.copy(treeUri = "content://fake/tree", language = "en", slskUser = "", slskPass = "", discover = false) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val initial = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/$folder")
        scenario!!.onActivity {
            @Suppress("DEPRECATION")
            it.startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                4242,
            )
        }
        device.waitForIdle()
        val use = findButton("use this folder", ".*:id/action_menu_select|.*:id/button1", 20_000)
            ?: return "sin «Use this folder»: " + snapshot("1_selector")
        snapshot("1_selector")
        use.click()
        device.waitForIdle()
        val allow = findButton("allow", "android:id/button1", 10_000)
            ?: return "sin «Allow»: " + snapshot("2_permiso")
        snapshot("2_permiso")
        allow.click()
        // El resultado vuelve a la app; se espera a que el permiso exista de verdad.
        var error: Throwable? = null
        repeat(20) {
            Thread.sleep(500)
            error = runCatching {
                app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.exceptionOrNull()
            if (error == null) return null
        }
        return "sin permiso (${error?.message}); en pantalla: " + snapshot("3_despues")
    }

    @After
    fun tearDown() {
        runCatching { scenario?.close() }
        runCatching { app.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        shell("rm -rf /sdcard/Music/$folder")
    }

    @Test
    fun renamedAlbumSurvivesARescan() = runBlocking {
        val assets = inst.context.assets
        val names = listOf("v23.mp3", "v24.mp3", "a.flac")
        shell("rm -rf /sdcard/Music/$folder")
        shell("mkdir -p /sdcard/Music/$folder")
        for (n in names) shell("tee /sdcard/Music/$folder/$n", assets.open("tags/$n").use { it.readBytes() })

        val report = ArrayList<String>()
        report += "archivos=" + String(shell("ls -l /sdcard/Music/$folder")).trim().replace("\n", "; ")
        val problem = grantFolder()
        report += "permiso=${problem ?: "ok"}"
        assertTrue(report.joinToString(" | "), problem == null)

        val c = app.container
        c.db.clearAllTables()
        c.prefs.update { it.copy(treeUri = tree.toString()) }
        val first = c.scanner.scan(tree.toString())
        val dao = c.db.dao()
        val before = dao.allTracksOnce()
        report += "escaneo1=$first álbumes=${before.map { it.album }.distinct()}"
        assertTrue(report.joinToString(" | "), first is ScanState.Done && before.size == names.size)

        val key = before.first().albumKey
        val (newKey, errors) = c.repository.editAlbum(key, TagEdits(album = "Álbum nuevo", albumArtist = "Grupo")) { _, _ -> }
        report += "editar errores=$errors claveNueva=$newKey bd=" + dao.allTracksOnce().joinToString { "${it.fileName}:«${it.album}» lm=${it.lastModified}" }
        report += "en disco=" + String(shell("stat -c %n=%s,%Y /sdcard/Music/$folder/v23.mp3 /sdcard/Music/$folder/v24.mp3 /sdcard/Music/$folder/a.flac")).trim().replace("\n", "; ")
        report += "archivos=" + names.joinToString { "$it:«${albumInFile(it)}»" }
        report += "MMR=" + dao.allTracksOnce().joinToString { "${it.fileName}:«${albumViaSaf(it.uri)}»" }

        // «Reiniciar»: deja pasar el escaneo automático y vuelve a escanear como al abrir la app.
        Thread.sleep(6_000)
        val second = c.scanner.scan(tree.toString())
        val after = dao.allTracksOnce()
        report += "escaneo2=$second bd=" + after.joinToString { "${it.fileName}:«${it.album}» lm=${it.lastModified}" }
        println("SafTagSaveTest\n" + report.joinToString("\n"))
        assertTrue(report.joinToString(" | "), errors.isEmpty() && after.size == names.size && after.all { it.album == "Álbum nuevo" })
    }

    /**
     * Lo mismo sin el selector: el archivo está en la memoria interna (Music/) pero se escribe por
     * MediaStore. Sirve para ver si escribir en la memoria compartida se queda de verdad en el archivo.
     */
    @Test
    fun tagWriterOnSharedStorage() = runBlocking {
        val report = ArrayList<String>()
        var ok = true
        val writer = dev.surco.tags.TagWriter(app)
        for (n in listOf("v23.mp3", "a.flac")) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "surco_ms_$n")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Music/SurcoPruebaMS")
            }
            val coll = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = app.contentResolver.insert(coll, values)
            if (uri == null) { report += "$n: no se pudo crear"; ok = false; continue }
            try {
                app.contentResolver.openOutputStream(uri, "wt")!!.use { o -> inst.context.assets.open("tags/$n").use { it.copyTo(o) } }
                val err = runCatching { writer.write(uri, n, TagEdits(album = "Álbum nuevo")) }.exceptionOrNull()
                val viaUri = albumViaSaf(uri.toString())
                val disk = runCatching {
                    TagReader.read(ByteArraySource(shell("cat /sdcard/Music/SurcoPruebaMS/surco_ms_$n")), n).album
                }.getOrElse { "?" }
                report += "$n: uri=«$viaUri» disco=«$disk»" + (err?.let { " error=${it.message}" } ?: "")
                if (err != null || viaUri != "Álbum nuevo" || disk != "Álbum nuevo") ok = false
            } finally {
                runCatching { app.contentResolver.delete(uri, null, null) }
            }
        }
        println("SafTagSaveTest.shared\n" + report.joinToString("\n"))
        assertTrue(report.joinToString(" | "), ok)
    }
}
