package com.desideri.familybalance.importazione

import androidx.room.withTransaction
import com.desideri.familybalance.data.AppDatabase
import com.desideri.familybalance.data.Conto
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.data.Voce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/** Spesa ricorrente letta da una colonna del foglio Bollette dell'Excel. */
data class RicorrenteExcel(
    val nome: String,
    val mesi: Int,
    val meseInizio: YearMonth?,
    /** Ultimo importo pagato nella colonna: previsione di ripiego se nessuna operazione le viene associata. */
    val ultimoImportoCent: Long?
)

/** Una combinazione tipo/sottotipo "Bollette" trovata nei conti, da associare a una spesa ricorrente. */
data class CombinazioneBollette(
    val chiave: String,
    val tipo: String,
    val sottotipo: String?,
    val operazioni: Int,
    val totaliPerValuta: Map<String, Long>,
    val prima: LocalDate,
    val ultima: LocalDate,
    /** Ricorrente del foglio Bollette con nome compatibile, proposta come scelta iniziale. */
    val suggerimento: String?
)

/** Scelta per una combinazione Bollette: una ricorrente ("R:<nome>") o [NON_RICORRENTE]. */
object SceltaBollette {
    const val NON_RICORRENTE = "B"
    private const val PREFISSO_RICORRENTE = "R:"

    fun ricorrente(nome: String) = PREFISSO_RICORRENTE + nome

    fun nomeRicorrente(scelta: String): String? =
        if (scelta.startsWith(PREFISSO_RICORRENTE)) scelta.removePrefix(PREFISSO_RICORRENTE) else null
}

internal data class RigaExcel(
    val conto: String,
    val valuta: String,
    val data: LocalDate,
    val importoCent: Long,
    val tipo: String?,
    val sottotipo: String?,
    val saldoCent: Long?
) {
    val spostamento: Boolean get() = tipo?.equals("Spostamento", ignoreCase = true) == true
    val bolletta: Boolean get() = !spostamento && tipo?.equals("Bollette", ignoreCase = true) == true
}

/** Risultato della prima fase dell'importazione: il file letto, in attesa delle scelte sulle Bollette. */
class AnalisiImport internal constructor(
    internal val righe: List<RigaExcel>,
    val ricorrenti: List<RicorrenteExcel>,
    val combinazioni: List<CombinazioneBollette>,
    val targetRisparmioCent: Long?
)

/**
 * Importa l'Excel delle spese ("Dettagli.xlsx") sostituendo tutti i dati dell'app, in due fasi:
 * [analizza] legge il file, [scrivi] salva i dati con le scelte dell'utente sulle Bollette.
 *
 * - fogli **HelloBank** (colonne H-K: Data, Importo, TIPO, SOTTOTIPO) e **LGT** (colonne I-M: Data,
 *   Valuta, Importo, TIPO, SOTTOTIPO) -> conti, operazioni e anagrafica voci. Le colonne sono
 *   cercate per intestazione nella riga 1, con queste lettere come ripiego;
 * - saldo iniziale di ogni conto/valuta = saldo progressivo della prima riga meno il suo importo;
 * - TIPO "Spostamento" -> trasferimento, con conto di destinazione ricavato abbinando le righe
 *   speculari sugli altri conti (stessa data circa, segno opposto);
 * - TIPO "Stipendio"/"Interessi" -> voci di entrata;
 * - le spese ricorrenti sono SOLO quelle del foglio **Bollette** (riga 1 nome, riga 2 ogni quanti
 *   mesi, colonna precedente il contatore del mese): ognuna diventa un tipo ricorrente. Se il nome
 *   coincide con un tipo normale già usato (es. "Autostrada") si aggiunge " (ricorrente)";
 * - le operazioni con TIPO "Bollette" vanno sulla ricorrente scelta dall'utente per la loro
 *   combinazione tipo/sottotipo ([CombinazioneBollette]), o restano "Bollette / sottotipo" non
 *   ricorrenti;
 * - foglio **Impostazioni**: "Risparmio target".
 */
class ImportatoreExcel(private val db: AppDatabase) {

    data class Esito(
        val operazioni: Int,
        val voci: Int,
        val vociRicorrenti: Int,
        val spostamentiAbbinati: Int,
        val targetRisparmioCent: Long?
    )

