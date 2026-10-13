package dev.surco.tags

import dev.surco.i18n.S

import android.content.Context
import android.net.Uri
import dev.surco.platform.AndroidArtStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Escribe etiquetas en archivos accesibles mediante SAF.
 * jaudiotagger necesita un java.io.File, así que copiamos el archivo a la caché, lo editamos
 * y lo volvemos a escribir en su sitio.
 */
class TagWriter(private val context: Context) : TagEditor {

    init {
        TagOptionSingleton.getInstance().setAndroid(true)
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    override suspend fun write(uri: String, fileName: String, edits: TagEdits) = write(Uri.parse(uri), fileName, edits)

    override suspend fun readLyrics(uri: String, fileName: String): String? = readLyrics(Uri.parse(uri), fileName)

    suspend fun write(uri: Uri, fileName: String, edits: TagEdits): Unit = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val ext = fileName.substringAfterLast('.', "").lowercase().ifEmpty { "mp3" }
        val tmp = File.createTempFile("surco_edit_", ".$ext", context.cacheDir)
        try {
            (cr.openInputStream(uri) ?: throw TagWriteException(S.cannotOpenFile)).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            val audioFile = try {
                AudioFileIO.read(tmp)
            } catch (e: Exception) {
                throw TagWriteException(S.formatNotSupported(ext), e)
            }
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.put(FieldKey.TITLE, edits.title)
            tag.put(FieldKey.ARTIST, edits.artist)
            tag.put(FieldKey.ALBUM, edits.album)
            tag.put(FieldKey.ALBUM_ARTIST, edits.albumArtist)
            tag.put(FieldKey.YEAR, edits.year)
            tag.put(FieldKey.TRACK, edits.trackNumber)
            tag.put(FieldKey.DISC_NO, edits.discNumber)
            tag.put(FieldKey.GENRE, edits.genre)
            tag.put(FieldKey.LYRICS, edits.lyrics)
            edits.cover?.let { jpeg ->
                runCatching { tag.deleteArtworkField() }
                if (jpeg.isNotEmpty()) {
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = jpeg
                    artwork.mimeType = "image/jpeg"
                    artwork.pictureType = 3 // Portada frontal
                    val (w, h) = AndroidArtStore.size(jpeg)
                    artwork.width = w
                    artwork.height = h
                    try {
                        tag.setField(artwork)
                    } catch (e: Exception) {
                        throw TagWriteException(S.couldNotEmbedCover(ext), e)
                    }
                }
            }
            try {
                audioFile.commit()
            } catch (e: Exception) {
                throw TagWriteException(S.couldNotSaveTags, e)
            }
            val out = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull()
                ?: cr.openOutputStream(uri, "w")
                ?: throw TagWriteException(S.noWritePermission)
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
        } finally {
            tmp.delete()
        }
    }

    /** Lee la letra incrustada (USLT / LYRICS / ©lyr). Null si no hay. */
    suspend fun readLyrics(uri: Uri, fileName: String): String? = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "").lowercase().ifEmpty { "mp3" }
        val tmp = File.createTempFile("surco_read_", ".$ext", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext null
            val tag = runCatching { AudioFileIO.read(tmp).tag }.getOrNull() ?: return@withContext null
            runCatching { tag.getFirst(FieldKey.LYRICS) }.getOrNull()?.takeIf { it.isNotBlank() }
        } finally {
            tmp.delete()
        }
    }

    private fun Tag.put(key: FieldKey, value: String?) {
        if (value == null) return
        runCatching {
            if (value.isBlank()) deleteField(key) else setField(key, value.trim())
        }
    }
}
