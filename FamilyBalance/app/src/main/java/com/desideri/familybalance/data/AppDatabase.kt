package com.desideri.familybalance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [Conto::class, ContoValuta::class, Voce::class, Operazione::class, Associazione::class, Cambio::class, PrevisioneRicorrente::class], version = 12, exportSchema = false)
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
                .addMigrations(MIGRAZIONE_1_2, MIGRAZIONE_2_3, MIGRAZIONE_3_4, MIGRAZIONE_4_5, MIGRAZIONE_5_6, MIGRAZIONE_6_7, MIGRAZIONE_7_8, MIGRAZIONE_8_9, MIGRAZIONE_9_10, MIGRAZIONE_10_11, MIGRAZIONE_11_12)
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

        /** Versione 6: voci obsolete e personalizzazioni delle scadenze delle spese ricorrenti. */
        private val MIGRAZIONE_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `voci` ADD COLUMN `obsoleta` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `previsioni_ricorrenti` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`voceId` INTEGER NOT NULL, `mese` TEXT NOT NULL, `importoCent` INTEGER, `data` INTEGER, `spostataA` TEXT, " +
                        "FOREIGN KEY(`voceId`) REFERENCES `voci`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_previsioni_ricorrenti_voceId_mese` ON `previsioni_ricorrenti` (`voceId`, `mese`)"
                )
            }
        }

        /** Versione 7: scadenze ricorrenti annullate. */
        private val MIGRAZIONE_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `previsioni_ricorrenti` ADD COLUMN `annullata` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 8: operazioni escluse dalla media delle spese ricorrenti. */
        private val MIGRAZIONE_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `operazioni` ADD COLUMN `esclusaDaMedia` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 9: data a cui imputare un'operazione per la spesa ricorrente. */
        private val MIGRAZIONE_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `operazioni` ADD COLUMN `dataRicorrente` INTEGER")
            }
        }

        /** Versione 10: scadenze ricorrenti aggiunte a mano fuori dalla ricorrenza. */
        private val MIGRAZIONE_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `previsioni_ricorrenti` ADD COLUMN `aggiunta` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 11: operazioni importate non ancora contabilizzate. */
        private val MIGRAZIONE_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `operazioni` ADD COLUMN `nonContabilizzata` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Versione 12: niente più sottotipo, né nelle voci né nelle associazioni. Il sottotipo va
         * nelle note delle operazioni (dopo " / " se c'è già una nota) e le operazioni passano alla
         * voce di solo tipo (la prima voce del tipo lo diventa, se manca); le voci con lo stesso tipo
         * (anche con maiuscole diverse) si uniscono nella prima.
         */
        internal val MIGRAZIONE_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val conSottotipo = "SELECT id FROM voci WHERE sottotipo IS NOT NULL"
                db.execSQL("UPDATE voci SET sottotipo = NULL WHERE TRIM(sottotipo) = ''")
                val sottotipoOp = "(SELECT TRIM(sottotipo) FROM voci WHERE id = operazioni.voceId)"
                db.execSQL(
                    "UPDATE operazioni SET note = CASE WHEN note IS NULL OR TRIM(note) = '' THEN $sottotipoOp " +
                        "ELSE note || ' / ' || $sottotipoOp END WHERE voceId IN ($conSottotipo)"
                )
                // Tipo senza voce di solo tipo: la sua prima voce lo diventa (tiene ricorrenza e previsioni).
                db.execSQL(
                    "UPDATE voci SET sottotipo = NULL WHERE id IN (SELECT MIN(v.id) FROM voci v WHERE v.sottotipo IS NOT NULL AND NOT EXISTS " +
                        "(SELECT 1 FROM voci w WHERE LOWER(w.tipo) = LOWER(v.tipo) AND w.sottotipo IS NULL) GROUP BY LOWER(v.tipo))"
                )
                // Ogni voce (con sottotipo o doppia per tipo) confluisce nella prima voce di solo tipo.
                val prima = "(SELECT MIN(w.id) FROM voci w WHERE w.sottotipo IS NULL AND LOWER(w.tipo) = " +
                    "(SELECT LOWER(v.tipo) FROM voci v WHERE v.id = %s))"
                db.execSQL("UPDATE operazioni SET voceId = ${prima.format("operazioni.voceId")} WHERE voceId IS NOT NULL")
                // Le previsioni delle voci che spariscono (con sottotipo o doppie) non hanno più senso.
                val daTenere = "SELECT MIN(id) FROM voci WHERE sottotipo IS NULL GROUP BY LOWER(tipo)"
                db.execSQL("DELETE FROM previsioni_ricorrenti WHERE voceId NOT IN ($daTenere)")
                // Si ricreano le tabelle senza la colonna: prima le previsioni (che puntano alle voci).
                db.execSQL("CREATE TABLE `previsioni_copia` AS SELECT * FROM previsioni_ricorrenti")
                db.execSQL("DROP TABLE previsioni_ricorrenti")
                db.execSQL(
                    "CREATE TABLE `voci_nuova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tipo` TEXT NOT NULL, " +
                        "`entrata` INTEGER NOT NULL, `ricorrente` INTEGER NOT NULL, `mesiRicorrenza` INTEGER NOT NULL, `meseInizio` TEXT, " +
                        "`importoPrevistoCent` INTEGER, `colore` INTEGER, `obsoleta` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT INTO voci_nuova (id, tipo, entrata, ricorrente, mesiRicorrenza, meseInizio, importoPrevistoCent, colore, obsoleta) " +
                        "SELECT id, tipo, entrata, ricorrente, mesiRicorrenza, meseInizio, importoPrevistoCent, colore, obsoleta FROM voci WHERE id IN ($daTenere)"
                )
                db.execSQL("DROP TABLE voci")
                db.execSQL("ALTER TABLE voci_nuova RENAME TO voci")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_voci_tipo` ON `voci` (`tipo`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `previsioni_ricorrenti` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`voceId` INTEGER NOT NULL, `mese` TEXT NOT NULL, `importoCent` INTEGER, `data` INTEGER, `spostataA` TEXT, " +
                        "`annullata` INTEGER NOT NULL DEFAULT 0, `aggiunta` INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`voceId`) REFERENCES `voci`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_previsioni_ricorrenti_voceId_mese` ON `previsioni_ricorrenti` (`voceId`, `mese`)")
                db.execSQL(
                    "INSERT INTO previsioni_ricorrenti (id, voceId, mese, importoCent, data, spostataA, annullata, aggiunta) " +
                        "SELECT id, voceId, mese, importoCent, data, spostataA, annullata, aggiunta FROM previsioni_copia"
                )
                db.execSQL("DROP TABLE previsioni_copia")
                // Associazioni: resta il solo tipo.
                db.execSQL("CREATE TABLE `associazioni_nuova` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chiave` TEXT NOT NULL, `tipo` TEXT NOT NULL)")
                db.execSQL("INSERT INTO associazioni_nuova (id, chiave, tipo) SELECT id, chiave, tipo FROM associazioni")
                db.execSQL("DROP TABLE associazioni")
                db.execSQL("ALTER TABLE associazioni_nuova RENAME TO associazioni")
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
