package dev.surco.tags

/** Cambios de etiquetas. Un campo null significa "no tocar". Un texto vacío borra el campo. */
data class TagEdits(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val genre: String? = null,
    /** Letra (texto o LRC sincronizado). */
    val lyrics: String? = null,
    /** JPEG para incrustar como portada; array vacío = quitar la portada. */
    val cover: ByteArray? = null,
)

class TagWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Lee y escribe etiquetas en los archivos de audio de la biblioteca. [uri] es el identificador de la pista. */
interface TagEditor {
    suspend fun write(uri: String, fileName: String, edits: TagEdits)
    suspend fun readLyrics(uri: String, fileName: String): String?
}
