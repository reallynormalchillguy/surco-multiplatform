package dev.surco.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Cliente de LRCLIB (https://lrclib.net): letras sincronizadas gratuitas, sin clave. */
class LrcLib(private val http: HttpClient) {

    data class Result(val synced: String?, val plain: String?, val instrumental: Boolean)

    /** Resultado de una búsqueda: encontrada, no existe en LRCLIB, o no se pudo preguntar (red, servidor…). */
    sealed interface Outcome {
        data class Found(val result: Result) : Outcome
        data object NotFound : Outcome
        data object Failed : Outcome
    }

    /** Respuesta de una petición: `null` si falló la red o el servidor (no confundir con «no existe»). */
    private class Reply(val code: Int, val body: String)

    suspend fun find(artist: String, title: String, album: String?, durationSec: Int): Outcome {
        var failed = false
        fun take(o: Outcome): Result? {
            if (o is Outcome.Failed) failed = true
            return (o as? Outcome.Found)?.result
        }
        // 1) Coincidencia exacta (la API tolera ±2 s de duración).
        take(exact(artist, title, album, durationSec))?.let { return Outcome.Found(it) }
        if (album != null) take(exact(artist, title, null, durationSec))?.let { return Outcome.Found(it) }
        // 2) Búsqueda aproximada con el título limpio («(feat. …)», «- Remastered»…).
        take(search(artist, cleanTitle(title), durationSec))?.let { return Outcome.Found(it) }
        return if (failed) Outcome.Failed else Outcome.NotFound
    }

    private suspend fun exact(artist: String, title: String, album: String?, durationSec: Int): Outcome {
        val r = get("$BASE/get") {
            parameter("artist_name", artist)
            parameter("track_name", title)
            if (album != null) parameter("album_name", album)
            if (durationSec > 0) parameter("duration", durationSec.toString())
        } ?: return Outcome.Failed
        if (r.code == 404) return Outcome.NotFound
        if (r.code !in 200..299) return Outcome.Failed
        return parseJson(r.body).obj()?.let { Outcome.Found(parse(it)) } ?: Outcome.Failed
    }

    private suspend fun search(artist: String, title: String, durationSec: Int): Outcome {
        val r = get("$BASE/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        } ?: return Outcome.Failed
        if (r.code == 404) return Outcome.NotFound
        if (r.code !in 200..299) return Outcome.Failed
        val arr = parseJson(r.body).arr() ?: return Outcome.Failed
        val candidates = arr.objects().filter { durationSec <= 0 || abs(it.double("duration") - durationSec) <= 4 }
        val best = candidates.firstOrNull { !it.isNull("syncedLyrics") } ?: candidates.firstOrNull()
            ?: return Outcome.NotFound
        return Outcome.Found(parse(best))
    }

    private fun parse(o: JsonObject) = Result(
        synced = o.str("syncedLyrics").takeIf { it.isNotBlank() },
        plain = o.str("plainLyrics").takeIf { it.isNotBlank() },
        instrumental = o.bool("instrumental"),
    )

    /** Cualquier fallo de red devuelve null; la cancelación se respeta (no se confunde con un fallo). */
    private suspend fun get(url: String, params: io.ktor.client.request.HttpRequestBuilder.() -> Unit): Reply? =
        try {
            val r = http.get(url, params)
            Reply(r.status.value, r.bodyAsText())
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Algunos tiempos de espera llegan como cancelación: sólo se propaga si de verdad nos cancelaron.
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
            null
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val BASE = "https://lrclib.net/api"

        fun cleanTitle(t: String): String = t
            .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|remaster(ed)?|\d{4} remaster|live|mono|stereo|bonus)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(\d{4}\s+)?remaster(ed)?.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { t }
    }
}
