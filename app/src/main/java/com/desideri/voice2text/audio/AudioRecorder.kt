package com.desideri.voice2text.audio

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registra un audio dal microfono in AAC/ADTS (mime "audio/aac", uno dei formati audio
 * accettati direttamente da Gemini) in un file temporaneo nella cache dell'app. Nessun
 * FileProvider necessario: il file resta interno al processo, letto solo dal nostro
 * ContentResolver (mai passato ad altre app via Intent).
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    /** Avvia la registrazione. Lancia SecurityException se il permesso RECORD_AUDIO manca. */
    fun avvia() {
        val output = File(context.cacheDir, "registrazione_${timestamp()}.aac")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        file = output
    }

    /** Ferma la registrazione e restituisce l'Uri del file registrato, o null se non era in corso. */
    fun ferma(): Uri? {
        val registrato = file
        try {
            recorder?.stop()
        } finally {
            recorder?.release()
            recorder = null
        }
        return registrato?.let { Uri.fromFile(it) }
    }

    /** Interrompe e scarta la registrazione in corso, cancellando il file parziale. */
    fun annulla() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // stop() può lanciare se richiamato troppo a ridosso di start(): il file va comunque scartato.
        }
        recorder?.release()
        recorder = null
        file?.delete()
        file = null
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALIAN).format(Date())
}
