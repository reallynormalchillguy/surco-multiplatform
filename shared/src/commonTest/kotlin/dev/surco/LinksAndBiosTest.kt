package dev.surco

import dev.surco.net.BioCleaner
import dev.surco.net.LinkKind
import dev.surco.net.SpotifyPage
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class LinksAndBiosTest {

    @Test
    fun findsSpotifyLinksInSharedText() {
        assertEquals(
            "https://open.spotify.com/album/6dVIqQ8qmQ5GBnJ9shOYGE?si=abc",
            SpotifyPage.findLink("Escucha esto!! https://open.spotify.com/album/6dVIqQ8qmQ5GBnJ9shOYGE?si=abc."),
        )
        assertEquals("https://spotify.link/AbCdEf", SpotifyPage.findLink("https://spotify.link/AbCdEf"))
        assertEquals("https://open.spotify.com/track/2takcwOaAZWiXQijPHIx7B", SpotifyPage.findLink("spotify:track:2takcwOaAZWiXQijPHIx7B"))
        assertNull(SpotifyPage.findLink("radiohead ok computer"))
        assertEquals(LinkKind.TRACK, SpotifyPage.kindOf("https://open.spotify.com/intl-es/track/xyz"))
    }

    @Test
    fun readsTheLinkPreviewTags() {
        val html = """<html><head>
            <meta property="og:title" content="OK Computer"/>
            <meta content="Radiohead · Album · 1997 · 12 songs" property="og:description"/>
            <meta property="og:type" content="music.album"/>
            <meta property="og:image" content="https://i.scdn.co/image/abc"/>
            <meta name="music:musician_description" content="Radiohead"/>
            </head></html>"""
        val m = SpotifyPage.parse("https://open.spotify.com/album/x", html)!!
        assertEquals(LinkKind.ALBUM, m.kind)
        assertEquals("OK Computer", m.title)
        assertEquals("Radiohead", m.artist)
        assertEquals("https://i.scdn.co/image/abc", m.coverUrl)

        val song = SpotifyPage.parse(
            "https://open.spotify.com/track/y",
            """<meta property="og:title" content="Don&#x27;t Look Back in Anger"><meta property="og:description" content="Listen to Don't Look Back in Anger on Spotify. Song · Oasis · 1995"><meta property="og:type" content="music.song">""",
        )!!
        assertEquals(LinkKind.TRACK, song.kind)
        assertEquals("Don't Look Back in Anger", song.title)
        assertEquals("Oasis", song.artist)
    }

    @Test
    fun cleansSearchEngineStyleTitles() {
        val m = SpotifyPage.parse(
            "https://open.spotify.com/album/x",
            """<meta property="og:title" content="Listen to OK Computer - Album by Radiohead | Spotify"><meta property="og:type" content="music.album">""",
        )!!
        assertEquals("OK Computer", m.title)
        assertEquals("Radiohead", m.artist)
        assertEquals("Paranoid Android", SpotifyPage.cleanTitle("Paranoid Android - song and lyrics by Radiohead | Spotify"))
    }

    @Test
    fun spotifySearchLinksAreEncoded() {
        assertEquals("https://open.spotify.com/search/Sigur%20R%C3%B3s%20%C3%81g%C3%A6tis%20byrjun", SpotifyPage.searchUrl("Sigur Rós Ágætis byrjun"))
    }

    @Test
    fun cleansBiographies() {
        assertEquals(
            "Radiohead are an English rock band.",
            BioCleaner.fromLastFm("Radiohead are an English rock band. <a href=\"https://www.last.fm/music/Radiohead\">Read more on Last.fm</a>. User-contributed text is available under the Creative Commons By-SA License; additional terms may apply."),
        )
        assertEquals("Uno\n\nDos", BioCleaner.clean("\n\nUno\n\n\n\nDos\n\n"))
    }
}
