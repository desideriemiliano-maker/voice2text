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

    /**
     * La riga sul conto/valuta [destinazioneId] che fa da contro-operazione di [op]: quella già
     * [collegata] se sta su quel conto, altrimenti la speculare trovata con [trovaControparte].
     */
    fun controparteSu(
        op: Operazione,
        collegata: Operazione?,
        destinazioneId: Long?,
        operazioni: List<Operazione>,
        valutaDi: (Long) -> String?
    ): Operazione? =
        if (collegata != null && collegata.contoValutaId == destinazioneId) collegata
        else trovaControparte(op, destinazioneId, operazioni, valutaDi)

    /** Distanza massima in giorni delle righe proposte come contro-operazione da scegliere a mano. */
    const val GIORNI_CANDIDATE = 15L

    /**
     * Le righe del conto/valuta [destinazioneId] che l'utente può scegliere come contro-operazione di
     * [op]: segno opposto, non collegate ad altro, entro [GIORNI_CANDIDATE] giorni (più la riga già
     * [collegata], se sta su quel conto). Prima gli spostamenti, poi per vicinanza di data.
     */
    fun candidate(op: Operazione, collegata: Operazione?, destinazioneId: Long?, operazioni: List<Operazione>): List<Operazione> {
        if (destinazioneId == null) return emptyList()
        val trovate = operazioni.filter {
            it.id != op.id && it.contoValutaId == destinazioneId &&
                (it.id == collegata?.id || (
                    (it.collegataId == null || it.collegataId == op.id) &&
                        (it.importoCent > 0) != (op.importoCent > 0) &&
                        abs(it.data - op.data) <= GIORNI_CANDIDATE
                    ))
        }
        return trovate.sortedWith(compareBy({ it.id != collegata?.id }, { !it.trasferimento }, { abs(it.data - op.data) }))
    }
}
