package dev.surco.data

/**
 * Estadísticas de escucha a partir de Last.fm. Este archivo es código puro (sin red ni Android):
 * el modelo y todos los cálculos (reloj de escucha, semana, actividad, rachas, maratones…).
 */

/** Periodo de las estadísticas, con el nombre que usa la API de Last.fm. */
enum class StatsPeriod(val api: String, val days: Int) {
    WEEK("7day", 7),
    MONTH("1month", 30),
    QUARTER("3month", 90),
    HALF("6month", 180),
    YEAR("12month", 365),
    ALL("overall", 0),
}

/** El perfil de Last.fm del usuario. */
data class LfmProfile(
    val name: String,
    val realName: String,
    val imageUrl: String?,
    val playcount: Long,
    val artists: Long,
    val albums: Long,
    val tracks: Long,
    /** Fecha de alta (segundos desde 1970). */
    val registeredAt: Long,
    val country: String,
    val url: String,
)

/** Un artista, álbum o canción de un top, con sus escuchas. */
data class TopItem(
    val name: String,
    val artist: String,
    val plays: Long,
    val imageUrl: String?,
    val url: String,
    /** Id de Deezer del artista (para abrir su página), si se encontró. */
    val deezerId: Long = 0,
)

/** Una escucha («scrobble»): cuándo (segundos desde 1970) y qué. */
data class Scrobble(val at: Long, val artist: String, val title: String, val album: String)

/** Un tramo del gráfico de actividad: desde el día [startDay] (días desde 1970, hora local), [count] escuchas. */
data class ActivityBucket(val startDay: Long, val count: Int)

data class DayCount(val day: Long, val count: Int)

/** La canción que más se repitió en un solo día. */
data class Obsession(val title: String, val artist: String, val count: Int, val day: Long)

/** La sesión más larga sin parar: escuchas seguidas con pausas cortas. */
data class Marathon(val start: Long, val end: Long, val tracks: Int, val day: Long = 0) {
    val minutes: Int get() = ((end - start) / 60 + 3).toInt()
}

/** Un género (etiqueta de Last.fm) y qué parte de tus escuchas representa (0–1). */
data class GenreShare(val name: String, val share: Float)

/** Todo lo que enseña la pantalla de estadísticas para un periodo. */
data class StatsReport(
    val profile: LfmProfile,
    val period: StatsPeriod,
    /** Escuchas en el periodo según Last.fm (exacto). */
    val periodPlays: Long,
    /** Escuchas analizadas para los gráficos (puede ser menos que [periodPlays] si hay muchísimas). */
    val sampled: Int,
    val sampleComplete: Boolean,
    /** Escuchas por hora del día (24). */
    val hours: List<Int>,
    /** Escuchas por día de la semana (lunes = 0) y hora: 7 × 24. */
    val week: List<List<Int>>,
    val activity: List<ActivityBucket>,
    /** Días que abarca cada tramo de [activity] (1 = diario, 7 = semanal, 30 = mensual). */
    val bucketDays: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val recordDay: DayCount?,
    val activeDays: Int,
    val topArtists: List<TopItem>,
    val topAlbums: List<TopItem>,
    val topTracks: List<TopItem>,
    val genres: List<GenreShare>,
    val obsession: Obsession?,
    val marathon: Marathon?,
    val uniqueArtists: Int,
    /** Parte de las escuchas que son de los 5 artistas top (0–1). */
    val top5Share: Float,
    /** Parte de las escuchas que son canciones de tu biblioteca de Surco (0–1), o null si no se pudo calcular. */
    val inLibraryShare: Float?,
    val lovedTracks: Int,
    /** Hora a la que se calculó (milisegundos). */
    val createdAt: Long,
) {
    /** Hora con más escuchas (0–23), o -1 si no hay datos. */
    val peakHour: Int get() = if (hours.all { it == 0 }) -1 else hours.indices.maxBy { hours[it] }

    /** Minutos de música aproximados (3,5 min por canción, la media de la música popular). */
    val minutesEstimate: Long get() = periodPlays * 7 / 2

    /** Día de la semana y hora con más escuchas (o null). */
    val peakSlot: Pair<Int, Int>?
        get() {
            var best: Pair<Int, Int>? = null
            var max = 0
            week.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > max) { max = v; best = d to h } } }
            return best
        }
}

/** Tipo de oyente según la hora punta. */
enum class Chronotype { EARLY_BIRD, DAYTIME, AFTERNOON, EVENING, NIGHT_OWL }

object StatsMath {

    /** Días desde 1970 en hora local. */
    fun localDay(at: Long, offset: (Long) -> Int): Long = (at + offset(at)).floorDiv(86_400L)

    fun localHour(at: Long, offset: (Long) -> Int): Int = ((at + offset(at)).mod(86_400L) / 3600).toInt()

    /** Día de la semana (lunes = 0 … domingo = 6) de un día desde 1970 (que fue jueves). */
    fun weekday(day: Long): Int = (day + 3).mod(7L).toInt()

