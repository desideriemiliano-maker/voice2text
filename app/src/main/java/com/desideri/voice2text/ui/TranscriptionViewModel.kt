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
import com.desideri.voice2text.audio.AudioSaver
import com.desideri.voice2text.audio.TtsSpeaker
import com.desideri.voice2text.gemini.GeminiTranscriber
import com.desideri.voice2text.gemini.RegistroPromptStore
import com.desideri.voice2text.gemini.StileRiscrittura
import com.desideri.voice2text.gemini.TrascrizioneResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quale testo sta leggendo il TTS in questo momento, se ce n'e' uno. */
enum class TestoInLettura { RIASSUNTO, RISCRITTO }

data class TranscriptionUiState(
    val audioUri: Uri? = null,
    val fileName: String? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val result: TrascrizioneResult? = null,
    val isRiscrivendo: Boolean = false,
    val stileRiscritto: StileRiscrittura? = null,
    val testoRiscritto: String? = null,
    val testoInLettura: TestoInLettura? = null,
    val messaggioSalvataggio: String? = null,
    val error: String? = null
)

/**
 * L'API key arriva da BuildConfig.GEMINI_API_KEY (definita in local.properties, mai
 * committata - stessa convenzione del progetto WorkoutAnalyzer): niente schermata
 * impostazioni, la chiave e' fissata in fase di build.
 */
class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriber = GeminiTranscriber(RegistroPromptStore(application))
    private val audioRecorder = AudioRecorder(application)
    private val audioSaver = AudioSaver(application)
    private val ttsSpeaker = TtsSpeaker(application) {
        viewModelScope.launch(Dispatchers.Main) { uiState = uiState.copy(testoInLettura = null) }
    }

    var uiState by mutableStateOf(TranscriptionUiState())
        private set

    fun selezionaAudio(uri: Uri) {
        uiState = TranscriptionUiState(audioUri = uri, fileName = resolveFileName(uri))
    }

    /** Copia il file audio corrente in Musica/Voice2Text, cosi' resta anche fuori dall'app. */
    fun salvaAudio() {
        val uri = uiState.audioUri ?: return
        val nomeFile = uiState.fileName ?: "voice2text_${System.currentTimeMillis()}"
        uiState = uiState.copy(messaggioSalvataggio = null, error = null)
        viewModelScope.launch {
            uiState = try {
                withContext(Dispatchers.IO) { audioSaver.salva(uri, nomeFile) }
                uiState.copy(messaggioSalvataggio = "Audio salvato in Musica/Voice2Text.")
            } catch (e: Exception) {
                uiState.copy(error = "Impossibile salvare l'audio: ${e.message}")
            }
        }
    }

    fun segnalaPermessoSalvataggioNegato() {
        uiState = uiState.copy(error = "Permesso di archiviazione negato: impossibile salvare l'audio.")
    }

    /** Ripulisce solo i risultati (trascrizione, riscrittura, messaggi), non l'audio selezionato. */
    fun puliciRisultati() {
        ttsSpeaker.ferma()
        uiState = uiState.copy(
            result = null, error = null,
            testoRiscritto = null, stileRiscritto = null, isRiscrivendo = false,
            testoInLettura = null, messaggioSalvataggio = null
        )
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
        ttsSpeaker.rilascia()
    }

    fun trascrivi() {
        val uri = uiState.audioUri ?: return
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            uiState = uiState.copy(error = "GEMINI_API_KEY non configurata in local.properties.")
            return
        }

        ttsSpeaker.ferma()
        uiState = uiState.copy(
            isTranscribing = true, error = null, result = null,
            testoRiscritto = null, stileRiscritto = null, testoInLettura = null
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

        val stavaLeggendoRiscritto = uiState.testoInLettura == TestoInLettura.RISCRITTO
        if (stavaLeggendoRiscritto) ttsSpeaker.ferma()
        uiState = uiState.copy(
            isRiscrivendo = true, error = null, testoRiscritto = null, stileRiscritto = null,
            testoInLettura = if (stavaLeggendoRiscritto) null else uiState.testoInLettura
        )
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
        ttsSpeaker.ferma()
        uiState = TranscriptionUiState()
    }

    /** Avvia/ferma la lettura ad alta voce del riassunto (toggle sul pulsante in UI). */
    fun leggiORiassunto() {
        if (uiState.testoInLettura == TestoInLettura.RIASSUNTO) {
            fermaLettura()
            return
        }
        val testo = uiState.result?.riassunto ?: return
        ttsSpeaker.leggi(testo, "riassunto")
        uiState = uiState.copy(testoInLettura = TestoInLettura.RIASSUNTO)
    }

    /** Avvia/ferma la lettura ad alta voce del testo riscritto (toggle sul pulsante in UI). */
    fun leggiOTestoRiscritto() {
        if (uiState.testoInLettura == TestoInLettura.RISCRITTO) {
            fermaLettura()
            return
        }
        val testo = uiState.testoRiscritto ?: return
        ttsSpeaker.leggi(testo, "riscritto")
        uiState = uiState.copy(testoInLettura = TestoInLettura.RISCRITTO)
    }

    private fun fermaLettura() {
        ttsSpeaker.ferma()
        uiState = uiState.copy(testoInLettura = null)
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
