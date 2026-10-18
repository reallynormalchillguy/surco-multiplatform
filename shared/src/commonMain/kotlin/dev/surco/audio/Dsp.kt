package dev.surco.audio

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// =====================================================================================
// Procesado de audio de Surco, en Kotlin puro (igual en Android y en iPhone):
// ecualizador paramétrico (filtros biquad «peak», «low shelf» y «high shelf»), limitador,
// medidor de sonoridad (LUFS, ITU-R BS.1770) para normalizar el volumen, lectura de
// ReplayGain y análisis de espectro (FFT) para el espectrograma.
// Con todo apagado el audio pasa intacto (bit a bit).
// =====================================================================================

enum class FilterType(val code: String) { PEAK("pk"), LOW_SHELF("ls"), HIGH_SHELF("hs") }

/** Una banda del ecualizador: tipo de filtro, frecuencia (Hz), ganancia (dB) y factor Q. */
data class EqBand(
    val type: FilterType = FilterType.PEAK,
    val freq: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 1f,
) {
    fun clamped() = copy(
        freq = freq.coerceIn(EqSettings.MIN_FREQ, EqSettings.MAX_FREQ),
        gain = gain.coerceIn(-EqSettings.MAX_GAIN, EqSettings.MAX_GAIN),
        q = q.coerceIn(EqSettings.MIN_Q, EqSettings.MAX_Q),
    )
}

/** Ajustes del ecualizador: las bandas y el preamplificador (dB). */
data class EqSettings(val bands: List<EqBand> = DEFAULT_BANDS, val preamp: Float = 0f) {

    fun encode(): String = buildString {
        append("v1;p=").append(fmt(preamp))
        bands.forEach { b -> append(';').append(b.type.code).append(',').append(fmt(b.freq)).append(',').append(fmt(b.gain)).append(',').append(fmt(b.q)) }
    }

    /** Respuesta de la curva entera (dB) a una frecuencia, con el preamplificador. */
    fun responseDb(freq: Float, sampleRate: Float = 48_000f): Float =
        preamp + bands.sumOf { Biquad.design(it, sampleRate).magnitudeDb(freq, sampleRate).toDouble() }.toFloat()

    /** Lo más que sube la curva (sin el preamplificador): bajando el preamplificador esto, nunca satura. */
    fun maxBoost(sampleRate: Float = 48_000f): Float {
        var m = 0f
        var f = 20f
        while (f <= 20_000f) {
            m = max(m, responseDb(f, sampleRate) - preamp)
            f *= 1.05f
        }
        return m
    }

    /** ¿Cambia algo el sonido? (todo a 0 dB = no hace falta procesar). */
    val isFlat get() = abs(preamp) < 0.01f && bands.all { abs(it.gain) < 0.01f }

    companion object {
        const val MAX_BANDS = 10
        const val MIN_FREQ = 20f
        const val MAX_FREQ = 20_000f
        const val MAX_GAIN = 15f
        const val MIN_Q = 0.1f
        const val MAX_Q = 10f

        val DEFAULT_BANDS = listOf(
            EqBand(FilterType.LOW_SHELF, 80f, 0f, 0.71f),
            EqBand(FilterType.PEAK, 250f, 0f, 1f),
            EqBand(FilterType.PEAK, 1_000f, 0f, 1f),
            EqBand(FilterType.PEAK, 4_000f, 0f, 1f),
            EqBand(FilterType.HIGH_SHELF, 10_000f, 0f, 0.71f),
        )

        fun decode(s: String): EqSettings {
            if (!s.startsWith("v1;")) return EqSettings()
            var preamp = 0f
            val bands = ArrayList<EqBand>()
            s.split(';').drop(1).forEach { part ->
                if (part.startsWith("p=")) preamp = part.removePrefix("p=").toFloatOrNull() ?: 0f
                else {
                    val f = part.split(',')
                    val type = FilterType.entries.firstOrNull { it.code == f.getOrNull(0) } ?: return@forEach
                    val freq = f.getOrNull(1)?.toFloatOrNull() ?: return@forEach
                    val gain = f.getOrNull(2)?.toFloatOrNull() ?: return@forEach
                    val q = f.getOrNull(3)?.toFloatOrNull() ?: return@forEach
                    bands += EqBand(type, freq, gain, q).clamped()
                }
            }
            return EqSettings(bands.take(MAX_BANDS), preamp.coerceIn(-MAX_GAIN, MAX_GAIN))
        }

        private fun fmt(v: Float): String {
            val r = (v * 100).roundToInt() / 100.0
            return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
        }
    }
}

