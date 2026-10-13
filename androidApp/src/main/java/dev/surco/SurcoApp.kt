package dev.surco

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.surco.app.PlatformParts
import dev.surco.app.SurcoCore
import dev.surco.data.Scanner
import dev.surco.data.SurcoDatabase
import dev.surco.data.create
import dev.surco.platform.AndroidArtStore
import dev.surco.platform.AndroidFiles
import dev.surco.platform.AndroidStore
import dev.surco.playback.PlayerConnection
import dev.surco.slsk.SoulseekManager
import dev.surco.tags.TagWriter

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

/** Lo común (biblioteca, red, letras, recomendaciones…) más el reproductor y Soulseek de Android. */
class AppContainer(app: Application) : SurcoCore(
    PlatformParts(
        db = SurcoDatabase.create(app),
        store = AndroidStore(app),
        files = AndroidFiles(app),
        art = AndroidArtStore(app),
        tags = TagWriter(app),
        scanner = { dao, art -> Scanner(app, dao, art) },
        userAgent = "Surco/${BuildConfig.VERSION_NAME} (https://github.com/reallynormalchillguy/surco)",
        lastfmKey = BuildConfig.LASTFM_API_KEY,
        lastfmSecret = BuildConfig.LASTFM_SECRET,
        scheduleScrobbleRetry = { dev.surco.playback.ScrobbleWorker.schedule(app) },
    )
) {
    override val player = PlayerConnection(app)
    override val soulseek = SoulseekManager(app, prefs, repository)
}
