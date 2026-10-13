package dev.surco

import dev.surco.net.LrcLib
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {
    @Test
    fun titleCleanup() {
        assertEquals("Song", LrcLib.cleanTitle("Song (feat. Someone)"))
        assertEquals("Song", LrcLib.cleanTitle("Song - 2011 Remastered"))
    }
}