    suspend fun analizza(input: InputStream, oggi: YearMonth = YearMonth.now()): AnalisiImport = withContext(Dispatchers.IO) {
        val xlsx = LettoreXlsx(input)
        val helloBank = xlsx.foglio("HelloBank") ?: throw IllegalArgumentException("Foglio \"HelloBank\" non trovato nel file")
        val lgt = xlsx.foglio("LGT") ?: throw IllegalArgumentException("Foglio \"LGT\" non trovato nel file")
        val righe = leggiHelloBank(helloBank) + leggiLgt(lgt)
        if (righe.isEmpty()) throw IllegalArgumentException("Nessuna operazione trovata nei fogli HelloBank e LGT")

        val ricorrenti = xlsx.foglio("Bollette")?.let { leggiRicorrenti(it, oggi) }.orEmpty()
        val combinazioni = righe.filter { it.bolletta }
            .groupBy { chiaveBollette(it.tipo!!, it.sottotipo) }
            .map { (chiave, lista) ->
                val sottotipo = lista.mapNotNull { it.sottotipo }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                CombinazioneBollette(
                    chiave = chiave,
                    tipo = lista.map { it.tipo!! }.groupingBy { it }.eachCount().maxBy { it.value }.key,
                    sottotipo = sottotipo,
                    operazioni = lista.size,
                    totaliPerValuta = lista.groupBy { it.valuta }.mapValues { (_, l) -> l.sumOf { it.importoCent } },
                    prima = lista.minOf { it.data },
                    ultima = lista.maxOf { it.data },
                    suggerimento = sottotipo?.let { suggerisci(it, ricorrenti) }
                )
            }
            .sortedBy { (it.sottotipo ?: "").lowercase() }

        AnalisiImport(righe, ricorrenti, combinazioni, xlsx.foglio("Impostazioni")?.let { leggiTarget(it) })
    }

