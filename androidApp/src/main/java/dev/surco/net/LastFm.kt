package dev.surco.net

import dev.surco.i18n.S

import dev.surco.data.PendingScrobble
import dev.surco.data.Prefs
import dev.surco.data.Track
import dev.surco.data.UNKNOWN_ALBUM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class LastFmException(val code: Int, message: String) : Exception(message)

/**
 * Cliente mínimo de la API 2.0 de Last.fm (el mismo protocolo que usan Pano Scrobbler y Web Scrobbler).
 * Necesita una API key + secret propios: https://www.last.fm/api/account/create
 */
class LastFm(private val http: OkHttpClient, private val prefs: Prefs) {

    /** Inicia sesión con usuario y contraseña (auth.getMobileSession) y guarda la clave de sesión. */
    suspend fun login(username: String, password: String) {
        val s = prefs.current
        if (!s.lastfmReady) throw LastFmException(-1, S.missingLastfmKeys)
        val j = post(
            mapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to s.lastfmKey,
            ),
            s.lastfmSecret,
        )
        val session = j.getJSONObject("session")
        val key = session.getString("key")
        val name = session.optString("name", username)
        prefs.update { it.copy(lastfmSession = key, lastfmUser = name) }
    }

    fun logout() = prefs.update { it.copy(lastfmSession = "", lastfmUser = "") }

    suspend fun nowPlaying(t: Track) {
        val s = prefs.current
        if (!s.lastfmLoggedIn) return
        val p = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to t.artist,
            "track" to t.title,
            "api_key" to s.lastfmKey,
            "sk" to s.lastfmSession,
        )
        if (t.album.isNotBlank() && t.album != UNKNOWN_ALBUM) p["album"] = t.album
        if (t.albumArtist.isNotBlank()) p["albumArtist"] = t.albumArtist
        if (t.durationMs > 0) p["duration"] = (t.durationMs / 1000).toString()
        if (t.trackNumber > 0) p["trackNumber"] = t.trackNumber.toString()
        post(p, s.lastfmSecret)
    }

    /** Resultado de un envío: aceptados, ignorados y, de esos, cuántos por ser demasiado antiguos (>14 días). */
    data class ScrobbleResult(val accepted: Int, val ignored: Int, val tooOld: Int)

    /** Envía hasta 50 scrobbles de una vez. */
    suspend fun scrobble(list: List<PendingScrobble>): ScrobbleResult {
        val s = prefs.current
        if (!s.lastfmLoggedIn || list.isEmpty()) return ScrobbleResult(0, 0, 0)
        val p = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to s.lastfmKey,
            "sk" to s.lastfmSession,
        )
        list.take(50).forEachIndexed { i, sc ->
            p["artist[$i]"] = sc.artist
            p["track[$i]"] = sc.track
            p["timestamp[$i]"] = sc.timestamp.toString()
            if (sc.album.isNotBlank() && sc.album != UNKNOWN_ALBUM) p["album[$i]"] = sc.album
            if (sc.albumArtist.isNotBlank()) p["albumArtist[$i]"] = sc.albumArtist
            if (sc.durationSec > 0) p["duration[$i]"] = sc.durationSec.toString()
            if (sc.trackNumber > 0) p["trackNumber[$i]"] = sc.trackNumber.toString()
        }
        val j = post(p, s.lastfmSecret)
        return runCatching {
            val root = j.getJSONObject("scrobbles")
            val attr = root.optJSONObject("@attr")
            // «scrobble» es un objeto si sólo hay uno, o una lista si hay varios.
            val items = root.optJSONArray("scrobble")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                ?: listOfNotNull(root.optJSONObject("scrobble"))
            val tooOld = items.count { it.optJSONObject("ignoredMessage")?.optString("code") == "3" }
            ScrobbleResult(attr?.optInt("accepted") ?: items.size, attr?.optInt("ignored") ?: 0, tooOld)
        }.getOrDefault(ScrobbleResult(list.size, 0, 0))
    }

    /** Artistas similares (sólo necesita la API key). Para "Más como…". */
    suspend fun similarArtists(artist: String): List<String> = withContext(Dispatchers.IO) {
        val key = prefs.current.lastfmKey
        if (key.isBlank()) return@withContext emptyList()
        val url = "$ENDPOINT?method=artist.getsimilar&autocorrect=1&limit=100&format=json" +
            "&artist=${CoverSearch.enc(artist)}&api_key=$key"
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", CoverSearch.UA).build()).execute().use { r ->
                val j = JSONObject(r.body?.string() ?: "{}")
                val arr = j.optJSONObject("similarartists")?.optJSONArray("artist") ?: return@use emptyList<String>()
                (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun post(params: Map<String, String>, secret: String): JSONObject = withContext(Dispatchers.IO) {
        val sig = md5(params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret)
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", sig)
            add("format", "json")
        }.build()
        val req = Request.Builder().url(ENDPOINT).post(body).header("User-Agent", CoverSearch.UA).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: throw IOException(S.emptyLastfmReply)
            val j = runCatching { JSONObject(text) }.getOrElse { throw IOException(S.invalidLastfmReply(r.code)) }
            if (j.has("error")) throw LastFmException(j.optInt("error"), j.optString("message"))
            j
        }
    }

    companion object {
        const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        const val ERR_INVALID_SESSION = 9

        fun md5(s: String): String =
            MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
