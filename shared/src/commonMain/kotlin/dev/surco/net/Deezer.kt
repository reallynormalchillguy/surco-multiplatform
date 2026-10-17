package dev.surco.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/** API pública de Deezer (sin clave): artistas parecidos, discografías y fichas de álbum. */
class Deezer(private val http: HttpClient) {

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

    suspend fun findArtist(name: String): Artist? {
        val list = searchArtists(name, 5)
        // Preferimos la coincidencia exacta del nombre; si no, el primer resultado (el más popular).
        return list.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: list.firstOrNull()
    }

    /** Álbum que mejor casa con «artista + título» (para abrir enlaces de otras plataformas). */
    suspend fun findAlbum(artist: String, title: String): Album? {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" album:\"$title\"" else title
        var data = get("$BASE/search/album?q=${enc(q)}&limit=10")?.arr("data")
        if (data == null || data.isEmpty()) data = get("$BASE/search/album?q=${enc("$artist $title".trim())}&limit=10")?.arr("data") ?: return null
        return data.objects().map { album(it, "") }
            .maxByOrNull { dev.surco.data.AlbumMatcher.similarity(it.title, title) * 2 + dev.surco.data.AlbumMatcher.similarity(it.artist, artist) }
    }

    /** Canción que mejor casa con «artista + título»: devuelve (id del álbum, título, álbum). */
    suspend fun findTrack(artist: String, title: String): Triple<Long, String, String>? {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" track:\"$title\"" else title
        val data = get("$BASE/search/track?q=${enc(q)}&limit=10")?.arr("data") ?: return null
        val best = data.objects().maxByOrNull {
            dev.surco.data.AlbumMatcher.similarity(it.str("title"), title) * 2 +
                dev.surco.data.AlbumMatcher.similarity(it.obj("artist")?.str("name").orEmpty(), artist)
        } ?: return null
        val album = best.obj("album") ?: return null
        return Triple(album.long("id"), best.str("title"), album.str("title"))
    }

    /** Artistas que coinciden con una búsqueda, del más popular al menos. */
    suspend fun searchArtists(query: String, limit: Int = 8): List<Artist> {
        val data = get("$BASE/search/artist?q=${enc(query)}&limit=$limit")?.arr("data") ?: return emptyList()
        return data.objects().map { artist(it) }
    }

    /** Ficha de un artista (foto, seguidores, nº de álbumes). */
    suspend fun artistInfo(id: Long): Artist? = get("$BASE/artist/$id")?.let { artist(it) }

    /** Sus canciones más escuchadas. */
    suspend fun top(artistId: Long, limit: Int = 10): List<TopTrack> {
        val data = get("$BASE/artist/$artistId/top?limit=$limit")?.arr("data") ?: return emptyList()
        return data.objects().map { t ->
            val al = t.obj("album")
            TopTrack(
                title = t.str("title"),
                artist = t.obj("artist")?.str("name").orEmpty(),
                album = al?.str("title").orEmpty(),
                albumId = al?.long("id") ?: 0L,
                cover = al?.str("cover_big")?.takeIf { c -> c.isNotBlank() },
                durationSec = t.int("duration"),
            )
        }
    }

    suspend fun related(artistId: Long): List<Artist> {
        val data = get("$BASE/artist/$artistId/related?limit=20")?.arr("data") ?: return emptyList()
        return data.objects().map { artist(it) }
    }

    suspend fun albums(artistId: Long, artistName: String): List<Album> {
        val data = get("$BASE/artist/$artistId/albums?limit=50")?.arr("data") ?: return emptyList()
        return data.objects().map { album(it, artistName) }
    }

    suspend fun album(id: Long): AlbumDetail? {
        val o = get("$BASE/album/$id") ?: return null
        val artistName = o.obj("artist")?.str("name").orEmpty()
        val genres = o.obj("genres")?.arr("data")?.objects()?.map { it.str("name") }.orEmpty()
        val tracks = o.obj("tracks")?.arr("data")?.objects()?.map { tr ->
            Song(tr.str("title"), tr.obj("artist")?.str("name") ?: artistName, tr.int("duration"))
        }.orEmpty()
        return AlbumDetail(
            album = album(o, artistName),
            genres = genres,
            label = o.str("label").takeIf { it.isNotBlank() },
            durationSec = o.int("duration"),
            tracks = tracks,
            artistId = o.obj("artist")?.long("id") ?: 0L,
        )
    }

    private fun artist(o: JsonObject) = Artist(
        id = o.long("id"),
        name = o.str("name"),
        picture = o.str("picture_xl").takeIf { it.isNotBlank() } ?: o.str("picture_big").takeIf { it.isNotBlank() },
        fans = o.int("nb_fan"),
        albums = o.int("nb_album"),
    )

    private fun album(o: JsonObject, artistName: String) = Album(
        id = o.long("id"),
        title = o.str("title"),
        artist = o.obj("artist")?.str("name")?.takeIf { it.isNotBlank() } ?: artistName,
        cover = o.str("cover_xl").takeIf { it.isNotBlank() } ?: o.str("cover_big").takeIf { it.isNotBlank() },
        year = o.str("release_date").take(4).toIntOrNull() ?: 0,
        type = o.str("record_type"),
        fans = o.int("fans"),
    )

    private suspend fun get(url: String): JsonObject? = runCatching {
        val r = http.get(url)
        if (!r.status.isSuccess()) return@runCatching null
        val o = parseJson(r.bodyAsText()).obj() ?: return@runCatching null
        if (o.containsKey("error")) null else o
    }.getOrNull()

    companion object {
        private const val BASE = "https://api.deezer.com"
    }
}
