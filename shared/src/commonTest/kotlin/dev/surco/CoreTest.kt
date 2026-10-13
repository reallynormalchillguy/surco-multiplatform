package dev.surco

import dev.surco.data.Discovery
import dev.surco.data.MemoryStore
import dev.surco.data.Prefs
import dev.surco.data.ScanRules
import dev.surco.data.ScrobbleMode
import dev.surco.i18n.I18n
import dev.surco.i18n.Lang
import dev.surco.util.Charsets1252
import dev.surco.util.fmt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    @Test
    fun prefsRoundTrip() {
        val store = MemoryStore()
        val p = Prefs(store, "K", "S")
        assertEquals("K", p.current.lastfmKey)
        assertNull(p.current.treeUri)
        p.update { it.copy(treeUri = "content://x", language = "es", scrobbleMode = ScrobbleMode.EXTERNAL, libraryGrid = true) }
        assertEquals(Lang.ES, I18n.lang)
        val again = Prefs(store, "K", "S")
        assertEquals("content://x", again.current.treeUri)
        assertEquals(ScrobbleMode.EXTERNAL, again.current.scrobbleMode)
        assertTrue(again.current.libraryGrid)
        again.update { it.copy(treeUri = null, language = "en") }
        assertNull(Prefs(store).current.treeUri)
        assertEquals(Lang.EN, I18n.lang)
    }

    @Test
    fun scanRules() {
        assertTrue(ScanRules.isAudio("a.FLAC", ""))
        assertFalse(ScanRules.isAudio("a.lrc", "audio/x"))
        assertTrue(ScanRules.isAudio("a.xyz", "audio/flac"))
        assertEquals(12, ScanRules.leadingInt(" 12/14"))
        assertEquals(1999, ScanRules.parseYear("1999-01-01"))
        assertEquals("", ScanRules.cleanGenre("(17)"))
        assertEquals("aa:radiohead|kid a", ScanRules.albumKeyFor("Radiohead ", "Kid A", "f"))
        assertEquals("f:f1|x", ScanRules.albumKeyFor("", "X", "f1"))
        assertEquals("c", ScanRules.pickFolderCover(listOf("back.jpg" to "b", "Cover.JPG" to "c")))
        assertNull(ScanRules.pickFolderCover(listOf("a.jpg" to "a", "b.jpg" to "b")))
    }

    @Test
    fun textHelpers() {
        assertEquals("Ñandú – café", Charsets1252.decodeText("Ñandú – café".encodeToByteArray()))
        val latin = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte(), 0x20, 0x80.toByte()) // «Café €» en Windows-1252
        assertEquals("Café €", Charsets1252.decodeText(latin))
        assertEquals("hola", Charsets1252.decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hola".encodeToByteArray()))
        assertEquals("44.1", 44.1.fmt(1))
        assertEquals("3", 2.6.fmt(0))
        assertEquals("1.25", 1.25.fmt(2))
        assertEquals("0.0", 0.04.fmt(1))
    }

    @Test
    fun soulseekQuery() {
        assertEquals("Artist Album", Discovery.soulseekQuery("Artist", "Album (Deluxe Edition)"))
        assertEquals("Björk Homogénic", Discovery.soulseekQuery("Björk", "Homogénic [Remastered]"))
        assertEquals("Sigur Rós Ágætis byrjun", Discovery.soulseekQuery("Sigur Rós", "Ágætis byrjun!"))
    }
}
