package dev.surco.ios

import dev.surco.data.Liked
import dev.surco.data.PlayEvent
import dev.surco.data.ScanRules
import dev.surco.data.Track
import dev.surco.playback.PlayerUiState
import dev.surco.util.nowMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill

/**
 * Biblioteca de demostración para las capturas automáticas en el simulador
 * (se activa con el argumento de arranque «--surco-demo»).
 */
@OptIn(ExperimentalForeignApi::class)
object IosDemo {
    private val args: List<String> get() = NSProcessInfo.processInfo.arguments.mapNotNull { it as? String }
    val enabled get() = "--surco-demo" in args
    val route: String? get() = args.firstOrNull { it.startsWith("--surco-route=") }?.substringAfter('=')
    val openPlayer get() = "--surco-player" in args

    private val albums = listOf(
        Triple("Night Transit", "Velvet Arcade", 0x7B2D5B), Triple("Hojas de Cobre", "Los Faros", 0x1F4E5F),
        Triple("Marea Baja", "Lumen Drift", 0x6A3C8F), Triple("Static Bloom", "Paper Moons", 0x8F5A2C),
        Triple("Arena y Sal", "Kestrel", 0x2C6E49), Triple("Raíz", "Nadia Ferro", 0x5C2C2C),
    )
    private val songs = listOf("Corriente", "Faro", "Medianoche", "Ceniza", "Vidrio", "Luz de neón", "Orilla", "Distancia")

    private fun art(rgb: Int): ByteArray? {
        val size = CGSizeMake(600.0, 600.0)
        val img = UIGraphicsImageRenderer(size).imageWithActions { _ ->
            UIColor(red = ((rgb shr 16) and 0xFF) / 255.0, green = ((rgb shr 8) and 0xFF) / 255.0, blue = (rgb and 0xFF) / 255.0, alpha = 1.0).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, 600.0, 600.0))
            UIColor(white = 1.0, alpha = 0.25).setFill()
            UIRectFill(CGRectMake(60.0, 320.0, 300.0, 220.0))
        }
        return UIImageJPEGRepresentation(img, 0.9)?.toByteArray()
    }

    fun seedIfRequested(c: IosContainer) {
        if (!enabled) return
        runBlocking {
            val dao = c.db.dao()
            if (dao.allTracksOnce().isEmpty()) {
                val now = nowMillis()
                val tracks = ArrayList<Track>()
                albums.forEachIndexed { ai, (album, artist, color) ->
                    val key = ScanRules.albumKeyFor(artist, album, "demo$ai")
                    val path = art(color)?.let { IosArtStore().writeAlbumArt(key, it) }
                    repeat(8) { ti ->
                        val title = songs[(ai + ti) % songs.size]
                        tracks += Track(
                            uri = "file:///demo/$ai/$ti.flac", folderId = "demo$ai", fileName = "${ti + 1}. $title.flac",
                            mime = "audio/flac", title = title, artist = artist, album = album, albumArtist = artist,
                            groupArtist = artist, albumKey = key, trackNumber = ti + 1, discNumber = 1, year = 2018 + ai,
                            genre = listOf("Synthpop", "Indie", "Ambient")[ai % 3], durationMs = 180_000L + ti * 7_000,
                            bitrate = 1_000_000, sampleRate = 96000, bitsPerSample = 24, size = 30_000_000,
                            lastModified = 0, addedAt = now - ai * 86_400_000L, artPath = path,
                        )
                    }
                }
                dao.upsertTracks(tracks)
                tracks.take(5).forEach { dao.like(Liked(it.uri, now)) }
                tracks.filterIndexed { i, _ -> i % 3 == 0 }.forEach {
                    dao.insertPlay(PlayEvent(trackUri = it.uri, albumKey = it.albumKey, artist = it.groupArtist, playedAt = now - 3_600_000))
                }
                c.repository.createPlaylist("Para correr").let { id -> c.repository.addToPlaylist(id, tracks.take(12).map { it.uri }) }
            }
            // «demo:» no es una carpeta real: el escaneo falla sin borrar nada.
            c.prefs.update { it.copy(treeUri = "demo:", language = "en", discover = false) }
            if (openPlayer) {
                val first = dao.allTracksOnce().first()
                c.player.debugSetState(PlayerUiState(connected = true, mediaId = first.uri, isPlaying = true, position = 72_000, duration = first.durationMs, source = "album|${first.album}"))
            }
        }
    }
}
