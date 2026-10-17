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
    private val tops = HashMap<String, List<TopItem>>()
    private var job: Job? = null

    /** Las últimas escuchas (independientes del periodo). */
    private val _recent = MutableStateFlow<List<RecentScrobble>>(emptyList())
    val recent: StateFlow<List<RecentScrobble>> = _recent.asStateFlow()
    private var recentFor = ""
    private var recentAt = 0L

    private fun loadRecent(force: Boolean) {
        val user = currentUser()
        if (user.isBlank()) return
        if (!force && recentFor.equals(user, ignoreCase = true) && now() - recentAt < 90_000) return
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            runCatching { api.latest(user, 10) }.onSuccess { list ->
                if (currentUser() == user) { _recent.value = list; recentFor = user; recentAt = now() }
            }
        }
    }

    /** Sólo para las capturas automáticas (CI). */
    fun debugSetRecent(list: List<RecentScrobble>) { _recent.value = list; recentFor = currentUser(); recentAt = now() }

    /** Otro usuario de Last.fm cuyas estadísticas se están viendo (null = las tuyas). */
    private val _viewing = MutableStateFlow<String?>(null)
    val viewing: StateFlow<String?> = _viewing.asStateFlow()

    /** De quién son las estadísticas que se enseñan. */
    fun currentUser(): String = _viewing.value ?: prefs.current.lastfmUser

    /** Ver las estadísticas de otra persona (null o vacío = volver a las tuyas). */
    fun view(user: String?) {
        val u = user?.trim()?.removePrefix("@")?.ifBlank { null }
        if (u != null && u.equals(prefs.current.lastfmUser, ignoreCase = true)) { view(null); return }
        job?.cancel()
        _viewing.value = u
        _recent.value = emptyList()
        recentFor = ""
        _state.value = StatsUiState(period = _state.value.period)
        load()
    }

    enum class TopKind { ARTISTS, ALBUMS, TRACKS }

    /** Una página (50) de un top completo, de la memoria si ya se pidió. */
    suspend fun topPage(kind: TopKind, period: StatsPeriod, page: Int): List<TopItem> {
        val user = currentUser()
        val key = "${user.lowercase()}|$kind|$period|$page"
        tops[key]?.let { return it }
        val list = when (kind) {
            TopKind.ARTISTS -> api.topArtists(user, period, TOP_PAGE, page)
            TopKind.ALBUMS -> api.topAlbums(user, period, TOP_PAGE, page)
            TopKind.TRACKS -> api.topTracks(user, period, TOP_PAGE, page)
        }
        tops[key] = list
        return list
    }

    /** Sólo para las capturas automáticas (CI): una página de un top, sin red. */
    fun debugPutTop(kind: TopKind, period: StatsPeriod, list: List<TopItem>) {
        tops["${currentUser().lowercase()}|$kind|$period|1"] = list
    }

    /** Foto (de Deezer) e id de Deezer de un artista, recordadas. */
    suspend fun artistPicture(name: String): Pair<String?, Long> {
        pictures[name.lowercase()]?.let { return it }
        val r = runCatching { deezer.findArtist(name) }.getOrNull().let { (it?.picture to (it?.id ?: 0L)) }
        pictures[name.lowercase()] = r
        return r
    }

    fun select(period: StatsPeriod) {
        _state.update { it.copy(period = period) }
        load(force = false)
    }

    /** Carga el periodo elegido (de la memoria si es reciente, salvo [force]). */
    fun load(force: Boolean = false) {
        val user = currentUser()
        if (user.isBlank()) return
        loadRecent(force)
        val period = _state.value.period
        val cached = cache[user to period]
        if (!force && cached != null && now() - cached.createdAt < FRESH_MS) {
            _state.update { it.copy(report = cached, loading = false, error = null) }
            return
        }
        job?.cancel()
        _state.update { it.copy(loading = true, progress = 0f, error = null, report = cached ?: it.report?.takeIf { r -> r.profile.name.equals(user, ignoreCase = true) }) }
        job = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val r = build(user, period)
                cache[user to period] = r
                if (_state.value.period == period && currentUser() == user) _state.update { it.copy(report = r, loading = false, error = null) }
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
        const val TOP_PAGE = 50
        private const val FRESH_MS = 20 * 60 * 1000L
    }
}
