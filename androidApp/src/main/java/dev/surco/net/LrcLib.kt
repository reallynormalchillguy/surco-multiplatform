package dev.surco.net

import dev.surco.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Cliente de LRCLIB (https://lrclib.net): letras sincronizadas gratuitas, sin clave. */
class LrcLib(private val http: OkHttpClient) {

    data class Result(val synced: String?, val plain: String?, val instrumental: Boolean)

    suspend fun find(artist: String, title: String, album: String?, durationSec: Int): Result? = withContext(Dispatchers.IO) {
        // 1) Coincidencia exacta (la API tolera ±2 s de duración).
        exact(artist, title, album, durationSec)?.let { return@withContext it }
        if (album != null) exact(artist, title, null, durationSec)?.let { return@withContext it }
        // 2) Búsqueda aproximada con el título limpio («(feat. …)», «- Remastered»…).
        search(artist, cleanTitle(title), durationSec)
    }

    private fun exact(artist: String, title: String, album: String?, durationSec: Int): Result? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .apply { if (album != null) addQueryParameter("album_name", album) }
            .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        return runCatching { parse(JSONObject(body)) }.getOrNull()
    }

    private fun search(artist: String, title: String, durationSec: Int): Result? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = get(url.toString()) ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val candidates = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= 4 }
        val best = candidates.firstOrNull { !it.isNull("syncedLyrics") } ?: candidates.firstOrNull() ?: return null
        return parse(best)
    }

    private fun parse(o: JSONObject) = Result(
        synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() },
        plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() },
        instrumental = o.optBoolean("instrumental", false),
    )

    private fun get(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Surco/${BuildConfig.VERSION_NAME} (https://github.com/reallynormalchillguy/surco)")
            .build()
        return runCatching {
            http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
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
