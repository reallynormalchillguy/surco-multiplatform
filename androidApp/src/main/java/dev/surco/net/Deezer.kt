package dev.surco.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** API pública de Deezer (sin clave): artistas parecidos, discografías y fichas de álbum. */
class Deezer(private val http: OkHttpClient) {

    data class Artist(val id: Long, val name: String, val picture: String?, val fans: Int = 0, val albums: Int = 0)

    /** Canción popular de un artista. */
    data class TopTrack(val title: String, val artist: String, val album: String, val albumId: Long, val cover: String?, val durationSec: Int)

    data class Album(
        val id: Long,
        val title: String,
        val artist: String,
        val cover: String?,
        val year: Int,
        val type: String,
        val fans: Int,
    )

    data class Song(val title: String, val artist: String, val durationSec: Int)

    data class AlbumDetail(
        val album: Album,
        val genres: List<String>,
        val label: String?,
        val durationSec: Int,
        val tracks: List<Song>,
        val artistId: Long = 0,
    )

    suspend fun findArtist(name: String): Artist? = withContext(Dispatchers.IO) {
        val url = "$BASE/search/artist".toHttpUrl().newBuilder()
            .addQueryParameter("q", name).addQueryParameter("limit", "5").build()
        val data = get(url.toString())?.optJSONArray("data") ?: return@withContext null
        val list = (0 until data.length()).map { artist(data.getJSONObject(it)) }
        // Preferimos la coincidencia exacta del nombre; si no, el primer resultado (el más popular).
        list.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: list.firstOrNull()
    }

    /** Artistas que coinciden con una búsqueda, del más popular al menos. */
    suspend fun searchArtists(query: String, limit: Int = 8): List<Artist> = withContext(Dispatchers.IO) {
        val url = "$BASE/search/artist".toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("limit", limit.toString()).build()
        val data = get(url.toString())?.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).map { artist(data.getJSONObject(it)) }
    }

    /** Ficha de un artista (foto, seguidores, nº de álbumes). */
    suspend fun artistInfo(id: Long): Artist? = withContext(Dispatchers.IO) {
        get("$BASE/artist/$id")?.let { artist(it) }
    }

    /** Sus canciones más escuchadas. */
    suspend fun top(artistId: Long, limit: Int = 10): List<TopTrack> = withContext(Dispatchers.IO) {
        val data = get("$BASE/artist/$artistId/top?limit=$limit")?.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).map {
            val t = data.getJSONObject(it)
            val al = t.optJSONObject("album")
            TopTrack(
                title = t.optString("title"),
                artist = t.optJSONObject("artist")?.optString("name").orEmpty(),
                album = al?.optString("title").orEmpty(),
                albumId = al?.optLong("id") ?: 0L,
                cover = al?.optString("cover_big")?.takeIf { c -> c.isNotBlank() },
                durationSec = t.optInt("duration"),
            )
        }
    }

    suspend fun related(artistId: Long): List<Artist> = withContext(Dispatchers.IO) {
        val data = get("$BASE/artist/$artistId/related?limit=20")?.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).map { artist(data.getJSONObject(it)) }
    }

    suspend fun albums(artistId: Long, artistName: String): List<Album> = withContext(Dispatchers.IO) {
        val data = get("$BASE/artist/$artistId/albums?limit=50")?.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).map { album(data.getJSONObject(it), artistName) }
    }

    suspend fun album(id: Long): AlbumDetail? = withContext(Dispatchers.IO) {
        val o = get("$BASE/album/$id") ?: return@withContext null
        val artistName = o.optJSONObject("artist")?.optString("name").orEmpty()
        val genres = o.optJSONObject("genres")?.optJSONArray("data")?.let { g ->
            (0 until g.length()).map { g.getJSONObject(it).optString("name") }
        }.orEmpty()
        val tracks = o.optJSONObject("tracks")?.optJSONArray("data")?.let { t ->
            (0 until t.length()).map {
                val tr = t.getJSONObject(it)
                Song(tr.optString("title"), tr.optJSONObject("artist")?.optString("name") ?: artistName, tr.optInt("duration"))
            }
        }.orEmpty()
        AlbumDetail(
            album = album(o, artistName),
            genres = genres,
            label = o.optString("label").takeIf { it.isNotBlank() },
            durationSec = o.optInt("duration"),
            tracks = tracks,
            artistId = o.optJSONObject("artist")?.optLong("id") ?: 0L,
        )
    }

    private fun artist(o: JSONObject) = Artist(
        id = o.optLong("id"),
        name = o.optString("name"),
        picture = o.optString("picture_xl").takeIf { it.isNotBlank() } ?: o.optString("picture_big").takeIf { it.isNotBlank() },
        fans = o.optInt("nb_fan"),
        albums = o.optInt("nb_album"),
    )

    private fun album(o: JSONObject, artistName: String) = Album(
        id = o.optLong("id"),
        title = o.optString("title"),
        artist = o.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() } ?: artistName,
        cover = o.optString("cover_xl").takeIf { it.isNotBlank() } ?: o.optString("cover_big").takeIf { it.isNotBlank() },
        year = o.optString("release_date").take(4).toIntOrNull() ?: 0,
        type = o.optString("record_type"),
        fans = o.optInt("fans"),
    )

    private fun get(url: String): JSONObject? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val o = JSONObject(r.body?.string() ?: return@use null)
            if (o.has("error")) null else o
        }
    }.getOrNull()

    companion object {
        private const val BASE = "https://api.deezer.com"
    }
}
