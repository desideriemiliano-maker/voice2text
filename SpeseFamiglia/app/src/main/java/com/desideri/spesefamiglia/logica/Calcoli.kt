package com.desideri.spesefamiglia.logica

import com.desideri.spesefamiglia.data.ContoValuta
import com.desideri.spesefamiglia.data.Operazione
import com.desideri.spesefamiglia.data.Valute
import com.desideri.spesefamiglia.data.Voce
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Come un'operazione concorre al bilancio. */
enum class Classe { ENTRATA, CORRENTE, RICORRENTE, TRASFERIMENTO }

enum class StatoMese { PASSATO, CORRENTE, FUTURO }

/** Una spesa ricorrente in un mese: quanto è stato pagato e/o quanto è previsto (importi EUR con segno). */
data class RigaRicorrente(
    val voce: Voce,
    val pagato: Double,
    val previsto: Double?
)

data class MeseRicorrenti(
    val mese: YearMonth,
    val righe: List<RigaRicorrente>
) {
    val totalePagato: Double get() = righe.sumOf { it.pagato }
    val totalePrevisto: Double get() = righe.sumOf { it.previsto ?: 0.0 }
}

/**
 * Riga mensile della sezione Bilancio (importi EUR con segno: le spese sono negative).
 *
 * - [risparmio] = entrate + spese correnti (le ricorrenti sono escluse, come nel foglio "Totale"
 *   dell'Excel dove il target si confronta con Stipendio+Interessi+spese correnti).
 * - [saldoFine]: saldo reale complessivo a fine mese (per il mese corrente: ad oggi).
 * - [saldoPrevisto]: per mese corrente e futuri, saldo del mese precedente + target di risparmio
 *   + spese ricorrenti (pagate e previste) del mese.
 */
data class RigaBilancio(
    val mese: YearMonth,
    val stato: StatoMese,
    val entrate: Double,
    val correnti: Double,
    val ricorrentiPagati: Double,
    val ricorrentiPrevisti: Double,
    val saldoFine: Double?,
    val saldoPrevisto: Double?,
    val target: Double
) {
    val risparmio: Double get() = entrate + correnti
    val deltaTarget: Double get() = risparmio - target
    val ricorrentiTotali: Double get() = ricorrentiPagati + ricorrentiPrevisti
}

object Calcoli {

    fun inEuro(cent: Long, valuta: String, cambioChfEur: Double): Double =
        cent / 100.0 * (if (valuta == Valute.CHF) cambioChfEur else 1.0)

