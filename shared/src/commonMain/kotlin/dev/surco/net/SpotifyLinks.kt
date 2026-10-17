package dev.surco.net

import dev.surco.data.AlbumMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay

/** Enlace para compartir: exacto (la ficha del álbum en Spotify) o una búsqueda en Spotify. */
data class ShareLink(val url: String, val exact: Boolean)

/**
 * Enlaces de Spotify, en los dos sentidos, sin la API de Spotify:
 * - abrir: se lee la ficha pública del enlace (título, artista, portada);
 * - compartir: MusicBrainz guarda el enlace de Spotify de muchos discos; si no lo tiene, se comparte
 *   una búsqueda de Spotify, que en su app abre el disco como primer resultado.
 */
class SpotifyLinks(private val http: HttpClient) {

    suspend fun resolve(url: String): LinkedMusic? {
        get(url, browser = true)?.let { (body, finalUrl) -> SpotifyPage.parse(finalUrl, body) }?.let { return it }
        // Plan B: oEmbed (sólo título y portada).
        val o = get("https://open.spotify.com/oembed?url=${enc(url)}")?.first?.let { parseJson(it).obj() } ?: return null
        val title = o.str("title").takeIf { it.isNotBlank() } ?: return null
        return LinkedMusic(SpotifyPage.kindOf(url) ?: LinkKind.ALBUM, title, "", o.str("thumbnail_url").ifBlank { null }, url)
    }

    /** Enlace de Spotify de un álbum. */
    suspend fun albumLink(artist: String, album: String): ShareLink {
        exactAlbum(artist, album)?.let { return ShareLink(it, true) }
        return ShareLink(SpotifyPage.searchUrl("$artist $album"), false)
    }

    /** Enlace de Spotify de una canción: una búsqueda «título artista» (su primer resultado es la canción). */
    fun trackLink(artist: String, title: String): ShareLink = ShareLink(SpotifyPage.searchUrl("$title $artist"), false)

    private suspend fun exactAlbum(artist: String, album: String): String? {
        if (album.isBlank()) return null
        val q = buildString {
            append("release:\"").append(album.replace("\"", " ")).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(artist.replace("\"", " ")).append('"')
        }
        val arr = get("$MB/release/?query=${enc(q)}&fmt=json&limit=8")?.first?.let { parseJson(it).obj() }?.arr("releases") ?: return null
        val candidates = arr.objects().filter { AlbumMatcher.similarity(it.str("title"), album) >= 0.8f }.take(3)
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
        private const val UA = "Surco/1.3 ( https://github.com/reallynormalchillguy/surco )"
        /** La ficha pública sólo trae las etiquetas de vista previa si la pide algo parecido a un navegador. */
        private const val BROWSER_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1 Surco/1.3"
    }
}
