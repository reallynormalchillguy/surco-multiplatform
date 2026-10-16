package dev.surco.data

import kotlin.math.abs
import kotlin.math.max

/** Un álbum encontrado en internet (MusicBrainz o Deezer), tal como sale en la lista de resultados. */
data class AlbumCandidate(
    val source: String,
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val trackCount: Int,
    val thumbUrl: String?,
    /** Formato, país, tipo… para distinguir ediciones («CD · GB», «Deluxe»). */
    val detail: String = "",
)

data class RemoteTrack(val disc: Int, val number: Int, val title: String, val artist: String, val durationMs: Long)

/** Datos completos de un álbum de internet. */
data class AlbumInfo(
    val candidate: AlbumCandidate,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String,
    val tracks: List<RemoteTrack>,
    val coverUrl: String?,
    val coverFallbackUrl: String? = null,
)

/** Qué partes de [AlbumInfo] aplicar a las canciones del álbum. */
data class AlbumInfoChoice(
    val album: Boolean = true,
    val albumArtist: Boolean = true,
    val year: Boolean = true,
    val genre: Boolean = true,
    val titles: Boolean = true,
    val numbers: Boolean = true,
    val trackArtists: Boolean = false,
    val cover: Boolean = false,
    /** Sólo rellenar lo que falta: no sobrescribir datos que ya tienen las canciones. */
    val onlyEmpty: Boolean = false,
)

/** Cambio propuesto para una canción (null = sin cambios en ese campo). */
data class TrackPlan(
    val track: Track,
    val remote: RemoteTrack?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: String?,
    val trackNumber: String?,
    val discNumber: String?,
    val genre: String?,
) {
    val hasChanges get() = listOf(title, artist, album, albumArtist, year, trackNumber, discNumber, genre).any { it != null }
}

/**
 * Empareja las canciones locales con las del álbum de internet y calcula qué cambiaría.
 * Puro (sin red ni archivos) para poder probarlo.
 */
object AlbumMatcher {

    /** Canción local → canción remota. Las que no casan con nada no aparecen. */
    fun match(local: List<Track>, remote: List<RemoteTrack>): Map<String, RemoteTrack> {
        if (local.isEmpty() || remote.isEmpty()) return emptyMap()
        val sameCount = local.size == remote.size
        val scored = ArrayList<Triple<Int, Int, Float>>()
        local.forEachIndexed { li, l ->
            remote.forEachIndexed { ri, r ->
                val s = score(l, li, r, ri, sameCount)
                if (s >= MIN_SCORE) scored += Triple(li, ri, s)
            }
        }
        scored.sortByDescending { it.third }
        val usedL = HashSet<Int>()
        val usedR = HashSet<Int>()
        val out = HashMap<String, RemoteTrack>()
        for ((li, ri, _) in scored) {
            if (li in usedL || ri in usedR) continue
            usedL += li; usedR += ri
            out[local[li].uri] = remote[ri]
        }
        return out
    }

    private fun score(l: Track, li: Int, r: RemoteTrack, ri: Int, sameCount: Boolean): Float {
        val t = max(similarity(l.title, r.title), similarity(titleFromFile(l.fileName), r.title))
        val num = if (l.trackNumber > 0 && l.trackNumber == r.number &&
            (l.discNumber == r.disc || (l.discNumber <= 1 && r.disc <= 1))) 1f else 0f
        val dur = if (l.durationMs > 0 && r.durationMs > 0)
            (1f - abs(l.durationMs - r.durationMs) / 8000f).coerceAtLeast(0f) else 0f
        val pos = if (sameCount && li == ri) 1f else 0f
        return 0.5f * t + 0.2f * num + 0.2f * dur + 0.1f * pos
    }

