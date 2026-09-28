package com.desideri.familybalance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [Conto::class, ContoValuta::class, Voce::class, Operazione::class, Associazione::class, Cambio::class], version = 5, exportSchema = false)
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
                .addMigrations(MIGRAZIONE_1_2, MIGRAZIONE_2_3, MIGRAZIONE_3_4, MIGRAZIONE_4_5)
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

        /** Versione 4: ordine delle operazioni nello stesso giorno (righe dell'estratto conto). */
        private val MIGRAZIONE_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `operazioni` ADD COLUMN `ordine` INTEGER")
            }
        }

        /** Versione 5: cambi CHF/EUR mensili inseriti a mano. */
        private val MIGRAZIONE_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cambi` (`mese` TEXT NOT NULL, `chfEur` REAL NOT NULL, PRIMARY KEY(`mese`))")
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
