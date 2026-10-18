package dev.surco.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Lang(val code: String, val label: String) { EN("en", "English"), ES("es", "Español") }

/**
 * Idioma de la interfaz. Es estado de Compose: al cambiarlo, todas las pantallas se redibujan
 * al momento (sin reiniciar la app). También lo usan los mensajes que no son de interfaz.
 */
object I18n {
    var lang by mutableStateOf(Lang.EN)
}

private fun t(en: String, es: String) = if (I18n.lang == Lang.ES) es else en
private fun n(count: Int, en1: String, enN: String, es1: String, esN: String) =
    if (count == 1) t(en1, es1) else t(enN.replace("#", "$count"), esN.replace("#", "$count"))

/** Todos los textos de la app, en inglés y español. Cada texto aparece una sola vez. */
@Suppress("FunctionName")
object S {
    // ------------------------------------------------------------------ General
    val back get() = t("Back", "Atrás")
    val cancel get() = t("Cancel", "Cancelar")
    val save get() = t("Save", "Guardar")
    val ok get() = t("OK", "Vale")
    val close get() = t("Close", "Cerrar")
    val clear get() = t("Clear", "Borrar")
    val remove get() = t("Remove", "Quitar")
    val delete get() = t("Delete", "Borrar")
    val more get() = t("More", "Más")
    val moreOptions get() = t("More options", "Más opciones")
    val options get() = t("Options", "Opciones")
    val loading get() = t("Loading…", "Cargando…")
    val error get() = t("Error", "Error")
    val retry get() = t("Retry", "Reintentar")
    val settings get() = t("Settings", "Ajustes")
    val search get() = t("Search", "Buscar")
    val shuffle get() = t("Shuffle", "Aleatorio")
    val play get() = t("Play", "Reproducir")
    val pause get() = t("Pause", "Pausa")
    val previous get() = t("Previous", "Anterior")
    val next get() = t("Next", "Siguiente")
    val repeat get() = t("Repeat", "Repetir")
    val queue get() = t("Queue", "Cola")
    val user get() = t("Username", "Usuario")
    val password get() = t("Password", "Contraseña")
    val connecting get() = t("Connecting…", "Conectando…")
    val logOut get() = t("Log out", "Cerrar sesión")
    val name get() = t("Name", "Nombre")
    val various get() = t("(various)", "(varios)")

    // ------------------------------------------------------------------ Pestañas y biblioteca
    val home get() = t("Home", "Inicio")
    val yourLibrary get() = t("Your Library", "Tu biblioteca")
    val all get() = t("All", "Todos")
    val albums get() = t("Albums", "Álbumes")
    val artists get() = t("Artists", "Artistas")
    val songsTab get() = t("Songs", "Canciones")
    val playlists get() = t("Playlists", "Listas")
    val album get() = t("Album", "Álbum")
    val artist get() = t("Artist", "Artista")
    val song get() = t("Song", "Canción")
    val playlist get() = t("Playlist", "Lista")
    val likedSongs get() = t("Liked Songs", "Canciones que te gustan")
    val allSongs get() = t("All songs", "Todas las canciones")
    val recents get() = t("Recents", "Recientes")
    val recentSearches get() = t("Recent searches", "Búsquedas recientes")
    val clearAll get() = t("Clear all", "Borrar todo")
    val searchedText get() = t("Search", "Búsqueda")
    val alphabetical get() = t("Alphabetical", "Alfabético")
    val changeView get() = t("Change view", "Cambiar vista")
    val createPlaylist get() = t("Create playlist", "Crear lista")
    val newPlaylist get() = t("New playlist", "Nueva lista")
    fun songs(count: Int) = n(count, "1 song", "# songs", "1 canción", "# canciones")
    fun albumsCount(count: Int) = n(count, "1 album", "# albums", "1 álbum", "# álbumes")
    fun tracksCount(count: Int) = n(count, "1 track", "# tracks", "1 pista", "# pistas")
    fun albumBy(artist: String) = "${album} • $artist"
    fun songBy(artist: String) = "${song} • $artist"
    fun playlistOf(count: Int) = "${playlist} • ${songs(count)}"

    // ------------------------------------------------------------------ Inicio
    val scanningMusic get() = t("Scanning your music…", "Escaneando tu música…")
    val noSongs get() = t("No songs", "No hay canciones")
    fun scanError(msg: String) = t("Scan failed: $msg", "Error al escanear: $msg")
    val noAudioFound get() = t(
        "I couldn't find any audio in that folder. Try another folder or scan again.",
        "No he encontrado audio en la carpeta elegida. Prueba con otra carpeta o vuelve a escanear.",
    )
    val openSettings get() = t("Open settings", "Abrir ajustes")
    val albumsWithLiked get() = t("Albums with songs you like", "Álbumes con canciones que te gustan")
    val diveBackIn get() = t("Dive back into your music", "Sumérgete de nuevo en tu música")
    val showAll get() = t("Show all", "Mostrar todos")
    val pickedForToday get() = t("Picked for today", "Recomendada para hoy")
    val favouriteArtists get() = t("Your favourite artists", "Tus artistas favoritos")
    val recentlyAdded get() = t("Recently added", "Añadidos recientemente")
    val moreLike get() = t("More like", "Más como")
    val becauseYouListen get() = t("Because you listen to", "Porque escuchas")
    val newForYou get() = t("New for you", "Nuevo para ti")
    val nothingPlayedYet get() = t("You haven't played anything yet.", "Aún no has escuchado nada.")

    // ------------------------------------------------------------------ Buscar
    val whatToListen get() = t("What do you want to listen to?", "¿Qué te apetece escuchar?")
    val browseGenres get() = t("Browse by genre", "Explorar por género")
    val downloadWithSoulseek get() = t("Download with Soulseek", "Descargar con Soulseek")
    fun nothingInLibrary(q: String) = t("Nothing in your library for “$q”", "Nada en tu biblioteca para «$q»")
    fun searchOnSoulseekFor(q: String) = t("Search “$q” on Soulseek", "Buscar «$q» en Soulseek")
    val downloadToFolder get() = t("Download to your music folder", "Descargar a tu carpeta de música")

    // ------------------------------------------------------------------ Detalle
    val disc get() = t("Disc", "Disco")
    fun duration(mins: Long) = if (mins >= 60) t("${mins / 60} hr ${mins % 60} min", "${mins / 60} h ${mins % 60} min") else "$mins min"
    val editAlbum get() = t("Edit album", "Editar álbum")
    val findCover get() = t("Find cover", "Buscar portada")
    val addToPlaylist get() = t("Add to playlist", "Añadir a una lista")
    val popular get() = t("Popular", "Populares")
    fun playAll(count: Int) = t("Play all ($count)", "Reproducir todo ($count)")
    val discography get() = t("Discography", "Discografía")
    val appearsOn get() = t("Appears on", "También aparece en")
    val rename get() = t("Rename", "Renombrar")
    val renamePlaylist get() = t("Rename playlist", "Renombrar lista")
    val deletePlaylist get() = t("Delete playlist", "Borrar lista")
    val emptyPlaylist get() = t(
        "This playlist is empty. Add songs from the ⋮ menu of any song.",
        "Esta lista está vacía. Añade canciones desde el menú ⋮ de cualquier canción.",
    )
    fun deleteQuestion(name: String) = t("Delete “$name”?", "¿Borrar «$name»?")
    val songsStayOnDevice get() = t("The songs stay on your device.", "Las canciones no se borran de tu dispositivo.")

    // ------------------------------------------------------------------ Menú de canción
    val removeFromLiked get() = t("Remove from Liked Songs", "Quitar de Canciones que te gustan")
    val addToLiked get() = t("Add to Liked Songs", "Añadir a Canciones que te gustan")
    val removeFavourite get() = t("Remove from favourites", "Quitar de favoritas")
    val addFavourite get() = t("Add to favourites", "Añadir a favoritas")
    val playNext get() = t("Play next", "Reproducir a continuación")
    val willPlayNext get() = t("Will play next", "Sonará a continuación")
    val addToQueue get() = t("Add to queue", "Añadir a la cola")
    val addedToQueue get() = t("Added to queue", "Añadida a la cola")
    val removeFromPlaylist get() = t("Remove from this playlist", "Quitar de esta lista")
    val goToAlbum get() = t("Go to album", "Ir al álbum")
    val goToArtist get() = t("Go to artist", "Ir al artista")
    val editTags get() = t("Edit tags", "Editar etiquetas")
    fun addedTo(name: String) = t("Added to “$name”", "Añadida a «$name»")

