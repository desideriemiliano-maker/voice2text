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

/**
 * Buste paga importate salvate in una cartella visibile di Google Drive dell'account del backup.
 * Con lo scope "drive.file" l'app vede solo i file e le cartelle creati da lei: la cartella
 * indicata nelle Impostazioni ("FamilyBalance/Buste paga", sottocartelle separate da "/") viene
 * cercata tra quelle dell'app e creata se manca.
 */
class BustePagaDrive(context: Context, email: String) {

    private val drive: Drive = run {
        val credenziali = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccountName = email }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credenziali)
            .setApplicationName("FamilyBalance")
            .build()
    }

    private fun String.sicuro() = replace("\\", "\\\\").replace("'", "\\'")

    /** Id della cartella [percorso] (creata se manca, un livello alla volta a partire dalla radice). */
    private fun cartella(percorso: String): String {
        var genitore = "root"
        percorso.split('/').map { it.trim() }.filter { it.isNotEmpty() }.forEach { nome ->
            val trovata = drive.files().list()
                .setQ("mimeType = '$MIME_CARTELLA' and name = '${nome.sicuro()}' and '$genitore' in parents and trashed = false")
                .setFields("files(id)")
                .execute().files.orEmpty().firstOrNull()?.id
            genitore = trovata ?: drive.files()
                .create(FileDrive().setName(nome).setMimeType(MIME_CARTELLA).setParents(listOf(genitore)))
                .setFields("id").execute().id
        }
        return genitore
    }

    /** Carica [file] con [nome] nella cartella [percorso]; restituisce il link per aprirlo. */
    suspend fun carica(file: File, nome: String, mime: String, percorso: String): String = withContext(Dispatchers.IO) {
        val idCartella = cartella(percorso)
        drive.files().create(FileDrive().setName(nome).setParents(listOf(idCartella)), FileContent(mime, file))
            .setFields("id, webViewLink")
            .execute()
            .let { it.webViewLink ?: "https://drive.google.com/file/d/${it.id}/view" }
    }

    /** Scarica il file [id] in [destinazione]. */
    suspend fun scarica(id: String, destinazione: File) = withContext(Dispatchers.IO) {
        destinazione.outputStream().use { out -> drive.files().get(id).executeMediaAndDownloadTo(out) }
    }

    /** Elimina definitivamente il file [id] da Drive (solo i file creati dall'app). */
    suspend fun elimina(id: String) = withContext(Dispatchers.IO) {
        drive.files().delete(id).execute()
    }

    companion object {
        /** Id del file Drive dal suo link ("…/file/d/<id>/view…" o "…?id=<id>"); null se non riconosciuto. */
        fun idDaLink(link: String): String? =
            Regex("/d/([A-Za-z0-9_-]+)").find(link)?.groupValues?.get(1) ?: Regex("[?&]id=([A-Za-z0-9_-]+)").find(link)?.groupValues?.get(1)

        private const val MIME_CARTELLA = "application/vnd.google-apps.folder"
    }
}
