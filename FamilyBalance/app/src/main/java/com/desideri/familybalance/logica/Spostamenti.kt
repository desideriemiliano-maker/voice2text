package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Operazione
import kotlin.math.abs

/** Regole sugli spostamenti tra conti. */
object Spostamenti {

    /** Distanza massima in giorni tra uno spostamento e la sua riga corrispondente sull'altro conto. */
    const val GIORNI_CONTROPARTE = 5L

    /**
     * La riga corrispondente di uno spostamento [op] NON collegato sul conto/valuta [destinazioneId]
     * (tipico delle operazioni importate dall'Excel, dove le due righe sono separate): spostamento di
     * segno opposto, non collegato ad altro, entro [GIORNI_CONTROPARTE] giorni e, se i due conti hanno
     * la stessa valuta, con l'importo opposto. Tra più candidate la più vicina per data e importo.
     */
    fun trovaControparte(
        op: Operazione,
        destinazioneId: Long?,
        operazioni: List<Operazione>,
        valutaDi: (Long) -> String?
    ): Operazione? {
        if (destinazioneId == null) return null
        val stessaValuta = valutaDi(op.contoValutaId) == valutaDi(destinazioneId)
        return operazioni
            .filter {
                it.id != op.id && it.contoValutaId == destinazioneId && it.trasferimento &&
                    (it.collegataId == null || it.collegataId == op.id) &&
                    (it.contoValutaDestId == null || it.contoValutaDestId == op.contoValutaId) &&
                    (it.importoCent > 0) != (op.importoCent > 0) &&
                    abs(it.data - op.data) <= GIORNI_CONTROPARTE &&
                    (!stessaValuta || it.importoCent == -op.importoCent)
            }
            .minWithOrNull(compareBy({ abs(it.data - op.data) }, { abs(abs(it.importoCent) - abs(op.importoCent)) }))
    }
}
