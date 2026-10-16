package dev.surco.net

import dev.surco.data.AlbumCandidate
import dev.surco.data.AlbumInfo
import dev.surco.data.AlbumMatcher
import dev.surco.data.RemoteTrack
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Datos de álbumes desde internet, por nombre de álbum y artista: MusicBrainz (la base de datos
 * libre de referencia, con ediciones, discos y año original) y Deezer (buen género y portada).
 * Ninguno necesita clave.
 */
class MetadataSearch(private val http: HttpClient) {

    /** Sólo para las capturas automáticas (CI, sin red): resultados y detalle fijos. */
    var debug: Pair<List<AlbumCandidate>, AlbumInfo>? = null

    suspend fun search(artist: String, album: String): List<AlbumCandidate> = debug?.first ?: coroutineScope {
        val mb = async { runCatching { musicBrainz(artist, album) }.getOrDefault(emptyList()) }
        val dz = async { runCatching { deezer(artist, album) }.getOrDefault(emptyList()) }
        val all = mb.await() + dz.await()
        // Los que más se parecen a lo buscado, primero; a igualdad, MusicBrainz (más completo).
        all.sortedByDescending {
            AlbumMatcher.similarity(it.title, album) * 2 + AlbumMatcher.similarity(it.artist, artist) +
                (if (it.source == MB) 0.01f else 0f)
        }
    }

    suspend fun details(c: AlbumCandidate): AlbumInfo? = debug?.second ?: runCatching {
        if (c.source == MB) musicBrainzDetails(c) else deezerDetails(c)
    }.getOrNull()

    // ------------------------------------------------------------------ MusicBrainz

    private suspend fun musicBrainz(artist: String, album: String): List<AlbumCandidate> {
        if (album.isBlank()) return emptyList()
        val q = buildString {
            append("release:\"").append(clean(album)).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(clean(artist)).append('"')
        }
        val j = getJson("$MB_BASE/release/?fmt=json&limit=15&query=${enc(q)}") ?: return emptyList()
        return j.arr("releases")?.objects().orEmpty().map { o ->
            val id = o.str("id")
            val formats = o.arr("media")?.objects().orEmpty().map { it.str("format") }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().map { (f, n) -> if (n > 1) "$n×$f" else f }
            val type = o.obj("release-group")?.str("primary-type").orEmpty()
            AlbumCandidate(
                source = MB,
                id = id,
                title = o.str("title"),
                artist = credit(o.arr("artist-credit")),
                year = o.str("date").take(4).toIntOrNull() ?: 0,
                trackCount = o.int("track-count"),
                thumbUrl = "https://coverartarchive.org/release/$id/front-250",
                detail = (formats + listOf(o.str("country"), type, o.str("disambiguation")))
                    .filter { it.isNotBlank() }.distinct().joinToString(" · "),
            )
        }
    }

    private suspend fun musicBrainzDetails(c: AlbumCandidate): AlbumInfo? {
        val o = getJson("$MB_BASE/release/${c.id}?fmt=json&inc=recordings+artist-credits+release-groups+genres") ?: return null
        val rg = o.obj("release-group")
        val rgId = rg?.str("id").orEmpty()
        val year = rg?.str("first-release-date")?.take(4)?.toIntOrNull()
            ?: o.str("date").take(4).toIntOrNull() ?: c.year
        val albumArtist = credit(o.arr("artist-credit")).ifBlank { c.artist }
        val tracks = ArrayList<RemoteTrack>()
        o.arr("media")?.objects().orEmpty().forEachIndexed { mi, m ->
            val disc = m.int("position", mi + 1)
            m.arr("tracks")?.objects().orEmpty().forEachIndexed { ti, t ->
                tracks += RemoteTrack(
                    disc = disc,
                    number = t.int("position", ti + 1),
                    title = t.str("title"),
                    artist = credit(t.arr("artist-credit")).ifBlank { albumArtist },
                    durationMs = t.long("length", 0L),
                )
            }
        }
        var genre = topGenre(o.arr("genres"))
        if (genre.isBlank() && rgId.isNotBlank()) {
            delay(1100) // MusicBrainz pide como mucho una consulta por segundo
            genre = topGenre(getJson("$MB_BASE/release-group/$rgId?fmt=json&inc=genres")?.arr("genres"))
        }
        return AlbumInfo(
            candidate = c,
            title = o.str("title").ifBlank { c.title },
            artist = albumArtist,
            year = year,
            genre = genre,
            tracks = tracks,
            coverUrl = "https://coverartarchive.org/release/${c.id}/front-1200",
            coverFallbackUrl = if (rgId.isNotBlank()) "https://coverartarchive.org/release-group/$rgId/front-1200" else null,
        )
    }

