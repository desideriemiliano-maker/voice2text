package com.desideri.familybalance.estratto

import android.content.Context
import android.net.Uri
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Bolletta o ricevuta letta da Gemini: causale (o descrizione del pagamento), data di esecuzione e importo (centesimi, positivo). */
data class RicevutaLetta(val causale: String, val data: LocalDate?, val importoCent: Long? = null)

/**
 * Legge una bolletta o ricevuta di pagamento (PDF o immagine) con Gemini, che restituisce la
 * causale e la data di esecuzione del pagamento (o di emissione, se non c'è).
 */
class RicevutaGemini(private val apiKey: String, private val registro: RegistroPromptStore? = null) {

    suspend fun leggi(context: Context, uri: Uri, nomeFile: String, mime: String): RicevutaLetta {
        if (apiKey.isBlank()) throw IllegalStateException("Chiave Gemini non configurata in questa build")
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Impossibile leggere il file")
        }
        val parte = when {
            mime == "application/pdf" || nomeFile.lowercase().endsWith(".pdf") -> Part.fromBytes(bytes, "application/pdf")
            mime.startsWith("image/") -> Part.fromBytes(bytes, mime)
            else -> throw IllegalArgumentException("Formato non supportato: usa un PDF o un'immagine")
        }
        val prompt = "Questo documento è una bolletta o una ricevuta di pagamento. Indica la causale del pagamento " +
            "(o, se manca, la descrizione breve di cosa si paga e a chi, es. \"Bolletta luce Enel\") nel campo causale, " +
            "la data di esecuzione del pagamento (o, se non c'è, la data di emissione o di scadenza) nel campo data, formato YYYY-MM-DD, " +
            "e l'importo pagato (o da pagare) nel campo importo, come numero positivo in euro."
        val schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "causale" to Schema.builder().type(Type.Known.STRING).build(),
                    "data" to Schema.builder().type(Type.Known.STRING).description("YYYY-MM-DD").build(),
                    "importo" to Schema.builder().type(Type.Known.NUMBER).description("importo in euro, positivo").build()
                )
            )
            .required("causale", "data", "importo")
            .build()
        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(schema)
            .temperature(0f)
            .build()
        val client = Client.builder().apiKey(apiKey).build()
        val richiesta = "$prompt\n\nFile: $nomeFile ($mime, ${bytes.size / 1024} KB)"
        var ultimo: Exception? = null
        for (attesa in listOf(0L, 3_000L, 10_000L)) {
            if (attesa > 0) delay(attesa)
            var json: String? = null
            try {
                val risposta = withContext(Dispatchers.IO) {
                    client.models.generateContent(MODELLO, Content.fromParts(parte, Part.fromText(prompt)), config)
                }
                json = risposta.text() ?: throw IllegalStateException("Gemini non ha restituito nulla")
                val o = JSONObject(json)
                val letta = RicevutaLetta(
                    o.optString("causale").trim().take(200),
                    runCatching { LocalDate.parse(o.optString("data").trim().take(10)) }.getOrNull(),
                    o.optDouble("importo", Double.NaN).takeIf { !it.isNaN() }
                        ?.let { kotlin.math.abs(it).toBigDecimal().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong() }
                )
                registra(richiesta, json, false)
                return letta
            } catch (e: Exception) {
                ultimo = e
                registra(richiesta, (e.message ?: e.javaClass.simpleName) + (json?.let { "\n\nRisposta ricevuta:\n$it" } ?: ""), true)
            }
        }
        throw IllegalStateException(ultimo?.message?.take(200) ?: "Chiamata a Gemini non riuscita", ultimo)
    }

    private suspend fun registra(richiesta: String, risposta: String, errore: Boolean) = withContext(Dispatchers.IO) {
        registro?.registra(TipoChiamataGemini.RICEVUTA, richiesta, risposta.take(60_000), errore)
    }

    private companion object {
        const val MODELLO = "gemini-3.5-flash-lite"
    }
}