    // ------------------------------------------------------------------ Reproductor
    fun playingFrom(type: String) = when (type) {
        "álbum" -> t("Playing from album", "Reproduciendo desde el álbum")
        "artista" -> t("Playing from artist", "Reproduciendo desde el artista")
        "lista" -> t("Playing from playlist", "Reproduciendo desde la lista")
        "favoritas" -> t("Playing from your collection", "Reproduciendo desde tu colección")
        "género" -> t("Playing genre", "Reproduciendo el género")
        "búsqueda" -> t("Playing from search", "Reproduciendo desde la búsqueda")
        "biblioteca" -> t("Playing from your library", "Reproduciendo desde tu biblioteca")
        else -> t("Playing", "Reproduciendo")
    }
    val nowPlaying get() = t("Now playing", "Sonando")
    val upNext get() = t("Next up", "A continuación")
    val upNextShuffled get() = t("Next up (shuffled)", "A continuación (aleatorio)")
    val removeFromQueue get() = t("Remove from queue", "Quitar de la cola")
    val queueName get() = t("Queue", "Cola")

    // ------------------------------------------------------------------ Letras
    val lyrics get() = t("Lyrics", "Letra")
    val expandLyrics get() = t("Expand lyrics", "Ampliar letra")
    val lyricsOptions get() = t("Lyrics options", "Opciones de la letra")
    val searchingLyrics get() = t("Looking for lyrics…", "Buscando la letra…")
    val lyricsNotFound get() = t(
        "We couldn't find lyrics for this song.\nTap to search again or write them yourself.",
        "No hemos encontrado la letra de esta canción.\nToca para buscarla de nuevo o escribirla tú.",
    )
    val instrumental get() = t("♪  Instrumental", "♪  Instrumental")
    val searchAgain get() = t("Search again", "Buscar de nuevo")
    val writeLyrics get() = t("Write lyrics", "Escribir letra")
    val editLyrics get() = t("Edit lyrics", "Editar letra")
    val writeThem get() = t("Write them", "Escribirla")
    val saveToFile get() = t("Save to file", "Guardar en el archivo")
    fun lyricsSavedIn(file: String) = t("Lyrics saved to $file", "Letra guardada en $file")
    fun couldNotSave(msg: String?) = t("Couldn't save: ${msg.orEmpty()}", "No se pudo guardar: ${msg.orEmpty()}")
    val noLyrics get() = t("No lyrics", "Sin letra")
    val lyricsOffline get() = t(
        "Couldn't reach the lyrics service.\nTap to try again.",
        "No se pudo conectar con el servicio de letras.\nToca para volver a intentarlo.",
    )
    val lyricsOfflineTitle get() = t("Couldn't load lyrics", "No se pudo cargar la letra")
    val lyricsOfflineBody get() = t(
        "The lyrics service didn't answer. Check your connection and search again.",
        "El servicio de letras no respondió. Comprueba la conexión y vuelve a buscar.",
    )
    val noLyricsAnywhere get() = t(
        "Not in a .lrc file, the tags, or LRCLIB.",
        "No está en un archivo .lrc, ni en las etiquetas, ni en LRCLIB.",
    )
    fun lyricsSource(source: String) = when (source) {
        "lrc" -> t("Lyrics: .lrc file", "Letra: archivo .lrc")
        "tag" -> t("Lyrics: file tags", "Letra: etiquetas del archivo")
        "lrclib" -> t("Lyrics: LRCLIB", "Letra: LRCLIB")
        "manual" -> t("Lyrics: written by you", "Letra: escrita por ti")
        else -> ""
    }
    val lyricsFormatHint get() = t(
        "Plain text, or LRC format ([mm:ss.xx] line) to keep it synced.",
        "Texto normal o formato LRC ([mm:ss.xx] línea) para que vaya sincronizada.",
    )

    // ------------------------------------------------------------------ Editores
    val couldNotEditSome get() = t("Some files couldn't be edited", "Algunos archivos no se pudieron editar")
    val tagsSaved get() = t("Tags saved", "Etiquetas guardadas")
    val couldNotSaveShort get() = t("Couldn't save", "No se pudo guardar")
    val coverIsEditedFromAlbum get() = t("Change the cover from the album page", "La portada se edita desde el álbum")
    val title get() = t("Title", "Título")
    val albumArtist get() = t("Album artist", "Artista del álbum")
    val year get() = t("Year", "Año")
    val track get() = t("Track", "Pista")
    val genre get() = t("Genre", "Género")
    val couldNotReadImage get() = t("Couldn't read the image", "No se pudo leer la imagen")
    fun savingProgress(done: Int, total: Int) = t("Saving $done of $total…", "Guardando $done de $total…")
    val albumUpdated get() = t("Album updated", "Álbum actualizado")
    val internet get() = t("Internet", "Internet")
    val gallery get() = t("Gallery", "Galería")
    val removeCover get() = t("Remove cover", "Quitar portada")
    val coverWillBeRemoved get() = t("The cover will be removed from the files when you save.", "Se quitará la portada de los archivos al guardar.")
    fun coverWillBeEmbedded(count: Int) = t(
        "The new cover will be embedded in the $count files when you save.",
        "La nueva portada se incrustará en los $count archivos al guardar.",
    )
    fun changesApplyTo(count: Int) = t(
        "Changes apply to the album's ${songs(count)}.",
        "Los cambios se aplican a las ${songs(count)} del álbum.",
    )
    val noCoversFound get() = t("No covers found. Try a different search.", "No se encontraron portadas. Prueba a cambiar la búsqueda.")
    val useThisCover get() = t("Use this cover?", "¿Usar esta portada?")
    val embedInFiles get() = t("Save it inside the files", "Guardarla dentro de los archivos")
    val downloadingAndSaving get() = t("Downloading and saving…", "Descargando y guardando…")
    val couldNotDownloadImage get() = t("Couldn't download the image", "No se pudo descargar la imagen")
    val coverSaved get() = t("Cover saved", "Portada guardada")
    fun coverSavedWithErrors(count: Int) = t("Cover saved ($count files not embedded)", "Portada guardada ($count archivos sin incrustar)")
    val use get() = t("Use", "Usar")

