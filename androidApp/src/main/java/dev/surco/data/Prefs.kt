package dev.surco.data

import android.content.Context
import dev.surco.BuildConfig
import dev.surco.i18n.I18n
import dev.surco.i18n.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    val lastfmReady get() = lastfmKey.isNotBlank() && lastfmSecret.isNotBlank()
    val lastfmLoggedIn get() = lastfmReady && lastfmSession.isNotBlank()
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("surco", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun read() = Settings(
        treeUri = sp.getString("treeUri", null),
        lastfmKey = sp.getString("lfmKey", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.LASTFM_API_KEY,
        lastfmSecret = sp.getString("lfmSecret", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.LASTFM_SECRET,
        lastfmSession = sp.getString("lfmSession", "") ?: "",
        lastfmUser = sp.getString("lfmUser", "") ?: "",
        scrobbleMode = runCatching { ScrobbleMode.valueOf(sp.getString("scrobbleMode", null) ?: "BUILTIN") }
            .getOrDefault(ScrobbleMode.BUILTIN),
        embedCovers = sp.getBoolean("embedCovers", true),
        autoCoversAfterScan = sp.getBoolean("autoCovers", false),
        artistImages = sp.getBoolean("artistImages", true),
        libraryGrid = sp.getBoolean("libGrid", false),
        librarySortRecent = sp.getBoolean("libSortRecent", true),
        slskUser = sp.getString("slskUser", "") ?: "",
        slskPass = sp.getString("slskPass", "") ?: "",
        slskDir = sp.getString("slskDir", null),
        slskSubfolders = sp.getBoolean("slskSubfolders", true),
        slskFormat = sp.getString("slskFormat", "all") ?: "all",
        language = sp.getString("language", "en") ?: "en",
        discover = sp.getBoolean("discover", true),
    )

    @Synchronized
    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        sp.edit()
            .putString("treeUri", s.treeUri)
            .putString("lfmKey", s.lastfmKey)
            .putString("lfmSecret", s.lastfmSecret)
            .putString("lfmSession", s.lastfmSession)
            .putString("lfmUser", s.lastfmUser)
            .putString("scrobbleMode", s.scrobbleMode.name)
            .putBoolean("embedCovers", s.embedCovers)
            .putBoolean("autoCovers", s.autoCoversAfterScan)
            .putBoolean("artistImages", s.artistImages)
            .putBoolean("libGrid", s.libraryGrid)
            .putBoolean("libSortRecent", s.librarySortRecent)
            .putString("slskUser", s.slskUser)
            .putString("slskPass", s.slskPass)
            .putString("slskDir", s.slskDir)
            .putBoolean("slskSubfolders", s.slskSubfolders)
            .putString("slskFormat", s.slskFormat)
            .putString("language", s.language)
            .putBoolean("discover", s.discover)
            .apply()
        _settings.value = s
        applyLanguage(s)
    }

    private fun applyLanguage(s: Settings) {
        I18n.lang = if (s.language == "es") Lang.ES else Lang.EN
    }

    init {
        applyLanguage(current)
    }
}
