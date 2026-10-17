package dev.surco.net

import dev.surco.data.LfmProfile
import dev.surco.data.Prefs
import dev.surco.data.Scrobble
import dev.surco.data.StatsPeriod
import dev.surco.data.TopItem
import dev.surco.i18n.S
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Lectura de las estadísticas públicas de un usuario de Last.fm (sólo hace falta la API key y su nombre):
 * perfil, tops, historial de escuchas y etiquetas de artistas.
 */
class LastFmStats(private val http: HttpClient, private val prefs: Prefs) {

    suspend fun profile(user: String): LfmProfile {
        val u = call("user.getinfo", "user" to user).obj("user") ?: throw LastFmException(-2, "user.getinfo")
        return LfmProfile(
            name = u.str("name").ifBlank { user },
            realName = u.str("realname"),
            imageUrl = image(u.arr("image")),
            playcount = u.str("playcount").toLongOrNull() ?: 0,
            artists = u.str("artist_count").toLongOrNull() ?: 0,
            albums = u.str("album_count").toLongOrNull() ?: 0,
            tracks = u.str("track_count").toLongOrNull() ?: 0,
            registeredAt = u.obj("registered")?.let { r -> r.str("unixtime").toLongOrNull() ?: r.long("#text") } ?: 0,
            country = u.str("country").takeIf { it != "None" }.orEmpty(),
            url = u.str("url"),
        )
    }

    suspend fun topArtists(user: String, period: StatsPeriod, limit: Int = 10): List<TopItem> =
        (call("user.gettopartists", "user" to user, "period" to period.api, "limit" to "$limit").obj("topartists")?.arr("artist") ?: JsonArray(emptyList()))
            .objects().map { TopItem(it.str("name"), it.str("name"), plays(it), null, it.str("url")) }

    suspend fun topAlbums(user: String, period: StatsPeriod, limit: Int = 10): List<TopItem> =
        (call("user.gettopalbums", "user" to user, "period" to period.api, "limit" to "$limit").obj("topalbums")?.arr("album") ?: JsonArray(emptyList()))
            .objects().map { TopItem(it.str("name"), artistName(it), plays(it), image(it.arr("image")), it.str("url")) }

    suspend fun topTracks(user: String, period: StatsPeriod, limit: Int = 10): List<TopItem> =
        (call("user.gettoptracks", "user" to user, "period" to period.api, "limit" to "$limit").obj("toptracks")?.arr("track") ?: JsonArray(emptyList()))
            .objects().map { TopItem(it.str("name"), artistName(it), plays(it), null, it.str("url")) }

    suspend fun lovedCount(user: String): Int = runCatching {
        call("user.getlovedtracks", "user" to user, "limit" to "1").obj("lovedtracks")?.obj("@attr")?.str("total")?.toIntOrNull() ?: 0
    }.getOrDefault(0)

    /** Una página del historial: (escuchas, total en el rango, páginas). */
    suspend fun recentPage(user: String, from: Long, page: Int): Triple<List<Scrobble>, Long, Int> {
        val params = mutableListOf("user" to user, "limit" to "200", "page" to "$page")
        if (from > 0) params += "from" to "$from"
        val rt = call("user.getrecenttracks", *params.toTypedArray()).obj("recenttracks") ?: throw LastFmException(-2, "recenttracks")
        val attr = rt.obj("@attr")
        val total = attr?.str("total")?.toLongOrNull() ?: 0
        val pages = attr?.str("totalPages")?.toIntOrNull() ?: 0
        val list = (rt.arr("track") ?: JsonArray(emptyList())).objects().mapNotNull { t ->
            // La que suena ahora no tiene fecha: no es una escucha terminada.
            val at = t.obj("date")?.str("uts")?.toLongOrNull() ?: return@mapNotNull null
            Scrobble(at, t.obj("artist")?.str("#text").orEmpty(), t.str("name"), t.obj("album")?.str("#text").orEmpty())
        }
        return Triple(list, total, pages)
    }

    /**
     * Historial desde [from] (0 = desde siempre), hasta [maxPages] páginas de 200, de 4 en 4 a la vez.
     * Devuelve (escuchas, total exacto en el rango, si están todas).
     */
    suspend fun recent(user: String, from: Long, maxPages: Int, onProgress: (Float) -> Unit = {}): Triple<List<Scrobble>, Long, Boolean> =
        coroutineScope {
            val (first, total, pages) = recentPage(user, from, 1)
            val want = pages.coerceAtMost(maxPages)
            val out = ArrayList(first)
            onProgress(1f / want.coerceAtLeast(1))
            var p = 2
            while (p <= want) {
                val batch = (p until minOf(p + 4, want + 1)).map { n -> async { runCatching { recentPage(user, from, n).first }.getOrDefault(emptyList()) } }
                batch.awaitAll().forEach { out += it }
                p += 4
                onProgress((p - 1).coerceAtMost(want).toFloat() / want)
            }
            Triple(out.distinctBy { it.at to it.title }, total, pages <= maxPages)
        }

    /** Etiquetas (géneros) de un artista con su peso 0–100. */
    suspend fun artistTags(artist: String): List<Pair<String, Int>> = runCatching {
        (call("artist.gettoptags", "artist" to artist, "autocorrect" to "1").obj("toptags")?.arr("tag") ?: JsonArray(emptyList()))
            .objects().take(10).map { it.str("name") to it.int("count") }
    }.getOrDefault(emptyList())

    private suspend fun call(method: String, vararg params: Pair<String, String>): JsonObject {
        val key = prefs.current.lastfmKey
        if (key.isBlank()) throw LastFmException(-1, S.missingLastfmKeys)
        val url = buildString {
            append(LastFm.ENDPOINT).append("?method=").append(method).append("&api_key=").append(enc(key)).append("&format=json")
            params.forEach { (k, v) -> append('&').append(k).append('=').append(enc(v)) }
        }
        val r = http.get(url) { header("User-Agent", USER_AGENT) }
        val text = r.bodyAsText()
        if (text.isBlank()) throw LastFmException(-2, S.emptyLastfmReply)
        val j = parseJson(text).obj() ?: throw LastFmException(-2, S.invalidLastfmReply(r.status.value))
        if (j.containsKey("error")) throw LastFmException(j.int("error"), j.str("message"))
        return j
    }

    private fun plays(o: JsonObject) = o.str("playcount").toLongOrNull() ?: 0
    private fun artistName(o: JsonObject) = o.obj("artist")?.let { it.str("name").ifBlank { it.str("#text") } }.orEmpty()

    /** La imagen más grande que no sea la estrella gris por defecto. */
    private fun image(a: JsonArray?): String? =
        a?.objects()?.map { it.str("#text") }?.lastOrNull { it.isNotBlank() && PLACEHOLDER !in it }

    companion object {
        private const val PLACEHOLDER = "2a96cbd8b46e442fc41c2b86b821562f"
    }
}
