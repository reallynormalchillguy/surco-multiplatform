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
    val tapSongToSearch get() = t("Tap a song to search for it on Soulseek.", "Toca una canción para buscarla en Soulseek.")
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
}
