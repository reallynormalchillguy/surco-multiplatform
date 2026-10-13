package dev.surco.data

import dev.surco.net.LrcLib
import dev.surco.tags.TagEditor
import dev.surco.util.Charsets1252
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
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
    suspend fun load(track: Track, force: Boolean = false): Lyrics = withContext(IoDispatcher) {
        if (!force) {
            dao.get(track.uri)?.let { cached ->
                val fresh = cached.source != "none" || nowMillis() - cached.fetchedAt < RETRY_NONE_MS
                if (fresh) return@withContext cached.toLyrics()
            }
        }
        val found = fromSidecar(track) ?: fromTags(track) ?: fromLrcLib(track)
        val entry = found ?: LyricsEntry(track.uri, null, null, "none", nowMillis())
        dao.put(entry)
        entry.toLyrics()
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

    private suspend fun fromLrcLib(track: Track): LyricsEntry? {
        val r = lrclib.find(
            artist = track.artist.takeIf { it != UNKNOWN_ARTIST } ?: return null,
            title = track.title,
            album = track.album.takeIf { it != UNKNOWN_ALBUM },
            durationSec = (track.durationMs / 1000).toInt(),
        ) ?: return null
        val now = nowMillis()
        return when {
            r.instrumental -> LyricsEntry(track.uri, null, null, "instrumental", now)
            !r.synced.isNullOrBlank() -> LyricsEntry(track.uri, r.synced, r.plain, "lrclib", now)
            !r.plain.isNullOrBlank() -> LyricsEntry(track.uri, null, r.plain, "lrclib", now)
            else -> null
        }
    }

    companion object {
        /** Si no se encontró letra, se vuelve a intentar pasada una semana. */
        private const val RETRY_NONE_MS = 7L * 24 * 3600 * 1000
        private const val MAX_TAG_READ = 250L * 1024 * 1024
    }
}
