package dev.surco.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: caché de letras. Migración explícita para no perder favoritas, listas ni historial. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lyrics` (`trackUri` TEXT NOT NULL, `synced` TEXT, `plain` TEXT, " +
                "`source` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`trackUri`))"
        )
    }
}

/** Abre la base de datos en Android (mismo archivo y migraciones que antes de ser multiplataforma). */
fun SurcoDatabase.Companion.create(context: Context): SurcoDatabase =
    Room.databaseBuilder(context, SurcoDatabase::class.java, FILE_NAME)
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
