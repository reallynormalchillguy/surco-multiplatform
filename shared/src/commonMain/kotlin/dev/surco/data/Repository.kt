package dev.surco.data

import dev.surco.i18n.S

import dev.surco.net.CoverSearch
import dev.surco.net.LastFm
import dev.surco.net.LastFmException
import dev.surco.net.toScrobbleItem
import dev.surco.tags.TagEdits
import dev.surco.tags.TagEditor
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class JobProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val message: String = "")

data class SearchResults(
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

/** Marca (en `checkedAt`) de una foto de artista elegida por el usuario. */
const val CUSTOM_ARTIST_IMAGE = Long.MAX_VALUE

class Repository(
    val dao: LibraryDao,
    val prefs: Prefs,
    val scanner: LibraryScanner,
    private val art: ArtStore,
    private val tags: TagEditor,
    val covers: CoverSearch,
    val lastFm: LastFm,
    private val appScope: CoroutineScope,
    /** Programa un reintento de los scrobbles pendientes para cuando haya conexión (WorkManager). */
    private val scheduleScrobbleRetry: () -> Unit = {},
) {
    // ---------------- Escaneo ----------------
    /** Momento del último escaneo pedido, para no repetirlos seguidos. */
    var lastRescanAt = 0L
        private set

    /**
     * Escaneo incremental. [auto] = lo pide el escaneo automático: entonces sólo se buscan portadas
     * si de verdad cambió algo (si no, cada aviso repetiría búsquedas por internet de álbumes sin portada).
     */
    fun rescan(auto: Boolean = false): Job? {
        val tree = prefs.current.treeUri ?: return null
        lastRescanAt = nowMillis()
        return appScope.launch {
            val result = scanner.scan(tree)
            if (result is ScanState.Done) {
                val changed = result.updated > 0 || result.removed > 0
                if (prefs.current.autoCoversAfterScan && (!auto || changed)) fillMissingCovers()
                if (prefs.current.artistImages && (!auto || changed)) fetchArtistImages()
            }
        }
    }

    /**
     * Borra un álbum entero del dispositivo (para siempre) y de la biblioteca.
     * Devuelve el resultado; las canciones que no se pudieron borrar siguen en la biblioteca.
     */
    suspend fun deleteAlbum(albumKey: String): DeleteResult {
        val tree = prefs.current.treeUri ?: throw IllegalStateException(S.folderUnavailable)
        val tracks = dao.albumTracksOnce(albumKey)
        val oldArt = tracks.firstOrNull { it.artPath != null }?.artPath
        val r = scanner.deleteTracks(tree, tracks)
        r.deleted.chunked(500).forEach { chunk ->
            dao.deleteTracks(chunk)
            dao.removeEntriesForTracks(chunk)
            dao.unlikeAll(chunk)
            dao.deleteLyrics(chunk)
        }
        if (r.failed.isEmpty()) art.delete(oldArt)
        return r
    }

    // ---------------- Me gusta / listas ----------------
    suspend fun setLiked(uri: String, liked: Boolean) {
        if (liked) dao.like(Liked(uri, nowMillis())) else dao.unlike(uri)
    }

    suspend fun createPlaylist(name: String): Long {
        val now = nowMillis()
        return dao.insertPlaylist(Playlist(name = name.trim().ifBlank { S.newPlaylist }, createdAt = now, updatedAt = now))
    }

    suspend fun addToPlaylist(id: Long, uris: List<String>) {
        var pos = dao.maxPosition(id) + 1
        val now = nowMillis()
        uris.forEach { dao.insertEntry(PlaylistEntry(playlistId = id, trackUri = it, position = pos++, addedAt = now)) }
        dao.touchPlaylist(id, now)
    }

    suspend fun deletePlaylist(id: Long) {
        dao.clearPlaylist(id)
        dao.deletePlaylist(id)
    }

    // ---------------- Búsqueda ----------------
    suspend fun search(q: String): SearchResults {
        val like = "%${q.trim()}%"
        return SearchResults(dao.searchArtists(like), dao.searchAlbums(like), dao.searchTracks(like))
    }

    /**
     * Apunta un texto buscado en el historial. Mientras escribes se guarda la búsqueda más completa:
     * «beat» se sustituye por «beatles» (y no al revés).
     */
    suspend fun rememberQuery(query: String) {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length < 2) return
        val low = q.lowercase()
        val old = dao.searchedQueries()
        if (old.any { it.ref.startsWith(low) && it.ref != low && it.at > nowMillis() - 60_000 }) return
        old.filter { low.startsWith(it.ref) && it.ref != low && it.at > nowMillis() - 60_000 }.forEach { dao.deleteSearchRecent(it.key) }
        dao.upsertSearchRecent(SearchRecent("query:$low", "query", low, q, "", null, nowMillis()))
    }

    suspend fun rememberSearch(type: String, ref: String, title: String, subtitle: String, artPath: String?) {
        dao.upsertSearchRecent(SearchRecent("$type:$ref", type, ref, title, subtitle, artPath, nowMillis()))
    }

    /** Convierte cualquier imagen a JPEG razonable (≤1200 px) o null si no es válida. */
    fun normalizeImage(bytes: ByteArray): ByteArray? = art.toJpeg(bytes)

    // ---------------- Edición de etiquetas ----------------
    /** Edita una canción. Devuelve la canción actualizada. */
    suspend fun editTrack(track: Track, edits: TagEdits): Track {
        tags.write(track.uri, track.fileName, edits)
        val albumArtist = edits.albumArtist ?: track.albumArtist
        val album = (edits.album ?: track.album).ifBlank { UNKNOWN_ALBUM }
        val artist = (edits.artist ?: track.artist).ifBlank { albumArtist.ifBlank { UNKNOWN_ARTIST } }
        val newKey = ScanRules.albumKeyFor(albumArtist, album, track.folderId)
        val updated = track.copy(
            title = (edits.title ?: track.title).ifBlank { track.fileName.substringBeforeLast('.') },
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            groupArtist = albumArtist.ifBlank { artist },
            albumKey = newKey,
            year = edits.year?.let { ScanRules.parseYear(it) } ?: track.year,
            trackNumber = edits.trackNumber?.let { ScanRules.leadingInt(it) } ?: track.trackNumber,
            discNumber = edits.discNumber?.let { ScanRules.leadingInt(it).coerceAtLeast(1) } ?: track.discNumber,
            genre = edits.genre?.trim() ?: track.genre,
            // Fuerza una relectura en el próximo escaneo para capturar el nuevo tamaño/fecha.
            lastModified = -1,
        )
        dao.upsertTracks(listOf(updated))
        return updated
    }

    /**
     * Edita todas las canciones de un álbum (campos comunes + portada).
     * Devuelve la nueva clave del álbum y la lista de errores (archivo -> motivo).
     */
    suspend fun editAlbum(
        albumKey: String,
        edits: TagEdits,
        onProgress: (Int, Int) -> Unit,
    ): Pair<String, List<String>> {
        val tracks = dao.albumTracksOnce(albumKey)
        val errors = mutableListOf<String>()
        var newKey = albumKey
        val oldArt = tracks.firstOrNull { it.artPath != null }?.artPath
        tracks.forEachIndexed { i, t ->
            onProgress(i, tracks.size)
            try {
                newKey = editTrack(t, edits).albumKey
            } catch (e: Exception) {
                errors += "${t.fileName}: ${e.message}"
            }
        }
        onProgress(tracks.size, tracks.size)
        // Portada en caché
        val cover = edits.cover
        if (cover == null) {
            if (newKey != albumKey && oldArt != null) dao.updateAlbumArt(newKey, oldArt)
        } else if (cover.isEmpty()) {
            dao.updateAlbumArt(newKey, null)
            art.delete(oldArt)
        } else {
            val path = withContext(IoDispatcher) { art.writeAlbumArt(newKey, cover) }
            dao.updateAlbumArt(newKey, path)
            if (oldArt != path) art.delete(oldArt)
        }
        return newKey to errors
    }

    /**
     * Aplica los datos de un álbum de internet (ver [AlbumMatcher.plan]) y, si se pasa, su portada.
     * Devuelve la nueva clave del álbum y los errores (archivo: motivo).
     */
    suspend fun applyAlbumInfo(
        albumKey: String,
        plans: List<TrackPlan>,
        cover: ByteArray?,
        onProgress: (Int, Int) -> Unit,
    ): Pair<String, List<String>> {
        val errors = mutableListOf<String>()
        var newKey = albumKey
        val todo = plans.filter { it.hasChanges }
        todo.forEachIndexed { i, p ->
            onProgress(i, todo.size)
            try {
                newKey = editTrack(
                    p.track,
                    TagEdits(
                        title = p.title, artist = p.artist, album = p.album, albumArtist = p.albumArtist,
                        year = p.year, trackNumber = p.trackNumber, discNumber = p.discNumber, genre = p.genre,
                    ),
                ).albumKey
            } catch (e: Exception) {
                errors += "${p.track.fileName}: ${e.message}"
            }
        }
        onProgress(todo.size, todo.size)
        if (cover != null) {
            val errs = setAlbumCover(newKey, cover, prefs.current.embedCovers)
            errors += errs
        }
        return newKey to errors
    }

    /** Pone una portada al álbum: la guarda en caché y, si [embed], la incrusta en los archivos. */
    suspend fun setAlbumCover(albumKey: String, imageBytes: ByteArray, embed: Boolean): List<String> {
        val jpeg = withContext(IoDispatcher) { art.toJpeg(imageBytes) } ?: return listOf(S.invalidImage)
        val tracks = dao.albumTracksOnce(albumKey)
        val oldArt = tracks.firstOrNull { it.artPath != null }?.artPath
        val path = withContext(IoDispatcher) { art.writeAlbumArt(albumKey, jpeg) }
        dao.updateAlbumArt(albumKey, path)
        if (oldArt != null && oldArt != path) art.delete(oldArt)
        val errors = mutableListOf<String>()
        if (embed) {
            tracks.forEach { t ->
                try {
                    tags.write(t.uri, t.fileName, TagEdits(cover = jpeg))
                } catch (e: Exception) {
                    errors += "${t.fileName}: ${e.message}"
                }
            }
        }
        return errors
    }

    // ---------------- Portadas automáticas ----------------
    private val _coverJob = MutableStateFlow(JobProgress())
    val coverJob: StateFlow<JobProgress> = _coverJob.asStateFlow()
    private val coverMutex = Mutex()

    fun startFillMissingCovers() {
        appScope.launch { fillMissingCovers() }
    }

    suspend fun fillMissingCovers() = coverMutex.withLock {
        val albums = dao.albumsMissingArt().filter { it.album != UNKNOWN_ALBUM }
        var found = 0
        albums.forEachIndexed { i, a ->
            _coverJob.value = JobProgress(true, i, albums.size, "${a.artist} — ${a.album}")
            val results = covers.search(a.artist.takeIf { it != UNKNOWN_ARTIST } ?: "", a.album)
            for (c in covers.matches(results, a.artist, a.album)) {
                val bytes = covers.download(c.fullUrl) ?: continue
                if (setAlbumCover(a.albumKey, bytes, prefs.current.embedCovers).firstOrNull() == S.invalidImage) continue
                found++
                break
            }
            delay(1100) // MusicBrainz pide como mucho una consulta por segundo
        }
        _coverJob.value = JobProgress(false, albums.size, albums.size,
            if (albums.isEmpty()) S.allAlbumsHaveCovers else S.foundOf(found, albums.size))
    }

    // ---------------- Fotos de artistas ----------------
    private val _artistJob = MutableStateFlow(JobProgress())
    val artistJob: StateFlow<JobProgress> = _artistJob.asStateFlow()
    private val artistMutex = Mutex()

    fun startFetchArtistImages() {
        appScope.launch { fetchArtistImages() }
    }

    suspend fun fetchArtistImages() = artistMutex.withLock {
        val names = dao.artistsWithoutImageCheck().filter { it != UNKNOWN_ARTIST }
        names.forEachIndexed { i, name ->
            _artistJob.value = JobProgress(true, i, names.size, name)
            val url = covers.artistPicture(name)
            val path = url?.let { covers.download(it) }?.let { art.toJpeg(it, 800) }?.let { art.writeArtistImage(name, it) }
            dao.upsertArtistImage(ArtistImage(name, path, nowMillis()))
            delay(250)
        }
        _artistJob.value = JobProgress(false, names.size, names.size, S.done)
    }

    /** Pone una foto elegida por el usuario a un artista. Las fotos propias nunca se sustituyen solas. */
    suspend fun setArtistImage(name: String, bytes: ByteArray): Boolean {
        val jpeg = withContext(IoDispatcher) { art.toJpeg(bytes, 1000) } ?: return false
        val old = dao.artistImage(name)?.path
        val path = withContext(IoDispatcher) { art.writeArtistImage(name, jpeg) }
        dao.upsertArtistImage(ArtistImage(name, path, CUSTOM_ARTIST_IMAGE))
        if (old != null && old != path) art.deleteArtistImage(old)
        return true
    }

    /** Vuelve a la foto automática (Deezer). Devuelve true si encontró una. */
    suspend fun resetArtistImage(name: String): Boolean {
        val old = dao.artistImage(name)?.path
        val url = covers.artistPicture(name)
        val path = url?.let { covers.download(it) }?.let { art.toJpeg(it, 800) }
            ?.let { withContext(IoDispatcher) { art.writeArtistImage(name, it) } }
        dao.upsertArtistImage(ArtistImage(name, path, nowMillis()))
        if (old != null && old != path) art.deleteArtistImage(old)
        return path != null
    }

    fun isCustomArtistImage(img: ArtistImage?) = img?.checkedAt == CUSTOM_ARTIST_IMAGE

    // ---------------- Scrobbling ----------------
    private val flushMutex = Mutex()
    private val _lastFmError = MutableStateFlow<String?>(null)
    val lastFmError: StateFlow<String?> = _lastFmError.asStateFlow()

    suspend fun enqueueScrobble(t: Track, startedAtSec: Long) {
        dao.insertScrobble(
            PendingScrobble(
                artist = t.artist, track = t.title, album = t.album, albumArtist = t.albumArtist,
                trackNumber = t.trackNumber, timestamp = startedAtSec, durationSec = t.durationMs / 1000,
            )
        )
        flushScrobbles()
    }

    /**
     * Envía la cola de scrobbles a Last.fm, en lotes de 50.
     * Devuelve true si la cola queda vacía (o no hay sesión: no tiene sentido reintentar).
     * Si falla por falta de conexión, programa un reintento automático para cuando vuelva la red.
     */
    suspend fun flushScrobbles(): Boolean = flushMutex.withLock {
        if (!prefs.current.lastfmLoggedIn) return@withLock true
        while (true) {
            val batch = dao.pendingScrobbles()
            if (batch.isEmpty()) return@withLock true
            try {
                val result = lastFm.scrobble(batch.map { it.toScrobbleItem() })
                dao.deleteScrobbles(batch.map { it.id })
                _lastFmError.value = if (result.tooOld > 0) S.scrobblesTooOld(result.tooOld) else null
            } catch (e: LastFmException) {
                _lastFmError.value = e.message
                if (e.code == LastFm.ERR_INVALID_SESSION || e.code == 4) {
                    lastFm.logout()
                    return@withLock true
                }
                // Errores de datos (p. ej. 6: parámetros) no se reintentan para no atascar la cola.
                if (e.code == 6 || e.code == 13) {
                    dao.deleteScrobbles(batch.map { it.id })
                    continue
                }
                // 11/16: servicio no disponible temporalmente → reintentar más tarde.
                scheduleScrobbleRetry()
                return@withLock false
            } catch (e: Exception) {
                _lastFmError.value = S.offlineScrobbles
                scheduleScrobbleRetry()
                return@withLock false
            }
        }
        @Suppress("UNREACHABLE_CODE")
        true
    }

    /** Días que lleva esperando el scrobble pendiente más antiguo (null si no hay). */
    suspend fun oldestPendingDays(): Long? {
        val ts = dao.oldestPendingScrobble().first() ?: return null
        return (nowMillis() / 1000 - ts) / 86_400
    }

    fun enqueueScrobbleAsync(t: Track, startedAtSec: Long) {
        appScope.launch { enqueueScrobble(t, startedAtSec) }
    }

    fun flushScrobblesAsync() {
        appScope.launch { flushScrobbles() }
    }
}
