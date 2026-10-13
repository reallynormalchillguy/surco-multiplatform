package dev.surco.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Letra guardada en caché. `source`: "lrc", "tag", "lrclib", "manual" o "none" (no encontrada). */
@Entity(tableName = "lyrics")
data class LyricsEntry(
    @PrimaryKey val trackUri: String,
    val synced: String?,
    val plain: String?,
    val source: String,
    val fetchedAt: Long,
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackUri = :uri")
    suspend fun get(uri: String): LyricsEntry?

    @Upsert
    suspend fun put(e: LyricsEntry)

    @Query("DELETE FROM lyrics WHERE trackUri = :uri")
    suspend fun delete(uri: String)
}
