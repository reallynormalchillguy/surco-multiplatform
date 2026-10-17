package dev.surco.net

import dev.surco.data.AlbumMatcher
import dev.surco.data.Prefs
import dev.surco.util.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Enlace para compartir: exacto (la ficha en Spotify) o una búsqueda en Spotify. */
data class ShareLink(val url: String, val exact: Boolean)

/**
 * Enlaces de Spotify, en los dos sentidos.
 *
 * Abrir: se lee la ficha pública del enlace (título, artista, portada), sin clave.
 *
 * Compartir (encontrar el enlace exacto), por orden:
 * 1. la API de Spotify, si el usuario puso su clave (exacto siempre; con el ISRC de Deezer para canciones);
 * 2. MusicBrainz (discos), la ficha del disco (canciones) y Wikidata, sin clave;
 * 3. si nada lo sabe, una búsqueda de Spotify.
 */
class SpotifyLinks(private val http: HttpClient, private val prefs: Prefs, private val deezer: Deezer) {

    suspend fun resolve(url: String): LinkedMusic? {
        get(url, browser = true)?.let { (body, finalUrl) -> SpotifyPage.parse(finalUrl, body) }?.let { return it }
        // Plan B: oEmbed (sólo título y portada).
        val o = get("https://open.spotify.com/oembed?url=${enc(url)}")?.first?.let { parseJson(it).obj() } ?: return null
        val title = o.str("title").takeIf { it.isNotBlank() } ?: return null
        return LinkedMusic(SpotifyPage.kindOf(url) ?: LinkKind.ALBUM, title, "", o.str("thumbnail_url").ifBlank { null }, url)
    }

    /** Enlace de Spotify de un álbum. */
    suspend fun albumLink(artist: String, album: String): ShareLink {
        (apiAlbum(artist, album) ?: exactAlbum(artist, album) ?: wikidata(album, artist, ALBUM_PROP, "album"))
            ?.let { return ShareLink(it, true) }
        return ShareLink(SpotifyPage.searchUrl("$artist $album"), false)
    }

    /**
     * Enlace de Spotify de una canción. Sin clave: se busca el enlace exacto de su álbum y, en la ficha
     * de ese álbum, la canción (por título o por número de pista).
     */
    suspend fun trackLink(artist: String, title: String, album: String = "", disc: Int = 0, number: Int = 0): ShareLink {
        apiTrack(artist, title)?.let { return ShareLink(it, true) }
        if (album.isNotBlank()) {
            val albumUrl = exactAlbum(artist, album) ?: wikidata(album, artist, ALBUM_PROP, "album")
            if (albumUrl != null) trackInAlbum(albumUrl, title, disc, number)?.let { return ShareLink(it, true) }
        }
        wikidata(title, artist, TRACK_PROP, "track")?.let { return ShareLink(it, true) }
        return ShareLink(SpotifyPage.searchUrl("$title $artist"), false)
    }

    /** Busca la canción en la ficha pública del álbum (primero la del reproductor incrustado, que trae los títulos). */
    private suspend fun trackInAlbum(albumUrl: String, title: String, disc: Int, number: Int): String? {
        val id = albumUrl.substringAfter("/album/").substringBefore('?').substringBefore('/')
        if (id.isBlank()) return null
        get("https://open.spotify.com/embed/album/$id", browser = true)?.first
            ?.let { SpotifyPage.pickTrack(SpotifyPage.albumTracks(it), title, disc, number) }?.let { return it }
        val page = get("https://open.spotify.com/album/$id", browser = true)?.first ?: return null
        return SpotifyPage.pickTrack(SpotifyPage.albumTracks(page), title, disc, number)
    }

    // ------------------------------------------------------------------ API de Spotify (clave propia, opcional)

    private var token: String? = null
    private var tokenUntil = 0L

    private suspend fun apiToken(): String? {
        val s = prefs.current
        if (!s.spotifyReady) return null
        token?.takeIf { nowMillis() < tokenUntil }?.let { return it }
        val basic = "${s.spotifyId.trim()}:${s.spotifySecret.trim()}".encodeBase64()
        return runCatching {
            val r = http.submitForm(
                url = "https://accounts.spotify.com/api/token",
                formParameters = parameters { append("grant_type", "client_credentials") },
            ) { header("Authorization", "Basic $basic") }
            if (!r.status.isSuccess()) return@runCatching null
            val o = parseJson(r.bodyAsText()).obj() ?: return@runCatching null
            token = o.str("access_token").ifBlank { null }
            tokenUntil = nowMillis() + (o.long("expires_in", 3600) - 60) * 1000
            token
        }.getOrNull()
    }

    private suspend fun apiSearch(q: String, type: String): JsonArray? {
        val t = apiToken() ?: return null
        return runCatching {
            val r = http.get("https://api.spotify.com/v1/search?q=${enc(q)}&type=$type&limit=10") { header("Authorization", "Bearer $t") }
            if (!r.status.isSuccess()) null else parseJson(r.bodyAsText()).obj()?.obj("${type}s")?.arr("items")
        }.getOrNull()
    }

    private fun bestItem(items: JsonArray?, name: String, artist: String): String? {
        if (items == null) return null
        return items.objects()
            .map { o ->
                val artists = o.arr("artists")?.objects().orEmpty().joinToString(", ") { it.str("name") }
                o to (AlbumMatcher.similarity(o.str("name"), name) * 2 + AlbumMatcher.similarity(artists, artist))
            }
            .filter { it.second >= 2.2f }
            .maxByOrNull { it.second }
            ?.first?.obj("external_urls")?.str("spotify")?.ifBlank { null }
    }