    // ------------------------------------------------------------------ Ajustes
    val language get() = t("Language", "Idioma")
    val yourMusic get() = t("Your music", "Tu música")
    fun libraryStats(songs: Int, plays: Int) = "${songs(songs)} • " + n(plays, "1 play", "# plays", "1 escucha", "# escuchas")
    fun scanningProgress(done: Int, total: Int, current: String) = t("Scanning $done/$total: $current", "Escaneando $done/$total: $current")
    fun lastScan(total: Int, updated: Int, removed: Int) = t(
        "Last scan: $total files, $updated re-read, $removed removed",
        "Último escaneo: $total archivos, $updated leídos de nuevo, $removed eliminados",
    )
    fun errorWith(msg: String) = "${error}: $msg"
    val changeFolder get() = t("Change folder", "Cambiar carpeta")
    val scanAgain get() = t("Scan again", "Volver a escanear")
    val noAccount get() = t("No account", "Sin cuenta")
    val connected get() = t("Connected", "Conectado")
    val connectsWhenOpened get() = t("Connects when you open Soulseek", "Se conecta al abrir Soulseek")
    fun downloadsIn(folder: String, perAlbum: Boolean) =
        t("Downloads go to: $folder", "Descargas en: $folder") + if (perAlbum) t(" (one folder per album)", " (una carpeta por álbum)") else ""
    val openSoulseek get() = t("Open Soulseek", "Abrir Soulseek")
    val howToScrobble get() = t("How to scrobble", "Cómo hacer scrobbling")
    val builtIn get() = t("Built-in", "Integrado")
    val builtInDesc get() = t("Surco sends scrobbles straight to Last.fm (with an offline queue).", "Surco envía los scrobbles directamente a Last.fm (cola sin conexión incluida).")
    val panoOrOther get() = t("Pano Scrobbler or another app", "Pano Scrobbler u otra app")
    val panoDesc get() = t(
        "Surco publishes a standard media session: enable Surco in Pano Scrobbler's app list and it takes care of the rest.",
        "Surco publica una sesión multimedia estándar: activa Surco en la lista de apps de Pano Scrobbler y él se encarga.",
    )
    val off get() = t("Off", "Desactivado")
    val nothingIsSent get() = t("Nothing is sent.", "No se envía nada.")
    fun connectedAs(user: String) = t("Connected as $user", "Conectado como $user")
    fun pendingScrobbles(count: Int) = if (count == 0) t("No pending scrobbles", "No hay scrobbles pendientes")
        else t(
            "$count scrobbles waiting. They're sent automatically as soon as you're online.",
            "$count scrobbles pendientes. Se envían solos en cuanto haya conexión.",
        )
    fun scrobblesTooOld(n: Int) = t(
        "$n scrobble(s) were more than 14 days old and Last.fm didn't accept them.",
        "$n scrobble(s) tenían más de 14 días y Last.fm no los aceptó.",
    )
    fun scrobblesAgeWarning(days: Long) = t(
        "The oldest pending scrobble is $days days old. Last.fm only accepts scrobbles up to 14 days old: connect to the internet soon.",
        "El scrobble pendiente más antiguo tiene $days días. Last.fm sólo acepta scrobbles de hasta 14 días: conéctate pronto a internet.",
    )
    val sendNow get() = t("Send now", "Enviar ahora")
    val coversAndPhotos get() = t("Covers & photos", "Portadas y fotos")
    val embedCovers get() = t("Save covers inside the files", "Guardar portadas dentro de los archivos")
    val embedCoversDesc get() = t("When a cover is downloaded, it's also embedded in each song's tags.", "Al descargar una portada, también se incrusta en las etiquetas de cada canción.")
    val autoCovers get() = t("Find covers after each scan", "Buscar portadas tras cada escaneo")
    val autoCoversDesc get() = t("Automatically looks up missing covers on Deezer, iTunes and MusicBrainz.", "Busca automáticamente en Deezer, iTunes y MusicBrainz las portadas que falten.")
    val findMissingCovers get() = t("Find missing covers", "Buscar portadas que faltan")
    val artistPhotos get() = t("Artist photos", "Fotos de artistas")
    val artistPhotosDesc get() = t("Downloads artist photos from Deezer after scanning.", "Descarga fotos de artistas de Deezer tras escanear.")
    val downloadPhotosNow get() = t("Download photos now", "Descargar fotos ahora")
    val recommendations get() = t("Recommendations", "Recomendaciones")
    val recommendationsDesc get() = t(
        "Suggests albums you don't have yet on Home, based on what you play (via Deezer).",
        "Sugiere en Inicio álbumes que aún no tienes, según lo que escuchas (vía Deezer).",
    )
    val about get() = t("About", "Acerca de")
    val aboutText get() = t(
        "A local player for your collection. Plays FLAC, MP3, AAC/M4A, OGG, Opus, WAV and more; edits tags with jaudiotagger and plays with Media3/ExoPlayer.",
        "Reproductor local para tu colección. Lee FLAC, MP3, AAC/M4A, OGG, Opus, WAV y más; edita etiquetas con jaudiotagger y reproduce con Media3/ExoPlayer.",
    )
    val noFolder get() = t("No folder", "Sin carpeta")
    val internalStorage get() = t("Internal storage", "Almacenamiento interno")
    val apiKeysHelp get() = t(
        "Built-in mode needs a free Last.fm API key and secret. Create them on the Last.fm website and paste them here.",
        "Para el modo integrado necesitas una API key y un secret de Last.fm (gratis). Créalos en la web de Last.fm y pégalos aquí.",
    )
    val saveKeys get() = t("Save keys", "Guardar claves")
    val signInLastfm get() = t("Sign in with your Last.fm account", "Inicia sesión con tu cuenta de Last.fm")
    val passwordNotStored get() = t("Your password isn't stored, only the session key Last.fm returns.", "La contraseña no se guarda: sólo la clave de sesión que devuelve Last.fm.")
    val signIn get() = t("Sign in", "Iniciar sesión")
    val connectedToLastfm get() = t("Connected to Last.fm", "Conectado a Last.fm")
    val couldNotSignIn get() = t("Couldn't sign in", "No se pudo iniciar sesión")
    val lastfmUnreachable get() = t("Couldn't reach Last.fm. Check your connection and try again.", "No se pudo conectar con Last.fm. Revisa la conexión y vuelve a probar.")
    val changeKeys get() = t("Change keys", "Cambiar claves")
    val onboardingText get() = t(
        "Your music, your files, your tags.\nPick the folder where you keep your collection and I'll scan all of it.",
        "Tu música, tus archivos, tus etiquetas.\nElige la carpeta donde guardas tu colección y la escanearé entera.",
    )
    val chooseMusicFolder get() = t("Choose music folder", "Elegir carpeta de música")
    val grantWriteAccess get() = t(
        "Grant write access so tags and covers can be saved into the files.",
        "Concede acceso de escritura para poder editar etiquetas y guardar portadas en los archivos.",
    )

