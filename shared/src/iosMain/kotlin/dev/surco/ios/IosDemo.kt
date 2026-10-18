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
    /** Reproduce de verdad una señal de prueba con el ecualizador y el espectrograma (comprueba el audio en el simulador). */
    val spectro get() = "--surco-spectro" in args

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
            if (route == "stats") {
                c.stats.debugSet(demoStats(dao.allTracksOnce()))
                c.prefs.update { it.copy(lastfmKey = "demo", lastfmSecret = "demo", lastfmSession = "demo", lastfmUser = "surco_demo") }
                val nowS = nowMillis() / 1000
                c.stats.debugSetRecent(songs.take(8).mapIndexed { i, t ->
                    dev.surco.data.RecentScrobble(t, albums[i % albums.size].second, albums[i % albums.size].first, null, if (i == 0) 0 else nowS - i * 1500L, i == 0, "")
                })
            }
            if (spectro) {
                val path = demoWav()
                val first = dao.allTracksOnce().first()
                val t = first.copy(
                    uri = platform.Foundation.NSURL.fileURLWithPath(path).absoluteString ?: "file://$path",
                    title = "Señal de prueba", fileName = "senal.wav", mime = "audio/wav", durationMs = 40_000,
                    sampleRate = 44_100, bitsPerSample = 16,
                )
                dao.upsertTracks(listOf(t))
                c.prefs.update {
                    it.copy(spectrogram = true, eqEnabled = true, eq = dev.surco.ui.eqPresets()[1].second.encode(), normalize = true)
                }
                demoTrack = t
            }
            if (openPlayer && !spectro) {
                val first = dao.allTracksOnce().first()
                c.player.debugSetState(PlayerUiState(connected = true, mediaId = first.uri, isPlaying = true, position = 72_000, duration = first.durationMs, source = "album|${first.album}", queue = listOf(first.uri), queueIndex = 0))
            }
        }
    }

    /** Canción de prueba que hay que poner a sonar (con «--surco-spectro»). */
    var demoTrack: Track? = null
        private set

    /** Pone a sonar la señal de prueba (en el hilo principal, después de montar la app). */
    fun playIfRequested(c: IosContainer) {
        val t = demoTrack ?: return
        c.player.play(listOf(t), 0, "album|${t.album}", false)
    }

    /**
     * WAV de 40 s generado aquí mismo: un bajo con armónicos que cambia de nota, una «voz» que sube y
     * baja, platos y bombo. Sirve para ver el espectrograma de verdad en el simulador.
     */
    private fun demoWav(): String {
        val path = "${IosDirs.cache}/surco-demo.wav"
        val rate = 44_100
        val frames = rate * 40
        val out = ByteArray(44 + frames * 4)
        fun put32(at: Int, v: Int) { for (i in 0 until 4) out[at + i] = (v shr (8 * i)).toByte() }
        fun put16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
        "RIFF".encodeToByteArray().copyInto(out, 0); put32(4, 36 + frames * 4)
        "WAVEfmt ".encodeToByteArray().copyInto(out, 8); put32(16, 16); put16(20, 1); put16(22, 2)
        put32(24, rate); put32(28, rate * 4); put16(32, 4); put16(34, 16)
        "data".encodeToByteArray().copyInto(out, 36); put32(40, frames * 4)
        val notes = doubleArrayOf(55.0, 65.4, 49.0, 73.4)
        val rnd = kotlin.random.Random(7)
        for (i in 0 until frames) {
            val t = i.toDouble() / rate
            val beat = (t * 2).toInt()
            val f0 = notes[(beat / 4) % notes.size]
            var v = 0.0
            for (h in 1..10) v += kotlin.math.sin(2 * kotlin.math.PI * f0 * h * t) * 0.18 / h
            val formant = 350.0 + 500.0 * (0.5 + 0.5 * kotlin.math.sin(t * 0.9))
            v += 0.09 * kotlin.math.sin(2 * kotlin.math.PI * formant * t) + 0.05 * kotlin.math.sin(2 * kotlin.math.PI * formant * 2.02 * t)
            val inBeat = t * 2 - beat
            if (inBeat < 0.08) v += 0.35 * kotlin.math.sin(2 * kotlin.math.PI * 52 * t) * (1 - inBeat / 0.08)
            if ((t * 4 - (t * 4).toInt()) < 0.04) v += (rnd.nextDouble() - 0.5) * 0.3
            val s = (v.coerceIn(-1.0, 1.0) * 30_000).toInt()
            put16(44 + i * 4, s); put16(46 + i * 4, s)
        }
        out.toNSData().writeToFile(path, true)
        return path
    }

    /** Estadísticas de ejemplo para la captura de la pestaña «Estadísticas». */
    private fun demoStats(library: List<Track>): dev.surco.data.StatsReport {
        val rnd = kotlin.random.Random(7)
        val now = nowMillis() / 1000
        val artists = albums.map { it.second }
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
                scrobbles += dev.surco.data.Scrobble(at, a, songs[rnd.nextInt(songs.size)], "")
            }
        }
        val byArtist = scrobbles.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }
        val top = byArtist.map { (a, n) -> dev.surco.data.TopItem(a, a, n.toLong(), null, "") }
        val topAlbums = albums.mapIndexed { i, (al, ar, _) -> dev.surco.data.TopItem(al, ar, (180 - i * 23).toLong(), null, "") }
        val tracks = songs.take(8).mapIndexed { i, t -> dev.surco.data.TopItem(t, artists[i % 3], (64 - i * 6).toLong(), null, "") }
        val profile = dev.surco.data.LfmProfile("surco_demo", "Surco Demo", null, 48_213, 1_284, 2_407, 9_872, 1_394_000_000L, "Spain", "")
        return dev.surco.data.StatsMath.report(
            profile, dev.surco.data.StatsPeriod.MONTH, scrobbles.size.toLong(), scrobbles, true, top, topAlbums, tracks,
            listOf("Synthpop" to 0.31f, "Indie pop" to 0.24f, "Ambient" to 0.17f, "Dream pop" to 0.12f, "Electronic" to 0.09f, "Flamenco" to 0.07f)
                .map { dev.surco.data.GenreShare(it.first, it.second) },
            library.map { dev.surco.data.StatsMath.key(it.artist, it.title) }.toSet(), 312, now,
            { sec -> dev.surco.util.utcOffsetSeconds(sec) },
        )
    }
}
