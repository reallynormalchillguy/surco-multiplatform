package dev.surco.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.surco.data.Track
import java.io.File

const val EXTRA_SOURCE = "surco.source"

/** Crea el MediaItem de una canción. [source] describe desde dónde se reproduce, p. ej. "álbum|Sol.Hz". */
fun Track.toMediaItem(source: String): MediaItem {
    val extras = Bundle().apply { putString(EXTRA_SOURCE, source) }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(albumArtist.ifBlank { null })
        .setTrackNumber(trackNumber.takeIf { it > 0 })
        .setDiscNumber(discNumber.takeIf { it > 0 })
        .setGenre(genre.ifBlank { null })
        .setArtworkUri(artPath?.let { Uri.fromFile(File(it)) })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setMediaMetadata(meta)
        .build()
}

fun MediaItem.source(): String = mediaMetadata.extras?.getString(EXTRA_SOURCE) ?: ""
