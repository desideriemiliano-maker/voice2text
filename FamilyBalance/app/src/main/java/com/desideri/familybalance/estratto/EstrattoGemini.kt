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
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.RoundingMode
import java.time.LocalDate

/** Stesso modello usato in Voice2Text. */
private const val GEMINI_MODEL = "gemini-3.5-flash-lite"

private const val PROMPT_ESTRATTO =
    "Questo è l'estratto conto di un conto corrente. Estrai TUTTI i movimenti (le singole operazioni), " +
        "ignorando saldi iniziali/finali, totali, intestazioni e righe riepilogative. Per ogni movimento indica: " +
        "dataValuta (formato YYYY-MM-DD: la data valuta; se il file ha una sola data usa quella) e " +
        "dataContabile (formato YYYY-MM-DD: la data contabile, stringa vuota se assente o se il movimento è " +
        "\"non contabilizzato\"), " +
        "valuta (codice ISO a 3 lettere, es. EUR o CHF; se non indicata usa la valuta del conto), " +
        "importo (numero con segno: NEGATIVO per addebiti/uscite, POSITIVO per accrediti/entrate; se il file ha " +
        "colonne separate Dare/Avere o Addebiti/Accrediti applica tu il segno), " +
        "descrizione (breve, massimo 150 caratteri: il testo descrittivo del movimento così come appare, esercente, " +
        "beneficiario o causale; se ci " +
        "sono più colonne descrittive, es. Descrizione e Dettaglio, uniscile nell'ordine separate da \" · \"). " +
        "Le date nei fogli di calcolo sono già convertite in formato YYYY-MM-DD."

/**
 * Un movimento letto da Gemini dall'estratto conto. [data] è la data valuta (quella usata anche
 * nell'Excel delle spese), [dataContabile] l'eventuale data contabile: per riconoscere le
 * operazioni già presenti si confrontano entrambe.
 */
data class MovimentoEstratto(
    val data: LocalDate,
    val valuta: String,
    val importoCent: Long,
    val descrizione: String,
    val dataContabile: LocalDate? = null
)

