package dev.surco.ios

import dev.surco.audio.FxChain
import dev.surco.audio.Normalization
import dev.surco.audio.ReplayGain
import dev.surco.audio.ReplayGainInfo
import dev.surco.data.Settings
import dev.surco.data.Track
import dev.surco.util.IoDispatcher
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.setAudioMix
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.MediaToolbox.MTAudioProcessingTapCallbacks
import platform.MediaToolbox.MTAudioProcessingTapCreate
import platform.MediaToolbox.MTAudioProcessingTapGetSourceAudio
import platform.MediaToolbox.MTAudioProcessingTapGetStorage
import platform.MediaToolbox.MTAudioProcessingTapRef
import platform.MediaToolbox.MTAudioProcessingTapRefVar
import platform.MediaToolbox.kMTAudioProcessingTapCallbacksVersion_0
import platform.MediaToolbox.kMTAudioProcessingTapCreationFlag_PostEffects
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

// =====================================================================================
// Audio en iPhone: la misma cadena que en Android (ecualizador, limitador, medición de
// sonoridad y espectrograma, en Kotlin común) enganchada a AVPlayer con un
// MTAudioProcessingTap, que entrega el audio decodificado de cada canción en tiempo real.
// =====================================================================================

/** Estado de un «grifo» de audio (uno por canción en reproducción). */
@OptIn(ExperimentalForeignApi::class)
internal class TapState(val chain: FxChain) {
    var rate = 44_100
    var channels = 2
    var usable = false
    var nonInterleaved = true
    var buf = FloatArray(0)

