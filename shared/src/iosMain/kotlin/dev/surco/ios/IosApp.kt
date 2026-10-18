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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** La app de iOS: lo común más el reproductor, el escáner y las etiquetas nativos. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
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
    override val player = IosPlayer(appScope, repository, sleepTimer)
    override val cache = IosCacheStore()
    override val preview: dev.surco.playback.PreviewPlayer = IosPreviewPlayer(player)
    override val soulseek: SoulseekService = IosSoulseek(prefs, repository, appScope)

    init {
        startBackgroundWork()
        // Icono a juego con el color de acento (no en las capturas automáticas: el aviso de iOS taparía la pantalla).
        if (!IosDemo.enabled) {
            appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                prefs.settings
                    .map { s ->
                        if (!s.iconFollowsAccent) "Green"
                        else dev.surco.ui.theme.AppIconColors.nearest(dev.surco.data.Prefs.effectivePalette(dev.surco.ui.theme.Palette.decode(s.palette), s).accent)
                    }
                    .distinctUntilChanged()
                    .debounce(1500)
                    .collect { IosAppIcon.apply(it) }
            }
        }
    }
}

private fun bundleString(key: String): String = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
private fun appVersion(): String = bundleString("CFBundleShortVersionString").ifEmpty { "dev" }

/** Estado global de la app de iOS (Swift lo usa como `IosApp.shared`). */
object IosApp {
    val versionName: String get() = appVersion()
    val container: IosContainer by lazy {
        // Negro puro automático en pantallas OLED (antes de crear los ajustes, que aplican la paleta).
        dev.surco.data.Prefs.deviceOled = IosDevice.isOled()
        IosContainer()
    }
    internal var openPlayerSignal by mutableIntStateOf(0)

    private var started = false

    /** Al volver a primer plano: enviar scrobbles pendientes; la primera vez, además, reescanear. */
    /** Enlaces «surco://…» (p. ej. la vuelta de Last.fm tras permitir el acceso). */
    fun handleUrl(url: String) {
        if (url.startsWith("surco://lastfm-auth")) {
            val token = url.substringAfter("token=", "").substringBefore('&')
            dev.surco.ui.Overlays.lastfmCallback = token.ifBlank { dev.surco.ui.Overlays.lastfmPending ?: "" }
        }
    }

    fun onForeground() {
        container.repository.flushScrobblesAsync()
        if (IosDemo.enabled) return
        if (!started) {
            started = true
            container.repository.rescan()
        }
        // Escaneo automático: al volver a la app y, mientras está abierta, cada poco tiempo
        // (iOS no avisa a las apps cuando cambia una carpeta de Archivos).
        container.autoScan.onForeground()
        container.autoScan.startPolling()
    }

    /** La app pasa a segundo plano: se deja de revisar la carpeta. */
    fun onBackground() {
        container.autoScan.stopPolling()
    }
}

