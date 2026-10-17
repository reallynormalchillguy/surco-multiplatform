package dev.surco.data

import dev.surco.i18n.I18n
import dev.surco.i18n.Lang
import dev.surco.ui.theme.Palette
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

enum class ScrobbleMode { BUILTIN, EXTERNAL, OFF }

data class Settings(
    val treeUri: String? = null,
    val lastfmKey: String = "",
    val lastfmSecret: String = "",
    val lastfmSession: String = "",
    val lastfmUser: String = "",
    val scrobbleMode: ScrobbleMode = ScrobbleMode.BUILTIN,
    val embedCovers: Boolean = true,
    val autoCoversAfterScan: Boolean = false,
    val artistImages: Boolean = true,
    val libraryGrid: Boolean = false,
    val librarySortRecent: Boolean = true,
    val slskUser: String = "",
    val slskPass: String = "",
    /** Carpeta de descargas de Soulseek (null = la carpeta de música). */
    val slskDir: String? = null,
    /** Guardar cada descarga en una subcarpeta con el nombre de la carpeta original. */
    val slskSubfolders: Boolean = true,
    /** Filtro de formato en las búsquedas de Soulseek: all, lossless, flac, mp3. */
    val slskFormat: String = "all",
    /** Idioma de la interfaz: "en" o "es". */
    val language: String = "en",
    /** Recomendaciones de música nueva en Inicio (Deezer). */
    val discover: Boolean = true,
    /** Reescanear solo cuando aparecen archivos o carpetas nuevos en la carpeta de música. */
    val autoScan: Boolean = true,
    /** Paleta de colores de la interfaz ("" = la original). Ver [dev.surco.ui.theme.Palette.encode]. */
    val palette: String = "",
    /** Negro puro para pantallas OLED: "" = automático (según la pantalla), "on" o "off". */
    val oled: String = "",
    /** Tema con imagen: "" (ninguno) o "floral". */
    val theme: String = "",
    /** Clave propia de la API de Spotify (opcional): sólo para dar enlaces exactos al compartir. */
    val spotifyId: String = "",
    val spotifySecret: String = "",
) {
    val spotifyReady get() = spotifyId.isNotBlank() && spotifySecret.isNotBlank()
    val lastfmReady get() = lastfmKey.isNotBlank() && lastfmSecret.isNotBlank()
    val lastfmLoggedIn get() = lastfmReady && lastfmSession.isNotBlank()
}

/** Almacén clave-valor de la plataforma (SharedPreferences en Android, NSUserDefaults en iOS). */
interface KeyValueStore {
    fun string(key: String): String?
    fun bool(key: String, default: Boolean): Boolean
    /** Guarda todos los valores (un null borra la clave). */
    fun save(strings: Map<String, String?>, bools: Map<String, Boolean>)
}

/** Ajustes de la app, observables. [defaultLastfmKey]/[defaultLastfmSecret]: claves incluidas en la compilación. */
class Prefs(
    private val store: KeyValueStore,
    private val defaultLastfmKey: String = "",
    private val defaultLastfmSecret: String = "",
) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun str(key: String, default: String) = store.string(key) ?: default

    private fun read() = Settings(
        treeUri = store.string("treeUri"),
        lastfmKey = store.string("lfmKey")?.takeIf { it.isNotBlank() } ?: defaultLastfmKey,
        lastfmSecret = store.string("lfmSecret")?.takeIf { it.isNotBlank() } ?: defaultLastfmSecret,
        lastfmSession = str("lfmSession", ""),
        lastfmUser = str("lfmUser", ""),
        scrobbleMode = ScrobbleMode.entries.firstOrNull { it.name == store.string("scrobbleMode") } ?: ScrobbleMode.BUILTIN,
        embedCovers = store.bool("embedCovers", true),
        autoCoversAfterScan = store.bool("autoCovers", false),
        artistImages = store.bool("artistImages", true),
        libraryGrid = store.bool("libGrid", false),
        librarySortRecent = store.bool("libSortRecent", true),
        slskUser = str("slskUser", ""),
        slskPass = str("slskPass", ""),
        slskDir = store.string("slskDir"),
        slskSubfolders = store.bool("slskSubfolders", true),
        slskFormat = str("slskFormat", "all"),
        language = str("language", "en"),
        discover = store.bool("discover", true),
        autoScan = store.bool("autoScan", true),
        palette = str("palette", ""),
        oled = str("oled", ""),
        theme = str("theme", ""),
        spotifyId = str("spotifyId", ""),
        spotifySecret = str("spotifySecret", ""),
    )

    fun update(block: (Settings) -> Settings) {
        val s = _settings.updateAndGet(block)
        store.save(
            strings = mapOf(
                "treeUri" to s.treeUri,
                "lfmKey" to s.lastfmKey,
                "lfmSecret" to s.lastfmSecret,
                "lfmSession" to s.lastfmSession,
                "lfmUser" to s.lastfmUser,
                "scrobbleMode" to s.scrobbleMode.name,
                "slskUser" to s.slskUser,
                "slskPass" to s.slskPass,
                "slskDir" to s.slskDir,
                "slskFormat" to s.slskFormat,
                "language" to s.language,
                "palette" to s.palette,
                "oled" to s.oled,
                "theme" to s.theme,
                "spotifyId" to s.spotifyId,
                "spotifySecret" to s.spotifySecret,
            ),
            bools = mapOf(
                "embedCovers" to s.embedCovers,
                "autoCovers" to s.autoCoversAfterScan,
                "artistImages" to s.artistImages,
                "libGrid" to s.libraryGrid,
                "libSortRecent" to s.librarySortRecent,
                "slskSubfolders" to s.slskSubfolders,
                "discover" to s.discover,
                "autoScan" to s.autoScan,
            ),
        )
        applyLanguage(s)
    }

    private fun applyLanguage(s: Settings) {
        I18n.lang = if (s.language == "es") Lang.ES else Lang.EN
        val p = effectivePalette(Palette.decode(s.palette), s)
        if (SurcoColors.palette != p) SurcoColors.palette = p
        val backdrop = s.theme.takeIf { it == "floral" }
        if (SurcoColors.backdrop != backdrop) SurcoColors.backdrop = backdrop
    }

    companion object {
        /** La pantalla del móvil parece OLED (lo detecta la plataforma al arrancar). */
        var deviceOled = false

        fun oledActive(s: Settings) = when (s.oled) { "on" -> true; "off" -> false; else -> deviceOled }

        /** La paleta tal como se ve: con negro puro si el modo OLED está activo. */
        fun effectivePalette(base: Palette, s: Settings) = when {
            s.theme == "floral" -> Palette.Floral
            oledActive(s) -> base.oled()
            else -> base
        }
    }

    init {
        applyLanguage(current)
    }
}

/** Almacén en memoria (pruebas y vistas previas). */
class MemoryStore : KeyValueStore {
    private val strings = HashMap<String, String>()
    private val bools = HashMap<String, Boolean>()
    override fun string(key: String) = strings[key]
    override fun bool(key: String, default: Boolean) = bools[key] ?: default
    override fun save(strings: Map<String, String?>, bools: Map<String, Boolean>) {
        strings.forEach { (k, v) -> if (v == null) this.strings.remove(k) else this.strings[k] = v }
        this.bools.putAll(bools)
    }
}