    /** Plan de cambios para cada canción del álbum (en el orden de [local]). */
    fun plan(local: List<Track>, info: AlbumInfo, choice: AlbumInfoChoice): List<TrackPlan> {
        val matches = match(local, info.tracks)
        val multiDisc = info.tracks.map { it.disc }.distinct().size > 1
        val compilation = info.tracks.map { norm(it.artist) }.filter { it.isNotEmpty() }.distinct().size > 1
        fun pick(current: String, new: String, isEmpty: Boolean, enabled: Boolean): String? {
            if (!enabled || new.isBlank()) return null
            if (choice.onlyEmpty && !isEmpty) return null
            return new.takeIf { it != current }
        }
        return local.map { t ->
            val r = matches[t.uri]
            val fileTitle = t.fileName.substringBeforeLast('.')
            val yearStr = if (info.year > 0) info.year.toString() else ""
            TrackPlan(
                track = t,
                remote = r,
                title = r?.let { pick(t.title, it.title, t.title.isBlank() || t.title == fileTitle, choice.titles) },
                artist = r?.let {
                    // En un álbum de un solo artista, el de la pista es el del álbum.
                    val a = it.artist.ifBlank { info.artist }
                    pick(t.artist, a, t.artist.isBlank() || t.artist == UNKNOWN_ARTIST, choice.trackArtists || (!compilation && t.artist == UNKNOWN_ARTIST))
                },
                album = pick(t.album, info.title, t.album.isBlank() || t.album == UNKNOWN_ALBUM, choice.album),
                albumArtist = pick(t.albumArtist, info.artist, t.albumArtist.isBlank(), choice.albumArtist),
                year = pick(if (t.year > 0) t.year.toString() else "", yearStr, t.year <= 0, choice.year),
                trackNumber = r?.let {
                    pick(if (t.trackNumber > 0) t.trackNumber.toString() else "", it.number.takeIf { n -> n > 0 }?.toString() ?: "",
                        t.trackNumber <= 0, choice.numbers)
                },
                discNumber = r?.takeIf { multiDisc || t.discNumber > 1 }?.let {
                    pick(t.discNumber.toString(), it.disc.coerceAtLeast(1).toString(), t.discNumber <= 1 && multiDisc, choice.numbers)
                },
                genre = pick(t.genre, info.genre, t.genre.isBlank(), choice.genre),
            )
        }
    }

    /** «01 - Canción.flac», «1-02 Canción.mp3», «01. Canción» → «Canción». */
    fun titleFromFile(fileName: String): String {
        var s = fileName.substringBeforeLast('.')
        var i = 0
        // Números iniciales y separadores (incluye «1-02», «CD1-03»…).
        while (i < s.length && (s[i].isDigit() || s[i] in " -._)") ) i++
        if (i in 1 until s.length) s = s.substring(i)
        return s.trim()
    }

    /** Parecido entre dos títulos, de 0 a 1 (coeficiente de Dice sobre pares de letras). */
    fun similarity(a: String, b: String): Float {
        val x = norm(a).replace(" ", "")
        val y = norm(b).replace(" ", "")
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        if (x.length < 2 || y.length < 2) return 0f
        val bx = x.windowed(2).groupingBy { it }.eachCount().toMutableMap()
        var common = 0
        for (g in y.windowed(2)) {
            val c = bx[g] ?: 0
            if (c > 0) { common++; bx[g] = c - 1 }
        }
        return 2f * common / ((x.length - 1) + (y.length - 1))
    }

    /** Minúsculas, sin acentos ni signos, sin paréntesis («(Remastered 2011)»). */
    fun norm(s: String): String {
        val sb = StringBuilder()
        var depth = 0
        for (ch in s.lowercase()) {
            when {
                ch == '(' || ch == '[' -> depth++
                ch == ')' || ch == ']' -> if (depth > 0) depth--
                depth > 0 -> {}
                ch.isLetterOrDigit() -> sb.append(fold(ch))
                ch == '&' -> sb.append(" and ")
                else -> sb.append(' ')
            }
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ").removePrefix("the ")
    }

    private fun fold(c: Char): Char = when (c) {
        'á', 'à', 'ä', 'â', 'ã', 'å', 'ā' -> 'a'
        'é', 'è', 'ë', 'ê', 'ē' -> 'e'
        'í', 'ì', 'ï', 'î', 'ī' -> 'i'
        'ó', 'ò', 'ö', 'ô', 'õ', 'ø', 'ō' -> 'o'
        'ú', 'ù', 'ü', 'û', 'ū' -> 'u'
        'ñ' -> 'n'
        'ç' -> 'c'
        'ý', 'ÿ' -> 'y'
        else -> c
    }

    private const val MIN_SCORE = 0.3f
}
