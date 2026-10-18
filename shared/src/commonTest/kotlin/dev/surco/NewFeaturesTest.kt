package dev.surco

import dev.surco.data.AlbumCandidate
import dev.surco.data.AlbumInfo
import dev.surco.data.AlbumInfoChoice
import dev.surco.data.AlbumMatcher
import dev.surco.data.RemoteTrack
import dev.surco.data.Track
import dev.surco.data.UNKNOWN_ALBUM
import dev.surco.data.UNKNOWN_ARTIST
import dev.surco.ui.theme.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.random.Random

class NewFeaturesTest {

    private fun track(i: Int, file: String, title: String = file.substringBeforeLast('.'), number: Int = 0, ms: Long = 0, artist: String = UNKNOWN_ARTIST, album: String = UNKNOWN_ALBUM) = Track(
        uri = "u$i", folderId = "f", fileName = file, mime = "audio/flac", title = title, artist = artist, album = album,
        albumArtist = "", groupArtist = artist, albumKey = "k", trackNumber = number, discNumber = 1, year = 0, genre = "",
        durationMs = ms, bitrate = 0, sampleRate = 0, bitsPerSample = 0, size = 0, lastModified = 0, addedAt = 0, artPath = null,
    )

    private val ok = AlbumInfo(
        candidate = AlbumCandidate("MusicBrainz", "id", "OK Computer", "Radiohead", 1997, 3, null),
        title = "OK Computer", artist = "Radiohead", year = 1997, genre = "Alternative Rock",
        tracks = listOf(
            RemoteTrack(1, 1, "Airbag", "Radiohead", 284_000),
            RemoteTrack(1, 2, "Paranoid Android", "Radiohead", 383_000),
            RemoteTrack(1, 3, "Subterranean Homesick Alien", "Radiohead", 267_000),
        ),
        coverUrl = null,
    )

    @Test
    fun untaggedFilesAreMatchedByFileName() {
        // Archivos sin etiquetas y en otro orden: el nombre de archivo basta para emparejarlos.
        val local = listOf(
            track(1, "02 - Paranoid Android.flac", ms = 383_500),
            track(2, "01 Airbag.flac", ms = 284_200),
            track(3, "03. Subterranean Homesick Alien.flac"),
        )
        val m = AlbumMatcher.match(local, ok.tracks)
        assertEquals("Paranoid Android", m["u1"]?.title)
        assertEquals("Airbag", m["u2"]?.title)
        assertEquals("Subterranean Homesick Alien", m["u3"]?.title)

        val plan = AlbumMatcher.plan(local, ok, AlbumInfoChoice()).associateBy { it.track.uri }
        assertEquals("2", plan["u1"]?.trackNumber)
        assertEquals("OK Computer", plan["u1"]?.album)
        assertEquals("1997", plan["u1"]?.year)
        assertEquals("Alternative Rock", plan["u1"]?.genre)
        // Artista desconocido en un álbum de un solo artista: se rellena aunque no se pida «artistas de cada canción».
        assertEquals("Radiohead", plan["u1"]?.artist)
    }

    @Test
    fun remasterSuffixAndNumbersStillMatch() {
        val local = listOf(
            track(1, "a.flac", title = "Airbag (Remastered)", number = 1, ms = 284_000, artist = "Radiohead", album = "OK Computer"),
            track(2, "b.flac", title = "Paranoid Android - Remastered", number = 2, ms = 383_000, artist = "Radiohead", album = "OK Computer"),
        )
        val m = AlbumMatcher.match(local, ok.tracks)
        assertEquals("Airbag", m["u1"]?.title)
        assertEquals("Paranoid Android", m["u2"]?.title)
    }

    @Test
    fun onlyEmptyKeepsExistingValues() {
        val local = listOf(track(1, "01 Airbag.flac", title = "Mi título", number = 1, ms = 284_000, artist = "Radiohead", album = "Mi álbum"))
        val p = AlbumMatcher.plan(local, ok, AlbumInfoChoice(onlyEmpty = true)).single()
        assertNull(p.title)
        assertNull(p.album)
        assertEquals("1997", p.year)       // estaba vacío
        assertEquals("Alternative Rock", p.genre)
    }

    @Test
    fun unrelatedTracksAreNotMatched() {
        val local = listOf(track(1, "Bohemian Rhapsody.mp3", ms = 355_000), track(2, "Yesterday.mp3", ms = 125_000))
        assertTrue(AlbumMatcher.match(local, ok.tracks).isEmpty())
    }

