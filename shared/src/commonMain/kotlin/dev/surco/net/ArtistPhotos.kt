package dev.surco.net

import dev.surco.data.AlbumMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/** Una foto de artista encontrada en internet: miniatura para la lista y la imagen grande para guardar. */
data class ArtistPhoto(val thumb: String, val full: String, val source: String)

/**
 * Fotos de un artista en varias fuentes públicas (sin claves), para elegir la que más te guste:
 * Deezer, TheAudioDB (fotos y fondos), la galería de Last.fm y Wikimedia Commons.
 */
class ArtistPhotos(private val http: HttpClient) {

    /** Cada fuente por separado: la pantalla las pide a la vez y va enseñando lo que llega. */
    val sources: List<suspend (String) -> List<ArtistPhoto>> = listOf(::deezer, ::audioDb, ::lastFm, ::wikimedia)

    suspend fun deezer(name: String): List<ArtistPhoto> {
        val arr = json("https://api.deezer.com/search/artist?limit=12&q=${enc(name)}")?.arr("data") ?: return emptyList()
        return arr.objects()
            .filter { AlbumMatcher.similarity(it.str("name"), name) >= 0.75f }
            .mapNotNull { o ->
                val full = o.str("picture_xl").takeIf { it.isNotBlank() && "/artist//" !in it } ?: return@mapNotNull null
                ArtistPhoto(o.str("picture_medium").ifBlank { full }, full, "Deezer")
            }
    }

    /** TheAudioDB guarda varias fotos por artista: retrato, fondos (fanart), banner ancho y recorte. */
    suspend fun audioDb(name: String): List<ArtistPhoto> {
        val arr = json("https://www.theaudiodb.com/api/v1/json/2/search.php?s=${enc(name)}")?.arr("artists") ?: return emptyList()
        val a = arr.objects().firstOrNull { AlbumMatcher.similarity(it.str("strArtist"), name) >= 0.8f } ?: return emptyList()
        return listOf("strArtistThumb", "strArtistFanart", "strArtistFanart2", "strArtistFanart3", "strArtistFanart4", "strArtistWideThumb", "strArtistCutout")
            .mapNotNull { k -> a.str(k).takeIf { it.startsWith("http") } }
            .map { ArtistPhoto("$it/preview", it, "TheAudioDB") }
    }

    /** La galería de fotos del artista en Last.fm (la página pública «+images»). */
    suspend fun lastFm(name: String): List<ArtistPhoto> {
        val path = enc(name.trim().replace(" ", "+")).replace("%2B", "+")
        val html = text("https://www.last.fm/music/$path/+images", browser = true) ?: return emptyList()
        // Sólo la galería (la página también enseña otras fotos, p. ej. de artistas parecidos).
        val start = html.indexOf("image-list")
        val gallery = if (start >= 0) html.substring(start, html.indexOf("</ul>", start).takeIf { it > start } ?: html.length) else html
        return LASTFM_IMG.findAll(gallery).map { it.groupValues[1] }.filter { it != LASTFM_PLACEHOLDER }.distinct().take(30)
            .map { h -> ArtistPhoto("https://lastfm.freetls.fastly.net/i/u/300x300/$h.jpg", "https://lastfm.freetls.fastly.net/i/u/770x0/$h.jpg", "Last.fm") }
            .toList()
    }

    /** Wikimedia Commons: la foto principal de Wikidata y las de su categoría. */
    suspend fun wikimedia(name: String): List<ArtistPhoto> {
        val search = json("https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&type=item&limit=8&language=en&search=${enc(name)}")
            ?.arr("search") ?: return emptyList()
        val musicWords = listOf("band", "singer", "musician", "rapper", "group", "duo", "dj", "composer", "producer", "songwriter", "music", "artist")
        val id = search.objects().firstOrNull { o ->
            AlbumMatcher.similarity(o.str("label"), name) >= 0.85f && musicWords.any { w -> o.str("description").lowercase().contains(w) }
        }?.str("id") ?: return emptyList()
        val claims = json("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=claims&ids=$id")
            ?.obj("entities")?.obj(id)?.obj("claims") ?: return emptyList()
        fun claim(p: String) = claims.arr(p)?.objects()?.firstOrNull()?.obj("mainsnak")?.obj("datavalue")?.str("value")?.ifBlank { null }
        val files = ArrayList<String>()
        claim("P18")?.let { files += "File:$it" }
        claim("P373")?.let { cat ->
            json("https://commons.wikimedia.org/w/api.php?action=query&format=json&list=categorymembers&cmtype=file&cmlimit=30&cmtitle=" + enc("Category:$cat"))
                ?.obj("query")?.arr("categorymembers")?.objects()?.forEach { files += it.str("title") }
        }
        val photos = files.distinct().filter { f -> f.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } }.take(20)
        if (photos.isEmpty()) return emptyList()
        val pages = json(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=" + enc("url|size") + "&iiurlwidth=400&titles=" +
                enc(photos.joinToString("|")),
        )?.obj("query")?.obj("pages") ?: return emptyList()
        return pages.values.mapNotNull { v ->
            val info = (v as? JsonObject)?.arr("imageinfo")?.objects()?.firstOrNull() ?: return@mapNotNull null
            val full = info.str("url").ifBlank { null } ?: return@mapNotNull null
            // La grande, limitada a 1200 px (las originales de Commons pueden pesar decenas de MB).
            val big = if (info.int("width") > 1600) info.str("thumburl").replace(Regex("/\\d+px-"), "/1200px-").ifBlank { full } else full
            ArtistPhoto(info.str("thumburl").ifBlank { full }, big, "Wikimedia")
        }
    }

    private suspend fun json(url: String): JsonObject? = text(url)?.let { parseJson(it).obj() }

    private suspend fun text(url: String, browser: Boolean = false): String? = runCatching {
        val r = http.get(url) {
            header("User-Agent", if (browser) BROWSER_UA else USER_AGENT)
            header("Accept-Language", "en")
        }
        if (r.status.isSuccess()) r.bodyAsText() else null
    }.getOrNull()

    companion object {
        private val LASTFM_IMG = Regex("""lastfm\.freetls\.fastly\.net/i/u/[a-z0-9]+/([0-9a-f]{32})""")
        private const val LASTFM_PLACEHOLDER = "2a96cbd8b46e442fc41c2b86b821562f"
        private const val BROWSER_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
    }
}