    /**
     * Seconda fase: sostituisce i dati dell'app con quelli analizzati. [scelte]: chiave della
     * combinazione Bollette -> [SceltaBollette]; le combinazioni senza scelta restano non ricorrenti.
     */
    suspend fun scrivi(analisi: AnalisiImport, scelte: Map<String, String>): Esito = withContext(Dispatchers.IO) {
        val righe = analisi.righe

        // --- Anagrafica voci, normalizzando maiuscole/spazi (es. "regali" e "Regali") ---
        val righeConVoce = righe.filter { !it.spostamento && it.tipo != null }
        val tipoCanonico = canonici(righeConVoce.map { it.tipo!! })
        val sottotipoCanonico = HashMap<String, Map<String, String>>()
        righeConVoce.groupBy { it.tipo!!.lowercase() }.forEach { (tipoKey, lista) ->
            sottotipoCanonico[tipoKey] = canonici(lista.mapNotNull { it.sottotipo })
        }
        val tipiNormali = righeConVoce.filter { !it.bolletta }.map { it.tipo!!.lowercase() }.toSet()

        /** Nome della voce ricorrente, senza collisioni con i tipi normali (es. "Autostrada"). */
        fun nomeVoceRicorrente(nome: String): String = if (nome.lowercase() in tipiNormali) "$nome$SUFFISSO_RICORRENTE" else nome

        val voci = LinkedHashMap<Pair<String, String?>, Voce>()
        for (r in analisi.ricorrenti) {
            voci[nomeVoceRicorrente(r.nome) to null] = Voce(
                tipo = nomeVoceRicorrente(r.nome),
                ricorrente = true,
                mesiRicorrenza = r.mesi,
                meseInizio = r.meseInizio?.toString()
            )
        }

        fun chiaveVoce(r: RigaExcel): Pair<String, String?> {
            val tipoKey = r.tipo!!.lowercase()
            val sottotipo = r.sottotipo?.let { sottotipoCanonico[tipoKey]?.get(it.lowercase()) }
            if (r.bolletta) {
                val nomeRicorrente = scelte[chiaveBollette(r.tipo, r.sottotipo)]?.let { SceltaBollette.nomeRicorrente(it) }
                if (nomeRicorrente != null) return nomeVoceRicorrente(nomeRicorrente) to null
            }
            return tipoCanonico.getValue(tipoKey) to sottotipo
        }

        val chiaviRighe = righe.map { r -> if (r.spostamento || r.tipo == null) null else chiaveVoce(r) }
        righe.forEachIndexed { indice, r ->
            val chiave = chiaviRighe[indice] ?: return@forEachIndexed
            if (chiave !in voci) {
                // Ricorrente scelta ma assente dal foglio Bollette: mensile, senza previsione.
                val ricorrente = r.bolletta && chiave.second == null && scelte[chiaveBollette(r.tipo!!, r.sottotipo)]?.let { SceltaBollette.nomeRicorrente(it) } != null
                voci[chiave] = Voce(
                    tipo = chiave.first,
                    sottotipo = chiave.second,
                    entrata = chiave.first.lowercase() in TIPI_ENTRATA,
                    ricorrente = ricorrente
                )
            }
        }
        // Ricorrenti del foglio senza operazioni associate: si prevede l'ultimo importo della colonna.
        val chiaviUsate = chiaviRighe.filterNotNull().toSet()
        for (r in analisi.ricorrenti) {
            val chiave = nomeVoceRicorrente(r.nome) to null
            if (chiave !in chiaviUsate && r.ultimoImportoCent != null) {
                voci[chiave] = voci.getValue(chiave).copy(importoPrevistoCent = r.ultimoImportoCent)
            }
        }

        // --- Conti/valute ---
        val coppieContoValuta = LinkedHashSet<Pair<String, String>>()
        coppieContoValuta += CONTO_HELLOBANK to Valute.EUR
        coppieContoValuta += CONTO_LGT to Valute.EUR
        coppieContoValuta += CONTO_LGT to Valute.CHF
        righe.forEach { coppieContoValuta += it.conto to it.valuta }
        val saldiIniziali = righe.groupBy { it.conto to it.valuta }.mapValues { (_, lista) ->
            val prima = lista.first()
            (prima.saldoCent ?: prima.importoCent) - prima.importoCent
        }

        val destinazioni = abbinaSpostamenti(righe)

        var abbinati = 0
        db.withTransaction {
            val dao = db.dao()
            dao.svuotaOperazioni()
            dao.svuotaVoci()
            dao.svuotaContiValuta()
            dao.svuotaConti()

            val idConto = coppieContoValuta.map { it.first }.distinct().associateWith { dao.inserisciConto(Conto(nome = it)) }
            val idContoValuta = coppieContoValuta.associateWith { (conto, valuta) ->
                dao.inserisciContoValuta(ContoValuta(contoId = idConto.getValue(conto), valuta = valuta, saldoInizialeCent = saldiIniziali[conto to valuta] ?: 0L))
            }
            val idVoce = voci.mapValues { (_, voce) -> dao.inserisciVoce(voce) }

            val operazioni = righe.mapIndexed { indice, r ->
                val propria = r.conto to r.valuta
                if (r.spostamento) {
                    val abbinata = destinazioni[indice]
                    if (abbinata != null) abbinati++
                    val dest = abbinata?.let { righe[it].conto to righe[it].valuta } ?: destinazionePredefinita(r, coppieContoValuta)
                    Operazione(
                        contoValutaId = idContoValuta.getValue(propria),
                        data = r.data.toEpochDay(),
                        importoCent = r.importoCent,
                        trasferimento = true,
                        contoValutaDestId = dest?.let { idContoValuta[it] }
                    )
                } else {
                    Operazione(
                        contoValutaId = idContoValuta.getValue(propria),
                        data = r.data.toEpochDay(),
                        importoCent = r.importoCent,
                        voceId = chiaviRighe[indice]?.let { idVoce[it] }
                    )
                }
            }
            dao.inserisciOperazioni(operazioni)
        }

        Esito(
            operazioni = righe.size,
            voci = voci.size,
            vociRicorrenti = voci.values.count { it.ricorrente },
            spostamentiAbbinati = abbinati,
            targetRisparmioCent = analisi.targetRisparmioCent
        )
    }

    /**
     * Ricorrente del foglio con nome uguale al sottotipo o, in mancanza, l'unica con nome compatibile
     * (le parole di uno contenute nell'altro, es. "Affitto" -> "Affitto (Cantú)"). Nessun
     * suggerimento se le compatibili sono più d'una (es. "Sport" -> Sport Chiara / Sport Davide).
     */
    private fun suggerisci(sottotipo: String, ricorrenti: List<RicorrenteExcel>): String? {
        val s = normalizza(sottotipo)
        if (s.isEmpty()) return null
        ricorrenti.firstOrNull { normalizza(it.nome) == s }?.let { return it.nome }
        val parole = s.split(" ").toSet()
        val compatibili = ricorrenti.filter { r ->
            val n = normalizza(r.nome).split(" ").toSet()
            n.containsAll(parole) || parole.containsAll(n)
        }
        return compatibili.singleOrNull()?.nome
    }

