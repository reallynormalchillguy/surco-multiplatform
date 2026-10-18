package dev.surco.playback

import android.content.Context
import android.net.Uri
import dev.surco.audio.Normalization
import dev.surco.audio.ReplayGain
import dev.surco.audio.ReplayGainInfo
import dev.surco.data.Settings
import dev.surco.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow

/**
 * Sonoridad de cada canción para normalizar el volumen: la de sus etiquetas ReplayGain si las trae y,
 * si no, la que mide Surco la primera vez que la escuchas entera (se guarda para las siguientes).
 */
class AudioLevels(private val context: Context) {
    private val sp = context.getSharedPreferences("loudness", Context.MODE_PRIVATE)
    private val rgCache = HashMap<String, ReplayGainInfo>()

    fun measured(uri: String): Float? = if (sp.contains(uri)) sp.getFloat(uri, 0f) else null

    fun saveMeasured(uri: String, lufs: Float) {
        if (lufs.isFinite() && lufs > -70f && lufs < 6f) sp.edit().putFloat(uri, lufs).apply()
    }

    /** ReplayGain del archivo (lee el principio y el final, donde van las etiquetas). */
    suspend fun replayGain(uri: String): ReplayGainInfo {
        rgCache[uri]?.let { return it }
        val info = withContext(Dispatchers.IO) {
            runCatching {
                val cr = context.contentResolver
                val u = Uri.parse(uri)
                val head = cr.openInputStream(u)?.use { s -> s.readNBytesCompat(HEAD) } ?: ByteArray(0)
                var rg = ReplayGain.parse(head)
                if (rg.track == null && rg.album == null) {
                    val size = cr.openFileDescriptor(u, "r")?.use { it.statSize } ?: -1L
                    if (size > HEAD) {
                        val tail = cr.openInputStream(u)?.use { s ->
                            var skip = size - TAIL
                            while (skip > 0) { val n = s.skip(skip); if (n <= 0) break; skip -= n }
                            s.readNBytesCompat(TAIL)
                        } ?: ByteArray(0)
                        rg = ReplayGain.parse(tail)
                    }
                }
                rg
            }.getOrDefault(ReplayGainInfo(null, null))
        }
        rgCache[uri] = info
        return info
    }

    /** Sonoridad conocida de una canción (LUFS), sin leer el archivo. */
    private fun knownLufs(t: Track): Float? = rgCache[t.uri]?.track?.let(ReplayGain::toLufs) ?: measured(t.uri)

    /**
     * Ganancia (dB) para esta canción según los ajustes, y si hace falta medirla al escucharla.
     * [album] = las canciones de su álbum (para el modo álbum o si ésta aún no se conoce).
     */
    suspend fun gainFor(track: Track, album: List<Track>, s: Settings): Pair<Float?, Boolean> {
        val rg = replayGain(track.uri)
        val trackLufs = rg.track?.let(ReplayGain::toLufs) ?: measured(track.uri)
        val albumLufs = rg.album?.let(ReplayGain::toLufs) ?: average(album.mapNotNull { knownLufs(it) })
        val needsMeasure = rg.track == null && measured(track.uri) == null
        return Normalization.gainDb(s.normalizeTarget, trackLufs, albumLufs, s.normalizeAlbum) to needsMeasure
    }

    /** Media «de energía» de varias sonoridades (no la media aritmética de los dB). */
    private fun average(list: List<Float>): Float? {
        if (list.isEmpty()) return null
        val e = list.sumOf { 10.0.pow(it / 10.0) } / list.size
        return (10 * log10(e)).toFloat()
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) { val r = read(buf, off, n - off); if (r <= 0) break; off += r }
        return if (off == n) buf else buf.copyOf(off)
    }

    companion object {
        private const val HEAD = 512 * 1024
        private const val TAIL = 128 * 1024
    }
}
