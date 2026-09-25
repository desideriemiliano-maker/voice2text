package com.desideri.familybalance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [Conto::class, ContoValuta::class, Voce::class, Operazione::class, Associazione::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SpeseDao

    companion object {
        const val NOME_FILE = "familybalance.db"

        @Volatile
        private var istanza: AppDatabase? = null

        fun get(context: Context): AppDatabase = istanza ?: synchronized(this) {
            istanza ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOME_FILE)
                // TRUNCATE invece di WAL: tutto il contenuto sta nel solo file .db, così il backup
                // su Drive è una semplice copia del file senza dover gestire -wal/-shm.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRAZIONE_1_2, MIGRAZIONE_2_3)
                .build()
                .also { istanza = it }
        }

        /** Versione 2: anagrafica associazioni per l'import degli estratti conto. */
        private val MIGRAZIONE_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `associazioni` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`chiave` TEXT NOT NULL, `tipo` TEXT NOT NULL, `sottotipo` TEXT)"
                )
            }
        }

        /** Versione 3: colore delle voci di spesa. */
        private val MIGRAZIONE_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `voci` ADD COLUMN `colore` INTEGER")
            }
        }

        fun fileDatabase(context: Context): File = context.getDatabasePath(NOME_FILE)

        /** Chiude il database (prima di sovrascriverne il file con un ripristino). */
        fun chiudi() = synchronized(this) {
            istanza?.close()
            istanza = null
        }
    }
}
