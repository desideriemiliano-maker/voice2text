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
import kotlinx.coroutines.delay
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
        "dataOperazione (YYYY-MM-DD: data dell'operazione o della transazione, es. la data del pagamento con " +
        "carta; stringa vuota se il file non ha una colonna distinta per essa), " +
        "dataContabile (YYYY-MM-DD: data contabile o di registrazione; stringa vuota se assente o se il " +
        "movimento è \"non contabilizzato\"), " +
        "dataValuta (YYYY-MM-DD: data valuta; se il file ha una sola data usa quella), " +
        "valuta (codice ISO a 3 lettere della valuta in cui il movimento è addebitato/accreditato SUL CONTO, " +
        "cioè quella della colonna Valuta o del conto; NON la valuta estera eventualmente citata nella " +
        "descrizione, es. \"EUR 28.17 / Corso CHF/EUR\" in un conto in CHF resta CHF), " +
        "importo (sempre nella valuta del conto, mai l'importo in valuta estera citato nella descrizione; " +
        "numero con segno: NEGATIVO per addebiti/uscite, POSITIVO per accrediti/entrate; se il file ha " +
        "colonne separate Dare/Avere o Addebiti/Accrediti applica tu il segno), " +
        "descrizione (breve, massimo 150 caratteri: il testo descrittivo del movimento così come appare, esercente, " +
        "beneficiario o causale; se ci " +
        "sono più colonne descrittive, es. Descrizione e Dettaglio, uniscile nell'ordine separate da \" · \"). " +
        "riga (numero intero: il numero tra parentesi quadre all'inizio della riga da cui proviene il movimento, " +
        "0 se le righe non sono numerate). " +
        "Le date nei fogli di calcolo sono già convertite in formato YYYY-MM-DD."

/** Movimenti letti da un estratto conto, con gli eventuali problemi (blocchi non letti o incompleti). */
data class EsitoEstrazione(val movimenti: List<MovimentoEstratto>, val avvisi: List<String>)

/** Quale data di un movimento registrare sull'operazione (scelta nel pannello di import). */
enum class TipoData(val etichetta: String) {
    OPERAZIONE("Operazione"),
    CONTABILE("Contabile"),
    VALUTA("Valuta")
}

/**
 * Un movimento letto da Gemini dall'estratto conto, con le date che l'estratto riporta:
 * operazione/transazione, contabile/registrazione, valuta. L'utente sceglie quale registrare
 * ([data]); per riconoscere le operazioni già presenti si confrontano tutte ([tutteLeDate]).
 */
data class MovimentoEstratto(
    val valuta: String,
    val importoCent: Long,
    val descrizione: String,
    val dataOperazione: LocalDate? = null,
    val dataContabile: LocalDate? = null,
    val dataValuta: LocalDate? = null,
    /** Numero della riga del file da cui proviene (per mantenere l'ordine dell'estratto conto). */
    val rigaFile: Int? = null,
    /** Movimento senza data contabile ("non contabilizzato"): ha la data di oggi, da sanare in seguito. */
    val nonContabilizzato: Boolean = false
) {
    fun dataDi(tipo: TipoData): LocalDate? = when (tipo) {
        TipoData.OPERAZIONE -> dataOperazione
        TipoData.CONTABILE -> dataContabile
        TipoData.VALUTA -> dataValuta
    }

    /** La data di tipo [tipo] o, se il movimento non la riporta, la prima disponibile (operazione, valuta, contabile). */
    fun data(tipo: TipoData): LocalDate = dataDi(tipo) ?: dataOperazione ?: dataValuta ?: dataContabile!!

    val tutteLeDate: List<LocalDate> get() = listOfNotNull(dataOperazione, dataContabile, dataValuta).distinct()
}

