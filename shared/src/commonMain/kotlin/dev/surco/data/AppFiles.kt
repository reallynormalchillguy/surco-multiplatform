package dev.surco.data

/** Archivos pequeños privados de la app (cachés en JSON, etc.). */
interface AppFiles {
    fun readText(name: String): String?
    fun writeText(name: String, text: String)
}
