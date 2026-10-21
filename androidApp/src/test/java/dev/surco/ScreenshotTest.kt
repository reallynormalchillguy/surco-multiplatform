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
import dev.surco.ui.onlineIdByName
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
     * Los gráficos nativos de Robolectric preparan sus clases de Java (JniConstants) la primera vez
     * que una imagen no se puede decodificar. Si eso ocurre en un hilo de fondo (Coil, el color de
     * la portada…), no encuentran java.nio y el proceso de pruebas aborta (código 134), según quién
     * llegue antes. Forzándolo aquí, en el hilo principal, queda preparado para todos.
     */
    @org.junit.Before
    fun warmUpNativeGraphics() {
        runCatching { android.graphics.BitmapFactory.decodeByteArray(ByteArray(32) { 0x42 }, 0, 32) }
        runCatching { android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).copyPixelsToBuffer(java.nio.IntBuffer.allocate(16)) }
    }

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
        // Sin la clave de Last.fm que lleva la app: las capturas no deben tocar la red (descargaba
        // imágenes y datos de verdad y Robolectric acababa cayéndose al decodificarlos).
        c.prefs.update { it.copy(treeUri = "content://fake/tree", language = "en", lastfmKey = "", lastfmSecret = "") }
        // Recomendaciones de ejemplo (sin red): álbumes ficticios «que no tienes».
        val recs = listOf("Aurora Static", "Paper Moons", "Kestrel", "Nadia Ferro", "The Lowlands").mapIndexed { i, artist ->
            DiscoverAlbum(1000L + i, listOf("Glass Hours", "Small Weather", "Northbound", "Luz de Agosto", "Fieldnotes")[i], artist, fakeArt("rec$i", i + 3), 2018 + i)
        }
        c.discovery.debugSet(listOf(DiscoverSection("Velvet Arcade", fakeArt("seed", 2), recs)))
        // Lo que «sabe internet» de dos artistas de la biblioteca (populares, discografía y parecidos), sin red.
        listOf("Carmen Duna" to 7300L, "Velvet Arcade" to 7400L).forEachIndexed { k, (name, id) ->
            val mine = albums.filter { it.second == name }.map { it.first }
            onlineIdByName[name.lowercase()] = id
            onlineArtistCache[id] = OnlineArtist(
                Deezer.Artist(id, name, fakeArt("artist$k", k + 4), fans = 384_000 + k * 91_000, albums = 6),
                top = listOf(songNames[(3 + k) % songNames.size], "Luces de Domingo", songNames[(4 + k) % songNames.size], "Otra Orilla", "Madrugada")
                    .mapIndexed { i, n -> Deezer.TopTrack(n, name, listOf(mine[0], "Edición Especial", mine.last())[i % 3], 2000L + i, fakeArt("rec${i % 3}", 3 + i), 200 + i * 11, preview = "https://cdn.example.com/p$k$i.mp3") },
                albums = mine.mapIndexed { i, a -> Deezer.Album(2100L + k * 10 + i, a, name, null, 2015 + i * 3, "album", 500) } + listOf(
                    Deezer.Album(2190L + k, "Edición Especial", name, fakeArt("rec2", 5), 2024, "album", 300),
                    Deezer.Album(2195L + k, "Directo en Cádiz", name, fakeArt("rec4", 7), 2022, "ep", 100),
                ),
                related = listOf("Paper Moons", "Los Faros", "Nadia Ferro", "Mint Avenue").mapIndexed { i, n -> Deezer.Artist(7500L + k * 10 + i, n, fakeArt("rel$i", i + 1)) },
            )
        }
        discoverDetailCache[1000L] = Deezer.AlbumDetail(
            album = Deezer.Album(1000L, "Glass Hours", "Aurora Static", fakeArt("rec0", 3), 2018, "album", 1000),
            genres = listOf("Synthpop", "Electronic"), label = "Satellite Records", durationSec = 2580,
            tracks = songNames.take(10).map { Deezer.Song(it, "Aurora Static", 200 + it.length * 7, preview = "https://cdn.example.com/$it.mp3") },
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
            top = songNames.take(5).mapIndexed { i, n -> Deezer.TopTrack(n, "Aurora Static", listOf("Glass Hours", "Small Weather")[i % 2], 1000L, fakeArt("rec${i % 2}", 3 + i % 2), 190 + i * 17, preview = "https://cdn.example.com/preview$i.mp3") },
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
        compose.onNodeWithTag("sleep_timer", useUnmergedTree = true).performClick()
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
        snap("10b_panel_perfil")
        compose.onNodeWithTag("drawer_settings").performClick()
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


        // Estadísticas: sin cuenta (invita a conectarla o crearla) y con datos de ejemplo.
        compose.onNodeWithTag("tab_stats").performClick()
        snap("15_estadisticas_conectar")
        // Con la clave de la app: un solo botón que abre Last.fm para permitir el acceso.
        c.prefs.update { it.copy(lastfmKey = "k", lastfmSecret = "s") }
        snap("15a_estadisticas_conectar_web")
        c.stats.debugSet(fakeStats(c))
        c.prefs.update { it.copy(lastfmKey = "k", lastfmSecret = "s", lastfmSession = "x", lastfmUser = "surco_demo") }
        settle()
        c.stats.debugSetRecent(fakeRecent())
        snap("15b_estadisticas")
        for ((tag, shot) in listOf(
            "stats_clock" to "15c_estadisticas_reloj", "stats_week" to "15d_estadisticas_semana",
            "stats_activity" to "15e_estadisticas_actividad", "stats_artists" to "15f_estadisticas_artistas",
            "stats_tracks" to "15g_estadisticas_canciones", "stats_facts" to "15h_estadisticas_datos",
        )) {
            compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag(tag))
            snap(shot)
        }
        // Lista top completa (al tocar «Artistas» en el perfil).
        c.stats.debugPutTop(dev.surco.data.StatsRepository.TopKind.ARTISTS, dev.surco.data.StatsPeriod.MONTH,
            (0 until 20).map { i -> dev.surco.data.TopItem("Artista ${'A' + i}", "Artista ${'A' + i}", (400 - i * 17).toLong(), fakeArt("artist${i % 6}", i), "") })
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("hero_artists"))
        compose.onNodeWithTag("hero_artists").performClick()
        snap("15i_top_artistas")
        compose.onNodeWithTag("tab_stats").performClick()
        settle()
        c.prefs.update { it.copy(lastfmSession = "", lastfmUser = "") }
        compose.onNodeWithTag("tab_home").performClick()
        settle()

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

    /** Audio para audiófilos, historial de búsquedas, artista completo, zoom y espectrograma. */
    @Test
    fun audioAndExtras() {
        val c = seed()
        compose.setContent { SurcoTheme { SurcoRoot(container = c, openPlayerSignal = 0) } }
        settle()

        // Ajustes de sonido: todo apagado de entrada.
        compose.onAllNodesWithContentDescription(S.settings)[0].performClick()
        compose.onNodeWithTag("drawer_settings").performClick()
        settle()
        compose.onNodeWithTag("cache_row").performScrollTo()
        snap("21_cache")
        compose.onNodeWithTag("clear_cache").performClick()
        snapScreen("21b_cache_confirmar")
        compose.onNodeWithText(S.cancel).performClick()
        settle()
        compose.onNodeWithTag("open_audio").performScrollTo().performClick()
        snap("16_audio")
        compose.onNodeWithTag("tab_home").performClick()
        settle()
        // Con un preajuste, normalización y crossfade activados.
        c.prefs.update {
            it.copy(
                eqEnabled = true, eq = dev.surco.ui.eqPresets()[4].second.encode(),
                normalize = true, crossfade = true, crossfadeSec = 6,
            )
        }
        compose.onAllNodesWithContentDescription(S.settings)[0].performClick()
        compose.onNodeWithTag("drawer_settings").performClick()
        settle()
        compose.onNodeWithTag("open_audio").performScrollTo().performClick()
        snap("16b_ecualizador")
        compose.onNodeWithTag("crossfade_slider").performScrollTo()
        snap("16c_normalizacion_crossfade")
        compose.onNodeWithTag("tab_home").performClick()
        settle()

        // Buscar dentro de «Tu biblioteca» (sin ir a la pestaña «Buscar»).
        compose.onNodeWithTag("tab_library").performClick()
        settle()
        compose.onNodeWithTag("library_search").performClick()
        settle()
        compose.onNodeWithTag("library_search_field").performTextInput("velvet")
        settle()
        snap("22_biblioteca_buscar")
        compose.onNodeWithTag("library_search_results").assertExists()
        compose.onNodeWithTag("library_search_close").performClick()
        compose.onNodeWithTag("tab_home").performClick()
        settle()

        // Historial de búsquedas.
        runBlocking {
            c.repository.rememberQuery("velvet arcade")
            Thread.sleep(5)
            c.repository.rememberQuery("flamenco")
            Thread.sleep(5)
            c.repository.rememberSearch("album", "x", "Raíz", "Carmen Duna", fakeArt("album9", 9))
            Thread.sleep(5)
            c.repository.rememberSearch("artist", "Velvet Arcade", "Velvet Arcade", "", null)
        }
        compose.onNodeWithTag("tab_search").performClick()
        snap("17_historial_busqueda")

        // Artista de la biblioteca con lo que sabe internet: populares, discografía y parecidos.
        compose.onNodeWithTag("tab_library").performClick()
        settle()
        compose.onAllNodesWithText("Velvet Arcade")[0].performClick()
        snap("18_artista_completo")
        runCatching { compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(hasTestTag("artist_discography")) }
        snap("18b_artista_discografia")
        runCatching { compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(hasTestTag("artist_related")) }
        snap("18c_artista_parecidos")

        // Zoom de la interfaz.
        compose.onNodeWithTag("tab_home").performClick()
        c.prefs.update { it.copy(uiScale = 0.85f) }
        snap("19_zoom_85")
        c.prefs.update { it.copy(uiScale = 1.2f) }
        snap("19b_zoom_120")
        c.prefs.update { it.copy(uiScale = dev.surco.data.DEFAULT_UI_SCALE) }
        settle()

        // Espectrograma: la portada del reproductor con una señal de prueba (graves, voz y platos).
        val first = runBlocking { c.db.dao().allTracksOnce().first { it.album == "Night Transit" } }
        c.player.debugSetState(
            PlayerUiState(
                connected = true, mediaId = first.uri, isPlaying = true, position = 72_500, duration = first.durationMs,
                source = "álbum|${first.album}", queue = listOf(first.uri), queueIndex = 0,
            )
        )
        c.sleepTimer.endOfTrack()
        settle()
        compose.onNodeWithTag("mini_player").performClick()
        settle()
        compose.mainClock.autoAdvance = false
        c.prefs.update { it.copy(spectrogram = true) }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        val rate = 44_100
        var n = 0L
        val rnd = Random(7)
        val notes = listOf(55.0, 65.4, 49.0, 73.4)
        repeat(240) { col ->
            val f0 = notes[(col / 40) % notes.size]
            val formant = 350.0 + 500.0 * (0.5 + 0.5 * kotlin.math.sin(col / 18.0))
            val buf = FloatArray(2048 * 2)
            for (i in 0 until 2048) {
                val t = (n + i).toDouble() / rate
                var v = 0.0
                for (h in 1..10) v += kotlin.math.sin(2 * Math.PI * f0 * h * t) * 0.25 / h
                v += 0.12 * kotlin.math.sin(2 * Math.PI * formant * t) * (if ((col / 6) % 3 == 0) 0.2 else 1.0)
                v += 0.08 * kotlin.math.sin(2 * Math.PI * formant * 2.02 * t)
                if (col % 20 < 2) v += (rnd.nextDouble() - 0.5) * 0.5
                if (col % 10 < 1) v += 0.4 * kotlin.math.sin(2 * Math.PI * 50 * t) * kotlin.math.exp(-i / 600.0)
                buf[i * 2] = v.toFloat(); buf[i * 2 + 1] = v.toFloat()
            }
            n += 2048
            dev.surco.audio.AudioFx.tap.write(buf, 2048, 2, rate)
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onRoot().captureRoboImage("build/screens/20_espectrograma.png")
        c.prefs.update { it.copy(spectrogram = false) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
    }

    private fun fakeRecent(): List<dev.surco.data.RecentScrobble> {
        val now = System.currentTimeMillis() / 1000
        return songNames.take(11).mapIndexed { i, t ->
            dev.surco.data.RecentScrobble(t, listOf("Carmen Duna", "Velvet Arcade", "Los Faros")[i % 3], "Raíz", null,
                if (i == 0) 0 else now - i * 23 * 60L - (if (i > 6) 86_400L else 0), i == 0, "")
        }
    }

    /** Estadísticas de ejemplo: un mes de escuchas, más de tarde-noche, con un domingo muy intenso. */
    private fun fakeStats(c: AppContainer): dev.surco.data.StatsReport {
        val rnd = Random(7)
        val now = System.currentTimeMillis() / 1000
        val artists = listOf("Carmen Duna", "Velvet Arcade", "Los Faros", "Lumen Drift", "Paper Moons", "Kestrel", "Nadia Ferro", "Aurora Static")
        val weights = listOf(1, 1, 1, 0, 0, 0, 0, 1, 2, 3, 3, 3, 4, 3, 3, 4, 5, 6, 8, 9, 11, 12, 10, 5)
        val scrobbles = ArrayList<dev.surco.data.Scrobble>()
        for (day in 0 until 30) {
            if (day % 9 == 4) continue
            val n = 12 + rnd.nextInt(30) + if (day == 11) 60 else 0
            repeat(n) {
                var h = rnd.nextInt(24)
                while (rnd.nextInt(12) > weights[h]) h = rnd.nextInt(24)
                val at = now - day * 86_400L - (now % 86_400) + h * 3600L + rnd.nextInt(3600)
                if (at > now) return@repeat
                val a = artists[minOf(artists.size - 1, (rnd.nextDouble().let { it * it } * artists.size).toInt())]
                scrobbles += dev.surco.data.Scrobble(at, a, songNames[rnd.nextInt(songNames.size)], "Raíz")
            }
        }
        repeat(9) { scrobbles += dev.surco.data.Scrobble(now - 3 * 86_400 + it * 240L, "Carmen Duna", "Kilómetro cero", "Raíz") }
        val byArtist = scrobbles.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }
        val top = byArtist.mapIndexed { i, (a, n) -> dev.surco.data.TopItem(a, a, n.toLong(), fakeArt("artist${i % 6}", i + 4), "") }
        val albums = listOf("Raíz", "Night Transit", "Hojas de Cobre", "Marea Baja", "Static Bloom", "Arena y Sal").mapIndexed { i, al ->
            dev.surco.data.TopItem(al, artists[i], (180 - i * 23).toLong(), fakeArt("album$i", i), "")
        }
        val tracks = songNames.take(8).mapIndexed { i, t -> dev.surco.data.TopItem(t, artists[i % 3], (64 - i * 6).toLong(), null, "") }
        val profile = dev.surco.data.LfmProfile("surco_demo", "Surco Demo", null, 48_213, 1_284, 2_407, 9_872, 1_394_000_000L, "Spain", "https://www.last.fm/user/surco_demo")
        val library = runBlocking { c.db.dao().allTracksOnce() }.map { dev.surco.data.StatsMath.key(it.artist, it.title) }.toSet()
        return dev.surco.data.StatsMath.report(
            profile, dev.surco.data.StatsPeriod.MONTH, scrobbles.size.toLong(), scrobbles, true, top, albums, tracks,
            listOf("Flamenco" to 0.31f, "Indie pop" to 0.24f, "Synthpop" to 0.17f, "Ambient" to 0.12f, "Dream pop" to 0.09f, "Electronic" to 0.07f)
                .map { dev.surco.data.GenreShare(it.first, it.second) },
            library, 312, now, { 7200 },
        )
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
