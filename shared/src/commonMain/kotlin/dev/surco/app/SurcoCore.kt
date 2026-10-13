package dev.surco.app

import dev.surco.data.AppFiles
import dev.surco.data.ArtStore
import dev.surco.data.Discovery
import dev.surco.data.KeyValueStore
import dev.surco.data.LibraryDao
import dev.surco.data.LibraryScanner
import dev.surco.data.LyricsRepository
import dev.surco.data.Prefs
import dev.surco.data.Repository
import dev.surco.data.SurcoDatabase
import dev.surco.net.CoverSearch
import dev.surco.net.Deezer
import dev.surco.net.LastFm
import dev.surco.net.LrcLib
import dev.surco.net.PrefsLastFmAccount
import dev.surco.net.surcoHttpClient
import dev.surco.playback.PlayerController
import dev.surco.slsk.SoulseekService
import dev.surco.tags.TagEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Piezas que aporta cada plataforma. */
class PlatformParts(
    val db: SurcoDatabase,
    val store: KeyValueStore,
    val files: AppFiles,
    val art: ArtStore,
    val tags: TagEditor,
    /** El escáner necesita la base de datos y el almacén de portadas. */
    val scanner: (LibraryDao, ArtStore) -> LibraryScanner,
    val userAgent: String,
    /** Versión visible en Ajustes. */
    val appVersion: String = "",
    val lastfmKey: String = "",
    val lastfmSecret: String = "",
    /** Programa un reintento de los scrobbles pendientes para cuando haya conexión. */
    val scheduleScrobbleRetry: () -> Unit = {},
)

/**
 * Inyección de dependencias manual, común a Android e iOS: todo lo que no depende de la plataforma
 * se construye aquí. Cada plataforma añade su reproductor y su servicio de Soulseek.
 */
abstract class SurcoCore(parts: PlatformParts) {
    val appVersion = parts.appVersion
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Cliente común (Ktor) para las APIs públicas: Deezer, iTunes, MusicBrainz, LRCLIB y Last.fm. */
    val api = surcoHttpClient(parts.userAgent)
    val db: SurcoDatabase = parts.db
    val prefs = Prefs(parts.store, parts.lastfmKey, parts.lastfmSecret)
    val scanner: LibraryScanner = parts.scanner(db.dao(), parts.art)
    val repository = Repository(
        dao = db.dao(),
        prefs = prefs,
        scanner = scanner,
        art = parts.art,
        tags = parts.tags,
        covers = CoverSearch(api),
        lastFm = LastFm(api, PrefsLastFmAccount(prefs)),
        appScope = appScope,
        scheduleScrobbleRetry = parts.scheduleScrobbleRetry,
    )
    val lyrics = LyricsRepository(db.lyrics(), prefs, scanner, parts.tags, LrcLib(api))
    val deezer = Deezer(api)
    val discovery = Discovery(parts.files, db.dao(), deezer, appScope)

    abstract val player: PlayerController
    abstract val soulseek: SoulseekService
}
