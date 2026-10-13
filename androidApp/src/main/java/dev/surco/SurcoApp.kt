package dev.surco

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.surco.data.ArtStore
import dev.surco.data.Discovery
import dev.surco.data.LyricsRepository
import dev.surco.net.Deezer
import dev.surco.net.LrcLib
import dev.surco.data.Prefs
import dev.surco.data.Repository
import dev.surco.data.Scanner
import dev.surco.data.SurcoDatabase
import dev.surco.net.CoverSearch
import dev.surco.net.LastFm
import dev.surco.playback.PlayerConnection
import dev.surco.slsk.SoulseekManager
import dev.surco.tags.TagWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SurcoApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).crossfade(true).build()
}

/** Inyección de dependencias manual: todo lo compartido vive aquí. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Cliente común (Ktor) para las APIs públicas: Deezer, iTunes, MusicBrainz, LRCLIB y Last.fm. */
    val api = dev.surco.net.surcoHttpClient("Surco/${BuildConfig.VERSION_NAME} (https://github.com/reallynormalchillguy/surco)")
    val db = SurcoDatabase.create(app)
    val prefs = Prefs(app)
    private val art = ArtStore(app)
    private val tagWriter = TagWriter(app)
    val scanner = Scanner(app, db.dao(), art)
    val repository = Repository(
        dao = db.dao(),
        prefs = prefs,
        scanner = scanner,
        art = art,
        tags = tagWriter,
        covers = CoverSearch(api),
        lastFm = LastFm(api, dev.surco.net.PrefsLastFmAccount(prefs)),
        appScope = appScope,
        scheduleScrobbleRetry = { dev.surco.playback.ScrobbleWorker.schedule(app) },
    )
    val player = PlayerConnection(app)
    val lyrics = LyricsRepository(app, db.lyrics(), prefs, tagWriter, LrcLib(api))
    val soulseek = SoulseekManager(app, prefs, repository)
    val deezer = Deezer(api)
    val discovery = Discovery(app, db.dao(), deezer, appScope)
}
