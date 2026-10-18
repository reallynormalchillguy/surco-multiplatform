package dev.surco

import dev.surco.audio.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioDspTest {
    private fun near(expected: Double, actual: Double, tol: Double, what: String) =
        assertTrue(kotlin.math.abs(expected - actual) <= tol, "$what: esperado $expected, salió $actual")

    @Test
    fun peakFilterHitsItsGainAtCenter() {
        val b = Biquad.design(EqBand(FilterType.PEAK, 1000f, 6f, 1f), 48_000f)
        near(6.0, b.magnitudeDb(1000f, 48_000f).toDouble(), 0.05, "pico a 1 kHz")
        near(0.0, b.magnitudeDb(20f, 48_000f).toDouble(), 0.1, "lejos del pico")
    }

    @Test
    fun shelvesBoostOnlyTheirSide() {
        val low = Biquad.design(EqBand(FilterType.LOW_SHELF, 100f, -8f, 0.71f), 44_100f)
        near(-8.0, low.magnitudeDb(20f, 44_100f).toDouble(), 0.3, "low shelf en graves")
        near(0.0, low.magnitudeDb(8000f, 44_100f).toDouble(), 0.1, "low shelf en agudos")
        val high = Biquad.design(EqBand(FilterType.HIGH_SHELF, 8000f, 5f, 0.71f), 44_100f)
        near(5.0, high.magnitudeDb(19_000f, 44_100f).toDouble(), 0.6, "high shelf en agudos")
        near(0.0, high.magnitudeDb(100f, 44_100f).toDouble(), 0.1, "high shelf en graves")
    }

    @Test
    fun eqSettingsRoundTripAndFlatness() {
        val s = EqSettings(listOf(EqBand(FilterType.LOW_SHELF, 90f, 3.5f, 0.7f), EqBand(FilterType.PEAK, 2500f, -4f, 2.2f)), -3f)
        assertEquals(s, EqSettings.decode(s.encode()))
        assertTrue(EqSettings().isFlat)
        assertTrue(!s.isFlat)
        near(3.5, s.maxBoost().toDouble(), 0.3, "subida máxima")
        assertEquals(EqSettings(), EqSettings.decode("basura"))
    }

    @Test
    fun filteringASineChangesItsLevelLikeTheCurve() {
        val rate = 48_000
        val n = rate
        val buf = FloatArray(n * 2) { i -> (0.25 * kotlin.math.sin(2 * kotlin.math.PI * 1000 * (i / 2) / rate)).toFloat() }
        val chain = BiquadChain()
        chain.setFilters(listOf(Biquad.design(EqBand(FilterType.PEAK, 1000f, -6f, 1f), rate.toFloat())), 2)
        chain.process(buf, n)
        var peak = 0f
        for (i in n until n * 2) peak = kotlin.math.max(peak, kotlin.math.abs(buf[i]))
        near(0.25 * 0.501, peak.toDouble(), 0.01, "seno tras -6 dB")
    }

    @Test
    fun limiterNeverClips() {
        val buf = FloatArray(2000) { if (it % 2 == 0) 1.8f else -1.5f }
        val l = Limiter(0.95f)
        l.setSampleRate(44_100)
        l.process(buf, 1000, 2)
        assertTrue(buf.all { kotlin.math.abs(it) <= 0.9501f })
    }

    @Test
    fun loudnessOfReferenceSineMatchesEbu() {
        // EBU Tech 3341: seno de 1 kHz a -23 dBFS en estéreo = -23 LUFS.
        val rate = 48_000
        val amp = Normalization.linear(-23f)
        val m = LoudnessMeter()
        val frames = 4800
        var t = 0L
        repeat(200) {
            val buf = FloatArray(frames * 2)
            for (f in 0 until frames) {
                val v = (amp * kotlin.math.sin(2 * kotlin.math.PI * 1000 * t / rate)).toFloat()
                buf[f * 2] = v; buf[f * 2 + 1] = v; t++
            }
            m.feed(buf, frames, 2, rate)
        }
        near(-23.0, m.integrated()!!.toDouble(), 0.1, "LUFS")
        assertTrue(m.seconds > 15f)
    }

    @Test
    fun replayGainIsFoundInId3AndVorbisAndUtf16() {
        val id3 = "TXXX\u0000\u0000\u0000 \u0000\u0000\u0000REPLAYGAIN_TRACK_GAIN\u0000-7.42 dB".encodeToByteArray()
        assertEquals(-7.42f, ReplayGain.parse(id3).track)
        val vorbis = "....replaygain_album_gain=+1.50 dB....REPLAYGAIN_TRACK_GAIN=-3,10 dB".encodeToByteArray()
        val v = ReplayGain.parse(vorbis)
        assertEquals(1.5f, v.album)
        assertEquals(-3.1f, v.track)
        // UTF-16LE: cada letra seguida de un cero.
        val utf16 = "REPLAYGAIN_TRACK_GAIN\u0000-9.00 dB".flatMap { listOf(it.code.toByte(), 0) }.toByteArray()
        assertEquals(-9f, ReplayGain.parse(utf16).track)
        assertEquals(ReplayGainInfo(null, null), ReplayGain.parse("nada".encodeToByteArray()))
        near(-10.58, ReplayGain.toLufs(-7.42f).toDouble(), 0.01, "LUFS de ReplayGain")
    }

    @Test
    fun normalizationOnlyLowersAndPrefersTheRightGain() {
        assertEquals(-6f, Normalization.gainDb(-14, -8f, -10f, albumMode = false))
        assertEquals(-4f, Normalization.gainDb(-14, -8f, -10f, albumMode = true))
        assertEquals(0f, Normalization.gainDb(-14, -20f, null, albumMode = false))
        assertEquals(-2f, Normalization.gainDb(-14, null, -12f, albumMode = false))
        assertEquals(null, Normalization.gainDb(-14, null, null, albumMode = true))
    }

    @Test
    fun spectrogramFindsTheToneRow() {
        val tap = SpectrumTap()
        tap.enabled = true
        val rate = 44_100
        val buf = FloatArray(4096 * 2) { i -> (0.5 * kotlin.math.sin(2 * kotlin.math.PI * 2000 * (i / 2) / rate)).toFloat() }
        tap.write(buf, 4096, 2, rate)
        val a = SpectrogramAnalyzer(rows = 64)
        val col = FloatArray(64)
        assertTrue(a.column(tap, 0, col))
        val best = col.indices.maxByOrNull { col[it] }!!
        near(2000.0, a.rowHz(best).toDouble(), 2000.0 * 0.15, "fila del tono")
    }

    @Test
    fun fxChainPassesAudioUntouchedWhenOff() {
        AudioFx.setEq(false, EqSettings())
        AudioFx.tap.enabled = false
        AudioFx.measureKey = null
        val chain = FxChain()
        assertTrue(!chain.needed())
        val buf = FloatArray(200) { it / 200f }
        val copy = buf.copyOf()
        assertTrue(!chain.process(buf, 100, 2, 44_100))
        assertTrue(buf.contentEquals(copy))
        AudioFx.setEq(true, EqSettings(listOf(EqBand(FilterType.PEAK, 1000f, 3f, 1f))))
        assertTrue(chain.needed())
        AudioFx.setEq(false, EqSettings())
    }
}
