package dev.surco.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.surco.app.PlatformParts
import dev.surco.app.SurcoCore
import dev.surco.data.IosStore
import dev.surco.data.SurcoDatabase
import dev.surco.data.create
import dev.surco.slsk.SoulseekService
import platform.Foundation.NSBundle

/** La app de iOS: lo común más el reproductor, el escáner y las etiquetas nativos. */
class IosContainer : SurcoCore(
    PlatformParts(
        db = SurcoDatabase.create(),
        store = IosStore(),
        files = IosFiles(),
        art = IosArtStore(),
        tags = IosTagEditor(),
        scanner = { dao, art -> IosScanner(dao, art) },
        userAgent = "Surco/${appVersion()} iOS (https://github.com/reallynormalchillguy/surco)",
        appVersion = appVersion(),
        lastfmKey = bundleString("SurcoLastfmKey"),
        lastfmSecret = bundleString("SurcoLastfmSecret"),
    )
) {
    override val player = IosPlayer(appScope, repository)
    override val soulseek: SoulseekService = IosSoulseek(prefs, repository, appScope)
}

private fun bundleString(key: String): String = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
private fun appVersion(): String = bundleString("CFBundleShortVersionString").ifEmpty { "dev" }

/** Estado global de la app de iOS (Swift lo usa como `IosApp.shared`). */
object IosApp {
    val versionName: String get() = appVersion()
    val container: IosContainer by lazy { IosContainer() }
    internal var openPlayerSignal by mutableIntStateOf(0)

    private var started = false

    /** Al volver a primer plano: enviar scrobbles pendientes; la primera vez, además, reescanear. */
    fun onForeground() {
        container.repository.flushScrobblesAsync()
        if (!started) {
            started = true
            if (!IosDemo.enabled) container.repository.rescan()
        }
    }
}

