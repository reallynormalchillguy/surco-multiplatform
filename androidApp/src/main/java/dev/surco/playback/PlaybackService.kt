package dev.surco.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.surco.MainActivity
import dev.surco.SurcoApp
import dev.surco.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Servicio de reproducción. Expone una MediaSession estándar, así que la notificación,
 * los auriculares, Android Auto/Wear y apps externas como Pano Scrobbler lo ven como cualquier reproductor.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var repo: Repository
    private lateinit var tracker: ScrobbleTracker
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val container = (application as SurcoApp).container
        repo = container.repository

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

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
                player.volume = 1f
                if (t.endsAt <= 0) return@collectLatest
                delay((t.endsAt - SleepTimer.FADE_MS - System.currentTimeMillis()).coerceAtLeast(0))
                // Últimos segundos: baja el volumen poco a poco y pausa.
                while (true) {
                    val left = t.endsAt - System.currentTimeMillis()
                    if (left <= 0) break
                    player.volume = (left.toFloat() / SleepTimer.FADE_MS).coerceIn(0f, 1f)
                    delay(100)
                }
                player.pause()
                player.volume = 1f
                container.sleepTimer.finished()
            }
        }


        restoreQueue()
        repo.flushScrobblesAsync()
    }

    private fun onTrackChanged(item: MediaItem?) {
        val uri = item?.mediaId
        scope.launch {
            val track = uri?.let { repo.dao.track(it) }
            // Si mientras tanto ya ha cambiado de canción, ignoramos este resultado.
            if (player.currentMediaItem?.mediaId != uri) return@launch
            tracker.onTrackStarted(track, player.isPlaying)
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
