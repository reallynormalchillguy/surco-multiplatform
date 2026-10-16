package dev.surco.data

/**
 * Guarda las portadas y fotos de artistas en el almacenamiento interno de la app.
 * Cada versión de una portada tiene un nombre distinto para que la caché de imágenes no muestre la antigua.
 */
interface ArtStore {
    /** Normaliza cualquier imagen a JPEG de como mucho [max] px de lado. Devuelve null si no es una imagen. */
    fun toJpeg(bytes: ByteArray, max: Int = 1200): ByteArray?
    /** Devuelve la ruta del archivo guardado. */
    fun writeAlbumArt(albumKey: String, jpeg: ByteArray): String
    /** Cada foto nueva lleva otro nombre: así la caché de imágenes no sigue enseñando la anterior. */
    fun writeArtistImage(name: String, jpeg: ByteArray): String
    /** Borra una foto de artista antigua (sólo si es nuestra). */
    fun deleteArtistImage(path: String?)
    /** Borra una portada antigua (sólo si es nuestra). */
    fun delete(path: String?)
    fun exists(path: String?): Boolean
}
