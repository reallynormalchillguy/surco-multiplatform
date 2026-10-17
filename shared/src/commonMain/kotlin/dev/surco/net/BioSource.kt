package dev.surco.net

import dev.surco.data.AlbumMatcher
import dev.surco.data.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject

/** Biografía de un artista tal como se encontró (texto, fuente y enlace para leer más). */
data class BioText(val text: String, val source: String, val url: String?, val lang: String)

/**
 * Biografías de artistas, de fuentes abiertas (nada lo escribe la app):
 * 1) Wikipedia, en el idioma de la app si existe (si no, en inglés). Para no confundir artistas con el mismo
 *    nombre, el artículo se localiza a través de MusicBrainz → Wikidata, no buscando el nombre a ciegas.
 * 2) Last.fm (si hay clave de la API), como alternativa.
 */
class BioSource(private val http: HttpClient, private val prefs: Prefs) {

    sealed interface Outcome {
        data class Found(val bio: BioText) : Outcome
        data object NotFound : Outcome
        data object Failed : Outcome
    }

    private class NetworkFailure : Exception()

    suspend fun fetch(name: String, lang: String): Outcome {
        var failed = false
        try {
            wikipedia(name, lang)?.let { return Outcome.Found(it) }
        } catch (_: NetworkFailure) {
            failed = true
        }
        try {
            lastFm(name, lang)?.let { return Outcome.Found(it) }
        } catch (_: NetworkFailure) {
            failed = true
        }
        return if (failed) Outcome.Failed else Outcome.NotFound
    }

    private suspend fun wikipedia(name: String, lang: String): BioText? {
        val artists = json("$MB/artist/?query=${enc("artist:\"${name.replace("\"", " ")}\"")}&fmt=json&limit=5")?.arr("artists") ?: return null
        val target = AlbumMatcher.norm(name)
        val best = artists.objects().firstOrNull {
            it.int("score") >= 90 && (AlbumMatcher.norm(it.str("name")) == target || AlbumMatcher.norm(it.str("sort-name")) == target)
        } ?: return null
        delay(1100) // MusicBrainz: una consulta por segundo
        val rels = json("$MB/artist/${best.str("id")}?inc=url-rels&fmt=json")?.arr("relations") ?: return null
        var wikidata: String? = null
        val wikis = HashMap<String, String>()
        for (r in rels.objects()) {
            val res = r.obj("url")?.str("resource").orEmpty()
            when (r.str("type")) {
                "wikidata" -> wikidata = res.substringAfterLast('/')
                "wikipedia" -> wikis[res.substringAfter("://").substringBefore('.')] = res.substringAfter("/wiki/").decodeURLPart()
            }
        }
        val wd = wikidata
        if (wd != null) {
            val links = json("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$wd&props=sitelinks&sitefilter=${lang}wiki%7Cenwiki&format=json")
                ?.obj("entities")?.obj(wd)?.obj("sitelinks")
            links?.obj("${lang}wiki")?.str("title")?.takeIf { it.isNotBlank() }?.let { wikis[lang] = it }
            links?.obj("enwiki")?.str("title")?.takeIf { it.isNotBlank() }?.let { wikis["en"] = it }
        }
        for (l in listOf(lang, "en").distinct()) {
            val title = wikis[l] ?: continue
            val pages = json(
                "https://$l.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&redirects=1" +
                    "&format=json&formatversion=2&titles=${enc(title)}"
            )?.obj("query")?.arr("pages")
            val text = BioCleaner.clean(pages?.objects()?.firstOrNull()?.str("extract").orEmpty())
            if (text.isNotBlank()) return BioText(text, "Wikipedia", "https://$l.wikipedia.org/wiki/" + title.replace(' ', '_'), l)
        }
        return null
    }

    private suspend fun lastFm(name: String, lang: String): BioText? {
        val key = prefs.current.lastfmKey.ifBlank { return null }
        for (l in listOf(lang, "en").distinct()) {
            val artist = json(
                "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=${enc(name)}&autocorrect=1&lang=$l&api_key=$key&format=json"
            )?.obj("artist") ?: continue
            val text = BioCleaner.fromLastFm(artist.obj("bio")?.str("content").orEmpty())
            if (text.isNotBlank()) return BioText(text, "Last.fm", artist.str("url").ifBlank { null }, l)
        }
        return null
    }

    /** JSON de una URL; null si no existe. Si falla la red lanza NetworkFailure (no se guarda como «sin biografía»). */
    private suspend fun json(url: String): JsonObject? {
        val r = try {
            http.get(url) { header("User-Agent", UA); header("Accept", "application/json") }
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            throw NetworkFailure()
        } catch (_: Exception) {
            throw NetworkFailure()
        }
        if (r.status.value == 404) return null
        if (r.status.value !in 200..299) throw NetworkFailure()
        return parseJson(r.bodyAsText()).obj()
    }

    companion object {
        private const val MB = "https://musicbrainz.org/ws/2"
        /** Wikimedia y MusicBrainz piden un User-Agent que identifique la app. */
        private const val UA = "Surco/1.3 (https://github.com/reallynormalchillguy/surco; music player)"
    }
}

/** Limpieza de los textos de biografía (puro, sin red). */
object BioCleaner {
    /** Quita líneas vacías repetidas y espacios sobrantes. */
    fun clean(text: String): String = text.lines().map { it.trim() }
        .fold(ArrayList<String>()) { acc, l -> if (!(l.isEmpty() && acc.lastOrNull()?.isEmpty() != false)) acc += l; acc }
        .joinToString("\n").trim()

    /** Last.fm añade un enlace «Read more on Last.fm» y la licencia: fuera, junto con cualquier etiqueta HTML. */
    fun fromLastFm(html: String): String {
        var t = html.substringBefore("<a href=\"https://www.last.fm")
        t = t.replace(Regex("<[^>]+>"), "")
        t = t.replace("User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.", "")
        return clean(t)
    }
}
