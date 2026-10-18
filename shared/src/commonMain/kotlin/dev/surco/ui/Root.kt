package dev.surco.ui

import dev.surco.i18n.S

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.surco.app.SurcoCore
import kotlinx.coroutines.Dispatchers
import kotlin.time.TimeSource
import dev.surco.data.Track
import dev.surco.playback.PlayerUiState
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.ktor.http.encodeURLParameter

/** Acciones globales accesibles desde cualquier pantalla. */
class AppActions(
    val container: SurcoCore,
    val nav: NavHostController,
    val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val onMenu: (Track, Long?) -> Unit,
    private val onAddToPlaylist: (List<Track>) -> Unit,
    private val onOpenPlayer: () -> Unit,
    private val onAlbumMenu: (String) -> Unit = {},
    private val onArtistPicture: (String) -> Unit = {},
    private val onShare: (Track?, dev.surco.data.AlbumRow?) -> Unit = { _, _ -> },
) {
    val repo get() = container.repository
    val player get() = container.player

    private var lastRoute: String? = null
    private var lastNavAt = 0L

    /**
     * Navega evitando duplicados por doble toque: misma ruta en menos de 600 ms, o la pantalla
     * que ya está abierta (launchSingleTop). Sin expresiones regulares: el motor de Android (ICU)
     * es más estricto que el de Java y una «}» sin escapar cerraba la app.
     */
    fun go(route: String) {
        // La navegación sólo funciona en el hilo principal: si nos llaman desde otro
        // (p. ej. tras esperar a la base de datos), la pasamos allí.
        scope.launch(Dispatchers.Main.immediate) { goNow(route) }
    }

    private val clock = TimeSource.Monotonic.markNow()

    private fun goNow(route: String) {
        val now = clock.elapsedNow().inWholeMilliseconds
        if (route == lastRoute && now - lastNavAt < 600) return
        lastRoute = route
        lastNavAt = now
        runCatching { nav.navigate(route) { launchSingleTop = true } }
            .onFailure { println("Surco: no se pudo navegar a $route: $it") }
    }

    fun openAlbum(key: String) = go("album/${encodeArg(key)}")
    fun openArtist(name: String) = go("artist/${encodeArg(name)}")
    fun openPlaylist(id: Long) = go("playlist/$id")
    fun openLiked() = go("liked")
    fun openGenre(name: String) = go("genre/${encodeArg(name)}")
    fun editTrack(uri: String) = go("edit_track/${encodeArg(uri)}")
    fun editAlbum(key: String) = go("edit_album/${encodeArg(key)}")
    fun searchCovers(key: String) = go("covers/${encodeArg(key)}")
    fun openSettings() = go("settings")
    fun openSoulseek(query: String = "") = go("soulseek?q=${encodeArg(query)}")
    fun openDiscover(id: Long) = go("discover/$id")
    fun openOnlineArtist(id: Long) = go("online_artist/$id")

    /** Busca en internet (Deezer) y abre dentro de la app algo que no tienes en la biblioteca. */
    private fun resolveOnline(find: suspend () -> Long?, open: (Long) -> Unit) {
        scope.launch {
            val id = runCatching { find() }.getOrNull()
            if (id != null && id > 0) open(id) else toast(S.notFoundOnline)
        }
    }

    /** La página de un artista que no tienes (con su id de Deezer si ya se sabe). */
    fun openOnlineArtistNamed(name: String, deezerId: Long = 0) =
        if (deezerId > 0) openOnlineArtist(deezerId) else resolveOnline({ container.deezer.findArtist(name)?.id }, ::openOnlineArtist)

    /** La página de un álbum que no tienes. */
    fun openOnlineAlbum(artist: String, album: String) =
        resolveOnline({ container.deezer.findAlbum(artist, album)?.id }, ::openDiscover)

    /** El álbum (de internet) de una canción que no tienes. */
    fun openOnlineTrackAlbum(artist: String, title: String) =
        resolveOnline({ container.deezer.findTrack(artist, title)?.first }, ::openDiscover)
    fun openPlayer() = onOpenPlayer()

    /** Cambia de pestaña igual que la barra inferior. */
    fun openTab(route: String) {
        scope.launch(Dispatchers.Main) { openTabNow(route) }
    }

    private fun openTabNow(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun play(tracks: List<Track>, index: Int = 0, source: String, shuffle: Boolean = false) =
        player.play(tracks, index, source, shuffle)

    fun menu(track: Track, playlistId: Long? = null) = onMenu(track, playlistId)
    fun addToPlaylist(tracks: List<Track>) = onAddToPlaylist(tracks)
    /** Menú de un álbum: poner en cola, buscar datos, borrar… */
    fun albumMenu(albumKey: String) = onAlbumMenu(albumKey)
    /** Hoja para cambiar la foto de un artista. */
    fun artistPicture(name: String) = onArtistPicture(name)
    fun albumInfo(key: String) = go("album_info/${encodeArg(key)}")
    fun openPalette() = go("palette")
    /** Abre un enlace de Spotify (pegado o compartido con Surco). */
    fun openLink(url: String) = go("spotify?u=${encodeArg(url)}")
    fun share(track: Track) = onShare(track, null)
    fun shareAlbum(album: dev.surco.data.AlbumRow) = onShare(null, album)

    fun toggleLike(uri: String, liked: Boolean) {
        scope.launch { repo.setLiked(uri, !liked) }
    }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions no disponible") }
val LocalLiked = compositionLocalOf<Set<String>> { emptySet() }
val LocalPlayer = compositionLocalOf { PlayerUiState() }

private data class Tab(val route: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    Tab("stats", Icons.Outlined.BarChart, Icons.Filled.BarChart),
)

/**
 * Zoom de toda la app: escala los dp y el texto a la vez (como el «tamaño de pantalla» del sistema,
 * pero sólo para Surco).
 */
@Composable
fun AppZoom(scale: Float, content: @Composable () -> Unit) {
    if (scale == 1f) { content(); return }
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * scale, d.fontScale),
        content = content,
    )
}

