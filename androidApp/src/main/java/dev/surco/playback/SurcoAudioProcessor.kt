package dev.surco.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.surco.audio.FxChain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Procesador de audio de ExoPlayer que aplica la cadena de Surco ([FxChain]): ecualizador, limitador,
 * medición de sonoridad y espectrograma. Con todo apagado copia los bytes tal cual (sale bit a bit).
 *
 * @param main el reproductor principal (el secundario del crossfade no mide ni alimenta el espectrograma).
 */
@OptIn(UnstableApi::class)
class SurcoAudioProcessor(main: Boolean = true) : BaseAudioProcessor() {
    private val chain = FxChain(feedTap = main, measure = main)
    private var floats = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Sólo PCM de 16 bits o decimal; cualquier otro formato pasa sin tocar (el procesador se desactiva).
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val fmt = inputAudioFormat
        if (!chain.needed()) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val ch = fmt.channelCount
        val isFloat = fmt.encoding == C.ENCODING_PCM_FLOAT
        val bytesPer = if (isFloat) 4 else 2
        val samples = size / bytesPer
        val frames = samples / ch
        if (floats.size < samples) floats = FloatArray(samples)
        val start = inputBuffer.position()
        val src = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        if (isFloat) {
            for (i in 0 until samples) floats[i] = src.getFloat(start + i * 4)
        } else {
            for (i in 0 until samples) floats[i] = src.getShort(start + i * 2) / 32768f
        }
        val changed = chain.process(floats, frames, ch, fmt.sampleRate)
        if (!changed) {
            out.put(inputBuffer)
        } else {
            out.order(ByteOrder.nativeOrder())
            if (isFloat) {
                for (i in 0 until samples) out.putFloat(floats[i])
            } else {
                for (i in 0 until samples) out.putShort((floats[i] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            // Bytes sueltos que no forman una muestra completa (no debería haberlos), tal cual.
            for (i in samples * bytesPer until size) out.put(src.get(start + i))
            inputBuffer.position(inputBuffer.limit())
        }
        out.flip()
    }

    override fun onFlush() { chain.reset() }

    override fun onReset() { chain.reset() }

    /** Medida de la canción que suena (al cerrar el servicio, para no perderla). */
    fun currentMeasure() = chain.currentMeasure()
}