/** Coeficientes de un filtro biquad (normalizados, a0 = 1), según el «Audio EQ Cookbook» de R. Bristow-Johnson. */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Ganancia (dB) del filtro a una frecuencia. */
    fun magnitudeDb(freq: Float, sampleRate: Float): Float {
        val w = 2 * PI * freq / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val mag2 = (nr * nr + ni * ni) / (dr * dr + di * di)
        return (10 * log10(max(mag2, 1e-12))).toFloat()
    }

    companion object {
        fun design(band: EqBand, sampleRate: Float): Biquad {
            val f0 = min(band.freq.toDouble(), sampleRate * 0.45)
            val a = 10.0.pow(band.gain / 40.0)
            val w0 = 2 * PI * f0 / sampleRate
            val cw = cos(w0)
            val alpha = sin(w0) / (2 * band.q.toDouble().coerceAtLeast(0.01))
            val sa = 2 * sqrt(a) * alpha
            val (b0, b1, b2, a0, a1, a2) = when (band.type) {
                FilterType.PEAK -> Six(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
                FilterType.LOW_SHELF -> Six(
                    a * ((a + 1) - (a - 1) * cw + sa),
                    2 * a * ((a - 1) - (a + 1) * cw),
                    a * ((a + 1) - (a - 1) * cw - sa),
                    (a + 1) + (a - 1) * cw + sa,
                    -2 * ((a - 1) + (a + 1) * cw),
                    (a + 1) + (a - 1) * cw - sa,
                )
                FilterType.HIGH_SHELF -> Six(
                    a * ((a + 1) + (a - 1) * cw + sa),
                    -2 * a * ((a - 1) + (a + 1) * cw),
                    a * ((a + 1) + (a - 1) * cw - sa),
                    (a + 1) - (a - 1) * cw + sa,
                    2 * ((a - 1) - (a + 1) * cw),
                    (a + 1) - (a - 1) * cw - sa,
                )
            }
            return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        /** Paso alto (para la ponderación K del medidor de sonoridad). */
        fun highPass(f0: Double, q: Double, sampleRate: Double): Biquad {
            val w0 = 2 * PI * f0 / sampleRate
            val cw = cos(w0)
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 + cw) / 2 / a0, -(1 + cw) / a0, (1 + cw) / 2 / a0, -2 * cw / a0, (1 - alpha) / a0)
        }
    }

    private data class Six(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double)
}

/** Cadena de biquads en serie, para audio entrelazado de varios canales (forma directa II transpuesta). */
class BiquadChain {
    private var filters: Array<Biquad> = emptyArray()
    private var state = DoubleArray(0)
    private var channels = 0

    fun setFilters(list: List<Biquad>, channelCount: Int) {
        val reset = list.size != filters.size || channelCount != channels
        filters = list.toTypedArray()
        channels = channelCount
        if (reset) state = DoubleArray(filters.size * channels * 2)
    }

    fun reset() { state.fill(0.0) }

