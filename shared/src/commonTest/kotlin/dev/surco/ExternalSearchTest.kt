package dev.surco

import dev.surco.ui.discogsSearch
import dev.surco.ui.rymSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalSearchTest {
    @Test
    fun discogsAndRymLinks() {
        assertEquals(
            "https://www.discogs.com/search/?q=Rosal%C3%ADa+El+Mal+Querer&type=all",
            discogsSearch("Rosalía", "El Mal Querer (Deluxe Edition)"),
        )
        assertEquals(
            "https://rateyourmusic.com/search?searchterm=Radiohead+OK+Computer&searchtype=l",
            rymSearch("Radiohead", "OK Computer [Remastered]"),
        )
        assertEquals("https://rateyourmusic.com/search?searchterm=AC%2FDC+Back+in+Black&searchtype=l", rymSearch("AC/DC", "Back in Black"))
    }
}