    @Test
    fun paletteRoundTripsAndDefaultKeepsOriginalShades() {
        assertEquals(Palette.Default, Palette.decode(""))
        assertEquals(Palette.Default, Palette.decode(Palette.Default.encode()))
        val p = Palette.random(Random(7))
        assertEquals(p.encode(), Palette.decode(p.encode()).encode())
    }

    @Test
    fun randomPalettesAreAlwaysReadable() {
        val rng = Random(1234)
        repeat(500) {
            val p = Palette.random(rng)
            assertTrue(Palette.contrast(p.text, p.background) >= 7f, "texto ${p.encode()}")
            assertTrue(Palette.contrast(p.accent, p.background) >= 4.5f, "acento ${p.encode()}")
            assertTrue(Palette.contrast(p.onAccent, p.accent) >= 3f)
        }
    }

    @Test
    fun hsvRoundTrip() {
        val c = Palette.parseHex("1ED760")!!
        val (h, s, v) = Palette.toHsv(c)
        assertEquals("1ED760", Palette.hex(Palette.hsv(h, s, v)))
    }

    @Test
    fun onlyKnownLeftoversLetAnAlbumFolderBeDeleted() {
        listOf("cover.jpg", "Folder.PNG", "rip.log", "album.cue", "info.nfo", ".DS_Store", "._01.flac", "Thumbs.db").forEach {
            assertTrue(dev.surco.data.ScanRules.isAlbumLeftover(it), it)
        }
        listOf("video.mp4", "booklet.pdf", "extras.zip", "01.dff", ".01 Song.flac.icloud", "notes.docx").forEach {
            assertTrue(!dev.surco.data.ScanRules.isAlbumLeftover(it), it)
        }
    }

    // ---------------------------------------------------------------- Color de fondo de la portada

    private fun hsvOf(c: androidx.compose.ui.graphics.Color): Triple<Float, Float, Float> {
        val mx = maxOf(c.red, c.green, c.blue); val mn = minOf(c.red, c.green, c.blue)
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == c.red -> ((c.green - c.blue) / d).mod(6f) * 60f
            mx == c.green -> ((c.blue - c.red) / d + 2) * 60f
            else -> ((c.red - c.green) / d + 4) * 60f
        }
        return Triple(h, if (mx == 0f) 0f else d / mx, mx)
    }

    @Test
    fun coverColorTakesTheDominantHueNotTheLoudestOne() {
        // Portada casi negra con mucho azul oscuro y una manchita roja muy viva (como la del ejemplo).
        val px = ArrayList<Int>()
        repeat(500) { px += 0xFF050608.toInt() }
        repeat(300) { px += 0xFF1E3A5F.toInt() }
        repeat(25) { px += 0xFFE01010.toInt() }
        val c = dev.surco.ui.dominantColor(px)!!
        val (h, s, v) = hsvOf(c)
        assertTrue(h in 195f..235f, "tono azul, no rojo: $h")
        assertTrue(s in 0.3f..0.65f, "apagado: $s")
        assertTrue(v in 0.3f..0.45f, "oscuro para leer texto blanco: $v")
    }

    @Test
    fun greyCoverGivesNeutralBackground() {
        val px = ArrayList<Int>()
        repeat(400) { px += 0xFF808080.toInt() }
        repeat(400) { px += 0xFFF4F4F4.toInt() }
        repeat(200) { px += 0xFF000000.toInt() }
        val (_, s, v) = hsvOf(dev.surco.ui.dominantColor(px)!!)
        assertTrue(s <= 0.1f, "gris: $s")
        assertTrue(v in 0.25f..0.35f, "oscuro: $v")
    }

    @Test
    fun scrobbleTitlesAreCleanedForSearch() {
        assertEquals("Creep", dev.surco.net.cleanTrackTitle("Creep - Remastered 2009"))
        assertEquals("Song", dev.surco.net.cleanTrackTitle("Song (feat. Someone)"))
        assertEquals("Tema", dev.surco.net.cleanTrackTitle("Tema [Live]"))
        assertEquals("Hey Jude", dev.surco.net.cleanTrackTitle("Hey Jude"))
        assertEquals("Song", dev.surco.net.cleanTrackTitle("Song feat. X"))
    }
}
