package com.desideri.familybalance.logica

import com.desideri.familybalance.data.dataPerRicorrente
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.PrevisioneRicorrente
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.data.Voce
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Come un'operazione concorre al bilancio. */
enum class Classe { ENTRATA, CORRENTE, RICORRENTE, TRASFERIMENTO }

enum class StatoMese { PASSATO, CORRENTE, FUTURO }

/** Da dove viene l'importo previsto di una scadenza ricorrente. */
enum class FontePrevisione { ANAGRAFICA, MEDIA, PERSONALIZZATA, NESSUNA }

/**
 * Una spesa ricorrente in un mese: quanto è stato pagato e/o quanto è previsto (importi EUR con segno).
 * Per le previsioni: [fonte] dell'importo, [mediaSu] (mesi e importi usati per la media),
 * [dataPrevista] se nota e [meseOrigine] se la scadenza è stata spostata qui da un altro mese.
 * [meseScadenza] è il mese della scadenza secondo la ricorrenza (quello a cui si riferisce la
 * personalizzazione).
 */
data class RigaRicorrente(
    val voce: Voce,
    val pagato: Double,
    val previsto: Double?,
    val fonte: FontePrevisione = FontePrevisione.NESSUNA,
    val mediaSu: List<Pair<YearMonth, Double>> = emptyList(),
    val dataPrevista: Long? = null,
    val meseOrigine: YearMonth? = null,
    val meseScadenza: YearMonth? = null,
    /** Scadenza del mese annullata dall'utente (nessuna previsione). */
    val annullata: Boolean = false,
    /** Parte di [pagato] con operazioni già avvenute (data fino a oggi); il resto è registrato con data futura. */
    val giaPagato: Double = pagato,
    /**
     * Importo della scadenza (calcolato o impostato) prima di togliere il pagato: [previsto] è il
     * residuo ancora da pagare. Null per le righe senza previsione.
     */
    val importoScadenza: Double? = previsto,
    /** Data dell'ultima operazione pagata nel mese (null se nessuna). */
    val dataPagamento: Long? = null,
    /**
     * Data prevista della scadenza: quella indicata ([dataPrevista]) o, se manca, lo stesso giorno
     * del mese dell'ultimo pagamento della spesa; null se non ricavabile.
     */
    val dataStimata: Long? = null
) {
    /** Data da mostrare: il pagamento se la spesa è tutta pagata, altrimenti quella prevista (null = n.d.). */
    val data: Long? get() = if (previsto == null && dataPagamento != null) dataPagamento else dataStimata
}

data class MeseRicorrenti(
    val mese: YearMonth,
    val righe: List<RigaRicorrente>
) {
    val totalePagato: Double get() = righe.sumOf { it.pagato }
    val totalePrevisto: Double get() = righe.sumOf { it.previsto ?: 0.0 }
    val totaleGiaPagato: Double get() = righe.sumOf { it.giaPagato }
    /** Totale del mese: pagato e, dove manca, previsto. */
    val totale: Double get() = totalePagato + totalePrevisto
    /** Quanto manca al totale rispetto al già pagato. */
    val totaleMancante: Double get() = totale - totaleGiaPagato
}

/**
 * Riga mensile della sezione Bilancio (importi EUR con segno: le spese sono negative).
 *
 * - [risparmio] = entrate + spese correnti (le ricorrenti sono escluse, come nel foglio "Totale"
 *   dell'Excel dove il target si confronta con Stipendio+Interessi+spese correnti).
 * - [saldoFine]: saldo complessivo a fine mese (per il mese corrente: ad oggi), con le ricorrenti nel
 *   mese a cui sono imputate: differisce dal saldo reale dei conti solo per quelle imputate a un altro mese.
 * - [saldoPrevisto]: per mese corrente e futuri, saldo del mese precedente + target di risparmio
 *   + spese ricorrenti (pagate e previste) del mese.
 * - [cambioChfEur]: cambio usato per il mese; le operazioni in CHF del mese sono convertite con questo
 *   e i saldi CHF a fine mese sono valutati con questo.
 * - [effettoCambio]: variazione del saldo dovuta solo al cambio: saldi CHF di inizio mese per la
 *   differenza di cambio rispetto al mese precedente.
 * - [cambioSpostamenti]: differenza tra EUR e CHF (al cambio del mese) negli spostamenti CHF↔EUR
 *   collegati: il costo (o guadagno) del cambio applicato dalla banca.
 */
