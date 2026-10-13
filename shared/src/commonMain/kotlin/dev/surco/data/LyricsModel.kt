package dev.surco.data

// Modelo de letras y analizador LRC: código común para Android e iOS.
// Ojo: corchetes y llaves literales siempre escapados en las expresiones regulares
// (el motor de Android, ICU, no los acepta sueltos).

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data object Loading : Lyrics
    data object None : Lyrics
    data object Instrumental : Lyrics
    data class Synced(val lines: List<LyricLine>, val source: String, val raw: String) : Lyrics
    data class Plain(val lines: List<String>, val source: String, val raw: String) : Lyrics
}

/** Índice de la línea que suena en [positionMs] (o -1 si aún no ha empezado ninguna). */
fun List<LyricLine>.indexAt(positionMs: Long): Int {
    var lo = 0
    var hi = size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return ans
}

object LrcParser {
    // Corchetes y llaves literales SIEMPRE escapados: el motor de Android (ICU) no los acepta sueltos.
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun isSynced(text: String) = text.lineSequence().any { timeTag.find(it.trim())?.range?.first == 0 }

    fun parse(text: String): List<LyricLine> {
        var offset = 0L
        val out = ArrayList<LyricLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val off = offsetTag.find(line)
            if (off != null) {
                offset = off.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            val times = ArrayList<Long>()
            var rest = line
            while (true) {
                val m = timeTag.find(rest) ?: break
                if (m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                times += min * 60_000 + sec * 1000 + ms
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) continue
            val words = rest.replace(wordTag, "").trim()
            times.forEach { out += LyricLine((it - offset).coerceAtLeast(0), words) }
        }
        out.sortBy { it.timeMs }
        return out
    }

    /** Letra sin marcas de tiempo, línea a línea. */
    fun plainLines(text: String): List<String> =
        text.lines().map { it.replace(timeTag, "").replace(wordTag, "").trim() }
            .dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
}
