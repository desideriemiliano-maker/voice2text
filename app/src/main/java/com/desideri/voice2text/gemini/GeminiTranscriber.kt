package com.desideri.voice2text.gemini

import android.content.Context
import android.net.Uri
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val GEMINI_MODEL = "gemini-3.5-flash-lite"

// Fallback se il content resolver non sa dire il mime type (capita con alcuni Uri
// di condivisione "generici"): Gemini accetta comunque i formati audio comuni.
private const val DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"

private const val PROMPT_TRASCRIZIONE =
    "Ascolta questo audio e restituisci: la trascrizione fedele del parlato in italiano, " +
        "un breve riassunto del contenuto trascritto, e una stima approssimativa di chi parla " +
        "basata solo sulla voce (genere, eta', umore, tono di voce). Per umore e tono di voce " +
        "motiva brevemente la classificazione, indicando gli indizi vocali (ritmo, energia, " +
        "pause, intonazione, volume, ecc.) su cui si basa. Sono stime indicative, non certezze: " +
        "se un aspetto non e' deducibile dalla voce usa 'Non determinabile' (e nella motivazione " +
        "spiega perche')."

/**
 * Esito strutturato di una trascrizione: [testo] e' il parlato trascritto, [riassunto] un
 * sunto del contenuto; gli altri campi sono stime di Gemini dedotte dalla sola voce (non dati
 * certi) - vanno presentati come tali nell'interfaccia. [umoreMotivazione]/[tonoVoceMotivazione]
 * spiegano su quali indizi vocali si basa la classificazione di [umore]/[tonoVoce].
 */
data class TrascrizioneResult(
    val testo: String,
    val riassunto: String,
    val genere: String,
    val eta: String,
    val umore: String,
    val umoreMotivazione: String,
    val tonoVoce: String,
    val tonoVoceMotivazione: String
)

private fun schemaTrascrizione(): Schema =
    Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            mapOf(
                "testoTrascritto" to Schema.builder().type(Type.Known.STRING)
                    .description("trascrizione fedele del parlato, in italiano").build(),
                "riassunto" to Schema.builder().type(Type.Known.STRING)
                    .description("breve riassunto del contenuto trascritto, in italiano").build(),
                "genere" to Schema.builder().type(Type.Known.STRING)
                    .description("stima del genere di chi parla: Uomo, Donna o Non determinabile").build(),
                "eta" to Schema.builder().type(Type.Known.STRING)
                    .description("stima della fascia d'eta', es. '25-35 anni', o Non determinabile").build(),
                "umore" to Schema.builder().type(Type.Known.STRING)
                    .description("umore percepito dalla voce, es. Allegro, Nervoso, Calmo, Triste").build(),
                "umoreMotivazione" to Schema.builder().type(Type.Known.STRING)
                    .description("breve spiegazione degli indizi vocali su cui si basa la stima dell'umore").build(),
                "tonoVoce" to Schema.builder().type(Type.Known.STRING)
                    .description("timbro/tono della voce, es. Pacato, Concitato, Formale, Informale").build(),
                "tonoVoceMotivazione" to Schema.builder().type(Type.Known.STRING)
                    .description("breve spiegazione degli indizi vocali su cui si basa la stima del tono di voce").build()
            )
        )
        .required(
            "testoTrascritto", "riassunto", "genere", "eta",
            "umore", "umoreMotivazione", "tonoVoce", "tonoVoceMotivazione"
        )
        .build()

/**
 * Incapsula la chiamata a Gemini per la trascrizione: legge i byte dell'audio dal content
 * resolver (funziona sia per l'Uri ricevuto via condivisione sia per quello scelto dal file
 * picker in-app, nessun permesso di storage necessario in entrambi i casi) e li manda come
 * Part audio + prompt testuale. Stessa modalita' di chiamata (Client/Content/Part) usata nel
 * progetto WorkoutAnalyzer, qui con responseSchema per ottenere un JSON affidabile invece del
 * solo testo (vedi generaJsonStrutturato in GeminiWorkoutAnalyzer).
 */
class GeminiTranscriber {

    suspend fun transcribe(context: Context, apiKey: String, uri: Uri): TrascrizioneResult {
        val mimeType = context.contentResolver.getType(uri) ?: DEFAULT_AUDIO_MIME_TYPE

        val audioBytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Impossibile leggere il file audio selezionato.")
        }

        val audioPart = Part.fromBytes(audioBytes, mimeType)
        val textPart = Part.fromText(PROMPT_TRASCRIZIONE)

        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(schemaTrascrizione())
            .build()

        val client = Client.builder()
            .apiKey(apiKey)
            .build()

        val response = withContext(Dispatchers.IO) {
            client.models.generateContent(GEMINI_MODEL, Content.fromParts(audioPart, textPart), config)
        }

        val json = response.text()
            ?: throw IllegalStateException("Gemini non ha restituito alcuna trascrizione.")

        val obj = JSONObject(json)
        return TrascrizioneResult(
            testo = obj.optString("testoTrascritto").ifBlank { "Nessun testo trascritto." },
            riassunto = obj.optString("riassunto").ifBlank { "Nessun riassunto disponibile." },
            genere = obj.optString("genere").ifBlank { "Non determinabile" },
            eta = obj.optString("eta").ifBlank { "Non determinabile" },
            umore = obj.optString("umore").ifBlank { "Non determinabile" },
            umoreMotivazione = obj.optString("umoreMotivazione").ifBlank { "Nessuna motivazione disponibile." },
            tonoVoce = obj.optString("tonoVoce").ifBlank { "Non determinabile" },
            tonoVoceMotivazione = obj.optString("tonoVoceMotivazione").ifBlank { "Nessuna motivazione disponibile." }
        )
    }
}
