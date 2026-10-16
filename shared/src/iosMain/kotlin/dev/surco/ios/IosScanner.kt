package dev.surco.ios

import dev.surco.data.ArtStore
import dev.surco.data.DeleteResult
import dev.surco.data.LibraryDao
import dev.surco.data.LibraryScanner
import dev.surco.data.ScanRules
import dev.surco.data.ScanState
import dev.surco.data.Track
import dev.surco.data.UNKNOWN_ALBUM
import dev.surco.data.UNKNOWN_ARTIST
import dev.surco.i18n.S
import dev.surco.tags.AudioTags
import dev.surco.tags.TagReader
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970

/**
 * Escáner de iOS: recorre la carpeta elegida (marcador de seguridad) y lee las etiquetas con el lector
 * común en Kotlin puro. Incremental como el de Android: sólo relee lo que cambió de fecha o tamaño.
 */
@OptIn(ExperimentalForeignApi::class)
class IosScanner(private val dao: LibraryDao, private val art: ArtStore) : LibraryScanner {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    override val state: StateFlow<ScanState> = _state.asStateFlow()
    private val mutex = Mutex()

    private class Doc(val url: NSURL, val folder: String, val name: String, val size: Long, val modified: Long)

    override suspend fun scan(root: String): ScanState = mutex.withLock {
        withContext(IoDispatcher) {
            try {
                val base = IosLibrary.resolve(root) ?: error(S.folderUnavailable)
                _state.value = ScanState.Running(0, 0, "")
                val audio = ArrayList<Doc>()
                val covers = HashMap<String, MutableList<Pair<String, NSURL>>>()
                walk(base, audio, covers)
                val old = dao.allTracksOnce().associateBy { it.uri }
                val seen = HashSet<String>()
                val artByAlbum = HashMap<String, String>()
                old.values.forEach { t -> t.artPath?.let { if (art.exists(it)) artByAlbum[t.albumKey] = it } }
                val batch = ArrayList<Track>()
                var updated = 0
                val now = nowMillis()
                audio.forEachIndexed { i, d ->
                    val uri = d.url.absoluteString ?: return@forEachIndexed
                    seen += uri
                    val prev = old[uri]
                    if (prev != null && prev.size == d.size && prev.lastModified == d.modified) return@forEachIndexed
                    if (i % 5 == 0) _state.value = ScanState.Running(i, audio.size, d.name)
                    val folderCover = ScanRules.pickFolderCover(covers[d.folder].orEmpty())
                    batch += read(d, uri, prev, folderCover, artByAlbum, now)
                    updated++
                    if (batch.size >= 50) { dao.upsertTracks(batch.toList()); batch.clear() }
                }
                if (batch.isNotEmpty()) dao.upsertTracks(batch)
                val removed = old.keys.filter { it !in seen }
                removed.chunked(500).forEach { dao.deleteTracks(it) }
                ScanState.Done(audio.size, updated, removed.size).also { _state.value = it }
            } catch (e: Throwable) {
                ScanState.Failed(e.message ?: e.toString()).also { _state.value = it }
            }
        }
    }