/** Un mese usato per la stima delle spese correnti: spese fino al giorno di riferimento e del mese intero. */
data class MeseStimaCorrenti(val mese: YearMonth, val finoAlGiorno: Double, val totale: Double) {
    val percentuale: Double? get() = if (totale != 0.0) finoAlGiorno / totale else null
}

/**
 * Proiezione a fine mese delle spese correnti del mese corrente: [finora] fino al [giorno] dell'ultima
 * spesa corrente; nei [mesi] precedenti la quota di spese fatta entro lo stesso giorno, in media
 * [percentuale]; [stima] = finora / percentuale (finora se non calcolabile).
 */
data class StimaCorrenti(
    val giorno: Int,
    val finora: Double,
    val mesi: List<MeseStimaCorrenti>,
    val percentuale: Double?,
    val stima: Double
)

data class RigaBilancio(
    val mese: YearMonth,
    val stato: StatoMese,
    val entrate: Double,
    val correnti: Double,
    val ricorrentiPagati: Double,
    val ricorrentiPrevisti: Double,
    val saldoFine: Double?,
    val saldoPrevisto: Double?,
    val target: Double,
    val cambioChfEur: Double = 1.0,
    val effettoCambio: Double = 0.0,
    val cambioSpostamenti: Double = 0.0,
    /**
     * Stipendio/interessi usati nel bilancio del mese: per i passati quelli entrati nel mese; per il
     * corrente quelli entrati o, se non ancora arrivati, la media degli ultimi mesi
     * ([stipendioStimato]); null per i futuri (si usa il target).
     */
    val stipendio: Double? = null,
    val stipendioStimato: Boolean = false,
    /** Saldo di inizio mese: reale di fine mese precedente (per i futuri il saldo finale previsto). */
    val saldoIniziale: Double? = null,
    /** Saldo finale calcolato del mese prima (per la variazione), null per il primo mese. */
    val saldoFinalePrecedente: Double? = null,
    /** Mese corrente: proiezione a fine mese delle spese correnti. */
    val stimaCorrenti: StimaCorrenti? = null
) {
    /** Spese correnti del bilancio: per il mese corrente la stima a fine mese, altrimenti quelle registrate. */
    val correntiBilancio: Double get() = stimaCorrenti?.stima ?: correnti

    /**
     * Saldo di fine mese del bilancio: saldo iniziale + stipendio + spese correnti e ricorrenti (per i
     * futuri con il target al posto dello stipendio e delle correnti). Il saldo reale dei conti è [saldoFine].
     */
    val saldoFinale: Double?
        get() = when (stato) {
            StatoMese.FUTURO -> saldoPrevisto
            else -> saldoIniziale?.let { it + (stipendio ?: 0.0) + correntiBilancio + ricorrentiTotali }
        }
    /** Risparmio del mese: stipendio (entrate) + spese correnti. */
    val risparmio: Double get() = (stipendio ?: entrate) + correntiBilancio
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
        cambi: Cambi,
        perMedia: Boolean = false
    ): Map<Long, Map<YearMonth, Double>> {
        val ids = vociRicorrenti.map { it.id }.toHashSet()
        val storico = HashMap<Long, HashMap<YearMonth, Double>>()
        for (op in operazioni) {
            val voceId = op.voceId ?: continue
            if (op.trasferimento || voceId !in ids || (perMedia && op.esclusaDaMedia)) continue
            val perMese = storico.getOrPut(voceId) { HashMap() }
            val m = mese(op.dataPerRicorrente)
            perMese[m] = (perMese[m] ?: 0.0) + cambi.inEuro(op.importoCent, valutaDi[op.contoValutaId] ?: Valute.EUR, m)
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
        val ultimi = ultimiPagamenti(storicoVoce, oggi).map { it.second }
        return if (ultimi.isEmpty()) null else ultimi.average()
    }

    /** Le ultime 6 occorrenze pagate prima di [oggi] (mese, importo EUR), usate per la media. */
    fun ultimiPagamenti(storicoVoce: Map<YearMonth, Double>?, oggi: YearMonth): List<Pair<YearMonth, Double>> =
        storicoVoce.orEmpty().entries
            .filter { it.key < oggi && it.value != 0.0 }
            .sortedByDescending { it.key }
            .take(6)
            .map { it.key to it.value }

    /**
     * Spese ricorrenti mese per mese secondo la loro ricorrenza: il pagato (operazioni del mese) e
     * il residuo ancora previsto, cioè l'importo stimato o impostato meno il pagato (senza
     * operazioni tutto l'importo, anche nei mesi passati come non pagato). Il residuo si considera
     * per le scadenze con importo impostato e, per quelle calcolate, dal mese corrente in poi: nei
     * mesi passati un pagamento chiude la scadenza stimata. Le [personalizzazioni] (importo, data o
     * spostamento di una singola scadenza) prevalgono sul calcolo; le voci obsolete non hanno
     * previsioni.
     */
    fun ricorrenti(
        mesi: List<YearMonth>,
        voci: List<Voce>,
        contiValuta: List<ContoValuta>,
        operazioni: List<Operazione>,
        cambi: Cambi,
        oggi: YearMonth,
        personalizzazioni: List<PrevisioneRicorrente> = emptyList(),
        /** Giorno (epochDay) fino a cui le operazioni contano come già pagate; null = tutte. */
        alGiorno: Long? = null
    ): List<MeseRicorrenti> {
        val vociRicorrenti = voci.filter { it.ricorrente && !it.entrata }
        val valutaDi = contiValuta.associate { it.id to it.valuta }
        val storico = storicoRicorrenti(vociRicorrenti, operazioni, valutaDi, cambi)
        val storicoAdOggi = if (alGiorno == null) storico
        else storicoRicorrenti(vociRicorrenti, operazioni.filter { it.data <= alGiorno }, valutaDi, cambi)
        // Per la media non contano le operazioni escluse dall'utente.
        val storicoMedia = storicoRicorrenti(vociRicorrenti, operazioni, valutaDi, cambi, perMedia = true)
        val previsioni = vociRicorrenti.associate { it.id to previsione(it, storicoMedia[it.id], oggi) }
        val perVoceEMese = personalizzazioni.associateBy { it.voceId to it.mese }
        val idsRicorrenti = vociRicorrenti.map { it.id }.toHashSet()
        val opsPerVoce = operazioni.filter { !it.trasferimento && it.voceId in idsRicorrenti }.groupBy { it.voceId!! }
        val spostatePerMese = personalizzazioni.filter { it.spostataA != null && it.spostataA != it.mese }
            .groupBy { testoInMese(it.spostataA!!) }

        fun riga(voce: Voce, pagato: Double, meseScadenza: YearMonth, p: PrevisioneRicorrente?, origine: YearMonth?): RigaRicorrente? {
            val calcolato = previsioni[voce.id]
            val (previsto, fonte) = when {
                p?.importoCent != null -> -abs(p.importoCent) / 100.0 to FontePrevisione.PERSONALIZZATA
                voce.importoPrevistoCent != null -> calcolato to FontePrevisione.ANAGRAFICA
                calcolato != null -> calcolato to FontePrevisione.MEDIA
                else -> null to FontePrevisione.NESSUNA
            }
            if (previsto == null || previsto == 0.0) return null
            return RigaRicorrente(
                voce, pagato, previsto, fonte,
                mediaSu = if (fonte == FontePrevisione.MEDIA) ultimiPagamenti(storicoMedia[voce.id], oggi) else emptyList(),
                dataPrevista = p?.data,
                meseOrigine = origine,
                meseScadenza = meseScadenza
            )
        }

        return mesi.map { m ->
            val righe = vociRicorrenti.flatMap { voce ->
                val pagato = storico[voce.id]?.get(m) ?: 0.0
                val giaPagato = storicoAdOggi[voce.id]?.get(m) ?: 0.0
                val p = perVoceEMese[voce.id to m.toString()]
                // Dovuta per la ricorrenza o aggiunta a mano in questo mese.
                val dovutaQui = !voce.obsoleta && (dovuta(voce, m) || p?.aggiunta == true)
                val annullata = dovutaQui && p?.annullata == true
                // Scadenza del mese secondo la ricorrenza, se non annullata né spostata altrove.
                val propria = if (dovutaQui && !annullata && (p?.spostataA == null || p.spostataA == m.toString())) {
                    riga(voce, 0.0, m, p, null)
                } else null
                // Scadenze di altri mesi spostate in questo.
                val arrivate = if (voce.obsoleta) emptyList() else {
                    spostatePerMese[m].orEmpty().filter { it.voceId == voce.id && !it.annullata }
                        .mapNotNull { q -> testoInMese(q.mese)?.let { origine -> riga(voce, 0.0, origine, q, origine) } }
                }
                // Il pagato del mese copre le scadenze in ordine; resta previsto il residuo (vedi sopra).
                var daCoprire = pagato
                val previste = (listOfNotNull(propria) + arrivate).mapNotNull { r ->
                    val importo = r.previsto ?: return@mapNotNull null
                    val conResiduo = pagato == 0.0 || r.fonte == FontePrevisione.PERSONALIZZATA || m >= oggi
                    val residuo = when {
                        !conResiduo -> { daCoprire = 0.0; 0.0 }
                        // Spese negative: resta da pagare se l'importo è "più negativo" del pagato.
                        importo < daCoprire - 0.005 -> (importo - daCoprire).also { daCoprire = 0.0 }
                        else -> { daCoprire -= importo; 0.0 }
                    }
                    if (residuo == 0.0) null else r.copy(previsto = residuo, importoScadenza = importo)
                }
                // Date: ultimo pagamento del mese e data prevista (indicata o dal giorno dell'ultimo pagamento).
                val dataPagamento = opsPerVoce[voce.id].orEmpty().filter { mese(it.dataPerRicorrente) == m }.maxOfOrNull { it.data }
                val giornoAbituale = opsPerVoce[voce.id].orEmpty().maxByOrNull { it.data }?.let { LocalDate.ofEpochDay(it.data).dayOfMonth }
                fun stimata(r: RigaRicorrente) = r.dataPrevista
                    ?: giornoAbituale?.let { m.atDay(it.coerceAtMost(m.lengthOfMonth())).toEpochDay() }
                // Il pagato va su una sola riga (la prima), per non contarlo più volte.
                val conPagato = if (pagato == 0.0) previste else when {
                    previste.isNotEmpty() -> listOf(previste.first().copy(pagato = pagato, giaPagato = giaPagato)) + previste.drop(1)
                    else -> listOf(RigaRicorrente(voce, pagato, null, meseScadenza = m, giaPagato = giaPagato))
                }
                when {
                    conPagato.isNotEmpty() -> conPagato.map { it.copy(dataPagamento = dataPagamento.takeIf { _ -> it.pagato != 0.0 }, dataStimata = stimata(it)) }
                    annullata -> listOf(RigaRicorrente(voce, 0.0, null, meseScadenza = m, annullata = true))
                    else -> emptyList()
                }
            }.sortedBy { it.voce.descrizione.lowercase() }
            MeseRicorrenti(m, righe)
        }
    }

    fun bilancio(
        contiValuta: List<ContoValuta>,
        voci: List<Voce>,
        operazioni: List<Operazione>,
        cambi: Cambi,
        targetEuro: Double,
        oggi: YearMonth,
        mesiFuturi: Int = 12,
        personalizzazioni: List<PrevisioneRicorrente> = emptyList(),
        /** Mesi per la media dello stipendio del mese corrente, se non è ancora arrivato. */
        mesiMediaStipendio: Int = 5
    ): List<RigaBilancio> {
        val valutaDi = contiValuta.associate { it.id to it.valuta }
        val vociPerId = voci.associateBy { it.id }
        val operazioniPerId = operazioni.associateBy { it.id }
        // Movimenti del mese per valuta (centesimi): i saldi CHF si valutano al cambio di ogni mese.
        val movimentoEur = HashMap<YearMonth, Long>()
        val movimentoChf = HashMap<YearMonth, Long>()
        val entrate = HashMap<YearMonth, Double>()
        val correnti = HashMap<YearMonth, Double>()
        val ricorrentiPagati = HashMap<YearMonth, Double>()
        val cambioSpostamenti = HashMap<YearMonth, Double>()
        var primoMese = oggi

        for (op in operazioni) {
            val classe = classifica(op, op.voceId?.let { vociPerId[it] })
            // Le ricorrenti contano nel mese a cui sono imputate (data per la spesa ricorrente), come
            // nella sezione Ricorrenti, sia come spesa sia nel saldo del bilancio: così una ricorrente
            // pagata a fine mese per il mese dopo non sposta il saldo da un mese all'altro.
            val m = if (classe == Classe.RICORRENTE) mese(op.dataPerRicorrente) else mese(op.data)
            if (m < primoMese) primoMese = m
            val valuta = valutaDi[op.contoValutaId] ?: Valute.EUR
            val movimento = if (valuta == Valute.CHF) movimentoChf else movimentoEur
            movimento[m] = (movimento[m] ?: 0L) + op.importoCent
            val eur = cambi.inEuro(op.importoCent, valuta, m)
            val destinazione = when (classe) {
                Classe.ENTRATA -> entrate
                Classe.CORRENTE -> correnti
                Classe.RICORRENTE -> ricorrentiPagati
                Classe.TRASFERIMENTO -> {
                    val altra = op.collegataId?.let { operazioniPerId[it] }
                    if (altra != null && valutaDi[altra.contoValutaId] != valuta) cambioSpostamenti else null
                }
            }
            if (destinazione != null) destinazione[m] = (destinazione[m] ?: 0.0) + eur
        }

        // Stima delle spese correnti del mese corrente (vedi StimaCorrenti).
        val stimaCorrenti = run {
            fun eur(op: Operazione) = cambi.inEuro(op.importoCent, valutaDi[op.contoValutaId] ?: Valute.EUR, mese(op.data))
            val correntiOps = operazioni.filter { classifica(it, it.voceId?.let { v -> vociPerId[v] }) == Classe.CORRENTE }
            val delMese = correntiOps.filter { mese(it.data) == oggi }
            val giorno = delMese.maxOfOrNull { LocalDate.ofEpochDay(it.data).dayOfMonth } ?: return@run null
            val finora = delMese.sumOf { eur(it) }
            val perMese = correntiOps.groupBy { mese(it.data) }
            val mesiStima = (1..mesiMediaStipendio.coerceAtLeast(1)).map { oggi.minusMonths(it.toLong()) }.mapNotNull { m ->
                val ops = perMese[m] ?: return@mapNotNull null
                val limite = giorno.coerceAtMost(m.lengthOfMonth())
                MeseStimaCorrenti(m, ops.filter { LocalDate.ofEpochDay(it.data).dayOfMonth <= limite }.sumOf { eur(it) }, ops.sumOf { eur(it) })
            }
            val percentuali = mesiStima.mapNotNull { it.percentuale }.filter { it > 0 }
            val percentuale = if (percentuali.isEmpty()) null else percentuali.average()
            StimaCorrenti(giorno, finora, mesiStima.sortedBy { it.mese }, percentuale, percentuale?.let { finora / it } ?: finora)
        }

        val ultimoMese = oggi.plusMonths(mesiFuturi.toLong())
        val mesi = generateSequence(primoMese) { it.plusMonths(1) }.takeWhile { it <= ultimoMese }.toList()
        val previsti = ricorrenti(mesi.filter { it >= oggi }, voci, contiValuta, operazioni, cambi, oggi, personalizzazioni)
            .associate { it.mese to it.totalePrevisto }

        var saldoEurCent = contiValuta.filter { it.valuta != Valute.CHF }.sumOf { it.saldoInizialeCent }
        var saldoChfCent = contiValuta.filter { it.valuta == Valute.CHF }.sumOf { it.saldoInizialeCent }
        var cambioPrecedente = cambi.chfEur(primoMese)
        var saldo = saldoEurCent / 100.0 + saldoChfCent / 100.0 * cambioPrecedente
        var saldoPrecedente = saldo
        var saldoPrevistoPrecedente = saldo
        var finalePrecedente: Double? = null
        val righe = mesi.map { m ->
            val cambio = cambi.chfEur(m)
            val effettoCambio = saldoChfCent / 100.0 * (cambio - cambioPrecedente)
            saldoEurCent += movimentoEur[m] ?: 0L
            saldoChfCent += movimentoChf[m] ?: 0L
            saldo = saldoEurCent / 100.0 + saldoChfCent / 100.0 * cambio
            cambioPrecedente = cambio
            val stato = when {
                m < oggi -> StatoMese.PASSATO
                m == oggi -> StatoMese.CORRENTE
                else -> StatoMese.FUTURO
            }
            val pagati = ricorrentiPagati[m] ?: 0.0
            val previstiMese = previsti[m] ?: 0.0
            // Stipendio del mese: quello entrato; nel mese corrente, se non è ancora arrivato, la media
            // degli ultimi mesi in cui è entrato qualcosa.
            val entrateMese = entrate[m] ?: 0.0
            val stimato = stato == StatoMese.CORRENTE && entrateMese == 0.0
            val stipendio = when {
                stato == StatoMese.FUTURO -> null
                stimato -> (1..mesiMediaStipendio.coerceAtLeast(1)).mapNotNull { entrate[m.minusMonths(it.toLong())] }
                    .filter { it != 0.0 }.let { if (it.isEmpty()) 0.0 else it.average() }
                else -> entrateMese
            }
            val saldoPrevisto = when (stato) {
                StatoMese.PASSATO -> null
                // Saldo di fine mese precedente + stipendio + spese correnti + ricorrenti (pagate e
                // ancora previste); il target di risparmio vale solo per i mesi futuri.
                // Per i mesi successivi il corrente vale come un mese futuro (conti non ancora consolidati):
                // saldo di fine mese precedente + target di risparmio + ricorrenti del mese.
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
                target = targetEuro,
                cambioChfEur = cambio,
                effettoCambio = if (stato == StatoMese.FUTURO) 0.0 else effettoCambio,
                cambioSpostamenti = cambioSpostamenti[m] ?: 0.0,
                stipendio = stipendio,
                stipendioStimato = stimato,
                saldoIniziale = if (stato == StatoMese.FUTURO) saldoPrevistoPrecedente else saldoPrecedente,
                saldoFinalePrecedente = finalePrecedente,
                stimaCorrenti = if (stato == StatoMese.CORRENTE) stimaCorrenti else null
            )
            // Dopo il mese corrente si parte dal suo saldo con il target (come per i futuri).
            finalePrecedente = if (stato == StatoMese.CORRENTE) saldoPrevisto else riga.saldoFinale
            saldoPrecedente = saldo
            if (saldoPrevisto != null) saldoPrevistoPrecedente = saldoPrevisto
            riga
        }
        // I mesi passati senza alcuna operazione non si mostrano: una data errata molto indietro nel
        // tempo produrrebbe altrimenti anni di righe vuote.
        return righe.filter { it.stato != StatoMese.PASSATO || movimentoEur.containsKey(it.mese) || movimentoChf.containsKey(it.mese) }
    }
}