    // --- Lettura fogli conti ---

    private fun colonna(foglio: Foglio, intestazione: String, predefinita: Int): Int =
        foglio[1]?.entries
            ?.filter { (it.value as? String)?.trim()?.equals(intestazione, ignoreCase = true) == true }
            ?.maxOfOrNull { it.key }
            ?: predefinita

    private fun leggiHelloBank(foglio: Foglio): List<RigaExcel> {
        val cData = colonna(foglio, "Data", 8)
        val cImporto = colonna(foglio, "Importo", 9)
        val cTipo = colonna(foglio, "TIPO", 10)
        val cSottotipo = colonna(foglio, "SOTTOTIPO", 11)
        val cSaldo = colonna(foglio, "Saldo", 4)
        return foglio.keys.filter { it > 1 }.sorted().mapNotNull { r ->
            val data = data(foglio.cella(r, cData)) ?: return@mapNotNull null
            val importo = foglio.numero(r, cImporto) ?: return@mapNotNull null
            RigaExcel(
                conto = CONTO_HELLOBANK,
                valuta = Valute.EUR,
                data = data,
                importoCent = cent(importo),
                tipo = pulisci(foglio.testo(r, cTipo)),
                sottotipo = pulisci(foglio.testo(r, cSottotipo)),
                saldoCent = foglio.numero(r, cSaldo)?.let { cent(it) }
            )
        }
    }

    private fun leggiLgt(foglio: Foglio): List<RigaExcel> {
        val cData = colonna(foglio, "Data", 9)
        val cValuta = colonna(foglio, "Valuta", 10)
        val cImporto = colonna(foglio, "Importo", 11)
        val cTipo = colonna(foglio, "TIPO", 12)
        val cSottotipo = colonna(foglio, "SOTTOTIPO", 13)
        val cSaldoChf = colonna(foglio, "CHF", 6)
        val cSaldoEur = colonna(foglio, "EUR", 7)
        return foglio.keys.filter { it > 1 }.sorted().mapNotNull { r ->
            val data = data(foglio.cella(r, cData)) ?: return@mapNotNull null
            val importo = foglio.numero(r, cImporto) ?: return@mapNotNull null
            val valuta = if (foglio.testo(r, cValuta)?.uppercase() == Valute.CHF) Valute.CHF else Valute.EUR
            RigaExcel(
                conto = CONTO_LGT,
                valuta = valuta,
                data = data,
                importoCent = cent(importo),
                tipo = pulisci(foglio.testo(r, cTipo)),
                sottotipo = pulisci(foglio.testo(r, cSottotipo)),
                saldoCent = foglio.numero(r, if (valuta == Valute.CHF) cSaldoChf else cSaldoEur)?.let { cent(it) }
            )
        }
    }

    // --- Spostamenti ---

    /**
     * Abbina ogni spostamento alla sua riga speculare su un altro conto/valuta (segno opposto,
     * entro [GIORNI_ABBINAMENTO] giorni): prima gli importi identici nella stessa valuta, poi i
     * cambi CHF/EUR con importi compatibili. Restituisce indice riga -> indice riga abbinata.
     */
    private fun abbinaSpostamenti(righe: List<RigaExcel>): Map<Int, Int> {
        val spostamenti = righe.indices.filter { righe[it].spostamento }
        val abbinamenti = HashMap<Int, Int>()
        for (sameCurrencyPass in listOf(true, false)) {
            for (i in spostamenti.sortedBy { righe[it].data }) {
                if (i in abbinamenti) continue
                val a = righe[i]
                val candidato = spostamenti
                    .filter { j ->
                        val b = righe[j]
                        j != i && j !in abbinamenti &&
                            (b.conto to b.valuta) != (a.conto to a.valuta) &&
                            (a.importoCent > 0) != (b.importoCent > 0) &&
                            abs(b.data.toEpochDay() - a.data.toEpochDay()) <= GIORNI_ABBINAMENTO &&
                            if (sameCurrencyPass) {
                                b.valuta == a.valuta && b.importoCent == -a.importoCent
                            } else {
                                b.valuta != a.valuta && abs(b.importoCent).toDouble() / abs(a.importoCent).coerceAtLeast(1) in 0.75..1.35
                            }
                    }
                    .minByOrNull { j -> abs(righe[j].data.toEpochDay() - a.data.toEpochDay()) }
                if (candidato != null) {
                    abbinamenti[i] = candidato
                    abbinamenti[candidato] = i
                }
            }
        }
        return abbinamenti
    }