private fun schemaMovimenti(): Schema {
    val movimento = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            mapOf(
                "dataValuta" to Schema.builder().type(Type.Known.STRING).description("data valuta, YYYY-MM-DD").build(),
                "dataContabile" to Schema.builder().type(Type.Known.STRING).description("data contabile, YYYY-MM-DD, o vuota").build(),
                "valuta" to Schema.builder().type(Type.Known.STRING).description("codice valuta ISO, es. EUR").build(),
                "importo" to Schema.builder().type(Type.Known.NUMBER).description("importo con segno, negativo per le uscite").build(),
                "descrizione" to Schema.builder().type(Type.Known.STRING).description("descrizione del movimento").build()
            )
        )
        .required("dataValuta", "dataContabile", "valuta", "importo", "descrizione")
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

    /** [onAvanzamento] riceve (blocco corrente, blocchi totali) quando il file viene inviato a blocchi. */
    suspend fun estrai(
        context: Context,
        uri: Uri,
        valutaPredefinita: String,
        onAvanzamento: (Int, Int) -> Unit = { _, _ -> }
    ): List<MovimentoEstratto> {
        if (apiKey.isBlank()) throw IllegalStateException("Chiave Gemini non configurata in questa build")
        val (nome, mime) = infoFile(context, uri)
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Impossibile leggere il file")
        }
        val nomeMinuscolo = nome.lowercase()
        // Fogli di calcolo e CSV vanno come testo (a blocchi), PDF e immagini come file.
        val testo: String?
        val parteBinaria: Part?
        when {
            nomeMinuscolo.endsWith(".xlsx") || mime == MIME_XLSX -> {
                testo = withContext(Dispatchers.Default) { LettoreXlsx(ByteArrayInputStream(bytes)).comeTesto() }
                parteBinaria = null
            }
            nomeMinuscolo.endsWith(".xls") || mime == "application/vnd.ms-excel" ->
                throw IllegalArgumentException("Formato .xls non supportato: salva il file come .xlsx, .csv o PDF")
            mime == "application/pdf" || nomeMinuscolo.endsWith(".pdf") -> {
                testo = null
                parteBinaria = Part.fromBytes(bytes, "application/pdf")
            }
            mime?.startsWith("image/") == true -> {
                testo = null
                parteBinaria = Part.fromBytes(bytes, mime ?: "image/jpeg")
            }
            else -> {
                testo = String(bytes, Charsets.UTF_8)
                parteBinaria = null
            }
        }
        val prompt = "$PROMPT_ESTRATTO La valuta del conto è $valutaPredefinita."
        val descrizioneFile = "File: $nome (${mime ?: "tipo sconosciuto"}, ${bytes.size / 1024} KB)"

        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(schemaMovimenti())
            .temperature(0f)
            .maxOutputTokens(MAX_TOKEN_RISPOSTA)
            .build()
        val client = Client.builder().apiKey(apiKey).build()

        if (testo == null) {
            return chiama(client, config, listOf(parteBinaria!!, Part.fromText(prompt)), "$prompt\n\n$descrizioneFile\n\nFile allegato così com'è.", valutaPredefinita)
        }

        // Testo: righe di intestazione (fino alla riga con i nomi delle colonne) ripetute come contesto
        // e movimenti divisi in blocchi, così ogni risposta di Gemini resta breve e completa.
        val righe = testo.lines().filter { riga -> riga.replace("|", "").isNotBlank() }
        val indiceIntestazione = righe.take(RIGHE_INTESTAZIONE_MAX).indexOfFirst {
            RIGA_INTESTAZIONE.containsMatchIn(it) && !DATA_NEL_TESTO.containsMatchIn(it)
        }
        val intestazione = if (indiceIntestazione >= 0) righe.take(indiceIntestazione + 1) else emptyList()
        val blocchi = righe.drop(intestazione.size).chunked(RIGHE_PER_BLOCCO).ifEmpty { listOf(emptyList()) }
        val movimenti = ArrayList<MovimentoEstratto>()
        blocchi.forEachIndexed { i, blocco ->
            onAvanzamento(i + 1, blocchi.size)
            val testoBlocco = buildString {
                if (intestazione.isNotEmpty()) {
                    append("Intestazione del file (solo per capire le colonne, NON contiene movimenti da estrarre):\n")
                    intestazione.forEach { append(it).append('\n') }
                    append('\n')
                }
                append("Righe da elaborare (blocco ${i + 1} di ${blocchi.size}):\n")
                blocco.forEach { append(it).append('\n') }
            }
            movimenti += chiama(
                client, config,
                listOf(Part.fromText(testoBlocco), Part.fromText(prompt)),
                "$prompt\n\n$descrizioneFile · blocco ${i + 1} di ${blocchi.size}\n\n$testoBlocco",
                valutaPredefinita
            )
        }
        return movimenti
    }

    /** Una chiamata a Gemini, registrata nel registro con la risposta originale. */
    private suspend fun chiama(
        client: Client,
        config: GenerateContentConfig,
        parti: List<Part>,
        richiesta: String,
        valutaPredefinita: String
    ): List<MovimentoEstratto> {
        var json: String? = null
        try {
            val risposta = withContext(Dispatchers.IO) {
                client.models.generateContent(GEMINI_MODEL, Content.fromParts(*parti.toTypedArray()), config)
            }
            json = risposta.text() ?: throw IllegalStateException("Gemini non ha restituito alcun movimento")
            val (movimenti, completa) = interpreta(json, valutaPredefinita)
            val esito = if (completa) "${movimenti.size} movimenti interpretati."
            else "Risposta incompleta (JSON interrotto): recuperati ${movimenti.size} movimenti completi."
            registra(richiesta, "$esito\n\n${tronca(json)}", errore = !completa)
            return movimenti
        } catch (e: Exception) {
            val dettaglio = e.message?.take(500) ?: e.javaClass.simpleName
            registra(richiesta, dettaglio + (json?.let { "\n\nRisposta ricevuta:\n${tronca(it)}" } ?: ""), errore = true)
            throw IllegalStateException(dettaglio.take(200), e)
        }
    }

    private suspend fun registra(richiesta: String, risposta: String, errore: Boolean) = withContext(Dispatchers.IO) {
        registro?.registra(TipoChiamataGemini.ESTRATTO_CONTO, tronca(richiesta), risposta, errore)
    }

    /** Limita i testi molto lunghi nel registro (il file del registro resta leggibile). */
    private fun tronca(testo: String, max: Int = 60_000): String =
        if (testo.length <= max) testo else testo.take(max) + "\n… (troncato, ${testo.length} caratteri in totale)"

    /**
     * Movimenti dalla risposta JSON, e se la risposta era completa. Se il JSON è interrotto (risposta
     * troppo lunga) si recuperano comunque i movimenti completi, uno per oggetto {…}.
     */
    private fun interpreta(json: String, valutaPredefinita: String): Pair<List<MovimentoEstratto>, Boolean> {
        val (oggetti, completa) = try {
            val array = JSONObject(json).optJSONArray("movimenti")
            (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) } to true
        } catch (e: JSONException) {
            Regex("\\{[^{}]*\\}").findAll(json).mapNotNull { runCatching { JSONObject(it.value) }.getOrNull() }.toList() to false
        }
        val movimenti = oggetti.mapNotNull { o ->
            fun leggiData(campo: String): LocalDate? = try {
                LocalDate.parse(o.optString(campo).trim().take(10))
            } catch (e: Exception) {
                null
            }
            val dataContabile = leggiData("dataContabile")
            val data = leggiData("dataValuta") ?: leggiData("data") ?: dataContabile ?: return@mapNotNull null
            val importo = o.opt("importo")?.toString()?.replace(',', '.')?.toBigDecimalOrNull() ?: return@mapNotNull null
            MovimentoEstratto(
                data = data,
                valuta = o.optString("valuta").trim().uppercase().ifEmpty { valutaPredefinita },
                importoCent = importo.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong(),
                descrizione = o.optString("descrizione").trim().replace(Regex("\\s+"), " ").take(MAX_DESCRIZIONE),
                dataContabile = dataContabile?.takeIf { it != data }
            )
        }.filter { it.importoCent != 0L }
        return movimenti to completa
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
        /** Righe di movimenti per ogni chiamata a Gemini. */
        const val RIGHE_PER_BLOCCO = 60
        /** Entro quante righe iniziali cercare quella con i nomi delle colonne. */
        const val RIGHE_INTESTAZIONE_MAX = 30
        const val MAX_TOKEN_RISPOSTA = 16_384
        const val MAX_DESCRIZIONE = 200
        /** Riga con i nomi delle colonne di un estratto conto. */
        val RIGA_INTESTAZIONE = Regex("(?i)importo|dare|avere|addebit|accredit|amount")
        /** Una data (le righe di movimento ne hanno sempre una, l'intestazione no). */
        val DATA_NEL_TESTO = Regex("\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}")
    }
}

