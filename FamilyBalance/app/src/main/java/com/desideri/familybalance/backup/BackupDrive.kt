package com.desideri.familybalance.backup

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as FileDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Un backup presente su Drive: data (millisecondi), dimensione e testo facoltativo dato dall'utente. */
data class InfoBackup(
    val id: String,
    val modificatoMillis: Long,
    val dimensioneByte: Long,
    val testo: String?
)

/**
 * Backup del database nella cartella nascosta "App Data" di Google Drive dell'account scelto
 * (invisibile su drive.google.com e alle altre app). Ogni backup è un file a sé, con il testo
 * facoltativo dell'utente nella descrizione del file; dopo ogni backup si tengono solo gli ultimi N
 * (Impostazioni). L'account viene scelto con il selettore di sistema e usato per email, come in
 * WorkoutAnalyzer: se non ha ancora concesso lo scope, la chiamata lancia
 * UserRecoverableAuthIOException e la UI mostra la schermata di consenso Google.
 */
class BackupDrive(private val context: Context, email: String) {

    private val drive: Drive = run {
        val credenziali = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccountName = email }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credenziali)
            .setApplicationName("FamilyBalance")
            .build()
    }

    private fun FileDrive.info() = InfoBackup(id, modifiedTime?.value ?: 0L, getSize() ?: 0L, description?.ifBlank { null })

    /** Tutti i backup dell'app su questo account, dal più recente (compreso quello unico delle versioni precedenti). */
    suspend fun elenco(): List<InfoBackup> = withContext(Dispatchers.IO) {
        drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name contains '$PREFISSO' and trashed = false")
            .setFields("files(id, name, modifiedTime, size, description)")
            .setPageSize(100)
            .execute()
            .files
            .orEmpty()
            .map { it.info() }
            .sortedByDescending { it.modificatoMillis }
    }

    /** Carica [file] come nuovo backup con [testo], poi elimina i più vecchi oltre i [daMantenere] più recenti. */
    suspend fun carica(file: File, testo: String?, daMantenere: Int): List<InfoBackup> = withContext(Dispatchers.IO) {
        val nome = PREFISSO + "_" + System.currentTimeMillis() + ".db"
        val metadati = FileDrive().setName(nome).setParents(listOf("appDataFolder")).setDescription(testo?.ifBlank { null })
        drive.files().create(metadati, FileContent("application/octet-stream", file)).setFields("id").execute()
        val tutti = elenco()
        tutti.drop(daMantenere.coerceAtLeast(1)).forEach { vecchio -> drive.files().delete(vecchio.id).execute() }
        tutti.take(daMantenere.coerceAtLeast(1))
    }

    /** Scarica il backup [id] in [destinazione]. */
    suspend fun scarica(id: String, destinazione: File) = withContext(Dispatchers.IO) {
        destinazione.outputStream().use { out -> drive.files().get(id).executeMediaAndDownloadTo(out) }
    }

    companion object {
        /** Prefisso dei file di backup (le versioni precedenti usavano il solo "familybalance_backup.db"). */
        const val PREFISSO = "familybalance_backup"
    }
}
