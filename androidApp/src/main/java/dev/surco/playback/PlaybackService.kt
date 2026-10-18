package dev.surco.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.surco.MainActivity
import dev.surco.SurcoApp
import dev.surco.audio.AudioFx
import dev.surco.audio.EqSettings
import dev.surco.audio.Normalization
import dev.surco.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Servicio de reproducción. Expone una MediaSession estándar, así que la notificación,
 * los auriculares, Android Auto/Wear y apps externas como Pano Scrobbler lo ven como cualquier reproductor.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repo: Repository
    private lateinit var tracker: ScrobbleTracker
    private lateinit var processor: SurcoAudioProcessor
    private lateinit var levels: AudioLevels
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val container = (application as SurcoApp).container
        repo = container.repository

        levels = AudioLevels(this)
        val (main, proc) = buildPlayer(main = true)
        player = main
        processor = proc

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    // Los MediaItems que llegan desde un controlador pueden venir sin URI: la reconstruimos.
                    val fixed = mediaItems.map { item ->
                        if (item.localConfiguration == null) item.buildUpon().setUri(item.mediaId).build() else item
                    }.toMutableList()
                    return Futures.immediateFuture(fixed)
                }
            })
            .build()

        tracker = ScrobbleTracker(scope, repo)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onTrackChanged(mediaItem)
                saveQueue()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) player.duration.takeIf { it != C.TIME_UNSET }?.let(tracker::onDuration)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                tracker.onPlayingChanged(isPlaying)
                if (!isPlaying) saveQueue()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // El temporizador «al acabar la canción» ya ha parado la música: se apaga.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    container.sleepTimer.state.value.endOfTrack
                ) container.sleepTimer.finished()
            }
        })

        // Temporizador para dormir (sigue funcionando con la app cerrada, mientras suena la música).
        scope.launch {
            container.sleepTimer.state.collectLatest { t ->
                player.pauseAtEndOfMediaItems = t.endOfTrack
                sleepFactor = 1f; applyVolume()
                if (t.endsAt <= 0) return@collectLatest
                delay((t.endsAt - SleepTimer.FADE_MS - System.currentTimeMillis()).coerceAtLeast(0))
                // Últimos segundos: baja el volumen poco a poco y pausa.
                while (true) {
                    val left = t.endsAt - System.currentTimeMillis()
                    if (left <= 0) break
                    sleepFactor = (left.toFloat() / SleepTimer.FADE_MS).coerceIn(0f, 1f); applyVolume()
                    delay(100)
                }
                player.pause()
                sleepFactor = 1f; applyVolume()
                container.sleepTimer.finished()
            }
        }

        // Ecualizador y normalización: siguen a los ajustes (también con la app cerrada).
        scope.launch {
            var last: Triple<Boolean, Int, Boolean>? = null
            container.prefs.settings.collect { s ->
                AudioFx.setEq(s.eqEnabled, EqSettings.decode(s.eq))
                val norm = Triple(s.normalize, s.normalizeTarget, s.normalizeAlbum)
                if (norm != last) { last = norm; refreshGain() }
            }
        }
        // Crossfade: vigila el final de cada canción.
        scope.launch {
            while (true) {
                val near = checkCrossfade()
                collectMeasure()
                delay(if (near) 25 else 250)
            }
        }

        restoreQueue()
        repo.flushScrobblesAsync()
    }

    // ---------- Audio: reproductores con el procesado de Surco ----------

    /** Un ExoPlayer con la cadena de audio de Surco (ecualizador, limitador, medición, espectrograma). */
    private fun buildPlayer(main: Boolean): Pair<ExoPlayer, SurcoAudioProcessor> {
        val proc = SurcoAudioProcessor(main)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(proc))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        val p = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                main, // sólo el principal gestiona el foco de audio
            )
            .setHandleAudioBecomingNoisy(main)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        return p to proc
    }

    /** Volumen del principal = normalización × fundido de entrada (crossfade) × temporizador. */
    private var normGain = 1f
    private var sleepFactor = 1f
    private var fadeIn = 1f

    private fun applyVolume() {
        player.volume = (normGain * sleepFactor * fadeIn).coerceIn(0f, 1f)
    }

    private var gainJob: Job? = null

    /** Recalcula la normalización de la canción actual. */
    private fun refreshGain() {
        gainJob?.cancel()
        val s = container.prefs.current
        val uri = player.currentMediaItem?.mediaId
        if (!s.normalize || uri == null) {
            AudioFx.measureKey = null
            normGain = 1f; applyVolume()
            return
        }
        gainJob = scope.launch {
            val t = repo.dao.track(uri) ?: return@launch
            val album = runCatching { repo.dao.albumTracksOnce(t.albumKey) }.getOrDefault(emptyList())
            val (db, measure) = levels.gainFor(t, album, s)
            if (player.currentMediaItem?.mediaId != uri) return@launch
            AudioFx.measureKey = if (measure) uri else null
            normGain = db?.let { Normalization.linear(it) } ?: 1f
            applyVolume()
        }
    }

    /** Guarda la sonoridad medida de la última canción escuchada entera. */
    private fun collectMeasure() {
        val m = AudioFx.measured ?: return
        AudioFx.measured = null
        levels.saveMeasured(m.first, m.second)
    }

    private val container get() = (application as SurcoApp).container

    /** Reproductor que toca el final de la canción anterior mientras empieza la siguiente. */
    private var fader: ExoPlayer? = null
    private var faderFor: String? = null
    private var fadeJob: Job? = null

    /** Prepara y lanza el crossfade cuando toca. Devuelve true si está a punto (para vigilar más a menudo). */
    private fun checkCrossfade(): Boolean {
        if (fadeJob?.isActive == true) return false
        val s = container.prefs.current
        val cur = player.currentMediaItem
        if (!s.crossfade || cur == null || !player.isPlaying || player.repeatMode == Player.REPEAT_MODE_ONE ||
            container.sleepTimer.state.value.endOfTrack || !player.hasNextMediaItem()
        ) { dropFader(); return false }
        val dur = player.duration
        val fadeMs = s.crossfadeSec * 1000L
        if (dur == C.TIME_UNSET || dur < fadeMs * 2 + 10_000) return false
        val next = player.getMediaItemAt(player.nextMediaItemIndex)
        if (!s.crossfadeAlbums && sameAlbum(cur, next)) { dropFader(); return false }
        val start = dur - fadeMs
        val pos = player.currentPosition
        if (pos < start - 5_000) {
            if (faderFor != cur.mediaId) dropFader()
            return false
        }
        if (fader == null || faderFor != cur.mediaId) {
            dropFader()
            val (f, _) = buildPlayer(main = false)
            f.setMediaItem(cur, start)
            f.volume = 0f
            f.playWhenReady = false
            f.prepare()
            fader = f
            faderFor = cur.mediaId
        }
        if (pos >= start - 15) startFade(fadeMs)
        return true
    }

    private fun sameAlbum(a: MediaItem, b: MediaItem): Boolean {
        val x = a.mediaMetadata; val y = b.mediaMetadata
        val album = x.albumTitle?.toString().orEmpty()
        return album.isNotBlank() && album.equals(y.albumTitle?.toString(), ignoreCase = true) &&
            (x.albumArtist ?: x.artist)?.toString().equals((y.albumArtist ?: y.artist)?.toString(), ignoreCase = true)
    }

    private fun startFade(fadeMs: Long) {
        val f = fader ?: return
        if (f.playbackState != Player.STATE_READY) return
        val outGain = normGain * sleepFactor
        f.volume = outGain
        f.play()
        fadeIn = 0f; applyVolume()
        player.seekToNextMediaItem()
        fadeJob = scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            try {
                while (true) {
                    val k = ((SystemClock.elapsedRealtime() - t0).toFloat() / fadeMs).coerceIn(0f, 1f)
                    // Potencia constante: la suma suena igual de fuerte durante todo el fundido.
                    fadeIn = sin(k * PI / 2).toFloat(); applyVolume()
                    f.volume = (cos(k * PI / 2).toFloat() * outGain * sleepFactor).coerceIn(0f, 1f)
                    if (k >= 1f) break
                    if (!player.playWhenReady) { if (f.isPlaying) f.pause() } else if (!f.playWhenReady) f.play()
                    delay(30)
                }
            } finally {
                f.release()
                if (fader === f) { fader = null; faderFor = null }
                fadeIn = 1f; applyVolume()
            }
        }
    }

    private fun dropFader() {
        if (fadeJob?.isActive == true) return
        fader?.release()
        fader = null
        faderFor = null
    }

    private fun onTrackChanged(item: MediaItem?) {
        val uri = item?.mediaId
        scope.launch {
            val track = uri?.let { repo.dao.track(it) }
            // Si mientras tanto ya ha cambiado de canción, ignoramos este resultado.
            if (player.currentMediaItem?.mediaId != uri) return@launch
            tracker.onTrackStarted(track, player.isPlaying)
            refreshGain()
            if (player.playbackState == Player.STATE_READY) player.duration.takeIf { it != C.TIME_UNSET }?.let(tracker::onDuration)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        tracker.finish()
        collectMeasure()
        processor.currentMeasure()?.let { (k, l) -> levels.saveMeasured(k, l) }
        fadeJob?.cancel()
        fader?.release()
        fader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Recordar la cola entre sesiones ----------
    private fun saveQueue() {
        val sp = getSharedPreferences("queue", Context.MODE_PRIVATE)
        val count = player.mediaItemCount
        if (count == 0) {
            sp.edit().clear().apply(); return
        }
        val uris = JSONArray()
        for (i in 0 until count) uris.put(player.getMediaItemAt(i).mediaId)
        sp.edit()
            .putString("uris", uris.toString())
            .putInt("index", player.currentMediaItemIndex)
            .putLong("position", player.currentPosition)
            .putString("source", player.currentMediaItem?.source() ?: "")
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    private fun restoreQueue() {
        val sp = getSharedPreferences("queue", Context.MODE_PRIVATE)
        val json = sp.getString("uris", null) ?: return
        val index = sp.getInt("index", 0)
        val position = sp.getLong("position", 0L)
        val source = sp.getString("source", "") ?: ""
        val shuffle = sp.getBoolean("shuffle", false)
        val repeat = sp.getInt("repeat", Player.REPEAT_MODE_OFF)
        scope.launch {
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@launch
            val uris = (0 until arr.length()).map { arr.getString(it) }
            val byUri = uris.chunked(500).flatMap { repo.dao.tracksByUris(it) }.associateBy { it.uri }
            val items = uris.mapNotNull { byUri[it]?.toMediaItem(source) }
            if (items.isEmpty() || player.mediaItemCount > 0) return@launch
            player.setMediaItems(items, index.coerceIn(0, items.size - 1), position)
            player.shuffleModeEnabled = shuffle
            player.repeatMode = repeat
            player.prepare()
        }
    }
}
