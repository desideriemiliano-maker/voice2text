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
import com.desideri.voice2text.gemini.GeminiTranscriber
import com.desideri.voice2text.gemini.TrascrizioneResult
import kotlinx.coroutines.launch

data class TranscriptionUiState(
    val audioUri: Uri? = null,
    val fileName: String? = null,
    val isTranscribing: Boolean = false,
    val result: TrascrizioneResult? = null,
    val error: String? = null
)

/**
 * L'API key arriva da BuildConfig.GEMINI_API_KEY (definita in local.properties, mai
 * committata - stessa convenzione del progetto WorkoutAnalyzer): niente schermata
 * impostazioni, la chiave e' fissata in fase di build.
 */
class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriber = GeminiTranscriber()

    var uiState by mutableStateOf(TranscriptionUiState())
        private set

    fun selezionaAudio(uri: Uri) {
        uiState = TranscriptionUiState(audioUri = uri, fileName = resolveFileName(uri))
    }

    fun trascrivi() {
        val uri = uiState.audioUri ?: return
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            uiState = uiState.copy(error = "GEMINI_API_KEY non configurata in local.properties.")
            return
        }

        uiState = uiState.copy(isTranscribing = true, error = null, result = null)
        viewModelScope.launch {
            uiState = try {
                val risultato = transcriber.transcribe(getApplication(), BuildConfig.GEMINI_API_KEY, uri)
                uiState.copy(isTranscribing = false, result = risultato)
            } catch (e: Exception) {
                uiState.copy(isTranscribing = false, error = e.message ?: "Errore durante la trascrizione.")
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
