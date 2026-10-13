package dev.surco.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Base de datos de la biblioteca, común a Android e iOS (Room multiplataforma). */
@Database(
    entities = [
        Track::class, PlayEvent::class, Playlist::class, PlaylistEntry::class, Liked::class,
        SearchRecent::class, PendingScrobble::class, ArtistImage::class, LyricsEntry::class,
    ],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(SurcoDatabaseConstructor::class)
abstract class SurcoDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
    abstract fun lyrics(): LyricsDao

    companion object {
        const val FILE_NAME = "surco.db"
    }
}

// Room genera las implementaciones `actual` para cada plataforma.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object SurcoDatabaseConstructor : RoomDatabaseConstructor<SurcoDatabase> {
    override fun initialize(): SurcoDatabase
}
