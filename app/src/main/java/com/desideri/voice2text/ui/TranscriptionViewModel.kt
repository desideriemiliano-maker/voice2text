package com.desideri.voice2text.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.desideri.voice2text.BuildConfig
import com.desideri.voice2text.audio.AudioRecorder
import com.desideri.voice2text.gemini.GeminiTranscriber
import com.desideri.voice2text.gemini.StileRiscrittura
import com.desideri.voice2text.gemini.TrascrizioneResult
import kotlinx.coroutines.launch

data class TranscriptionUiState(
    val audioUri: Uri? = null,
    val fileName: String? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val result: TrascrizioneResult? = null,
    val isRiscrivendo: Boolean = false,
    val stileRiscritto: StileRiscrittura? = null,
    val testoRiscritto: String? = null,
    val error: String? = null
)

/**
 * L'API key arriva da BuildConfig.GEMINI_API_KEY (definita in local.properties, mai
 * committata - stessa convenzione del progetto WorkoutAnalyzer): niente schermata
 * impostazioni, la chiave e' fissata in fase di build.
 */
class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriber = GeminiTranscriber()
    private val audioRecorder = AudioRecorder(application)

    var uiState by mutableStateOf(TranscriptionUiState())
        private set

    fun selezionaAudio(uri: Uri) {
        uiState = TranscriptionUiState(audioUri = uri, fileName = resolveFileName(uri))
    }

    /** Il permesso RECORD_AUDIO va gia' concesso a questo punto: lo richiede la UI. */
    fun avviaRegistrazione() {
        try {
            audioRecorder.avvia()
            uiState = TranscriptionUiState(isRecording = true)
        } catch (e: Exception) {
            uiState = TranscriptionUiState(error = "Impossibile avviare la registrazione: ${e.message}")
        }
    }

    /** Ferma la registrazione e avvia subito la trascrizione del file appena registrato. */
    fun fermaRegistrazioneETrascrivi() {
        val uri = audioRecorder.ferma()
        if (uri != null) {
            selezionaAudio(uri)
            trascrivi()
        } else {
            uiState = TranscriptionUiState(error = "Registrazione non riuscita.")
        }
    }

    fun annullaRegistrazione() {
        audioRecorder.annulla()
        uiState = TranscriptionUiState()
    }

    fun segnalaPermessoMicrofonoNegato() {
        uiState = uiState.copy(error = "Permesso microfono negato: impossibile registrare.")
    }

    override fun onCleared() {
        super.onCleared()
        if (uiState.isRecording) audioRecorder.annulla()
    }

    fun trascrivi() {
        val uri = uiState.audioUri ?: return
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            uiState = uiState.copy(error = "GEMINI_API_KEY non configurata in local.properties.")
            return
        }

        uiState = uiState.copy(
            isTranscribing = true, error = null, result = null,
            testoRiscritto = null, stileRiscritto = null
        )
        viewModelScope.launch {
            uiState = try {
                val risultato = transcriber.transcribe(getApplication(), BuildConfig.GEMINI_API_KEY, uri)
                uiState.copy(isTranscribing = false, result = risultato)
            } catch (e: Exception) {
                uiState.copy(isTranscribing = false, error = e.message ?: "Errore durante la trascrizione.")
            }
        }
    }

    /** Riscrive il testo gia' trascritto nello [stile] scelto (Formale/Amichevole/Neutro). */
    fun riscrivi(stile: StileRiscrittura) {
        val testo = uiState.result?.testo ?: return
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            uiState = uiState.copy(error = "GEMINI_API_KEY non configurata in local.properties.")
            return
        }

        uiState = uiState.copy(isRiscrivendo = true, error = null, testoRiscritto = null, stileRiscritto = null)
        viewModelScope.launch {
            uiState = try {
                val riscritto = transcriber.riscrivi(BuildConfig.GEMINI_API_KEY, testo, stile)
                uiState.copy(isRiscrivendo = false, testoRiscritto = riscritto, stileRiscritto = stile)
            } catch (e: Exception) {
                uiState.copy(isRiscrivendo = false, error = e.message ?: "Errore durante la riscrittura.")
            }
        }
    }

    fun reset() {
        uiState = TranscriptionUiState()
    }

    private fun resolveFileName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