    // ------------------------------------------------------------------ Soulseek
    val downloadsFolder get() = t("Downloads folder…", "Carpeta de descargas…")
    val useMusicFolder get() = t("Use the music folder", "Usar la carpeta de música")
    val folderPerAlbum get() = t("One folder per album", "Una carpeta por álbum")
    val downloads get() = t("Downloads", "Descargas")
    val offline get() = t("Offline", "Sin conexión")
    val connectAccount get() = t("Connect your account", "Conecta tu cuenta")
    val soulseekIntro get() = t(
        "Soulseek is the people-to-people music sharing network used by Nicotine+ and SoulseekQt. If the username doesn't exist, it's created with the password you type. It can't be recovered, so write it down.",
        "Soulseek es la red para compartir música entre personas que usan Nicotine+ y SoulseekQt. Si el usuario no existe, se crea con la contraseña que escribas. No se puede recuperar, así que apúntala.",
    )
    val connect get() = t("Connect", "Conectar")
    val soulseekNoShare get() = t(
        "Surco only downloads: it doesn't share your music. Some users don't allow downloads from people who share nothing.",
        "Surco sólo descarga: no comparte tu música. Algunos usuarios no dejan descargar a quien no comparte nada.",
    )
    val artistAlbumOrSong get() = t("Artist, album or song", "Artista, álbum o canción")
    val formatAll get() = t("All formats", "Todos")
    val formatLossless get() = t("Lossless", "Sin pérdida")
    val cancelAll get() = t("Cancel all", "Cancelar todo")
    fun cancelQueuedN(n: Int) = t("Cancel queued ($n)", "Cancelar en cola ($n)")
    fun cancelDownloadingN(n: Int) = t("Cancel downloading ($n)", "Cancelar en curso ($n)")
    fun retryFailedN(n: Int) = t("Retry failed ($n)", "Reintentar fallidas ($n)")
    fun cancelledN(n: Int) = t("$n download(s) cancelled", "$n descarga(s) cancelada(s)")
    fun retryingN(n: Int) = t("Retrying $n download(s)", "Reintentando $n descarga(s)")
    fun downloadsSummary(done: Int, total: Int) = t("$done of $total files", "$done de $total archivos")
    fun queuedAndActive(active: Int, queued: Int) = t("$active downloading · $queued queued", "$active bajando · $queued en cola")
    val losslessOnly get() = t("Lossless only", "Sólo sin pérdida")
    val searchingNetwork get() = t("Searching the network…", "Buscando en la red…")
    val noResultsYet get() = t("No results yet", "Sin resultados todavía")
    fun foldersAndUsers(folders: Int, users: Int) = t("$folders folders · $users users", "$folders carpetas · $users usuarios")
    fun showingOf(shown: Int, total: Int) = t("Showing $shown of $total folders", "Mostrando $shown de $total carpetas")
    val showTenMore get() = t("Show 10 more", "Mostrar 10 más")
    fun addedToDownloads(count: Int) = t("$count file(s) added to downloads", "$count archivo(s) añadidos a descargas")
    val free get() = t("Free", "Libre")
    fun queued(count: Long) = t("Queue: $count", "Cola: $count")
    val inDownloads get() = t("In downloads", "En descargas")
    val download get() = t("Download", "Descargar")
    val downloadsEmpty get() = t(
        "What you download shows up here. When it's done, it's added to your library automatically.",
        "Aquí verás lo que descargues. Al terminar, se añade solo a tu biblioteca.",
    )
    val clearFinished get() = t("Clear finished", "Limpiar terminadas")
    fun progressOf(pct: Int, got: String, total: String) = t("$pct % · $got of $total", "$pct % · $got de $total")
    fun inQueue(place: Long?) = t("Queued", "En cola") + (place?.let { t(" · position $it", " · puesto $it") } ?: "")
    fun savedIn(where: String?) = t("Saved to ${where ?: "your folder"}", "Guardada en ${where ?: "tu carpeta"}")
    val rootFolder get() = t("(root)", "(raíz)")
    val stConnecting get() = t("Connecting…", "Conectando…")
    val stQueued get() = t("Queued", "En cola")
    val stStarting get() = t("Starting…", "Iniciando…")
    val stTransferring get() = t("Downloading", "Descargando")
    val stSaving get() = t("Saving…", "Guardando…")
    val stDone get() = t("Done", "Completada")
    val stCancelled get() = t("Cancelled", "Cancelada")
    val loginRejected get() = t("Login rejected", "Inicio de sesión rechazado")
    val slskReconnecting get() = t("No connection to Soulseek. Retrying…", "Sin conexión con Soulseek. Reintentando…")
    val unexpectedReply get() = t("Unexpected reply from the server", "Respuesta inesperada del servidor")
    fun loginError(reason: String, detail: String?) = when (reason) {
        "INVALIDPASS" -> t("Wrong password for that username", "Contraseña incorrecta para ese usuario")
        "INVALIDUSERNAME" -> t("Invalid username", "Nombre de usuario no válido") + (detail?.let { " ($it)" } ?: "")
        "EMPTYPASSWORD" -> t("The password can't be empty", "La contraseña no puede estar vacía")
        "INVALIDVERSION" -> t("The server doesn't accept this client version", "El servidor no acepta esta versión del cliente")
        "SVRFULL" -> t("The server is full, try again later", "El servidor está lleno, prueba más tarde")
        "SVRPRIVATE" -> t("The server isn't accepting new accounts right now", "El servidor no admite registros nuevos ahora mismo")
        else -> t("Login rejected: $reason", "Inicio de sesión rechazado: $reason")
    }
    val loggedInElsewhere get() = t("You logged in with this username somewhere else", "Has iniciado sesión con este usuario en otro sitio")
    val userUnreachable get() = t("The user can't be reached", "El usuario no es accesible")
    fun couldNotConnectTo(user: String) = t("Couldn't connect to $user", "No se pudo conectar con $user")
    val uploaderCancelled get() = t("The user cancelled the upload", "El usuario canceló la subida")
    fun denied(reason: String) = when (reason.trimEnd('.')) {
        "File not shared" -> t("The file is no longer shared", "El archivo ya no está compartido")
        "Banned" -> t("This user doesn't let you download", "Este usuario no te permite descargar")
        "Too many files", "Too many megabytes" -> t("You've hit this user's limit", "Has superado el límite de este usuario")
        "Pending shutdown" -> t("The user is going offline", "El usuario se está desconectando")
        "Cancelled" -> t("Cancelled by the user", "Cancelada por el usuario")
        else -> reason
    }
    fun connectionDropped(pct: Long) = t("Connection dropped at $pct %", "La conexión se cortó al $pct %")
    val downloadError get() = t("Download error", "Error al descargar")
    val chooseDownloadsFolder get() = t("Choose a downloads folder in Settings", "Elige una carpeta de descargas en Ajustes")
    fun couldNotCreateFolder(name: String) = t("Couldn't create the folder “$name”", "No se pudo crear la carpeta «$name»")
    val couldNotCreateFile get() = t("Couldn't create the file", "No se pudo crear el archivo")
    val noWritePermission get() = t("No write permission for the folder", "Sin permiso de escritura en la carpeta")
    val preparingDownloads get() = t("Preparing downloads…", "Preparando descargas…")
    fun downloadingFiles(count: Int) = t("Downloading $count file(s)", "Descargando $count archivo(s)")
    fun queuedFiles(count: Int) = t("$count queued", "$count en cola")
    val downloadsFinished get() = t("Downloads finished", "Descargas terminadas")
    fun addedToLibrary(count: Int) = t("$count file(s) added to your library", "$count archivo(s) añadidos a tu biblioteca")

    // ------------------------------------------------------------------ Descubrir
    val searchOnSoulseek get() = t("Search on Soulseek", "Buscar en Soulseek")
    val searchArtistOnSoulseek get() = t("Search the artist on Soulseek", "Buscar al artista en Soulseek")
    val openInLibrary get() = t("Open in your library", "Abrir en tu biblioteca")
    val notInLibrary get() = t("Not in your library", "No está en tu biblioteca")
    val tapSongToSearch get() = t("Tap ▶ to hear a 30-second preview, or a song to search for it on Soulseek.", "Toca ▶ para escuchar 30 segundos, o la canción para buscarla en Soulseek.")
    val playPreview get() = t("Play preview", "Escuchar vista previa")
    val stopPreview get() = t("Stop preview", "Parar vista previa")
    val previewFailed get() = t("This preview can't be played right now", "No se puede reproducir esta vista previa ahora")
    val artistsToExplore get() = t("Artists to explore", "Artistas para explorar")
    val inYourLibrary get() = t("In your library", "En tu biblioteca")
    fun fans(v: String) = t("$v fans", "$v seguidores")
    val fansAlsoLike get() = t("Fans also like", "A sus fans también les gusta")
    val couldNotLoadArtist get() = t("Couldn't load this artist. Check your connection.", "No se pudo cargar este artista. Revisa la conexión.")
    val couldNotLoadAlbum get() = t("Couldn't load this album. Check your connection.", "No se pudo cargar este álbum. Revisa la conexión.")

    // ------------------------------------------------------------------ Informe de fallos
    val crashTitle get() = t("Surco closed unexpectedly", "Surco se cerró inesperadamente")
    val crashText get() = t(
        "Here's what went wrong. Copy it and send it so it can be fixed.",
        "Esto es lo que falló. Cópialo y envíalo para que se pueda arreglar.",
    )
    val copyReport get() = t("Copy report", "Copiar informe")

    // ------------------------------------------------------------------ Mensajes del sistema
    val folderUnavailable get() = t(
        "Can't read your music folder (permission lost or storage unavailable). Choose it again in Settings.",
        "No se puede leer tu carpeta de música (permiso perdido o almacenamiento no disponible). Vuelve a elegirla en Ajustes.",
    )
    val searchingFiles get() = t("Looking for files…", "Buscando archivos…")
    val invalidImage get() = t("The image isn't valid", "La imagen no es válida")
    val allAlbumsHaveCovers get() = t("Every album has a cover", "Todos los álbumes tienen portada")
    fun foundOf(found: Int, total: Int) = t("Found $found of $total", "Encontradas $found de $total")
    val done get() = t("Done", "Listo")
    val offlineScrobbles get() = t("Offline: they'll be sent later", "Sin conexión: se enviarán más tarde")
    val cannotOpenFile get() = t("Can't open the file", "No se puede abrir el archivo")
    fun formatNotSupported(ext: String) = t(".$ext isn't supported for tag editing", "Formato .$ext no soportado para editar etiquetas")
    fun couldNotEmbedCover(ext: String) = t("Couldn't embed the cover in .$ext", "No se pudo incrustar la portada en .$ext")
    val couldNotSaveTags get() = t("Couldn't save the tags", "No se pudieron guardar las etiquetas")
    val missingLastfmKeys get() = t("The Last.fm API key or secret is missing", "Falta la API key o el secret de Last.fm")
    val emptyLastfmReply get() = t("Empty reply from Last.fm", "Respuesta vacía de Last.fm")
    fun invalidLastfmReply(code: Int) = t("Invalid reply from Last.fm ($code)", "Respuesta inválida de Last.fm ($code)")