    /** Procesa en el sitio [frames] tramas de [buf] (entrelazado). */
    fun process(buf: FloatArray, frames: Int) {
        val ch = channels
        if (filters.isEmpty() || ch == 0) return
        val st = state
        for (f in filters.indices) {
            val q = filters[f]
            val b0 = q.b0; val b1 = q.b1; val b2 = q.b2; val a1 = q.a1; val a2 = q.a2
            for (c in 0 until ch) {
                val si = (f * ch + c) * 2
                var z1 = st[si]; var z2 = st[si + 1]
                var i = c
                for (n in 0 until frames) {
                    val x = buf[i].toDouble()
                    val y = b0 * x + z1
                    z1 = b1 * x - a1 * y + z2
                    z2 = b2 * x - a2 * y
                    buf[i] = y.toFloat()
                    i += ch
                }
                // Evita números subnormales (lentísimos) en los silencios.
                st[si] = if (abs(z1) < 1e-25) 0.0 else z1
                st[si + 1] = if (abs(z2) < 1e-25) 0.0 else z2
            }
        }
    }
}

/** Limitador de picos: nunca deja pasar de [ceiling] (sin distorsión dura), con recuperación suave. */
class Limiter(private val ceiling: Float = 0.977f) {
    private var gain = 1f
    private var release = 0.9999f

    fun setSampleRate(rate: Int) { release = exp(-1.0 / (0.12 * rate)).toFloat() }
    fun reset() { gain = 1f }

    fun process(buf: FloatArray, frames: Int, channels: Int) {
        var g = gain
        var i = 0
        for (n in 0 until frames) {
            var peak = 0f
            for (c in 0 until channels) peak = max(peak, abs(buf[i + c]))
            // Se recupera poco a poco, pero nunca tanto como para pasar del techo.
            g = 1f - (1f - g) * release
            if (peak * g > ceiling) g = ceiling / peak
            if (g < 0.99999f) for (c in 0 until channels) buf[i + c] *= g
            i += channels
        }
        gain = g
    }
}

/**
 * Medidor de sonoridad integrada (LUFS) según ITU-R BS.1770-4: ponderación K, bloques de 400 ms con
 * solapamiento del 75 %, compuerta absoluta (-70 LUFS) y relativa (-10 LU).
 */
class LoudnessMeter {
    private var rate = 0
    private var channels = 0
    private val k = BiquadChain()
    private var tmp = FloatArray(0)
    private var sub = 0.0
    private var subCount = 0
    private var subSize = 4410
    private val subs = DoubleArray(4)
    private var subIndex = 0
    private var subsFilled = 0
    private var blocks = DoubleArray(1024)
    private var blockCount = 0

    /** Segundos de audio medidos. */
    val seconds: Float get() = if (rate == 0) 0f else (blockCount * 0.1f)

    fun reset() {
        k.reset(); sub = 0.0; subCount = 0; subIndex = 0; subsFilled = 0; blockCount = 0
    }

    private fun setup(sampleRate: Int, ch: Int) {
        if (sampleRate == rate && ch == channels) return
        rate = sampleRate; channels = ch
        val sr = sampleRate.toDouble()
        k.setFilters(kWeighting(sr), ch)
        subSize = (sampleRate / 10).coerceAtLeast(1)
        reset()
    }

    fun feed(buf: FloatArray, frames: Int, ch: Int, sampleRate: Int) {
        setup(sampleRate, ch)
        val n = frames * ch
        if (tmp.size < n) tmp = FloatArray(n)
        buf.copyInto(tmp, 0, 0, n)
        k.process(tmp, frames)
        val use = min(ch, 2) // los canales de surround pesan distinto; con estéreo basta
        var i = 0
        for (f in 0 until frames) {
            var e = 0.0
            for (c in 0 until use) { val v = tmp[i + c].toDouble(); e += v * v }
            sub += e
            i += ch
            if (++subCount == subSize) {
                subs[subIndex] = sub / subSize
                subIndex = (subIndex + 1) % 4
                sub = 0.0; subCount = 0
                if (subsFilled < 4) subsFilled++
                if (subsFilled == 4) {
                    if (blockCount == blocks.size) blocks = blocks.copyOf(blocks.size * 2)
                    blocks[blockCount++] = (subs[0] + subs[1] + subs[2] + subs[3]) / 4
                }
            }
        }
    }

