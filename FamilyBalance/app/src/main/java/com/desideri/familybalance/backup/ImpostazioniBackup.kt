package com.desideri.familybalance.backup

import android.database.sqlite.SQLiteDatabase
import com.desideri.familybalance.data.Impostazioni
import java.io.File

/**
 * Impostazioni salvate dentro la copia del database caricata su Drive, in una tabella a parte
 * (chiave/valore) che Room ignora: così un ripristino completo riporta anche le Impostazioni.
 * L'account Google del backup non viene salvato né ripristinato (resta quello del telefono).
 */
object ImpostazioniBackup {
    private const val TABELLA = "impostazioni_backup"

    /** Scrive [imp] nel file di database [file] (la copia da caricare). */
    fun scrivi(file: File, imp: Impostazioni) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABELLA (chiave TEXT PRIMARY KEY NOT NULL, valore TEXT NOT NULL)")
            db.execSQL("DELETE FROM $TABELLA")
            mapOf(
                "targetRisparmioCent" to imp.targetRisparmioCent.toString(),
                "cambioChfEur" to imp.cambioChfEur.toString(),
                "bloccoBiometrico" to imp.bloccoBiometrico.toString(),
                "backupDaMantenere" to imp.backupDaMantenere.toString(),
                "minutiBlocco" to imp.minutiBlocco.toString()
            ).forEach { (k, v) -> db.execSQL("INSERT INTO $TABELLA (chiave, valore) VALUES (?, ?)", arrayOf(k, v)) }
        }
    }

    /** Le impostazioni [attuali] aggiornate con quelle salvate nel backup [file] (invariate se non ce ne sono). */
    fun leggi(file: File, attuali: Impostazioni): Impostazioni {
        val valori = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT chiave, valore FROM $TABELLA", null).use { c ->
                    buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
                }
            }
        }.getOrNull() ?: return attuali
        return attuali.copy(
            targetRisparmioCent = valori["targetRisparmioCent"]?.toLongOrNull() ?: attuali.targetRisparmioCent,
            cambioChfEur = valori["cambioChfEur"]?.toDoubleOrNull() ?: attuali.cambioChfEur,
            bloccoBiometrico = valori["bloccoBiometrico"]?.toBooleanStrictOrNull() ?: attuali.bloccoBiometrico,
            backupDaMantenere = valori["backupDaMantenere"]?.toIntOrNull() ?: attuali.backupDaMantenere,
            minutiBlocco = valori["minutiBlocco"]?.toIntOrNull() ?: attuali.minutiBlocco
        )
    }
}