    fun process(abl: CPointer<AudioBufferList>, frames: Int) {
        val ch = channels
        if (frames <= 0 || ch <= 0) return
        val list = abl.pointed
        val n = list.mNumberBuffers.toInt()
        if (buf.size < frames * ch) buf = FloatArray(frames * ch)
        if (nonInterleaved) {
            for (c in 0 until min(n, ch)) {
                val data = list.mBuffers[c].mData?.reinterpret<FloatVar>() ?: return
                var j = c
                for (i in 0 until frames) { buf[j] = data[i]; j += ch }
            }
        } else {
            val data = list.mBuffers[0].mData?.reinterpret<FloatVar>() ?: return
            for (i in 0 until frames * ch) buf[i] = data[i]
        }
        if (!chain.process(buf, frames, ch, rate)) return
        if (nonInterleaved) {
            for (c in 0 until min(n, ch)) {
                val data = list.mBuffers[c].mData?.reinterpret<FloatVar>() ?: return
                var j = c
                for (i in 0 until frames) { data[i] = buf[j]; j += ch }
            }
        } else {
            val data = list.mBuffers[0].mData?.reinterpret<FloatVar>() ?: return
            for (i in 0 until frames * ch) data[i] = buf[i]
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun stateOf(tap: MTAudioProcessingTapRef?): TapState? =
    MTAudioProcessingTapGetStorage(tap)?.asStableRef<TapState>()?.get()

@OptIn(ExperimentalForeignApi::class)
private val tapInit = staticCFunction { _: MTAudioProcessingTapRef?, clientInfo: COpaquePointer?, storageOut: CPointer<COpaquePointerVar>? ->
    if (storageOut != null) storageOut.pointed.value = clientInfo
}

@OptIn(ExperimentalForeignApi::class)
private val tapFinalize = staticCFunction { tap: MTAudioProcessingTapRef? ->
    MTAudioProcessingTapGetStorage(tap)?.asStableRef<TapState>()?.dispose()
}

@OptIn(ExperimentalForeignApi::class)
private val tapPrepare = staticCFunction { tap: MTAudioProcessingTapRef?, maxFrames: Long, format: CPointer<AudioStreamBasicDescription>? ->
    val st = stateOf(tap)
    val f = format?.pointed
    if (st != null && f != null) {
        st.rate = f.mSampleRate.toInt()
        st.channels = f.mChannelsPerFrame.toInt()
        // Sólo audio decimal de 32 bits (lo normal en AVPlayer); si no, pasa sin tocar.
        st.usable = (f.mFormatFlags and 1u) != 0u && f.mBitsPerChannel == 32u
        st.nonInterleaved = (f.mFormatFlags and 32u) != 0u
        st.buf = FloatArray((maxFrames.toInt().coerceAtLeast(1024)) * st.channels.coerceAtLeast(1))
        st.chain.reset()
        println("Surco audio: grifo listo (${st.rate} Hz, ${st.channels} canales, decimal=${st.usable}, separados=${st.nonInterleaved})")
    }
}

@OptIn(ExperimentalForeignApi::class)
private val tapUnprepare = staticCFunction { _: MTAudioProcessingTapRef? -> }

@OptIn(ExperimentalForeignApi::class)
private val tapProcess = staticCFunction { tap: MTAudioProcessingTapRef?, numberFrames: Long, _: UInt, abl: CPointer<AudioBufferList>?, framesOut: CPointer<LongVar>?, flagsOut: CPointer<UIntVar>? ->
    val status = MTAudioProcessingTapGetSourceAudio(tap, numberFrames, abl, flagsOut, null, framesOut)
    if (status == 0 && abl != null) {
        val st = stateOf(tap)
        if (st != null && st.usable && st.chain.needed()) {
            st.process(abl, (framesOut?.pointed?.value ?: numberFrames).toInt())
        }
    }
}

/**
 * Una canción lista para AVPlayer con la cadena de audio de Surco enganchada.
 * [main]: el reproductor principal (el del crossfade no mide ni dibuja el espectrograma).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun audioItem(url: NSURL, main: Boolean): AVPlayerItem {
    val asset = AVURLAsset(uRL = url, options = null)
    val item = AVPlayerItem(asset = asset)
    runCatching { attachTap(item, asset, main) }
    return item
}

@OptIn(ExperimentalForeignApi::class)
private fun attachTap(item: AVPlayerItem, asset: AVURLAsset, main: Boolean) {
    val track = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return
    val ref = StableRef.create(TapState(FxChain(feedTap = main, measure = main)))
    memScoped {
        val cb = alloc<MTAudioProcessingTapCallbacks>()
        cb.version = kMTAudioProcessingTapCallbacksVersion_0
        cb.clientInfo = ref.asCPointer()
        cb.init = tapInit
        cb.finalize = tapFinalize
        cb.prepare = tapPrepare
        cb.unprepare = tapUnprepare
        cb.process = tapProcess
        val out = alloc<MTAudioProcessingTapRefVar>()
        val err = MTAudioProcessingTapCreate(kCFAllocatorDefault, cb.ptr, kMTAudioProcessingTapCreationFlag_PostEffects, out.ptr)
        val tap = out.value
        if (err != 0 || tap == null) {
            ref.dispose()
            return
        }
        val params = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track)
        params.setAudioTapProcessor(tap)
        CFRelease(tap) // los parámetros ya lo retienen
        val mix = AVMutableAudioMix()
        mix.setInputParameters(listOf(params))
        item.setAudioMix(mix)
    }
}

/**
 * Sonoridad de cada canción para normalizar (ReplayGain del archivo o lo que se mide al escucharla),
 * igual que en Android. Las medidas se guardan en los ajustes del iPhone.
 */
class IosAudioLevels {
    private val defaults = NSUserDefaults(suiteName = "dev.surco.loudness")
    private val rgCache = HashMap<String, ReplayGainInfo>()

    fun measured(uri: String): Float? = (defaults.objectForKey(uri) as? NSNumber)?.floatValue

    fun saveMeasured(uri: String, lufs: Float) {
        if (lufs.isFinite() && lufs > -70f && lufs < 6f) defaults.setFloat(lufs, forKey = uri)
    }

    suspend fun replayGain(uri: String): ReplayGainInfo {
        rgCache[uri]?.let { return it }
        val info = withContext(IoDispatcher) {
            runCatching {
                val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
                url.withSource { src ->
                    var rg = ReplayGain.parse(src.read(0, min(src.size, HEAD.toLong()).toInt()))
                    if (rg.track == null && rg.album == null && src.size > HEAD) rg = ReplayGain.parse(src.read(src.size - TAIL, TAIL))
                    rg
                }
            }.getOrNull() ?: ReplayGainInfo(null, null)
        }
        rgCache[uri] = info
        return info
    }

    private fun knownLufs(t: Track): Float? = rgCache[t.uri]?.track?.let(ReplayGain::toLufs) ?: measured(t.uri)

    suspend fun gainFor(track: Track, album: List<Track>, s: Settings): Pair<Float?, Boolean> {
        val rg = replayGain(track.uri)
        val trackLufs = rg.track?.let(ReplayGain::toLufs) ?: measured(track.uri)
        val albumLufs = rg.album?.let(ReplayGain::toLufs) ?: average(album.mapNotNull { knownLufs(it) })
        val needsMeasure = rg.track == null && measured(track.uri) == null
        return Normalization.gainDb(s.normalizeTarget, trackLufs, albumLufs, s.normalizeAlbum) to needsMeasure
    }

    private fun average(list: List<Float>): Float? {
        if (list.isEmpty()) return null
        val e = list.sumOf { 10.0.pow(it / 10.0) } / list.size
        return (10 * log10(e)).toFloat()
    }

    private companion object {
        const val HEAD = 512 * 1024
        const val TAIL = 128 * 1024
    }
}
