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
