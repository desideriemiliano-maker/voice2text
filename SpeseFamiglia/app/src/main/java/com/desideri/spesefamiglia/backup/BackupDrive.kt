package com.desideri.spesefamiglia.backup

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

/** Data (millisecondi) e dimensione dell'ultimo backup presente su Drive. */
data class InfoBackup(val modificatoMillis: Long, val dimensioneByte: Long)

/**
 * Backup del database nella cartella nascosta "App Data" di Google Drive dell'account scelto
 * (invisibile su drive.google.com e alle altre app, un solo file per account). L'account viene
 * scelto con il selettore di sistema e usato per email, come in WorkoutAnalyzer: se non ha ancora
 * concesso lo scope, la chiamata lancia UserRecoverableAuthIOException e la UI mostra la schermata
 * di consenso Google.
 */
class BackupDrive(private val context: Context, email: String) {

    private val drive: Drive = run {
        val credenziali = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccountName = email }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credenziali)
            .setApplicationName("SpeseFamiglia")
            .build()
    }

    private fun trovaBackup(): FileDrive? = drive.files().list()
        .setSpaces("appDataFolder")
        .setQ("name = '$NOME_FILE' and trashed = false")
        .setFields("files(id, name, modifiedTime, size)")
        .execute()
        .files
        ?.firstOrNull()

    private fun FileDrive.info() = InfoBackup(modifiedTime?.value ?: 0L, getSize() ?: 0L)

    suspend fun info(): InfoBackup? = withContext(Dispatchers.IO) { trovaBackup()?.info() }

    /** Carica [file] sovrascrivendo il backup precedente. */
    suspend fun carica(file: File): InfoBackup = withContext(Dispatchers.IO) {
        val contenuto = FileContent("application/octet-stream", file)
        val esistente = trovaBackup()
        val risultato = if (esistente != null) {
            drive.files().update(esistente.id, FileDrive(), contenuto)
                .setFields("id, modifiedTime, size")
                .execute()
        } else {
            val metadati = FileDrive().setName(NOME_FILE).setParents(listOf("appDataFolder"))
            drive.files().create(metadati, contenuto)
                .setFields("id, modifiedTime, size")
                .execute()
        }
        risultato.info()
    }

    /** Scarica il backup in [destinazione]; false se su Drive non c'è nessun backup. */
    suspend fun scarica(destinazione: File): Boolean = withContext(Dispatchers.IO) {
        val esistente = trovaBackup() ?: return@withContext false
        destinazione.outputStream().use { out -> drive.files().get(esistente.id).executeMediaAndDownloadTo(out) }
        true
    }

    companion object {
        const val NOME_FILE = "spese_famiglia_backup.db"
    }
}
