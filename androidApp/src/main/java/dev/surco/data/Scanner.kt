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

/**
 * Recorre la carpeta elegida (Storage Access Framework) y mantiene la tabla de canciones al día.
 * Es incremental: sólo vuelve a leer los archivos cuya fecha o tamaño ha cambiado.
 */
class Scanner(
    private val context: Context,
    private val dao: LibraryDao,
    private val art: ArtStore,
) : LibraryScanner {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    override val state: StateFlow<ScanState> = _state.asStateFlow()
    private val mutex = Mutex()

    private data class Doc(
        val uri: Uri, val folderId: String, val name: String, val mime: String,
        val size: Long, val lastModified: Long,
    )

    override suspend fun scan(root: String): ScanState = scanTree(Uri.parse(root))

    private suspend fun scanTree(treeUri: Uri): ScanState = mutex.withLock {
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
                        ScanRules.isAudio(name, mime) -> audio.add(
                            Doc(DocumentsContract.buildDocumentUriUsingTree(tree, id), parent, name, mime, size, lm)
                        )
                        ScanRules.isImage(name) -> coverCandidates.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }
            ScanRules.pickFolderCover(coverCandidates)?.let { covers[parent] = it }
        }
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
        val albumKey = ScanRules.albumKeyFor(albumArtist, album, doc.folderId)
        val year = ScanRules.parseYear(m(MediaMetadataRetriever.METADATA_KEY_YEAR))
            .takeIf { it > 0 } ?: ScanRules.parseYear(m(MediaMetadataRetriever.METADATA_KEY_DATE))
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
            trackNumber = ScanRules.leadingInt(m(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
            discNumber = ScanRules.leadingInt(m(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)).coerceAtLeast(1),
            year = year,
            genre = ScanRules.cleanGenre(m(MediaMetadataRetriever.METADATA_KEY_GENRE)),
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

    private data class Child(val id: String, val name: String, val mime: String) {
        val isDir get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun children(tree: Uri, parentId: String): List<Child>? {
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                buildList { while (c.moveToNext()) add(Child(c.getString(0) ?: continue, c.getString(1) ?: "", c.getString(2) ?: "")) }
            }
        }.getOrNull()
    }

    private fun deleteDoc(tree: Uri, id: String): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }.getOrDefault(false)

    /**
     * Borra del dispositivo, para siempre, los archivos de estas canciones (y su .lrc). Después quita
     * la carpeta del álbum si ya no le queda música (con su portada, .cue, .log…) y las carpetas
     * superiores que hayan quedado vacías. Nunca toca la carpeta raíz de la biblioteca.
     */
    override suspend fun deleteTracks(root: String, tracks: List<Track>): DeleteResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(root)
            val cr = context.contentResolver
            val deleted = ArrayList<String>()
            val failed = ArrayList<String>()
            val listings = HashMap<String, Set<String>?>()
            for (t in tracks) {
                val uri = Uri.parse(t.uri)
                val ok = runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)
                // Si no se pudo borrar, sólo cuenta como borrada si su carpeta se puede leer y ya no está en ella
                // (un proveedor caído o sin permiso NO significa que el archivo no exista).
                val gone = ok || run {
                    val ids = listings.getOrPut(t.folderId) { children(tree, t.folderId)?.map { it.id }?.toSet() }
                    val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ids != null && id != null && id !in ids
                }
                if (gone) deleted += t.uri else failed += t.fileName
            }
            // Limpieza de carpetas: lo mejor posible. Si falla, los archivos ya borrados siguen contando
            // como borrados (para quitarlos de la biblioteca); nunca se deja la biblioteca a medias.
            var removedFolders = 0
            runCatching { removedFolders = pruneFolders(tree, tracks, deleted.toHashSet()) }
            DeleteResult(deleted, failed, removedFolders)
        }
    }

    private fun pruneFolders(tree: Uri, tracks: List<Track>, deletedSet: Set<String>): Int {
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            var removedFolders = 0
            for (folder in tracks.map { it.folderId }.distinct()) {
                val bases = tracks.filter { it.folderId == folder && it.uri in deletedSet }
                    .map { it.fileName.substringBeforeLast('.').lowercase() }.toSet()
                val kids = children(tree, folder) ?: continue
                kids.filter { it.name.lowercase().endsWith(".lrc") && it.name.substringBeforeLast('.').lowercase() in bases }
                    .forEach { deleteDoc(tree, it.id) }
                if (folder == rootId) continue
                val left = children(tree, folder) ?: continue
                // Sólo si lo que queda es «basura» de álbum conocida; cualquier otra cosa (vídeo, PDF, .zip…) la conserva.
                if (left.any { it.isDir || !ScanRules.isAlbumLeftover(it.name) }) continue
                // La carpeta del álbum ya no tiene música: fuera, con lo que quede (portada, .cue, .log…).
                val path = runCatching {
                    DocumentsContract.findDocumentPath(cr, DocumentsContract.buildDocumentUriUsingTree(tree, folder))?.path
                }.getOrNull()
                if (!deleteDoc(tree, folder)) continue
                removedFolders++
                // Carpetas de arriba (p. ej. la del artista) sólo si quedan completamente vacías.
                if (path != null) {
                    val idx = path.indexOf(folder)
                    var i = (if (idx >= 0) idx else path.size - 1) - 1
                    while (i >= 0) {
                        val up = path[i]
                        if (up == rootId || !path.subList(0, i + 1).contains(rootId)) break
                        if (children(tree, up)?.isEmpty() != true || !deleteDoc(tree, up)) break
                        removedFolders++
                        i--
                    }
                }
            }
            return removedFolders
    }

    override suspend fun readSibling(root: String, track: Track, fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val tree = Uri.parse(root)
            val target = fileName.lowercase()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, track.folderId)
            val proj = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            var found: String? = null
            context.contentResolver.query(children, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (name.lowercase() == target) { found = c.getString(0); break }
                }
            }
            val id = found ?: return@runCatching null
            context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes() }
        }.getOrNull()
    }
}
