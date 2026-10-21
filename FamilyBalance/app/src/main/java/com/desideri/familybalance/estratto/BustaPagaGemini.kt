package com.desideri.familybalance.estratto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import java.math.RoundingMode
import java.time.YearMonth

/** Una voce letta dalla busta paga: descrizione e importo in centesimi (con segno). */
data class VoceBustaLetta(val descrizione: String, val importoCent: Long)

/** Busta paga letta da Gemini: mese di competenza (null se non riconosciuto) e voci. */
data class BustaPagaLetta(val mese: YearMonth?, val voci: List<VoceBustaLetta>)

/**
 * Legge una busta paga (PDF o immagine) con Gemini, che restituisce un JSON con il mese/anno dello
 * stipendio e le voci con il loro importo. Le [vociNote] della tipologia di stipendio sono indicate
 * a Gemini perché usi gli stessi nomi quando una voce della busta corrisponde.
 */
class BustaPagaGemini(private val apiKey: String, private val registro: RegistroPromptStore? = null) {

    suspend fun leggi(context: Context, uri: Uri, nomeStipendio: String, valuta: String, vociNote: List<String>): BustaPagaLetta {
        if (apiKey.isBlank()) throw IllegalStateException("Chiave Gemini non configurata in questa build")
        val mime = context.contentResolver.getType(uri)
        val nome = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Impossibile leggere il file")
        }
        val parte = when {
            mime == "application/pdf" || nome.lowercase().endsWith(".pdf") -> Part.fromBytes(bytes, "application/pdf")
            mime?.startsWith("image/") == true -> Part.fromBytes(bytes, mime)
            else -> throw IllegalArgumentException("Formato non supportato: usa un PDF o un'immagine della busta paga")
        }
        val prompt = buildString {
            append("Questa è una busta paga ($nomeStipendio, importi in $valuta). ")
            append("Indica il mese e l'anno di competenza dello stipendio (campo mese, formato YYYY-MM) ")
            append("e TUTTE le voci con un importo che lo compongono (competenze, trattenute, contributi, imposte, netto…), ")
            append("ignorando le voci senza importo, le basi di calcolo, le aliquote, le ore e i progressivi annui. ")
            append("Per ogni voce: descrizione (breve, come appare) e importo (numero in $valuta: positivo per competenze e netto, ")
            append("negativo per trattenute, contributi e imposte).")
            if (vociNote.isNotEmpty()) {
                append(" Voci già definite dall'utente per questo stipendio: ")
                append(vociNote.joinToString("; ") { "\"$it\"" })
                append(". Se una voce della busta corrisponde a una di queste, usa ESATTAMENTE quel nome come descrizione.")
            }
        }
        val schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "mese" to Schema.builder().type(Type.Known.STRING).description("mese di competenza, YYYY-MM").build(),
                    "voci" to Schema.builder().type(Type.Known.ARRAY).items(
                        Schema.builder().type(Type.Known.OBJECT).properties(
                            mapOf(
                                "descrizione" to Schema.builder().type(Type.Known.STRING).build(),
                                "importo" to Schema.builder().type(Type.Known.NUMBER).build()
                            )
                        ).required("descrizione", "importo").build()
                    ).build()
                )
            )
            .required("mese", "voci")
            .build()
        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(schema)
            .temperature(0f)
            .build()
        val client = Client.builder().apiKey(apiKey).build()
        val richiesta = "$prompt\n\nFile: $nome (${mime ?: "tipo sconosciuto"}, ${bytes.size / 1024} KB)"

        var ultimo: Exception? = null
        for (attesa in listOf(0L, 3_000L, 10_000L)) {
            if (attesa > 0) delay(attesa)
            var json: String? = null
            try {
                val risposta = withContext(Dispatchers.IO) {
                    client.models.generateContent(MODELLO, Content.fromParts(parte, Part.fromText(prompt)), config)
                }
                json = risposta.text() ?: throw IllegalStateException("Gemini non ha restituito nulla")
                val letta = interpreta(json)
                registra(richiesta, "${letta.voci.size} voci, mese ${letta.mese ?: "non riconosciuto"}.\n\n$json", false)
                return letta
            } catch (e: Exception) {
                ultimo = e
                registra(richiesta, (e.message ?: e.javaClass.simpleName) + (json?.let { "\n\nRisposta ricevuta:\n$it" } ?: ""), true)
            }
        }
        throw IllegalStateException(ultimo?.message?.take(200) ?: "Chiamata a Gemini non riuscita", ultimo)
    }

    private fun interpreta(json: String): BustaPagaLetta {
        val o = JSONObject(json)
        val mese = o.optString("mese").trim().let { m -> runCatching { YearMonth.parse(m.take(7)) }.getOrNull() }
        val array = o.optJSONArray("voci")
        val voci = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val v = array?.optJSONObject(i) ?: return@mapNotNull null
            val descrizione = v.optString("descrizione").trim().take(120)
            val importo = v.optDouble("importo", Double.NaN)
            if (descrizione.isEmpty() || importo.isNaN()) null
            else VoceBustaLetta(descrizione, importo.toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
        }
        return BustaPagaLetta(mese, voci)
    }

    private suspend fun registra(richiesta: String, risposta: String, errore: Boolean) = withContext(Dispatchers.IO) {
        registro?.registra(TipoChiamataGemini.BUSTA_PAGA, richiesta, risposta.take(60_000), errore)
    }

    private companion object {
        /** Stesso modello degli estratti conto. */
        const val MODELLO = "gemini-3.5-flash-lite"
    }
}
