package dev.surco

import dev.surco.net.LrcLib
import dev.surco.net.MetadataSearch
import dev.surco.net.surcoHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contra las APIs reales (MusicBrainz, Deezer, LRCLIB) desde el simulador: comprueba que el formato
 * de sus respuestas sigue siendo el que esperamos. Si no hay red, no falla (sólo lo dice).
 */
class LiveNetworkTest {
    private val http = surcoHttpClient()

    @Test
    fun metadataFromMusicBrainzAndDeezer() = runBlocking {
        val results = MetadataSearch(http).search("Radiohead", "OK Computer")
        if (results.isEmpty()) { println("LIVE: sin red, se omite"); return@runBlocking }
        println("LIVE resultados: " + results.take(6).joinToString { "${it.source}:${it.title}/${it.trackCount}/${it.detail}" })
        val mb = results.firstOrNull { it.source == MetadataSearch.MB && it.title == "OK Computer" && it.trackCount == 12 }
        if (mb != null) {
            val d = MetadataSearch(http).details(mb)!!
            println("LIVE MB: ${d.year} ${d.genre} ${d.tracks.size} pistas, primera «${d.tracks.first().title}»")
            assertEquals(1997, d.year)
            assertEquals(12, d.tracks.size)
            assertEquals("Airbag", d.tracks.first().title)
        }
        val dz = results.firstOrNull { it.source == MetadataSearch.DZ }
        if (dz != null) {
            val d = MetadataSearch(http).details(dz)!!
            println("LIVE Deezer: ${d.year} ${d.genre} ${d.tracks.size} pistas")
            assertTrue(d.tracks.isNotEmpty())
        }
        assertTrue(mb != null || dz != null, "ningún resultado útil: $results")
    }

    @Test
    fun lyricsFromLrclib() = runBlocking {
        val r = LrcLib(http).find("Radiohead", "Airbag", "OK Computer", 284)
        println("LIVE LRCLIB: $r".take(300))
        if (r == LrcLib.Outcome.Failed) { println("LIVE: LRCLIB no responde, se omite"); return@runBlocking }
        assertTrue(r is LrcLib.Outcome.Found)
    }
}
