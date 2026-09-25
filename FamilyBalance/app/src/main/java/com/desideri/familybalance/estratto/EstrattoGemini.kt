package com.desideri.familybalance.estratto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.desideri.familybalance.importazione.LettoreXlsx
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.RoundingMode
import java.time.LocalDate

/** Stesso modello usato in Voice2Text. */
private const val GEMINI_MODEL = "gemini-3.5-flash-lite"

private const val PROMPT_ESTRATTO =
    "Questo è l'estratto conto di un conto corrente. Estrai TUTTI i movimenti (le singole operazioni), " +
        "ignorando saldi iniziali/finali, totali, intestazioni e righe riepilogative. Per ogni movimento indica: " +
        "data (formato YYYY-MM-DD; se ci sono data contabile e data valuta usa la data dell'operazione/contabile), " +
        "valuta (codice ISO a 3 lettere, es. EUR o CHF; se non indicata usa la valuta del conto), " +
        "importo (numero con segno: NEGATIVO per addebiti/uscite, POSITIVO per accrediti/entrate; se il file ha " +
        "colonne separate Dare/Avere o Addebiti/Accrediti applica tu il segno), " +
        "descrizione (il testo descrittivo del movimento così come appare: esercente, beneficiario o causale). " +
        "Le date nei fogli di calcolo sono già convertite in formato YYYY-MM-DD."

/** Un movimento letto da Gemini dall'estratto conto. */
data class MovimentoEstratto(
    val data: LocalDate,
    val valuta: String,
    val importoCent: Long,
    val descrizione: String
)

private fun schemaMovimenti(): Schema {
    val movimento = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            mapOf(
                "data" to Schema.builder().type(Type.Known.STRING).description("data del movimento, YYYY-MM-DD").build(),
                "valuta" to Schema.builder().type(Type.Known.STRING).description("codice valuta ISO, es. EUR").build(),
                "importo" to Schema.builder().type(Type.Known.NUMBER).description("importo con segno, negativo per le uscite").build(),
                "descrizione" to Schema.builder().type(Type.Known.STRING).description("descrizione del movimento").build()
            )
        )
        .required("data", "valuta", "importo", "descrizione")
        .build()
    return Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(mapOf("movimenti" to Schema.builder().type(Type.Known.ARRAY).items(movimento).build()))
        .required("movimenti")
        .build()
}

/**
 * Manda l'estratto conto a Gemini e ne ricava i movimenti (risposta JSON con schema, come in
 * Voice2Text). Gemini non accetta i file .xlsx: i fogli Excel vengono convertiti in testo (con le
 * date già in formato ISO); PDF e immagini sono inviati così come sono, CSV/testo come testo.
 */
class EstrattoGemini(private val apiKey: String, private val registro: RegistroPromptStore? = null) {

