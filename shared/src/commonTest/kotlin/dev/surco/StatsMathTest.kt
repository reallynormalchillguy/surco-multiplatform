package dev.surco

import dev.surco.data.Scrobble
import dev.surco.data.StatsMath
import dev.surco.data.TopItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsMathTest {
    private val madrid: (Long) -> Int = { 7200 }
    private val utc: (Long) -> Int = { 0 }

    /** 2026-10-17 00:00:00 UTC (sábado). */
    private val day0 = 1_792_195_200L

    @Test
    fun datesAndWeekdays() {
        assertEquals(Triple(1970, 1, 1), StatsMath.civil(0))
        assertEquals(Triple(2026, 10, 17), StatsMath.civil(day0 / 86_400))
        assertEquals(Triple(2024, 2, 29), StatsMath.civil(19_782))
        assertEquals(5, StatsMath.weekday(day0 / 86_400)) // sábado
        assertEquals(3, StatsMath.weekday(0)) // 1 de enero de 1970: jueves
    }

    @Test
    fun hoursUseLocalTime() {
        // 22:30 UTC = 00:30 en Madrid (horario de verano), al día siguiente.
        val s = listOf(Scrobble(day0 + 22 * 3600 + 1800, "A", "x", ""))
        assertEquals(1, StatsMath.hours(s, utc)[22])
        assertEquals(1, StatsMath.hours(s, madrid)[0])
        assertEquals(day0 / 86_400 + 1, StatsMath.localDay(s[0].at, madrid))
        assertEquals(1, StatsMath.week(s, madrid)[6][0]) // domingo a las 00
    }

    @Test
    fun streaksCountConsecutiveDaysUpToTodayOrYesterday() {
        val today = 100L
        assertEquals(3 to 4, StatsMath.streaks(setOf(90, 91, 92, 93, 97, 98, 99), today))
        assertEquals(0 to 2, StatsMath.streaks(setOf(50, 51), today))
        assertEquals(1 to 1, StatsMath.streaks(setOf(100), today))
    }

    @Test
    fun activityBucketsByPeriodLength() {
        val per = mapOf(10L to 3, 11L to 4, 40L to 1)
        val (daily, d) = StatsMath.activity(per, 10, 39)
        assertEquals(1, d)
        assertEquals(30, daily.size)
        assertEquals(7, daily.take(2).sumOf { it.count })
        val (weekly, w) = StatsMath.activity(per, 0, 200)
        assertEquals(7, w)
        assertEquals(8, weekly.sumOf { it.count })
        assertTrue(weekly.all { StatsMath.weekday(it.startDay) == 0 }) // empiezan en lunes
        val (monthly, m) = StatsMath.activity(per, 0, 800)
        assertEquals(30, m)
        assertEquals(8, monthly.sumOf { it.count })
        assertEquals(Triple(1970, 1, 1), StatsMath.civil(monthly.first().startDay))
    }

    @Test
    fun obsessionAndMarathon() {
        val s = (0 until 6).map { Scrobble(day0 + it * 200L, "Kestrel", "Ceniza", "") } +
            Scrobble(day0 + 1900, "Kestrel", "Faro", "") + Scrobble(day0 + 50_000, "Otro", "Lejos", "")
        val o = StatsMath.obsession(s, utc)
        assertNotNull(o)
        assertEquals(6, o!!.count)
        assertEquals("Ceniza", o.title)
        val m = StatsMath.marathon(s)
        assertNotNull(m)
        assertEquals(7, m!!.tracks)
        assertNull(StatsMath.marathon(s.take(3)))
    }

    @Test
    fun genresAreWeightedByPlaysAndSkipNonGenres() {
        val artists = listOf(TopItem("A", "A", 300, null, ""), TopItem("B", "B", 100, null, ""))
        val tags = mapOf(
            "A" to listOf("seen live" to 100, "flamenco" to 100, "Spanish" to 80),
            "B" to listOf("indie pop" to 100, "indie-pop" to 50, "B" to 100),
        )
        val g = StatsMath.genres(artists, tags)
        assertEquals("Flamenco", g.first().name)
        assertEquals(0.75f, g.first().share, 0.01f)
        assertTrue(g.none { it.name.equals("seen live", true) || it.name.equals("spanish", true) })
        assertEquals(2, g.size) // «indie pop» e «indie-pop» son el mismo
    }

    @Test
    fun chronotypeAndDayParts() {
        assertEquals(dev.surco.data.Chronotype.NIGHT_OWL, StatsMath.chronotype(23))
        assertEquals(dev.surco.data.Chronotype.NIGHT_OWL, StatsMath.chronotype(2))
        assertEquals(dev.surco.data.Chronotype.EARLY_BIRD, StatsMath.chronotype(7))
        val parts = StatsMath.dayParts(List(24) { if (it >= 18) 10 else 0 })
        assertEquals(1f, parts[3], 0.001f)
    }

    @Test
    fun libraryMatchIgnoresCaseAndAccents() {
        val lib = setOf(StatsMath.key("Carmen Duna", "Kilómetro cero"))
        val s = listOf(Scrobble(1, "carmen duna", "Kilometro Cero", ""), Scrobble(2, "X", "Y", ""))
        assertEquals(0.5f, StatsMath.inLibrary(s, lib)!!, 0.001f)
    }
}
