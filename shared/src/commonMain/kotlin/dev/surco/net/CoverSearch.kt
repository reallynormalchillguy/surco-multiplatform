package dev.surco.net

import dev.surco.util.Text
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

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
class CoverSearch(private val http: HttpClient) {

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

    suspend fun download(url: String): ByteArray? = runCatching {
        val r = http.get(url)
        if (r.status.isSuccess()) r.readRawBytes() else null
    }.getOrNull()

    /** Foto de artista desde Deezer (sólo si el nombre coincide). */
    suspend fun artistPicture(name: String): String? = runCatching {
        val arr = getJson("https://api.deezer.com/search/artist?limit=5&q=${enc(name)}").arr("data")
            ?: return@runCatching null
        val target = norm(name)
        for (o in arr.objects()) {
            if (norm(o.str("name")) == target) {
                val url = o.str("picture_xl")
                // Deezer devuelve una silueta genérica cuando no tiene foto.
                if (url.isNotBlank() && !url.contains("/artist//")) return@runCatching url
            }
        }
        null
    }.getOrNull()

    private suspend fun deezer(q: String): List<CoverResult> {
        val arr = getJson("https://api.deezer.com/search/album?limit=12&q=${enc(q)}").arr("data") ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val full = o.str("cover_xl")
            if (full.isBlank()) null else CoverResult(
                source = "Deezer",
                title = o.str("title"),
                artist = o.obj("artist")?.str("name") ?: "",
                thumbUrl = o.str("cover_medium").ifBlank { full },
                fullUrl = full,
            )
        }
    }

    private suspend fun itunes(q: String): List<CoverResult> {
        val arr = getJson("https://itunes.apple.com/search?media=music&entity=album&limit=12&term=${enc(q)}").arr("results")
            ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val art = o.str("artworkUrl100")
            if (art.isBlank()) null else CoverResult(
                source = "iTunes",
                title = o.str("collectionName"),
                artist = o.str("artistName"),
                thumbUrl = art.replace("100x100bb", "300x300bb"),
                fullUrl = art.replace("100x100bb", "1400x1400bb"),
            )
        }
    }

    private suspend fun musicBrainz(artist: String, album: String): List<CoverResult> {
        if (album.isBlank()) return emptyList()
        val query = buildString {
            append("releasegroup:\"").append(album.replace("\"", "")).append("\"")
            if (artist.isNotBlank()) append(" AND artist:\"").append(artist.replace("\"", "")).append("\"")
        }
        val arr = getJson("https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=6&query=${enc(query)}")
            .arr("release-groups") ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val id = o.str("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val credit = o.arr("artist-credit")?.objects()?.firstOrNull()?.str("name") ?: ""
            CoverResult(
                source = "MusicBrainz",
                title = o.str("title"),
                artist = credit,
                thumbUrl = "https://coverartarchive.org/release-group/$id/front-250",
                fullUrl = "https://coverartarchive.org/release-group/$id/front-1200",
            )
        }
    }

    private suspend fun getJson(url: String): JsonObject {
        val r = http.get(url) { header("Accept", "application/json") }
        return parseJson(r.bodyAsText()).obj() ?: JsonObject(emptyMap())
    }

    companion object {
        private val brackets = Regex("[\\(\\[][^\\)\\]]*[\\)\\]]")
        private val nonAlnum = Regex("[^a-z0-9]+")
        private val leadingThe = Regex("^the ")

        fun norm(s: String): String =
            Text.foldAccents(s.lowercase())
                .replace(brackets, " ")
                .replace("&", " and ")
                .replace(nonAlnum, " ")
                .trim()
                .replace(leadingThe, "")
                .trim()
    }
}