    /** Spostamento senza riga speculare: l'altro conto, preferendo la stessa valuta. */
    private fun destinazionePredefinita(r: RigaExcel, coppie: Collection<Pair<String, String>>): Pair<String, String>? {
        val altri = coppie.filter { it.first != r.conto }
        return altri.firstOrNull { it.second == r.valuta } ?: altri.firstOrNull { it.second == Valute.EUR } ?: altri.firstOrNull()
    }

    // --- Ricorrenze dal foglio Bollette ---

    /** Una spesa ricorrente per ogni colonna del foglio Bollette con nome (riga 1) e periodicità (riga 2). */
    private fun leggiRicorrenti(foglio: Foglio, oggi: YearMonth): List<RicorrenteExcel> {
        val mesiRighe = foglio.keys.filter { it >= 3 }.sorted()
            .mapNotNull { r -> data(foglio.cella(r, 1))?.let { r to YearMonth.from(it) } }
        val intestazioni = foglio[1].orEmpty().filter { (colonna, valore) -> colonna >= 8 && valore is String && valore.isNotBlank() }
        val risultato = LinkedHashMap<String, RicorrenteExcel>()
        for ((colonna, valore) in intestazioni.toSortedMap()) {
            val mesi = foglio.numero(2, colonna + 1)?.toInt()?.takeIf { it in 1..120 } ?: continue
            val nome = (valore as String).lines().first().trim()
            if (nome.isEmpty() || risultato.keys.any { it.equals(nome, ignoreCase = true) }) continue
            val conContatore1 = mesiRighe.filter { (r, _) -> foglio.numero(r, colonna - 1) == 1.0 }.map { it.second }
            val ultimoImporto = mesiRighe.filter { it.second <= oggi }
                .mapNotNull { (r, _) -> foglio.numero(r, colonna)?.takeIf { it != 0.0 } }
                .lastOrNull()
            risultato[nome] = RicorrenteExcel(
                nome = nome,
                mesi = mesi,
                meseInizio = conContatore1.lastOrNull { it <= oggi } ?: conContatore1.firstOrNull(),
                ultimoImportoCent = ultimoImporto?.let { cent(abs(it)) }
            )
        }
        return risultato.values.toList()
    }

    private fun leggiTarget(foglio: Foglio): Long? {
        for ((_, celle) in foglio) {
            val colonnaEtichetta = celle.entries.firstOrNull { (it.value as? String)?.trim()?.equals("Risparmio target", ignoreCase = true) == true }?.key
                ?: continue
            val valore = celle.entries.filter { it.key > colonnaEtichetta && it.value is Double }.maxByOrNull { it.key }?.value as? Double
            return valore?.let { cent(it) }
        }
        return null
    }

    // --- Utilità ---

    private fun cent(valore: Double): Long = (valore * 100).roundToLong()

    private fun pulisci(testo: String?): String? = testo?.trim()?.replace(Regex("\\s+"), " ")?.ifEmpty { null }

    /** Per ogni variante (chiave minuscola) la grafia più frequente. */
    private fun canonici(valori: List<String>): Map<String, String> =
        valori.groupBy { it.lowercase() }.mapValues { (_, lista) ->
            lista.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        }

    private fun data(valore: Any?): LocalDate? = when (valore) {
        is Double -> if (valore > 1) EPOCA_EXCEL.plusDays(valore.toLong()) else null
        is String -> try {
            LocalDate.parse(valore.trim(), FORMATO_DATA)
        } catch (e: Exception) {
            null
        }
        else -> null
    }

    companion object {
        /** Chiave (memorizzabile) di una combinazione tipo/sottotipo Bollette, indipendente da maiuscole e accenti. */
        fun chiaveBollette(tipo: String, sottotipo: String?): String = normalizza(tipo) + "|" + normalizza(sottotipo ?: "")

        private fun normalizza(testo: String): String =
            Normalizer.normalize(testo, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        private const val CONTO_HELLOBANK = "HelloBank"
        private const val CONTO_LGT = "LGT"
        private const val SUFFISSO_RICORRENTE = " (ricorrente)"
        private val TIPI_ENTRATA = setOf("stipendio", "interessi")
        private const val GIORNI_ABBINAMENTO = 7L
        private val EPOCA_EXCEL: LocalDate = LocalDate.of(1899, 12, 30)
        private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")
    }
}
