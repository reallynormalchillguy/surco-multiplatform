package dev.surco.data

import dev.surco.i18n.S

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val done: Int, val total: Int, val current: String) : ScanState
    data class Done(val total: Int, val updated: Int, val removed: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Recorre la carpeta elegida (Storage Access Framework) y mantiene la tabla de canciones al día.
 * Es incremental: sólo vuelve a leer los archivos cuya fecha o tamaño ha cambiado.
 */
class Scanner(
    private val context: Context,
    private val dao: LibraryDao,
    private val art: ArtStore,
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private val mutex = Mutex()

    private data class Doc(
        val uri: Uri, val folderId: String, val name: String, val mime: String,
        val size: Long, val lastModified: Long,
    )

    suspend fun scan(treeUri: Uri): ScanState = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                _state.value = ScanState.Running(0, 0, S.searchingFiles)
                val existing = dao.allTracksOnce().associateBy { it.uri }
                val artByAlbum = HashMap<String, String>()
                existing.values.forEach { t -> if (art.exists(t.artPath)) artByAlbum[t.albumKey] = t.artPath!! }

                val audio = ArrayList<Doc>()
                val folderCovers = HashMap<String, Uri>()
                walk(treeUri, audio, folderCovers)

                val now = System.currentTimeMillis()
                val batch = ArrayList<Track>()
                var updated = 0
                audio.forEachIndexed { i, doc ->
                    val key = doc.uri.toString()
                    val old = existing[key]
                    if (i % 10 == 0) _state.value = ScanState.Running(i, audio.size, doc.name)
                    if (old != null && old.lastModified == doc.lastModified && old.size == doc.size) {
                        // Sin cambios; sólo rellenamos la portada si ahora existe una para su álbum.
                        val a = artByAlbum[old.albumKey]
                        if (old.artPath != a && a != null) batch += old.copy(artPath = a)
                    } else {
                        batch += read(doc, old, folderCovers, artByAlbum, now)
                        updated++
                    }
                    if (batch.size >= 100) {
                        dao.upsertTracks(batch); batch.clear()
                    }
                }
                if (batch.isNotEmpty()) dao.upsertTracks(batch)

                val found = audio.mapTo(HashSet()) { it.uri.toString() }
                val removed = existing.keys.filter { it !in found }
                removed.chunked(500).forEach { dao.deleteTracks(it) }

                ScanState.Done(audio.size, updated, removed.size).also { _state.value = it }
            } catch (e: Exception) {
                ScanState.Failed(e.message ?: e.javaClass.simpleName).also { _state.value = it }
            }
        }
    }

    private fun walk(tree: Uri, audio: MutableList<Doc>, covers: MutableMap<String, Uri>) {
        val cr = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val stack = ArrayDeque<String>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        stack.addLast(rootId)
        while (stack.isNotEmpty()) {
            val parent = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val coverCandidates = ArrayList<Pair<String, Uri>>()
            val cursor = cr.query(children, projection, null, null, null)
            // Si no se puede leer la carpeta raíz (permiso perdido, tarjeta SD sin montar…), abortamos:
            // de lo contrario parecería vacía y se borraría toda la biblioteca.
            if (cursor == null && parent == rootId) throw IllegalStateException(S.folderUnavailable)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    val lm = if (c.isNull(3)) 0L else c.getLong(3)
                    val size = if (c.isNull(4)) 0L else c.getLong(4)
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> if (!name.startsWith(".")) stack.addLast(id)
                        isAudio(name, mime) -> audio.add(
                            Doc(DocumentsContract.buildDocumentUriUsingTree(tree, id), parent, name, mime, size, lm)
                        )
                        isImage(name) -> coverCandidates.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }
            pickFolderCover(coverCandidates)?.let { covers[parent] = it }
        }
    }

    private fun pickFolderCover(list: List<Pair<String, Uri>>): Uri? {
        if (list.isEmpty()) return null
        val preferred = listOf("cover", "folder", "front", "album", "albumart")
        for (p in preferred) {
            list.firstOrNull { it.first.substringBeforeLast('.').lowercase() == p }?.let { return it.second }
        }
        for (p in preferred) {
            list.firstOrNull { it.first.lowercase().contains(p) }?.let { return it.second }
        }
        return if (list.size == 1) list[0].second else null
    }

    private fun read(
        doc: Doc, old: Track?, folderCovers: Map<String, Uri>,
        artByAlbum: MutableMap<String, String>, now: Long,
    ): Track {
        val r = MediaMetadataRetriever()
        var ok = false
        try {
            r.setDataSource(context, doc.uri)
            ok = true
        } catch (_: Exception) {
        }
        fun m(k: Int): String = if (!ok) "" else (runCatching { r.extractMetadata(k) }.getOrNull()?.trim() ?: "")

        val title = m(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { doc.name.substringBeforeLast('.') }
        val albumArtist = m(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val artist = m(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { albumArtist }.ifBlank { UNKNOWN_ARTIST }
        val album = m(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { UNKNOWN_ALBUM }
        val groupArtist = albumArtist.ifBlank { artist }
        val albumKey = albumKeyFor(albumArtist, album, doc.folderId)
        val year = parseYear(m(MediaMetadataRetriever.METADATA_KEY_YEAR))
            .takeIf { it > 0 } ?: parseYear(m(MediaMetadataRetriever.METADATA_KEY_DATE))
        val sampleRate = if (Build.VERSION.SDK_INT >= 31) m(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE).toIntOrNull() ?: 0 else 0
        val bits = if (Build.VERSION.SDK_INT >= 31) m(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE).toIntOrNull() ?: 0 else 0

        var artPath = artByAlbum[albumKey]
        if (artPath == null && ok) {
            val pic = runCatching { r.embeddedPicture }.getOrNull()
            val jpeg = pic?.let { art.toJpeg(it) } ?: folderCovers[doc.folderId]?.let { uri ->
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { art.toJpeg(it) }
            }
            if (jpeg != null) {
                artPath = art.writeAlbumArt(albumKey, jpeg)
                artByAlbum[albumKey] = artPath
            }
        }
        runCatching { r.release() }

        return Track(
            uri = doc.uri.toString(),
            folderId = doc.folderId,
            fileName = doc.name,
            mime = doc.mime,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            groupArtist = groupArtist,
            albumKey = albumKey,
            trackNumber = leadingInt(m(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
            discNumber = leadingInt(m(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)).coerceAtLeast(1),
            year = year,
            genre = cleanGenre(m(MediaMetadataRetriever.METADATA_KEY_GENRE)),
            durationMs = m(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
            bitrate = m(MediaMetadataRetriever.METADATA_KEY_BITRATE).toIntOrNull() ?: 0,
            sampleRate = sampleRate,
            bitsPerSample = bits,
            size = doc.size,
            lastModified = doc.lastModified,
            addedAt = old?.addedAt ?: now,
            artPath = artPath,
        )
    }

    companion object {
        private val AUDIO_EXT = setOf(
            "mp3", "flac", "m4a", "m4b", "aac", "alac", "ogg", "oga", "opus", "wav", "aif", "aiff",
            "wma", "ape", "wv", "mka", "dsf", "mp4",
        )
        private val NOT_AUDIO = setOf("m3u", "m3u8", "pls", "cue", "lrc", "nfo", "log", "txt")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

        fun isAudio(name: String, mime: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in NOT_AUDIO) return false
            return ext in AUDIO_EXT || (mime.startsWith("audio/") && !mime.contains("mpegurl"))
        }

        fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT

        /**
         * Clave de agrupación de un álbum. Si hay "artista del álbum" se usa junto al título;
         * si no, se agrupa por carpeta + título para que los recopilatorios sin etiquetar no se rompan
         * en un álbum por artista.
         */
        fun albumKeyFor(albumArtist: String, album: String, folderId: String): String =
            if (albumArtist.isNotBlank()) "aa:${albumArtist.trim().lowercase()}|${album.trim().lowercase()}"
            else "f:$folderId|${album.trim().lowercase()}"

        fun leadingInt(s: String): Int = Regex("^\\s*(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        fun parseYear(s: String): Int = Regex("(\\d{4})").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        private fun cleanGenre(g: String): String {
            val t = g.trim()
            if (Regex("^\\(?\\d+\\)?$").matches(t)) return ""
            return t
        }
    }
}
