package dev.surco.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

data class CoverResult(
    val source: String,
    val title: String,
    val artist: String,
    val thumbUrl: String,
    val fullUrl: String,
)

/**
 * Busca portadas en Deezer, iTunes y MusicBrainz/Cover Art Archive (ninguno necesita clave).
 */
class CoverSearch(private val http: OkHttpClient) {

    suspend fun search(artist: String, album: String): List<CoverResult> = coroutineScope {
        val q = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        listOf(
            async { runCatching { deezer(q) }.getOrDefault(emptyList()) },
            async { runCatching { itunes(q) }.getOrDefault(emptyList()) },
            async { runCatching { musicBrainz(artist, album) }.getOrDefault(emptyList()) },
        ).awaitAll().flatten()
    }

    /** Busca con texto libre (desde la pantalla de búsqueda manual). */
    suspend fun searchFree(query: String): List<CoverResult> = coroutineScope {
        listOf(
            async { runCatching { deezer(query) }.getOrDefault(emptyList()) },
            async { runCatching { itunes(query) }.getOrDefault(emptyList()) },
        ).awaitAll().flatten()
    }

    /** Resultados que casan razonablemente con el álbum, en orden de preferencia. */
    fun matches(results: List<CoverResult>, artist: String, album: String): List<CoverResult> {
        val na = norm(album)
        val nr = norm(artist)
        if (na.isBlank()) return emptyList()
        return results.filter { r ->
            val t = norm(r.title)
            val a = norm(r.artist)
            val albumOk = t == na || (t.length > 3 && na.length > 3 && (t.startsWith(na) || na.startsWith(t)))
            val artistOk = nr.isBlank() || a == nr || a.contains(nr) || nr.contains(a) ||
                a == "various artists" || a == "varios artistas"
            albumOk && artistOk
        }
    }

    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()
            }
        }.getOrNull()
    }

    /** Foto de artista desde Deezer (sólo si el nombre coincide). */
    suspend fun artistPicture(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val j = getJson("https://api.deezer.com/search/artist?limit=5&q=${enc(name)}")
            val arr = j.optJSONArray("data") ?: return@runCatching null
            val target = norm(name)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (norm(o.optString("name")) == target) {
                    val url = o.optString("picture_xl")
                    // Deezer devuelve una silueta genérica cuando no tiene foto.
                    if (url.isNotBlank() && !url.contains("/artist//")) return@runCatching url
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun deezer(q: String): List<CoverResult> = withContext(Dispatchers.IO) {
        val j = getJson("https://api.deezer.com/search/album?limit=12&q=${enc(q)}")
        val arr = j.optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val full = o.optString("cover_xl")
            if (full.isBlank()) null else CoverResult(
                source = "Deezer",
                title = o.optString("title"),
                artist = o.optJSONObject("artist")?.optString("name") ?: "",
                thumbUrl = o.optString("cover_medium").ifBlank { full },
                fullUrl = full,
            )
        }
    }

    private suspend fun itunes(q: String): List<CoverResult> = withContext(Dispatchers.IO) {
        val j = getJson("https://itunes.apple.com/search?media=music&entity=album&limit=12&term=${enc(q)}")
        val arr = j.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val art = o.optString("artworkUrl100")
            if (art.isBlank()) null else CoverResult(
                source = "iTunes",
                title = o.optString("collectionName"),
                artist = o.optString("artistName"),
                thumbUrl = art.replace("100x100bb", "300x300bb"),
                fullUrl = art.replace("100x100bb", "1400x1400bb"),
            )
        }
    }

    private suspend fun musicBrainz(artist: String, album: String): List<CoverResult> = withContext(Dispatchers.IO) {
        if (album.isBlank()) return@withContext emptyList()
        val query = buildString {
            append("releasegroup:\"").append(album.replace("\"", "")).append("\"")
            if (artist.isNotBlank()) append(" AND artist:\"").append(artist.replace("\"", "")).append("\"")
        }
        val j = getJson("https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=6&query=${enc(query)}")
        val arr = j.optJSONArray("release-groups") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val credit = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name") ?: ""
            CoverResult(
                source = "MusicBrainz",
                title = o.optString("title"),
                artist = credit,
                thumbUrl = "https://coverartarchive.org/release-group/$id/front-250",
                fullUrl = "https://coverartarchive.org/release-group/$id/front-1200",
            )
        }
    }

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: "{}"
            return JSONObject(body)
        }
    }

    companion object {
        const val UA = "Surco/1.0 (reproductor de musica Android)"
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ")
                .replace("&", " and ")
                .replace(Regex("[^a-z0-9]+"), " ")
                .replace(Regex("^the "), "")
                .trim()
    }
}
