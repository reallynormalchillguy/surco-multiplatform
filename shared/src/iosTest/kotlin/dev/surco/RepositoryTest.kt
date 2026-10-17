package dev.surco

import dev.surco.app.PlatformParts
import dev.surco.app.SurcoCore
import dev.surco.data.AppFiles
import dev.surco.data.DeleteResult
import dev.surco.data.ArtStore
import dev.surco.data.LibraryScanner
import dev.surco.data.MemoryStore
import dev.surco.data.ScanState
import dev.surco.data.SurcoDatabase
import dev.surco.data.Track
import dev.surco.data.inMemory
import dev.surco.playback.PlayerController
import dev.surco.slsk.SoulseekService
import dev.surco.tags.TagEdits
import dev.surco.tags.TagEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La lógica común (Repository, letras…) funcionando en iOS con piezas de prueba. */
class RepositoryTest {
    private class FakeTags : TagEditor {
        val writes = mutableListOf<Pair<String, TagEdits>>()
        override suspend fun write(uri: String, fileName: String, edits: TagEdits) { writes += uri to edits }
        override suspend fun readLyrics(uri: String, fileName: String): String? = null
    }

    private class FakeArt : ArtStore {
        override fun toJpeg(bytes: ByteArray, max: Int) = bytes.takeIf { it.isNotEmpty() }
        override fun writeAlbumArt(albumKey: String, jpeg: ByteArray) = "/art/$albumKey"
        override fun writeArtistImage(name: String, jpeg: ByteArray) = "/artists/$name"
        override fun deleteArtistImage(path: String?) {}
        override fun delete(path: String?) {}
        override fun exists(path: String?) = path != null
    }

    private class FakeScanner : LibraryScanner {
        val deletedFiles = mutableListOf<String>()
        override suspend fun deleteTracks(root: String, tracks: List<Track>): DeleteResult {
            // La pista 3 está «protegida»: simula un archivo que no se deja borrar.
            val (ok, ko) = tracks.partition { !it.fileName.startsWith("03") }
            deletedFiles += ok.map { it.uri }
            return DeleteResult(ok.map { it.uri }, ko.map { it.fileName }, 1)
        }
        override val state = MutableStateFlow<ScanState>(ScanState.Idle)
        override suspend fun scan(root: String): ScanState = ScanState.Done(0, 0, 0)
        override suspend fun readSibling(root: String, track: Track, fileName: String): ByteArray? =
            if (fileName == "01.lrc") "[00:01.00]primera\n[00:05.00]segunda".encodeToByteArray() else null
    }

    private class MemFiles : AppFiles {
        val map = HashMap<String, String>()
        override fun readText(name: String) = map[name]
        override fun writeText(name: String, text: String) { map[name] = text }
    }

    private fun track(n: Int) = Track(
        uri = "file:///m/0$n.flac", folderId = "root", fileName = "0$n.flac", mime = "audio/flac",
        title = "T$n", artist = "A", album = "Al", albumArtist = "A", groupArtist = "A",
        albumKey = "aa:a|al", trackNumber = n, discNumber = 1, year = 2001, genre = "",
        durationMs = 100_000, bitrate = 0, sampleRate = 0, bitsPerSample = 0,
        size = 10, lastModified = 0, addedAt = 0, artPath = null,
    )

    private val tags = FakeTags()
    private val core = object : SurcoCore(
        PlatformParts(
            db = SurcoDatabase.inMemory(), store = MemoryStore(), files = MemFiles(), art = FakeArt(),
            tags = tags, scanner = { _, _ -> FakeScanner() }, userAgent = "test",
        )
    ) {
        override val player: PlayerController get() = error("sin reproductor")
        override val preview: dev.surco.playback.PreviewPlayer get() = error("sin vista previa")
        override val soulseek: SoulseekService get() = error("sin soulseek")
    }