    private fun walk(base: NSURL, audio: MutableList<Doc>, covers: MutableMap<String, MutableList<Pair<String, NSURL>>>) {
        val keys = listOf(NSURLIsDirectoryKey, NSURLFileSizeKey, NSURLContentModificationDateKey)
        val e = NSFileManager.defaultManager.enumeratorAtURL(base, keys, NSDirectoryEnumerationSkipsHiddenFiles, null) ?: return
        while (true) {
            val url = e.nextObject() as? NSURL ?: break
            val values = url.resourceValuesForKeys(keys, null) ?: continue
            if ((values[NSURLIsDirectoryKey] as? NSNumber)?.boolValue == true) continue
            val name = url.lastPathComponent ?: continue
            val folder = url.URLByDeletingLastPathComponent?.path ?: ""
            when {
                ScanRules.isAudio(name, "") -> audio += Doc(
                    url, folder, name,
                    (values[NSURLFileSizeKey] as? NSNumber)?.longLongValue ?: 0,
                    ((values[NSURLContentModificationDateKey] as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(1000).toLong(),
                )
                ScanRules.isImage(name) -> covers.getOrPut(folder) { ArrayList() } += name to url
            }
        }
    }

    private fun read(d: Doc, uri: String, old: Track?, folderCover: NSURL?, artByAlbum: MutableMap<String, String>, now: Long): Track {
        val tags = runCatching { d.url.withSource { TagReader.read(it, d.name) } }.getOrNull() ?: AudioTags()
        val title = tags.title.ifBlank { d.name.substringBeforeLast('.') }
        val albumArtist = tags.albumArtist
        val artist = tags.artist.ifBlank { albumArtist }.ifBlank { UNKNOWN_ARTIST }
        val album = tags.album.ifBlank { UNKNOWN_ALBUM }
        val albumKey = ScanRules.albumKeyFor(albumArtist, album, d.folder)
        var artPath = artByAlbum[albumKey]
        if (artPath == null) {
            val jpeg = tags.cover?.let { art.toJpeg(it) }
                ?: folderCover?.let { NSData.dataWithContentsOfURL(it)?.toByteArray() }?.let { art.toJpeg(it) }
            if (jpeg != null) {
                artPath = art.writeAlbumArt(albumKey, jpeg)
                artByAlbum[albumKey] = artPath
            }
        }
        val duration = tags.durationMs.takeIf { it > 0 } ?: assetDuration(d.url)
        return Track(
            uri = uri,
            folderId = d.folder,
            fileName = d.name,
            mime = "audio/" + d.name.substringAfterLast('.', "").lowercase(),
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            groupArtist = albumArtist.ifBlank { artist },
            albumKey = albumKey,
            trackNumber = ScanRules.leadingInt(tags.trackNumber),
            discNumber = ScanRules.leadingInt(tags.discNumber).coerceAtLeast(1),
            year = ScanRules.parseYear(tags.year),
            genre = ScanRules.cleanGenre(tags.genre),
            durationMs = duration,
            bitrate = tags.bitrate,
            sampleRate = tags.sampleRate,
            bitsPerSample = tags.bitsPerSample,
            size = d.size,
            lastModified = d.modified,
            addedAt = old?.addedAt ?: now,
            artPath = artPath,
        )
    }

    private fun assetDuration(url: NSURL): Long = runCatching {
        val sec = CMTimeGetSeconds(AVURLAsset(uRL = url, options = null).duration)
        if (sec.isNaN() || sec <= 0) 0L else (sec * 1000).toLong()
    }.getOrDefault(0L)

    override suspend fun deleteTracks(root: String, tracks: List<Track>): DeleteResult = mutex.withLock {
        withContext(IoDispatcher) {
            val base = IosLibrary.resolve(root) ?: error(S.folderUnavailable)
            val rootPath = (base.path ?: error(S.folderUnavailable)).trimEnd('/')
            val fm = NSFileManager.defaultManager
            val deleted = ArrayList<String>()
            val failed = ArrayList<String>()
            for (t in tracks) {
                val path = NSURL.URLWithString(t.uri)?.path
                when {
                    path == null || !path.startsWith("$rootPath/") -> failed += t.fileName
                    !fm.fileExistsAtPath(path) -> deleted += t.uri       // ya no estaba: cuenta como borrada
                    fm.removeItemAtPath(path, null) -> deleted += t.uri
                    else -> failed += t.fileName
                }
            }
            fun list(dir: String) = fm.contentsOfDirectoryAtPath(dir, null)?.mapNotNull { it as? String }.orEmpty()
            fun isDir(path: String) = (fm.attributesOfItemAtPath(path, null)?.get(NSFileType) as? String) == NSFileTypeDirectory
            var removedFolders = 0
            val deletedSet = deleted.toHashSet()
            for (folder in tracks.map { it.folderId.trimEnd('/') }.distinct()) {
                val bases = tracks.filter { it.folderId.trimEnd('/') == folder && it.uri in deletedSet }
                    .map { it.fileName.substringBeforeLast('.').lowercase() }.toSet()
                list(folder).filter { it.lowercase().endsWith(".lrc") && it.substringBeforeLast('.').lowercase() in bases }
                    .forEach { fm.removeItemAtPath("$folder/$it", null) }
                if (folder == rootPath || !folder.startsWith("$rootPath/")) continue
                val left = list(folder)
                if (left.any { ScanRules.isAudio(it, "") || isDir("$folder/$it") }) continue
                // La carpeta del álbum ya no tiene música: fuera, con lo que quede (portada, .cue, .log…).
                if (!fm.removeItemAtPath(folder, null)) continue
                removedFolders++
                // Carpetas de arriba (p. ej. la del artista) sólo si quedan vacías (sin contar ocultos).
                var up = folder.substringBeforeLast('/')
                while (up.startsWith("$rootPath/") && list(up).none { !it.startsWith(".") }) {
                    if (!fm.removeItemAtPath(up, null)) break
                    removedFolders++
                    up = up.substringBeforeLast('/')
                }
            }
            DeleteResult(deleted, failed, removedFolders)
        }
    }

    override suspend fun readSibling(root: String, track: Track, fileName: String): ByteArray? = withContext(IoDispatcher) {
        IosLibrary.resolve(root) ?: return@withContext null
        val dir = NSURL.fileURLWithPath(track.folderId)
        val target = fileName.lowercase()
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(track.folderId, null)?.mapNotNull { it as? String }.orEmpty()
        val match = names.firstOrNull { it.lowercase() == target } ?: return@withContext null
        dir.URLByAppendingPathComponent(match)?.let { NSData.dataWithContentsOfURL(it)?.toByteArray() }
    }
}
