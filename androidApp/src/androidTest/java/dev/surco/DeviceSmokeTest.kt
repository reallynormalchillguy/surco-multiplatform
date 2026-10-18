package dev.surco

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.surco.data.DiscoverAlbum
import dev.surco.data.DiscoverSection
import dev.surco.data.Discovery
import dev.surco.data.LrcParser
import dev.surco.data.Scanner
import dev.surco.data.Track
import dev.surco.i18n.S
import dev.surco.slsk.TransferState as T
import dev.surco.net.Deezer
import dev.surco.net.LrcLib
import dev.surco.ui.discoverDetailCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo en un Android real (emulador en CI): abre la app de verdad y pulsa los botones
 * principales. Detecta lo que Robolectric no ve (p. ej. el motor de expresiones regulares de Android).
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val notifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val app get() = ApplicationProvider.getApplicationContext<SurcoApp>()

    @Before
    fun setUp() {
        val c = app.container
        runBlocking {
            c.db.clearAllTables()
            val tracks = (0 until 4).flatMap { a ->
                val album = listOf("Raíz", "Marea Baja", "Night Transit", "Grain")[a]
                val artist = listOf("Carmen Duna", "Lumen Drift", "Velvet Arcade", "Halvorsen Trio")[a]
                (1..6).map { i ->
                    Track(
                        uri = "content://fake/$a/$i", folderId = "f$a", fileName = "$i.flac", mime = "audio/flac",
                        title = "Song $a-$i", artist = artist, album = album, albumArtist = artist, groupArtist = artist,
                        albumKey = dev.surco.data.ScanRules.albumKeyFor(artist, album, "f$a"), trackNumber = i, discNumber = 1, year = 2020,
                        genre = listOf("Flamenco", "Electronic", "Synthpop", "Jazz")[a], durationMs = 200_000, bitrate = 900_000,
                        sampleRate = 44_100, bitsPerSample = 16, size = 20_000_000, lastModified = 1, addedAt = 1, artPath = null,
                    )
                }
            }
            c.db.dao().upsertTracks(tracks)
        }
        c.prefs.update {
            it.copy(treeUri = "content://fake/tree", language = "en", slskUser = "", slskPass = "", discover = true)
        }
        c.discovery.debugSet(
            listOf(DiscoverSection("Velvet Arcade", null, (0 until 4).map { DiscoverAlbum(9000L + it, "Rec $it", "Someone $it", null, 2020) }))
        )
        discoverDetailCache[9000L] = Deezer.AlbumDetail(
            Deezer.Album(9000L, "Rec 0", "Someone 0", null, 2020, "album", 10),
            listOf("Pop"), "Label", 600, listOf(Deezer.Song("Track A", "Someone 0", 200)),
        )
        // Perfil de artista de internet con datos de ejemplo (sin depender de la red).
        val fake = Deezer.Artist(7000L, "Aurora Static", null, fans = 125_000, albums = 3)
        dev.surco.ui.onlineSearchCache["aurora"] = listOf(fake)
        dev.surco.ui.onlineArtistCache[7000L] = dev.surco.ui.OnlineArtist(
            fake,
            top = listOf(Deezer.TopTrack("Glass", "Aurora Static", "Rec 0", 9000L, null, 210)),
            albums = listOf(Deezer.Album(9000L, "Rec 0", "Aurora Static", null, 2020, "album", 10)),
            related = listOf(Deezer.Artist(7001L, "Paper Moons", null)),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    @After
    fun tearDown() {
        runCatching { scenario.close() }
    }

    private fun settle() {
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
    }

    private fun ComposeTestRule.first(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        onAllNodesWithText(text, substring = substring)[0]
    private fun ComposeTestRule.firstDesc(desc: String): SemanticsNodeInteraction = onAllNodesWithContentDescription(desc)[0]

    /** Pulsación real de la tecla «atrás», como la del móvil. */
    private fun back() {
        // Equivale al gesto de atrás del sistema (sin depender del foco de ventana del emulador).
        @Suppress("DEPRECATION")
        scenario.onActivity { it.onBackPressed() }
        settle()
    }

    private fun shows(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gone(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    private var step = 0

    /** Guarda una captura real del dispositivo (la CI las descarga para poder revisarlas). */
    private fun shot(label: String) {
        android.util.Log.i("SURCO_STEP", "paso ${step + 1}: $label")
        runCatching {
            // Con un diálogo u hoja abiertos hay varias ventanas: copiar sus píxeles colgaba el emulador 37.
            val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            if (roots.size > 1) { ++step; return }
            val bmp = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
            val dir = java.io.File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
            java.io.File(dir, "%02d_%s.png".format(++step, label.replace(Regex("[^A-Za-z0-9]+"), "_").take(40)))
                .outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        }
    }

    private fun tap(node: SemanticsNodeInteraction, label: String = "tap") {
        node.performClick()
        settle()
        shot(label)
    }

    @Test
    fun regexesCompileOnAndroid() {
        // El motor de Android (ICU) es más estricto que el de Java: aquí se comprueba de verdad.
        assertEquals(2, LrcParser.parse("[offset:+0]\n[00:01.00]a\n[00:02.50]b").size)
        assertEquals("Song", LrcLib.cleanTitle("Song (feat. X)"))
        assertEquals("Artist Album", Discovery.soulseekQuery("Artist", "Album (Deluxe Edition)"))
        assertEquals(12, dev.surco.data.ScanRules.leadingInt("12/14"))
        assertEquals(1999, dev.surco.data.ScanRules.parseYear("1999-01-01"))
    }

    /** Si un paso falla, vuelca al logcat lo que hay en pantalla (para poder diagnosticarlo desde CI). */
    private fun dumpingOnFailure(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("SURCO_TREE") }
            throw t
        }
    }

    @Test
    fun soulseekFiltersAndBulkActions() = dumpingOnFailure {
        val c = app.container
        c.prefs.update { it.copy(slskUser = "surco_demo", slskPass = "x", slskFormat = "all") }
        fun f(user: String, path: String, size: Long) = dev.surco.slsk.SlskFile(user, path, size, if (path.endsWith("mp3")) 320 else 0)
        val results = listOf(
            dev.surco.slsk.UserResult("ana", (1..4).map { f("ana", "@@m\\Album A\\0$it.flac", 30_000_000) }, true, 1_000_000, 0),
            dev.surco.slsk.UserResult("bob", (1..4).map { f("bob", "@@m\\Album B\\0$it.mp3", 9_000_000) }, true, 800_000, 0),
        )
        val transfers = listOf(
            dev.surco.slsk.Transfer(1, "ana", "@@m\\Album A\\01.flac", 30_000_000, T.QUEUED),
            dev.surco.slsk.Transfer(2, "ana", "@@m\\Album A\\02.flac", 30_000_000, T.QUEUED),
            dev.surco.slsk.Transfer(3, "bob", "@@m\\Album B\\01.mp3", 9_000_000, T.TRANSFERRING, 3_000_000, 500_000),
            dev.surco.slsk.Transfer(4, "bob", "@@m\\Album B\\02.mp3", 9_000_000, T.FAILED, error = "x"),
        )
        c.soulseek.client.debugSet(dev.surco.slsk.SlskState.Online("surco_demo", 2234), results, transfers)

        tap(compose.onNodeWithTag("tab_search"))
        tap(compose.firstDesc(S.downloadWithSoulseek), "soulseek")
        tap(compose.onNodeWithTag("format_mp3"), "filtro_mp3")
        compose.first("Album B").assertExists()
        org.junit.Assert.assertTrue(
            "El filtro MP3 debería ocultar el álbum FLAC",
            compose.onAllNodesWithText("Album A").fetchSemanticsNodes().isEmpty(),
        )
        tap(compose.onNodeWithTag("format_flac"), "filtro_flac")
        compose.first("Album A").assertExists()
        tap(compose.onNodeWithTag("format_all"))

        tap(compose.first(S.downloads, substring = true), "descargas")
        tap(compose.onNodeWithTag("cancel_queued"), "cancelar_cola")
        // Lo cancelado sale de la lista; queda la que baja y la fallida.
        assertEquals(listOf(3L, 4L), c.soulseek.client.transfers.value.map { it.id })
        tap(compose.onNodeWithTag("cancel_downloading"), "cancelar_activas")
        // Sin nada en marcha, el historial se limpia solo.
        assertEquals(emptyList<Long>(), c.soulseek.client.transfers.value.map { it.id })
    }

    @Test
    fun navigatesThroughTheAppWithoutCrashing() = dumpingOnFailure {
        // Biblioteca → álbum → atrás
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.first("Raíz"))
        tap(compose.firstDesc(S.back))

        // Ajustes → Abrir Soulseek (el botón que cerraba la app) → atrás
        tap(compose.onNodeWithTag("tab_home"))
        tap(compose.firstDesc(S.settings)); Thread.sleep(500); tap(compose.onNodeWithTag("drawer_settings"))
        compose.first(S.openSoulseek).performScrollTo()
        tap(compose.first(S.openSoulseek))
        compose.first(S.connectAccount).assertExists()
        back()
        back()

        // Buscar → género → pestaña Buscar → escribir → «Buscar en Soulseek»
        tap(compose.onNodeWithTag("tab_search"))
        tap(compose.first("Flamenco"))
        tap(compose.onNodeWithTag("tab_search"))
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Raíz")
        settle()
        compose.onNodeWithTag("search_results").performScrollToNode(hasText(S.searchOnSoulseekFor("Raíz")))
        tap(compose.first(S.searchOnSoulseekFor("Raíz")))
        back()

        // Buscador → artista de internet → perfil → álbum de su discografía → atrás ×2
        compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("aurora")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("online_artist_7000")).fetchSemanticsNodes().isNotEmpty() }
        tap(compose.onNodeWithTag("online_artist_7000"), "perfil_online")
        compose.onNodeWithTag("online_artist_screen").performScrollToNode(hasTestTag("online_album_9000"))
        tap(compose.onNodeWithTag("online_album_9000"), "album_online")
        back()
        back()

        // Álbum: reproducir, menú ⋮, ir al artista
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.first("Raíz"))
        tap(compose.firstDesc(S.play))
        // El reproductor tarda un momento en conectar con el servicio.
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("mini_player")).fetchSemanticsNodes().isNotEmpty() }
        tap(compose.firstDesc(S.moreOptions))
        tap(compose.first(S.goToArtist))
        shows(S.popular)                                  // pantalla del artista

        // Reproductor completo, letra, cola
        tap(compose.onNodeWithTag("mini_player"))
        compose.onNodeWithTag("lyrics_card").performScrollTo()
        tap(compose.onNodeWithTag("lyrics_card"))
        compose.waitUntil(5_000) {                        // letra a pantalla completa
            compose.onAllNodesWithContentDescription(S.lyricsOptions).fetchSemanticsNodes().isNotEmpty()
        }
        back()                                            // cierra la letra
        back()                                            // cierra el reproductor
        gone("lyrics_card")

        // Inicio → recomendación → página del álbum → Buscar en Soulseek
        tap(compose.onNodeWithTag("tab_home"))
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("discover_9000"))
        tap(compose.onNodeWithTag("discover_9000"))
        tap(compose.onNodeWithTag("discover_soulseek"))
        back()
        back()

        // Crear una lista desde la biblioteca (la pestaña recuerda dónde estabas: segundo toque → raíz)
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.firstDesc(S.createPlaylist), "crear_lista")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Test list")
        settle(); shot("nombre_lista")
        tap(compose.first(S.save), "guardar_lista")
        shows("Test list")

        // Cambiar el idioma en caliente
        tap(compose.onNodeWithTag("tab_home"))
        tap(compose.onNodeWithTag("tab_home"))
        tap(compose.firstDesc(S.settings)); Thread.sleep(500); tap(compose.onNodeWithTag("drawer_settings"))
        tap(compose.first("Español"), "espanol")
        compose.onAllNodes(hasText("Ajustes"))[0].assertExists()
        tap(compose.first("English"))
    }

    @Test
    fun albumQueueDeleteAndPalette() = dumpingOnFailure {
        val c = app.container
        val key = dev.surco.data.ScanRules.albumKeyFor("Halvorsen Trio", "Grain", "f3")
        fun grainInQueue() = c.player.state.value.queue.count { it.startsWith("content://fake/3/") }

        // Álbum entero a la cola
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.first("Grain"))
        tap(compose.onNodeWithTag("album_more"), "menu_album")
        tap(compose.onNodeWithTag("album_enqueue"), "album_en_cola")
        compose.waitUntil(10_000) { grainInQueue() == 6 }

        // Borrar: dos avisos; el segundo botón no responde hasta pasado un momento
        tap(compose.onNodeWithTag("album_more"))
        tap(compose.onNodeWithTag("album_delete"), "aviso_1")
        tap(compose.onNodeWithTag("delete_step1"), "aviso_2")
        compose.onNodeWithTag("delete_step2").performClick()   // demasiado pronto: no hace nada
        settle()
        assertEquals(6, runBlocking { c.db.dao().albumTracksOnce(key).size })
        // La cuenta atrás usa el reloj de Compose, que en las pruebas avanza a mano.
        compose.mainClock.advanceTimeBy(2_500)
        Thread.sleep(500)
        tap(compose.onNodeWithTag("delete_step2"), "borrado")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("delete_step2")).fetchSemanticsNodes().isEmpty() }
        // Estas canciones son de mentira (no hay archivos ni proveedor de documentos): como no se puede
        // comprobar que se hayan borrado, NO deben desaparecer de la biblioteca ni de la cola.
        assertEquals(6, runBlocking { c.db.dao().albumTracksOnce(key).size })
        assertEquals(6, grainInQueue())
        shot("tras_borrar")
        back()

        // Paleta de colores: sortear y volver a la original
        tap(compose.onNodeWithTag("tab_home"))
        tap(compose.firstDesc(S.settings)); Thread.sleep(500); tap(compose.onNodeWithTag("drawer_settings"))
        // Se baja hasta el zoom (unas filas más abajo) para que la fila de la paleta quede a la vista,
        // ni bajo el mini reproductor ni por encima de la barra de arriba.
        compose.onNodeWithTag("ui_zoom").performScrollTo()
        tap(compose.onNodeWithTag("open_palette"), "paleta")
        tap(compose.onNodeWithTag("palette_shuffle"), "paleta_sorteada")
        org.junit.Assert.assertTrue(c.prefs.current.palette.isNotBlank())
        tap(compose.onNodeWithTag("swatch_background"), "editar_fondo")
        tap(compose.firstDesc(S.resetPalette))
        assertEquals("", c.prefs.current.palette)
    }

    @Test
    fun swipingTheCoverChangesTheSong() = dumpingOnFailure {
        val c = app.container
        tap(compose.onNodeWithTag("tab_library"))
        tap(compose.first("Raíz"))
        tap(compose.firstDesc(S.play))
        compose.waitUntil(10_000) { c.player.state.value.mediaId == "content://fake/0/1" && c.player.state.value.nextIndex >= 0 }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("track_swipe"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Mini reproductor: a la izquierda → siguiente
        compose.onAllNodes(hasTestTag("track_swipe"), useUnmergedTree = true)[0].performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { c.player.state.value.mediaId == "content://fake/0/2" }
        shot("deslizar_siguiente")
        // Reproductor completo: a la derecha → anterior
        tap(compose.onNodeWithTag("mini_player"))
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("track_swipe"), useUnmergedTree = true).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasTestTag("track_swipe"), useUnmergedTree = true)[1].performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { c.player.state.value.mediaId == "content://fake/0/1" }
        shot("deslizar_anterior")
        back()
    }
}
