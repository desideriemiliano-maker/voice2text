package com.desideri.voice2text.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Salva una copia del file audio corrente (registrato in-app o ricevuto/scelto altrove, sempre
 * un Uri leggibile dal nostro ContentResolver) in una posizione visibile all'utente, cartella
 * Music/Voice2Text. Su Android 10+ passa da MediaStore (scoped storage, nessun permesso
 * necessario); su versioni precedenti scrive direttamente nella cartella pubblica Music
 * (richiede WRITE_EXTERNAL_STORAGE, chiesto dalla UI solo li' dove serve).
 */
class AudioSaver(private val context: Context) {

    fun salva(sourceUri: Uri, nomeFileSuggerito: String): Uri {
        val mimeType = context.contentResolver.getType(sourceUri) ?: "audio/*"
        val nomeFile = if ('.' in nomeFileSuggerito) nomeFileSuggerito else "$nomeFileSuggerito.aac"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, nomeFile)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Voice2Text")
            }
            val destUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Impossibile creare il file nella libreria audio.")
            copia(sourceUri, destUri)
            destUri
        } else {
            @Suppress("DEPRECATION")
            val cartella = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Voice2Text")
            cartella.mkdirs()
            val destFile = File(cartella, nomeFile)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Impossibile leggere il file audio da salvare.")
            Uri.fromFile(destFile)
        }
    }

    private fun copia(sourceUri: Uri, destUri: Uri) {
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            context.contentResolver.openInputStream(sourceUri)?.use { input -> input.copyTo(output) }
                ?: throw IllegalStateException("Impossibile leggere il file audio da salvare.")
        } ?: throw IllegalStateException("Impossibile scrivere il file nella libreria audio.")
    }
}
