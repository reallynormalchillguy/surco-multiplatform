package dev.surco.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ALBUM_COLS =
    "SELECT albumKey, album, groupArtist AS artist, MAX(year) AS year, MAX(artPath) AS artPath, " +
        "COUNT(*) AS trackCount, MAX(addedAt) AS addedAt FROM tracks"

private const val ARTIST_COLS =
    "SELECT t.groupArtist AS name, COUNT(*) AS trackCount, COUNT(DISTINCT t.albumKey) AS albumCount, " +
        "MAX(t.artPath) AS artPath, MAX(ai.path) AS imagePath " +
        "FROM tracks t LEFT JOIN artist_images ai ON ai.name = t.groupArtist"

private const val TRACK_ORDER = "ORDER BY discNumber, trackNumber, fileName COLLATE NOCASE"

@Dao
interface LibraryDao {

    // ---------- Canciones ----------
    @Query("SELECT * FROM tracks")
    suspend fun allTracksOnce(): List<Track>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun tracks(): Flow<List<Track>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun trackCount(): Flow<Int>

    @Upsert
    suspend fun upsertTracks(tracks: List<Track>)

    @Query("DELETE FROM tracks WHERE uri IN (:uris)")
    suspend fun deleteTracks(uris: List<String>)

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun track(uri: String): Track?

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    fun trackFlow(uri: String): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE uri IN (:uris)")
    suspend fun tracksByUris(uris: List<String>): List<Track>

