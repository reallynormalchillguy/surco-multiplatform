package dev.surco.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private fun RoomDatabase.Builder<SurcoDatabase>.configure(): SurcoDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()

/** Abre (o crea) la base de datos en la carpeta Documentos de la app. */
@OptIn(ExperimentalForeignApi::class)
fun SurcoDatabase.Companion.create(): SurcoDatabase {
    val docs = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(docs?.path) { "Sin carpeta Documentos" } + "/" + FILE_NAME
    return Room.databaseBuilder<SurcoDatabase>(name = path).configure()
}

/** Base de datos en memoria (pruebas). */
fun SurcoDatabase.Companion.inMemory(): SurcoDatabase =
    Room.inMemoryDatabaseBuilder<SurcoDatabase>().configure()