@Composable
fun SurcoRoot(container: SurcoCore, openPlayerSignal: Int, initialRoute: String? = null) {
    val scale by remember { container.prefs.settings }.collectAsState()
    AppZoom(scale.uiScale) { SurcoRootContent(container, openPlayerSignal, initialRoute) }
}

@Composable
private fun SurcoRootContent(container: SurcoCore, openPlayerSignal: Int, initialRoute: String? = null) {
    val settings by container.prefs.settings.collectAsState()
    if (settings.treeUri == null) {
        OnboardingScreen(container)
        return
    }

    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showPlayer by Overlays::player
    var menuFor by remember { mutableStateOf<Pair<Track, Long?>?>(null) }
    var playlistPickFor by remember { mutableStateOf<List<Track>?>(null) }
    var albumMenuFor by remember { mutableStateOf<String?>(null) }
    var artistPictureFor by remember { mutableStateOf<String?>(null) }
    var shareFor by remember { mutableStateOf<Pair<Track?, dev.surco.data.AlbumRow?>?>(null) }

    val playerState by container.player.state.collectAsState()
    val likedList by remember { container.db.dao().likedUris() }.collectAsState(initial = emptyList())
    val liked = remember(likedList) { likedList.toHashSet() }

    LaunchedEffect(openPlayerSignal) { if (openPlayerSignal > 0) showPlayer = true }
    // Aviso si hay scrobbles sin enviar a punto de caducar (Last.fm rechaza los de más de 14 días).
    LaunchedEffect(Unit) {
        val days = runCatching { container.repository.oldestPendingDays() }.getOrNull()
        if (days != null && days >= 10 && container.prefs.current.lastfmLoggedIn) snackbar.showSnackbar(S.scrobblesAgeWarning(days))
    }
    // Sin canción no hay reproductor visible: que «atrás» no se gaste en una capa invisible.
    LaunchedEffect(playerState.mediaId == null) { if (playerState.mediaId == null) showPlayer = false }
    LaunchedEffect(showPlayer) { if (!showPlayer) Overlays.lyrics = false }

    val actions = remember(nav) {
        AppActions(
            container, nav, scope, snackbar,
            onMenu = { t, p -> menuFor = t to p },
            onAddToPlaylist = { playlistPickFor = it },
            onOpenPlayer = { showPlayer = true },
            onAlbumMenu = { albumMenuFor = it },
            onArtistPicture = { artistPictureFor = it },
            onShare = { t, a -> shareFor = t to a },
        )
    }
    // Enlace compartido desde otra app (p. ej. un álbum de Spotify que te mandaron por WhatsApp).
    val pending = Overlays.pendingLink
    LaunchedEffect(pending) {
        if (pending != null) {
            Overlays.pendingLink = null
            showPlayer = false
            actions.openLink(pending)
        }
    }

    // Ruta inicial opcional (capturas automáticas de iOS).
    LaunchedEffect(initialRoute) {
        val route = initialRoute ?: return@LaunchedEffect
        nav.currentBackStackEntryFlow.first() // espera a que exista el grafo de navegación
        if (route in tabs.map { t -> t.route }) actions.openTab(route) else actions.go(route)
    }

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalLiked provides liked,
        LocalPlayer provides playerState,
    ) {
        Box(Modifier.fillMaxSize().background(SurcoColors.Background)) {
            ProfileDrawerHost {
            Scaffold(
                containerColor = SurcoColors.Background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(snackbar) { data ->
                        androidx.compose.material3.Snackbar(
                            data,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            containerColor = SurcoColors.SurfaceHigher,
                            contentColor = SurcoColors.TextPrimary,
                            actionColor = SurcoColors.Accent,
                        )
                    }
                },
                bottomBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.35f to SurcoColors.Background.copy(alpha = 0.92f),
                                    1f to SurcoColors.Background,
                                )
                            )
                    ) {
                        if (playerState.mediaId != null) {
                            MiniPlayer(onOpen = { showPlayer = true })
                        }
                        BottomTabs(nav)
                    }
                },
            ) { padding ->
                NavHost(
                    nav,
                    startDestination = "home",
                    modifier = Modifier.padding(padding),
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable("home") { HomeScreen() }
                    composable("search") { SearchScreen() }
                    composable("library") { LibraryScreen() }
                    composable("stats") { StatsScreen() }
                    composable("stats_top/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) {
                        StatsTopScreen(it.stringArg("kind"))
                    }
                    composable("settings") { SettingsScreen() }
                    composable("audio") { AudioScreen() }
                    composable("online_artist/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        OnlineArtistScreen(it.longArg("id"))
                    }
                    composable("discover/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        DiscoverAlbumScreen(it.longArg("id"))
                    }
                    composable(
                        "soulseek?q={q}",
                        listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
                    ) { SoulseekScreen(it.stringArg("q")) }
                    composable("liked") { LikedScreen() }
                    composable("recent") { RecentScreen() }
                    composable("album/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                        AlbumScreen(it.stringArg("key"))
                    }
                    composable("artist/{name}", listOf(navArgument("name") { type = NavType.StringType })) {
                        ArtistScreen(it.stringArg("name"))
                    }
                    composable("genre/{name}", listOf(navArgument("name") { type = NavType.StringType })) {
                        GenreScreen(it.stringArg("name"))
                    }
                    composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        PlaylistScreen(it.longArg("id"))
                    }
                    composable("edit_track/{uri}", listOf(navArgument("uri") { type = NavType.StringType })) {
                        TrackEditorScreen(it.stringArg("uri"))
                    }
                    composable("edit_album/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                        AlbumEditorScreen(it.stringArg("key"))
                    }
                    composable("covers/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                        CoverSearchScreen(it.stringArg("key"))
                    }
                    composable("album_info/{key}", listOf(navArgument("key") { type = NavType.StringType })) {
                        AlbumInfoScreen(it.stringArg("key"))
                    }
                    composable("palette") { PaletteScreen() }
                    composable("spotify?u={u}", listOf(navArgument("u") { type = NavType.StringType; defaultValue = "" })) {
                        SpotifyLinkScreen(it.stringArg("u"))
                    }
                }
            }

            }

            // Al abrir el reproductor, lo de detrás se oscurece mientras sube (como en Spotify).
            val playerVisible = showPlayer && playerState.mediaId != null
            val dim by androidx.compose.animation.core.animateFloatAsState(
                if (playerVisible) 0.7f else 0f, androidx.compose.animation.core.tween(320), label = "dim",
            )
            if (dim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            AnimatedVisibility(
                visible = playerVisible,
                enter = slideInVertically(androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it },
                exit = slideOutVertically(androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it },
            ) {
                // Esquinas redondeadas mientras sube o baja; rectas cuando ocupa la pantalla.
                val corner by transition.animateDp(label = "esquinas") { if (it == androidx.compose.animation.EnterExitState.Visible) 0.dp else 22.dp }
                Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = corner, topEnd = corner))) {
                    NowPlayingScreen(onClose = { showPlayer = false })
                }
            }
        }

        menuFor?.let { (track, playlistId) ->
            TrackMenuSheet(track, playlistId, onDismiss = { menuFor = null }, onNavigate = { showPlayer = false })
        }
        playlistPickFor?.let { tracks ->
            AddToPlaylistDialog(tracks, onDismiss = { playlistPickFor = null })
        }
        albumMenuFor?.let { key ->
            AlbumMenuSheet(key, onDismiss = { albumMenuFor = null })
        }
        artistPictureFor?.let { name ->
            ArtistPictureSheet(name, onDismiss = { artistPictureFor = null })
        }
        shareFor?.let { (t, a) ->
            ShareSheet(t, a, onDismiss = { shareFor = null })
        }
    }
}