    /** «Artista feat. Otro» tal como lo escribe MusicBrainz (nombre + frase de unión). */
    private fun credit(arr: JsonArray?): String = buildString {
        arr?.objects()?.forEach { o ->
            append(o.str("name").ifBlank { o.obj("artist")?.str("name").orEmpty() })
            append(o.str("joinphrase"))
        }
    }.trim()

    /** El género más votado, con mayúsculas («alternative rock» → «Alternative Rock»). */
    private fun topGenre(arr: JsonArray?): String {
        val best = arr?.objects()?.maxByOrNull { it.int("count") } ?: return ""
        return best.str("name").split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }

    // ------------------------------------------------------------------ Deezer

    private suspend fun deezer(artist: String, album: String): List<AlbumCandidate> {
        if (album.isBlank()) return emptyList()
        val q = if (artist.isNotBlank()) "artist:\"${clean(artist)}\" album:\"${clean(album)}\"" else album
        var j = getJson("$DZ_BASE/search/album?limit=10&q=${enc(q)}")
        if ((j?.arr("data")?.size ?: 0) == 0 && artist.isNotBlank()) {
            j = getJson("$DZ_BASE/search/album?limit=10&q=${enc("$artist $album")}")
        }
        return j?.arr("data")?.objects().orEmpty().map { o ->
            AlbumCandidate(
                source = DZ,
                id = o.long("id").toString(),
                title = o.str("title"),
                artist = o.obj("artist")?.str("name").orEmpty(),
                year = 0,
                trackCount = o.int("nb_tracks"),
                thumbUrl = o.str("cover_medium").takeIf { it.isNotBlank() },
                detail = o.str("record_type").replaceFirstChar { it.uppercaseChar() },
            )
        }
    }

    private suspend fun deezerDetails(c: AlbumCandidate): AlbumInfo? {
        val o = getJson("$DZ_BASE/album/${c.id}") ?: return null
        if (o.containsKey("error")) return null
        val albumArtist = o.obj("artist")?.str("name").orEmpty().ifBlank { c.artist }
        val tracks = getJson("$DZ_BASE/album/${c.id}/tracks?limit=500")?.arr("data")?.objects().orEmpty()
            .mapIndexed { i, x ->
                RemoteTrack(
                    disc = x.int("disk_number", 1).coerceAtLeast(1),
                    number = x.int("track_position", i + 1),
                    title = x.str("title"),
                    artist = x.obj("artist")?.str("name").orEmpty().ifBlank { albumArtist },
                    durationMs = x.long("duration") * 1000,
                )
            }
        return AlbumInfo(
            candidate = c,
            title = o.str("title").ifBlank { c.title },
            artist = albumArtist,
            year = o.str("release_date").take(4).toIntOrNull() ?: 0,
            genre = o.obj("genres")?.arr("data")?.objects()?.firstOrNull()?.str("name").orEmpty(),
            tracks = tracks,
            coverUrl = o.str("cover_xl").takeIf { it.isNotBlank() },
        )
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun getJson(url: String): JsonObject? = runCatching {
        val r = http.get(url) {
            header("User-Agent", UA)
            header("Accept", "application/json")
        }
        if (r.status.isSuccess()) parseJson(r.bodyAsText()).obj() else null
    }.getOrNull()

    companion object {
        const val MB = "MusicBrainz"
        const val DZ = "Deezer"
        private const val MB_BASE = "https://musicbrainz.org/ws/2"
        private const val DZ_BASE = "https://api.deezer.com"
        /** MusicBrainz exige un User-Agent que identifique la app y cómo contactar. */
        private const val UA = "Surco/1.2 ( https://github.com/reallynormalchillguy/surco )"

        /** Quita lo que rompería la consulta (comillas y barras de la sintaxis Lucene). */
        private fun clean(s: String) = s.replace("\"", " ").replace("\\", " ").trim()
    }
}
