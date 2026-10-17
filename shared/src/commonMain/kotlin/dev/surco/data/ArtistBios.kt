package dev.surco.data

import dev.surco.net.BioSource
import dev.surco.net.BioText
import dev.surco.net.JsonParser
import dev.surco.net.obj
import dev.surco.net.parseJson
import dev.surco.net.str
import dev.surco.net.long
import dev.surco.util.IoDispatcher
import dev.surco.util.Md5
import dev.surco.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Biografías de los artistas de la biblioteca, guardadas en el móvil (una por artista e idioma).
 * Se buscan solas tras cada escaneo para los artistas nuevos, y al abrir un artista si aún no la tiene.
 */
class ArtistBios(
    private val files: AppFiles,
    private val source: BioSource,
    private val prefs: Prefs,
    private val dao: LibraryDao,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    private val _version = MutableStateFlow(0)
    /** Cambia cada vez que se guarda una biografía (para que la pantalla del artista se entere). */
    val version: StateFlow<Int> = _version

    private fun lang() = if (prefs.current.language == "es") "es" else "en"
    private fun name(artist: String, lang: String) = "bio_${Md5.hex(artist.trim().lowercase())}_$lang.json"

    /** Biografía escrita (o borrada) a mano: vale para todos los idiomas y nunca se sustituye sola. */
    private fun manualName(artist: String) = "bio_${Md5.hex(artist.trim().lowercase())}_manual.json"

    private fun manual(artist: String): BioText? {
        val o = files.readText(manualName(artist))?.let { runCatching { parseJson(it).obj() }.getOrNull() } ?: return null
        val src = o.str("source")
        if (src != MANUAL && src != DELETED) return null // «descartada» al volver a buscar en internet
        return BioText(o.str("text"), src, null, lang())
    }

    private fun writeManual(artist: String, text: String, source: String) {
        val json = buildJsonObject { put("text", JsonPrimitive(text)); put("source", JsonPrimitive(source)) }
        files.writeText(manualName(artist), JsonParser.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json))
    }

    /** Guarda una biografía escrita por el usuario (vacía = «sin biografía», y no se vuelve a buscar). */
    suspend fun setManual(artist: String, text: String) {
        val t = text.trim()
        kotlinx.coroutines.withContext(IoDispatcher) { writeManual(artist, t, if (t.isEmpty()) DELETED else MANUAL) }
        _version.value++
    }

    /** Vuelve a buscarla en internet (descarta la escrita a mano y la guardada). Devuelve false si no hay red. */
    suspend fun refresh(artist: String): Boolean = mutex.withLock {
        val lang = lang()
        val bio = when (val o = source.fetch(artist, lang)) {
            is BioSource.Outcome.Found -> o.bio
            BioSource.Outcome.NotFound -> BioText("", "", null, lang)
            BioSource.Outcome.Failed -> return@withLock false
        }
        kotlinx.coroutines.withContext(IoDispatcher) {
            if (files.readText(manualName(artist)) != null) writeManual(artist, "", "")
            put(artist, bio)
        }
        true
    }

    /** Biografía guardada (texto vacío = se buscó y no hay, o se borró), o null si aún no se ha buscado. */
    fun cached(artist: String, lang: String = lang()): BioText? {
        manual(artist)?.let { return it }
        val o = files.readText(name(artist, lang))?.let { parseJson(it).obj() } ?: return null
        if (o.str("text").isBlank() && nowMillis() - o.long("at") > RETRY_MS) return null
        return BioText(o.str("text"), o.str("source"), o.str("url").ifBlank { null }, o.str("lang", lang))
    }

    suspend fun get(artist: String): BioText? {
        if (artist == UNKNOWN_ARTIST || artist.isBlank()) return null
        val lang = lang()
        cached(artist, lang)?.let { return it.takeIf { b -> b.text.isNotBlank() } }
        return mutex.withLock { fetchAndStore(artist, lang) }
    }

    private suspend fun fetchAndStore(artist: String, lang: String): BioText? {
        cached(artist, lang)?.let { return it.takeIf { b -> b.text.isNotBlank() } }
        val bio = when (val o = source.fetch(artist, lang)) {
            is BioSource.Outcome.Found -> o.bio
            BioSource.Outcome.NotFound -> BioText("", "", null, lang)
            BioSource.Outcome.Failed -> return null // sin red: se reintentará, no se guarda nada
        }
        put(artist, bio)
        return bio.takeIf { it.text.isNotBlank() }
    }

    private fun put(artist: String, bio: BioText) {
        val json = buildJsonObject {
            put("text", JsonPrimitive(bio.text)); put("source", JsonPrimitive(bio.source)); put("url", JsonPrimitive(bio.url ?: ""))
            put("lang", JsonPrimitive(bio.lang)); put("at", JsonPrimitive(nowMillis()))
        }
        files.writeText(name(artist, bio.lang), JsonParser.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json))
        _version.value++
    }

    /** Sólo para las capturas automáticas (CI): guarda una biografía sin red. */
    fun debugPut(artist: String, bio: BioText) = put(artist, bio)

    /** Busca, en segundo plano, las biografías que faltan (p. ej. tras añadir música nueva). */
    fun fetchMissing() {
        if (job?.isActive == true) return
        job = scope.launch(IoDispatcher) {
            val lang = lang()
            val names = runCatching { dao.artistNames() }.getOrDefault(emptyList())
                .filter { it.isNotBlank() && it != UNKNOWN_ARTIST && cached(it, lang) == null }
            for (n in names) {
                mutex.withLock { runCatching { fetchAndStore(n, lang) } }
                delay(1200) // MusicBrainz: una consulta por segundo
            }
        }
    }

    companion object {
        const val MANUAL = "manual"
        const val DELETED = "deleted"
        private const val RETRY_MS = 30L * 24 * 3600 * 1000
    }
}
