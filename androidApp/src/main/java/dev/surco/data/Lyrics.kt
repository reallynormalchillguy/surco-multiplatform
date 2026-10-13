package dev.surco.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.surco.net.LrcLib
import dev.surco.tags.TagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Busca la letra de una canción, por este orden:
 * caché → archivo .lrc junto a la canción → letra incrustada en las etiquetas → LRCLIB (internet).
 */
class LyricsRepository(
    private val context: Context,
    private val dao: LyricsDao,
    private val prefs: Prefs,
    private val tags: TagWriter,
    private val lrclib: LrcLib,
) {
    suspend fun load(track: Track, force: Boolean = false): Lyrics = withContext(Dispatchers.IO) {
        if (!force) {
            dao.get(track.uri)?.let { cached ->
                val fresh = cached.source != "none" || System.currentTimeMillis() - cached.fetchedAt < RETRY_NONE_MS
                if (fresh) return@withContext cached.toLyrics()
            }
        }
        val found = fromSidecar(track) ?: fromTags(track) ?: fromLrcLib(track)
        val entry = found ?: LyricsEntry(track.uri, null, null, "none", System.currentTimeMillis())
        dao.put(entry)
        entry.toLyrics()
    }

    /** Guarda una letra escrita a mano (o la quita si está vacía). */
    suspend fun saveManual(track: Track, text: String) {
        val t = text.trim()
        val entry = if (t.isEmpty()) LyricsEntry(track.uri, null, null, "none", System.currentTimeMillis())
        else if (LrcParser.isSynced(t)) LyricsEntry(track.uri, t, null, "manual", System.currentTimeMillis())
        else LyricsEntry(track.uri, null, t, "manual", System.currentTimeMillis())
        dao.put(entry)
    }

    /** Incrusta la letra actual en el archivo de audio. */
    suspend fun embed(track: Track, text: String) {
        tags.write(Uri.parse(track.uri), track.fileName, dev.surco.tags.TagEdits(lyrics = text))
    }

    private fun LyricsEntry.toLyrics(): Lyrics = when {
        source == "instrumental" -> Lyrics.Instrumental
        !synced.isNullOrBlank() -> LrcParser.parse(synced).takeIf { it.isNotEmpty() }
            ?.let { Lyrics.Synced(it, source, synced) }
            ?: Lyrics.Plain(LrcParser.plainLines(synced), source, synced)
        !plain.isNullOrBlank() -> Lyrics.Plain(LrcParser.plainLines(plain), source, plain)
        else -> Lyrics.None
    }

    private fun entryFromText(track: Track, text: String, source: String): LyricsEntry? {
        if (text.isBlank()) return null
        return if (LrcParser.isSynced(text)) LyricsEntry(track.uri, text, null, source, System.currentTimeMillis())
        else LyricsEntry(track.uri, null, text, source, System.currentTimeMillis())
    }

    private fun fromSidecar(track: Track): LyricsEntry? = runCatching {
        val tree = prefs.current.treeUri?.let(Uri::parse) ?: return null
        val base = track.fileName.substringBeforeLast('.').lowercase()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, track.folderId)
        val proj = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        var lrcId: String? = null
        context.contentResolver.query(children, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.lowercase() == "$base.lrc") { lrcId = c.getString(0); break }
            }
        }
        val id = lrcId ?: return null
        val bytes = context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))
            ?.use { it.readBytes() } ?: return null
        entryFromText(track, decode(bytes), "lrc")
    }.getOrNull()

    private suspend fun fromTags(track: Track): LyricsEntry? {
        if (track.size > MAX_TAG_READ) return null
        val text = runCatching { tags.readLyrics(Uri.parse(track.uri), track.fileName) }.getOrNull() ?: return null
        return entryFromText(track, text, "tag")
    }

    private suspend fun fromLrcLib(track: Track): LyricsEntry? {
        val r = lrclib.find(
            artist = track.artist.takeIf { it != UNKNOWN_ARTIST } ?: return null,
            title = track.title,
            album = track.album.takeIf { it != UNKNOWN_ALBUM },
            durationSec = (track.durationMs / 1000).toInt(),
        ) ?: return null
        val now = System.currentTimeMillis()
        return when {
            r.instrumental -> LyricsEntry(track.uri, null, null, "instrumental", now)
            !r.synced.isNullOrBlank() -> LyricsEntry(track.uri, r.synced, r.plain, "lrclib", now)
            !r.plain.isNullOrBlank() -> LyricsEntry(track.uri, null, r.plain, "lrclib", now)
            else -> null
        }
    }

    private fun decode(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8)
        // Archivos .lrc antiguos en Latin-1/Windows-1252: si no es UTF-8 válido aparece el carácter de reemplazo.
        return if ('�' in utf) String(bytes, charset("windows-1252")) else utf.removePrefix("﻿")
    }

    companion object {
        /** Si no se encontró letra, se vuelve a intentar pasada una semana. */
        private const val RETRY_NONE_MS = 7L * 24 * 3600 * 1000
        private const val MAX_TAG_READ = 250L * 1024 * 1024
    }
}
