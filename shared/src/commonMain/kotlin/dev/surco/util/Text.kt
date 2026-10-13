package dev.surco.util

/**
 * Quita tildes y diacríticos de las letras latinas (equivalente práctico a NFD + quitar marcas,
 * que no existe en Kotlin común).
 */
object Text {
    private val map: Map<Char, Char> = buildMap {
        fun add(base: Char, chars: String) = chars.forEach { put(it, base) }
        add('a', "àáâãäåāăąǎȁȃạảấầẩẫậắằẳẵặ"); add('A', "ÀÁÂÃÄÅĀĂĄǍ")
        add('c', "çćĉċč"); add('C', "ÇĆĈĊČ")
        add('d', "ď"); add('D', "Ď")
        add('e', "èéêëēĕėęěȅȇẹẻẽếềểễệ"); add('E', "ÈÉÊËĒĔĖĘĚ")
        add('g', "ĝğġģǧ"); add('G', "ĜĞĠĢǦ")
        add('h', "ĥ"); add('H', "Ĥ")
        add('i', "ìíîïĩīĭįǐȉȋịỉ"); add('I', "ÌÍÎÏĨĪĬĮİǏ")
        add('j', "ĵ"); add('J', "Ĵ")
        add('k', "ķǩ"); add('K', "ĶǨ")
        add('l', "ĺļľ"); add('L', "ĹĻĽ")
        add('n', "ñńņňǹ"); add('N', "ÑŃŅŇ")
        add('o', "òóôõöōŏőǒȍȏơọỏốồổỗộớờởỡợ"); add('O', "ÒÓÔÕÖŌŎŐǑƠ")
        add('r', "ŕŗřȑȓ"); add('R', "ŔŖŘ")
        add('s', "śŝşšș"); add('S', "ŚŜŞŠȘ")
        add('t', "ţťț"); add('T', "ŢŤȚ")
        add('u', "ùúûüũūŭůűųǔǖǘǚǜưụủứừửữự"); add('U', "ÙÚÛÜŨŪŬŮŰŲǓƯ")
        add('w', "ŵ"); add('W', "Ŵ")
        add('y', "ýÿŷỳỵỷỹ"); add('Y', "ÝŸŶ")
        add('z', "źżž"); add('Z', "ŹŻŽ")
    }

    fun foldAccents(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c in '̀'..'ͯ') continue // marcas combinantes sueltas
            append(map[c] ?: c)
        }
    }
}