    /** Fecha (año, mes 1–12, día) de un día desde 1970. Algoritmo de Howard Hinnant. */
    fun civil(day: Long): Triple<Int, Int, Int> {
        val z = day + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }

    fun hours(scrobbles: List<Scrobble>, offset: (Long) -> Int): List<Int> {
        val h = IntArray(24)
        for (s in scrobbles) h[localHour(s.at, offset)]++
        return h.toList()
    }

    fun week(scrobbles: List<Scrobble>, offset: (Long) -> Int): List<List<Int>> {
        val w = Array(7) { IntArray(24) }
        for (s in scrobbles) w[weekday(localDay(s.at, offset))][localHour(s.at, offset)]++
        return w.map { it.toList() }
    }

    /** Escuchas por día (sólo los días con alguna). */
    fun perDay(scrobbles: List<Scrobble>, offset: (Long) -> Int): Map<Long, Int> {
        val m = HashMap<Long, Int>()
        for (s in scrobbles) { val d = localDay(s.at, offset); m[d] = (m[d] ?: 0) + 1 }
        return m
    }

    /**
     * Tramos del gráfico de actividad entre [fromDay] y [toDay] (incluidos): diarios hasta 3 meses,
     * semanales (empezando en lunes) hasta un año y mensuales después.
     */
    fun activity(perDay: Map<Long, Int>, fromDay: Long, toDay: Long): Pair<List<ActivityBucket>, Int> {
        val span = toDay - fromDay + 1
        if (span <= 0) return emptyList<ActivityBucket>() to 1
        return when {
            span <= 92 -> (fromDay..toDay).map { ActivityBucket(it, perDay[it] ?: 0) } to 1
            span <= 400 -> {
                val first = fromDay - weekday(fromDay)
                val out = ArrayList<ActivityBucket>()
                var d = first
                while (d <= toDay) {
                    out += ActivityBucket(d, (d until d + 7).sumOf { perDay[it] ?: 0 })
                    d += 7
                }
                out to 7
            }
            else -> {
                // Por meses naturales.
                val out = ArrayList<ActivityBucket>()
                var cur = -1L
                var curKey = ""
                var sum = 0
                for (d in fromDay..toDay) {
                    val (y, m, _) = civil(d)
                    val key = "$y-$m"
                    if (key != curKey) {
                        if (cur >= 0) out += ActivityBucket(cur, sum)
                        cur = d; curKey = key; sum = 0
                    }
                    sum += perDay[d] ?: 0
                }
                if (cur >= 0) out += ActivityBucket(cur, sum)
                out to 30
            }
        }
    }