    // ------------------------------------------------------------------ Álbum: cola y borrado
    val addAlbumToQueue get() = t("Add album to queue", "Añadir álbum a la cola")
    fun albumAddedToQueue(count: Int) = n(count, "1 song added to the queue", "# songs added to the queue", "1 canción añadida a la cola", "# canciones añadidas a la cola")
    val albumWillPlayNext get() = t("The album will play next", "El álbum sonará a continuación")
    val deleteAlbum get() = t("Delete album from device", "Borrar álbum del dispositivo")
    fun deleteAlbumQuestion(name: String) = t("Delete “$name”?", "¿Borrar «$name»?")
    fun deleteAlbumWarning(count: Int) = n(
        count,
        "This permanently deletes its 1 file from your device, and its folder if no music is left in it. It can't be undone.",
        "This permanently deletes its # files from your device, and its folder if no music is left in it. It can't be undone.",
        "Se borrará para siempre su archivo del dispositivo, y su carpeta si no le queda música. No se puede deshacer.",
        "Se borrarán para siempre sus # archivos del dispositivo, y su carpeta si no le queda música. No se puede deshacer.",
    )
    val deleteAlbumSure get() = t("Are you sure?", "¿Seguro?")
    fun deleteAlbumFinal(count: Int, name: String, artist: String) = n(
        count,
        "1 song of “$name” by $artist will be erased from your device. There is no recycle bin.",
        "# songs of “$name” by $artist will be erased from your device. There is no recycle bin.",
        "Se borrará 1 canción de «$name» de $artist de tu dispositivo. No hay papelera.",
        "Se borrarán # canciones de «$name» de $artist de tu dispositivo. No hay papelera.",
    )
    val deleteForever get() = t("Delete forever", "Borrar para siempre")
    fun albumSpansFolders(count: Int) = t(
        "Careful: these songs are spread across $count different folders.",
        "Ojo: estas canciones están repartidas en $count carpetas distintas.",
    )
    val keepAlbum get() = t("Keep it", "Conservarlo")
    val deleting get() = t("Deleting…", "Borrando…")
    fun albumDeleted(name: String) = t("“$name” deleted", "«$name» borrado")
    fun albumDeletedPartly(deleted: Int, failed: Int) = t(
        "$deleted files deleted; $failed couldn't be deleted",
        "$deleted archivos borrados; $failed no se pudieron borrar",
    )
    val couldNotDelete get() = t("Couldn't delete the album", "No se pudo borrar el álbum")

    // ------------------------------------------------------------------ Datos del álbum desde internet
    val fillFromInternet get() = t("Fill in info from the internet", "Completar datos desde internet")
    val fillFromInternetHelp get() = t(
        "Search MusicBrainz and Deezer by album and artist. You'll see every change before anything is saved.",
        "Busca en MusicBrainz y Deezer por álbum y artista. Verás cada cambio antes de guardar nada.",
    )
    val noAlbumsFound get() = t("No albums found. Try other words.", "No se encontraron álbumes. Prueba con otras palabras.")
    fun pickTheEdition(local: Int) = t(
        "Pick the edition that matches yours (you have $local songs; editions with the same count are highlighted).",
        "Elige la edición que coincide con la tuya (tienes $local canciones; se resaltan las que tienen las mismas).",
    )
    val couldNotLoadAlbumInfo get() = t("Couldn't load that album. Try another result.", "No se pudo cargar ese álbum. Prueba otro resultado.")
    val reviewChanges get() = t("Review changes", "Revisar cambios")
    fun matchedOf(matched: Int, total: Int) = t("$matched of $total songs matched", "$matched de $total canciones emparejadas")
    val onlyFillEmpty get() = t("Only fill in what's missing", "Sólo completar lo que falta")
    val onlyFillEmptyDesc get() = t("Keep the info your files already have", "Conserva los datos que ya tienen tus archivos")
    val songTitles get() = t("Song titles", "Títulos de las canciones")
    val trackAndDiscNumbers get() = t("Track and disc numbers", "Números de pista y disco")
    val trackArtists get() = t("Song artists", "Artistas de cada canción")
    val cover get() = t("Cover", "Portada")
    val replacesCurrentCover get() = t("Replaces the current cover", "Sustituye la portada actual")
    val albumHasNoCover get() = t("The album has no cover yet", "El álbum aún no tiene portada")
    fun noMatchKeepsTitle(title: String) = t("No match · keeps “$title”", "Sin pareja · se queda «$title»")
    fun applyToSongs(count: Int) = n(count, "Save changes to 1 song", "Save changes to # songs", "Guardar cambios en 1 canción", "Guardar cambios en # canciones")
    val applyCoverOnly get() = t("Save cover", "Guardar portada")
    fun couldNotEditSomeCount(count: Int) = n(count, "1 file couldn't be edited", "# files couldn't be edited", "No se pudo editar 1 archivo", "No se pudieron editar # archivos")

    // ------------------------------------------------------------------ Foto de artista
    fun artistPhotoOf(name: String) = t("Photo of $name", "Foto de $name")
    val changeArtistPhoto get() = t("Change photo", "Cambiar foto")
    val chooseFromGallery get() = t("Choose from gallery", "Elegir de la galería")
    val searchOnline get() = t("Search online", "Buscar en internet")
    val useAutomaticPhoto get() = t("Use the automatic photo", "Usar la foto automática")
    val chooseAPhoto get() = t("Choose a photo", "Elige una foto")
    val artistPhotoUpdated get() = t("Artist photo updated", "Foto del artista actualizada")
    val noArtistPhotoFound get() = t("No photos found", "No se encontraron fotos")
    val saving get() = t("Saving…", "Guardando…")

    // ------------------------------------------------------------------ Paleta de colores
    val appearance get() = t("Appearance", "Apariencia")
    val colorPalette get() = t("Color palette", "Paleta de colores")
    val colorPaletteDesc get() = t("Shuffle a random palette or pick each color", "Sortea una paleta al azar o elige cada color")
    val shufflePalette get() = t("Shuffle", "Sortear")
    val customize get() = t("Customize", "Personalizar")
    val undo get() = t("Undo", "Deshacer")
    val resetPalette get() = t("Back to the original colors", "Volver a los colores originales")
    val tapColorToEdit get() = t("Tap a color to edit it", "Toca un color para editarlo")
    val colorAccent get() = t("Accent", "Acento")
    val colorBackground get() = t("Background", "Fondo")
    val colorSurface get() = t("Cards", "Tarjetas")
    val colorText get() = t("Text", "Texto")
    val hue get() = t("Hue", "Tono")
    val saturation get() = t("Saturation", "Saturación")
    val brightness get() = t("Brightness", "Brillo")
    val hexCode get() = t("Hex code", "Código hexadecimal")
    val previewTitle get() = t("Good evening", "Buenas tardes")
    val previewSong get() = t("Song title", "Título de la canción")
    val previewArtist get() = t("Artist • Album", "Artista • Álbum")
    val previewCaption get() = t("This is how Surco will look", "Así se verá Surco")
    val lowContrastText get() = t("Text may be hard to read on this background", "El texto puede costar de leer sobre este fondo")
    val lowContrastAccent get() = t("The accent color barely stands out from the background", "El color de acento apenas se distingue del fondo")

    // ------------------------------------------------------------------ Escaneo automático
    val autoScan get() = t("Scan automatically", "Escanear automáticamente")
    val autoScanDesc get() = t(
        "Add new songs and folders to the library as soon as they appear in your music folder",
        "Añade a la biblioteca las canciones y carpetas nuevas en cuanto aparecen en tu carpeta de música",
    )

