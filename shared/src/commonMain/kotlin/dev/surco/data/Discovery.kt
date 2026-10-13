package dev.surco.data

import dev.surco.net.Deezer
import dev.surco.net.arr
import dev.surco.net.int
import dev.surco.net.long
import dev.surco.net.obj
import dev.surco.net.objects
import dev.surco.net.parseJson
import dev.surco.net.str
import dev.surco.util.IoDispatcher
import dev.surco.util.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    private val files: AppFiles,
    private val dao: LibraryDao,
    private val deezer: Deezer,
    private val scope: CoroutineScope,
) {
    private val _sections = MutableStateFlow<List<DiscoverSection>>(emptyList())
    val sections: StateFlow<List<DiscoverSection>> = _sections.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val mutex = Mutex()
    private var computedAt = 0L

    init {
        scope.launch(IoDispatcher) { load() }
    }

    fun refreshIfStale(force: Boolean = false) {
        if (!force && nowMillis() - computedAt < MAX_AGE_MS && _sections.value.isNotEmpty()) return
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                _loading.value = true
                val result = runCatching { compute() }.getOrNull()
                if (!result.isNullOrEmpty()) {
                    _sections.value = result
                    computedAt = nowMillis()
                    save()
                }
            } finally {
                _loading.value = false
                mutex.unlock()
            }
        }
    }

    private suspend fun compute(): List<DiscoverSection> = withContext(IoDispatcher) {
        val libraryArtists = dao.artists().first()
        if (libraryArtists.isEmpty()) return@withContext emptyList()
        val owned = libraryArtists.map { norm(it.name) }.toSet()
        val stats = dao.artistPlayStats().first().filter { it.key != UNKNOWN_ARTIST }
        val seeds = (stats.sortedByDescending { it.plays }.map { it.key } +
            libraryArtists.sortedByDescending { it.trackCount }.map { it.name })
            .filter { it != UNKNOWN_ARTIST }
            .distinct()
            .take(SEEDS)

        val rnd = Random(nowMillis() / 86_400_000L)
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
        val json = buildJsonObject {
            put("at", computedAt)
            put("sections", buildJsonArray {
                _sections.value.forEach { s ->
                    add(buildJsonObject {
                        put("seed", s.seed)
                        put("seedImage", s.seedImage)
                        put("albums", buildJsonArray {
                            s.albums.forEach { a ->
                                add(buildJsonObject {
                                    put("id", a.id); put("title", a.title); put("artist", a.artist)
                                    put("cover", a.cover); put("year", a.year)
                                })
                            }
                        })
                    })
                }
            })
        }
        files.writeText(FILE, json.toString())
    }

    private fun load() = runCatching {
        val o = parseJson(files.readText(FILE) ?: return@runCatching).obj() ?: return@runCatching
        computedAt = o.long("at")
        _sections.value = o.arr("sections")?.objects().orEmpty().map { s ->
            DiscoverSection(
                seed = s.str("seed"),
                seedImage = s.str("seedImage").takeIf { it.isNotBlank() && it != "null" },
                albums = s.arr("albums")?.objects().orEmpty().map { a ->
                    DiscoverAlbum(
                        a.long("id"), a.str("title"), a.str("artist"),
                        a.str("cover").takeIf { it.isNotBlank() && it != "null" }, a.int("year"),
                    )
                },
            )
        }
    }

    /** Sólo para las capturas de pantalla automáticas. */
    fun debugSet(list: List<DiscoverSection>) {
        _sections.value = list
        computedAt = nowMillis()
    }

    companion object {
        private const val FILE = "discover.json"
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
