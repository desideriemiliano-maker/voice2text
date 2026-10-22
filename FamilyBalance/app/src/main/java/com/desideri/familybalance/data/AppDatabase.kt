package com.desideri.familybalance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [Conto::class, ContoValuta::class, Voce::class, Operazione::class, Associazione::class, Cambio::class, PrevisioneRicorrente::class, TipoStipendio::class, VoceStipendio::class, Stipendio::class, ImportoStipendio::class], version = 29, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SpeseDao
    abstract fun stipendiDao(): StipendiDao

    companion object {
        const val NOME_FILE = "familybalance.db"

        @Volatile
        private var istanza: AppDatabase? = null

        fun get(context: Context): AppDatabase = istanza ?: synchronized(this) {
            istanza ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOME_FILE)
                // TRUNCATE invece di WAL: tutto il contenuto sta nel solo file .db, così il backup
                // su Drive è una semplice copia del file senza dover gestire -wal/-shm.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRAZIONE_1_2, MIGRAZIONE_2_3, MIGRAZIONE_3_4, MIGRAZIONE_4_5, MIGRAZIONE_5_6, MIGRAZIONE_6_7, MIGRAZIONE_7_8, MIGRAZIONE_8_9, MIGRAZIONE_9_10, MIGRAZIONE_10_11, MIGRAZIONE_11_12, MIGRAZIONE_12_13, MIGRAZIONE_13_14, MIGRAZIONE_14_15, MIGRAZIONE_15_16, MIGRAZIONE_16_17, MIGRAZIONE_17_18, MIGRAZIONE_18_19, MIGRAZIONE_19_20, MIGRAZIONE_20_21, MIGRAZIONE_21_22, MIGRAZIONE_22_23, MIGRAZIONE_23_24, MIGRAZIONE_24_25, MIGRAZIONE_25_26, MIGRAZIONE_26_27, MIGRAZIONE_27_28, MIGRAZIONE_28_29)
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

        /** Versione 13: legame stabile tra operazione e movimento dell'estratto conto. */
        private val MIGRAZIONE_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `operazioni` ADD COLUMN `chiaveEstratto` TEXT")
            }
        }

        /** Versione 14: conti virtuali (esclusi dal saldo totale e dal bilancio). */
        private val MIGRAZIONE_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conti` ADD COLUMN `virtuale` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 15: colore dei conti e ordine dei conti/valuta nella lista. */
        private val MIGRAZIONE_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conti` ADD COLUMN `colore` INTEGER")
                db.execSQL("ALTER TABLE `conti_valuta` ADD COLUMN `ordine` INTEGER")
            }
        }

        /** Versione 16: descrizione libera di ogni conto/valuta. */
        private val MIGRAZIONE_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conti_valuta` ADD COLUMN `descrizione` TEXT")
            }
        }

        /** Versione 17: anagrafica stipendi (tipologie e voci) e stipendi registrati con i loro importi. */
        private val MIGRAZIONE_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stipendi_tipi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nome` TEXT NOT NULL, " +
                        "`valuta` TEXT NOT NULL, `contoValutaId` INTEGER, `obsoleto` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stipendi_voci` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tipoId` INTEGER NOT NULL, " +
                        "`descrizione` TEXT NOT NULL, `ordine` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tipoId`) REFERENCES `stipendi_tipi`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stipendi_voci_tipoId` ON `stipendi_voci` (`tipoId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stipendi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tipoId` INTEGER NOT NULL, " +
                        "`mese` TEXT NOT NULL, FOREIGN KEY(`tipoId`) REFERENCES `stipendi_tipi`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stipendi_tipoId_mese` ON `stipendi` (`tipoId`, `mese`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stipendi_importi` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stipendioId` INTEGER NOT NULL, " +
                        "`voceId` INTEGER NOT NULL, `importoCent` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`stipendioId`) REFERENCES `stipendi`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`voceId`) REFERENCES `stipendi_voci`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stipendi_importi_stipendioId_voceId` ON `stipendi_importi` (`stipendioId`, `voceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stipendi_importi_voceId` ON `stipendi_importi` (`voceId`)")
            }
        }

        /** Versione 18: voci di stipendio calcolate (somma di altre voci, con percentuale facoltativa). */
        private val MIGRAZIONE_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `calcolata` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `addendi` TEXT")
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `percentuale` REAL")
            }
        }

        /** Versione 19: nomi con cui le voci di stipendio compaiono nelle buste paga. */
        private val MIGRAZIONE_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `nomiImport` TEXT")
            }
        }

        /** Versione 20: percentuale delle voci calcolate memorizzata in ogni stipendio. */
        private val MIGRAZIONE_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_importi` ADD COLUMN `percentuale` REAL")
            }
        }

        /** Versione 21: arrotondamento del risultato delle voci di stipendio calcolate. */
        private val MIGRAZIONE_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `arrotondaDecimali` INTEGER")
            }
        }

        /** Versione 22: arrotondamento delle voci calcolate all'ultima cifra 0 o 5. */
        private val MIGRAZIONE_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `arrotondaCinque` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 23: cambio di segno del risultato delle voci di stipendio calcolate. */
        private val MIGRAZIONE_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `invertiSegno` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 24: importi degli stipendi modificati a mano. */
        private val MIGRAZIONE_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_importi` ADD COLUMN `manuale` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versione 25: valore importato degli importi degli stipendi, per ripristinarlo dopo una modifica a mano. */
        private val MIGRAZIONE_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_importi` ADD COLUMN `importoImportatoCent` INTEGER")
            }
        }

        /** Versione 26: valori sostitutivi delle voci sommate, solo per una voce calcolata. */
        private val MIGRAZIONE_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_importi` ADD COLUMN `sostituzioni` TEXT")
            }
        }

        /** Versione 27: valore fisso nella somma delle voci di stipendio calcolate. */
        private val MIGRAZIONE_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi_voci` ADD COLUMN `valoreFissoCent` INTEGER")
            }
        }

        /** Versione 28: più stipendi nello stesso mese (l'indice su tipologia e mese non è più univoco). */
        private val MIGRAZIONE_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_stipendi_tipoId_mese`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stipendi_tipoId_mese` ON `stipendi` (`tipoId`, `mese`)")
            }
        }

        /** Versione 29: operazione di accredito associata a uno stipendio (riscontro stipendi). */
        private val MIGRAZIONE_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stipendi` ADD COLUMN `operazioneId` INTEGER")
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
