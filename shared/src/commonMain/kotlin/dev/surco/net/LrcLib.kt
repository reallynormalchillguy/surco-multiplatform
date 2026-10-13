package dev.surco.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/** Cliente de LRCLIB (https://lrclib.net): letras sincronizadas gratuitas, sin clave. */
class LrcLib(private val http: HttpClient) {

    data class Result(val synced: String?, val plain: String?, val instrumental: Boolean)

    suspend fun find(artist: String, title: String, album: String?, durationSec: Int): Result? {
        // 1) Coincidencia exacta (la API tolera ±2 s de duración).
        exact(artist, title, album, durationSec)?.let { return it }
        if (album != null) exact(artist, title, null, durationSec)?.let { return it }
        // 2) Búsqueda aproximada con el título limpio («(feat. …)», «- Remastered»…).
        return search(artist, cleanTitle(title), durationSec)
    }

    private suspend fun exact(artist: String, title: String, album: String?, durationSec: Int): Result? {
        val body = get("$BASE/get") {
            parameter("artist_name", artist)
            parameter("track_name", title)
            if (album != null) parameter("album_name", album)
            if (durationSec > 0) parameter("duration", durationSec.toString())
        } ?: return null
        return parseJson(body).obj()?.let { parse(it) }
    }

    private suspend fun search(artist: String, title: String, durationSec: Int): Result? {
        val body = get("$BASE/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        } ?: return null
        val candidates = parseJson(body).arr()?.objects().orEmpty()
            .filter { durationSec <= 0 || abs(it.double("duration") - durationSec) <= 4 }
        val best = candidates.firstOrNull { !it.isNull("syncedLyrics") } ?: candidates.firstOrNull() ?: return null
        return parse(best)
    }

    private fun parse(o: JsonObject) = Result(
        synced = o.str("syncedLyrics").takeIf { it.isNotBlank() },
        plain = o.str("plainLyrics").takeIf { it.isNotBlank() },
        instrumental = o.bool("instrumental"),
    )

    private suspend fun get(url: String, params: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String? =
        runCatching {
            val r = http.get(url, params)
            if (r.status.isSuccess()) r.bodyAsText() else null
        }.getOrNull()

    companion object {
        private const val BASE = "https://lrclib.net/api"

        fun cleanTitle(t: String): String = t
            .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|remaster(ed)?|\d{4} remaster|live|mono|stereo|bonus)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(\d{4}\s+)?remaster(ed)?.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { t }
    }
}