    fun mese(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))

    /** Saldo in centesimi (nella valuta propria) per ogni conto/valuta: iniziale + tutte le operazioni. */
    fun saldiCent(contiValuta: List<ContoValuta>, operazioni: List<Operazione>): Map<Long, Long> {
        val somme = HashMap<Long, Long>()
        for (op in operazioni) somme[op.contoValutaId] = (somme[op.contoValutaId] ?: 0L) + op.importoCent
        return contiValuta.associate { it.id to it.saldoInizialeCent + (somme[it.id] ?: 0L) }
    }

    fun classifica(op: Operazione, voce: Voce?): Classe = when {
        op.trasferimento -> Classe.TRASFERIMENTO
        voce?.entrata == true -> Classe.ENTRATA
        voce?.ricorrente == true -> Classe.RICORRENTE
        else -> Classe.CORRENTE
    }

    /** true se la voce ricorrente è attesa nel [mese] (ogni mesiRicorrenza mesi da meseInizio). */
    fun dovuta(voce: Voce, mese: YearMonth): Boolean {
        if (!voce.ricorrente) return false
        val inizio = voce.meseInizio?.let { testoInMese(it) } ?: return false
        if (mese < inizio) return false
        val passo = voce.mesiRicorrenza.coerceAtLeast(1)
        return ChronoUnit.MONTHS.between(inizio, mese) % passo == 0L
    }

    /** Totale (EUR con segno) di ogni voce ricorrente per mese, escludendo gli spostamenti. */
    private fun storicoRicorrenti(
        vociRicorrenti: Collection<Voce>,
        operazioni: List<Operazione>,
        valutaDi: Map<Long, String>,
        cambioChfEur: Double
    ): Map<Long, Map<YearMonth, Double>> {
        val ids = vociRicorrenti.map { it.id }.toHashSet()
        val storico = HashMap<Long, HashMap<YearMonth, Double>>()
        for (op in operazioni) {
            val voceId = op.voceId ?: continue
            if (op.trasferimento || voceId !in ids) continue
            val perMese = storico.getOrPut(voceId) { HashMap() }
            val m = mese(op.data)
            perMese[m] = (perMese[m] ?: 0.0) + inEuro(op.importoCent, valutaDi[op.contoValutaId] ?: Valute.EUR, cambioChfEur)
        }
        return storico
    }

    /**
     * Importo previsto (EUR, negativo) per una voce ricorrente: quello impostato in anagrafica o, in
     * mancanza, la media delle ultime 6 occorrenze pagate prima di [oggi] (come la media usata nel
     * foglio "Bollette"). null se non c'è nessun dato per stimarla.
     */
    fun previsione(voce: Voce, storicoVoce: Map<YearMonth, Double>?, oggi: YearMonth): Double? {
        voce.importoPrevistoCent?.let { return -abs(it) / 100.0 }
        val ultimi = storicoVoce.orEmpty().entries
            .filter { it.key < oggi && it.value != 0.0 }
            .sortedByDescending { it.key }
            .take(6)
            .map { it.value }
        return if (ultimi.isEmpty()) null else ultimi.average()
    }

    fun ricorrenti(
        mesi: List<YearMonth>,
        voci: List<Voce>,
        contiValuta: List<ContoValuta>,
        operazioni: List<Operazione>,
        cambioChfEur: Double,
        oggi: YearMonth
    ): List<MeseRicorrenti> {
        val vociRicorrenti = voci.filter { it.ricorrente && !it.entrata }
        val valutaDi = contiValuta.associate { it.id to it.valuta }
        val storico = storicoRicorrenti(vociRicorrenti, operazioni, valutaDi, cambioChfEur)
        val previsioni = vociRicorrenti.associate { it.id to previsione(it, storico[it.id], oggi) }
        return mesi.map { m ->
            val righe = vociRicorrenti.mapNotNull { voce ->
                val pagato = storico[voce.id]?.get(m) ?: 0.0
                val previsto = if (m >= oggi && pagato == 0.0 && dovuta(voce, m)) previsioni[voce.id] else null
                if (pagato != 0.0 || (previsto != null && previsto != 0.0)) RigaRicorrente(voce, pagato, previsto) else null
            }.sortedBy { it.voce.descrizione.lowercase() }
            MeseRicorrenti(m, righe)
        }
    }

    fun bilancio(
        contiValuta: List<ContoValuta>,
        voci: List<Voce>,
        operazioni: List<Operazione>,
        cambioChfEur: Double,
        targetEuro: Double,
        oggi: YearMonth,
        mesiFuturi: Int = 12
    ): List<RigaBilancio> {
        val valutaDi = contiValuta.associate { it.id to it.valuta }
        val vociPerId = voci.associateBy { it.id }
        val movimento = HashMap<YearMonth, Double>()
        val entrate = HashMap<YearMonth, Double>()
        val correnti = HashMap<YearMonth, Double>()
        val ricorrentiPagati = HashMap<YearMonth, Double>()
        var primoMese = oggi

        for (op in operazioni) {
            val m = mese(op.data)
            if (m < primoMese) primoMese = m
            val eur = inEuro(op.importoCent, valutaDi[op.contoValutaId] ?: Valute.EUR, cambioChfEur)
            movimento[m] = (movimento[m] ?: 0.0) + eur
            val destinazione = when (classifica(op, op.voceId?.let { vociPerId[it] })) {
                Classe.ENTRATA -> entrate
                Classe.CORRENTE -> correnti
                Classe.RICORRENTE -> ricorrentiPagati
                Classe.TRASFERIMENTO -> null
            }
            if (destinazione != null) destinazione[m] = (destinazione[m] ?: 0.0) + eur
        }

        val ultimoMese = oggi.plusMonths(mesiFuturi.toLong())
        val mesi = generateSequence(primoMese) { it.plusMonths(1) }.takeWhile { it <= ultimoMese }.toList()
        val previsti = ricorrenti(mesi.filter { it >= oggi }, voci, contiValuta, operazioni, cambioChfEur, oggi)
            .associate { it.mese to it.totalePrevisto }

        var saldo = contiValuta.sumOf { inEuro(it.saldoInizialeCent, it.valuta, cambioChfEur) }
        var saldoPrecedente = saldo
        var saldoPrevistoPrecedente = saldo
        val righe = mesi.map { m ->
            saldo += movimento[m] ?: 0.0
            val stato = when {
                m < oggi -> StatoMese.PASSATO
                m == oggi -> StatoMese.CORRENTE
                else -> StatoMese.FUTURO
            }
            val pagati = ricorrentiPagati[m] ?: 0.0
            val previstiMese = previsti[m] ?: 0.0
            val saldoPrevisto = when (stato) {
                StatoMese.PASSATO -> null
                StatoMese.CORRENTE -> saldoPrecedente + targetEuro + pagati + previstiMese
                StatoMese.FUTURO -> saldoPrevistoPrecedente + targetEuro + pagati + previstiMese
            }
            val riga = RigaBilancio(
                mese = m,
                stato = stato,
                entrate = entrate[m] ?: 0.0,
                correnti = correnti[m] ?: 0.0,
                ricorrentiPagati = pagati,
                ricorrentiPrevisti = previstiMese,
                saldoFine = if (stato == StatoMese.FUTURO) null else saldo,
                saldoPrevisto = saldoPrevisto,
                target = targetEuro
            )
            saldoPrecedente = saldo
            if (saldoPrevisto != null) saldoPrevistoPrecedente = saldoPrevisto
            riga
        }
        // I mesi passati senza alcuna operazione non si mostrano: una data errata molto indietro nel
        // tempo produrrebbe altrimenti anni di righe vuote.
        return righe.filter { it.stato != StatoMese.PASSATO || movimento.containsKey(it.mese) }
    }
}