    private suspend fun apiTrack(artist: String, title: String): String? {
        if (!prefs.current.spotifyReady) return null
        // Con el ISRC (de Deezer) se encuentra la grabación exacta aunque el título varíe.
        val isrc = runCatching { deezer.isrcOf(artist, title) }.getOrNull()
        if (isrc != null) {
            apiSearch("isrc:$isrc", "track")?.objects()?.firstOrNull()?.obj("external_urls")?.str("spotify")
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return bestItem(apiSearch("track:\"$title\" artist:\"$artist\"", "track"), title, artist)
            ?: bestItem(apiSearch("$title $artist", "track"), title, artist)
    }

    private suspend fun apiAlbum(artist: String, album: String): String? {
        if (!prefs.current.spotifyReady) return null
        return bestItem(apiSearch("album:\"$album\" artist:\"$artist\"", "album"), album, artist)
            ?: bestItem(apiSearch("$album $artist", "album"), album, artist)
    }

    // ------------------------------------------------------------------ Wikidata (discos y canciones conocidas)

    /**
     * Wikidata guarda el identificador de Spotify de muchos discos (P2205) y canciones (P2207).
     * Se busca por título y se comprueba que el intérprete (P175) sea el artista.
     */
    private suspend fun wikidata(title: String, artist: String, prop: String, kind: String): String? = runCatching {
        val search = "$WD?action=wbsearchentities&format=json&type=item&limit=10&language=en&search=${enc(title)}"
        val ids = parseJson(get(search)?.first ?: return null).obj()?.arr("search")?.objects().orEmpty()
            .filter { AlbumMatcher.similarity(it.str("label"), title) >= 0.85f }
            .map { it.str("id") }.take(8)
        if (ids.isEmpty()) return null
        val entities = parseJson(get("$WD?action=wbgetentities&format=json&props=claims&ids=${ids.joinToString("|")}")?.first ?: return null)
            .obj()?.obj("entities") ?: return null
        val withSpotify = ids.mapNotNull { id ->
            val claims = entities.obj(id)?.obj("claims") ?: return@mapNotNull null
            val sid = claims.arr(prop)?.objects()?.firstOrNull()?.obj("mainsnak")?.obj("datavalue")?.str("value")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val performers = claims.arr("P175")?.objects().orEmpty()
                .mapNotNull { it.obj("mainsnak")?.obj("datavalue")?.obj("value")?.str("id")?.ifBlank { null } }
            Triple(id, sid, performers)
        }
        if (withSpotify.isEmpty()) return null
        val performerIds = withSpotify.flatMap { it.third }.distinct().take(40)
        val labels: JsonObject = if (performerIds.isEmpty()) JsonObject(emptyMap()) else
            parseJson(get("$WD?action=wbgetentities&format=json&props=labels&languages=en&ids=${performerIds.joinToString("|")}")?.first ?: "{}")
                .obj()?.obj("entities") ?: JsonObject(emptyMap())
        val hit = withSpotify.firstOrNull { (_, _, perf) ->
            artist.isBlank() || perf.any { pid ->
                val l = labels.obj(pid)?.obj("labels")?.obj("en")?.str("value").orEmpty()
                AlbumMatcher.similarity(l, artist) >= 0.8f
            }
        } ?: return null
        "https://open.spotify.com/$kind/${hit.second}"
    }.getOrNull()

    private suspend fun exactAlbum(artist: String, album: String): String? {
        if (album.isBlank()) return null
        val q = buildString {
            append("release:\"").append(album.replace("\"", " ")).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(artist.replace("\"", " ")).append('"')
        }
        val arr = get("$MB/release/?query=${enc(q)}&fmt=json&limit=25")?.first?.let { parseJson(it).obj() }?.arr("releases") ?: return null
        // Las ediciones digitales son las que llevan enlaces a Spotify: primero ésas.
        val candidates = arr.objects().filter { AlbumMatcher.similarity(it.str("title"), album) >= 0.8f }
            .sortedByDescending { r -> r.arr("media")?.objects().orEmpty().any { it.str("format") == "Digital Media" } }
            .take(4)
        for (r in candidates) {
            delay(1100) // MusicBrainz: una consulta por segundo
            val rels = get("$MB/release/${r.str("id")}?inc=url-rels&fmt=json")?.first?.let { parseJson(it).obj() }?.arr("relations") ?: continue
            for (rel in rels.objects()) {
                val res = rel.obj("url")?.str("resource").orEmpty()
                if ("open.spotify.com/album/" in res) return res
            }
        }
        return null
    }

    /** Devuelve (cuerpo, URL final tras redirecciones), o null si falla. */
    private suspend fun get(url: String, browser: Boolean = false): Pair<String, String>? = runCatching {
        val r = http.get(url) {
            header("User-Agent", if (browser) BROWSER_UA else UA)
            header("Accept-Language", "en")
        }
        if (r.status.isSuccess()) r.bodyAsText() to r.call.request.url.toString() else null
    }.getOrNull()

    companion object {
        private const val MB = "https://musicbrainz.org/ws/2"
        private const val WD = "https://www.wikidata.org/w/api.php"
        private const val ALBUM_PROP = "P2205"
        private const val TRACK_PROP = "P2207"
        private const val UA = "Surco/1.3 ( https://github.com/reallynormalchillguy/surco )"
        /** La ficha pública sólo trae las etiquetas de vista previa si la pide algo parecido a un navegador. */
        private const val BROWSER_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1 Surco/1.3"
    }
}