private fun schemaMovimenti(): Schema {
    val movimento = Schema.builder()
        .type(Type.Known.OBJECT)
        .properties(
            mapOf(
                "dataOperazione" to Schema.builder().type(Type.Known.STRING).description("data operazione/transazione, YYYY-MM-DD, o vuota").build(),
                "dataValuta" to Schema.builder().type(Type.Known.STRING).description("data valuta, YYYY-MM-DD").build(),
                "dataContabile" to Schema.builder().type(Type.Known.STRING).description("data contabile, YYYY-MM-DD, o vuota").build(),
                "valuta" to Schema.builder().type(Type.Known.STRING).description("codice valuta ISO, es. EUR").build(),
                "importo" to Schema.builder().type(Type.Known.NUMBER).description("importo con segno, negativo per le uscite").build(),
                "descrizione" to Schema.builder().type(Type.Known.STRING).description("descrizione del movimento").build(),
                "riga" to Schema.builder().type(Type.Known.INTEGER).description("numero tra [ ] della riga di origine, 0 se assente").build()
            )
        )
        .required("dataOperazione", "dataContabile", "dataValuta", "valuta", "importo", "descrizione", "riga")
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
    ): EsitoEstrazione {
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

        val avvisi = ArrayList<String>()
        if (testo == null) {
            val (movimenti, completa) = chiamaConRiprova(
                client, config, listOf(parteBinaria!!, Part.fromText(prompt)),
                "$prompt\n\n$descrizioneFile\n\nFile allegato così com'è.", valutaPredefinita
            )
            if (!completa) avvisi += "Risposta di Gemini incompleta: alcuni movimenti potrebbero mancare."
            return EsitoEstrazione(movimenti, avvisi)
        }

        // Testo: righe di intestazione (fino alla riga con i nomi delle colonne) ripetute come contesto
        // e movimenti divisi in blocchi, così ogni risposta di Gemini resta breve e completa.
        val righe = testo.lines().filter { riga -> riga.replace("|", "").isNotBlank() }
        val indiceIntestazione = righe.take(RIGHE_INTESTAZIONE_MAX).indexOfFirst {
            !it.startsWith("Nota:") && RIGA_INTESTAZIONE.containsMatchIn(it) && !DATA_NEL_TESTO.containsMatchIn(it)
        }
        val intestazione = if (indiceIntestazione >= 0) righe.take(indiceIntestazione + 1) else emptyList()
        // Ogni riga di movimenti è numerata ("[N] ..."): Gemini restituisce il numero e l'elenco viene
        // riportato all'ordine del file, anche per più movimenti con la stessa data.
        val blocchi = righe.drop(intestazione.size)
            .mapIndexed { i, riga -> "[${i + 1}] $riga" }
            .chunked(RIGHE_PER_BLOCCO)
            .ifEmpty { listOf(emptyList()) }

        /** Un blocco (o parte di blocco): se la risposta è interrotta lo si divide a metà e si riprova. */
        suspend fun elaboraBlocco(blocco: List<String>, etichetta: String): List<MovimentoEstratto> {
            val testoBlocco = buildString {
                if (intestazione.isNotEmpty()) {
                    append("Intestazione del file (solo per capire le colonne, NON contiene movimenti da estrarre):\n")
                    intestazione.forEach { append(it).append('\n') }
                    append('\n')
                }
                append("Righe da elaborare ($etichetta):\n")
                blocco.forEach { append(it).append('\n') }
            }
            val (movimenti, completa) = chiamaConRiprova(
                client, config,
                listOf(Part.fromText(testoBlocco), Part.fromText(prompt)),
                "$prompt\n\n$descrizioneFile · $etichetta\n\n$testoBlocco",
                valutaPredefinita
            )
            if (completa) return movimenti
            if (blocco.size <= RIGHE_MINIME_DIVISIONE) {
                avvisi += "Risposta incompleta per $etichetta: alcuni movimenti potrebbero mancare."
                return movimenti
            }
            val meta = blocco.size / 2
            return elaboraBlocco(blocco.take(meta), "$etichetta, parte 1") + elaboraBlocco(blocco.drop(meta), "$etichetta, parte 2")
        }

        val movimenti = ArrayList<MovimentoEstratto>()
        var riusciti = 0
        var ultimoErrore: Exception? = null
        blocchi.forEachIndexed { i, blocco ->
            onAvanzamento(i + 1, blocchi.size)
            try {
                movimenti += elaboraBlocco(blocco, "blocco ${i + 1} di ${blocchi.size}")
                riusciti++
            } catch (e: Exception) {
                // Un blocco non letto non ferma l'import: gli altri movimenti restano disponibili.
                ultimoErrore = e
                avvisi += "Blocco ${i + 1} di ${blocchi.size} non letto (${blocco.size} righe, dalla riga «${blocco.firstOrNull()?.take(60)}»): " +
                    (e.message ?: e.javaClass.simpleName)
            }
        }
        if (riusciti == 0) throw ultimoErrore ?: IllegalStateException("Nessun movimento letto")
        // Ordine delle righe del file (ordinamento stabile: senza numero restano nell'ordine di arrivo).
        return EsitoEstrazione(movimenti.sortedWith(compareBy(nullsLast<Int>()) { it.rigaFile }), avvisi)
    }

    /** [chiama] con fino a 2 nuovi tentativi (attese crescenti) per gli errori temporanei di Gemini. */
    private suspend fun chiamaConRiprova(
        client: Client,
        config: GenerateContentConfig,
        parti: List<Part>,
        richiesta: String,
        valutaPredefinita: String
    ): Pair<List<MovimentoEstratto>, Boolean> {
        var ultimo: Exception? = null
        for ((tentativo, attesa) in ATTESE_RIPROVA_MS.withIndex()) {
            if (attesa > 0) delay(attesa)
            try {
                val suffisso = if (tentativo > 0) "\n\n(tentativo ${tentativo + 1})" else ""
                return chiama(client, config, parti, richiesta + suffisso, valutaPredefinita)
            } catch (e: Exception) {
                ultimo = e
            }
        }
        throw ultimo ?: IllegalStateException("Chiamata a Gemini non riuscita")
    }

    /** Una chiamata a Gemini, registrata nel registro con la risposta originale. */
    private suspend fun chiama(
        client: Client,
        config: GenerateContentConfig,
        parti: List<Part>,
        richiesta: String,
        valutaPredefinita: String
    ): Pair<List<MovimentoEstratto>, Boolean> {
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
            return movimenti to completa
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
            val dataOperazione = leggiData("dataOperazione")
            val dataContabile = leggiData("dataContabile")
            val dataValuta = leggiData("dataValuta") ?: leggiData("data")
            if (dataOperazione == null && dataContabile == null && dataValuta == null) return@mapNotNull null
            val importo = o.opt("importo")?.toString()?.replace(',', '.')?.toBigDecimalOrNull() ?: return@mapNotNull null
            MovimentoEstratto(
                valuta = o.optString("valuta").trim().uppercase().ifEmpty { valutaPredefinita },
                importoCent = importo.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong(),
                descrizione = o.optString("descrizione").trim().replace(Regex("\\s+"), " ").take(MAX_DESCRIZIONE),
                dataOperazione = dataOperazione,
                dataContabile = dataContabile,
                dataValuta = dataValuta,
                rigaFile = o.optInt("riga", 0).takeIf { it > 0 }
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
        const val RIGHE_PER_BLOCCO = 40
        /** Sotto questo numero di righe un blocco con risposta interrotta non viene più diviso. */
        const val RIGHE_MINIME_DIVISIONE = 5
        /** Attesa prima di ogni tentativo (il primo subito). */
        val ATTESE_RIPROVA_MS = listOf(0L, 3_000L, 10_000L)
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
    val giorniDistanza: Int = 0,
    /** Operazione del database che corrisponde al movimento (per aggiornarne la data), se presente. */
    val esistente: com.desideri.familybalance.data.Operazione? = null,
    /** Ordine nella giornata dalla posizione nel file (crescente = più vecchia). */
    val ordine: Long = 0
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
    val righe: List<RigaEstratto>,
    /** Problemi della lettura con Gemini (blocchi non letti o incompleti), mostrati nel pannello. */
    val avvisi: List<String> = emptyList()
) {
    val presenti: Int get() = righe.count { it.presenza == Presenza.PRESENTE }
    val simili: Int get() = righe.count { it.presenza == Presenza.SIMILE }
}

/**
 * Aggiornamento di un'operazione già presente: [nuovaData] se l'utente ha scelto di cambiarla (null =
 * data invariata) e [ordine] dell'estratto conto, per rispettarne l'ordine nella giornata.
 */
data class AggiornamentoData(
    val operazione: com.desideri.familybalance.data.Operazione,
    val nuovaData: LocalDate?,
    val ordine: Long?
)

/** Scelta dell'utente per un movimento: data, tipo, o "Spostamento" verso [destinazioneId]. */
data class SceltaEstratto(
    val riga: RigaEstratto,
    val data: LocalDate,
    val tipo: String,
    val destinazioneId: Long?
)
