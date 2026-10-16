package dev.surco.data

import kotlinx.coroutines.flow.StateFlow

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val done: Int, val total: Int, val current: String) : ScanState
    data class Done(val total: Int, val updated: Int, val removed: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/** Recorre la carpeta de música y mantiene la tabla de canciones al día (incremental). */
interface LibraryScanner {
    val state: StateFlow<ScanState>
    /** [root]: la carpeta elegida (URI SAF en Android, marcador de seguridad en iOS). */
    suspend fun scan(root: String): ScanState
    /** Lee un archivo que está junto a la canción (p. ej. su .lrc), o null si no existe. */
    suspend fun readSibling(root: String, track: Track, fileName: String): ByteArray?
    /**
     * Borra del dispositivo, para siempre, los archivos de estas canciones (y su .lrc). Después quita
     * la carpeta del álbum si ya no le queda música (con su portada, .cue, .log…) y las carpetas
     * superiores que hayan quedado vacías. Nunca toca la carpeta raíz de la biblioteca.
     */
    suspend fun deleteTracks(root: String, tracks: List<Track>): DeleteResult
}

/** Resultado de borrar canciones del dispositivo (URIs borradas, archivos que no se pudieron borrar). */
data class DeleteResult(val deleted: List<String>, val failed: List<String>, val foldersRemoved: Int)

/** Reglas comunes del escaneo: qué es audio, cómo se agrupan los álbumes, cómo se leen números… */
object ScanRules {
    private val AUDIO_EXT = setOf(
        "mp3", "flac", "m4a", "m4b", "aac", "alac", "ogg", "oga", "opus", "wav", "aif", "aiff",
        "wma", "ape", "wv", "mka", "dsf", "mp4",
    )
    private val NOT_AUDIO = setOf("m3u", "m3u8", "pls", "cue", "lrc", "nfo", "log", "txt")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val leadingDigits = Regex("^\\s*(\\d+)")
    private val fourDigits = Regex("(\\d{4})")
    private val numericGenre = Regex("^\\(?\\d+\\)?$")

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

    fun leadingInt(s: String): Int = leadingDigits.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun parseYear(s: String): Int = fourDigits.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun cleanGenre(g: String): String {
        val t = g.trim()
        if (numericGenre.matches(t)) return ""
        return t
    }

    /** Elige la portada de una carpeta entre sus imágenes: cover/folder/front primero. */
    fun <T> pickFolderCover(list: List<Pair<String, T>>): T? {
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
}
