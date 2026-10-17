package dev.surco.ios

import dev.surco.playback.PlayerController
import dev.surco.playback.PreviewPlayer
import dev.surco.playback.PreviewState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/** Escuchas previas de 30 s con su propio AVPlayer: pausa la música principal y la reanuda al acabar. */
@OptIn(ExperimentalForeignApi::class)
class IosPreviewPlayer(private val main: PlayerController) : PreviewPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AVPlayer()
    private var ticker: Job? = null
    private var resumeMain = false

    private val _state = MutableStateFlow(PreviewState())
    override val state: StateFlow<PreviewState> = _state.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n ->
            if (n?.`object` == player.currentItem && _state.value.url != null) stop()
        }
        center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n ->
            if (n?.`object` == player.currentItem && _state.value.url != null) {
                val url = _state.value.url
                stop()
                _state.value = PreviewState(failed = url)
            }
        }
        // Si el usuario pone música mientras suena una vista previa, la vista previa se calla.
        scope.launch {
            var was = main.state.value.isPlaying
            main.state.collect {
                if (it.isPlaying && !was && _state.value.url != null) { resumeMain = false; stop() }
                was = it.isPlaying
            }
        }
    }

    override fun toggle(url: String) {
        if (_state.value.url == url) { stop(); return }
        val nsUrl = NSURL.URLWithString(url) ?: return
        if (main.state.value.isPlaying) { main.togglePlay(); resumeMain = true }
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = nsUrl))
        player.play()
        _state.value = PreviewState(url = url, playing = true)
        ticker?.cancel()
        ticker = scope.launch {
            var stalled = 0
            while (isActive) {
                val pos = CMTimeGetSeconds(player.currentTime()).takeIf { !it.isNaN() } ?: 0.0
                val dur = player.currentItem?.let { CMTimeGetSeconds(it.duration) }?.takeIf { !it.isNaN() && it > 0 } ?: 30.0
                _state.value = _state.value.copy(progress = (pos / dur).toFloat().coerceIn(0f, 1f))
                // Si en 15 s no ha empezado a sonar, se da por fallida.
                if (pos <= 0.0) stalled++ else stalled = 0
                if (stalled > 150) { val u = _state.value.url; stop(); _state.value = PreviewState(failed = u); break }
                delay(100)
            }
        }
    }

    override fun stop() {
        ticker?.cancel()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        if (_state.value.url != null) _state.value = PreviewState()
        if (resumeMain) {
            resumeMain = false
            if (!main.state.value.isPlaying) main.togglePlay()
        }
    }
}