    suspend fun estrai(context: Context, uri: Uri, valutaPredefinita: String): List<MovimentoEstratto> {
        if (apiKey.isBlank()) throw IllegalStateException("Chiave Gemini non configurata in questa build")
        val (nome, mime) = infoFile(context, uri)
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Impossibile leggere il file")
        }
        val nomeMinuscolo = nome.lowercase()
        // Testo inviato al posto del file (fogli di calcolo e CSV), riportato anche nel registro.
        var testoInviato: String? = null
        val parteFile: Part = when {
            nomeMinuscolo.endsWith(".xlsx") || mime == MIME_XLSX -> withContext(Dispatchers.Default) {
                LettoreXlsx(ByteArrayInputStream(bytes)).comeTesto().also { testoInviato = it }.let { Part.fromText(it) }
            }
            nomeMinuscolo.endsWith(".xls") || mime == "application/vnd.ms-excel" ->
                throw IllegalArgumentException("Formato .xls non supportato: salva il file come .xlsx, .csv o PDF")
            mime == "application/pdf" || nomeMinuscolo.endsWith(".pdf") -> Part.fromBytes(bytes, "application/pdf")
            mime?.startsWith("image/") == true -> Part.fromBytes(bytes, mime ?: "image/jpeg")
            else -> String(bytes, Charsets.UTF_8).also { testoInviato = it }.let { Part.fromText(it) }
        }
        val prompt = "$PROMPT_ESTRATTO La valuta del conto è $valutaPredefinita."
        val richiesta = buildString {
            append(prompt).append("\n\nFile: ").append(nome).append(" (").append(mime ?: "tipo sconosciuto")
            append(", ").append(bytes.size / 1024).append(" KB)")
            testoInviato?.let { append("\n\nContenuto inviato come testo:\n").append(tronca(it)) }
                ?: append("\n\nFile allegato così com'è.")
        }

        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(schemaMovimenti())
            .build()
        val client = Client.builder().apiKey(apiKey).build()
        try {
            val risposta = withContext(Dispatchers.IO) {
                client.models.generateContent(GEMINI_MODEL, Content.fromParts(parteFile, Part.fromText(prompt)), config)
            }
            val json = risposta.text() ?: throw IllegalStateException("Gemini non ha restituito alcun movimento")
            val movimenti = interpreta(json, valutaPredefinita)
            registra(richiesta, "${movimenti.size} movimenti interpretati.\n\n${tronca(json)}", errore = false)
            return movimenti
        } catch (e: Exception) {
            registra(richiesta, e.message ?: e.javaClass.simpleName, errore = true)
            throw e
        }
    }

    private suspend fun registra(richiesta: String, risposta: String, errore: Boolean) = withContext(Dispatchers.IO) {
        registro?.registra(TipoChiamataGemini.ESTRATTO_CONTO, richiesta, risposta, errore)
    }

    /** Limita i testi molto lunghi nel registro (il file del registro resta leggibile). */
    private fun tronca(testo: String, max: Int = 60_000): String =
        if (testo.length <= max) testo else testo.take(max) + "\n… (troncato, ${testo.length} caratteri in totale)"

    private fun interpreta(json: String, valutaPredefinita: String): List<MovimentoEstratto> {
        val array = JSONObject(json).optJSONArray("movimenti") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val data = try {
                LocalDate.parse(o.optString("data").trim().take(10))
            } catch (e: Exception) {
                return@mapNotNull null
            }
            val importo = o.opt("importo")?.toString()?.replace(',', '.')?.toBigDecimalOrNull() ?: return@mapNotNull null
            MovimentoEstratto(
                data = data,
                valuta = o.optString("valuta").trim().uppercase().ifEmpty { valutaPredefinita },
                importoCent = importo.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong(),
                descrizione = o.optString("descrizione").trim().replace(Regex("\\s+"), " ")
            )
        }.filter { it.importoCent != 0L }
    }

    private fun infoFile(context: Context, uri: Uri): Pair<String, String?> {
        val mime = context.contentResolver.getType(uri)
        val nome = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        return nome to mime
    }

    private companion object {
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}

/** Un movimento dell'estratto conto da importare (non già presente esattamente una volta). */
data class RigaEstratto(
    val id: Int,
    val movimento: MovimentoEstratto,
    val contoValutaId: Long,
    /** Associazioni la cui chiave compare nella descrizione, una per destinazione. */
    val candidate: List<com.desideri.familybalance.data.Associazione>,
    /** Operazioni già presenti con stessa data e importo (0, o più di una se ambiguo). */
    val giaPresenti: Int
)

/** Import di un estratto conto in attesa delle scelte dell'utente. */
data class ImportEstratto(
    val contoId: Long,
    val righe: List<RigaEstratto>,
    val saltate: Int,
    val totali: Int
)

/** Scelta dell'utente per un movimento: tipo/sottotipo, o "Spostamento" verso [destinazioneId]. */
data class SceltaEstratto(
    val riga: RigaEstratto,
    val tipo: String,
    val sottotipo: String?,
    val destinazioneId: Long?
)
