package dev.surco.ios

import dev.surco.audio.AudioFx
import dev.surco.audio.EqSettings
import dev.surco.audio.Normalization
import dev.surco.data.Repository
import dev.surco.data.Track
import dev.surco.playback.PlayerController
import dev.surco.playback.PlayerUiState
import dev.surco.playback.Repeat
import dev.surco.playback.ScrobbleTracker
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.volume
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

/**
 * Reproductor de iOS con AVFoundation: cola propia (con aleatorio y repetición), audio en segundo plano,
 * controles en la pantalla de bloqueo / Centro de control y scrobbles con las mismas reglas que Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlayer(private val scope: CoroutineScope, private val repo: Repository, private val sleepTimer: dev.surco.playback.SleepTimer) : PlayerController {
    private val player = AVPlayer()
    private val tracker = ScrobbleTracker(scope, repo)
    private val _state = MutableStateFlow(PlayerUiState(connected = true))
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    /** Orden de reproducción: índices de [queue]. */
    private var order: List<Int> = emptyList()
    /** Posición actual dentro de [order]. */
    private var pos = -1
    private var source = ""
    private var shuffle = false
    private var repeat = Repeat.OFF
    private var playing = false
    private var sessionReady = false

    init {
        player.addPeriodicTimeObserverForInterval(CMTimeMakeWithSeconds(0.25, 600), null) { _ -> tick() }
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n ->
            if (n?.`object` == player.currentItem) onEnded()
        }
        center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n ->
            if (n?.`object` == player.currentItem) onEnded()
        }
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { n ->
            onInterruption(n)
        }
        setupRemoteCommands()
        // Temporizador para dormir: en los últimos segundos baja el volumen poco a poco y pausa.
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            sleepTimer.state.collectLatest { t ->
                sleepFactor = 1f; applyVolume()
                if (t.endsAt <= 0) return@collectLatest
                delay((t.endsAt - dev.surco.playback.SleepTimer.FADE_MS - dev.surco.util.nowMillis()).coerceAtLeast(0))
                while (true) {
                    val left = t.endsAt - dev.surco.util.nowMillis()
                    if (left <= 0) break
                    sleepFactor = (left.toFloat() / dev.surco.playback.SleepTimer.FADE_MS).coerceIn(0f, 1f); applyVolume()
                    delay(100)
                }
                if (playing) pause()
                sleepFactor = 1f; applyVolume()
                sleepTimer.finished()
            }
        }
        // Audio para audiófilos: ecualizador y normalización siguen a los ajustes.
        AudioFx.tapLatencyMs = 40
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            var last: Triple<Boolean, Int, Boolean>? = null
            repo.prefs.settings.collect { s ->
                AudioFx.setEq(s.eqEnabled, EqSettings.decode(s.eq))
                val norm = Triple(s.normalize, s.normalizeTarget, s.normalizeAlbum)
                if (norm != last) { last = norm; refreshGain() }
            }
        }
        // Crossfade (y guardar las medidas de sonoridad).
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            while (true) {
                val near = checkCrossfade()
                collectMeasure()
                delay(if (near) 25 else 250)
            }
        }
    }

    // ---------------------------------------------------------------- Volumen, normalización y crossfade

    private val levels = IosAudioLevels()
    private var normGain = 1f
    private var sleepFactor = 1f
    private var fadeIn = 1f

    private fun applyVolume() {
        player.volume = (normGain * sleepFactor * fadeIn).coerceIn(0f, 1f)
    }

    private var gainJob: Job? = null

    private fun refreshGain() {
        gainJob?.cancel()
        val s = repo.prefs.current
        val t = current()
        if (!s.normalize || t == null) {
            AudioFx.measureKey = null
            normGain = 1f; applyVolume()
            return
        }
        gainJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val album = runCatching { repo.dao.albumTracksOnce(t.albumKey) }.getOrDefault(emptyList())
            val (db, measure) = levels.gainFor(t, album, s)
            if (current()?.uri != t.uri) return@launch
            AudioFx.measureKey = if (measure) t.uri else null
            normGain = db?.let { Normalization.linear(it) } ?: 1f
            applyVolume()
        }
    }

    private fun collectMeasure() {
        val m = AudioFx.measured ?: return
        AudioFx.measured = null
        levels.saveMeasured(m.first, m.second)
    }

    private var fader: AVPlayer? = null
    private var faderFor: String? = null
    private var fadeJob: Job? = null

    /** Prepara y lanza el crossfade cuando toca. Devuelve true si está a punto (para vigilar más a menudo). */
    private fun checkCrossfade(): Boolean {
        if (fadeJob?.isActive == true) return false
        val s = repo.prefs.current
        val cur = current()
        val hasNext = pos + 1 < order.size || (repeat == Repeat.ALL && order.size > 1)
        if (!s.crossfade || cur == null || !playing || repeat == Repeat.ONE || sleepTimer.state.value.endOfTrack || !hasNext) {
            dropFader(); return false
        }
        val dur = durationMs()
        val fadeMs = s.crossfadeSec * 1000L
        if (dur <= 0 || dur < fadeMs * 2 + 10_000) return false
        val next = queue.getOrNull(order[(pos + 1) % order.size]) ?: return false
        if (!s.crossfadeAlbums && next.albumKey == cur.albumKey) { dropFader(); return false }
        val start = dur - fadeMs
        val p = positionMs()
        if (p < start - 5_000) {
            if (faderFor != cur.uri) dropFader()
            return false
        }
        if (fader == null || faderFor != cur.uri) {
            dropFader()
            val f = AVPlayer()
            val url = NSURL.URLWithString(cur.uri) ?: NSURL.fileURLWithPath(cur.uri)
            f.replaceCurrentItemWithPlayerItem(audioItem(url, main = false))
            f.volume = 0f
            f.seekToTime(CMTimeMakeWithSeconds(start / 1000.0, 1000))
            fader = f
            faderFor = cur.uri
        }
        if (p >= start - 15) startFade(fadeMs)
        return true
    }

    private fun startFade(fadeMs: Long) {
        val f = fader ?: return
        if (f.currentItem?.status != AVPlayerItemStatusReadyToPlay) return
        val outGain = normGain * sleepFactor
        f.volume = outGain
        f.play()
        fadeIn = 0f
        if (pos + 1 < order.size) pos++ else pos = 0
        load(autoPlay = true)
        fadeJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val t0 = dev.surco.util.nowMillis()
            try {
                while (true) {
                    val k = ((dev.surco.util.nowMillis() - t0).toFloat() / fadeMs).coerceIn(0f, 1f)
                    // Potencia constante: el conjunto suena igual de fuerte durante el fundido.
                    fadeIn = kotlin.math.sin(k * kotlin.math.PI / 2).toFloat(); applyVolume()
                    f.volume = (kotlin.math.cos(k * kotlin.math.PI / 2).toFloat() * outGain * sleepFactor).coerceIn(0f, 1f)
                    if (k >= 1f) break
                    if (!playing) f.pause() else if (f.rate == 0f) f.play()
                    delay(30)
                }
            } finally {
                f.pause()
                f.replaceCurrentItemWithPlayerItem(null)
                if (fader === f) { fader = null; faderFor = null }
                fadeIn = 1f; applyVolume()
            }
        }
    }

    private fun dropFader() {
        if (fadeJob?.isActive == true) return
        fader?.let { it.pause(); it.replaceCurrentItemWithPlayerItem(null) }
        fader = null
        faderFor = null
    }

    // ---------------------------------------------------------------- Órdenes de la interfaz

    override fun play(tracks: List<Track>, startIndex: Int, source: String, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        queue = tracks
        this.source = source
        this.shuffle = shuffle
        val start = if (shuffle && startIndex == 0) tracks.indices.random() else startIndex.coerceIn(0, tracks.size - 1)
        order = buildOrder(start)
        pos = if (shuffle) 0 else start
        load(autoPlay = true)
    }

    override fun togglePlay() {
        if (current() == null) return
        if (playing) pause() else resume()
    }

    override fun next() {
        if (order.isEmpty()) return
        if (pos + 1 < order.size) { pos++; load(true) }
        else if (repeat == Repeat.ALL) { pos = 0; load(true) }
    }

    override fun previous() {
        if (order.isEmpty()) return
        if (positionMs() > 3000 || pos == 0) seekTo(0) else { pos--; load(true) }
    }

    override fun previousTrack() {
        if (order.isEmpty()) return
        if (pos > 0) { pos--; load(true) }
        else if (repeat == Repeat.ALL) { pos = order.size - 1; load(true) }
    }

    override fun seekTo(ms: Long) {
        _state.update { it.copy(position = ms) }
        player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 1000))
        updateNowPlaying()
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        val cur = order.getOrNull(pos) ?: return publish()
        order = buildOrder(cur)
        pos = if (shuffle) 0 else cur
        publish()
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            Repeat.OFF -> Repeat.ALL
            Repeat.ALL -> Repeat.ONE
            else -> Repeat.OFF
        }
        publish()
    }

    override fun playNext(track: Track) {
        if (queue.isEmpty()) return play(listOf(track), 0, "cola|Cola")
        val cur = order.getOrNull(pos) ?: return
        if (shuffle) {
            queue = queue + track
            order = order.toMutableList().apply { add(pos + 1, queue.size - 1) }
        } else {
            queue = queue.toMutableList().apply { add(cur + 1, track) }
            order = queue.indices.toList()
            pos = cur
        }
        publish()
    }

    override fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty()) return play(tracks, 0, "cola|Cola")
        val cur = order.getOrNull(pos) ?: return
        if (shuffle) {
            val first = queue.size
            queue = queue + tracks
            order = order.toMutableList().apply { addAll(pos + 1, tracks.indices.map { first + it }) }
        } else {
            queue = queue.toMutableList().apply { addAll(cur + 1, tracks) }
            order = queue.indices.toList()
            pos = cur
        }
        publish()
    }

    override fun removeFromQueue(uris: Collection<String>) {
        val gone = uris.toHashSet()
        // De atrás adelante: los índices de delante no cambian al quitar.
        for (i in queue.indices.reversed()) {
            if (queue.getOrNull(i)?.uri in gone) removeFromQueue(i)
        }
    }

    override fun enqueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty()) return play(tracks, 0, "cola|Cola")
        val first = queue.size
        queue = queue + tracks
        order = order + tracks.indices.map { first + it }
        publish()
    }

    override fun seekToQueueIndex(index: Int) {
        val p = order.indexOf(index)
        if (p < 0) return
        pos = p
        load(playing)
    }

    override fun skipToQueueIndex(index: Int) {
        val p = order.indexOf(index)
        if (p < 0) return
        pos = p
        load(true)
    }

    override fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val curIndex = order.getOrNull(pos)
        if (index == curIndex) {
            // Quitar la que suena: pasa a la siguiente.
            val wasPlaying = playing
            queue = queue.filterIndexed { i, _ -> i != index }
            order = order.filter { it != index }.map { if (it > index) it - 1 else it }
            if (order.isEmpty()) return stop()
            pos = pos.coerceAtMost(order.size - 1)
            load(wasPlaying)
            return
        }
        queue = queue.filterIndexed { i, _ -> i != index }
        val removedPos = order.indexOf(index)
        order = order.filter { it != index }.map { if (it > index) it - 1 else it }
        if (removedPos in 0 until pos) pos--
        publish()
    }

    override fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val list = queue.toMutableList()
        val t = list.removeAt(from)
        list.add(to, t)
        val cur = queue.getOrNull(order.getOrNull(pos) ?: -1)
        queue = list
        // Sin aleatorio el orden es el de la cola; con aleatorio mantenemos el orden y sólo reasignamos índices.
        order = if (!shuffle) queue.indices.toList() else order.map { i ->
            when {
                i == from -> to
                from < to && i in (from + 1)..to -> i - 1
                from > to && i in to until from -> i + 1
                else -> i
            }
        }
        pos = cur?.let { c -> order.indexOfFirst { queue[it] === c } }?.takeIf { it >= 0 } ?: pos
        publish()
    }

    override fun debugSetState(s: PlayerUiState) { _state.value = s }

    // ---------------------------------------------------------------- Interno

    private fun current(): Track? = order.getOrNull(pos)?.let { queue.getOrNull(it) }

    /** Con aleatorio: la actual primero y el resto barajado. Sin aleatorio: la cola tal cual. */
    private fun buildOrder(start: Int): List<Int> =
        if (shuffle) listOf(start) + queue.indices.filter { it != start }.shuffled()
        else queue.indices.toList()

    private fun ensureSession() {
        if (sessionReady) return
        val s = AVAudioSession.sharedInstance()
        s.setCategory(AVAudioSessionCategoryPlayback, null)
        s.setActive(true, null)
        sessionReady = true
    }

    private fun load(autoPlay: Boolean) {
        tracker.finish()
        val t = current() ?: return stop()
        val url = NSURL.URLWithString(t.uri) ?: NSURL.fileURLWithPath(t.uri)
        player.replaceCurrentItemWithPlayerItem(audioItem(url, main = true))
        if (autoPlay) {
            ensureSession()
            player.play()
            playing = true
        } else {
            playing = false
        }
        tracker.onTrackStarted(t, playing)
        refreshGain()
        applyVolume()
        publish()
        updateNowPlaying()
    }

    private fun resume() {
        ensureSession()
        player.play()
        playing = true
        tracker.onPlayingChanged(true)
        publish()
        updateNowPlaying()
    }

    private fun pause() {
        player.pause()
        playing = false
        tracker.onPlayingChanged(false)
        publish()
        updateNowPlaying()
    }

    private fun stop() {
        tracker.finish()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        playing = false
        queue = emptyList(); order = emptyList(); pos = -1
        _state.value = PlayerUiState(connected = true, shuffle = shuffle, repeat = repeat)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun onEnded() {
        // Temporizador «al acabar la canción»: se deja preparada la siguiente, en pausa.
        if (sleepTimer.state.value.endOfTrack) {
            sleepTimer.finished()
            when {
                pos + 1 < order.size -> { pos++; load(false) }
                repeat == Repeat.ALL && order.isNotEmpty() -> { pos = 0; load(false) }
                else -> { tracker.finish(); playing = false; player.seekToTime(CMTimeMakeWithSeconds(0.0, 1000)); player.pause(); publish(); updateNowPlaying() }
            }
            return
        }
        when {
            repeat == Repeat.ONE -> { tracker.finish(); seekTo(0); current()?.let { tracker.onTrackStarted(it, true) }; player.play() }
            pos + 1 < order.size -> { pos++; load(true) }
            repeat == Repeat.ALL && order.isNotEmpty() -> { pos = 0; load(true) }
            else -> { tracker.finish(); playing = false; player.seekToTime(CMTimeMakeWithSeconds(0.0, 1000)); player.pause(); publish(); updateNowPlaying() }
        }
    }

    private fun onInterruption(n: NSNotification?) {
        val info = n?.userInfo ?: return
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        if (type == AVAudioSessionInterruptionTypeBegan) {
            if (playing) { playing = false; tracker.onPlayingChanged(false); publish(); updateNowPlaying() }
        } else {
            val opts = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0u
            if (opts and AVAudioSessionInterruptionOptionShouldResume != 0uL) resume()
        }
    }

    private fun positionMs(): Long {
        val sec = CMTimeGetSeconds(player.currentTime())
        return if (sec.isNaN() || sec < 0) 0 else (sec * 1000).toLong()
    }

    private fun durationMs(): Long {
        val t = current() ?: return 0
        if (t.durationMs > 0) return t.durationMs
        val item = player.currentItem ?: return 0
        val sec = CMTimeGetSeconds(item.duration)
        val ms = if (sec.isNaN() || sec <= 0) 0 else (sec * 1000).toLong()
        if (ms > 0) tracker.onDuration(ms)
        return ms
    }

    private fun tick() {
        if (current() == null) return
        val reallyPlaying = player.rate > 0f
        if (playing != reallyPlaying && playing && player.currentItem == null) playing = false
        _state.update { it.copy(position = positionMs(), duration = durationMs(), isPlaying = playing) }
    }

    private fun publish() {
        val t = current()
        _state.value = PlayerUiState(
            connected = true,
            mediaId = t?.uri,
            isPlaying = playing,
            position = positionMs(),
            duration = durationMs(),
            shuffle = shuffle,
            repeat = repeat,
            source = source,
            queue = queue.map { it.uri },
            queueIndex = order.getOrNull(pos) ?: -1,
            upNext = if (pos < 0) emptyList() else order.drop(pos + 1).take(500),
            prevIndex = order.getOrNull(pos - 1) ?: (if (repeat == Repeat.ALL && pos >= 0) order.lastOrNull() else null) ?: -1,
            nextIndex = order.getOrNull(pos + 1) ?: (if (repeat == Repeat.ALL && pos >= 0) order.firstOrNull() else null) ?: -1,
            order = order,
        )
    }

    // ---------------------------------------------------------------- Pantalla de bloqueo

    private var artworkFor: String? = null
    private var artwork: MPMediaItemArtwork? = null

    private fun updateNowPlaying() {
        val t = current() ?: return
        if (artworkFor != t.artPath) {
            artworkFor = t.artPath
            artwork = t.artPath?.let { UIImage.imageWithContentsOfFile(it) }?.let { img ->
                MPMediaItemArtwork(boundsSize = img.size, requestHandler = { _ -> img })
            }
        }
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to t.title,
            MPMediaItemPropertyArtist to t.artist,
            MPMediaItemPropertyAlbumTitle to t.album,
            MPMediaItemPropertyPlaybackDuration to durationMs() / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to positionMs() / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (playing) 1.0 else 0.0,
        )
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun setupRemoteCommands() {
        val c = MPRemoteCommandCenter.sharedCommandCenter()
        fun ok(block: () -> Unit): (MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus = { _ ->
            if (current() == null) MPRemoteCommandHandlerStatusCommandFailed else { block(); MPRemoteCommandHandlerStatusSuccess }
        }
        c.playCommand.addTargetWithHandler(ok { resume() })
        c.pauseCommand.addTargetWithHandler(ok { pause() })
        c.togglePlayPauseCommand.addTargetWithHandler(ok { togglePlay() })
        c.nextTrackCommand.addTargetWithHandler(ok { next() })
        c.previousTrackCommand.addTargetWithHandler(ok { previous() })
        c.changePlaybackPositionCommand.addTargetWithHandler { e ->
            val ev = e as? MPChangePlaybackPositionCommandEvent
            if (ev == null || current() == null) MPRemoteCommandHandlerStatusCommandFailed
            else { seekTo((ev.positionTime * 1000).toLong()); MPRemoteCommandHandlerStatusSuccess }
        }
    }
}
