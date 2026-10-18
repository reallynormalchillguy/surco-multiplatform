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
    /** El icono de la app toma el color de acento. */
    val iconFollowsAccent: Boolean = true,
    /** Paleta de colores de la interfaz ("" = la original). Ver [dev.surco.ui.theme.Palette.encode]. */
    val palette: String = "",
    /** Negro puro para pantallas OLED: "on" si lo activas; "" u "off", apagado. */
    val oled: String = "",
    /** Clave propia de la API de Spotify (opcional): sólo para dar enlaces exactos al compartir. */
    val spotifyId: String = "",
    val spotifySecret: String = "",
    /** Periodo con el que se abren las listas top completas (nombre de StatsPeriod). */
    val statsTopArtists: String = "MONTH",
    val statsTopAlbums: String = "MONTH",
    val statsTopTracks: String = "MONTH",
    /** Tamaño de toda la interfaz (1 = normal; menos, todo más pequeño; más, todo más grande). */
    val uiScale: Float = 1f,
    // ---- Audio para audiófilos (todo apagado por defecto: así el sonido sale tal cual)
    /** Normalizar el volumen (ReplayGain si lo trae el archivo; si no, lo que mide Surco al escucharla). */
    val normalize: Boolean = false,
    /** Normalizar por álbum (respeta las diferencias de volumen dentro de un disco) en vez de por canción. */
    val normalizeAlbum: Boolean = false,
    /** Volumen objetivo en LUFS (-14 como Spotify; -18 como ReplayGain; -11 más fuerte). */
    val normalizeTarget: Int = -14,
    /** Ecualizador paramétrico encendido. */
    val eqEnabled: Boolean = false,
    /** Bandas del ecualizador (ver [dev.surco.audio.EqSettings.encode]). */
    val eq: String = "",
    /** Fundido encadenado entre canciones. */
    val crossfade: Boolean = false,
    val crossfadeSec: Int = 6,
    /** Fundir también entre canciones seguidas del mismo álbum (los discos sin pausas suenan mejor sin él). */
    val crossfadeAlbums: Boolean = false,
    /** La portada del reproductor convertida en espectrograma (5 toques en la portada). */
    val spectrogram: Boolean = false,
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
        iconFollowsAccent = store.bool("iconFollowsAccent", true),
        palette = str("palette", ""),
        oled = str("oled", ""),
        spotifyId = str("spotifyId", ""),
        spotifySecret = str("spotifySecret", ""),
        statsTopArtists = str("statsTopArtists", "MONTH"),
        statsTopAlbums = str("statsTopAlbums", "MONTH"),
        statsTopTracks = str("statsTopTracks", "MONTH"),
        uiScale = str("uiScale", "1").toFloatOrNull()?.coerceIn(0.7f, 1.4f) ?: 1f,
        normalize = store.bool("normalize", false),
        normalizeAlbum = store.bool("normAlbum", false),
        normalizeTarget = str("normTarget", "-14").toIntOrNull() ?: -14,
        eqEnabled = store.bool("eqEnabled", false),
        eq = str("eq", ""),
        crossfade = store.bool("crossfade", false),
        crossfadeSec = str("crossfadeSec", "6").toIntOrNull()?.coerceIn(1, 12) ?: 6,
        crossfadeAlbums = store.bool("crossfadeAlbums", false),
        spectrogram = store.bool("spectrogram", false),
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
                "spotifyId" to s.spotifyId,
                "spotifySecret" to s.spotifySecret,
                "statsTopArtists" to s.statsTopArtists,
                "statsTopAlbums" to s.statsTopAlbums,
                "statsTopTracks" to s.statsTopTracks,
                "uiScale" to s.uiScale.toString(),
                "normTarget" to s.normalizeTarget.toString(),
                "eq" to s.eq,
                "crossfadeSec" to s.crossfadeSec.toString(),
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
                "iconFollowsAccent" to s.iconFollowsAccent,
                "normalize" to s.normalize,
                "normAlbum" to s.normalizeAlbum,
                "eqEnabled" to s.eqEnabled,
                "crossfade" to s.crossfade,
                "crossfadeAlbums" to s.crossfadeAlbums,
                "spectrogram" to s.spectrogram,
            ),
        )
        applyLanguage(s)
    }

    private fun applyLanguage(s: Settings) {
        I18n.lang = if (s.language == "es") Lang.ES else Lang.EN
        val p = effectivePalette(Palette.decode(s.palette), s)
        if (SurcoColors.palette != p) SurcoColors.palette = p
    }

    companion object {
        /** La pantalla del móvil parece OLED (lo detecta la plataforma al arrancar). */
        var deviceOled = false

        fun oledActive(s: Settings) = s.oled == "on" // sólo si lo activas tú (la detección sólo lo sugiere)

        /** La paleta tal como se ve: con negro puro si el modo OLED está activo. */
        fun effectivePalette(base: Palette, s: Settings) = if (oledActive(s)) base.oled() else base
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
