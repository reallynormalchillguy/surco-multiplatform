package dev.surco.util

/** Decodificación de textos antiguos (Windows-1252 / Latin-1), que Kotlin común no trae. */
object Charsets1252 {
    private val high = charArrayOf(
        '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
        '\u0090', '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
    )

    fun decode(bytes: ByteArray): String = buildString(bytes.size) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(if (v in 0x80..0x9F) high[v - 0x80] else v.toChar())
        }
    }

    /** UTF-8 si es válido; si no, Windows-1252. Quita la marca BOM. */
    fun decodeText(bytes: ByteArray): String {
        val utf = bytes.decodeToString()
        return if ('�' in utf) decode(bytes) else utf.removePrefix("﻿")
    }
}