    @Test
    fun editsPlaylistsAndLyrics() = runTest {
        val repo = core.repository
        repo.dao.upsertTracks(listOf(track(1), track(2)))

        val edited = repo.editTrack(track(1), TagEdits(album = "Nuevo", trackNumber = "7/10", year = "1999-05-01"))
        assertEquals("aa:a|nuevo", edited.albumKey)
        assertEquals(7, edited.trackNumber)
        assertEquals(1999, edited.year)
        assertEquals("file:///m/01.flac", tags.writes.single().first)

        val errors = repo.setAlbumCover("aa:a|al", byteArrayOf(1, 2, 3), embed = true)
        assertTrue(errors.isEmpty())
        assertEquals("/art/aa:a|al", repo.dao.track("file:///m/02.flac")!!.artPath)

        val id = repo.createPlaylist("  ")
        repo.addToPlaylist(id, listOf("file:///m/01.flac", "file:///m/02.flac"))
        assertEquals(2, repo.dao.playlist(id).first()!!.trackCount)
        assertEquals(listOf("T2"), repo.search("t2").tracks.map { it.title })

        // Sin sesión de Last.fm la cola se queda guardada, con su hora original.
        repo.enqueueScrobble(track(2), 1_700_000_000)
        assertEquals(1_700_000_000L, repo.dao.pendingScrobbles().single().timestamp)

        core.prefs.update { it.copy(treeUri = "root") }
        val lyrics = core.lyrics.load(track(1))
        assertTrue(lyrics is dev.surco.data.Lyrics.Synced, "letra del .lrc de al lado: $lyrics")
    }

    @Test
    fun deletingAnAlbumRemovesItsSongsEverywhere() = runTest {
        val repo = core.repository
        repo.dao.upsertTracks(listOf(track(1), track(2), track(3)))
        repo.setLiked(track(1).uri, true)
        val pl = repo.createPlaylist("x")
        repo.addToPlaylist(pl, listOf(track(1).uri, track(2).uri))
        core.prefs.update { it.copy(treeUri = "root") }

        val r = repo.deleteAlbum("aa:a|al")
        assertEquals(listOf(track(1).uri, track(2).uri), r.deleted)
        assertEquals(listOf("03.flac"), r.failed)
        // Lo que no se pudo borrar sigue en la biblioteca; lo borrado desaparece de listas y «Me gusta».
        assertEquals(listOf(track(3).uri), repo.dao.albumTracksOnce("aa:a|al").map { it.uri })
        assertEquals(0, repo.dao.playlist(pl).first()!!.trackCount)
        assertTrue(repo.dao.likedUris().first().isEmpty())
    }

    @Test
    fun albumInfoFromTheInternetIsWrittenToEachSong() = runTest {
        val repo = core.repository
        val local = listOf(
            track(1).copy(title = "01", album = dev.surco.data.UNKNOWN_ALBUM, albumKey = "f:root|x"),
            track(2).copy(title = "02", album = dev.surco.data.UNKNOWN_ALBUM, albumKey = "f:root|x"),
        )
        repo.dao.upsertTracks(local)
        val info = dev.surco.data.AlbumInfo(
            dev.surco.data.AlbumCandidate("MusicBrainz", "id", "Disco", "A", 2001, 2, null),
            "Disco", "A", 2001, "Rock",
            listOf(dev.surco.data.RemoteTrack(1, 1, "Uno", "A", 100_000), dev.surco.data.RemoteTrack(1, 2, "Dos", "A", 100_000)),
            coverUrl = null,
        )
        val plans = dev.surco.data.AlbumMatcher.plan(local, info, dev.surco.data.AlbumInfoChoice())
        val (key, errors) = repo.applyAlbumInfo("f:root|x", plans, null) { _, _ -> }
        assertTrue(errors.isEmpty())
        assertEquals("aa:a|disco", key)
        assertEquals(listOf("Uno", "Dos"), repo.dao.albumTracksOnce(key).map { it.title })
        assertEquals("Rock", tags.writes.last().second.genre)
    }

    @Test
    fun lyricsNetworkFailureIsNotCachedAsMissing() = runTest {
        var online = false
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { req ->
            if (!online) throw RuntimeException("sin red")
            if (req.url.encodedPath.endsWith("/get")) respond("""{"syncedLyrics":"[00:01.00]hola","plainLyrics":"hola"}""", io.ktor.http.HttpStatusCode.OK)
            else respond("[]", io.ktor.http.HttpStatusCode.OK)
        })
        val lyrics = dev.surco.data.LyricsRepository(core.db.lyrics(), core.prefs, FakeScanner(), FakeTags(), dev.surco.net.LrcLib(http))
        val t = track(5)
        assertEquals(dev.surco.data.Lyrics.Failed, lyrics.load(t))
        assertEquals(null, core.db.lyrics().get(t.uri), "un fallo de red no se guarda como «sin letra»")
        online = true
        val again = lyrics.load(t)
        assertTrue(again is dev.surco.data.Lyrics.Synced, "la siguiente carga, sin forzar, la encuentra: $again")
    }
}
