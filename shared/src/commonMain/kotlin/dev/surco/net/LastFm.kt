package dev.surco.net

import dev.surco.data.UNKNOWN_ALBUM
import dev.surco.i18n.S
import dev.surco.util.Md5
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject

class LastFmException(val code: Int, message: String) : Exception(message)

/** Dónde guarda cada plataforma la clave de la API y la sesión de Last.fm. */
interface LastFmAccount {
    val apiKey: String
    val secret: String
    val session: String
    fun saveSession(key: String, user: String)

    val ready: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()
    val loggedIn: Boolean get() = ready && session.isNotBlank()
}

/** Una escucha para Last.fm. [timestamp] en segundos Unix (se ignora en «now playing»). */
data class ScrobbleItem(
    val artist: String,
    val track: String,
    val album: String = "",
    val albumArtist: String = "",
    val durationSec: Int = 0,
    val trackNumber: Int = 0,
    val timestamp: Long = 0,
)

/**
 * Cliente mínimo de la API 2.0 de Last.fm (el mismo protocolo que usan Pano Scrobbler y Web Scrobbler).
 * Necesita una API key + secret propios: https://www.last.fm/api/account/create
 */
class LastFm(private val http: HttpClient, private val account: LastFmAccount) {

    /** Inicia sesión con usuario y contraseña (auth.getMobileSession) y guarda la clave de sesión. */
    suspend fun login(username: String, password: String) {
        if (!account.ready) throw LastFmException(-1, S.missingLastfmKeys)
        val j = post(
            mapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to account.apiKey,
            ),
        )
        val session = j.obj("session") ?: throw LastFmException(-1, S.invalidLastfmReply(200))
        val key = session.str("key").ifBlank { throw LastFmException(-1, S.invalidLastfmReply(200)) }
        account.saveSession(key, session.str("name", username))
    }

    fun logout() = account.saveSession("", "")

    suspend fun nowPlaying(t: ScrobbleItem) {
        if (!account.loggedIn) return
        val p = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to t.artist,
            "track" to t.track,
            "api_key" to account.apiKey,
            "sk" to account.session,
        )
        if (t.album.isNotBlank() && t.album != UNKNOWN_ALBUM) p["album"] = t.album
        if (t.albumArtist.isNotBlank()) p["albumArtist"] = t.albumArtist
        if (t.durationSec > 0) p["duration"] = t.durationSec.toString()
        if (t.trackNumber > 0) p["trackNumber"] = t.trackNumber.toString()
        post(p)
    }

    /** Resultado de un envío: aceptados, ignorados y, de esos, cuántos por ser demasiado antiguos (>14 días). */
    data class ScrobbleResult(val accepted: Int, val ignored: Int, val tooOld: Int)

    /** Envía hasta 50 scrobbles de una vez, cada uno con su fecha y hora original. */
    suspend fun scrobble(list: List<ScrobbleItem>): ScrobbleResult {
        if (!account.loggedIn || list.isEmpty()) return ScrobbleResult(0, 0, 0)
        val batch = list.take(50)
        val p = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to account.apiKey,
            "sk" to account.session,
        )
        batch.forEachIndexed { i, sc ->
            p["artist[$i]"] = sc.artist
            p["track[$i]"] = sc.track
            p["timestamp[$i]"] = sc.timestamp.toString()
            if (sc.album.isNotBlank() && sc.album != UNKNOWN_ALBUM) p["album[$i]"] = sc.album
            if (sc.albumArtist.isNotBlank()) p["albumArtist[$i]"] = sc.albumArtist
            if (sc.durationSec > 0) p["duration[$i]"] = sc.durationSec.toString()
            if (sc.trackNumber > 0) p["trackNumber[$i]"] = sc.trackNumber.toString()
        }
        return parseScrobbleResult(post(p), batch.size)
    }

    /** Artistas similares (sólo necesita la API key). Para "Más como…". */
    suspend fun similarArtists(artist: String): List<String> {
        val key = account.apiKey
        if (key.isBlank()) return emptyList()
        val url = "$ENDPOINT?method=artist.getsimilar&autocorrect=1&limit=100&format=json" +
            "&artist=${enc(artist)}&api_key=${enc(key)}"
        return runCatching {
            val j = parseJson(http.get(url).bodyAsText()).obj() ?: return@runCatching emptyList()
            j.obj("similarartists")?.arr("artist")?.objects()?.map { it.str("name") }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun post(params: Map<String, String>): JsonObject {
        val sig = signature(params, account.secret)
        val r = http.submitForm(
            url = ENDPOINT,
            formParameters = parameters {
                params.forEach { (k, v) -> append(k, v) }
                append("api_sig", sig)
                append("format", "json")
            },
        )
        val text = r.bodyAsText()
        if (text.isBlank()) throw LastFmException(-2, S.emptyLastfmReply)
        val j = parseJson(text).obj() ?: throw LastFmException(-2, S.invalidLastfmReply(r.status.value))
        if (j.containsKey("error")) throw LastFmException(j.int("error"), j.str("message"))
        return j
    }

    companion object {
        const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        const val ERR_INVALID_SESSION = 9

        /** api_sig: md5 de «clave+valor» ordenados por clave, más el secreto. */
        fun signature(params: Map<String, String>, secret: String): String =
            Md5.hex(params.entries.sortedBy { it.key }.joinToString("") { it.key + it.value } + secret)

        /** «scrobble» es un objeto si sólo hay uno, o una lista si hay varios. */
        internal fun parseScrobbleResult(j: JsonObject, sent: Int): ScrobbleResult {
            val root = j.obj("scrobbles") ?: return ScrobbleResult(sent, 0, 0)
            val attr = root.obj("@attr")
            val items = root.arr("scrobble")?.objects() ?: listOfNotNull(root.obj("scrobble"))
            val tooOld = items.count { it.obj("ignoredMessage")?.str("code") == "3" }
            return ScrobbleResult(attr?.int("accepted", items.size) ?: items.size, attr?.int("ignored") ?: 0, tooOld)
        }
    }
}