    @Query("SELECT * FROM tracks WHERE albumKey = :key $TRACK_ORDER")
    fun albumTracks(key: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE albumKey = :key $TRACK_ORDER")
    suspend fun albumTracksOnce(key: String): List<Track>

    @Query("SELECT * FROM tracks WHERE groupArtist = :name OR artist = :name ORDER BY year DESC, album COLLATE NOCASE, discNumber, trackNumber")
    fun artistTracks(name: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY groupArtist COLLATE NOCASE, album COLLATE NOCASE, discNumber, trackNumber")
    fun genreTracks(genre: String): Flow<List<Track>>

    @Query("UPDATE tracks SET artPath = :path WHERE albumKey = :key")
    suspend fun updateAlbumArt(key: String, path: String?)

    // ---------- Álbumes ----------
    @Query("$ALBUM_COLS GROUP BY albumKey ORDER BY album COLLATE NOCASE")
    fun albums(): Flow<List<AlbumRow>>

    @Query("$ALBUM_COLS WHERE albumKey = :key GROUP BY albumKey")
    fun album(key: String): Flow<AlbumRow?>

    @Query("$ALBUM_COLS WHERE albumKey IN (:keys) GROUP BY albumKey")
    suspend fun albumsByKeys(keys: List<String>): List<AlbumRow>

    @Query("$ALBUM_COLS WHERE groupArtist = :name GROUP BY albumKey ORDER BY MAX(year) DESC")
    fun albumsByArtist(name: String): Flow<List<AlbumRow>>

    @Query("$ALBUM_COLS WHERE groupArtist IN (:names) GROUP BY albumKey")
    suspend fun albumsByArtists(names: List<String>): List<AlbumRow>

    @Query("$ALBUM_COLS GROUP BY albumKey HAVING MAX(artPath) IS NULL")
    suspend fun albumsMissingArt(): List<AlbumRow>

    @Query("$ALBUM_COLS WHERE albumKey IN (SELECT t2.albumKey FROM tracks t2 JOIN liked l ON l.trackUri = t2.uri) GROUP BY albumKey")
    fun albumsWithLiked(): Flow<List<AlbumRow>>

    // ---------- Artistas ----------
    @Query("$ARTIST_COLS GROUP BY t.groupArtist ORDER BY t.groupArtist COLLATE NOCASE")
    fun artists(): Flow<List<ArtistRow>>

    @Query("$ARTIST_COLS WHERE t.groupArtist = :name GROUP BY t.groupArtist")
    fun artist(name: String): Flow<ArtistRow?>

    @Query("SELECT DISTINCT groupArtist FROM tracks WHERE groupArtist NOT IN (SELECT name FROM artist_images)")
    suspend fun artistsWithoutImageCheck(): List<String>

    @Query("SELECT DISTINCT groupArtist FROM tracks")
    suspend fun artistNames(): List<String>

    @Upsert
    suspend fun upsertArtistImage(img: ArtistImage)

    @Query("SELECT * FROM artist_images WHERE name = :name")
    suspend fun artistImage(name: String): ArtistImage?

    @Query("DELETE FROM artist_images WHERE name = :name")
    suspend fun deleteArtistImage(name: String)

    // ---------- Géneros ----------
    @Query("SELECT genre, COUNT(*) AS trackCount, MAX(artPath) AS artPath FROM tracks WHERE genre != '' GROUP BY genre ORDER BY trackCount DESC")
    fun genres(): Flow<List<GenreRow>>

    // ---------- Búsqueda ----------
    @Query("SELECT * FROM tracks WHERE title LIKE :q OR artist LIKE :q OR album LIKE :q ORDER BY title COLLATE NOCASE LIMIT 60")
    suspend fun searchTracks(q: String): List<Track>

    @Query("$ALBUM_COLS WHERE album LIKE :q OR groupArtist LIKE :q GROUP BY albumKey ORDER BY album COLLATE NOCASE LIMIT 30")
    suspend fun searchAlbums(q: String): List<AlbumRow>

    @Query("$ARTIST_COLS WHERE t.groupArtist LIKE :q GROUP BY t.groupArtist ORDER BY t.groupArtist COLLATE NOCASE LIMIT 20")
    suspend fun searchArtists(q: String): List<ArtistRow>

    @Upsert
    suspend fun upsertSearchRecent(r: SearchRecent)

    @Query("DELETE FROM search_recents WHERE `key` = :key")
    suspend fun deleteSearchRecent(key: String)

    @Query("SELECT * FROM search_recents ORDER BY at DESC LIMIT 25")
    fun searchRecents(): Flow<List<SearchRecent>>

    // ---------- Historial ----------
    @Insert
    suspend fun insertPlay(e: PlayEvent)

    @Query("SELECT albumKey AS `key`, COUNT(*) AS plays, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY albumKey")
    fun albumPlayStats(): Flow<List<PlayStat>>

    @Query("SELECT artist AS `key`, COUNT(*) AS plays, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY artist ORDER BY plays DESC")
    fun artistPlayStats(): Flow<List<PlayStat>>

    @Query("SELECT trackUri AS `key`, COUNT(*) AS plays, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY trackUri ORDER BY plays DESC LIMIT :limit")
    fun topTracks(limit: Int): Flow<List<PlayStat>>

    @Query("SELECT COUNT(*) FROM play_events")
    fun playCount(): Flow<Int>

    // ---------- Me gusta ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(l: Liked)

    @Query("DELETE FROM liked WHERE trackUri = :uri")
    suspend fun unlike(uri: String)

    @Query("SELECT trackUri FROM liked")
    fun likedUris(): Flow<List<String>>

    @Query("SELECT t.* FROM tracks t JOIN liked l ON l.trackUri = t.uri ORDER BY l.likedAt DESC")
    fun likedTracks(): Flow<List<Track>>

    @Query("SELECT MAX(likedAt) FROM liked")
    fun lastLikedAt(): Flow<Long?>

    // ---------- Listas ----------
    @Insert
    suspend fun insertPlaylist(p: Playlist): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Query("SELECT p.id, p.name, p.updatedAt, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS trackCount FROM playlists p ORDER BY p.updatedAt DESC")
    fun playlists(): Flow<List<PlaylistRow>>

    @Query("SELECT p.id, p.name, p.updatedAt, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS trackCount FROM playlists p WHERE p.id = :id")
    fun playlist(id: Long): Flow<PlaylistRow?>

    @Query("SELECT t.* FROM playlist_entries e JOIN tracks t ON t.uri = e.trackUri WHERE e.playlistId = :id ORDER BY e.position")
    fun playlistTracks(id: Long): Flow<List<Track>>

    @Query("SELECT DISTINCT t.artPath FROM playlist_entries e JOIN tracks t ON t.uri = e.trackUri WHERE e.playlistId = :id AND t.artPath IS NOT NULL ORDER BY e.position LIMIT 4")
    fun playlistArt(id: Long): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertEntry(e: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id AND trackUri = :uri")
    suspend fun removeEntry(id: Long, uri: String)

    /** Quita canciones borradas del dispositivo de todas las listas, de «Me gusta» y de la caché de letras. */
    @Query("DELETE FROM playlist_entries WHERE trackUri IN (:uris)")
    suspend fun removeEntriesForTracks(uris: List<String>)

    @Query("DELETE FROM liked WHERE trackUri IN (:uris)")
    suspend fun unlikeAll(uris: List<String>)

    @Query("DELETE FROM lyrics WHERE trackUri IN (:uris)")
    suspend fun deleteLyrics(uris: List<String>)

    // ---------- Scrobbles ----------
    @Insert
    suspend fun insertScrobble(s: PendingScrobble)

    @Query("SELECT * FROM scrobbles ORDER BY timestamp LIMIT 50")
    suspend fun pendingScrobbles(): List<PendingScrobble>

    @Query("DELETE FROM scrobbles WHERE id IN (:ids)")
    suspend fun deleteScrobbles(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM scrobbles")
    fun pendingScrobbleCount(): Flow<Int>

    /** Momento (segundos UNIX) del scrobble pendiente más antiguo, o null si no hay. */
    @Query("SELECT MIN(timestamp) FROM scrobbles")
    fun oldestPendingScrobble(): Flow<Long?>
}