    // ------------------------------------------------------------------ Enlaces de Spotify y compartir
    val spotifyLink get() = t("Spotify link", "Enlace de Spotify")
    val readingLink get() = t("Reading the link…", "Leyendo el enlace…")
    val couldNotReadLink get() = t(
        "Couldn't read that link. Check your connection, or that it's an album, song or artist link.",
        "No se pudo leer el enlace. Comprueba la conexión, o que sea de un álbum, una canción o un artista.",
    )
    fun linkKind(kind: String) = when (kind) {
        "ALBUM" -> t("Album", "Álbum")
        "TRACK" -> t("Song", "Canción")
        else -> t("Artist", "Artista")
    }
    fun fromAlbum(album: String) = t("from “$album”", "de «$album»")
    val seeAlbumDetails get() = t("See the album", "Ver el álbum")
    val share get() = t("Share", "Compartir")
    val shareSpotifyLink get() = t("Spotify link", "Enlace de Spotify")
    fun shareAudioFile(ext: String) = t("Audio file${if (ext.isNotBlank()) " ($ext)" else ""}", "Archivo de audio${if (ext.isNotBlank()) " ($ext)" else ""}")
    val findingSpotifyLink get() = t("Looking for the Spotify link…", "Buscando el enlace de Spotify…")
    val sharedSpotifySearch get() = t(
        "No exact Spotify link was found, so a Spotify search was shared instead",
        "No se encontró el enlace exacto: se compartió una búsqueda de Spotify",
    )

    // ------------------------------------------------------------------ Biografía
    val aboutArtist get() = t("About", "Acerca de")
    val showMore get() = t("Show more", "Ver más")
    val showLess get() = t("Show less", "Ver menos")
    fun bioSource(source: String, lang: String) =
        source + (if (lang == "en" && I18n.lang == Lang.ES) " · en inglés" else "") + if (source == "Wikipedia") " · CC BY-SA" else ""

    // ------------------------------------------------------------------ OLED
    val oledBlack get() = t("Pure black (OLED)", "Negro puro (OLED)")
    val oledDetected get() = t(
        "Your screen looks like OLED: turn this on and the black background switches pixels off, saving battery",
        "Tu pantalla parece OLED: si lo activas, el fondo negro apaga los píxeles y ahorra batería",
    )
    val oledNotDetected get() = t(
        "Your screen doesn't seem to be OLED, but you can turn it on anyway",
        "Tu pantalla no parece OLED, pero puedes activarlo igualmente",
    )

    // ------------------------------------------------------------------ Biografía editable
    val noConnection get() = t("No connection", "Sin conexión")
    val storage get() = t("Storage", "Almacenamiento")
    val appCache get() = t("App cache", "Caché de la app")
    fun appCacheDesc(size: String) = t("Downloaded images, internet data and temporary files: $size. Your music, playlists and settings are not touched.", "Imágenes descargadas, datos de internet y archivos temporales: $size. Tu música, tus listas y tus ajustes no se tocan.")
    val clearCache get() = t("Clear cache", "Borrar la caché")
    val wipeAll get() = t("Erase all data", "Borrar todos los datos")
    val wipeAllDesc get() = t("Leaves Surco as if just installed: library, playlists, likes, settings, covers and cache. Your music files are not touched.", "Deja Surco como recién instalada: biblioteca, listas, favoritas, ajustes, portadas y caché. Tus archivos de música no se tocan.")
    val wipeAllButton get() = t("Erase everything and start over", "Borrar todo y empezar de cero")
    val wiping get() = t("Erasing…", "Borrando…")
    val wipeAllQuestion get() = t("Erase everything?", "¿Borrar todo?")
    val wipeAllExplain get() = t("Surco will close. When you open it again it will start from scratch and ask you to choose your music folder. This can't be undone.", "Surco se cerrará. Al abrirla de nuevo empezará desde cero y te pedirá elegir la carpeta de música. No se puede deshacer.")
    val wipeAllConfirm get() = t("Erase everything", "Borrar todo")
    val clearingCache get() = t("Clearing…", "Borrando…")
    val clearCacheQuestion get() = t("Clear the whole cache?", "¿Borrar toda la caché?")
    val clearCacheExplain get() = t("Images and internet data will be downloaded again when needed. Your library, playlists, likes, stats and settings stay as they are.", "Las imágenes y los datos de internet se volverán a descargar cuando hagan falta. Tu biblioteca, listas, favoritas, estadísticas y ajustes se quedan como están.")
    fun cacheCleared(size: String) = t("Cache cleared ($size freed)", "Caché borrada ($size liberados)")
    val connectWithLastfm get() = t("Connect with Last.fm", "Conectar con Last.fm")
    val connectWithLastfmHelp get() = t("Opens Last.fm: tap «Yes, allow access» and come back. No keys or passwords in Surco.", "Se abre Last.fm: pulsa «Sí, permitir acceso» y vuelve. Sin claves ni contraseñas en Surco.")
    val waitingForLastfm get() = t("Waiting for Last.fm…", "Esperando a Last.fm…")
    val waitingForLastfmHelp get() = t("Allow access on the Last.fm page and come back: it connects by itself.", "Permite el acceso en la página de Last.fm y vuelve: se conecta solo.")
    val usePasswordLogin get() = t("Or sign in with username and password", "O entrar con usuario y contraseña")
    val hidePasswordLogin get() = t("Hide username and password", "Ocultar usuario y contraseña")
    val searchOnDiscogs get() = t("Search on Discogs", "Buscar en Discogs")
    val searchOnRym get() = t("Search on Rate Your Music", "Buscar en Rate Your Music")
    val notFoundOnline get() = t("Couldn't find it online", "No se ha encontrado en internet")
    val noBioFound get() = t("No biography was found online", "No se encontró ninguna biografía en internet")
    val bioOptions get() = t("Biography options", "Opciones de la biografía")
    val editBio get() = t("Edit biography", "Editar biografía")
    val writeBio get() = t("Write a biography", "Escribir una biografía")
    val searchBioOnline get() = t("Search online", "Buscar en internet")
    val deleteBio get() = t("Delete biography", "Eliminar biografía")
    val bioWrittenByYou get() = t("Written by you", "Escrita por ti")
    val noBioYet get() = t("No biography yet", "Aún no hay biografía")
    fun bioOf(name: String) = t("About $name", "Sobre $name")
    val bioPlaceholder get() = t("Write about this artist…", "Escribe sobre este artista…")

    // ------------------------------------------------------------------ Spotify (clave propia)
    val spotifyKeyTitle get() = t("Exact Spotify links", "Enlaces exactos de Spotify")
    val spotifyKeyDesc get() = t(
        "Without a key, Surco finds the exact link through MusicBrainz and Wikidata (most known albums and songs). " +
            "To get it every time, create a free app at developer.spotify.com (needs a Premium account) and paste its Client ID and Client secret here.",
        "Sin clave, Surco busca el enlace exacto en MusicBrainz y Wikidata (la mayoría de discos y canciones conocidos). " +
            "Para tenerlo siempre, crea una app gratuita en developer.spotify.com (necesita una cuenta Premium) y pega aquí su Client ID y Client secret.",
    )
    val spotifyClientId get() = t("Client ID", "Client ID")
    val spotifyClientSecret get() = t("Client secret", "Client secret")
    val openSpotifyDashboard get() = t("Open the Spotify dashboard", "Abrir el panel de Spotify")
    val spotifyKeySaved get() = t("Spotify key saved", "Clave de Spotify guardada")

    // ------------------------------------------------------------------ Temporizador
    val sleepTimer get() = t("Sleep timer", "Temporizador")
    fun minutes(n: Int) = t("$n minutes", "$n minutos")
    val endOfTrack get() = t("End of the song", "Al acabar la canción")
    val turnOffTimer get() = t("Turn off timer", "Apagar el temporizador")
    fun timerStopsIn(time: String) = t("Music stops in $time", "La música se para en $time")
    val timerStopsAtEnd get() = t("Music stops when this song ends", "La música se para al acabar esta canción")
    fun timerSet(n: Int) = t("Music will stop in $n minutes", "La música se parará en $n minutos")
    val timerOff get() = t("Sleep timer off", "Temporizador apagado")

