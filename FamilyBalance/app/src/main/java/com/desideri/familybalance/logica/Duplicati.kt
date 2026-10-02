package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Operazione

/** Ricerca di possibili operazioni doppie su un conto. */
object Duplicati {

    /**
     * Gruppi di operazioni con lo stesso importo e date entro [giorni] l'una dall'altra (a catena:
     * ognuna entro [giorni] dalla precedente), dal gruppo più recente. Solo gruppi di almeno due.
     */
    fun gruppi(operazioni: List<Operazione>, giorni: Int): List<List<Operazione>> =
        operazioni.groupBy { it.importoCent }.values.flatMap { stesse ->
            val ordinate = stesse.sortedWith(compareBy({ it.data }, { it.id }))
            val gruppi = mutableListOf<MutableList<Operazione>>()
            for (op in ordinate) {
                val ultimo = gruppi.lastOrNull()
                if (ultimo != null && op.data - ultimo.last().data <= giorni) ultimo += op else gruppi += mutableListOf(op)
            }
            gruppi.filter { it.size >= 2 }
        }.sortedByDescending { g -> g.maxOf { it.data } }

    /**
     * Le altre operazioni con lo stesso importo del [gruppo] con data entro [giorni] dalla prima o
     * dall'ultima del gruppo (fuori dal gruppo stesso), in ordine di data.
     */
    fun intorno(operazioni: List<Operazione>, gruppo: List<Operazione>, giorni: Int): List<Operazione> {
        val importo = gruppo.first().importoCent
        val ids = gruppo.map { it.id }.toSet()
        val da = gruppo.minOf { it.data } - giorni
        val a = gruppo.maxOf { it.data } + giorni
        return operazioni.filter { it.importoCent == importo && it.id !in ids && it.data in da..a }.sortedBy { it.data }
    }
}
