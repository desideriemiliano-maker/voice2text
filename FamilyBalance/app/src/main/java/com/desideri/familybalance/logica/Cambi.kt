package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Valute
import java.time.YearMonth
import java.util.TreeMap
import kotlin.math.abs

/** Da dove viene il cambio usato per un mese. */
enum class FonteCambio(val descrizione: String) {
    INSERITO("inserito"),
    SPOSTAMENTI("dagli spostamenti CHF↔EUR"),
    RIPORTATO("riportato da un altro mese"),
    ATTUALE("cambio attuale (Impostazioni)")
}

data class CambioMese(val mese: YearMonth, val chfEur: Double, val fonte: FonteCambio)

/**
 * Cambio CHF→EUR per mese (1 CHF = x EUR), usato per il bilancio storico:
 * - mese corrente e futuri: il cambio attuale delle Impostazioni;
 * - mesi passati: quello inserito a mano, altrimenti la media (pesata sugli importi) degli
 *   spostamenti CHF↔EUR collegati del mese, altrimenti quello del mese noto più vicino prima (o,
 *   se non c'è, dopo); senza nessun dato il cambio attuale.
 */
class Cambi(
    private val inseriti: Map<YearMonth, Double>,
    private val daSpostamenti: Map<YearMonth, Double>,
    val attuale: Double,
    private val oggi: YearMonth
) {
    private val noti = TreeMap<YearMonth, Double>().apply {
        putAll(daSpostamenti)
        putAll(inseriti)
    }

    fun cambioMese(mese: YearMonth): CambioMese {
        // Corrente e futuri: il cambio attuale delle Impostazioni, salvo quello inserito per il mese.
        if (mese >= oggi) return inseriti[mese]?.let { CambioMese(mese, it, FonteCambio.INSERITO) } ?: CambioMese(mese, attuale, FonteCambio.ATTUALE)
        inseriti[mese]?.let { return CambioMese(mese, it, FonteCambio.INSERITO) }
        daSpostamenti[mese]?.let { return CambioMese(mese, it, FonteCambio.SPOSTAMENTI) }
        val vicino = noti.floorEntry(mese) ?: noti.ceilingEntry(mese)
        return if (vicino != null) CambioMese(mese, vicino.value, FonteCambio.RIPORTATO) else CambioMese(mese, attuale, FonteCambio.ATTUALE)
    }

    fun chfEur(mese: YearMonth): Double = cambioMese(mese).chfEur

    fun inEuro(cent: Long, valuta: String, mese: YearMonth): Double =
        cent / 100.0 * (if (valuta == Valute.CHF) chfEur(mese) else 1.0)

    companion object {
        /** Cambi plausibili: fuori da questo intervallo uno spostamento è considerato un errore di dati. */
        private val PLAUSIBILI = 0.5..2.0

        /** Un unico cambio per tutti i mesi. */
        fun fisso(chfEur: Double) = Cambi(emptyMap(), emptyMap(), chfEur, YearMonth.of(1900, 1))

        /**
         * Cambio medio per mese ricavato dagli spostamenti collegati tra un conto CHF e uno EUR:
         * EUR accreditati/addebitati diviso CHF, pesato sugli importi (mese della riga in CHF).
         */
        fun daSpostamenti(operazioni: List<Operazione>, valutaDi: (Long) -> String?): Map<YearMonth, Double> {
            val perId = operazioni.associateBy { it.id }
            val chf = HashMap<YearMonth, Long>()
            val eur = HashMap<YearMonth, Long>()
            for (op in operazioni) {
                if (!op.trasferimento || valutaDi(op.contoValutaId) != Valute.CHF) continue
                val altra = op.collegataId?.let { perId[it] } ?: continue
                if (valutaDi(altra.contoValutaId) != Valute.EUR || op.importoCent == 0L) continue
                if ((op.importoCent > 0) == (altra.importoCent > 0)) continue
                val cambio = abs(altra.importoCent).toDouble() / abs(op.importoCent)
                if (cambio !in PLAUSIBILI) continue
                val m = Calcoli.mese(op.data)
                chf[m] = (chf[m] ?: 0L) + abs(op.importoCent)
                eur[m] = (eur[m] ?: 0L) + abs(altra.importoCent)
            }
            return chf.mapValues { (m, c) -> eur.getValue(m).toDouble() / c }
        }
    }
}