    // ------------------------------------------------------------------ Estadísticas (Last.fm)
    val stats get() = t("Stats", "Estadísticas")
    val statsConnectTitle get() = t("Your music, in numbers", "Tu música, en números")
    val statsConnectBody get() = t(
        "Connect your Last.fm account to see when you listen the most, your top artists, albums and songs, your streaks, your musical DNA and much more.",
        "Conecta tu cuenta de Last.fm para ver a qué horas escuchas más, tus artistas, álbumes y canciones top, tus rachas, tu ADN musical y mucho más.",
    )
    val createLastfmAccount get() = t("Create a free Last.fm account", "Crear una cuenta gratis en Last.fm")
    val statsKeysHelp get() = t(
        "Step 1: Last.fm asks apps for a free API key. Create one on the Last.fm website (it takes a minute), paste it here, and then sign in with your account.",
        "Paso 1: Last.fm pide a las apps una clave gratuita de su API. Créala en la web de Last.fm (es un minuto), pégala aquí y luego inicia sesión con tu cuenta.",
    )
    val noLastfmAccountYet get() = t("Don't have an account yet?", "¿Aún no tienes cuenta?")
    val statsReadOnlyNote get() = t(
        "Surco only reads your stats here. It won't send scrobbles unless you turn that on in Settings.",
        "Aquí Surco sólo lee tus estadísticas. No enviará scrobbles a menos que lo actives en Ajustes.",
    )
    val statsLoadFailed get() = t("Couldn't load your stats. Check your connection.", "No se pudieron cargar tus estadísticas. Comprueba la conexión.")
    val lastfmUserNotFound get() = t("Last.fm doesn't know that user.", "Last.fm no conoce a ese usuario.")
    fun readingScrobbles(pct: Int) = t("Reading your scrobbles… $pct%", "Leyendo tus escuchas… $pct%")
    fun periodChip(p: String) = when (p) {
        "WEEK" -> t("7 days", "7 días")
        "MONTH" -> t("Month", "Mes")
        "QUARTER" -> t("3 months", "3 meses")
        "HALF" -> t("6 months", "6 meses")
        "YEAR" -> t("Year", "Año")
        else -> t("All time", "Siempre")
    }
    fun periodTitle(p: String) = when (p) {
        "WEEK" -> t("Last 7 days", "Últimos 7 días")
        "MONTH" -> t("Last month", "Último mes")
        "QUARTER" -> t("Last 3 months", "Últimos 3 meses")
        "HALF" -> t("Last 6 months", "Últimos 6 meses")
        "YEAR" -> t("Last year", "Último año")
        else -> t("All time", "Desde siempre")
    }
    val scrobblesTotal get() = t("scrobbles in total", "escuchas en total")
    fun since(month: Int, year: Int) = t("Since ${monthLong(month)} $year", "Desde ${monthLong(month)} de $year")
    val songs get() = t("Songs", "Canciones")
    val searchYourLibrary get() = t("Search Your Library", "Buscar en tu biblioteca")
    val closeSearch get() = t("Close search", "Cerrar búsqueda")
    val clearText get() = t("Clear", "Borrar")
    val scrobblesWord get() = t("Scrobbles", "Escuchas")
    val hoursOfMusic get() = t("Hours of music", "Horas de música")
    val perDay get() = t("Per day", "Al día")
    val clockTitle get() = t("Your listening clock", "Tu reloj musical")
    val clockSub get() = t("The hours you listen to music the most. Tap an hour.", "Las horas a las que más música escuchas. Toca una hora.")
    fun chronotypeName(c: String) = when (c) {
        "EARLY_BIRD" -> t("Early bird", "Madrugador")
        "DAYTIME" -> t("Daytime listener", "De día")
        "AFTERNOON" -> t("Afternoon soul", "Alma de tarde")
        "EVENING" -> t("Golden hour", "Hora dorada")
        else -> t("Night owl", "Noctámbulo")
    }
    fun chronotypeText(c: String, hour: String) = when (c) {
        "EARLY_BIRD" -> t("Your music wakes up with you. Peak at $hour.", "Tu música se despierta contigo. Hora punta: $hour.")
        "DAYTIME" -> t("Music to get through the day. Peak at $hour.", "Música para llevar el día. Hora punta: $hour.")
        "AFTERNOON" -> t("Your moment comes after lunch. Peak at $hour.", "Tu momento llega después de comer. Hora punta: $hour.")
        "EVENING" -> t("You unwind with music as the day ends. Peak at $hour.", "Desconectas con música al caer la tarde. Hora punta: $hour.")
        else -> t("Your best songs play after dark. Peak at $hour.", "Tus mejores canciones suenan de noche. Hora punta: $hour.")
    }
    fun playsShare(n: String, pct: Int) = t("$n plays · $pct%", "$n escuchas · $pct%")
    fun dayPart(i: Int) = when (i) {
        0 -> t("Late night", "Madrugada")
        1 -> t("Morning", "Mañana")
        2 -> t("Afternoon", "Tarde")
        else -> t("Evening", "Noche")
    }
    val weekTitle get() = t("Your week", "Tu semana")
    val weekSub get() = t("Day and hour: the brighter, the more you listen", "Día y hora: cuanto más intenso, más escuchas")
    fun weekdayShort(i: Int) = if (I18n.lang == Lang.ES) "LMXJVSD"[i].toString() else "MTWTFSS"[i].toString()
    fun weekdayLong(i: Int) = if (I18n.lang == Lang.ES)
        listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")[i]
    else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i]
    fun yourMoment(day: Int, hour: String) = t(
        "Your moment: ${weekdayLong(day)}s at $hour",
        "Tu momento: los ${weekdayLong(day).lowercase().let { if (it.endsWith("s")) it else it + "s" }} a las $hour",
    )
    fun slotDetail(day: Int, from: String, to: String, n: String) = t("${weekdayLong(day)} · $from–$to · $n plays", "${weekdayLong(day)} · $from–$to · $n escuchas")
    val heatLess get() = t("Less", "Menos")
    val heatMore get() = t("More", "Más")
    val activityTitle get() = t("Activity", "Actividad")
    fun activitySub(bucketDays: Int) = when (bucketDays) {
        1 -> t("Plays per day", "Escuchas por día")
        7 -> t("Plays per week", "Escuchas por semana")
        else -> t("Plays per month", "Escuchas por mes")
    }
    fun bucketDetail(date: String, n: String) = t("$date · $n plays", "$date · $n escuchas")
    fun weekOf(date: String) = t("Week of $date", "Semana del $date")
    val currentStreak get() = t("Current streak", "Racha actual")
    val bestStreak get() = t("Longest streak", "Racha más larga")
    val activeDays get() = t("Days with music", "Días con música")
    fun daysN(n: Int) = t(if (n == 1) "1 day" else "$n days", if (n == 1) "1 día" else "$n días")
    val recordDay get() = t("Record day", "Día récord")
    val topArtistsTitle get() = t("Top artists", "Artistas top")
    val topAlbumsTitle get() = t("Top albums", "Álbumes top")
    val topTracksTitle get() = t("Top songs", "Canciones top")
    fun playsN(n: String) = t("$n plays", "$n escuchas")
    val genresTitle get() = t("Your musical DNA", "Tu ADN musical")
    val genresSub get() = t("Genres of your top artists, weighted by plays", "Géneros de tus artistas top, según cuánto los escuchas")
    val factsTitle get() = t("Fun facts", "Datos curiosos")
    val factLoyalty get() = t("Loyalty", "Fidelidad")
    val factLoyaltyText get() = t("of your plays are your top 5 artists", "de tus escuchas son de tus 5 artistas top")
    val factVariety get() = t("Variety", "Variedad")
    val factVarietyText get() = t("different artists", "artistas distintos")
    val factObsession get() = t("On repeat", "En bucle")
    fun factObsessionText(title: String, artist: String, date: String) = t("“$title” by $artist on $date", "«$title» de $artist el $date")
    val factMarathon get() = t("Marathon", "Maratón")
    fun factMarathonText(n: Int, date: String) = t("$n songs non-stop on $date", "$n canciones sin parar el $date")
    val factLibrary get() = t("In Surco", "En Surco")
    val factLibraryText get() = t("of what you play is in your library", "de lo que escuchas está en tu biblioteca")
    val factLoved get() = t("Loved", "Favoritas")
    val factLovedText get() = t("songs with ♥ on Last.fm", "canciones con ♥ en Last.fm")
    val viewLastfmProfile get() = t("Open profile on Last.fm", "Ver perfil en Last.fm")
    fun sampleNote(n: String) = t("Charts use your last $n scrobbles", "Los gráficos usan tus últimas $n escuchas")
    val dataFromLastfm get() = t("Data from Last.fm", "Datos de Last.fm")
    val noScrobblesInPeriod get() = t("No scrobbles in this period yet", "Aún no hay escuchas en este periodo")
    fun monthShort(m: Int) = if (I18n.lang == Lang.ES)
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")[m - 1]
    else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m - 1]
    fun monthLong(m: Int) = if (I18n.lang == Lang.ES)
        listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")[m - 1]
    else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[m - 1]
    fun dayMonth(d: Int, m: Int) = if (I18n.lang == Lang.ES) "$d ${monthShort(m)}" else "${monthShort(m)} $d"
    fun monthYear(m: Int, y: Int) = if (I18n.lang == Lang.ES) "${monthShort(m)} $y" else "${monthShort(m)} $y"
    fun hoursMinutes(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    fun topPeriodChip(p: String) = when (p) {
        "WEEK" -> t("7 days", "7 días")
        "MONTH" -> t("30 days", "30 días")
        "QUARTER" -> t("90 days", "90 días")
        "HALF" -> t("180 days", "180 días")
        "YEAR" -> t("365 days", "365 días")
        else -> t("All time", "Siempre")
    }
    fun makeDefaultPeriod(label: String) = t("Open with “$label” by default", "Abrir con «$label» por defecto")
    val defaultPeriod get() = t("Default", "Predeterminado")
    val viewOtherUser get() = t("See someone else's stats", "Ver las estadísticas de otra persona")
    val viewOtherUserHint get() = t("Type a Last.fm username. Their profile has to be public.", "Escribe un usuario de Last.fm. Su perfil tiene que ser público.")
    val lastfmUsername get() = t("Last.fm username", "Usuario de Last.fm")
    val view get() = t("View", "Ver")
    fun viewingUser(u: String) = t("Viewing @$u", "Viendo a @$u")
    val backToMyStats get() = t("Back to mine", "Volver a las mías")
    val seeAll get() = t("See all", "Ver todo")
    val nothingInPeriod get() = t("Nothing here for this period", "No hay nada en este periodo")
    val recentlyPlayed get() = t("Recently played", "Escuchado hace poco")
    val nowLabel get() = t("Now", "Ahora")
    val justNow get() = t("just now", "ahora mismo")
    fun minutesAgo(n: Int) = t("$n min ago", "hace $n min")
    fun hoursAgo(n: Int) = t(if (n == 1) "1 hour ago" else "$n hours ago", if (n == 1) "hace 1 hora" else "hace $n horas")
    val yesterday get() = t("yesterday", "ayer")
    val viewProfile get() = t("View profile", "Ver perfil")
    val listeningStats get() = t("Listening stats", "Estadísticas de escucha")
    val iconFollowsAccent get() = t("App icon matches your color", "Icono a juego con tu color")
    // ---- Audio para audiófilos
    val audiophileIntro get() = t(
        "Everything here is off by default, so your files play exactly as they are. Turn on only what you want.",
        "Todo esto viene apagado: así tus archivos suenan exactamente como son. Activa sólo lo que quieras.",
    )
    val parametricEq get() = t("Parametric equalizer", "Ecualizador paramétrico")
    val eqEnable get() = t("Equalizer", "Ecualizador")
    val eqEnableDesc get() = t("Drag the points on the curve, or pick a band and fine-tune it", "Arrastra los puntos de la curva o elige una banda y ajústala")
    val addBand get() = t("Add band", "Añadir banda")
    val removeBand get() = t("Remove band", "Quitar banda")
    val frequency get() = t("Frequency", "Frecuencia")
    val gain get() = t("Gain", "Ganancia")
    val qFactor get() = t("Q factor", "Factor Q")
    val peakHelp get() = t("Peak: boosts or cuts around the frequency. Higher Q = narrower.", "Peak: sube o baja alrededor de la frecuencia. Más Q = más estrecho.")
    val shelfHelp get() = t("Shelf: boosts or cuts everything below (low) or above (high) the frequency.", "Shelf: sube o baja todo lo que queda por debajo (low) o por encima (high) de la frecuencia.")
    val preamp get() = t("Preamp", "Preamplificador")
    fun preampWarning(v: String) = t("The curve goes above 0 dB: a preamp of $v avoids any limiting.", "La curva pasa de 0 dB: con el preamplificador a $v no hará falta limitar.")
    val preampOk get() = t("No clipping: there's headroom.", "Sin saturación: hay margen.")
    val autoPreamp get() = t("Auto", "Auto")
    val resetEq get() = t("Reset to flat", "Dejar plano")
    val filterPeak get() = t("Peak", "Peak")
    val filterLowShelf get() = t("Low shelf", "Low shelf")
    val filterHighShelf get() = t("High shelf", "High shelf")
    val presetFlat get() = t("Flat", "Plano")
    val presetBass get() = t("Bass boost", "Más graves")
    val presetVocal get() = t("Vocal", "Voces")
    val presetTreble get() = t("Treble boost", "Más agudos")
    val presetLoudness get() = t("Loudness", "Loudness")
    val normalization get() = t("Volume normalization", "Normalización del volumen")
    val normalizeVolume get() = t("Normalize volume", "Normalizar el volumen")
    val normalizeVolumeDesc get() = t("All songs play at a similar loudness", "Que todas las canciones suenen a un volumen parecido")
    val normalizeBy get() = t("Based on", "Según")
    val bySong get() = t("Each song", "Cada canción")
    val byAlbum get() = t("The whole album", "El álbum entero")
    val loudnessLevel get() = t("Level", "Nivel")
    val levelQuiet get() = t("Quiet", "Bajo")
    val levelNormal get() = t("Normal", "Normal")
    val levelLoud get() = t("Loud", "Alto")
    val normalizeHow get() = t(
        "Uses the ReplayGain tags of your files when they have them; otherwise Surco measures each song (EBU R128) the first time you listen to it. It only lowers the volume, never compresses.",
        "Usa las etiquetas ReplayGain de tus archivos si las tienen; si no, Surco mide cada canción (EBU R128) la primera vez que la escuchas. Sólo baja el volumen, nunca comprime.",
    )
    val crossfade get() = t("Crossfade", "Crossfade")
    val crossfadeEnable get() = t("Crossfade songs", "Fundir las canciones")
    val crossfadeDesc get() = t("The next song fades in while the current one fades out", "La siguiente entra mientras la actual se apaga")
    val crossfadeLength get() = t("Length", "Duración")
    val crossfadeAlbums get() = t("Also within an album", "También dentro de un álbum")
    val crossfadeAlbumsDesc get() = t("Off: albums play gapless, as they were made", "Apagado: los discos suenan seguidos, como se grabaron")
    val spectrogram get() = t("Spectrogram", "Espectrograma")
    val spectrogramCover get() = t("Spectrogram instead of the cover", "Espectrograma en lugar de la portada")
    val spectrogramCoverDesc get() = t("In the player. Tip: tap the cover 5 times to switch.", "En el reproductor. Truco: toca 5 veces la portada para cambiar.")
    val uiZoom get() = t("Interface size", "Tamaño de la interfaz")
    val showStatsTab get() = t("Stats tab", "Pestaña «Estadísticas»")
    val showStatsTabDesc get() = t("Your Last.fm listening stats in the bottom bar. Turn off to hide it.", "Tus estadísticas de Last.fm en la barra de abajo. Apágala para esconderla.")
    val uiZoomDesc get() = t("Make everything smaller or larger", "Todo más pequeño o más grande")
    val resetZoom get() = t("Back to 100 %", "Volver al 100 %")
    val sound get() = t("Sound", "Sonido")
    val audiophile get() = t("Audio for audiophiles", "Audio para audiófilos")
    val audiophileOff get() = t("Equalizer, volume normalization and crossfade · all off", "Ecualizador, normalización del volumen y crossfade · todo apagado")
    val eqShort get() = t("Equalizer", "Ecualizador")
    val normalizeShort get() = t("Normalization", "Normalización")
    fun crossfadeShort(sec: Int) = t("Crossfade $sec s", "Crossfade $sec s")
    val iconFollowsAccentDesc get() = t(
        "The app icon takes the shade closest to your accent color.",
        "El icono de la app toma el tono más parecido a tu color de acento.",
    )
}
