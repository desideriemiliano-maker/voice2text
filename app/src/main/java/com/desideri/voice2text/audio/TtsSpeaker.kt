package com.desideri.voice2text.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Wrapper minimale su TextToSpeech per leggere ad alta voce riassunto e testo riscritto.
 * [onLetturaFinita] viene invocato (dal thread del motore TTS, non quello UI) sia a fine
 * lettura naturale sia in caso di errore, cosi' la UI puo' sempre tornare allo stato "fermo".
 */
class TtsSpeaker(context: Context, private val onLetturaFinita: (String) -> Unit) {

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { esito ->
            if (esito == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let(onLetturaFinita)
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let(onLetturaFinita)
                    }
                })
            }
        }
    }

    /** Legge [testo] ad alta voce; [utteranceId] identifica la lettura per il callback di fine. */
    fun leggi(testo: String, utteranceId: String) {
        tts?.speak(testo, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun ferma() {
        tts?.stop()
    }

    /** Da chiamare quando il componente che lo usa viene distrutto (es. onCleared del ViewModel). */
    fun rilascia() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
