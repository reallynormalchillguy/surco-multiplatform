package dev.surco

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.surco.data.AlbumCandidate
import dev.surco.data.AlbumInfo
import dev.surco.data.ArtistImage
import dev.surco.data.RemoteTrack
import dev.surco.data.DiscoverAlbum
import dev.surco.data.DiscoverSection
import dev.surco.data.Liked
import dev.surco.i18n.S
import dev.surco.net.Deezer
import dev.surco.ui.OnlineArtist
import dev.surco.ui.discoverDetailCache
import dev.surco.ui.onlineArtistCache
import dev.surco.ui.onlineSearchCache
import dev.surco.data.LyricsEntry
import dev.surco.data.PlayEvent
import dev.surco.data.Playlist
import dev.surco.data.PlaylistEntry
import dev.surco.data.Scanner
import dev.surco.data.Track
import dev.surco.playback.PlayerUiState
import dev.surco.slsk.SlskFile
import dev.surco.slsk.SlskState
import dev.surco.slsk.Transfer
import dev.surco.slsk.TransferState
import dev.surco.slsk.UserResult
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Genera capturas de las pantallas principales con una biblioteca ficticia.
 * Se ejecuta en CI: las imágenes se publican en la rama `ci` para revisar la interfaz sin un móvil.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SQLite «legacy»: la base de datos (Room, WorkManager) se abre en hilos de fondo y, en modo nativo,