/** Un movimento dell'estratto conto da importare (non già presente esattamente una volta). */
data class RigaEstratto(
    val id: Int,
    val movimento: MovimentoEstratto,
    val contoValutaId: Long,
    /** Associazioni la cui chiave compare nella descrizione, una per destinazione. */
    val candidate: List<com.desideri.familybalance.data.Associazione>,
    /** Se il movimento sembra già registrato sul conto (stesso importo, data uguale o vicina). */
    val presenza: Presenza = Presenza.NUOVA,
    /** Per [Presenza.SIMILE]: giorni di distanza dell'operazione più vicina. */
    val giorniDistanza: Int = 0
) {
    /** Più associazioni corrispondono alla descrizione: l'utente deve scegliere. */
    val piuCandidati: Boolean get() = candidate.size > 1
}

enum class Presenza {
    /** Nessuna operazione con lo stesso importo in date vicine. */
    NUOVA,
    /** Operazione con lo stesso importo e stessa data valuta o contabile. */
    PRESENTE,
    /** Operazione con lo stesso importo entro pochi giorni: possibile doppione. */
    SIMILE
}

/** Import di un estratto conto in attesa delle scelte dell'utente. */
data class ImportEstratto(
    val contoId: Long,
    val righe: List<RigaEstratto>
) {
    val presenti: Int get() = righe.count { it.presenza == Presenza.PRESENTE }
    val simili: Int get() = righe.count { it.presenza == Presenza.SIMILE }
}

/** Scelta dell'utente per un movimento: tipo/sottotipo, o "Spostamento" verso [destinazioneId]. */
data class SceltaEstratto(
    val riga: RigaEstratto,
    val tipo: String,
    val sottotipo: String?,
    val destinazioneId: Long?
)
