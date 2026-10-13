package dev.surco.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Track::class, PlayEvent::class, Playlist::class, PlaylistEntry::class, Liked::class,
        SearchRecent::class, PendingScrobble::class, ArtistImage::class, LyricsEntry::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SurcoDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
    abstract fun lyrics(): LyricsDao

    companion object {
        /** v2: caché de letras. Migración explícita para no perder favoritas, listas ni historial. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` (`trackUri` TEXT NOT NULL, `synced` TEXT, `plain` TEXT, " +
                        "`source` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`trackUri`))"
                )
            }
        }

        fun create(context: Context): SurcoDatabase =
            Room.databaseBuilder(context, SurcoDatabase::class.java, "surco.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
