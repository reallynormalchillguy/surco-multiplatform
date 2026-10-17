package dev.surco.net

/** Qué es un enlace de música compartido. */
enum class LinkKind { ALBUM, TRACK, ARTIST }

/** Lo que dice un enlace de Spotify: tipo, título, artista y portada. */
data class LinkedMusic(
    val kind: LinkKind,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val url: String,
)

/**
 * Enlaces de Spotify sin la API de Spotify (cerrada a apps pequeñas): se lee la ficha pública de la página
 * (las etiquetas Open Graph que usan WhatsApp o Telegram para la vista previa). Código puro, sin red.
 */
object SpotifyPage {
    // Sin llaves ni corchetes sueltos: el motor de expresiones de Android (ICU) es estricto.
    private val linkRegex = Regex("""https?://(?:open\.spotify\.com|spotify\.link|spotify\.app\.link)/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val uriRegex = Regex("""spotify:(album|track|artist):([A-Za-z0-9]+)""")
    private val metaRegex = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([a-zA-Z:_-]+)\s*=\s*"([^"]*)"""")

    /** El primer enlace de Spotify que aparezca en un texto (p. ej. «Escucha esto: https://open.spotify.com/…»). */
    fun findLink(text: String): String? {
        linkRegex.find(text)?.let { return it.value.trimEnd('.', ',', ')', '!', '?') }
        val m = uriRegex.find(text) ?: return null
        return "https://open.spotify.com/${m.groupValues[1]}/${m.groupValues[2]}"
    }

    /** Tipo según la ruta del enlace (open.spotify.com/intl-es/album/…). */
    fun kindOf(url: String): LinkKind? = when {
        "/album/" in url -> LinkKind.ALBUM
        "/track/" in url -> LinkKind.TRACK
        "/artist/" in url -> LinkKind.ARTIST
        else -> null
    }

    /** Etiquetas «meta» de una página: property/name → content. */
    fun metaTags(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in metaRegex.findAll(html)) {
            val attrs = attrRegex.findAll(m.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val key = attrs["property"] ?: attrs["name"] ?: continue
            val content = attrs["content"] ?: continue
            if (key !in out) out[key] = decodeEntities(content)
        }
        return out
    }

    /**
     * Interpreta la ficha de un enlace. Spotify pone, por ejemplo:
     * og:title «OK Computer», og:description «Radiohead · Album · 1997 · 12 songs»,
     * music:musician_description «Radiohead», og:type «music.album».
     */
    fun parse(url: String, html: String): LinkedMusic? {
        val meta = metaTags(html)
        val title = cleanTitle(meta["og:title"]?.trim().orEmpty())
        if (title.isBlank()) return null
        val type = meta["og:type"].orEmpty()
        val kind = when {
            type == "music.album" -> LinkKind.ALBUM
            type == "music.song" -> LinkKind.TRACK
            type == "profile" || type == "music.musician" -> LinkKind.ARTIST
            else -> kindOf(meta["og:url"] ?: url) ?: return null
        }
        val desc = meta["og:description"].orEmpty()
        val artist = when (kind) {
            LinkKind.ARTIST -> title
            else -> meta["music:musician_description"]?.trim()?.takeIf { it.isNotBlank() }
                ?: artistFromTitle(meta["og:title"].orEmpty()).takeIf { it.isNotBlank() }
                ?: artistFromDescription(desc, title)
        }
        return LinkedMusic(kind, title, artist, meta["og:image"], meta["og:url"] ?: url)
    }

    /**
     * Spotify a veces da el título «de buscador»: «Listen to OK Computer - Album by Radiohead | Spotify»,
     * «Paranoid Android - song and lyrics by Radiohead | Spotify». Se deja sólo «OK Computer».
     */
    fun cleanTitle(raw: String): String {
        var t = raw.removeSuffix(" | Spotify").trim()
        if (t.startsWith("Listen to ")) t = t.removePrefix("Listen to ")
        for (marker in listOf(" - Album by ", " - Single by ", " - EP by ", " - Compilation by ", " - song and lyrics by ", " - song by ")) {
            val i = t.lastIndexOf(marker)
            if (i > 0) { t = t.substring(0, i); break }
        }
        return t.trim()
    }

    /** Artista dentro del título «de buscador» («… - Album by Radiohead | Spotify»), si lo trae. */
    fun artistFromTitle(raw: String): String {
        val t = raw.removeSuffix(" | Spotify")
        for (marker in listOf(" - Album by ", " - Single by ", " - EP by ", " - Compilation by ", " - song and lyrics by ", " - song by ")) {
            val i = t.lastIndexOf(marker)
            if (i > 0) return t.substring(i + marker.length).trim()
        }
        return ""
    }

    /**
     * «Radiohead · Album · 1997 · 12 songs» → «Radiohead»; «Listen to Airbag on Spotify. Song · Radiohead · 1997» → «Radiohead».
     * Se descartan las palabras de tipo, años y recuentos.
     */
    fun artistFromDescription(desc: String, title: String): String {
        val text = desc.substringAfterLast(". ", desc)
        val skip = setOf("album", "álbum", "song", "canción", "single", "ep", "compilation", "recopilatorio", "playlist")
        return text.split(" · ", " • ").map { it.trim() }.firstOrNull { part ->
            part.isNotBlank() && part.lowercase() !in skip && part != title &&
                !part.all { it.isDigit() } && !part.first().isDigit()
        }.orEmpty()
    }

    /** Una canción dentro de la ficha de un álbum de Spotify. */
    data class AlbumTrack(val url: String, val title: String?, val disc: Int?, val number: Int?)

    private val trackUriRegex = Regex(""""uri"\s*:\s*"spotify:track:([A-Za-z0-9]+)"""")
    private val titleKeyRegex = Regex(""""(?:title|name)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val songMetaRegex = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * Las canciones de la ficha de un álbum. Sirve tanto la página normal (etiquetas «music:song»,
     * con disco y número de pista) como la del reproductor incrustado (/embed/album/…, con los títulos).
     */
    fun albumTracks(html: String): List<AlbumTrack> {
        val out = ArrayList<AlbumTrack>()
        // 1) Etiquetas music:song / music:song:disc / music:song:track, en orden.
        var cur: AlbumTrack? = null
        for (m in songMetaRegex.findAll(html)) {
            val attrs = attrRegex.findAll(m.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val key = attrs["property"] ?: attrs["name"] ?: continue
            val content = attrs["content"] ?: continue
            when (key) {
                "music:song" -> { cur?.let(out::add); cur = AlbumTrack(content, null, null, null) }
                "music:song:disc" -> cur = cur?.copy(disc = content.toIntOrNull())
                "music:song:track" -> cur = cur?.copy(number = content.toIntOrNull())
            }
        }
        cur?.let(out::add)
        // 2) Datos del reproductor incrustado: «"uri":"spotify:track:ID"» con su título cerca.
        for (m in trackUriRegex.findAll(html)) {
            val id = m.groupValues[1]
            val url = "https://open.spotify.com/track/$id"
            val start = (m.range.first - 400).coerceAtLeast(0)
            val end = (m.range.last + 400).coerceAtMost(html.length)
            // El título dentro del mismo objeto JSON; si no, el más cercano a la URI.
            val objStart = html.lastIndexOf('{', m.range.first).coerceAtLeast(0)
            val objEnd = html.indexOf('}', m.range.last).let { if (it < 0) html.length else it }
            val title = (titleKeyRegex.find(html.substring(objStart, objEnd))
                ?: titleKeyRegex.findAll(html.substring(start, end)).minByOrNull { kotlin.math.abs(start + it.range.first - m.range.first) })
                ?.groupValues?.get(1)?.let(::unescapeJson)
            val i = out.indexOfFirst { it.url.endsWith("/track/$id") }
            if (i >= 0) { if (out[i].title == null) out[i] = out[i].copy(title = title) }
            else out.add(AlbumTrack(url, title, null, null))
        }
        return out
    }

    /** La canción del álbum que corresponde a la nuestra: por título y, si no, por disco + número de pista. */
    fun pickTrack(tracks: List<AlbumTrack>, title: String, disc: Int, number: Int): String? {
        tracks.filter { it.title != null }
            .map { it to dev.surco.data.AlbumMatcher.similarity(it.title!!, title) }
            .filter { it.second >= 0.8f }
            .maxByOrNull { it.second }?.let { return it.first.url }
        if (number <= 0) return null
        return tracks.firstOrNull { it.number == number && (it.disc ?: 1) == (disc.takeIf { d -> d > 0 } ?: 1) }?.url
    }

    private fun unescapeJson(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { sb.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> {}
                'u' -> { s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)?.let { sb.append(it.toChar()) }; i += 4 }
                else -> sb.append(n)
            }
            i += 2
        }
        return sb.toString()
    }

    /** Enlace de búsqueda de Spotify (cuando no se conoce el enlace exacto): abre la búsqueda en la app de Spotify. */
    fun searchUrl(query: String): String = "https://open.spotify.com/search/" + encodePath(query)

    private fun encodePath(s: String): String = buildString {
        for (b in s.trim().encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch)
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val named = mapOf("&amp;" to "&", "&quot;" to "\"", "&#x27;" to "'", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">")
        var r = s
        named.forEach { (k, v) -> r = r.replace(k, v) }
        // Numéricas restantes (&#225; / &#xE1;)
        val num = Regex("""&#(x?)([0-9a-fA-F]+);""")
        return num.replace(r) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isNotEmpty()) 16 else 10) ?: return@replace m.value
            code.toChar().toString()
        }
    }
}
