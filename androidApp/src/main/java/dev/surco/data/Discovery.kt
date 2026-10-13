package dev.surco.data

import android.content.Context
import dev.surco.net.Deezer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

data class DiscoverAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val cover: String?,
    val year: Int,
)

/** Una fila de recomendaciones: «Porque escuchas [seed]». */
data class DiscoverSection(
    val seed: String,
    val seedImage: String?,
    val albums: List<DiscoverAlbum>,
)

/**
 * Recomienda música que NO tienes, a partir de lo que más escuchas:
 * artistas parecidos (Deezer) que no están en tu biblioteca → su álbum más popular.
 * Se recalcula como mucho una vez al día (y cambia un poco cada día).
 */
class Discovery(
    context: Context,
    private val dao: LibraryDao,
    private val deezer: Deezer,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "discover.json")
    private val _sections = MutableStateFlow<List<DiscoverSection>>(emptyList())
    val sections: StateFlow<List<DiscoverSection>> = _sections.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val mutex = Mutex()
    private var computedAt = 0L

    init {
        scope.launch(Dispatchers.IO) { load() }
    }

    fun refreshIfStale(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - computedAt < MAX_AGE_MS && _sections.value.isNotEmpty()) return
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                _loading.value = true
                val result = runCatching { compute() }.getOrNull()
                if (!result.isNullOrEmpty()) {
                    _sections.value = result
                    computedAt = System.currentTimeMillis()
                    save()
                }
            } finally {
                _loading.value = false
                mutex.unlock()
            }
        }
    }

    private suspend fun compute(): List<DiscoverSection> = withContext(Dispatchers.IO) {
        val libraryArtists = dao.artists().first()
        if (libraryArtists.isEmpty()) return@withContext emptyList()
        val owned = libraryArtists.map { norm(it.name) }.toSet()
        val stats = dao.artistPlayStats().first().filter { it.key != UNKNOWN_ARTIST }
        val seeds = (stats.sortedByDescending { it.plays }.map { it.key } +
            libraryArtists.sortedByDescending { it.trackCount }.map { it.name })
            .filter { it != UNKNOWN_ARTIST }
            .distinct()
            .take(SEEDS)

        val rnd = Random(LocalDate.now().toEpochDay())
        val used = HashSet<String>()
        val out = ArrayList<DiscoverSection>()
        for (seed in seeds) {
            val artist = deezer.findArtist(seed) ?: continue
            delay(120)
            val related = deezer.related(artist.id)
                .filter { norm(it.name) !in owned && norm(it.name) !in used }
                .take(15)
                .shuffled(rnd)
                .take(PER_SECTION)
            val albums = ArrayList<DiscoverAlbum>()
            for (r in related) {
                delay(120)
                val best = deezer.albums(r.id, r.name)
                    .filter { it.type == "album" }
                    .maxByOrNull { it.fans }
                    ?: continue
                albums += DiscoverAlbum(best.id, best.title, r.name, best.cover, best.year)
                used += norm(r.name)
            }
            if (albums.size >= 3) out += DiscoverSection(seed, artist.picture, albums)
            if (out.size >= MAX_SECTIONS) break
        }
        out
    }

    private fun norm(s: String) = s.lowercase().trim()

    private fun save() = runCatching {
        val arr = JSONArray()
        _sections.value.forEach { s ->
            arr.put(JSONObject().apply {
                put("seed", s.seed); put("seedImage", s.seedImage)
                put("albums", JSONArray().apply {
                    s.albums.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id); put("title", a.title); put("artist", a.artist)
                            put("cover", a.cover); put("year", a.year)
                        })
                    }
                })
            })
        }
        file.writeText(JSONObject().put("at", computedAt).put("sections", arr).toString())
    }

    private fun load() = runCatching {
        if (!file.exists()) return@runCatching
        val o = JSONObject(file.readText())
        computedAt = o.optLong("at")
        val arr = o.getJSONArray("sections")
        _sections.value = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            val al = s.getJSONArray("albums")
            DiscoverSection(
                seed = s.getString("seed"),
                seedImage = s.optString("seedImage").takeIf { it.isNotBlank() && it != "null" },
                albums = (0 until al.length()).map { j ->
                    val a = al.getJSONObject(j)
                    DiscoverAlbum(
                        a.getLong("id"), a.getString("title"), a.getString("artist"),
                        a.optString("cover").takeIf { it.isNotBlank() && it != "null" }, a.optInt("year"),
                    )
                },
            )
        }
    }

    /** Sólo para las capturas de pantalla automáticas. */
    fun debugSet(list: List<DiscoverSection>) {
        _sections.value = list
        computedAt = System.currentTimeMillis()
    }

    companion object {
        private const val MAX_AGE_MS = 20L * 3600 * 1000
        private const val SEEDS = 6
        private const val MAX_SECTIONS = 3
        private const val PER_SECTION = 8

        /** Búsqueda limpia para Soulseek: sin «(Deluxe Edition)», «[Remastered]»… */
        fun soulseekQuery(artist: String, title: String): String {
            val clean = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifBlank { title }
            return "$artist $clean".replace(Regex("""[^\p{L}\p{N}\s'&]"""), " ").replace(Regex("""\s+"""), " ").trim()
        }
    }
}