    /** (racha actual, racha más larga) en días seguidos con alguna escucha. La actual vale si llega a hoy o ayer. */
    fun streaks(days: Set<Long>, today: Long): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        var current = 0
        var d = if (today in days) today else today - 1
        while (d in days) { current++; d-- }
        return current to best
    }

    /** La canción más repetida en un mismo día (al menos 3 veces). */
    fun obsession(scrobbles: List<Scrobble>, offset: (Long) -> Int): Obsession? {
        val counts = HashMap<Triple<Long, String, String>, Int>()
        val names = HashMap<Triple<Long, String, String>, Scrobble>()
        for (s in scrobbles) {
            val k = Triple(localDay(s.at, offset), s.artist.lowercase(), s.title.lowercase())
            counts[k] = (counts[k] ?: 0) + 1
            names.getOrPut(k) { s }
        }
        val best = counts.maxByOrNull { it.value } ?: return null
        if (best.value < 3) return null
        val s = names[best.key]!!
        return Obsession(s.title, s.artist, best.value, best.key.first)
    }

    /**
     * La sesión más larga: escuchas seguidas en las que entre una y otra no pasan más de [gapSec]
     * (Last.fm apunta la hora de empezar cada canción; 20 minutos dejan sitio a una canción larga).
     */
    fun marathon(scrobbles: List<Scrobble>, gapSec: Long = 20 * 60): Marathon? {
        if (scrobbles.size < 2) return null
        val s = scrobbles.map { it.at }.sorted()
        var best: Marathon? = null
        var start = s[0]
        var count = 1
        for (i in 1 until s.size) {
            if (s[i] - s[i - 1] <= gapSec) count++ else { start = s[i]; count = 1 }
            val m = Marathon(start, s[i], count)
            if (count >= 2 && (best == null || m.end - m.start > best.end - best.start)) best = m
        }
        return best?.takeIf { it.tracks >= 5 }
    }

    /** Hora punta → tipo de oyente. */
    fun chronotype(peakHour: Int): Chronotype = when (peakHour) {
        in 5..9 -> Chronotype.EARLY_BIRD
        in 10..13 -> Chronotype.DAYTIME
        in 14..18 -> Chronotype.AFTERNOON
        in 19..21 -> Chronotype.EVENING
        else -> Chronotype.NIGHT_OWL
    }

    /** Parte del día: madrugada (0–6), mañana (6–12), tarde (12–18), noche (18–24), en proporción. */
    fun dayParts(hours: List<Int>): List<Float> {
        val total = hours.sum().toFloat().takeIf { it > 0 } ?: return listOf(0f, 0f, 0f, 0f)
        return (0 until 4).map { p -> (p * 6 until p * 6 + 6).sumOf { hours[it] } / total }
    }

    /** Etiquetas de Last.fm que no son géneros. */
    private val notGenres = setOf(
        "seen live", "favorites", "favourite", "favorite", "favourites", "my favorites", "love", "beautiful",
        "awesome", "albums i own", "under 2000 listeners", "spotify", "all", "cool", "best", "amazing",
        "male vocalists", "female vocalists", "female vocalist", "male vocalist", "singer-songwriter", "british", "american",
        "uk", "usa", "spanish", "english", "00s", "90s", "80s", "70s", "60s", "10s", "2010s", "2000s", "1990s", "1980s",
    )

    /**
     * «ADN musical»: las etiquetas de cada artista top, pesadas por sus escuchas.
     * [tags] = por artista, lista de (etiqueta, peso 0–100 de Last.fm).
     */
    fun genres(artists: List<TopItem>, tags: Map<String, List<Pair<String, Int>>>, max: Int = 6): List<GenreShare> {
        val weight = HashMap<String, Double>()
        val label = HashMap<String, String>()
        for (a in artists) {
            val list = tags[a.name].orEmpty()
                .filter { (t, _) -> t.isNotBlank() && t.lowercase() !in notGenres && !t.equals(a.name, ignoreCase = true) }
                .take(5)
            val sum = list.sumOf { it.second }.takeIf { it > 0 } ?: continue
            for ((t, w) in list) {
                val k = t.lowercase().replace('-', ' ').trim()
                weight[k] = (weight[k] ?: 0.0) + a.plays.toDouble() * w / sum
                if (k !in label) label[k] = t.lowercase().replaceFirstChar { it.titlecase() }
            }
        }
        val total = weight.values.sum().takeIf { it > 0 } ?: return emptyList()
        return weight.entries.sortedByDescending { it.value }.take(max)
            .map { GenreShare(label[it.key] ?: it.key, (it.value / total).toFloat()) }
    }

    /** Parte de las escuchas que son de canciones que están en la biblioteca. */
    fun inLibrary(scrobbles: List<Scrobble>, library: Set<String>): Float? {
        if (scrobbles.isEmpty() || library.isEmpty()) return null
        val hit = scrobbles.count { key(it.artist, it.title) in library }
        return hit.toFloat() / scrobbles.size
    }

    /** Clave para casar una canción de Last.fm con la biblioteca. */
    fun key(artist: String, title: String) = AlbumMatcher.norm(artist).replace(" ", "") + "|" + AlbumMatcher.norm(title).replace(" ", "")

    /** Monta el informe con los datos ya descargados. */
    fun report(
        profile: LfmProfile,
        period: StatsPeriod,
        periodPlays: Long,
        scrobbles: List<Scrobble>,
        sampleComplete: Boolean,
        topArtists: List<TopItem>,
        topAlbums: List<TopItem>,
        topTracks: List<TopItem>,
        genres: List<GenreShare>,
        library: Set<String>,
        lovedTracks: Int,
        nowSec: Long,
        offset: (Long) -> Int,
    ): StatsReport {
        val today = localDay(nowSec, offset)
        val perDay = perDay(scrobbles, offset)
        val fromDay = when {
            period.days > 0 -> today - period.days + 1
            perDay.isEmpty() -> today
            else -> perDay.keys.min()
        }
        val (activity, bucketDays) = activity(perDay, fromDay, today)
        val (current, best) = streaks(perDay.keys, today)
        val record = perDay.maxByOrNull { it.value }?.let { DayCount(it.key, it.value) }
        val artistPlays = topArtists.sumOf { it.plays }
        val top5 = topArtists.take(5).sumOf { it.plays }
        val totalForShare = if (period == StatsPeriod.ALL) profile.playcount else periodPlays
        return StatsReport(
            profile = profile,
            period = period,
            periodPlays = periodPlays,
            sampled = scrobbles.size,
            sampleComplete = sampleComplete,
            hours = hours(scrobbles, offset),
            week = week(scrobbles, offset),
            activity = activity,
            bucketDays = bucketDays,
            currentStreak = current,
            bestStreak = best,
            recordDay = record,
            activeDays = perDay.size,
            topArtists = topArtists,
            topAlbums = topAlbums,
            topTracks = topTracks,
            genres = genres,
            obsession = obsession(scrobbles, offset),
            marathon = marathon(scrobbles)?.let { it.copy(day = localDay(it.start, offset)) },
            uniqueArtists = scrobbles.map { it.artist.lowercase() }.distinct().size,
            top5Share = if (totalForShare > 0 && artistPlays > 0) (top5.toFloat() / totalForShare).coerceIn(0f, 1f) else 0f,
            inLibraryShare = inLibrary(scrobbles, library),
            lovedTracks = lovedTracks,
            createdAt = nowSec * 1000,
        )
    }
}
