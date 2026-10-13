package dev.surco.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una canción encontrada en la carpeta de música. `uri` es el URI SAF del documento. */
@Entity(
    tableName = "tracks",
    indices = [Index("albumKey"), Index("groupArtist"), Index("genre")]
)
data class Track(
    @PrimaryKey val uri: String,
    val folderId: String,
    val fileName: String,
    val mime: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    /** Artista con el que se agrupa (artista del álbum o, si falta, artista de la pista). */
    val groupArtist: String,
    val albumKey: String,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val durationMs: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val size: Long,
    val lastModified: Long,
    val addedAt: Long,
    val artPath: String?,
)

/** Cada vez que empieza a sonar una canción. Alimenta "Recientes", "Sumérgete de nuevo", favoritos… */
@Entity(tableName = "play_events", indices = [Index("albumKey"), Index("trackUri")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackUri: String,
    val albumKey: String,
    val artist: String,
    val playedAt: Long,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "playlist_entries", indices = [Index("playlistId")])
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackUri: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "liked")
data class Liked(
    @PrimaryKey val trackUri: String,
    val likedAt: Long,
)

/** Elementos recientes de la pantalla de búsqueda. key = "tipo:id". */
@Entity(tableName = "search_recents")
data class SearchRecent(
    @PrimaryKey val key: String,
    val type: String,
    val ref: String,
    val title: String,
    val subtitle: String,
    val artPath: String?,
    val at: Long,
)

/** Scrobbles pendientes de enviar a Last.fm (cola offline). */
@Entity(tableName = "scrobbles")
data class PendingScrobble(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: Int,
    val timestamp: Long,
    val durationSec: Long,
)

@Entity(tableName = "artist_images")
data class ArtistImage(
    @PrimaryKey val name: String,
    val path: String?,
    val checkedAt: Long,
)

// ---- Resultados de consultas ----

data class AlbumRow(
    val albumKey: String,
    val album: String,
    val artist: String,
    val year: Int,
    val artPath: String?,
    val trackCount: Int,
    val addedAt: Long,
)

data class ArtistRow(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artPath: String?,
    val imagePath: String?,
)

data class PlaylistRow(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val trackCount: Int,
)

data class PlayStat(
    val key: String,
    val plays: Int,
    val lastPlayed: Long,
)

data class GenreRow(
    val genre: String,
    val trackCount: Int,
    val artPath: String?,
)

const val UNKNOWN_ARTIST = "Artista desconocido"
const val UNKNOWN_ALBUM = "Álbum desconocido"

fun Track.qualityLabel(): String {
    val ext = fileName.substringAfterLast('.', "").uppercase()
    val parts = mutableListOf<String>()
    if (ext.isNotEmpty()) parts += ext
    if (bitsPerSample > 0 && sampleRate > 0) {
        parts += "$bitsPerSample bit / ${formatKhz(sampleRate)} kHz"
    } else if (sampleRate > 0) {
        parts += "${formatKhz(sampleRate)} kHz"
    }
    if (bitrate > 0) parts += "${bitrate / 1000} kbps"
    return parts.joinToString(" · ")
}

private fun formatKhz(hz: Int): String {
    val k = hz / 1000.0
    return if (k % 1.0 == 0.0) k.toInt().toString() else String.format(java.util.Locale.US, "%.1f", k)
}

fun Long.formatDuration(): String {
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}
