package dev.surco.net

import dev.surco.data.PendingScrobble
import dev.surco.data.Prefs
import dev.surco.data.Track

/** La cuenta de Last.fm guardada en las preferencias de Android. */
class PrefsLastFmAccount(private val prefs: Prefs) : LastFmAccount {
    override val apiKey get() = prefs.current.lastfmKey
    override val secret get() = prefs.current.lastfmSecret
    override val session get() = prefs.current.lastfmSession
    override fun saveSession(key: String, user: String) =
        prefs.update { it.copy(lastfmSession = key, lastfmUser = user) }
}

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

suspend fun LastFm.nowPlaying(t: Track) = nowPlaying(t.toScrobbleItem())

suspend fun LastFm.scrobble(list: List<PendingScrobble>) = scrobble(list.map { it.toScrobbleItem() })