private fun tabLabel(route: String) = when (route) {
    "home" -> S.home
    "search" -> S.search
    "stats" -> S.stats
    else -> S.yourLibrary
}

/** Pestaña a la que pertenece una ruta: la de la raíz más cercana en la pila. */
private fun tabOf(nav: NavHostController): String {
    val roots = tabs.map { it.route }.toSet()
    val stack = nav.currentBackStack.value
    for (i in stack.indices.reversed()) {
        val r = stack[i].destination.route
        if (r in roots) return r!!
    }
    return "home"
}

@Composable
private fun BottomTabs(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = remember(entry) { tabOf(nav) }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            val selected = currentTab == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .testTag("tab_${tab.route}")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Overlays.tapTab(tab.route)
                        if (selected) {
                            // Volver a tocar la pestaña activa lleva a su raíz (y arriba del todo).
                            if (entry?.destination?.route != tab.route) nav.popBackStack(tab.route, inclusive = false)
                        } else {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (selected) tab.selectedIcon else tab.icon,
                    tabLabel(tab.route),
                    tint = if (selected) SurcoColors.TextPrimary else SurcoColors.TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tabLabel(tab.route),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) SurcoColors.TextPrimary else SurcoColors.TextSecondary,
                )
            }
        }
    }
}

/** Selector de carpeta reutilizable (onboarding y ajustes). */
@Composable
fun rememberFolderPicker(container: SurcoCore): () -> Unit {
    val release = rememberFolderReleaser()
    return rememberDirectoryPicker { root ->
        // Soltamos el permiso de la carpeta anterior, si la había.
        val old = container.prefs.current.treeUri
        if (old != null && old != root) release(old)
        container.prefs.update { it.copy(treeUri = root) }
        container.repository.rescan()
    }
}

private fun NavBackStackEntry.longArg(key: String): Long =
    arguments?.read { if (contains(key)) getLong(key) else 0L } ?: 0L

private fun NavBackStackEntry.stringArg(key: String): String =
    arguments?.read { if (contains(key)) getStringOrNull(key) else null }.orEmpty()

/** Codifica un texto para usarlo dentro de una ruta de navegación. */
fun encodeArg(s: String): String = s.encodeURLParameter()
