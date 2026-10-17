package dev.surco.data

import dev.surco.net.Deezer
import dev.surco.net.LastFmException
import dev.surco.net.LastFmStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Lo que enseña la pantalla de estadísticas. */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val report: StatsReport? = null,
    val loading: Boolean = false,
    /** Avance de la descarga del historial (0–1). */
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * Estadísticas de Last.fm del usuario conectado: las descarga, las calcula y las guarda en memoria
 * (20 minutos por periodo), para que cambiar de periodo y volver sea instantáneo.
 */
class StatsRepository(
    private val api: LastFmStats,
    private val deezer: Deezer,
    private val dao: LibraryDao,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val offset: (Long) -> Int,
    private val now: () -> Long,
) {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private val cache = HashMap<Pair<String, StatsPeriod>, StatsReport>()
    private val pictures = HashMap<String, Pair<String?, Long>>()
    private var job: Job? = null

    fun select(period: StatsPeriod) {
        _state.update { it.copy(period = period) }
        load(force = false)
    }

    /** Carga el periodo elegido (de la memoria si es reciente, salvo [force]). */
    fun load(force: Boolean = false) {
        val user = prefs.current.lastfmUser
        if (user.isBlank()) return
        val period = _state.value.period
        val cached = cache[user to period]
        if (!force && cached != null && now() - cached.createdAt < FRESH_MS) {
            _state.update { it.copy(report = cached, loading = false, error = null) }
            return
        }
        job?.cancel()
        _state.update { it.copy(loading = true, progress = 0f, error = null, report = cached ?: it.report?.takeIf { r -> r.profile.name == user }) }
        job = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val r = build(user, period)
                cache[user to period] = r
                if (_state.value.period == period) _state.update { it.copy(report = r, loading = false, error = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = when {
                    e is LastFmException && e.code == 6 -> dev.surco.i18n.S.lastfmUserNotFound
                    e is LastFmException -> e.message ?: dev.surco.i18n.S.statsLoadFailed
                    else -> dev.surco.i18n.S.statsLoadFailed
                }
                _state.update { it.copy(loading = false, error = msg) }
            }
        }
    }

    /** Sólo para las capturas automáticas (CI). */
    fun debugSet(report: StatsReport) {
        cache[report.profile.name to report.period] = report
        _state.value = StatsUiState(period = report.period, report = report)
    }

    private suspend fun build(user: String, period: StatsPeriod): StatsReport = coroutineScope {
        val nowSec = now() / 1000
        val from = if (period.days > 0) nowSec - period.days * 86_400L else 0L
        val profile = async { api.profile(user) }
        val artists = async { api.topArtists(user, period, 10) }
        val albums = async { api.topAlbums(user, period, 12) }
        val tracks = async { api.topTracks(user, period, 10) }
        val loved = async { api.lovedCount(user) }
        val recent = async { api.recent(user, from, MAX_PAGES) { p -> _state.update { it.copy(progress = p) } } }
        val library = async {
            runCatching { dao.allTracksOnce().map { StatsMath.key(it.artist, it.title) }.toHashSet() }.getOrDefault(hashSetOf())
        }
        val topArtists = withPictures(artists.await())
        // Géneros: etiquetas de los 8 artistas más escuchados.
        val tagged = topArtists.take(8)
        val tags = tagged.map { a -> async { a.name to api.artistTags(a.name) } }.awaitAll().toMap()
        val (scrobbles, total, complete) = recent.await()
        val p = profile.await()
        val al = albums.await()
        val tr = tracks.await()
        val lib = library.await()
        val lv = loved.await()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { StatsMath.report(
            profile = p,
            period = period,
            periodPlays = if (period == StatsPeriod.ALL) p.playcount else total,
            scrobbles = scrobbles,
            sampleComplete = complete,
            topArtists = topArtists,
            topAlbums = al,
            topTracks = tr,
            genres = StatsMath.genres(tagged, tags),
            library = lib,
            lovedTracks = lv,
            nowSec = nowSec,
            offset = offset,
        ) }
    }

    /** Last.fm ya no da fotos de artistas: se buscan en Deezer (y se recuerdan). */
    private suspend fun withPictures(list: List<TopItem>): List<TopItem> = coroutineScope {
        val gate = Semaphore(4)
        val known = pictures.toMap()
        val found = list.map { a ->
            async {
                a.name.lowercase() to (known[a.name.lowercase()] ?: gate.withPermit {
                    runCatching { deezer.findArtist(a.name) }.getOrNull().let { (it?.picture to (it?.id ?: 0L)) }
                })
            }
        }.awaitAll()
        found.forEach { (k, v) -> pictures[k] = v }
        list.map { a ->
            val (pic, id) = pictures[a.name.lowercase()] ?: (null to 0L)
            a.copy(imageUrl = pic ?: a.imageUrl, deezerId = id)
        }
    }

    companion object {
        /** 25 páginas de 200: las últimas 5.000 escuchas como mucho para los gráficos. */
        const val MAX_PAGES = 25
        private const val FRESH_MS = 20 * 60 * 1000L
    }
}
