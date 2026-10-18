package dev.surco.data

/**
 * La caché de la app (imágenes descargadas, respuestas de internet, archivos temporales): se puede
 * borrar entera sin perder nada tuyo (biblioteca, listas, ajustes, portadas guardadas…).
 */
interface CacheStore {
    /** Bytes que ocupa ahora. */
    suspend fun size(): Long

    /** La vacía por completo. */
    suspend fun clear()

    object None : CacheStore {
        override suspend fun size() = 0L
        override suspend fun clear() {}
    }
}