    /** Los dos filtros de la ponderación K (los de BS.1770 recalculados para cualquier frecuencia de muestreo). */
    private fun kWeighting(fs: Double): List<Biquad> {
        var f0 = 1681.974450955533
        val g = 3.999843853973347
        var q = 0.7071752369554196
        var k = kotlin.math.tan(PI * f0 / fs)
        val vh = 10.0.pow(g / 20)
        val vb = vh.pow(0.4996667741545416)
        var a0 = 1 + k / q + k * k
        val shelf = Biquad((vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        f0 = 38.13547087602444
        q = 0.5003270373238773
        k = kotlin.math.tan(PI * f0 / fs)
        a0 = 1 + k / q + k * k
        val hp = Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
        return listOf(shelf, hp)
    }

    /** Sonoridad integrada (LUFS), o null si aún no hay suficiente. */
    fun integrated(): Float? {
        if (blockCount < 10) return null
        val abs = 10.0.pow((-70.0 + 0.691) / 10)
        var sum = 0.0; var count = 0
        for (i in 0 until blockCount) if (blocks[i] > abs) { sum += blocks[i]; count++ }
        if (count == 0) return null
        val rel = (sum / count) * 10.0.pow(-10.0 / 10)
        var s2 = 0.0; var c2 = 0
        for (i in 0 until blockCount) if (blocks[i] > abs && blocks[i] > rel) { s2 += blocks[i]; c2++ }
        if (c2 == 0) return null
        return (-0.691 + 10 * log10(s2 / c2)).toFloat()
    }
}

/** Ganancia de ReplayGain que trae el archivo (dB). */
data class ReplayGainInfo(val track: Float?, val album: Float?)

object ReplayGain {
    /** Nivel de referencia de ReplayGain 2 (LUFS). */
    const val REFERENCE_LUFS = -18f

    private val RG = Regex("""replaygain_(track|album)_gain[^0-9+\-]{0,12}([+\-]?\d{1,2}(?:[.,]\d+)?)\s*db""", RegexOption.IGNORE_CASE)

    /** Busca las etiquetas ReplayGain (ID3 TXXX, comentarios Vorbis de FLAC/Ogg o átomos de MP4) en unos bytes del archivo. */
    fun parse(bytes: ByteArray): ReplayGainInfo {
        if (bytes.isEmpty()) return ReplayGainInfo(null, null)
        // Texto «latin1» sin los ceros (así también se leen las etiquetas guardadas en UTF-16).
        val sb = StringBuilder(bytes.size)
        for (b in bytes) { val c = b.toInt() and 0xFF; if (c != 0) sb.append(c.toChar()) }
        var track: Float? = null
        var album: Float? = null
        RG.findAll(sb).forEach { m ->
            val v = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: return@forEach
            if (m.groupValues[1].equals("track", ignoreCase = true)) { if (track == null) track = v } else if (album == null) album = v
        }
        return ReplayGainInfo(track, album)
    }

    /** Sonoridad equivalente (LUFS) de una ganancia ReplayGain. */
    fun toLufs(gainDb: Float) = REFERENCE_LUFS - gainDb
}

/** Normalización del volumen: qué ganancia aplicar a una canción. */
object Normalization {
    /**
     * Ganancia (dB) para llevar la canción al objetivo. Sólo baja el volumen (subirlo obligaría a
     * comprimir); null si no se sabe su sonoridad.
     */
    fun gainDb(target: Int, trackLufs: Float?, albumLufs: Float?, albumMode: Boolean): Float? {
        val l = (if (albumMode) albumLufs ?: trackLufs else trackLufs ?: albumLufs) ?: return null
        return (target - l).coerceIn(-24f, 0f)
    }

    fun linear(db: Float): Float = 10f.pow(db / 20f)
}

// ======================================================================= Espectro

/** Las últimas muestras que suenan (en mono), para dibujar el espectrograma. */
class SpectrumTap(bits: Int = 15) {
    private val size = 1 shl bits
    private val mask = size - 1
    private val ring = FloatArray(size)

    @Volatile var written: Long = 0L
        private set

    @Volatile var sampleRate: Int = 44_100
        private set

    /** Sólo se copian muestras mientras alguien mira el espectrograma. */
    @Volatile var enabled: Boolean = false

    fun write(buf: FloatArray, frames: Int, channels: Int, rate: Int) {
        if (!enabled || channels <= 0) return
        sampleRate = rate
        var w = written
        var i = 0
        val inv = 1f / channels
        for (f in 0 until frames) {
            var s = 0f
            for (c in 0 until channels) s += buf[i + c]
            ring[(w and mask.toLong()).toInt()] = s * inv
            w++
            i += channels
        }
        written = w
    }

    /** Copia en [out] las muestras que terminan [behind] muestras antes de la última escrita. */
    fun read(out: FloatArray, behind: Int = 0): Boolean {
        val end = written - behind
        val n = out.size
        if (end < n) return false
        var p = end - n
        for (i in 0 until n) { out[i] = ring[(p and mask.toLong()).toInt()]; p++ }
        return true
    }
}

/** FFT de radix 2 (en el sitio), para el espectrograma. */
class Fft(val n: Int) {
    private val bits = (ln(n.toDouble()) / ln(2.0)).roundToInt()
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { -sin(2 * PI * it / n).toFloat() }
    private val rev = IntArray(n) { i -> var r = 0; var x = i; repeat(bits) { r = (r shl 1) or (x and 1); x = x shr 1 }; r }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                    re[j] += xr; im[j] += xi
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * Convierte las últimas muestras en una columna del espectrograma: [rows] filas en escala logarítmica
 * de [minHz] a [maxHz], con la intensidad de 0 a 1.
 */
class SpectrogramAnalyzer(val rows: Int = 128, val n: Int = 2048, private val minHz: Float = 30f, private val maxHz: Float = 16_000f) {
    private val fft = Fft(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val samples = FloatArray(n)
    private val mags = FloatArray(n / 2)

    /** Frecuencia (Hz) del centro de una fila (0 = abajo). */
    fun rowHz(row: Int): Float = minHz * (maxHz / minHz).pow((row + 0.5f) / rows)

    fun column(tap: SpectrumTap, behind: Int, out: FloatArray): Boolean {
        if (!tap.read(samples, behind)) return false
        val rate = tap.sampleRate.toFloat()
        for (i in 0 until n) { re[i] = samples[i] * window[i]; im[i] = 0f }
        fft.transform(re, im)
        val norm = 4f / n
        for (i in 0 until n / 2) mags[i] = sqrt(re[i] * re[i] + im[i] * im[i]) * norm
        val binHz = rate / n
        for (r in 0 until rows) {
            val lo = minHz * (maxHz / minHz).pow(r.toFloat() / rows)
            val hi = minHz * (maxHz / minHz).pow((r + 1f) / rows)
            val a = (lo / binHz).toInt().coerceIn(1, n / 2 - 1)
            val b = (hi / binHz).toInt().coerceIn(a, n / 2 - 1)
            var m = 0f
            if (b > a) { for (k in a..b) m = max(m, mags[k]) }
            else {
                // Graves: menos de un «bin» por fila, se interpola.
                val pos = ((lo + hi) / 2 / binHz).coerceIn(1f, n / 2f - 2)
                val k0 = pos.toInt(); val t = pos - k0
                m = mags[k0] * (1 - t) + mags[k0 + 1] * t
            }
            val db = 20 * log10(max(m, 1e-9f))
            out[r] = ((db + 95f) / 85f).coerceIn(0f, 1f)
        }
        return true
    }
}

// ======================================================================= Estado compartido

/**
 * Lo que comparten la app y el procesado de audio (que corre en el hilo de audio): los ajustes del
 * ecualizador, el «grifo» del espectrograma y la medición de sonoridad de la canción que suena.
 */
object AudioFx {
    /** Ecualizador activo (null = apagado o plano). */
    @Volatile var eq: EqSettings? = null
        private set

    /** Sube cada vez que cambian los ajustes (el hilo de audio recalcula los filtros). */
    @Volatile var version: Int = 0
        private set

    val tap = SpectrumTap()

    /** Cuánto va el procesado por delante de lo que suena (ms): el espectrograma mira ese tanto atrás. */
    @Volatile var tapLatencyMs: Int = 180

    /** Canción que se está midiendo (null = no medir). */
    @Volatile var measureKey: String? = null

    /** Última medición terminada: (canción, LUFS). La recoge la app para guardarla. */
    @Volatile var measured: Pair<String, Float>? = null

    fun setEq(enabled: Boolean, settings: EqSettings) {
        val e = if (enabled && !settings.isFlat) settings else null
        if (e != eq) { eq = e; version++ }
    }
}

/** El procesado completo de un reproductor: medir (sin tocar), ecualizar, limitar y copiar al espectrograma. */
class FxChain(private val feedTap: Boolean = true, private val measure: Boolean = true) {
    private val eq = BiquadChain()
    private val limiter = Limiter()
    private val meter = LoudnessMeter()
    private var version = -1
    private var rate = 0
    private var channels = 0
    private var meterKey: String? = null
    private var skip = 0

    /** ¿Hay que convertir el audio a decimal? Si no, pasa tal cual. */
    fun needed(): Boolean = AudioFx.eq != null || (feedTap && AudioFx.tap.enabled) || (measure && AudioFx.measureKey != null)

    fun reset() { eq.reset(); limiter.reset() }

    /** Procesa [buf] en el sitio. Devuelve true si ha cambiado el sonido (si no, se puede usar el original). */
    fun process(buf: FloatArray, frames: Int, ch: Int, sampleRate: Int): Boolean {
        if (measure) measure(buf, frames, ch, sampleRate)
        val settings = AudioFx.eq
        var changed = false
        if (settings != null) {
            if (version != AudioFx.version || rate != sampleRate || channels != ch) {
                version = AudioFx.version; rate = sampleRate; channels = ch
                eq.setFilters(settings.bands.map { Biquad.design(it, sampleRate.toFloat()) }, ch)
                limiter.setSampleRate(sampleRate)
            }
            eq.process(buf, frames)
            val pre = Normalization.linear(settings.preamp)
            if (abs(pre - 1f) > 1e-4f) for (i in 0 until frames * ch) buf[i] *= pre
            limiter.process(buf, frames, ch)
            changed = true
        }
        if (feedTap) AudioFx.tap.write(buf, frames, ch, sampleRate)
        return changed
    }

    private fun measure(buf: FloatArray, frames: Int, ch: Int, sampleRate: Int) {
        val key = AudioFx.measureKey
        if (key != meterKey) {
            // Cambio de canción: se entrega la medida de la anterior si se ha oído lo bastante.
            val prev = meterKey
            if (prev != null && meter.seconds >= 45f) meter.integrated()?.let { AudioFx.measured = prev to it }
            meter.reset()
            meterKey = key
            skip = sampleRate * 2 // los primeros 2 s pueden ser aún de la canción anterior
        }
        if (key == null) return
        if (skip > 0) { skip -= frames; return }
        meter.feed(buf, frames, ch, sampleRate)
    }

    /** Sonoridad medida hasta ahora de la canción actual (para guardarla al terminar). */
    fun currentMeasure(): Pair<String, Float>? {
        val k = meterKey ?: return null
        if (meter.seconds < 45f) return null
        return meter.integrated()?.let { k to it }
    }
}
