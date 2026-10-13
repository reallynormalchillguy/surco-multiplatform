package dev.surco.net

import dev.surco.data.PendingScrobble
import dev.surco.data.Track

fun Track.toScrobbleItem(timestamp: Long = 0) = ScrobbleItem(
    artist = artist,
    track = title,
    album = album,
    albumArtist = albumArtist,
    durationSec = (durationMs / 1000).toInt(),
    trackNumber = trackNumber,
    timestamp = timestamp,
)

fun PendingScrobble.toScrobbleItem() = ScrobbleItem(
    artist = artist,
    track = track,
    album = album,
    albumArtist = albumArtist,
    durationSec = durationSec.toInt(),
    trackNumber = trackNumber,
    timestamp = timestamp,
)

/** La cuenta de Last.fm guardada en los ajustes de la app. */
class PrefsLastFmAccount(private val prefs: dev.surco.data.Prefs) : LastFmAccount {
    override val apiKey get() = prefs.current.lastfmKey
    override val secret get() = prefs.current.lastfmSecret
    override val session get() = prefs.current.lastfmSession
    override fun saveSession(key: String, user: String) =
        prefs.update { it.copy(lastfmSession = key, lastfmUser = user) }
}

suspend fun LastFm.nowPlaying(t: Track) = nowPlaying(t.toScrobbleItem())
