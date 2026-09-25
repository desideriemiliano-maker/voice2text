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

/**
 * Importa l'Excel delle spese ("Dettagli.xlsx") sostituendo tutti i dati dell'app:
 *
 * - fogli **HelloBank** (colonne H-K: Data, Importo, TIPO, SOTTOTIPO) e **LGT** (colonne I-M: Data,
 *   Valuta, Importo, TIPO, SOTTOTIPO) -> conti, operazioni e anagrafica voci. Le colonne sono
 *   cercate per intestazione nella riga 1, con queste lettere come ripiego;
 * - saldo iniziale di ogni conto/valuta = saldo progressivo della prima riga meno il suo importo;
 * - TIPO "Spostamento" -> trasferimento, con conto di destinazione ricavato abbinando le righe
 *   speculari sugli altri conti (stessa data circa, segno opposto);
 * - TIPO "Stipendio"/"Interessi" -> voci di entrata; TIPO "Bollette" -> voci ricorrenti, con
 *   periodicità e mese di partenza presi dal foglio **Bollette** (riga 1 intestazioni, riga 2 ogni
 *   quanti mesi, colonna precedente il contatore del mese);
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

    private data class Riga(
        val conto: String,
        val valuta: String,
        val data: LocalDate,
        val importoCent: Long,
        val tipo: String?,
        val sottotipo: String?,
        val saldoCent: Long?
    ) {
        val spostamento: Boolean get() = tipo?.equals(TIPO_SPOSTAMENTO, ignoreCase = true) == true
    }

    suspend fun importa(input: InputStream, oggi: YearMonth = YearMonth.now()): Esito = withContext(Dispatchers.IO) {
        val xlsx = LettoreXlsx(input)
        val helloBank = xlsx.foglio("HelloBank") ?: throw IllegalArgumentException("Foglio \"HelloBank\" non trovato nel file")
        val lgt = xlsx.foglio("LGT") ?: throw IllegalArgumentException("Foglio \"LGT\" non trovato nel file")

        val righe = leggiHelloBank(helloBank) + leggiLgt(lgt)
        if (righe.isEmpty()) throw IllegalArgumentException("Nessuna operazione trovata nei fogli HelloBank e LGT")

        // --- Anagrafica voci, normalizzando maiuscole/spazi (es. "regali" e "Regali") ---
        val righeConVoce = righe.filter { !it.spostamento && it.tipo != null }
        val tipoCanonico = canonici(righeConVoce.map { it.tipo!! })
        val sottotipoCanonico = HashMap<String, Map<String, String>>()
        righeConVoce.groupBy { it.tipo!!.lowercase() }.forEach { (tipoKey, lista) ->
            sottotipoCanonico[tipoKey] = canonici(lista.mapNotNull { it.sottotipo })
        }
        fun chiaveVoce(r: Riga): Pair<String, String?> {
            val tipoKey = r.tipo!!.lowercase()
            return tipoCanonico.getValue(tipoKey) to r.sottotipo?.let { sottotipoCanonico[tipoKey]?.get(it.lowercase()) }
        }

        val voci = LinkedHashMap<Pair<String, String?>, Voce>()
        for (r in righeConVoce) {
            val chiave = chiaveVoce(r)
            if (chiave !in voci) {
                val tipoLower = chiave.first.lowercase()
                voci[chiave] = Voce(
                    tipo = chiave.first,
                    sottotipo = chiave.second,
                    entrata = tipoLower in TIPI_ENTRATA,
                    ricorrente = tipoLower == TIPO_BOLLETTE,
                    mesiRicorrenza = 1
                )
            }
        }
        xlsx.foglio("Bollette")?.let { applicaRicorrenze(it, voci, oggi) }
        val target = xlsx.foglio("Impostazioni")?.let { leggiTarget(it) }

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
                        voceId = if (r.tipo != null) idVoce[chiaveVoce(r)] else null
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
            targetRisparmioCent = target
        )
    }

    // --- Lettura fogli conti ---

    private fun colonna(foglio: Foglio, intestazione: String, predefinita: Int): Int =
        foglio[1]?.entries
            ?.filter { (it.value as? String)?.trim()?.equals(intestazione, ignoreCase = true) == true }
            ?.maxOfOrNull { it.key }
            ?: predefinita

    private fun leggiHelloBank(foglio: Foglio): List<Riga> {
        val cData = colonna(foglio, "Data", 8)
        val cImporto = colonna(foglio, "Importo", 9)
        val cTipo = colonna(foglio, "TIPO", 10)
        val cSottotipo = colonna(foglio, "SOTTOTIPO", 11)
        val cSaldo = colonna(foglio, "Saldo", 4)
        return foglio.keys.filter { it > 1 }.sorted().mapNotNull { r ->
            val data = data(foglio.cella(r, cData)) ?: return@mapNotNull null
            val importo = foglio.numero(r, cImporto) ?: return@mapNotNull null
            Riga(
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

    private fun leggiLgt(foglio: Foglio): List<Riga> {
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
            Riga(
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
    private fun abbinaSpostamenti(righe: List<Riga>): Map<Int, Int> {
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
    private fun destinazionePredefinita(r: Riga, coppie: Collection<Pair<String, String>>): Pair<String, String>? {
        val altri = coppie.filter { it.first != r.conto }
        return altri.firstOrNull { it.second == r.valuta } ?: altri.firstOrNull { it.second == Valute.EUR } ?: altri.firstOrNull()
    }

    // --- Ricorrenze dal foglio Bollette ---

    private fun applicaRicorrenze(foglio: Foglio, voci: LinkedHashMap<Pair<String, String?>, Voce>, oggi: YearMonth) {
        val mesiRighe = foglio.keys.filter { it >= 3 }.sorted()
            .mapNotNull { r -> data(foglio.cella(r, 1))?.let { r to YearMonth.from(it) } }
        val tipoBollette = voci.keys.firstOrNull { it.first.equals(TIPO_BOLLETTE, ignoreCase = true) }?.first ?: "Bollette"
        val intestazioni = foglio[1].orEmpty().filter { (colonna, valore) -> colonna >= 8 && valore is String && valore.isNotBlank() }

        for ((colonna, valore) in intestazioni.toSortedMap()) {
            val mesi = foglio.numero(2, colonna + 1)?.toInt()?.takeIf { it in 1..120 } ?: continue
            val nome = (valore as String).lines().first().trim()
            val chiaveNormalizzata = normalizza(nome)

            val conContatore1 = mesiRighe.filter { (r, _) -> foglio.numero(r, colonna - 1) == 1.0 }.map { it.second }
            val meseInizio = conContatore1.lastOrNull { it <= oggi } ?: conContatore1.firstOrNull()

            val esistente = voci.entries.firstOrNull { (chiave, _) ->
                chiave.first.equals(tipoBollette, ignoreCase = true) && chiave.second?.let { normalizza(it) } == chiaveNormalizzata
            }
            if (esistente != null) {
                esistente.setValue(esistente.value.copy(ricorrente = true, mesiRicorrenza = mesi, meseInizio = meseInizio?.toString()))
            } else {
                val ultimoImporto = mesiRighe.filter { it.second <= oggi }
                    .mapNotNull { (r, _) -> foglio.numero(r, colonna)?.takeIf { it != 0.0 } }
                    .lastOrNull()
                voci[tipoBollette to nome] = Voce(
                    tipo = tipoBollette,
                    sottotipo = nome,
                    ricorrente = true,
                    mesiRicorrenza = mesi,
                    meseInizio = meseInizio?.toString(),
                    importoPrevistoCent = ultimoImporto?.let { cent(abs(it)) }
                )
            }
        }
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

    private fun normalizza(testo: String): String =
        Normalizer.normalize(testo, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun data(valore: Any?): LocalDate? = when (valore) {
        is Double -> if (valore > 1) EPOCA_EXCEL.plusDays(valore.toLong()) else null
        is String -> try {
            LocalDate.parse(valore.trim(), FORMATO_DATA)
        } catch (e: Exception) {
            null
        }
        else -> null
    }

    private companion object {
        const val CONTO_HELLOBANK = "HelloBank"
        const val CONTO_LGT = "LGT"
        const val TIPO_SPOSTAMENTO = "Spostamento"
        const val TIPO_BOLLETTE = "bollette"
        val TIPI_ENTRATA = setOf("stipendio", "interessi")
        const val GIORNI_ABBINAMENTO = 7L
        val EPOCA_EXCEL: LocalDate = LocalDate.of(1899, 12, 30)
        val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")
    }
}
