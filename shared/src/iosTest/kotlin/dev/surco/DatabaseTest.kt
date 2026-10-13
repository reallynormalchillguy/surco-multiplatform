package dev.surco

import dev.surco.data.Liked
import dev.surco.data.LyricsEntry
import dev.surco.data.PendingScrobble
import dev.surco.data.PlayEvent
import dev.surco.data.SurcoDatabase
import dev.surco.data.Track
import dev.surco.data.formatDuration
import dev.surco.data.inMemory
import dev.surco.data.qualityLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La misma base de datos de Room que en Android, funcionando en iOS con SQLite incluido. */
class DatabaseTest {

    private fun track(n: Int, album: String, artist: String = "Radiohead") = Track(
        uri = "file:///music/$n.flac", folderId = "root", fileName = "$n.flac", mime = "audio/flac",
        title = "Song $n", artist = artist, album = album, albumArtist = artist, groupArtist = artist,
        albumKey = "$artist|$album", trackNumber = n, discNumber = 1, year = 2000 + n, genre = "Rock",
        durationMs = 200_000L + n, bitrate = 900_000, sampleRate = 44100, bitsPerSample = 16,
        size = 1000, lastModified = 0, addedAt = n.toLong(), artPath = null,
    )

    @Test
    fun libraryQueries() = runTest {
        val db = SurcoDatabase.inMemory()
        val dao = db.dao()
        dao.upsertTracks(listOf(track(1, "Kid A"), track(2, "Kid A"), track(3, "Amnesiac"), track(4, "Homogenic", "Björk")))
        assertEquals(4, dao.trackCount().first())
        assertEquals(listOf("Amnesiac", "Homogenic", "Kid A"), dao.albums().first().map { it.album })
        assertEquals(2, dao.albumTracksOnce("Radiohead|Kid A").size)
        val rh = dao.artist("Radiohead").first()!!
        assertEquals(3, rh.trackCount)
        assertEquals(2, rh.albumCount)
        assertEquals(listOf("Song 4"), dao.searchTracks("%bjö%").map { it.title })

        dao.like(Liked("file:///music/2.flac", 10))
        assertEquals(listOf("Song 2"), dao.likedTracks().first().map { it.title })

        dao.insertPlay(PlayEvent(trackUri = "file:///music/1.flac", albumKey = "Radiohead|Kid A", artist = "Radiohead", playedAt = 5))
        assertEquals(1, dao.playCount().first())

        val id = dao.insertPlaylist(dev.surco.data.Playlist(name = "Mix", createdAt = 1, updatedAt = 1))
        dao.insertEntry(dev.surco.data.PlaylistEntry(playlistId = id, trackUri = "file:///music/3.flac", position = 0, addedAt = 1))
        assertEquals(1, dao.playlist(id).first()!!.trackCount)

        dao.deleteTracks(listOf("file:///music/4.flac"))
        assertEquals(3, dao.allTracksOnce().size)
        db.close()
    }

    @Test
    fun scrobbleQueueKeepsOriginalTimes() = runTest {
        val db = SurcoDatabase.inMemory()
        val dao = db.dao()
        listOf(300L, 100L, 200L).forEach {
            dao.insertScrobble(PendingScrobble(artist = "A", track = "T$it", album = "", albumArtist = "", trackNumber = 0, timestamp = it, durationSec = 180))
        }
        val pending = dao.pendingScrobbles()
        assertEquals(listOf(100L, 200L, 300L), pending.map { it.timestamp })
        assertEquals(100L, dao.oldestPendingScrobble().first())
        dao.deleteScrobbles(pending.map { it.id })
        assertEquals(0, dao.pendingScrobbleCount().first())
        assertNull(dao.oldestPendingScrobble().first())
        db.close()
    }

    @Test
    fun lyricsCacheAndFormatting() = runTest {
        val db = SurcoDatabase.inMemory()
        db.lyrics().put(LyricsEntry("u", "[00:01.00]hola", null, "lrclib", 1))
        assertEquals("lrclib", db.lyrics().get("u")!!.source)
        db.lyrics().delete("u")
        assertNull(db.lyrics().get("u"))
        db.close()

        assertEquals("FLAC · 16 bit / 44.1 kHz · 900 kbps", track(1, "X").qualityLabel())
        assertEquals("3:05", 185_000L.formatDuration())
        assertEquals("1:02:03", 3_723_000L.formatDuration())
    }
}
