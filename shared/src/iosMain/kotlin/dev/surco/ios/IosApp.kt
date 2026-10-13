package dev.surco.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.surco.app.PlatformParts
import dev.surco.app.SurcoCore
import dev.surco.data.IosStore
import dev.surco.data.SurcoDatabase
import dev.surco.data.create
import dev.surco.slsk.SlskFile
import dev.surco.slsk.SlskState
import dev.surco.slsk.SoulseekEngine
import dev.surco.slsk.SoulseekService
import dev.surco.slsk.Transfer
import dev.surco.slsk.UserResult
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

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
    override val soulseek: SoulseekService = SoulseekNotYet()
}

private fun bundleString(key: String): String = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
private fun appVersion(): String = bundleString("CFBundleShortVersionString").ifEmpty { "dev" }

/** Punto de entrada para Swift: `MainViewControllerKt.MainViewController()`. */
object IosApp {
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

fun MainViewController(): UIViewController {
    IosDemo.seedIfRequested(IosApp.container)
    if (IosDemo.openPlayer) IosApp.openPlayerSignal = 1
    val route = IosDemo.route
    return ComposeUIViewController {
        SurcoTheme { SurcoRoot(container = IosApp.container, openPlayerSignal = IosApp.openPlayerSignal, initialRoute = route) }
    }
}

/** Soulseek llega a iOS en el siguiente paso (cliente común con sockets de Ktor). */
private class SoulseekNotYet : SoulseekService {
    override val client: SoulseekEngine = object : SoulseekEngine {
        override val state = MutableStateFlow<SlskState>(SlskState.Failed("Soulseek llegará pronto a iOS"))
        override val results = MutableStateFlow<List<UserResult>>(emptyList())
        override val searching = MutableStateFlow(false)
        override val transfers = MutableStateFlow<List<Transfer>>(emptyList())
        override fun search(query: String) {}
        override fun clearResults() {}
        override fun cancel(id: Long) {}
        override fun cancelAll(which: (Transfer) -> Boolean) = 0
        override fun cancelQueued() = 0
        override fun cancelDownloading() = 0
        override fun clearFinished() {}
        override fun debugSet(state: SlskState, results: List<UserResult>, transfers: List<Transfer>) {
            this.state.value = state; this.results.value = results; this.transfers.value = transfers
        }
    }
    override val hasAccount = false
    override val targetTree: String? = null
    override fun ensureConnected() {}
    override fun login(user: String, pass: String) {}
    override fun logout() {}
    override fun download(files: List<SlskFile>) {}
    override fun retry(id: Long) {}
    override fun retryFailed() = 0
}
