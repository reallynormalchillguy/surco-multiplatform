package dev.surco.data

import dev.surco.net.LrcLib
import dev.surco.tags.TagEditor
import dev.surco.util.Charsets1252
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Busca la letra de una canción, por este orden:
 * caché → archivo .lrc junto a la canción → letra incrustada en las etiquetas → LRCLIB (internet).
 */
class LyricsRepository(
    private val dao: LyricsDao,
    private val prefs: Prefs,
    private val scanner: LibraryScanner,
    private val tags: TagEditor,
    private val lrclib: LrcLib,
) {
    /** Búsquedas en curso por canción: si el reproductor y la precarga piden la misma, se hace una sola vez. */
    private val inFlight = HashMap<String, Deferred<Lyrics>>()
    private val inFlightLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)

    /**
     * Devuelve la letra de [track]. Un fallo de red NUNCA se guarda como «sin letra»: se devuelve
     * [Lyrics.Failed] para que la interfaz vuelva a intentarlo sola.
     */
    suspend fun load(track: Track, force: Boolean = false): Lyrics {
        if (!force) cached(track)?.let { return it }
        val job = inFlightLock.withLock {
            inFlight[track.uri]?.takeIf { !force && it.isActive } ?: scope.async {
                val me = coroutineContext[Job]
                try { fetch(track) } finally {
                    withContext(NonCancellable) {
                        inFlightLock.withLock { if (inFlight[track.uri] === me) inFlight.remove(track.uri) }
                    }
                }
            }.also { inFlight[track.uri] = it }
        }
        // La búsqueda sigue aunque se cierre la pantalla: así el resultado queda en caché para la próxima vez.
        return job.await()
    }

    /** Busca en segundo plano la letra de la canción que empieza a sonar, para tenerla lista al abrir el reproductor. */
    fun prefetch(track: Track) {
        scope.launch { runCatching { load(track) } }
    }

    private suspend fun cached(track: Track): Lyrics? = withContext(IoDispatcher) {
        val c = dao.get(track.uri) ?: return@withContext null
        val fresh = c.source != "none" || nowMillis() - c.fetchedAt < RETRY_NONE_MS
        if (fresh) c.toLyrics() else null
    }

    private suspend fun fetch(track: Track): Lyrics {
        val local = fromSidecar(track) ?: fromTags(track)
        if (local != null) { dao.put(local); return local.toLyrics() }
        val entry = when (val r = fromLrcLib(track)) {
            Remote.Failed -> return Lyrics.Failed
            Remote.NotFound -> LyricsEntry(track.uri, null, null, "none", nowMillis())
            is Remote.Found -> r.entry
        }
        dao.put(entry)
        return entry.toLyrics()
    }

    private sealed interface Remote {
        data class Found(val entry: LyricsEntry) : Remote
        data object NotFound : Remote
        data object Failed : Remote
    }

    /** Guarda una letra escrita a mano (o la quita si está vacía). */
    suspend fun saveManual(track: Track, text: String) {
        val t = text.trim()
        val entry = if (t.isEmpty()) LyricsEntry(track.uri, null, null, "none", nowMillis())
        else if (LrcParser.isSynced(t)) LyricsEntry(track.uri, t, null, "manual", nowMillis())
        else LyricsEntry(track.uri, null, t, "manual", nowMillis())
        dao.put(entry)
    }

    /** Incrusta la letra actual en el archivo de audio. */
    suspend fun embed(track: Track, text: String) {
        tags.write(track.uri, track.fileName, dev.surco.tags.TagEdits(lyrics = text))
    }

    private fun LyricsEntry.toLyrics(): Lyrics {
        val synced = synced
        val plain = plain
        return when {
        source == "instrumental" -> Lyrics.Instrumental
        !synced.isNullOrBlank() -> LrcParser.parse(synced).takeIf { it.isNotEmpty() }
            ?.let { Lyrics.Synced(it, source, synced) }
            ?: Lyrics.Plain(LrcParser.plainLines(synced), source, synced)
        !plain.isNullOrBlank() -> Lyrics.Plain(LrcParser.plainLines(plain), source, plain)
        else -> Lyrics.None
        }
    }

    private fun entryFromText(track: Track, text: String, source: String): LyricsEntry? {
        if (text.isBlank()) return null
        return if (LrcParser.isSynced(text)) LyricsEntry(track.uri, text, null, source, nowMillis())
        else LyricsEntry(track.uri, null, text, source, nowMillis())
    }

    private suspend fun fromSidecar(track: Track): LyricsEntry? {
        val root = prefs.current.treeUri ?: return null
        val name = track.fileName.substringBeforeLast('.') + ".lrc"
        val bytes = runCatching { scanner.readSibling(root, track, name) }.getOrNull() ?: return null
        return entryFromText(track, Charsets1252.decodeText(bytes), "lrc")
    }

    private suspend fun fromTags(track: Track): LyricsEntry? {
        if (track.size > MAX_TAG_READ) return null
        val text = runCatching { tags.readLyrics(track.uri, track.fileName) }.getOrNull() ?: return null
        return entryFromText(track, text, "tag")
    }

    private suspend fun fromLrcLib(track: Track): Remote {
        val artist = track.artist.takeIf { it != UNKNOWN_ARTIST } ?: return Remote.NotFound
        val r = when (val o = lrclib.find(
            artist = artist,
            title = track.title,
            album = track.album.takeIf { it != UNKNOWN_ALBUM },
            durationSec = (track.durationMs / 1000).toInt(),
        )) {
            LrcLib.Outcome.Failed -> return Remote.Failed
            LrcLib.Outcome.NotFound -> return Remote.NotFound
            is LrcLib.Outcome.Found -> o.result
        }
        val now = nowMillis()
        return when {
            r.instrumental -> Remote.Found(LyricsEntry(track.uri, null, null, "instrumental", now))
            !r.synced.isNullOrBlank() -> Remote.Found(LyricsEntry(track.uri, r.synced, r.plain, "lrclib", now))
            !r.plain.isNullOrBlank() -> Remote.Found(LyricsEntry(track.uri, null, r.plain, "lrclib", now))
            else -> Remote.NotFound
        }
    }

    companion object {
        /** Si LRCLIB respondió que no la tiene, se vuelve a preguntar pasados 3 días (puede añadirse después). */
        private const val RETRY_NONE_MS = 3L * 24 * 3600 * 1000
        private const val MAX_TAG_READ = 250L * 1024 * 1024
    }
}