// carga la misma biblioteca nativa que los gráficos a la vez que el hilo principal → el JVM abortaba.
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Carga los gráficos nativos de Robolectric en el hilo principal antes que nada: si el primero en
     * usarlos es un hilo de fondo (Coil decodificando una portada), el JVM aborta con
     * «Class not found: java/nio/DoubleBuffer».
     */
    @org.junit.Before
    fun preloadNativeGraphics() {
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).recycle()
        android.graphics.BitmapFactory.decodeByteArray(ByteArray(0), 0, 0)
    }

    private val app get() = ApplicationProvider.getApplicationContext<SurcoApp>()

    private val palettes = listOf(
        0xFF3A1C71 to 0xFFD76D77, 0xFF0F2027 to 0xFF2C5364, 0xFFB24592 to 0xFFF15F79,
        0xFF134E5E to 0xFF71B280, 0xFFE65C00 to 0xFFF9D423, 0xFF1D2B64 to 0xFFF8CDDA,
        0xFF42275A to 0xFF734B6D, 0xFF000000 to 0xFF434343, 0xFF2193B0 to 0xFF6DD5ED,
        0xFF833AB4 to 0xFFFCB045,
    )

    private fun fakeArt(name: String, i: Int): String {
        val dir = File(app.filesDir, "art").apply { mkdirs() }
        val f = File(dir, "$name.jpg")
        val bmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (a, b) = palettes[i % palettes.size]
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 500f, 500f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, 500f, 500f, p)
        val r = Random(i)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        repeat(3) { c.drawCircle(r.nextFloat() * 500, r.nextFloat() * 500, 40f + r.nextFloat() * 120, dot) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f.absolutePath
    }

    private val albums = listOf(
        Triple("Marea Baja", "Lumen Drift", "Electrónica"),
        Triple("Hojas de Cobre", "Los Faros", "Indie"),
        Triple("Night Transit", "Velvet Arcade", "Synthpop"),
        Triple("Arena y Sal", "Carmen Duna", "Pop"),
        Triple("Grain", "Halvorsen Trio", "Jazz"),
        Triple("Ciudad Satélite", "Los Faros", "Indie"),
        Triple("Polaroid Summer", "Mint Avenue", "Dream pop"),
        Triple("Lo Que Queda", "Bruno Cielo", "Cantautor"),
        Triple("Static Bloom", "Velvet Arcade", "Synthpop"),
        Triple("Raíz", "Carmen Duna", "Flamenco"),
    )

    private val songNames = listOf(
        "Primera luz", "Desvío", "Corriente", "Faro norte", "Vértigo suave", "Tinta", "Mediodía",
        "Ecos", "La última ola", "Kilómetro cero", "Neón", "Respira",
    )

    private fun seed() = runBlocking {
        val c = app.container
        val dao = c.db.dao()
        val now = System.currentTimeMillis()
        val tracks = ArrayList<Track>()
        albums.forEachIndexed { ai, (album, artist, genre) ->
            val art = fakeArt("album$ai", ai)
            val key = dev.surco.data.ScanRules.albumKeyFor(artist, album, "folder$ai")
            repeat(8) { ti ->
                tracks += Track(
                    uri = "content://fake/$ai/$ti", folderId = "folder$ai", fileName = "${ti + 1}. ${songNames[(ai + ti) % songNames.size]}.flac",
                    mime = "audio/flac", title = songNames[(ai + ti) % songNames.size], artist = artist, album = album,
                    albumArtist = artist, groupArtist = artist, albumKey = key, trackNumber = ti + 1, discNumber = 1,
                    year = 2015 + ai, genre = genre, durationMs = 180_000L + ti * 13_000L, bitrate = 1_000_000,
                    sampleRate = if (ai % 2 == 0) 96_000 else 44_100, bitsPerSample = if (ai % 2 == 0) 24 else 16,
                    size = 30_000_000L, lastModified = now, addedAt = now - ai * 86_400_000L, artPath = art,
                )
            }
        }
        dao.upsertTracks(tracks)
        tracks.groupBy { it.artist }.keys.forEachIndexed { i, a ->
            dao.upsertArtistImage(ArtistImage(a, fakeArt("artist$i", i + 4), now))
        }
        tracks.shuffled(Random(3)).take(40).forEachIndexed { i, t ->
            dao.insertPlay(PlayEvent(trackUri = t.uri, albumKey = t.albumKey, artist = t.groupArtist, playedAt = now - i * 3_600_000L))
        }
        tracks.take(14).forEach { dao.like(Liked(it.uri, now)) }
        val pl = dao.insertPlaylist(Playlist(name = "Para correr", createdAt = now, updatedAt = now))
        tracks.shuffled(Random(5)).take(12).forEachIndexed { i, t ->
            dao.insertEntry(PlaylistEntry(playlistId = pl, trackUri = t.uri, position = i, addedAt = now))
        }
        dao.insertPlaylist(Playlist(name = "Domingo lento", createdAt = now, updatedAt = now - 1000))
        c.prefs.update { it.copy(treeUri = "content://fake/tree", language = "en") }
        // Recomendaciones de ejemplo (sin red): álbumes ficticios «que no tienes».
        val recs = listOf("Aurora Static", "Paper Moons", "Kestrel", "Nadia Ferro", "The Lowlands").mapIndexed { i, artist ->
            DiscoverAlbum(1000L + i, listOf("Glass Hours", "Small Weather", "Northbound", "Luz de Agosto", "Fieldnotes")[i], artist, fakeArt("rec$i", i + 3), 2018 + i)
        }
        c.discovery.debugSet(listOf(DiscoverSection("Velvet Arcade", fakeArt("seed", 2), recs)))
        discoverDetailCache[1000L] = Deezer.AlbumDetail(
            album = Deezer.Album(1000L, "Glass Hours", "Aurora Static", fakeArt("rec0", 3), 2018, "album", 1000),
            genres = listOf("Synthpop", "Electronic"), label = "Satellite Records", durationSec = 2580,
            tracks = songNames.take(10).map { Deezer.Song(it, "Aurora Static", 200 + it.length * 7) },
        )
        c
    }

    private fun settle() {
        repeat(8) {
            Thread.sleep(300)
            compose.waitForIdle()
        }
    }

    private fun snap(name: String) {
        settle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    /** Captura la pantalla entera, con diálogos y hojas inferiores (que van en otra ventana). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun snapScreen(name: String) {
        settle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/$name.png")
    }

    private val fakeLrc = (0 until 40).joinToString("\n") { i ->
        val t = i * 6
        "[%02d:%02d.00] %s".format(t / 60, t % 60, listOf(
            "Las luces de la ciudad se apagan despacio", "y el tren de medianoche cruza el puente",
            "guardo tu voz en una cinta gastada", "que suena mejor cuando nadie la escucha",
            "", "Corriente, llévame donde no haya ruido", "corriente, que el mar sabe mi nombre",
        )[i % 7])
    }

    @Test
    fun screens() {
        val c = seed()
        compose.setContent { SurcoTheme { SurcoRoot(container = c, openPlayerSignal = 0) } }
        snap("01_inicio")

        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("discover_1000"))
        snap("01b_inicio_descubrir")
        compose.onNodeWithTag("discover_1000").performClick()
        snap("01c_album_recomendado")
        compose.onNodeWithTag("tab_home").performClick()

        compose.onNodeWithTag("tab_search").performClick()
        snap("02_buscar")

        // Artistas de internet en el buscador y su perfil (datos de ejemplo, sin red).
        val aurora = Deezer.Artist(7000L, "Aurora Static", fakeArt("aurora", 6), fans = 1_250_000, albums = 4)
        onlineSearchCache["aurora"] = listOf(aurora, Deezer.Artist(7002L, "Aurora Fields", fakeArt("af", 8), fans = 8_400))
        onlineArtistCache[7000L] = OnlineArtist(
            aurora,
            top = songNames.take(5).mapIndexed { i, n -> Deezer.TopTrack(n, "Aurora Static", listOf("Glass Hours", "Small Weather")[i % 2], 1000L, fakeArt("rec${i % 2}", 3 + i % 2), 190 + i * 17) },
            albums = listOf(
                Deezer.Album(1000L, "Glass Hours", "Aurora Static", fakeArt("rec0", 3), 2018, "album", 900),
                Deezer.Album(1001L, "Small Weather", "Aurora Static", fakeArt("rec1", 4), 2021, "album", 600),
                Deezer.Album(1002L, "Night Swim", "Aurora Static", fakeArt("rec2", 5), 2023, "ep", 300),
            ),
            related = listOf("Paper Moons", "Kestrel", "Nadia Ferro").mapIndexed { i, n -> Deezer.Artist(7100L + i, n, fakeArt("rel$i", i + 1)) },
        )
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("aurora")
        snap("02b_buscar_online")
        compose.onNodeWithTag("online_artist_7000").performClick()
        snap("02c_perfil_online")
        compose.onNodeWithTag("tab_search").performClick()
        compose.onNodeWithTag("tab_search").performClick()

        compose.onNodeWithTag("tab_library").performClick()
        snap("03_biblioteca")

        compose.onAllNodesWithText("Raíz")[0].performClick()
        snap("04_album")

        // Menú del álbum: cola, datos de internet y borrado con doble confirmación.
        compose.onNodeWithTag("album_more").performClick()
        snapScreen("04b_album_menu")
        compose.onNodeWithTag("album_delete").performClick()
        snapScreen("04c_borrar_aviso1")
        compose.onNodeWithTag("delete_step1").performClick()
        snapScreen("04d_borrar_aviso2")
        compose.onNodeWithText(S.keepAlbum).performClick()
        settle()

        val raiz = runBlocking { c.db.dao().allTracksOnce().filter { it.album == "Raíz" }.sortedBy { it.trackNumber } }
        val cand = AlbumCandidate("MusicBrainz", "mb1", "Raíz", "Carmen Duna", 2024, 8, fakeArt("mb1", 9), "CD · ES · Album")
        c.metadata.debug = listOf(
            cand,
            AlbumCandidate("Deezer", "dz1", "Raíz (Deluxe)", "Carmen Duna", 0, 11, fakeArt("dz1", 7), "Album"),
        ) to AlbumInfo(
            cand, "Raíz", "Carmen Duna", 2024, "Flamenco",
            raiz.mapIndexed { i, t -> RemoteTrack(1, i + 1, if (i % 3 == 0) t.title + " (Remasterizado)" else t.title, "Carmen Duna", t.durationMs) },
            coverUrl = null,
        )
        compose.onNodeWithTag("album_more").performClick()
        settle()
        compose.onNodeWithTag("album_fill_info").performClick()
        snap("04e_datos_internet")
        compose.onAllNodes(hasTestTag("candidate"))[0].performClick()
        snap("04f_datos_revision")
        compose.onNodeWithTag("tab_library").performClick()
        compose.onNodeWithTag("tab_library").performClick()
        settle()


        // Simula que suena una canción para ver el mini reproductor y el reproductor completo.
        val first = runBlocking { c.db.dao().allTracksOnce().first { it.album == "Night Transit" } }
        runBlocking { c.db.lyrics().put(LyricsEntry(first.uri, fakeLrc, null, "lrclib", System.currentTimeMillis())) }
        c.player.debugSetState(
            PlayerUiState(
                connected = true, mediaId = first.uri, isPlaying = true, position = 72_500, duration = first.durationMs,
                source = "álbum|${first.album}", queue = listOf(first.uri), queueIndex = 0,
            )
        )
        compose.onNodeWithTag("tab_home").performClick()
        snap("05_inicio_reproduciendo")

        c.sleepTimer.endOfTrack() // sin cuenta atrás: la prueba no espera a un reloj que no para
        compose.onNodeWithTag("mini_player").performClick()
        snap("06_reproductor")
        // Temporizador para dormir.
        compose.onNodeWithTag("sleep_timer").performClick()
        snapScreen("06b_temporizador")
        compose.onNodeWithText(S.turnOffTimer).performClick()
        settle()

        compose.onNodeWithTag("lyrics_card").performScrollTo()
        snap("07_reproductor_letra")

        compose.onNodeWithTag("lyrics_card").performClick()
        snap("08_letra_completa")

        // Cierra el reproductor y abre Soulseek con datos de ejemplo.
        c.player.debugSetState(PlayerUiState())
        settle()
        c.prefs.update { it.copy(slskUser = "surco_demo", slskPass = "x") }
        c.soulseek.client.debugSet(SlskState.Online("surco_demo", 2234), fakeResults(), fakeTransfers())
        compose.onNodeWithTag("tab_search").performClick()
        settle()
        compose.onNodeWithContentDescription(S.downloadWithSoulseek).performClick()
        snap("09_soulseek")

        compose.onNodeWithText(S.downloads, substring = true).performClick()
        snap("10_soulseek_descargas")

        compose.onNodeWithTag("tab_home").performClick()
        settle()
        compose.onAllNodesWithContentDescription(S.settings)[0].performClick()
        snap("11_ajustes")

        // Paleta de colores: generador aleatorio y edición manual.
        compose.onNodeWithTag("open_palette").performScrollTo().performClick()
        snap("11b_paleta")
        c.prefs.update { it.copy(palette = dev.surco.ui.theme.Palette.random(Random(42)).encode()) }
        compose.onNodeWithTag("swatch_background").performClick()
        snap("11c_paleta_sorteada")
        compose.onNodeWithTag("tab_home").performClick()
        snap("11d_inicio_con_paleta")
        c.prefs.update { it.copy(palette = "") }

        // Tema floral (fondo vino con flores).
        c.prefs.update { it.copy(theme = "floral") }
        compose.onNodeWithTag("tab_home").performClick()
        snap("11e_tema_floral")
        compose.onNodeWithTag("tab_library").performClick()
        snap("11f_tema_floral_biblioteca")
        compose.onAllNodesWithText("Raíz")[0].performClick()
        snap("11g_tema_floral_album")
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("tab_home").performClick()
        c.prefs.update { it.copy(theme = "") }

        // El mismo Inicio en español, para comprobar el cambio de idioma en caliente.
        c.prefs.update { it.copy(language = "es") }
        compose.onNodeWithTag("tab_home").performClick()
        snap("12_inicio_es")

        // Foto de artista (al final: la hoja inferior se queda abierta).
        c.prefs.update { it.copy(language = "en") }
        compose.onNodeWithTag("tab_library").performClick()
        settle()
        compose.onAllNodesWithText("Carmen Duna")[0].performClick()
        settle()
        compose.onNodeWithTag("artist_photo").performClick()
        snapScreen("13_foto_artista")
    }

    private fun fakeResults(): List<UserResult> {
        fun album(user: String, folder: String, ext: String, n: Int, rate: Int, depth: Int, bitrate: Int) =
            (1..n).map { i ->
                SlskFile(user, "@@music\\$folder\\%02d - %s.$ext".format(i, songNames[i % songNames.size]),
                    if (ext == "flac") 38_000_000L else 9_000_000L, bitrate, 200 + i * 7, rate, depth)
            } + SlskFile(user, "@@music\\$folder\\cover.jpg", 450_000L)
        return listOf(
            UserResult("vinylhead", album("vinylhead", "Velvet Arcade\\2019 - Night Transit [FLAC 24-96]", "flac", 11, 96_000, 24, 0), true, 2_400_000, 0),
            UserResult("discos_ana", album("discos_ana", "Velvet Arcade - Night Transit (2019)", "mp3", 11, 0, 0, 320), true, 900_000, 0),
            UserResult("kaito", album("kaito", "Synthpop\\Velvet Arcade\\Night Transit", "flac", 10, 44_100, 16, 0), false, 600_000, 12),
        )
    }

    private fun fakeTransfers(): List<Transfer> = listOf(
        Transfer(1, "vinylhead", "@@music\\Night Transit\\01 - Primera luz.flac", 38_000_000L, TransferState.DONE, 38_000_000L, savedAs = "Night Transit/01 - Primera luz.flac"),
        Transfer(2, "vinylhead", "@@music\\Night Transit\\02 - Desvío.flac", 41_000_000L, TransferState.TRANSFERRING, 17_500_000L, speed = 2_300_000),
        Transfer(3, "vinylhead", "@@music\\Night Transit\\03 - Corriente.flac", 36_000_000L, TransferState.QUEUED, place = 2),
        Transfer(4, "kaito", "@@music\\Night Transit\\04 - Faro norte.flac", 30_000_000L, TransferState.FAILED, error = "Este usuario no te permite descargar"),
    )
}
