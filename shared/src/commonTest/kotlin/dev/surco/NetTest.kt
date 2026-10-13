package dev.surco

import dev.surco.net.CoverSearch
import dev.surco.net.Deezer
import dev.surco.net.LastFm
import dev.surco.net.LastFmAccount
import dev.surco.net.LastFmException
import dev.surco.net.LrcLib
import dev.surco.net.ScrobbleItem
import dev.surco.util.Md5
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(route: (HttpRequestData) -> Pair<HttpStatusCode, String>) = HttpClient(MockEngine { req ->
        requests += req
        val (status, body) = route(req)
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    })

    private fun HttpRequestData.form(): Map<String, String> {
        val bytes = (body as OutgoingContent.ByteArrayContent).bytes()
        return bytes.decodeToString().parseUrlEncodedParameters().entries().associate { it.key to it.value.first() }
    }

    @Test
    fun accentFolding() {
        assertEquals("bjork homogenic", CoverSearch.norm("Björk – Homogénic"))
        assertEquals("sigur ros", CoverSearch.norm("Sigur Rós (Deluxe)"))
        assertEquals("ozdemir", CoverSearch.norm("Özdemİr"))
        assertEquals("beatles abbey road", CoverSearch.norm("The Beatles - Abbey Road [Remastered]"))
        assertEquals("simon and garfunkel", CoverSearch.norm("Simon & Garfunkel"))
    }

    @Test
    fun coverMatches() = runTest {
        val http = client { req ->
            when (req.url.host) {
                "api.deezer.com" -> HttpStatusCode.OK to """{"data":[{"title":"OK Computer","artist":{"name":"Radiohead"},"cover_xl":"https://x/xl.jpg","cover_medium":"https://x/m.jpg"},{"title":"Pablo Honey","artist":{"name":"Radiohead"},"cover_xl":"https://x/p.jpg"}]}"""
                "itunes.apple.com" -> HttpStatusCode.OK to """{"results":[{"collectionName":"OK Computer OKNOTOK 1997 2017","artistName":"Radiohead","artworkUrl100":"https://a/100x100bb.jpg"}]}"""
                else -> HttpStatusCode.ServiceUnavailable to "nope"
            }
        }
        val c = CoverSearch(http)
        val all = c.search("Radiohead", "OK Computer")
        assertEquals(3, all.size)
        assertEquals("https://a/1400x1400bb.jpg", all.first { it.source == "iTunes" }.fullUrl)
        val good = c.matches(all, "Radiohead", "OK Computer")
        assertEquals(listOf("https://x/xl.jpg", "https://a/1400x1400bb.jpg"), good.map { it.fullUrl })
        assertTrue(requests.any { it.url.encodedQuery.contains("Radiohead%20OK%20Computer") || it.url.encodedQuery.contains("Radiohead+OK+Computer") })
    }

    @Test
    fun deezerAlbumDetail() = runTest {
        val http = client {
            HttpStatusCode.OK to """{"id":12,"title":"Kid A","artist":{"id":399,"name":"Radiohead"},"cover_xl":"https://c/xl","release_date":"2000-10-02","record_type":"album","fans":1000,"label":"XL","duration":2998,"genres":{"data":[{"name":"Alternative"}]},"tracks":{"data":[{"title":"Everything in Its Right Place","duration":251,"artist":{"name":"Radiohead"}},{"title":"Kid A","duration":284}]}}"""
        }
        val d = Deezer(http).album(12)!!
        assertEquals("Kid A", d.album.title)
        assertEquals(2000, d.album.year)
        assertEquals(399L, d.artistId)
        assertEquals(listOf("Alternative"), d.genres)
        assertEquals("Radiohead", d.tracks[1].artist)
        assertEquals(251, d.tracks[0].durationSec)
        assertEquals("XL", d.label)
    }

    @Test
    fun deezerErrorsAreNull() = runTest {
        val http = client { HttpStatusCode.OK to """{"error":{"type":"DataException","message":"no data","code":800}}""" }
        assertNull(Deezer(http).artistInfo(1))
        assertEquals(emptyList(), Deezer(http).related(1))
    }

    @Test
    fun lrclibFallsBackToSearch() = runTest {
        val http = client { req ->
            if (req.url.encodedPath.endsWith("/get")) HttpStatusCode.NotFound to """{"code":404}"""
            else HttpStatusCode.OK to """[{"duration":300.0,"syncedLyrics":"[00:01.00]far"},{"duration":200.0,"syncedLyrics":null,"plainLyrics":"plain"},{"duration":201.0,"syncedLyrics":"[00:01.00]hola","plainLyrics":"hola","instrumental":false}]"""
        }
        val r = LrcLib(http).find("Artista", "Canción (feat. Otro)", "Disco", 200)!!
        assertEquals("[00:01.00]hola", r.synced)
        assertEquals("hola", r.plain)
        val search = requests.last()
        assertEquals("Canción", search.url.parameters["track_name"])
        assertEquals(3, requests.size) // get con álbum, get sin álbum, search
    }

    private class Account(override val apiKey: String = "KEY", override val secret: String = "SECRET", override var session: String = "SK") : LastFmAccount {
        var user = ""
        override fun saveSession(key: String, user: String) { session = key; this.user = user }
    }

    @Test
    fun lastFmSignatureAndScrobble() = runTest {
        val http = client {
            HttpStatusCode.OK to """{"scrobbles":{"scrobble":[{"ignoredMessage":{"code":"0","#text":""}},{"ignoredMessage":{"code":"3","#text":"Timestamp too old"}}],"@attr":{"accepted":1,"ignored":1}}}"""
        }
        val lf = LastFm(http, Account())
        val r = lf.scrobble(listOf(
            ScrobbleItem("Björk", "Jóga", "Homogenic", timestamp = 1_700_000_000, durationSec = 305),
            ScrobbleItem("Old", "Song", "Álbum desconocido", timestamp = 1_600_000_000),
        ))
        assertEquals(LastFm.ScrobbleResult(1, 1, 1), r)
        val form = requests.single().form()
        assertEquals("track.scrobble", form["method"])
        assertEquals("Björk", form["artist[0]"])
        assertEquals("1700000000", form["timestamp[0]"])
        assertEquals("305", form["duration[0]"])
        assertNull(form["album[1]"]) // «Álbum desconocido» no se envía
        val signed = form - "api_sig" - "format"
        assertEquals(LastFm.signature(signed, "SECRET"), form["api_sig"])
        // Firma calculada a mano: claves ordenadas, concatenadas, más el secreto.
        assertEquals(Md5.hex("api_keyKmethodtrack.updateNowPlayingsksSECRET"),
            LastFm.signature(mapOf("sk" to "s", "method" to "track.updateNowPlaying", "api_key" to "K"), "SECRET"))
    }

    @Test
    fun lastFmSingleScrobbleObjectAndErrors() = runTest {
        var reply = """{"scrobbles":{"scrobble":{"ignoredMessage":{"code":"0"}},"@attr":{"accepted":1,"ignored":0}}}"""
        val http = client { HttpStatusCode.OK to reply }
        val acc = Account()
        val lf = LastFm(http, acc)
        assertEquals(LastFm.ScrobbleResult(1, 0, 0), lf.scrobble(listOf(ScrobbleItem("A", "B", timestamp = 1))))
        reply = """{"error":9,"message":"Invalid session key"}"""
        val e = assertFailsWith<LastFmException> { lf.scrobble(listOf(ScrobbleItem("A", "B", timestamp = 1))) }
        assertEquals(LastFm.ERR_INVALID_SESSION, e.code)
        reply = """{"session":{"name":"pepe","key":"NEWKEY","subscriber":0}}"""
        lf.login("Pepe", "pw")
        assertEquals("NEWKEY", acc.session)
        assertEquals("pepe", acc.user)
        lf.logout()
        assertEquals("", acc.session)
        // Sin sesión no se envía nada.
        val before = requests.size
        lf.nowPlaying(ScrobbleItem("A", "B"))
        assertEquals(before, requests.size)
    }
}
